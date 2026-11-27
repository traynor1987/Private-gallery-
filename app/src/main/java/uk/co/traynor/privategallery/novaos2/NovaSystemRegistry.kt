package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    // THEMES

    // WORKING

    // CONTROL

    // LOCK

    // DESKTOP

    // HEALTH

    add(NovaSystemFeature(NovaSystemId.PRIVACY, "Privacy Dashboard", "Permissions, protection and cloud-consent status.") { NovaPrivacyDashboard(it) })

    // UTILITIES
}
