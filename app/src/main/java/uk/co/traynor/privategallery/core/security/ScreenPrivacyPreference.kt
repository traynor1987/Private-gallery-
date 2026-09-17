package uk.co.traynor.privategallery.core.security

/** Screenshot capture is denied unless the owner explicitly enables it. */
object ScreenPrivacyPreference {
    fun secureWindow(storedAllowScreenshots: String?): Boolean = storedAllowScreenshots != "true"
}
