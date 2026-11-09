package uk.co.traynor.privategallery.launcher

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

/** Usage preferences and history are device-local and outside the portable layout store. */
internal class LauncherSmartTracking(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nova_launcher_smart_tracking", Context.MODE_PRIVATE)
    private val usage = appContext.getSystemService(UsageStatsManager::class.java)
    private val keyguard = appContext.getSystemService(KeyguardManager::class.java)
    private val updates = MutableStateFlow(read())
    val state = updates.asStateFlow()
    private var scanGeneration = 0L

    fun recordLaunch(app: LauncherApp) {
        if (app.packageName == appContext.packageName || isLocked()) return
        update { it.record(app.component, System.currentTimeMillis()) }
    }

    fun setMode(mode: LauncherTrackingMode) {
        if (mode == LauncherTrackingMode.EXTENDED) return
        prefs.edit().remove(CONSENT_PENDING).apply()
        if (mode != state.value.mode) {
            resetUsageWindow()
            update { it.copy(mode = mode) }
        }
    }

    /** Call only after the launcher disclosure is confirmed, even if Android access already exists. */
    fun beginExtendedConsent(): Intent? {
        if (SanctuaryGuardian.hasUsageAccess(appContext)) {
            enableExtended()
            return null
        }
        prefs.edit().putBoolean(CONSENT_PENDING, true).apply()
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun cancelExtendedConsent() {
        prefs.edit().remove(CONSENT_PENDING).apply()
    }

    /** Android Settings may return RESULT_CANCELED even when access was granted. */
    fun finishExtendedConsent(): Boolean {
        if (!prefs.getBoolean(CONSENT_PENDING, false)) return false
        prefs.edit().remove(CONSENT_PENDING).apply()
        if (!SanctuaryGuardian.hasUsageAccess(appContext)) return false
        enableExtended()
        return true
    }

    private fun enableExtended() {
        if (state.value.mode == LauncherTrackingMode.EXTENDED) return
        prefs.edit().remove(CONSENT_PENDING).putString(PREVIOUS_MODE, state.value.mode.name).apply()
        resetUsageWindow()
        update { it.copy(mode = LauncherTrackingMode.EXTENDED) }
    }

    fun setRecentLimit(limit: Int) {
        if (limit in LAUNCHER_RECENT_LIMITS) update { it.copy(recentLimit = limit) }
    }

    fun setShowHomeRecents(show: Boolean) = update { it.copy(showHomeRecents = show) }
    fun setShowSuggestions(show: Boolean) = update { it.copy(showSuggestions = show) }

    fun togglePin(component: String) = update {
        when (component) {
            in it.pinned -> it.copy(pinned = it.pinned - component)
            in it.excluded -> it
            else -> it.copy(pinned = it.pinned + component)
        }
    }

    fun toggleExclusion(component: String) {
        // Re-including an app must not re-import uses from the period when it was excluded.
        if (state.value.mode == LauncherTrackingMode.EXTENDED) resetUsageWindow()
        update { it.withExclusion(component) }
    }

    fun clearHistory() {
        resetUsageWindow()
        update { it.copy(history = emptyMap()) }
    }

    fun resetSuggestions() = update { it.copy(suggestionsResetAt = System.currentTimeMillis()) }

    /** Discard the protected interval and invalidate any query that is still in flight. */
    fun suspendExtended() {
        if (state.value.mode == LauncherTrackingMode.EXTENDED) resetUsageWindow(knownUnlocked = false)
    }

    /** Called at a launcher foreground boundary with currently eligible entries from this profile. */
    suspend fun refreshExtended(apps: List<LauncherApp>) {
        val current = state.value
        if (current.mode != LauncherTrackingMode.EXTENDED) return
        if (!SanctuaryGuardian.hasUsageAccess(appContext)) {
            val previous = trackingMode(prefs.getString(PREVIOUS_MODE, null))
            setMode(if (previous == LauncherTrackingMode.EXTENDED) LauncherTrackingMode.AUTOMATIC else previous)
            return
        }
        if (isLocked()) {
            suspendExtended()
            return
        }
        val protectionBoundary = protectionBoundary() ?: run {
            suspendExtended()
            return
        }
        val generation = ++scanGeneration
        val eligible = apps.asSequence().filter { it.profileSerial == null && it.packageName != appContext.packageName }
            .map(LauncherApp::component).filterNot { it in current.excluded }.toSet()
        if (eligible.isEmpty()) return
        val end = System.currentTimeMillis()
        val cutoff = prefs.getLong(USAGE_CUTOFF, end)
        val lastScan = prefs.getLong(LAST_SCAN, cutoff)
        if (end < maxOf(cutoff, lastScan, protectionBoundary)) {
            resetUsageWindow()
            return
        }
        val start = maxOf(cutoff, lastScan, protectionBoundary + 1, end - MAX_USAGE_LOOKBACK)
        if (start > end) return
        val knownUnlocked = start == lastScan && (prefs.contains(LAST_SCAN) || prefs.getBoolean(CUTOFF_UNLOCKED, false))
        val observed = withContext(Dispatchers.IO) { queryUsage(start, end, eligible, knownUnlocked) } ?: return
        if (generation != scanGeneration || state.value.mode != LauncherTrackingMode.EXTENDED) return
        if (isLocked() || protectionBoundary() != protectionBoundary) {
            suspendExtended()
            return
        }
        if (!SanctuaryGuardian.hasUsageAccess(appContext)) return
        prefs.edit().putLong(LAST_SCAN, end).apply()
        update { it.mergeHistory(observed) }
    }

    private fun queryUsage(start: Long, end: Long, eligible: Set<String>, knownUnlocked: Boolean): Map<String, Long>? {
        val query = UsageEventsQuery.Builder(start, end + 1).setEventTypes(
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.SCREEN_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_SHOWN,
            UsageEvents.Event.KEYGUARD_HIDDEN,
        ).build()
        val events = try {
            // UsageStatsManager uses this context's Android user. Never query other profiles.
            usage?.queryEvents(query)
        } catch (_: SecurityException) {
            null
        } ?: return null
        val observed = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()
        var screenInteractive = true
        var keyguardVisible = !knownUnlocked
        while (events.hasNextEvent() && events.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> screenInteractive = false
                UsageEvents.Event.SCREEN_INTERACTIVE -> screenInteractive = true
                UsageEvents.Event.KEYGUARD_SHOWN -> keyguardVisible = true
                UsageEvents.Event.KEYGUARD_HIDDEN -> keyguardVisible = false
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!screenInteractive || keyguardVisible) continue
                    val component = matchLauncherUsageEvent(event.packageName.orEmpty(), event.className.orEmpty(), eligible)
                        ?: continue
                    observed[component] = maxOf(event.timeStamp, observed[component] ?: 0)
                }
            }
        }
        return observed
    }

    private fun isLocked() = keyguard?.isKeyguardLocked != false

    // A whole Sanctuary session can start and end while the launcher is stopped.
    private fun protectionBoundary(): Long? = runCatching {
        val sanctuary = SanctuaryStore.get(appContext)
        if (sanctuary.refresh() != null) return null
        sanctuary.history().firstOrNull()?.endedAt ?: 0L
    }.getOrNull()

    private fun resetUsageWindow(knownUnlocked: Boolean = !isLocked()) {
        scanGeneration++
        prefs.edit().putLong(USAGE_CUTOFF, System.currentTimeMillis())
            .putBoolean(CUTOFF_UNLOCKED, knownUnlocked)
            .remove(LAST_SCAN)
            .apply()
    }

    private fun read(): LauncherSmartState {
        val excluded = prefs.getStringSet(EXCLUDED, emptySet()).orEmpty().toSet()
        val history = prefs.getStringSet(HISTORY, emptySet()).orEmpty().mapNotNull { entry ->
            val component = entry.substringBeforeLast('\t', "")
            val timestamp = entry.substringAfterLast('\t', "").toLongOrNull()
            if (component.isEmpty() || component in excluded || timestamp == null || timestamp <= 0) null
            else component to timestamp
        }.toMap().boundedLauncherHistory()
        return LauncherSmartState(
            mode = trackingMode(prefs.getString(MODE, null)),
            recentLimit = prefs.getInt(RECENT_LIMIT, 8).takeIf { it in LAUNCHER_RECENT_LIMITS } ?: 8,
            showHomeRecents = prefs.getBoolean(HOME_RECENTS, true),
            showSuggestions = prefs.getBoolean(SUGGESTIONS, true),
            pinned = prefs.getStringSet(PINNED, emptySet()).orEmpty() - excluded,
            excluded = excluded,
            history = history,
            suggestionsResetAt = prefs.getLong(SUGGESTIONS_RESET, 0),
        )
    }

    private fun update(transform: (LauncherSmartState) -> LauncherSmartState) {
        val previous = updates.value
        val next = transform(previous)
        if (next == previous) return
        prefs.edit()
            .putString(MODE, next.mode.name)
            .putInt(RECENT_LIMIT, next.recentLimit)
            .putBoolean(HOME_RECENTS, next.showHomeRecents)
            .putBoolean(SUGGESTIONS, next.showSuggestions)
            .putLong(SUGGESTIONS_RESET, next.suggestionsResetAt)
            .putStringSet(PINNED, next.pinned)
            .putStringSet(EXCLUDED, next.excluded)
            .putStringSet(HISTORY, next.history.entries.mapTo(mutableSetOf()) { "${it.key}\t${it.value}" })
            .apply()
        updates.value = next
    }

    private fun trackingMode(value: String?): LauncherTrackingMode =
        LauncherTrackingMode.entries.firstOrNull { it.name == value } ?: LauncherTrackingMode.AUTOMATIC

    private companion object {
        const val MODE = "mode"
        const val PREVIOUS_MODE = "previous_mode"
        const val CONSENT_PENDING = "consent_pending"
        const val USAGE_CUTOFF = "usage_cutoff"
        const val CUTOFF_UNLOCKED = "cutoff_unlocked"
        const val LAST_SCAN = "last_scan"
        const val RECENT_LIMIT = "recent_limit"
        const val HOME_RECENTS = "home_recents"
        const val SUGGESTIONS = "suggestions"
        const val SUGGESTIONS_RESET = "suggestions_reset"
        const val PINNED = "pinned"
        const val EXCLUDED = "excluded"
        const val HISTORY = "history"
        const val MAX_USAGE_LOOKBACK = 24 * 60 * 60 * 1000L
    }
}
