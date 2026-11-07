package uk.co.traynor.privategallery.home2

private val sanctuaryEssentialRoutes = setOf(
    "home", "sanctuary", "sanctuary-settings", "sanctuary-permissions", "settings", "home-settings",
    "native-settings", "shell-settings", "lock-settings", "lock-now", "panic", "close-browser",
)

internal fun sanctuaryAllowsNovaRoute(allowed: Set<String>, destination: String): Boolean {
    val route = destination.removePrefix("resume-internal:")
    if (route in sanctuaryEssentialRoutes || route.startsWith("folder:")) return true
    if (route in allowed) return true
    return when {
        route.startsWith("mind:") || route == "calm" -> "mind" in allowed
        route in setOf("arcade-2048", "arcade-sudoku", "arcade-snake") -> "arcade" in allowed
        route.startsWith("store:") -> "store" in allowed
        route.startsWith("widget:") -> "widgets" in allowed
        else -> false
    }
}
