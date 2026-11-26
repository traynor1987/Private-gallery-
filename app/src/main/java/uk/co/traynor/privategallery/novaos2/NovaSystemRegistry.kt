package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    // THEMES

    // WORKING

    // CONTROL

    // LOCK

    // DESKTOP

    // HEALTH

    // PRIVACY

    add(NovaSystemFeature(NovaSystemId.UTILITIES, "Nova Utilities", "Notes, Tasks, Calendar, Files and everyday tools.") { NovaUtilities(it) })
}
