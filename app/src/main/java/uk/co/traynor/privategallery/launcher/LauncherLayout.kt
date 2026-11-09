package uk.co.traynor.privategallery.launcher

internal enum class LauncherWallpaper { OCEAN, MIDNIGHT, PLUM, DEVICE }
internal enum class LauncherTheme { SYSTEM, LIGHT, DARK }
internal enum class LauncherAccent { SKY, TEAL, LAVENDER, AMBER }
internal enum class LauncherIconShape { ROUNDED, CIRCLE, SQUIRCLE }
internal enum class LauncherIconSize { SMALL, MEDIUM, LARGE }
internal enum class LauncherDock { GLASS, SOLID, TRANSPARENT, HIDDEN }
internal enum class LauncherNewAppPlacement { LIBRARY_ONLY, HOME }
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

internal data class LauncherDockOptions(
    val locked: Boolean = true,
    val showInLibrary: Boolean = true,
    val labels: Boolean = false,
)

internal sealed interface LauncherItem {
    data class App(val component: String) : LauncherItem
    data class Folder(val name: String, val apps: List<String>) : LauncherItem
    data class SmartFolder(
        val category: LauncherCategory,
        val customName: String? = null,
        val included: List<String> = emptyList(),
    ) : LauncherItem {
        val name: String get() = customName ?: category.label
    }
    data object Workspace : LauncherItem
}

internal data class LauncherLayout(
    val pages: List<List<LauncherItem>> = listOf(emptyList()),
    val dock: List<LauncherItem> = listOf(LauncherItem.Workspace),
    val favourites: Set<String> = emptySet(),
)

private val stockSmartFolders = listOf(
    LauncherCategory.SUGGESTIONS, LauncherCategory.RECENTLY_USED,
    LauncherCategory.RECENTLY_ADDED, LauncherCategory.NOVA_OS,
).map { LauncherItem.SmartFolder(it) }

internal fun LauncherLayout.withSmartFolderTemplate(
    onlyIfEmpty: Boolean,
    capacity: Int = MAX_LAUNCHER_PAGE_ITEMS,
): LauncherLayout {
    val page = 1
    val items = pages.getOrNull(page).orEmpty()
    if (stockSmartFolders in pages || onlyIfEmpty && items.isNotEmpty()) return this
    val room = capacity.coerceIn(0, MAX_LAUNCHER_PAGE_ITEMS) - items.size
    if (room <= 0) return this
    val existing = items.filterIsInstance<LauncherItem.SmartFolder>().map { it.category }.toSet()
    val additions = stockSmartFolders.filterNot { it.category in existing }.take(room)
    if (additions.isEmpty()) return this

    val nextPages = List(maxOf(pages.size, page + 1)) { index ->
        if (index == page) items + additions else pages.getOrNull(index).orEmpty()
    }
    return copy(pages = nextPages)
}

internal data class LauncherWidget(val id: Int, val provider: String, val wide: Boolean, val page: Int = 0)

internal data class LauncherShortcutPosition(val page: Int, val item: Int)

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
    val coverCleanupUndo: List<List<LauncherItem>>? = null,
    val innerCleanupUndo: List<List<LauncherItem>>? = null,
    val dockOptions: LauncherDockOptions = LauncherDockOptions(),
    val newAppPlacement: LauncherNewAppPlacement = LauncherNewAppPlacement.LIBRARY_ONLY,
) {
    fun layout(wide: Boolean): LauncherLayout = if (wide && separateDisplays) inner else cover

    fun withLayout(wide: Boolean, layout: LauncherLayout): NovaLauncherState =
        if (wide && separateDisplays) copy(
            inner = layout, innerCleanupUndo = innerCleanupUndo.takeIf { inner.pages == layout.pages },
        ) else copy(
            cover = layout, coverCleanupUndo = coverCleanupUndo.takeIf { cover.pages == layout.pages },
        )

    fun withoutPage(wide: Boolean, page: Int): NovaLauncherState {
        val current = layout(wide)
        if (current.pages.size <= 1 || page !in current.pages.indices) return this
        val usesInner = wide && separateDisplays
        val next = withLayout(wide, current.copy(pages = current.pages.filterIndexed { index, _ -> index != page }))
        return next.copy(widgets = widgets.map { widget ->
            if (widget.wide != usesInner || widget.page < page) widget
            else widget.copy(page = (widget.page - 1).coerceAtLeast(0))
        })
    }

    fun shortcutCleanupUndo(wide: Boolean): List<List<LauncherItem>>? =
        if (wide && separateDisplays) innerCleanupUndo else coverCleanupUndo

    fun cleanUpShortcuts(wide: Boolean, selected: Set<LauncherShortcutPosition>): NovaLauncherState {
        val current = layout(wide)
        val cleaned = current.pages.mapIndexed { page, items ->
            items.filterIndexed { index, item ->
                item !is LauncherItem.App || LauncherShortcutPosition(page, index) !in selected
            }
        }
        if (cleaned == current.pages) return this
        val usesInner = wide && separateDisplays
        val lastWidgetPage = widgets.filter { it.wide == usesInner }.maxOfOrNull(LauncherWidget::page) ?: 0
        var pageCount = cleaned.size
        while (pageCount > lastWidgetPage + 1 &&
            cleaned[pageCount - 1].isEmpty() && current.pages[pageCount - 1].isNotEmpty()
        ) pageCount--
        val next = withLayout(wide, current.copy(pages = cleaned.take(pageCount)))
        val before = shortcutCleanupUndo(wide) ?: current.pages
        return if (usesInner) next.copy(innerCleanupUndo = before) else next.copy(coverCleanupUndo = before)
    }

    fun undoShortcutCleanup(wide: Boolean): NovaLauncherState {
        val before = shortcutCleanupUndo(wide) ?: return this
        return withLayout(wide, layout(wide).copy(pages = before))
    }

    fun withSuccessfulLaunch(component: String): NovaLauncherState {
        if (!LauncherAppIdentity.isValidKey(component)) return this
        val count = launchCounts[component] ?: 0
        val recent = launchCounts.entries.filter { it.key != component }.takeLast(MAX_LAUNCH_COUNTS - 1)
        return copy(launchCounts = recent.associate { it.key to it.value } + (component to (count + 1).coerceAtMost(MAX_LAUNCH_COUNT)))
    }

    fun withoutRedundantSmartFolderPages(): NovaLauncherState {
        fun duplicates(layout: LauncherLayout, wide: Boolean, hasUndo: Boolean): Set<Int> {
            // Legacy layouts do not track template provenance. Only collapse exact copies
            // after the canonical stock page, preserving customized pages and undo snapshots.
            if (hasUndo || layout.pages.getOrNull(1) != stockSmartFolders) return emptySet()
            val widgetPages = widgets.filter { it.wide == wide }.mapTo(mutableSetOf(), LauncherWidget::page)
            return layout.pages.indices.filterTo(mutableSetOf()) { index ->
                index > 1 && index !in widgetPages && layout.pages[index] == stockSmartFolders
            }
        }

        val removedCover = duplicates(cover, wide = false, hasUndo = coverCleanupUndo != null)
        val removedInner = duplicates(inner, wide = true, hasUndo = innerCleanupUndo != null)
        if (removedCover.isEmpty() && removedInner.isEmpty()) return this
        return copy(
            cover = cover.copy(pages = cover.pages.filterIndexed { index, _ -> index !in removedCover }),
            inner = inner.copy(pages = inner.pages.filterIndexed { index, _ -> index !in removedInner }),
            widgets = widgets.map { widget ->
                val removed = if (widget.wide) removedInner else removedCover
                widget.copy(page = widget.page - removed.count { it < widget.page })
            },
        )
    }

    fun withNewlyInstalledApps(components: Collection<String>): NovaLauncherState {
        if (!configured || newAppPlacement != LauncherNewAppPlacement.HOME || components.isEmpty()) return this
        val valid = components.filter(LauncherAppIdentity::isValidKey).distinct()
        var next = this
        for (wide in if (separateDisplays) listOf(false, true) else listOf(false)) {
            val layout = next.layout(wide)
            val capacity = appearance.grid.columns(wide) * appearance.grid.rows
            val placed = layout.withNewlyInstalledApps(valid, capacity)
            if (placed != layout) next = next.withLayout(wide, placed)
        }
        return next
    }
}

private fun LauncherLayout.withNewlyInstalledApps(components: List<String>, capacity: Int): LauncherLayout {
    val existing = buildSet {
        for (item in pages.flatten() + dock) when (item) {
            is LauncherItem.App -> add(item.component)
            is LauncherItem.Folder -> addAll(item.apps)
            else -> Unit
        }
    }
    val updated = pages.toMutableList()
    for (component in components) {
        if (component in existing) continue
        val index = updated.indexOfFirst { page ->
            page.size < capacity && (page.isEmpty() || page.any { it !is LauncherItem.SmartFolder })
        }
        val item = LauncherItem.App(component)
        if (index >= 0) updated[index] = updated[index] + item
        else if (updated.size < MAX_LAUNCHER_PAGES) updated.add(listOf(item))
        else break
    }
    return if (updated == pages) this else copy(pages = updated)
}

internal fun <T> List<T>.movingLauncherItem(from: Int, to: Int): List<T> =
    if (from == to || from !in indices || to !in indices) this
    else toMutableList().apply { add(to, removeAt(from)) }

internal const val MAX_LAUNCHER_PAGES = 12
internal const val MAX_LAUNCHER_PAGE_ITEMS = 48
internal const val MAX_LAUNCHER_DOCK_ITEMS = 8
internal const val MAX_LAUNCHER_FOLDER_ITEMS = 48
internal const val MAX_LAUNCHER_FAVOURITES = 128
internal const val MAX_LAUNCHER_WIDGETS = 24
internal const val MAX_LAUNCH_COUNTS = 256
internal const val MAX_LAUNCH_COUNT = 10_000

/** Plain Android component names, including widget providers. */
internal fun isLauncherComponent(value: String): Boolean =
    LauncherAppIdentity.forComponent(value, profileSerial = null) != null

internal fun launcherInitialState(
    mode: LauncherStartingLayout,
    template: LauncherTemplate,
    components: List<String>,
    selectedCopyComponents: List<String> = emptyList(),
): NovaLauncherState {
    val state = NovaLauncherState()
    if (mode == LauncherStartingLayout.START_FRESH) return state
    val apps = components.asSequence()
        .filter(LauncherAppIdentity::isValidKey).distinct().toList()
    if (mode == LauncherStartingLayout.COPY_CURRENT) {
        val available = apps.toSet()
        val items = selectedCopyComponents.asSequence().filter { it in available }.distinct()
            .take(LauncherGrid.STANDARD.columns(false) * LauncherGrid.STANDARD.rows)
            .map(LauncherItem::App).toList()
        return state.copy(
            cover = populatedLayout(items, LauncherGrid.STANDARD, wide = false),
            inner = populatedLayout(items, LauncherGrid.STANDARD, wide = true),
        )
    }

    val appearance = when (template) {
        LauncherTemplate.MINIMAL -> LauncherAppearance(grid = LauncherGrid.SPACIOUS)
        LauncherTemplate.CLASSIC -> LauncherAppearance()
        LauncherTemplate.PRODUCTIVITY -> LauncherAppearance(accent = LauncherAccent.TEAL, grid = LauncherGrid.COMPACT)
        LauncherTemplate.GAMING -> LauncherAppearance(wallpaper = LauncherWallpaper.MIDNIGHT, accent = LauncherAccent.LAVENDER)
    }
    val matching = when (template) {
        LauncherTemplate.MINIMAL -> emptyList()
        LauncherTemplate.CLASSIC -> apps.filter { it.matchesCategory("dialer", "phone", "messaging", "camera") }
        LauncherTemplate.PRODUCTIVITY -> apps.filter { it.matchesCategory("mail", "notes", "office", "calendar", "drive", "docs") }
        LauncherTemplate.GAMING -> apps.filter { it.matchesCategory("game", "steam", "xbox", "playstation") }
    }
    val dockApps = matching.take(3)
    val grouped = matching.drop(dockApps.size).take(MAX_LAUNCHER_FOLDER_ITEMS)
    val folderName = when (template) {
        LauncherTemplate.PRODUCTIVITY -> "Productivity"
        LauncherTemplate.GAMING -> "Games"
        else -> null
    }
    val homeApps = if (folderName != null && grouped.size >= 2) {
        listOf(LauncherItem.Folder(folderName, grouped))
    } else {
        emptyList()
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
