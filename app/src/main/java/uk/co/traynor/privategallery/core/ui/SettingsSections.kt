package uk.co.traynor.privategallery.core.ui

/** The labels used by the one, active Settings route. */
object SettingsSections {
    const val SECURITY = "Security"
    const val PRIVACY = "Privacy"
    const val DEBUG = "Debug"
    const val APPEARANCE = "Appearance"
    const val BROWSER = "Browser"
    const val AI_EDITING = "AI editing"
    const val NOVA_BRAIN = "Nova Brain"
    const val UPDATES = "Updates"
    const val ABOUT = "About"

    val production = listOf(SECURITY, PRIVACY, APPEARANCE, BROWSER, NOVA_BRAIN, AI_EDITING, UPDATES, ABOUT)
}

object SettingsLayoutPolicy {
    const val isVerticallyScrollable = true
}
