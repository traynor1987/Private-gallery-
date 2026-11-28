package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    add(NovaSystemFeature(NovaSystemId.THEMES, "Themes Studio", "Preview and save Home appearance for both displays.") { NovaThemesStudio(it) })

    // WORKING

    add(NovaSystemFeature(NovaSystemId.CONTROL, "Control Centre", "Device controls and safe notification status.") { NovaControlCentre(it) })

    // LOCK

    // DESKTOP

    // HEALTH

    // PRIVACY

    // UTILITIES
}
