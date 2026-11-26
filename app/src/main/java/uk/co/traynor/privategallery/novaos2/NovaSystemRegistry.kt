package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    // THEMES

    add(NovaSystemFeature(NovaSystemId.WORKING, "Working Mode", "Start a shift or open the driving dashboard.") { NovaWorkingMode(it) })

    // CONTROL

    // LOCK

    // DESKTOP

    // HEALTH

    // PRIVACY

    // UTILITIES
}
