package uk.co.traynor.privategallery.core.guardian

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
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

internal data class GuardianCaptureEntry(val time: Long, val sender: String, val text: String)

internal data class GuardianCaptureEvent(
    val profileSerial: Long,
    val conversation: String,
    val observedAt: Long,
    val entries: List<GuardianCaptureEntry>,
)

internal data class GuardianCaptureResult(val added: Int, val retained: Int, val partial: Boolean)

internal data class GuardianInboxRow(
    val id: String, val time: Long, val section: GuardianInboxSection, val remainingMillis: Long,
    val assessment: GuardianAssessment? = null,
)

internal enum class GuardianInboxAction { READY, HOLD, ARCHIVE, DELETE }

internal fun guardianInboxClock(context: Context) = GuardianInboxClock(
    System.currentTimeMillis(), SystemClock.elapsedRealtime(),
    runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1),
)

/** Private, bounded archive. The capture phase exposes aggregate counts only. */
internal class GuardianCaptureStore private constructor(private val context: Context, private val training: Boolean) {
    constructor(context: Context) : this(context.applicationContext, false)

    private val destination = File(context.noBackupFilesDir, if (training) "guardian-learning-v1.enc" else ARCHIVE_NAME)
    private val alias = if (training) "nova-guardian-learning-v1" else KEY_ALIAS
    private val aad = if (training) "NovaOS Guardian learning v1".toByteArray(Charsets.UTF_8) else AAD
    private val atomic = AtomicFile(destination)

    fun teach(text: String, assessment: GuardianAssessment): Boolean = synchronized(lock) {
        val features = guardianFeatures(text)
        if (features.isEmpty()) return@synchronized false
        val examples = GuardianCaptureStore(context, true)
        val id = fingerprint(JSONArray(features.sorted()).toString().toByteArray(Charsets.UTF_8))
        val saved = examples.read().filterNot { it.getString("id") == id }
        val row = JSONObject().put("id", id).put("features", JSONArray(features.sorted())).put("label", assessment.name)
        examples.write((listOf(row) + saved).take(MAX_EXAMPLES))
        true
    }

    fun resetLearning() = GuardianCaptureStore(context, true).clear()

    fun learningCount(): Int = synchronized(lock) { examples().size }

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

    private fun examples(): List<GuardianExample> = GuardianCaptureStore(context, true).read().map { row ->
        val features = row.getJSONArray("features")
        GuardianExample(List(features.length(), features::getString).toSet(),
            GuardianAssessment.valueOf(row.getString("label")))
    }

    fun assessInboxMessage(id: String, days: Int, now: GuardianInboxClock): GuardianAssessment? = synchronized(lock) {
        val rows = retainedRows(days, now.wall)
        val row = rows.firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") }
            ?: return@synchronized null
        if (inboxRow(row, now).section == GuardianInboxSection.COOLING) return@synchronized null
        val assessment = classifyGuardian(row.getString("text"), examples(), contextFor(row, rows))
        row.put("assessment", assessment.name).put("assessmentVersion", 1)
        write(rows)
        assessment
    }

    fun append(event: GuardianCaptureEvent, days: Int, now: Long): GuardianCaptureResult = synchronized(lock) {
        require(event.profileSerial >= 0 && event.conversation.length in 1..512 && event.observedAt <= now)
        val cutoff = cutoff(days, now)
        val stored = read()
        val live = stored.filter { isRetained(it, cutoff) }
        val existing = live.associateBy { it.getString("id") }
        val candidates = event.entries.filter {
            it.time in cutoff..event.observedAt && it.sender.length in 1..512 &&
                it.sender.isNotBlank() && it.text.length in 1..2048 && it.text.isNotBlank()
        }
        val examples by lazy { runCatching { examples() }.getOrDefault(emptyList()) }
        val incoming = LinkedHashMap<String, JSONObject>()
        for (entry in candidates.sortedBy { it.time }) {
            val id = identity(event, entry)
            val row = JSONObject().put("id", id).put("profile", event.profileSerial)
                .put("conversation", event.conversation).put("sender", entry.sender)
                .put("messageAt", entry.time).put("capturedAt", event.observedAt).put("text", entry.text)
            if (id !in existing && id !in incoming && examples.isNotEmpty()) {
                val assessment = classifyGuardian(entry.text, examples, contextFor(row, live + incoming.values))
                row.put("assessment", assessment.name).put("assessmentVersion", 1)
            }
            incoming.putIfAbsent(id, row)
        }
        val retained = (existing + incoming.filterKeys { it !in existing }).values
            .sortedByDescending { it.getLong("messageAt") }.take(MAX_RECORDS)
        val ids = retained.mapTo(HashSet()) { it.getString("id") }
        val added = incoming.keys.count { it in ids && it !in existing }
        if (added > 0 || stored.size != live.size) write(retained)
        GuardianCaptureResult(added, retained.count { !it.optBoolean("deleted") }, candidates.size != event.entries.size)
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

    fun reviewInboxMessage(id: String, action: GuardianInboxAction, days: Int, now: GuardianInboxClock): Boolean =
        synchronized(lock) {
            val rows = retainedRows(days, now.wall)
            val row = rows.firstOrNull { it.getString("id") == id && !it.optBoolean("deleted") } ?: return@synchronized false
            if (inboxRow(row, now).section == GuardianInboxSection.COOLING) return@synchronized false
            when (action) {
                GuardianInboxAction.READY -> row.remove("hold").also { row.put("review", "ready") }
                GuardianInboxAction.HOLD -> row.put("hold", JSONObject()
                    .put("wall", now.wall + GUARDIAN_HOLD_MILLIS)
                    .put("elapsed", now.elapsed + GUARDIAN_HOLD_MILLIS).put("boot", now.boot))
                GuardianInboxAction.ARCHIVE -> row.remove("hold").also { row.put("review", "archived") }
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

    private fun contextFor(row: JSONObject, rows: Collection<JSONObject>): List<String> {
        if (!usesConversationContext()) return emptyList()
        val time = row.getLong("messageAt")
        return rows.asSequence().filter {
            !it.optBoolean("deleted") && it.getLong("profile") == row.getLong("profile") &&
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
        val hold = row.optJSONObject("hold")?.let {
            GuardianInboxHold(it.getLong("wall"), it.getLong("elapsed"), it.getInt("boot"))
        }
        val review = row.optString("review")
        return GuardianInboxRow(row.getString("id"), row.getLong("messageAt"),
            guardianInboxSection(hold, review == "ready", now, review == "archived"), hold?.remaining(now) ?: 0L,
            if (row.optInt("assessmentVersion") == 1)
                GuardianAssessment.entries.firstOrNull { it.name == row.optString("assessment") } else null)
    }

    fun clear() = synchronized(lock) {
        keyStore().deleteEntry(alias)
        atomic.delete()
        check(!destination.exists())
    }

    private fun cutoff(days: Int, now: Long): Long {
        require(days in RETENTION_DAYS)
        return now - days * 86_400_000L
    }

    private fun isRetained(row: JSONObject, cutoff: Long) =
        row.getLong("capturedAt") >= cutoff && row.getLong("messageAt") >= cutoff

    private fun identity(event: GuardianCaptureEvent, entry: GuardianCaptureEntry): String {
        val fields = JSONArray().put(event.profileSerial).put(event.conversation)
            .put(entry.time).put(entry.sender).put(entry.text).toString().toByteArray(Charsets.UTF_8)
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
    }
}
