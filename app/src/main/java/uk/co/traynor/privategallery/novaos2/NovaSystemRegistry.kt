package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    // THEMES

    // WORKING

    // CONTROL

    add(NovaSystemFeature(NovaSystemId.LOCK, "Lock Screen Studio", "Preview styles and choose a supported lock wallpaper.") { NovaLockScreenStudio(it) })

    // DESKTOP

    // HEALTH

    add(NovaSystemFeature(NovaSystemId.PRIVACY, "Privacy Dashboard", "Permissions, protection and cloud-consent status.") { NovaPrivacyDashboard(it) })

    // UTILITIES
}
