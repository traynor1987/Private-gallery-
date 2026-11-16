package uk.co.traynor.privategallery.launcher

/** A null page refers to the dock. The index is in the saved list, including hidden items. */
internal data class LauncherSlot(val page: Int?, val index: Int)
internal data class LauncherDrop(val slot: LauncherSlot, val group: Boolean = false)

internal fun LauncherLayout.itemsAt(page: Int?): List<LauncherItem>? =
    if (page == null) dock else pages.getOrNull(page)

internal fun LauncherLayout.uniqueFolderSlot(folder: LauncherItem.Folder): LauncherSlot? = buildList {
    pages.forEachIndexed { page, items ->
        items.forEachIndexed { index, item -> if (item == folder) add(LauncherSlot(page, index)) }
    }
    dock.forEachIndexed { index, item -> if (item == folder) add(LauncherSlot(null, index)) }
}.singleOrNull()

internal fun LauncherLayout.replaceFolderAt(
    slot: LauncherSlot,
    expected: LauncherItem.Folder,
    replacement: LauncherItem.Folder,
): LauncherLayout? {
    val items = itemsAt(slot.page) ?: return null
    if (items.getOrNull(slot.index) != expected) return null
    return withItemsAt(slot.page, items.mapIndexed { index, item -> if (index == slot.index) replacement else item })
}

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

/** Move one stored folder member to the slot immediately after its source. */
internal fun LauncherLayout.moveFolderMemberOut(
    slot: LauncherSlot,
    expected: LauncherItem.Folder,
    component: String,
): LauncherLayout? {
    val items = itemsAt(slot.page) ?: return null
    if (items.getOrNull(slot.index) != expected || component !in expected.apps ||
        items.any { it == LauncherItem.App(component) }
    ) return null
    val remaining = expected.apps.filterNot { it == component }
    val capacity = if (slot.page == null) MAX_LAUNCHER_DOCK_ITEMS else MAX_LAUNCHER_PAGE_ITEMS
    if (remaining.isNotEmpty() && items.size >= capacity) return null
    val next = items.toMutableList()
    if (remaining.isEmpty()) next.removeAt(slot.index)
    else next[slot.index] = if (remaining.size == 1) LauncherItem.App(remaining.single())
        else expected.copy(apps = remaining)
    next.add(slot.index + if (remaining.isEmpty()) 0 else 1, LauncherItem.App(component))
    return withItemsAt(slot.page, next)
}
