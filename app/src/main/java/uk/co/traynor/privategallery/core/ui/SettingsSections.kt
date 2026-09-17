package uk.co.traynor.privategallery.core.ui

/** The labels used by the one, active Settings route. */
object SettingsSections {
    const val SECURITY = "Security"
    const val PRIVACY = "Privacy"
    const val DEBUG = "Debug"
    const val APPEARANCE = "Appearance"
    const val UPDATES = "Updates"
    const val ABOUT = "About"

    val production = listOf(SECURITY, PRIVACY, DEBUG, APPEARANCE, UPDATES, ABOUT)
}

object SettingsLayoutPolicy {
    const val isVerticallyScrollable = true
}
