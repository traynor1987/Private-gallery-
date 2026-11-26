package uk.co.traynor.privategallery.launcher

internal enum class LauncherLook(val label: String) {
    NOVA("NovaOS Original"),
    ROUNDED("One UI-inspired"),
    FLOATING("iOS-inspired"),
}

internal enum class LauncherTypeface { SYSTEM, ROUNDED, EDITORIAL, MONOSPACE }
internal enum class LauncherWidgetFrame { NATIVE, GLASS, SOLID }
internal enum class LauncherMotion(val milliseconds: Int) { REDUCED(0), STANDARD(210), SMOOTH(340) }

internal data class LauncherColourSchedule(val lightHour: Int = 7, val darkHour: Int = 20) {
    init { require(lightHour in 0..23 && darkHour in 0..23 && lightHour != darkHour) }

    fun themeAt(hour: Int): LauncherTheme {
        require(hour in 0..23)
        val light = if (lightHour < darkHour) hour in lightHour until darkHour
            else hour >= lightHour || hour < darkHour
        return if (light) LauncherTheme.LIGHT else LauncherTheme.DARK
    }
}

internal data class LauncherStudioStyle(
    val look: LauncherLook = LauncherLook.NOVA,
    val typeface: LauncherTypeface = LauncherTypeface.SYSTEM,
    val widgetFrame: LauncherWidgetFrame = LauncherWidgetFrame.NATIVE,
    val motion: LauncherMotion = LauncherMotion.STANDARD,
    val innerGrid: LauncherGrid? = null,
    val customAccent: Int? = null,
    val schedule: LauncherColourSchedule? = null,
    val folder: LauncherFolderAppearance? = null,
) {
    init { require(customAccent == null || customAccent in 0..0xFFFFFF) }
}

internal fun LauncherLook.preset(current: LauncherAppearance): LauncherAppearance {
    // A theme is presentation only. Home pages, widgets, folders and application identities live in the layout.
    val shared = current.studio.copy(look = this, customAccent = null)
    return when (this) {
        LauncherLook.NOVA -> current.copy(
            wallpaper = LauncherWallpaper.MIDNIGHT, accent = LauncherAccent.SKY,
            iconShape = LauncherIconShape.SQUIRCLE, iconStyle = LauncherIconStyle.NOVA_TILES,
            dock = LauncherDock.GLASS, studio = shared.copy(widgetFrame = LauncherWidgetFrame.GLASS,
                typeface = LauncherTypeface.SYSTEM, motion = LauncherMotion.STANDARD),
        )
        LauncherLook.ROUNDED -> current.copy(
            wallpaper = LauncherWallpaper.OCEAN, accent = LauncherAccent.TEAL,
            iconShape = LauncherIconShape.CIRCLE, iconStyle = LauncherIconStyle.NOVA_TILES,
            iconSize = LauncherIconSize.LARGE, dock = LauncherDock.SOLID,
            studio = shared.copy(widgetFrame = LauncherWidgetFrame.SOLID,
                typeface = LauncherTypeface.ROUNDED, motion = LauncherMotion.STANDARD),
        )
        LauncherLook.FLOATING -> current.copy(
            wallpaper = LauncherWallpaper.PLUM, accent = LauncherAccent.LAVENDER,
            iconShape = LauncherIconShape.ROUNDED, iconStyle = LauncherIconStyle.NOVA_TILES,
            iconSize = LauncherIconSize.MEDIUM, dock = LauncherDock.GLASS,
            studio = shared.copy(widgetFrame = LauncherWidgetFrame.GLASS,
                typeface = LauncherTypeface.SYSTEM, motion = LauncherMotion.SMOOTH),
        )
    }
}

internal fun LauncherAppearance.gridFor(inner: Boolean): LauncherGrid =
    if (inner) studio.innerGrid ?: grid else grid
