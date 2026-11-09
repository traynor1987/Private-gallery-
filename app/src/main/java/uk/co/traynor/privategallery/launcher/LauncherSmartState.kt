package uk.co.traynor.privategallery.launcher

internal val LAUNCHER_RECENT_LIMITS = listOf(4, 8, 12, 16)
private const val MAX_LAUNCHER_HISTORY = 64

internal enum class LauncherTrackingMode(val title: String, val description: String) {
    AUTOMATIC("Automatic", "Remember apps you open from Nova Launcher."),
    EXTENDED("Extended", "Also recognise apps you open outside Nova Launcher using Android Usage Access."),
    OFF("Off", "Pause app tracking and hide automatic recents and suggestions."),
}

internal data class LauncherSmartState(
    val mode: LauncherTrackingMode = LauncherTrackingMode.AUTOMATIC,
    val recentLimit: Int = 8,
    val showHomeRecents: Boolean = true,
    val showSuggestions: Boolean = true,
    val pinned: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
    val history: Map<String, Long> = emptyMap(),
    val suggestionsResetAt: Long = 0,
) {
    fun record(component: String, timestamp: Long): LauncherSmartState = mergeHistory(mapOf(component to timestamp))

    fun mergeHistory(observed: Map<String, Long>): LauncherSmartState {
        if (mode == LauncherTrackingMode.OFF || observed.isEmpty()) return this
        val updated = history.toMutableMap()
        observed.forEach { (component, timestamp) ->
            if (component !in excluded && timestamp > (updated[component] ?: 0L)) {
                updated[component] = timestamp
            }
        }
        return copy(history = updated.boundedLauncherHistory())
    }

    fun withExclusion(component: String): LauncherSmartState = if (component in excluded) {
        copy(excluded = excluded - component)
    } else {
        copy(excluded = excluded + component, pinned = pinned - component, history = history - component)
    }

    fun recentApps(eligibleApps: List<LauncherApp>): List<LauncherApp> = recentApps(eligibleApps, after = 0)

    private fun recentApps(eligibleApps: List<LauncherApp>, after: Long): List<LauncherApp> {
        if (mode == LauncherTrackingMode.OFF) return emptyList()
        val byComponent = eligibleApps.associateBy(LauncherApp::component)
        return history.orderedComponents()
            .filter { it !in excluded && history.getValue(it) > after }
            .mapNotNull(byComponent::get)
            .take(recentLimit)
            .toList()
    }

    fun pinnedApps(eligibleApps: List<LauncherApp>): List<LauncherApp> =
        eligibleApps.filter { it.component in pinned && it.component !in excluded }

    fun suggestedApps(eligibleApps: List<LauncherApp>): List<LauncherApp> {
        if (mode == LauncherTrackingMode.OFF || !showSuggestions) return emptyList()
        return (pinnedApps(eligibleApps) + recentApps(eligibleApps, after = suggestionsResetAt))
            .distinctBy(LauncherApp::component)
            .take(recentLimit)
    }
}

internal fun Map<String, Long>.boundedLauncherHistory(): Map<String, Long> =
    orderedComponents().take(MAX_LAUNCHER_HISTORY).associateWith(::getValue)

private fun Map<String, Long>.orderedComponents(): Sequence<String> =
    entries.asSequence().sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .map { it.key }

internal fun matchLauncherUsageEvent(
    packageName: String,
    className: String,
    eligibleComponents: Set<String>,
): String? {
    if (packageName.isBlank() || className.isBlank()) return null
    val fullClass = if (className.startsWith('.')) packageName + className else className
    return "$packageName/$fullClass".takeIf { it in eligibleComponents }
}
