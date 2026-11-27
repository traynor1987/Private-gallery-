package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    // THEMES

    // WORKING

    // CONTROL

    // LOCK

    add(NovaSystemFeature(NovaSystemId.DESKTOP, "Nova Desktop", "Open a workspace with resizable Nova panels.") { NovaDesktopMode(it) })

    // HEALTH

    // PRIVACY

    // UTILITIES
}
