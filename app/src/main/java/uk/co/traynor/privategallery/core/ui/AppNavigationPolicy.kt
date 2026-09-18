package uk.co.traynor.privategallery.core.ui

/** Stable app-level destinations. Planned areas are intentionally inert until implemented. */
enum class AppNavigationDestination(val label: String, val icon: String) {
    GALLERY("Gallery", "▦"),
    VAULT("Vault", "⌑"),
    JENNA("Jenna", "♥"),
    BROWSER("Browser", "◉"),
    SETTINGS("Settings", "⚙"),
}

object AppNavigationPolicy {
    val destinations: List<AppNavigationDestination> = AppNavigationDestination.entries
}
