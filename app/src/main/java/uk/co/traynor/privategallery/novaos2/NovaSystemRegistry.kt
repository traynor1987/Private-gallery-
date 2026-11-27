package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    // THEMES

    // WORKING

    // CONTROL

    // LOCK

    // DESKTOP

    add(NovaSystemFeature(NovaSystemId.HEALTH, "Nova Health & Mind", "Private check-ins, health records and reflections.") { NovaHealthMind(it) })

    // PRIVACY

    add(NovaSystemFeature(NovaSystemId.UTILITIES, "Nova Utilities", "Notes, Tasks, Calendar, Files and everyday tools.") { NovaUtilities(it) })
}
