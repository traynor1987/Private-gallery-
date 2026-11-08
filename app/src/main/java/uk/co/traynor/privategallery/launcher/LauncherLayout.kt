package uk.co.traynor.privategallery.launcher

internal enum class LauncherWallpaper { OCEAN, MIDNIGHT, PLUM, DEVICE }
internal enum class LauncherTheme { SYSTEM, LIGHT, DARK }
internal enum class LauncherAccent { SKY, TEAL, LAVENDER, AMBER }
internal enum class LauncherIconShape { ROUNDED, CIRCLE, SQUIRCLE }
internal enum class LauncherIconSize { SMALL, MEDIUM, LARGE }
internal enum class LauncherDock { GLASS, SOLID, HIDDEN }
internal enum class LauncherDrawerOrientation { VERTICAL, HORIZONTAL }
internal enum class LauncherSearchPlacement { HOME, DRAWER, BOTH }
internal enum class LauncherSort { NAME, FAVOURITES, FREQUENT }
internal enum class LauncherGesture { NONE, SWIPE_DOWN, DOUBLE_TAP }
internal enum class LauncherTemplate { MINIMAL, CLASSIC, PRODUCTIVITY, GAMING }
internal enum class LauncherStartingLayout { COPY_CURRENT, START_FRESH, TEMPLATE }

internal enum class LauncherGrid(private val cover: Int, private val inner: Int, val rows: Int) {
    COMPACT(5, 8, 6),
    STANDARD(4, 6, 5),
    SPACIOUS(3, 5, 4);

    fun columns(wide: Boolean): Int = if (wide) inner else cover
}

internal data class LauncherAppearance(
    val wallpaper: LauncherWallpaper = LauncherWallpaper.OCEAN,
    val theme: LauncherTheme = LauncherTheme.SYSTEM,
    val accent: LauncherAccent = LauncherAccent.SKY,
    val iconShape: LauncherIconShape = LauncherIconShape.ROUNDED,
    val iconSize: LauncherIconSize = LauncherIconSize.MEDIUM,
    val labels: Boolean = true,
    val grid: LauncherGrid = LauncherGrid.STANDARD,
    val dock: LauncherDock = LauncherDock.GLASS,
)

internal data class LauncherDrawerOptions(
    val orientation: LauncherDrawerOrientation = LauncherDrawerOrientation.VERTICAL,
    val search: LauncherSearchPlacement = LauncherSearchPlacement.DRAWER,
    val sort: LauncherSort = LauncherSort.NAME,
    val folders: Boolean = true,
    val frequent: Boolean = true,
    val shortcutSearch: Boolean = false,
)

internal sealed interface LauncherItem {
    data class App(val component: String) : LauncherItem
    data class Folder(val name: String, val apps: List<String>) : LauncherItem
    data object Workspace : LauncherItem
}

internal data class LauncherLayout(
    val pages: List<List<LauncherItem>> = listOf(emptyList()),
    val dock: List<LauncherItem> = listOf(LauncherItem.Workspace),
    val favourites: Set<String> = emptySet(),
)

internal data class LauncherWidget(val id: Int, val provider: String, val wide: Boolean)

internal data class NovaLauncherState(
    val configured: Boolean = false,
    val separateDisplays: Boolean = true,
    val cover: LauncherLayout = LauncherLayout(),
    val inner: LauncherLayout = LauncherLayout(),
    val appearance: LauncherAppearance = LauncherAppearance(),
    val drawer: LauncherDrawerOptions = LauncherDrawerOptions(),
    val workspaceGesture: LauncherGesture = LauncherGesture.NONE,
    val hideBlocked: Boolean = false,
    val widgets: List<LauncherWidget> = emptyList(),
    val launchCounts: Map<String, Int> = emptyMap(),
) {
    fun layout(wide: Boolean): LauncherLayout = if (wide && separateDisplays) inner else cover

    fun withLayout(wide: Boolean, layout: LauncherLayout): NovaLauncherState =
        if (wide && separateDisplays) copy(inner = layout) else copy(cover = layout)

    fun withSuccessfulLaunch(component: String): NovaLauncherState {
        if (!isLauncherComponent(component)) return this
        val count = launchCounts[component] ?: 0
        val recent = launchCounts.entries.filter { it.key != component }.takeLast(MAX_LAUNCH_COUNTS - 1)
        return copy(launchCounts = recent.associate { it.key to it.value } + (component to (count + 1).coerceAtMost(MAX_LAUNCH_COUNT)))
    }
}

internal const val MAX_LAUNCHER_PAGES = 12
internal const val MAX_LAUNCHER_PAGE_ITEMS = 48
internal const val MAX_LAUNCHER_DOCK_ITEMS = 8
internal const val MAX_LAUNCHER_FOLDER_ITEMS = 48
internal const val MAX_LAUNCHER_FAVOURITES = 128
internal const val MAX_LAUNCHER_WIDGETS = 24
internal const val MAX_LAUNCH_COUNTS = 256
internal const val MAX_LAUNCH_COUNT = 10_000

private val componentPattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*/\\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

/** Layout files contain Android component names, never Intents, URI strings or extras. */
internal fun isLauncherComponent(value: String): Boolean =
    value.length in 3..512 && componentPattern.matches(value)

internal fun launcherInitialState(
    mode: LauncherStartingLayout,
    template: LauncherTemplate,
    components: List<String>,
): NovaLauncherState {
    val state = NovaLauncherState()
    if (mode == LauncherStartingLayout.START_FRESH) return state
    val apps = components.asSequence()
        .filter(::isLauncherComponent).distinct().take(LauncherGrid.STANDARD.rows * 4 * MAX_LAUNCHER_PAGES)
        .toList()
    if (mode == LauncherStartingLayout.COPY_CURRENT) {
        return state.copy(
            cover = populatedLayout(apps.map(LauncherItem::App), LauncherGrid.STANDARD, wide = false),
            inner = populatedLayout(apps.map(LauncherItem::App), LauncherGrid.STANDARD, wide = true),
        )
    }

    val appearance = when (template) {
        LauncherTemplate.MINIMAL -> LauncherAppearance(grid = LauncherGrid.SPACIOUS)
        LauncherTemplate.CLASSIC -> LauncherAppearance()
        LauncherTemplate.PRODUCTIVITY -> LauncherAppearance(accent = LauncherAccent.TEAL, grid = LauncherGrid.COMPACT)
        LauncherTemplate.GAMING -> LauncherAppearance(wallpaper = LauncherWallpaper.MIDNIGHT, accent = LauncherAccent.LAVENDER)
    }
    val dockApps = when (template) {
        LauncherTemplate.MINIMAL -> emptyList()
        LauncherTemplate.CLASSIC -> apps.take(3)
        LauncherTemplate.PRODUCTIVITY -> apps.filter { it.matchesCategory("phone", "calendar", "mail", "notes") }.take(3)
        LauncherTemplate.GAMING -> apps.filter { it.matchesCategory("game", "steam", "xbox", "playstation") }.take(3)
    }
    val remaining = apps.filterNot { it in dockApps }
    val homeApps = when (template) {
        LauncherTemplate.MINIMAL -> emptyList()
        LauncherTemplate.CLASSIC -> remaining.map(LauncherItem::App)
        LauncherTemplate.PRODUCTIVITY -> groupedApps(remaining, "Productivity", "mail", "notes", "office", "calendar", "drive", "docs")
        LauncherTemplate.GAMING -> groupedApps(remaining, "Games", "game", "steam", "xbox", "playstation")
    }
    return state.copy(
        cover = populatedLayout(homeApps, appearance.grid, wide = false, dockApps),
        inner = populatedLayout(homeApps, appearance.grid, wide = true, dockApps),
        appearance = appearance,
        drawer = LauncherDrawerOptions(
            orientation = if (template == LauncherTemplate.GAMING) LauncherDrawerOrientation.HORIZONTAL else LauncherDrawerOrientation.VERTICAL,
            search = if (template == LauncherTemplate.PRODUCTIVITY) LauncherSearchPlacement.BOTH else LauncherSearchPlacement.DRAWER,
        ),
    )
}

private fun String.matchesCategory(vararg parts: String): Boolean {
    val packageName = substringBefore('/')
    return parts.any { packageName.contains(it, ignoreCase = true) }
}

private fun groupedApps(apps: List<String>, label: String, vararg parts: String): List<LauncherItem> {
    val matching = apps.filter { it.matchesCategory(*parts) }.take(MAX_LAUNCHER_FOLDER_ITEMS)
    if (matching.size < 2) return apps.map(LauncherItem::App)
    return listOf(LauncherItem.Folder(label, matching)) + apps.filterNot { it in matching }.map(LauncherItem::App)
}

private fun populatedLayout(
    items: List<LauncherItem>,
    grid: LauncherGrid,
    wide: Boolean,
    dockApps: List<String> = emptyList(),
): LauncherLayout {
    val pages = items.chunked(grid.columns(wide) * grid.rows).take(MAX_LAUNCHER_PAGES)
    return LauncherLayout(
        pages = pages.ifEmpty { listOf(emptyList()) },
        dock = dockApps.map(LauncherItem::App) + LauncherItem.Workspace,
        favourites = dockApps.toSet(),
    )
}
