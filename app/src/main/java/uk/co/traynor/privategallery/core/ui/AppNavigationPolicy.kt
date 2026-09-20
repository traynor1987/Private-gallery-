package uk.co.traynor.privategallery.core.ui

/** Stable app-level destinations. Planned areas are intentionally inert until implemented. */
enum class AppNavigationDestination(val label: String, val icon: String) {
    GALLERY("Gallery", "▦"),
    VAULT("Vault", "⌑"),
    /** Legacy enum ordinal is retained for installed navigation state; label is generic. */
    JENNA("Favourite", "♥"),
    BROWSER("Browser", "◉"),
    SETTINGS("Settings", "⚙"),
}

object AppNavigationPolicy {
    val destinations: List<AppNavigationDestination> = AppNavigationDestination.entries
}
