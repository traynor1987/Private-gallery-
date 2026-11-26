package uk.co.traynor.privategallery.novaos2

/** Feature branches add their own entry. Only compiled and registered pages appear in Home. */
internal val novaSystemFeatures: List<NovaSystemFeature> = buildList {
    add(NovaSystemFeature(NovaSystemId.THEMES, "Themes Studio", "Preview and save Home appearance for both displays.") { NovaThemesStudio(it) })

    add(NovaSystemFeature(NovaSystemId.WORKING, "Working Mode", "Start a shift or open the driving dashboard.") { NovaWorkingMode(it) })

    add(NovaSystemFeature(NovaSystemId.CONTROL, "Control Centre", "Device controls and safe notification status.") { NovaControlCentre(it) })

    add(NovaSystemFeature(NovaSystemId.LOCK, "Lock Screen Studio", "Preview styles and choose a supported lock wallpaper.") { NovaLockScreenStudio(it) })

    add(NovaSystemFeature(NovaSystemId.DESKTOP, "Nova Desktop", "Open a workspace with resizable Nova panels.") { NovaDesktopMode(it) })

    add(NovaSystemFeature(NovaSystemId.HEALTH, "Nova Health & Mind", "Private check-ins, health records and reflections.") { NovaHealthMind(it) })

    add(NovaSystemFeature(NovaSystemId.PRIVACY, "Privacy Dashboard", "Permissions, protection and cloud-consent status.") { NovaPrivacyDashboard(it) })

    add(NovaSystemFeature(NovaSystemId.UTILITIES, "Nova Utilities", "Notes, Tasks, Calendar, Files and everyday tools.") { NovaUtilities(it) })
}
