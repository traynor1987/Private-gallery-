package uk.co.traynor.privategallery.core.guardian

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class GuardianCaptureEntry(val time: Long, val sender: String, val text: String, val displayName: String? = null)

internal data class GuardianCaptureEvent(
    val profileSerial: Long,
    val conversation: String,
    val observedAt: Long,
    val entries: List<GuardianCaptureEntry>,
    val app: String = GUARDIAN_WHATSAPP,
    val sensitivity: GuardianSensitivity = GuardianSensitivity.STANDARD,
    val holdUncertain: Boolean = false,
    val requiresContactConsent: Boolean = false,
)

internal data class GuardianCaptureResult(val added: Int, val retained: Int, val partial: Boolean, val allDurable: Boolean = false)

internal data class GuardianInboxRow(
    val id: String, val time: Long, val section: GuardianInboxSection, val remainingMillis: Long,
    val assessment: GuardianAssessment? = null,
    val senderName: String? = null,
    val arrivedAt: Long = time,
    val conversation: GuardianConversationKey? = null,
    val availableAfter: Long? = null,
    val ownerLabel: GuardianAssessment? = null,
    val patterns: Set<NovaBrainPattern>? = null,
)

/** The export only needs section membership and layout-local list keys. */
internal fun guardianDiagnosticRows(rows: List<GuardianInboxRow>): List<GuardianInboxRow> =
    rows.mapIndexed { index, row -> GuardianInboxRow("preview-$index", 0, row.section, 0) }

internal enum class GuardianInboxAction { READY, HOLD, ARCHIVE, DELETE }

internal fun guardianInboxClock(context: Context) = GuardianInboxClock(
    System.currentTimeMillis(), SystemClock.elapsedRealtime(),
    runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1),
)

/** Private, bounded archive. The capture phase exposes aggregate counts only. */
internal class GuardianCaptureStore private constructor(private val context: Context, private val training: Boolean, private val fixture: Boolean = false, private val contacts: Boolean = false, private val validation: Boolean = false, private val phoneShield: Boolean = false, private val callHistory: Boolean = false) {
    constructor(context: Context) : this(context.applicationContext, false)

    private val destination = File(context.noBackupFilesDir, when { callHistory -> "guardian-call-history-v1.enc"; phoneShield -> "guardian-phone-shield-v1.enc"; validation -> "nova-brain-validation-v1.enc"; contacts -> "guardian-contacts-v1.enc"; fixture -> "guardian-fixture-v1.enc"; training -> "guardian-learning-v1.enc"; else -> ARCHIVE_NAME })
    private val alias = when { callHistory -> "nova-guardian-call-history-v1"; phoneShield -> "nova-guardian-phone-shield-v1"; validation -> "nova-brain-validation-v1"; contacts -> "nova-guardian-contacts-v1"; fixture -> "nova-guardian-fixture-v1"; training -> "nova-guardian-learning-v1"; else -> KEY_ALIAS }
    private val aad = when {
        callHistory -> "NovaOS Guardian call history v1".toByteArray(Charsets.UTF_8)
        phoneShield -> "NovaOS Guardian phone shield v1".toByteArray(Charsets.UTF_8)
        validation -> "NovaOS Nova Brain validation v1".toByteArray(Charsets.UTF_8)
        contacts -> "NovaOS Guardian contact rules v1".toByteArray(Charsets.UTF_8)
        fixture -> "NovaOS Guardian fixture v1".toByteArray(Charsets.UTF_8)
        training -> "NovaOS Guardian learning v1".toByteArray(Charsets.UTF_8)
        else -> AAD
    }
    private val atomic = AtomicFile(destination)

    private fun phoneShieldArchive() = GuardianCaptureStore(context, training = false, phoneShield = true)

    private fun callHistoryArchive() = GuardianCaptureStore(context, training = false, callHistory = true)

    fun callHistoryEnabled(): Boolean = synchronized(lock) {
        phoneShieldArchive().read().firstOrNull()?.optBoolean("recordCalls") == true
    }

    fun setCallHistoryEnabled(enabled: Boolean) = synchronized(lock) {
        val archive = phoneShieldArchive()
        val rows = archive.read()
        val settings = rows.firstOrNull() ?: JSONObject().put("enabled", false)
        archive.write(listOf(settings.put("recordCalls", enabled)) + rows.drop(1))
    }

    fun recordCallAttempt(activity: GuardianCallActivity) = synchronized(lock) {
        if (!callHistoryEnabled()) return@synchronized
        val archive = callHistoryArchive()
        val rows = archive.retainedCallRows(activity.time)
        val row = JSONObject().put("number", activity.number).put("platform", activity.platform.name)
            .put("time", activity.time).put("mode", activity.mode.name)
        archive.write((listOf(row) + rows).take(100))
    }

    private fun retainedCallRows(now: Long): List<JSONObject> {
        val rows = read()
        val retained = rows.filter { it.getLong("time") in (now - 7L * 86_400_000)..now }
        if (rows.size != retained.size) write(retained)
        return retained
    }

    fun callHistory(): List<GuardianCallActivity> = synchronized(lock) {
        callHistoryArchive().retainedCallRows(System.currentTimeMillis()).map {
            GuardianCallActivity(it.getString("number"), GuardianCallSource.valueOf(it.getString("platform")),
                it.getLong("time"), GuardianCallMode.valueOf(it.getString("mode")))
        }
    }

    fun clearCallHistory() = callHistoryArchive().clear()

    fun phoneShieldSettings(): Pair<Boolean, List<GuardianPhoneRule>> = synchronized(lock) {
        val records = phoneShieldArchive().read()
        val enabled = records.firstOrNull()?.getBoolean("enabled") ?: false
        enabled to records.drop(1).map {
            GuardianPhoneRule(
                it.getString("number"), GuardianCallMode.valueOf(it.getString("mode")),
                GuardianCallMode.valueOf(it.optString("mainWhatsApp", GuardianCallMode.ALLOW.name)),
                GuardianCallMode.valueOf(it.optString("friendsWhatsApp", GuardianCallMode.ALLOW.name)),
            )
        }
    }

    fun phoneShieldSanctuaryModes(): Set<String> = synchronized(lock) {
        val modes = phoneShieldArchive().read().firstOrNull()?.optJSONArray("sanctuaryModes")
            ?: return@synchronized emptySet()
        List(modes.length(), modes::getString).toSet()
    }

    fun savePhoneShieldSanctuaryModes(modes: Set<String>) = synchronized(lock) {
        val archive = phoneShieldArchive()
        val rows = archive.read()
        val settings = rows.firstOrNull() ?: JSONObject().put("enabled", false)
        settings.put("sanctuaryModes", JSONArray(modes.sorted()))
        archive.write(listOf(settings) + rows.drop(1))
    }

    fun savePhoneShieldSettings(enabled: Boolean, rules: List<GuardianPhoneRule>) = synchronized(lock) {
        require(rules.size <= 100 && rules.distinctBy { it.number }.size == rules.size)
        require(rules.all { guardianPhoneNumber(it.number) == it.number })
        val archive = phoneShieldArchive()
        val settings = archive.read().firstOrNull() ?: JSONObject()
        archive.write(listOf(settings.put("enabled", enabled)) + rules.map {
            JSONObject().put("number", it.number).put("mode", it.mode.name)
                .put("mainWhatsApp", it.mainWhatsApp.name).put("friendsWhatsApp", it.friendsWhatsApp.name)
        })
    }

    private fun validationArchive() = GuardianCaptureStore(context, training = false, validation = true)

    private fun validationModel(examples: List<GuardianExample>): String {
        val entries = examples.map {
            JSONArray().put(it.assessment.name).put(JSONArray(it.features.sorted())).toString()
        }.sorted()
        return fingerprint(JSONArray().put(1).put(JSONArray(entries)).toString().toByteArray(Charsets.UTF_8))
    }

    fun knowledgeValidation(): NovaBrainValidation? = synchronized(lock) {
        val result = validationArchive().read().singleOrNull() ?: return@synchronized null
        val rows = retainedRows(GuardianCapture.retention(context), System.currentTimeMillis())
        if (result.getString("model") != validationModel(examples(rows))) return@synchronized null
        NovaBrainValidation(
            result.getInt("conversations"), result.getInt("upsetting"), result.getInt("ordinary"),
            result.getDouble("precision"), result.getDouble("recall"), result.getDouble("patternRecall"),
            result.getDouble("calibration"), result.getDouble("coverage"), result.getDouble("improvement"),
        )
    }

    fun measureKnowledge(holdout: List<NovaBrainValidationMessage>): NovaBrainValidation? = synchronized(lock) {
        val rows = retainedRows(GuardianCapture.retention(context), System.currentTimeMillis())
        val learned = examples(rows)
        val conversations = rows.asSequence().filterNot { it.optBoolean("deleted") }
            .filter { ownerLabel(it) != null }.map {
                JSONArray().put(it.optString("app", GUARDIAN_WHATSAPP))
                    .put(it.getLong("profile")).put(it.getString("conversation")).toString()
            }.toSet()
        val result = evaluateNovaBrain(holdout, conversations, learned.map { it.features }.toSet())
            ?: return@synchronized null
        validationArchive().write(listOf(JSONObject()
            .put("model", validationModel(learned))
            .put("conversations", result.conversations).put("upsetting", result.upsetting).put("ordinary", result.ordinary)
            .put("precision", result.precision).put("recall", result.recall).put("patternRecall", result.patternRecall)
            .put("calibration", result.calibration).put("coverage", result.confirmedPatternCoverage)
            .put("improvement", result.validatedImprovement)))
        result
    }

    private fun contactArchive() = GuardianCaptureStore(context, training = false, contacts = true)

    fun monitoredContacts(): List<GuardianContact> = synchronized(lock) {
        contactArchive().read().map {
            GuardianContact(GuardianConversationKey(it.getString("app"), it.getLong("profile"), it.getString("conversation")),
                it.getString("name"), it.getBoolean("enabled"), it.getBoolean("retain"),
                GuardianSensitivity.valueOf(it.getString("sensitivity")), it.optBoolean("holdUncertain"), it.optBoolean("quietCollection"))
        }
    }

    fun saveContact(key: GuardianConversationKey, contact: GuardianContact?) = synchronized(lock) {
        require(contact == null || contact.key == key && key.profile >= 0 && key.conversation.length in 1..512 &&
            key.app.length in 1..200 && contact.name.length in 1..100)
        val retained = monitoredContacts().filterNot { it.key == key } + listOfNotNull(contact)
        require(retained.size <= 100)
        contactArchive().write(retained.map {
            JSONObject().put("app", it.key.app).put("profile", it.key.profile).put("conversation", it.key.conversation)
                .put("name", it.name).put("enabled", it.enabled).put("retain", it.retain).put("sensitivity", it.sensitivity.name).put("holdUncertain", it.holdUncertain)
                .put("quietCollection", it.quietCollection)
        })
    }

    fun searchInbox(query: String, days: Int, now: GuardianInboxClock): Set<String> = synchronized(lock) {
        retainedRows(days, now.wall).asSequence().filterNot { it.optBoolean("deleted") }.filter { row ->
            val metadata = inboxRow(row, now)
            metadata.section != GuardianInboxSection.COOLING && metadata.assessment != null &&
                row.getString("text").contains(query, ignoreCase = true)
        }.map { it.getString("id") }.toSet()
    }

    fun verifyFixture(): Boolean = synchronized(lock) {
        val receipt = GuardianCaptureStore(context, training = false, fixture = true)
        val now = System.currentTimeMillis()
        val text = "Harmless Nova Guardian diagnostic"
        val event = GuardianCaptureEvent(0L, "diagnostic", now,
            listOf(GuardianCaptureEntry(now, "fixture", text)))
        try {
            receipt.clear()
            val stored = receipt.append(event, 7, now, assess = false)
            stored.allDurable && receipt.read().singleOrNull()?.optString("text") == text
        } finally {
            receipt.clear()
        }
    }

    fun teach(text: String, assessment: GuardianAssessment, evidenceId: String? = null): Boolean = synchronized(lock) {
        require(evidenceId == null || evidenceId.isNotBlank())
        val features = guardianFeatures(text)
        if (features.isEmpty()) return@synchronized false
        val examples = GuardianCaptureStore(context, true)
        val id = fingerprint(JSONArray(features.sorted()).toString().toByteArray(Charsets.UTF_8))
        val rows = examples.read()
        val previous = rows.firstOrNull { it.getString("id") == id }?.optJSONArray("evidenceSources")
        val sources = if (previous == null) emptyList() else List(previous.length(), previous::getString)
        val updatedSources = (listOfNotNull(evidenceId) + sources).distinct().take(MAX_EXAMPLES)
        val saved = rows.filterNot { it.getString("id") == id }
        val row = JSONObject().put("id", id).put("features", JSONArray(features.sorted())).put("label", assessment.name)
        if (updatedSources.isNotEmpty()) row.put("evidenceSources", JSONArray(updatedSources))
        examples.write((listOf(row) + saved).take(MAX_EXAMPLES))
        true
    }

    fun resetLearning() = synchronized(lock) {
        val rows = read()
        if (rows.count { it.remove("ownerLabel") != null } > 0) write(rows)
        GuardianCaptureStore(context, true).clear()
        validationArchive().clear()
    }

    fun learningCount(): Int = synchronized(lock) { examples(read()).size }

    fun forgetExample(text: String): Boolean = synchronized(lock) {
        val features = guardianFeatures(text)
        if (features.isEmpty()) return@synchronized false
        val id = fingerprint(JSONArray(features.sorted()).toString().toByteArray(Charsets.UTF_8))
        val archive = GuardianCaptureStore(context, true)
        val original = archive.read()
        val revised = original.filterNot { it.getString("id") == id }
        if (revised.size == original.size) return@synchronized false
        archive.write(revised)
        true
    }

    private fun ownerLabel(row: JSONObject): GuardianAssessment? = when (row.optString("ownerLabel")) {
        GuardianAssessment.LOW_RISK.name -> GuardianAssessment.LOW_RISK
        GuardianAssessment.UPSETTING.name -> GuardianAssessment.UPSETTING
        else -> null
    }

    private fun examples(rows: List<JSONObject>): List<GuardianExample> {
        val evidence = GuardianCaptureStore(context, true).read().map { row ->
            val features = row.getJSONArray("features")
            GuardianExample(List(features.length(), features::getString).toSet(),
                GuardianAssessment.valueOf(row.getString("label")))
        }
        val conversation = rows.asSequence().filterNot { it.optBoolean("deleted") }.mapNotNull { row ->
            val label = ownerLabel(row) ?: return@mapNotNull null
            val features = guardianFeatures(row.getString("text"))
            if (features.isEmpty()) null else GuardianExample(features, label)
        }.take(MAX_EXAMPLES).toList()
        return (evidence + conversation).distinct()
    }

    fun labelConversationMessage(id: String, label: GuardianAssessment?, days: Int, now: GuardianInboxClock): Boolean =
        synchronized(lock) {
            require(label == null || label == GuardianAssessment.LOW_RISK || label == GuardianAssessment.UPSETTING)
            val rows = retainedRows(days, now.wall)
            val row = rows.firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") } ?: return@synchronized false
            val state = inboxRow(row, now)
            if (state.assessment == null || state.section == GuardianInboxSection.COOLING) return@synchronized false
            if (label == null) row.remove("ownerLabel") else row.put("ownerLabel", label.name)
            write(rows)
            true
        }

    fun assessInboxMessage(id: String, days: Int, now: GuardianInboxClock): GuardianAssessment? = synchronized(lock) {
        val rows = retainedRows(days, now.wall)
        val row = rows.firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") }
            ?: return@synchronized null
        if (inboxRow(row, now).section == GuardianInboxSection.COOLING) return@synchronized null
        val assessment = classifyGuardian(row.getString("text"), examples(rows), contextFor(row, rows), rowSensitivity(row))
        applyAssessment(row, assessment, now)
        write(rows)
        assessment
    }

    fun append(event: GuardianCaptureEvent, days: Int, now: Long, assess: Boolean = true, clock: GuardianInboxClock = GuardianInboxClock(now, 0L, -1)): GuardianCaptureResult = synchronized(lock) {
        require(event.profileSerial >= 0 && event.conversation.length in 1..512 && event.observedAt <= now)
        val cutoff = cutoff(days, now)
        val stored = read()
        val live = stored.filter { isRetained(it, cutoff) }
        val existing = live.associateBy { it.getString("id") }
        val candidates = event.entries.filter {
            it.time in cutoff..event.observedAt && it.sender.length in 1..512 &&
                it.sender.isNotBlank() && it.text.length in 1..2048 && it.text.isNotBlank()
        }
        val examples by lazy { runCatching { examples(live) }.getOrDefault(emptyList()) }
        val incoming = LinkedHashMap<String, JSONObject>()
        for (entry in candidates.sortedBy { it.time }) {
            val id = identity(event, entry)
            val row = JSONObject().put("id", id).put("profile", event.profileSerial)
                .put("conversation", event.conversation).put("sender", entry.sender)
                .put("app", event.app).put("sensitivity", event.sensitivity.name).put("holdUncertain", event.holdUncertain)
                .put("messageAt", entry.time).put("capturedAt", event.observedAt).put("text", entry.text)
            guardianSenderDisplayName(entry.displayName)?.let { row.put("displayName", it) }
            if (assess && id !in existing && id !in incoming && examples.isNotEmpty()) {
                val assessment = classifyGuardian(entry.text, examples, contextFor(row, live + incoming.values), event.sensitivity)
                applyAssessment(row, assessment, clock)
            }
            incoming.putIfAbsent(id, row)
        }
        val retained = (existing + incoming.filterKeys { it !in existing }).values
            .sortedByDescending { it.getLong("messageAt") }.take(MAX_RECORDS)
        val ids = retained.mapTo(HashSet()) { it.getString("id") }
        val added = incoming.keys.count { it in ids && it !in existing }
        if (added > 0 || stored.size != live.size) write(retained)
        val readable = retained.filterNot { it.optBoolean("deleted") }.mapTo(HashSet()) { it.getString("id") }
        val partial = candidates.size != event.entries.size
        GuardianCaptureResult(added, readable.size, partial,
            added > 0 && !partial && incoming.keys.all { it in readable })
    }

    fun assessCaptured(event: GuardianCaptureEvent, days: Int, now: GuardianInboxClock) = synchronized(lock) {
        val rows = retainedRows(days, now.wall)
        val learned = examples(rows)
        if (learned.isEmpty()) return@synchronized
        val ids = event.entries.mapTo(HashSet()) { identity(event, it) }
        var changed = false
        for (row in rows) {
            if (row.getString("id") !in ids || row.optBoolean("deleted") || row.has("assessment") ||
                inboxRow(row, now).section == GuardianInboxSection.COOLING) continue
            val assessment = classifyGuardian(row.getString("text"), learned, contextFor(row, rows), rowSensitivity(row))
            applyAssessment(row, assessment, now)
            changed = true
        }
        if (changed) write(rows)
    }

    fun summary(days: Int, now: Long): Int = synchronized(lock) { retainedRows(days, now).count { !it.optBoolean("deleted") } }

    fun inboxCounts(days: Int, now: GuardianInboxClock): GuardianInboxCounts = synchronized(lock) {
        val rows = retainedRows(days, now.wall).filterNot { it.optBoolean("deleted") }.map { inboxRow(it, now) }
        val sections = rows.groupingBy { it.section }.eachCount()
        GuardianInboxCounts(
            sections[GuardianInboxSection.PENDING] ?: 0, sections[GuardianInboxSection.READY] ?: 0,
            sections[GuardianInboxSection.COOLING] ?: 0, sections[GuardianInboxSection.PROTECTED] ?: 0,
            sections[GuardianInboxSection.HISTORY] ?: 0,
            rows.asSequence().map { it.remainingMillis }.filter { it > 0 }.minOrNull(),
        )
    }

    fun inboxRows(days: Int, now: GuardianInboxClock): List<GuardianInboxRow> = synchronized(lock) {
        retainedRows(days, now.wall).filterNot { it.optBoolean("deleted") }.map { inboxRow(it, now) }
    }

    fun revealInboxMessage(id: String, days: Int, now: GuardianInboxClock): String? = synchronized(lock) {
        val row = retainedRows(days, now.wall).firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") } ?: return@synchronized null
        if (inboxRow(row, now).section == GuardianInboxSection.COOLING) null else row.getString("text")
    }

    fun prepareInboxReview(id: String, days: Int, now: GuardianInboxClock): Pair<GuardianInboxRow, String>? = synchronized(lock) {
        val rows = retainedRows(days, now.wall)
        val stored = rows.firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") } ?: return@synchronized null
        val initial = inboxRow(stored, now)
        if (initial.section == GuardianInboxSection.COOLING) return@synchronized null
        if (initial.assessment == null) {
            val assessment = classifyGuardian(stored.getString("text"), examples(rows), contextFor(stored, rows), rowSensitivity(stored))
            applyAssessment(stored, assessment, now)
            write(rows)
        }
        val revised = inboxRow(stored, now)
        if (revised.section == GuardianInboxSection.COOLING) null else revised to stored.getString("text")
    }

    fun eligibleEvidenceMessage(id: String, days: Int, now: GuardianInboxClock): String? = synchronized(lock) {
        val row = retainedRows(days, now.wall).firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") }
            ?: return@synchronized null
        val metadata = inboxRow(row, now)
        if (metadata.section == GuardianInboxSection.COOLING || metadata.assessment == null) null else row.getString("text")
    }

    fun reviewInboxMessage(id: String, action: GuardianInboxAction, days: Int, now: GuardianInboxClock): Boolean =
        synchronized(lock) {
            val rows = retainedRows(days, now.wall)
            val row = rows.firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") } ?: return@synchronized false
            if (inboxRow(row, now).section == GuardianInboxSection.COOLING) return@synchronized false
            when (action) {
                GuardianInboxAction.READY -> row.remove("hold").also { row.put("review", "ready").put("holdReviewed", true) }
                GuardianInboxAction.HOLD -> startHold(row, now)
                GuardianInboxAction.ARCHIVE -> row.remove("hold").also { row.put("review", "archived").put("holdReviewed", true) }
                GuardianInboxAction.DELETE -> {
                    val keep = setOf("id", "capturedAt", "messageAt")
                    row.keys().asSequence().filterNot { it in keep }.toList().forEach { row.remove(it) }
                    row.put("deleted", true)
                }
            }
            write(rows)
            true
        }

    fun usesConversationContext() = context.getSharedPreferences("nova_guardian_capture", Context.MODE_PRIVATE)
        .getBoolean("use_local_context", true)

    fun setConversationContext(enabled: Boolean) {
        check(context.getSharedPreferences("nova_guardian_capture", Context.MODE_PRIVATE)
            .edit().putBoolean("use_local_context", enabled).commit())
    }

    private fun applyAssessment(row: JSONObject, assessment: GuardianAssessment, now: GuardianInboxClock) {
        val patterns = recogniseNovaBrainPatterns(row.getString("text"))
        row.put("patterns", JSONArray(patterns.map { it.name }))
        row.put("assessment", assessment.name).put("assessmentVersion", 1)
        if (!row.has("hold") && (assessment == GuardianAssessment.UPSETTING ||
                assessment == GuardianAssessment.UNCERTAIN && row.optBoolean("holdUncertain"))) startHold(row, now)
    }

    private fun startHold(row: JSONObject, now: GuardianInboxClock) {
        row.put("hold", JSONObject().put("wall", now.wall + GUARDIAN_HOLD_MILLIS)
            .put("elapsed", now.elapsed + GUARDIAN_HOLD_MILLIS).put("boot", now.boot))
    }

    private fun rowSensitivity(row: JSONObject): GuardianSensitivity =
        GuardianSensitivity.entries.firstOrNull { it.name == row.optString("sensitivity") } ?: GuardianSensitivity.STANDARD

    private fun contextFor(row: JSONObject, rows: Collection<JSONObject>): List<String> {
        if (!usesConversationContext()) return emptyList()
        val time = row.getLong("messageAt")
        return rows.asSequence().filter {
            !it.optBoolean("deleted") && it.getLong("profile") == row.getLong("profile") &&
                it.optString("app", GUARDIAN_WHATSAPP) == row.optString("app", GUARDIAN_WHATSAPP) &&
                it.getString("conversation") == row.getString("conversation") &&
                it.getString("sender") == row.getString("sender") && it.getLong("messageAt") in time - 1_800_000L until time
        }.sortedByDescending { it.getLong("messageAt") }.take(3).map { it.getString("text") }.toList().asReversed()
    }

    private fun retainedRows(days: Int, now: Long): List<JSONObject> {
        val stored = read()
        val cutoff = cutoff(days, now)
        val live = stored.filter { isRetained(it, cutoff) }
        if (stored.size != live.size) write(live)
        return live
    }

    private fun inboxRow(row: JSONObject, now: GuardianInboxClock): GuardianInboxRow {
        val assessment = if (row.optInt("assessmentVersion") == 1)
            GuardianAssessment.entries.firstOrNull { it.name == row.optString("assessment") } else null
        val review = row.optString("review")
        val hold = row.optJSONObject("hold")?.let {
            GuardianInboxHold(it.getLong("wall"), it.getLong("elapsed"), it.getInt("boot"))
        } ?: if (assessment == GuardianAssessment.UPSETTING && !row.optBoolean("holdReviewed")) {
            GuardianInboxHold(row.getLong("capturedAt") + GUARDIAN_HOLD_MILLIS, 0L, -1)
        } else null
        val remaining = hold?.remaining(now) ?: 0L
        val section = guardianInboxSection(hold, review == "ready" || assessment == GuardianAssessment.LOW_RISK,
            now, review == "archived")
        return GuardianInboxRow(row.getString("id"), row.getLong("messageAt"),
            if (section == GuardianInboxSection.PENDING && assessment == GuardianAssessment.UNCERTAIN)
                GuardianInboxSection.PROTECTED else section, remaining, assessment,
            guardianSenderDisplayName(row.optString("displayName")), row.getLong("capturedAt"),
            GuardianConversationKey(row.optString("app", GUARDIAN_WHATSAPP), row.getLong("profile"), row.getString("conversation")),
            if (remaining > 0) now.wall + remaining else null, ownerLabel(row),
            row.optJSONArray("patterns")?.let { stored ->
                (0 until stored.length()).mapNotNull { index ->
                    NovaBrainPattern.entries.firstOrNull { it.name == stored.optString(index) }
                }.toSet()
            })
    }

    fun clear() = synchronized(lock) {
        keyStore().deleteEntry(alias)
        atomic.delete()
        check(!destination.exists())
        if (!fixture && !contacts && !phoneShield && !callHistory) knowledgeRevision.value += 1
    }

    private fun cutoff(days: Int, now: Long): Long {
        require(days in RETENTION_DAYS)
        return now - days * 86_400_000L
    }

    private fun isRetained(row: JSONObject, cutoff: Long) =
        row.getLong("capturedAt") >= cutoff && row.getLong("messageAt") >= cutoff

    private fun identity(event: GuardianCaptureEvent, entry: GuardianCaptureEntry): String {
        val values = JSONArray().put(event.profileSerial).put(event.conversation).put(entry.time).put(entry.sender).put(entry.text)
        if (event.app != GUARDIAN_WHATSAPP) values.put(event.app)
        val fields = values.toString().toByteArray(Charsets.UTF_8)
        return fingerprint(fields)
    }

    private fun fingerprint(fields: ByteArray): String = try {
        java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fields))
    } finally {
        fields.fill(0)
    }

    private fun read(): List<JSONObject> {
        val blob = readBlob() ?: return emptyList()
        require(blob.size >= 1 + IV_BYTES + TAG_BYTES && blob[0] == VERSION)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, blob, 1, IV_BYTES))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
        return try {
            val rows = JSONArray(plain.toString(Charsets.UTF_8))
            require(rows.length() <= MAX_RECORDS)
            List(rows.length(), rows::getJSONObject)
        } finally {
            plain.fill(0)
        }
    }

    private fun write(rows: List<JSONObject>) {
        val plain = JSONArray(rows).toString().toByteArray(Charsets.UTF_8)
        val blob = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            check(cipher.iv.size == IV_BYTES)
            cipher.updateAAD(aad)
            byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plain)
        } finally {
            plain.fill(0)
        }
        require(blob.size <= MAX_BYTES)
        val output = atomic.startWrite()
        try {
            output.write(blob)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        check(readBlob()?.contentEquals(blob) == true)
        if (!fixture && !contacts && !phoneShield && !callHistory) knowledgeRevision.value += 1
    }

    private fun readBlob(): ByteArray? = try {
        atomic.openRead().use { it.readNBytes(MAX_BYTES + 1).also { bytes -> require(bytes.size <= MAX_BYTES) } }
    } catch (missing: FileNotFoundException) {
        if (destination.exists() || File(destination.path + ".bak").exists()) throw missing
        null
    }

    private fun key(create: Boolean): SecretKey {
        val store = keyStore()
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        check(create && !destination.exists())
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        const val ARCHIVE_NAME = "guardian-notifications-v1.enc"
        val RETENTION_DAYS = setOf(1, 7, 30)
        private const val KEY_ALIAS = "nova-guardian-capture-v1"
        private const val VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val MAX_RECORDS = 256
        private const val MAX_EXAMPLES = 128
        private const val MAX_BYTES = 6 * 1024 * 1024
        private val AAD = "NovaOS Guardian capture v1".toByteArray(Charsets.UTF_8)
        private val lock = Any()
        private val knowledgeRevision = MutableStateFlow(0L)
        val knowledgeChanges = knowledgeRevision.asStateFlow()
    }
}
