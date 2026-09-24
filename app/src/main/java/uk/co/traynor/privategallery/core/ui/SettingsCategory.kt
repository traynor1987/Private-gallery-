package uk.co.traynor.privategallery.core.ui

enum class SettingsCategory(val title: String, val summary: String) {
    SECURITY("Security & privacy", "PIN, biometrics, recovery and auto-lock"),
    GALLERY("Gallery & Vault", "Media browsing, thumbnails and collections"),
    BROWSER("Browser", "Search, history and browsing data"),
    VPN("VPN", "Profiles and connection behaviour"),
    AI("AI editing", "Provider, consent and Vault-only output"),
    APPEARANCE("Appearance", "Theme and display"),
    ABOUT("Updates & About", "Version, updates and licences"),
    DEBUG("Debug / Acceptance", "Owner diagnostics"),
}
