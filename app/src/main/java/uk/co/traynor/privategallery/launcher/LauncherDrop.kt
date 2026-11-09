package uk.co.traynor.privategallery.launcher

/** A null page refers to the dock. The index is in the saved list, including hidden items. */
internal data class LauncherSlot(val page: Int?, val index: Int)
internal data class LauncherDrop(val slot: LauncherSlot, val group: Boolean = false)

internal fun LauncherLayout.itemsAt(page: Int?): List<LauncherItem>? =
    if (page == null) dock else pages.getOrNull(page)

internal fun LauncherLayout.dropLauncherItem(
    item: LauncherItem,
    source: LauncherSlot?,
    drop: LauncherDrop,
): LauncherLayout {
    val (page, index) = drop.slot
    val destination = itemsAt(page) ?: return this
    if (index !in 0..destination.size || source == drop.slot) return this
    val sourceItems = source?.let { itemsAt(it.page) }
    if (source != null && sourceItems?.getOrNull(source.index) != item) return this
    val sameList = source != null && source.page == page
    val grouped = if (drop.group) {
        groupLauncherApp(item, destination.getOrNull(index)) ?: return this
    } else {
        val capacity = if (page == null) MAX_LAUNCHER_DOCK_ITEMS else MAX_LAUNCHER_PAGE_ITEMS
        if (!sameList && (destination.size >= capacity || item in destination)) return this
        null
    }

    val updated = if (source != null && sourceItems != null) {
        withItemsAt(source.page, sourceItems.toMutableList().apply { removeAt(source.index) })
    } else this
    val insertion = if (sameList && source.index < index) index - 1 else index
    val items = checkNotNull(updated.itemsAt(page)).toMutableList()
    if (grouped == null) items.add(insertion, item) else items[insertion] = grouped
    return updated.withItemsAt(page, items)
}

internal fun LauncherLayout.withItemsAt(page: Int?, items: List<LauncherItem>): LauncherLayout =
    if (page == null) copy(dock = items) else copy(pages = pages.mapIndexed { index, current ->
        if (index == page) items else current
    })

internal fun groupLauncherApp(item: LauncherItem, target: LauncherItem?): LauncherItem.Folder? {
    if (item !is LauncherItem.App) return null
    return when (target) {
        is LauncherItem.App -> if (target == item) null
            else LauncherItem.Folder("Folder", listOf(target.component, item.component))
        is LauncherItem.Folder -> if (item.component in target.apps || target.apps.size >= MAX_LAUNCHER_FOLDER_ITEMS) null
            else target.copy(apps = target.apps + item.component)
        else -> null
    }
}
