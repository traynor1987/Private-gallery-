package uk.co.traynor.privategallery.core.sanctuary

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.SpaceContext

enum class SanctuaryMode(val label: String, val defaultMinutes: Int) {
    GAMING("Gaming Mode", 120),
    LEARNING("Learning Mode", 60),
    SELF_CARE("Self Care Mode", 90),
    WORK("Work Mode", 240),
    PANIC("Panic Mode", 720);

    internal fun defaultPreset(): SanctuaryPreset {
        val media = setOf("com.google.android.youtube", "com.google.android.apps.youtube.music")
        val work = setOf("com.google.android.apps.maps", "com.ubercab.driver")
        val androidApps = when (this) {
            GAMING, SELF_CARE -> media
            LEARNING -> setOf("com.android.chrome", "com.google.android.youtube")
            WORK -> work
            PANIC -> media + work
        }
        val novaApps = when (this) {
            GAMING -> setOf("arcade")
            LEARNING -> setOf("browser-v1", "mind", "nova-calculator")
            SELF_CARE -> setOf("mind", "arcade")
            WORK -> setOf("nova-calculator")
            PANIC -> setOf("mind", "arcade", "nova-calculator")
        }
        return SanctuaryPreset(defaultMinutes, immutableSet(androidApps), immutableSet(novaApps))
    }
}

data class SanctuaryPreset(
    val minutes: Int,
    val androidApps: Set<String>,
    val novaApps: Set<String>,
    val enableDnd: Boolean = false,
    val offerPinning: Boolean = false,
    val enableGuardian: Boolean = true,
    val allowExternalApps: Boolean = true,
    val blockedNovaApps: Set<String> = emptySet(),
    val allowWhatsApp: Boolean = false,
)

data class SanctuarySession(
    val id: String,
    val mode: SanctuaryMode,
    val startedAt: Long,
    val expiresAt: Long,
    val androidApps: Set<String>,
    val novaApps: Set<String>,
    val extensions: Int,
    internal val elapsedExpiry: Long = 0,
    internal val bootCount: Int = -1,
    val enableDnd: Boolean = false,
    val offerPinning: Boolean = false,
    val enableGuardian: Boolean = true,
    val allowExternalApps: Boolean = true,
    val blockedNovaApps: Set<String> = emptySet(),
    val allowWhatsApp: Boolean = false,
) {
    fun allowsNovaApp(id: String, url: String? = null): Boolean =
        sanctuaryAllowsInternalApp(blockedNovaApps, allowWhatsApp, id, url)
}

enum class SanctuaryOutcome { EXPIRED, ENDED }

data class SanctuaryHistoryEntry(
    val mode: SanctuaryMode,
    val startedAt: Long,
    val endedAt: Long,
    val extensions: Int,
    val outcome: SanctuaryOutcome,
)

internal data class SanctuaryApprovalChange(
    val mode: SanctuaryMode,
    val at: Long,
    val packageName: String,
    val approved: Boolean,
)

internal data class SanctuaryTime(val wall: Long, val elapsed: Long, val boot: Int)
internal data class SanctuarySnapshot(
    val presets: Map<SanctuaryMode, SanctuaryPreset> = emptyMap(),
    val session: SanctuarySession? = null,
    val history: List<SanctuaryHistoryEntry> = emptyList(),
    val approvals: List<SanctuaryApprovalChange> = emptyList(),
)

internal interface SanctuaryPersistence {
    fun read(): SanctuarySnapshot?
    fun write(value: SanctuarySnapshot): Boolean
}

/** One process-wide store for Primary Sanctuary. It never reads a Space store, Vault, contact or usage event. */
class SanctuaryStore internal constructor(
    private val persistence: SanctuaryPersistence,
    private val clock: () -> SanctuaryTime,
) {
    private var state = persistence.read() ?: SanctuarySnapshot()
    private val active = MutableStateFlow<SanctuarySession?>(null)
    val current: StateFlow<SanctuarySession?> = active.asStateFlow()

    init {
        refresh()
    }

    @Synchronized
    fun preset(mode: SanctuaryMode): SanctuaryPreset = state.presets[mode] ?: mode.defaultPreset()

    @Synchronized
    fun savePreset(mode: SanctuaryMode, preset: SanctuaryPreset): Boolean {
        if (!preset.isValid()) return false
        val copy = preset.copy(
            androidApps = immutableSet(preset.androidApps),
            novaApps = immutableSet(preset.novaApps),
            blockedNovaApps = immutableSet(preset.blockedNovaApps),
        )
        val changes = approvalChanges(mode, preset(mode).androidApps, copy.androidApps)
        return commit(state.copy(
            presets = state.presets + (mode to copy),
            approvals = changes,
        ))
    }

    /** An explicit Primary owner approval edit updates both this mode and its live Android policy. */
    @Synchronized
    internal fun saveAndroidApprovals(sessionId: String, mode: SanctuaryMode, packages: Set<String>): Boolean {
        if (!packages.validIds(packageId)) return false
        val live = refresh()?.takeIf { it.id == sessionId && it.mode == mode } ?: return false
        val selected = immutableSet(packages)
        val previous = preset(mode)
        if (selected == live.androidApps && selected == previous.androidApps) return true
        return commit(state.copy(
            session = live.copy(androidApps = selected),
            presets = state.presets + (mode to previous.copy(androidApps = selected)),
            approvals = approvalChanges(mode, live.androidApps, selected),
        ))
    }

    @Synchronized
    internal fun approvalHistory(): List<SanctuaryApprovalChange> = state.approvals.toList()

    private fun approvalChanges(
        mode: SanctuaryMode, previous: Set<String>, next: Set<String>,
    ): List<SanctuaryApprovalChange> {
        val now = clock().wall.coerceAtLeast(0L)
        val removed = (previous - next).sorted().map { SanctuaryApprovalChange(mode, now, it, false) }
        val added = (next - previous).sorted().map { SanctuaryApprovalChange(mode, now, it, true) }
        return (removed + added + state.approvals).take(APPROVAL_HISTORY_LIMIT)
    }

    @Synchronized
    fun start(mode: SanctuaryMode): Boolean = start(mode, foldTest = false)

    @Synchronized
    fun startFoldTest(mode: SanctuaryMode): Boolean = start(mode, foldTest = true)

    private fun start(mode: SanctuaryMode, foldTest: Boolean): Boolean {
        if (refresh() != null) return false
        // If the expiry transition could not be persisted, do not discard its history.
        if (state.session != null) return false
        val time = clock()
        val preset = preset(mode)
        val duration = (if (foldTest) 5 else preset.minutes) * MINUTE_MS
        val session = SanctuarySession(
            UUID.randomUUID().toString(), mode, time.wall, time.wall + duration,
            preset.androidApps, preset.novaApps, 0, time.elapsed + duration, time.boot,
            enableDnd = !foldTest && preset.enableDnd,
            offerPinning = !foldTest && preset.offerPinning,
            enableGuardian = foldTest || preset.enableGuardian,
            allowExternalApps = preset.allowExternalApps,
            blockedNovaApps = preset.blockedNovaApps,
            allowWhatsApp = preset.allowWhatsApp,
        )
        return commit(state.copy(session = session))
    }

    @Synchronized
    fun end(phrase: String): Boolean {
        if (phrase != END_PHRASE) return false
        val session = refresh() ?: return false
        return finish(session, clock().wall.coerceAtLeast(session.startedAt), SanctuaryOutcome.ENDED)
    }

    @Synchronized
    fun extend(hours: Int): Boolean {
        if (hours !in EXTENSIONS) return false
        val session = refresh() ?: return false
        if (session.mode != SanctuaryMode.PANIC) return false
        val time = clock()
        val remaining = remainingAt(session, time)
        val additional = hours * 60 * MINUTE_MS
        if (remaining <= 0 || remaining + additional > MAX_REMAINING_MS) return false
        return commit(state.copy(session = session.copy(
            expiresAt = session.expiresAt + additional,
            extensions = session.extensions + 1,
            elapsedExpiry = time.elapsed + remaining + additional,
            bootCount = time.boot,
        )))
    }

    fun remainingMillis(session: SanctuarySession): Long = remainingAt(session, clock())

    @Synchronized
    fun refresh(): SanctuarySession? {
        val session = state.session ?: return null
        val time = clock()
        val remaining = remainingAt(session, time)
        if (remaining <= 0) {
            // Display no active protection even if recording expiry temporarily fails.
            active.value = null
            finish(session, session.expiresAt, SanctuaryOutcome.EXPIRED)
            return null
        }
        if (time.boot >= 0 && time.boot != session.bootCount) {
            val rebased = session.copy(elapsedExpiry = time.elapsed + remaining, bootCount = time.boot)
            if (commit(state.copy(session = rebased))) return rebased
        }
        active.value = session
        return session
    }

    @Synchronized
    fun history(): List<SanctuaryHistoryEntry> = state.history.toList()

    private fun finish(session: SanctuarySession, time: Long, outcome: SanctuaryOutcome): Boolean {
        val record = SanctuaryHistoryEntry(session.mode, session.startedAt, time, session.extensions, outcome)
        return commit(state.copy(session = null, history = (listOf(record) + state.history).take(HISTORY_LIMIT)))
    }

    private fun commit(next: SanctuarySnapshot): Boolean {
        if (!persistence.write(next)) return false
        state = next
        active.value = next.session?.takeIf { remainingMillis(it) > 0 }
        return true
    }

    companion object {
        const val END_PHRASE = "I CHOOSE TO END SANCTUARY"
        val EXTENSIONS = immutableSet(setOf(1, 3, 6, 12))

        @Volatile private var instance: SanctuaryStore? = null

        fun get(context: Context): SanctuaryStore = instance ?: synchronized(this) {
            instance ?: run {
                var app = context.applicationContext ?: context
                // A SpaceContext deliberately returns itself as applicationContext.
                while (app is SpaceContext) app = app.baseContext.applicationContext ?: app.baseContext
                val boot = runCatching {
                    Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
                }.getOrDefault(-1)
                SanctuaryStore(FileSanctuaryPersistence(File(app.noBackupFilesDir, "nova-sanctuary-v1.json"))) {
                    SanctuaryTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
                }.also { instance = it }
            }
        }
    }
}

private const val MINUTE_MS = 60_000L
private const val MAX_REMAINING_MS = 7 * 24 * 60 * MINUTE_MS
private const val HISTORY_LIMIT = 30
private const val APPROVAL_HISTORY_LIMIT = 120
private const val MAX_IDS = 512
private const val MAX_RECORD_BYTES = 2 * 1024 * 1024
private val packageId = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private val novaId = Regex("[A-Za-z0-9_.:-]+")

private fun <T> immutableSet(values: Set<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))

private fun SanctuaryPreset.isValid(): Boolean =
    minutes in 1..1440 && androidApps.validIds(packageId) &&
        novaApps.validIds(novaId) && blockedNovaApps.validIds(novaId)

private fun Set<String>.validIds(pattern: Regex): Boolean =
    size <= MAX_IDS && all { it.length in 1..180 && pattern.matches(it) }

private fun remainingAt(session: SanctuarySession, now: SanctuaryTime): Long =
    if (now.boot >= 0 && now.boot == session.bootCount && session.elapsedExpiry > 0) {
        (session.elapsedExpiry - now.elapsed).coerceIn(0L, MAX_REMAINING_MS)
    } else {
        // Wall time is needed only when restoring across reboot or when boot identity is unavailable.
        val originalWindow = (session.expiresAt - session.startedAt).coerceIn(0L, MAX_REMAINING_MS)
        (session.expiresAt - now.wall).coerceIn(0L, originalWindow)
    }

private class FileSanctuaryPersistence(file: File) : SanctuaryPersistence {
    private val atomic = AtomicFile(file)

    override fun read(): SanctuarySnapshot? = runCatching {
        readBytes()?.let(SanctuaryCodec::decode)
    }.getOrNull()

    override fun write(value: SanctuarySnapshot): Boolean = runCatching {
        val bytes = SanctuaryCodec.encode(value)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: IOException) {
            atomic.failWrite(output)
            throw error
        }
        // AtomicFile cannot throw for every final rename failure.
        bytes.contentEquals(readBytes())
    }.getOrDefault(false)

    private fun readBytes(): ByteArray? = try {
        atomic.openRead().use { input ->
            input.readNBytes(MAX_RECORD_BYTES + 1).also { require(it.size <= MAX_RECORD_BYTES) }
        }
    } catch (_: FileNotFoundException) {
        null
    }
}

internal object SanctuaryCodec {
    fun encode(value: SanctuarySnapshot): ByteArray = JSONObject()
        .put("version", 1)
        .put("presets", JSONObject().apply {
            value.presets.forEach { (mode, preset) ->
                put(mode.name, JSONObject().put("minutes", preset.minutes)
                    .putApps(preset.androidApps, preset.novaApps, preset.blockedNovaApps)
                    .put("allowWhatsApp", preset.allowWhatsApp)
                    .put("enableDnd", preset.enableDnd).put("offerPinning", preset.offerPinning)
                    .put("enableGuardian", preset.enableGuardian).put("allowExternalApps", preset.allowExternalApps))
            }
        })
        .put("session", value.session?.let {
            JSONObject().put("id", it.id).put("mode", it.mode.name)
                .put("startedAt", it.startedAt).put("expiresAt", it.expiresAt)
                .putApps(it.androidApps, it.novaApps, it.blockedNovaApps).put("extensions", it.extensions)
                .put("allowWhatsApp", it.allowWhatsApp)
                .put("elapsedExpiry", it.elapsedExpiry).put("bootCount", it.bootCount)
                .put("enableDnd", it.enableDnd).put("offerPinning", it.offerPinning)
                .put("enableGuardian", it.enableGuardian).put("allowExternalApps", it.allowExternalApps)
        } ?: JSONObject.NULL)
        .put("history", JSONArray(value.history.map {
            JSONObject().put("mode", it.mode.name).put("startedAt", it.startedAt)
                .put("endedAt", it.endedAt).put("extensions", it.extensions).put("outcome", it.outcome.name)
        }))
        .apply {
            if (value.approvals.isNotEmpty()) put("approvals", JSONArray(value.approvals.map {
                JSONObject().put("mode", it.mode.name).put("at", it.at)
                    .put("package", it.packageName).put("approved", it.approved)
            }))
        }
        .toString().encodeToByteArray().also { require(it.size <= MAX_RECORD_BYTES) }

    fun decode(bytes: ByteArray): SanctuarySnapshot {
        require(bytes.size <= MAX_RECORD_BYTES)
        val root = JSONObject(bytes.decodeToString())
        require(root.getInt("version") == 1)
        val presets = root.getJSONObject("presets")
        return SanctuarySnapshot(
            presets.keys().asSequence().associate { key ->
                val mode = SanctuaryMode.valueOf(key)
                val preset = presets.getJSONObject(key)
                mode to SanctuaryPreset(
                    preset.getInt("minutes"), preset.apps("androidApps", packageId), preset.apps("novaApps", novaId),
                    enableDnd = preset.optBoolean("enableDnd", false),
                    offerPinning = preset.optBoolean("offerPinning", false),
                    enableGuardian = preset.optBoolean("enableGuardian", true),
                    allowExternalApps = preset.optBoolean("allowExternalApps", true),
                    blockedNovaApps = preset.blockedNovaApps(mode),
                    allowWhatsApp = preset.optBoolean("allowWhatsApp", false),
                ).also { require(it.isValid()) }
            },
            root.optJSONObject("session")?.let {
                val id = it.getString("id")
                require(UUID.fromString(id).toString() == id)
                val startedAt = it.getLong("startedAt")
                val expiresAt = it.getLong("expiresAt")
                val elapsedExpiry = it.getLong("elapsedExpiry")
                val extensions = it.getInt("extensions")
                val boot = it.getInt("bootCount")
                require(startedAt >= 0 && expiresAt > startedAt && elapsedExpiry > 0 && extensions >= 0 && boot >= -1)
                val mode = SanctuaryMode.valueOf(it.getString("mode"))
                SanctuarySession(
                    id, mode, startedAt, expiresAt,
                    it.apps("androidApps", packageId), it.apps("novaApps", novaId), extensions, elapsedExpiry, boot,
                    enableDnd = it.optBoolean("enableDnd", false),
                    offerPinning = it.optBoolean("offerPinning", false),
                    enableGuardian = it.optBoolean("enableGuardian", true),
                    allowExternalApps = it.optBoolean("allowExternalApps", true),
                    blockedNovaApps = it.blockedNovaApps(mode),
                    allowWhatsApp = it.optBoolean("allowWhatsApp", false),
                )
            },
            root.getJSONArray("history").let { history ->
                require(history.length() <= HISTORY_LIMIT)
                List(history.length()) { index ->
                    val item = history.getJSONObject(index)
                    SanctuaryHistoryEntry(
                        SanctuaryMode.valueOf(item.getString("mode")), item.getLong("startedAt"),
                        item.getLong("endedAt"), item.getInt("extensions"), SanctuaryOutcome.valueOf(item.getString("outcome")),
                    ).also { require(it.startedAt >= 0 && it.endedAt >= it.startedAt && it.extensions >= 0) }
                }
            },
            root.optJSONArray("approvals")?.let { approvals ->
                require(approvals.length() <= APPROVAL_HISTORY_LIMIT)
                List(approvals.length()) { index ->
                    val item = approvals.getJSONObject(index)
                    SanctuaryApprovalChange(
                        SanctuaryMode.valueOf(item.getString("mode")), item.getLong("at"),
                        item.getString("package"), item.getBoolean("approved"),
                    ).also { require(it.at >= 0 && it.packageName.length in 1..180 && packageId.matches(it.packageName)) }
                }
            }.orEmpty(),
        )
    }

    private fun JSONObject.putApps(
        androidApps: Set<String>, novaApps: Set<String>, blockedNovaApps: Set<String>,
    ): JSONObject = put("androidApps", JSONArray(androidApps.sorted()))
        .put("novaApps", JSONArray(novaApps.sorted()))
        .put("blockedNovaApps", JSONArray(blockedNovaApps.sorted()))

    private fun JSONObject.blockedNovaApps(mode: SanctuaryMode): Set<String> =
        if (has("blockedNovaApps")) apps("blockedNovaApps", novaId)
        else immutableSet(mode.defaultPreset().novaApps - apps("novaApps", novaId))

    private fun JSONObject.apps(key: String, pattern: Regex): Set<String> {
        val entries = getJSONArray(key)
        require(entries.length() <= MAX_IDS)
        return immutableSet((0 until entries.length()).map(entries::getString).toSet())
            .also { require(it.size == entries.length() && it.validIds(pattern)) }
    }
}


internal val sanctuaryEssentialNovaRoutes = setOf(
    "home", "sanctuary", "sanctuary-settings", "sanctuary-permissions", "settings", "home-settings",
    "native-settings", "shell-settings", "lock-settings", "lock-now", "panic", "close-browser",
)

internal fun sanctuaryAllowsInternalApp(
    blocked: Set<String>,
    allowWhatsApp: Boolean,
    destination: String,
    url: String? = null,
): Boolean {
    val route = destination.removePrefix("resume-internal:")
    if (!allowWhatsApp && (SanctuaryWhatsApp.isRelatedApp(route) ||
            url?.let(SanctuaryWhatsApp::isRelatedUrl) == true)
    ) return false
    if (route in sanctuaryEssentialNovaRoutes || route.startsWith("folder:")) return true
    if (route in blocked) return false
    val parent = when {
        route.startsWith("mind:") || route == "calm" -> "mind"
        route in setOf("arcade-2048", "arcade-sudoku", "arcade-snake") -> "arcade"
        route.startsWith("store:") -> "store"
        route.startsWith("widget:") -> "widgets"
        else -> null
    }
    return (parent == null || parent !in blocked) &&
        (!route.startsWith("store:") || route.removePrefix("store:") !in blocked)
}
