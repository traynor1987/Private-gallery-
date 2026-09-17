package uk.co.traynor.privategallery.core.ui

enum class AppTheme { SYSTEM, LIGHT, DARK }

object ThemePreference {
    fun encode(theme: AppTheme): String = when (theme) {
        AppTheme.SYSTEM -> "system"
        AppTheme.LIGHT -> "light"
        AppTheme.DARK -> "dark"
    }

    fun decode(stored: String?): AppTheme = when (stored) {
        "light" -> AppTheme.LIGHT
        "dark" -> AppTheme.DARK
        else -> AppTheme.SYSTEM
    }
}
