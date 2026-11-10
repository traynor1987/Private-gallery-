package uk.co.traynor.privategallery.launcher

import java.security.MessageDigest
import java.util.HexFormat

import uk.co.traynor.privategallery.home2.AppFolder
import uk.co.traynor.privategallery.home2.Profile

/** This boundary carries only fixed public built-in routes and bundled wallpaper choices. */
internal data class WorkspacePublicProjection(
    val pages: List<List<LauncherItem>>,
    val dock: List<LauncherItem>,
    val wallpaper: LauncherWallpaper?,
    val grid: LauncherGrid,
    val iconSize: LauncherIconSize,
)

private val routes = mapOf(
    "vault" to LauncherBuiltIn.PRIMARY_GALLERY,
    "store" to LauncherBuiltIn.STORE,
    "arcade" to LauncherBuiltIn.ARCADE,
    "nova-calculator" to LauncherBuiltIn.CALCULATOR,
    "sanctuary" to LauncherBuiltIn.SANCTUARY,
)

private fun routeByComponent(applicationId: String) =
    routes.entries.associate { (route, builtIn) -> builtIn.app(applicationId).component to route }

internal fun Profile.publicProjection(applicationId: String): WorkspacePublicProjection {
    val components = routes.mapValues { (_, builtIn) -> builtIn.app(applicationId).component }
    val folders = folders.associateBy(AppFolder::id)
    fun project(id: String): LauncherItem? {
        components[id]?.let { return LauncherItem.App(it) }
        val folder = folders[id] ?: return null
        val members = folder.apps.mapNotNull(components::get).distinct()
        if (members.size < 2 || members.size != folder.apps.size) return null
        // Even a folder with public members can have a private custom title.
        return LauncherItem.Folder("Nova apps", members.take(MAX_LAUNCHER_FOLDER_ITEMS))
    }
    return WorkspacePublicProjection(
        pages = pages.take(MAX_LAUNCHER_PAGES).map { page -> page.mapNotNull(::project).take(MAX_LAUNCHER_PAGE_ITEMS) },
        dock = dock.mapNotNull { id -> components[id]?.let { LauncherItem.App(it) } }.take(MAX_LAUNCHER_DOCK_ITEMS),
        wallpaper = if (wallpaper.image == null) when (wallpaper.builtin) {
            "Aurora" -> LauncherWallpaper.OCEAN
            "Midnight" -> LauncherWallpaper.MIDNIGHT
            "Dusk" -> LauncherWallpaper.PLUM
            else -> null
        } else null,
        grid = when {
            columns <= 3 -> LauncherGrid.SPACIOUS
            columns >= 5 -> LauncherGrid.COMPACT
            else -> LauncherGrid.STANDARD
        },
        iconSize = when {
            iconSize < 54 -> LauncherIconSize.SMALL
            iconSize > 66 -> LauncherIconSize.LARGE
            else -> LauncherIconSize.MEDIUM
        },
    )
}

internal fun NovaLauncherState.mergePrimaryProjection(
    projection: WorkspacePublicProjection,
    applicationId: String,
): NovaLauncherState {
    val public = routeByComponent(applicationId)
    fun shared(item: LauncherItem): Boolean = when (item) {
        is LauncherItem.App -> item.component in public
        is LauncherItem.Folder -> item.apps.isNotEmpty() && item.apps.all(public::containsKey)
        else -> false
    }
    fun merge(previous: LauncherLayout): LauncherLayout {
        val count = maxOf(previous.pages.size, projection.pages.size).coerceIn(1, MAX_LAUNCHER_PAGES)
        return previous.copy(
            pages = List(count) { index ->
                mergeSharedSlots(previous.pages.getOrElse(index) { emptyList() },
                    projection.pages.getOrElse(index) { emptyList() }, MAX_LAUNCHER_PAGE_ITEMS, ::shared)
            },
            dock = mergeSharedSlots(previous.dock, projection.dock, MAX_LAUNCHER_DOCK_ITEMS, ::shared),
        )
    }
    return copy(
        cover = merge(cover),
        inner = merge(inner),
        appearance = appearance.copy(
            wallpaper = projection.wallpaper ?: appearance.wallpaper,
            grid = projection.grid,
            iconSize = projection.iconSize,
        ),
        coverCleanupUndo = null,
        innerCleanupUndo = null,
    )
}

internal fun LauncherLayout.withWidgetPages(widgets: List<LauncherWidget>): LauncherLayout {
    val needed = (widgets.maxOfOrNull(LauncherWidget::page) ?: -1) + 1
    return if (needed <= pages.size) this else copy(pages = pages + List(needed - pages.size) { emptyList() })
}

/** Materializes the compatible subset and retains the workspace's own app and widget definitions. */
internal fun Profile.withLauncherProjection(
    state: NovaLauncherState,
    applicationId: String,
    wide: Boolean,
    lastWidgetPage: Int = -1,
): Profile {
    val public = routeByComponent(applicationId)
    val layout = state.layout(wide)
    val generatedIds = folders.filter(AppFolder::isLauncherProjection).mapTo(mutableSetOf(), AppFolder::id)
    val ownedFolders = folders.filterNot { it.id in generatedIds }
    val ownedFolderIds = ownedFolders.mapTo(mutableSetOf(), AppFolder::id)
    val projectedFolders = mutableListOf<AppFolder>()
    fun shared(id: String): Boolean = id in routes || id in generatedIds
    val pageCount = maxOf(pages.size, layout.pages.size, lastWidgetPage + 1).coerceIn(1, 8)
    val mergedPages = List(pageCount) { page ->
        val projected = layout.pages.getOrElse(page) { emptyList() }.mapIndexedNotNull { index, item ->
            when (item) {
                is LauncherItem.App -> public[item.component]
                is LauncherItem.Folder -> {
                    val members = item.apps.mapNotNull(public::get).distinct()
                    if (members.size < 2 || members.size != item.apps.size ||
                        ownedFolders.size + projectedFolders.size >= 64) null else {
                        val name = item.name.take(32)
                        val key = "folder:shared-launcher-${page + 1}-${index + 1}-${folderFingerprint(name, members)}"
                        val id = if (key !in ownedFolderIds) key else generateSequence(1) { it + 1 }
                            .map { "$key-$it" }.first { it !in ownedFolderIds }
                        projectedFolders.add(AppFolder(id, name, members))
                        id
                    }
                }
                else -> null
            }
        }
        mergeSharedSlots(pages.getOrElse(page) { emptyList() }, projected.distinct(), 100, ::shared)
    }
    val mergedDock = mergeSharedSlots(
        dock,
        layout.dock.mapNotNull { (it as? LauncherItem.App)?.let { item -> public[item.component] } }.distinct(),
        5, ::shared,
    )
    val builtin = when (state.appearance.wallpaper) {
        LauncherWallpaper.OCEAN -> "Aurora"
        LauncherWallpaper.MIDNIGHT -> "Midnight"
        LauncherWallpaper.PLUM -> "Dusk"
        LauncherWallpaper.DEVICE -> wallpaper.builtin
    }
    val keptProjections = projectedFolders.filter { folder -> mergedPages.any { folder.id in it } }
    val newIds = keptProjections.mapTo(mutableSetOf(), AppFolder::id)
    return copy(
        pages = mergedPages,
        dock = mergedDock,
        folders = ownedFolders + keptProjections,
        drawerOrder = drawerOrder.filter { it !in generatedIds || it in newIds },
        wallpaper = if (wallpaper.image == null) wallpaper.copy(builtin = builtin) else wallpaper,
        columns = state.appearance.grid.columns(wide).coerceIn(3, 6),
        iconSize = when (state.appearance.iconSize) {
            LauncherIconSize.SMALL -> 48
            LauncherIconSize.MEDIUM -> 60
            LauncherIconSize.LARGE -> 72
        },
    )
}

private val projectionFolderId = Regex("folder:shared-launcher-[1-9][0-9]*-[1-9][0-9]*-([a-f0-9]{16})(?:-[1-9][0-9]*)?")

/** A Primary rename or membership edit gives the folder local ownership. */
private fun AppFolder.isLauncherProjection(): Boolean {
    val recorded = projectionFolderId.matchEntire(id)?.groupValues?.get(1) ?: return false
    return recorded == folderFingerprint(name, apps)
}

private fun folderFingerprint(name: String, members: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256").digest((listOf(name) + members).joinToString("\u0000").encodeToByteArray())
    return HexFormat.of().formatHex(digest, 0, 8)
}

/** Shared items take available positions. Workspace-only items keep their order and capacity. */
private fun <T> mergeSharedSlots(previous: List<T>, shared: List<T>, limit: Int, isShared: (T) -> Boolean): List<T> {
    val capacity = (limit - previous.count { !isShared(it) }).coerceAtLeast(0)
    val values = shared.take(capacity).iterator()
    return buildList {
        previous.forEach { old ->
            if (!isShared(old)) add(old)
            else if (values.hasNext()) add(values.next())
        }
        addAll(values.asSequence())
    }
}
