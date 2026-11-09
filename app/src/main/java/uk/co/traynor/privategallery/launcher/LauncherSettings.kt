package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import kotlin.math.abs

@Composable
internal fun LauncherSettings(
    state: NovaLauncherState,
    decisions: Map<String, Decision>,
    apps: List<LauncherApp>,
    actions: LauncherUiActions,
    defaultHome: Boolean,
    outsideProtection: Boolean,
    protectedBySanctuary: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    LauncherPanel("Launcher settings", onBack) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Home app", style = MaterialTheme.typography.titleMedium)
                Text(if (defaultHome) "Nova Launcher is Android’s current Home app." else "Android is using another Home app.")
                Button(onClick = actions.selectHome) { Text("Android Home app settings") }
                Text("To recover if the launcher cannot open, pull down Android Quick Settings, open Settings, then choose Apps → Default apps → Home app → One UI Home.",
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                HorizontalDivider()
                Text("Layout", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                LauncherToggle("Separate Fold layouts", state.separateDisplays) { actions.save(state.copy(separateDisplays = it)) }
                OutlinedButton(onClick = onEdit) { Text("Edit Home pages, dock and widgets") }
                LauncherOptions("Open NovaOS gesture", LauncherGesture.entries, state.workspaceGesture, {
                    actions.save(state.copy(workspaceGesture = it))
                })
                Text("Use the selected gesture in the empty area below the Home grid.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                HorizontalDivider()
                Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                LauncherAppearanceOptions(state.appearance, { actions.save(state.copy(appearance = it)) }, actions.chooseWallpaper)
            }
            item {
                HorizontalDivider()
                Text("App drawer and search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                LauncherDrawerSettings(state.drawer) { actions.save(state.copy(drawer = it)) }
            }
            item {
                HorizontalDivider()
                Text("Sanctuary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                LauncherPolicySummary(apps, decisions, protectedBySanctuary, outsideProtection)
                Text("App Drawer While Protected", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    listOf("Show All Apps With Locks", "Show Approved Apps Only").forEachIndexed { index, label ->
                        val hideBlocked = index == 1
                        val selected = state.hideBlocked == hideBlocked
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { actions.save(state.copy(hideBlocked = hideBlocked)) },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(label)
                        }
                    }
                }
                LauncherToggle("Protect outside approved apps", outsideProtection, actions.setOutsideProtection)
                Text("Keep the Sanctuary shield over blocked apps opened outside Nova Launcher when Android permits. Standard App Guardian remains available while this is off.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = actions.openSanctuary) { Text("Open Sanctuary") }
                OutlinedButton(onClick = actions.reviewSanctuary) { Text("Review Sanctuary approved apps") }
                if (protectedBySanctuary) TextButton(onClick = actions.pauseSanctuary) { Text("Pause Sanctuary") }
                TextButton(onClick = actions.emergencyPhone) { Text("Phone and emergency access") }
            }
            item {
                HorizontalDivider()
                Text("Layout backup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                Text("Export or restore ordinary launcher layouts, folders, favourites and appearance. Android requires widgets to be added again on a new device.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = actions.exportLayout) { Text("Export layout") }
                    TextButton(onClick = actions.importLayout) { Text("Restore layout") }
                }
            }
        }
    }
}

private data class LauncherFolderEdit(val index: Int?, val value: LauncherItem.Folder?)
private enum class LauncherEditSection { PAGE, DOCK }
private data class LauncherEditorDrag(val section: LauncherEditSection, val from: Int, val y: Float, val target: Int)

private fun LauncherEditSection.itemKey(index: Int): String = "${name.lowercase()}-$index"

@Composable
internal fun LauncherLayoutEditor(
    state: NovaLauncherState,
    layout: LauncherLayout,
    wide: Boolean,
    page: Int,
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    onEdit: (LauncherLayout) -> Unit,
    onPage: (Int) -> Unit,
    actions: LauncherUiActions,
    onBack: () -> Unit,
) {
    val byComponent = remember(apps) { apps.associateBy { it.component } }
    val current = layout.pages[page]
    var folderEdit by remember(page, wide) { mutableStateOf<LauncherFolderEdit?>(null) }
    var appPicker by remember(page, wide) { mutableStateOf(false) }
    var confirmRemoval by remember(page, wide) { mutableStateOf(false) }
    var showCleanup by remember(layout.pages, wide) { mutableStateOf(false) }
    var drag by remember(page, wide) { mutableStateOf<LauncherEditorDrag?>(null) }
    val listState = rememberLazyListState()
    val display = if (!state.separateDisplays) "Shared layout" else if (wide) "Unfolded layout" else "Cover layout"
    fun savePage(next: List<LauncherItem>) {
        val pages = layout.pages.toMutableList()
        pages[page] = next
        onEdit(layout.copy(pages = pages))
    }
    fun label(item: LauncherItem): String = when (item) {
        LauncherItem.Workspace -> "Open NovaOS"
        is LauncherItem.App -> byComponent[item.component]?.label ?: item.component.substringBefore('/') + " (unavailable)"
        is LauncherItem.Folder -> item.name
    }
    fun dragHandle(section: LauncherEditSection, index: Int, count: Int, onMove: (Int) -> Unit): Modifier =
        Modifier.pointerInput(section, index, page, wide, current, layout.dock) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    val entry = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == section.itemKey(index) }
                    if (entry != null) {
                        drag = LauncherEditorDrag(section, index, entry.offset + entry.size / 2f, index)
                    }
                },
                onDrag = { change, amount ->
                    val currentDrag = drag
                    if (currentDrag?.section == section && currentDrag.from == index) {
                        change.consume()
                        val y = currentDrag.y + amount.y
                        val targets = listState.layoutInfo.visibleItemsInfo.associateBy { it.key }
                        val closest = (0 until count).minByOrNull { candidate ->
                            val entry = targets[section.itemKey(candidate)]
                            if (entry == null) Float.POSITIVE_INFINITY else abs(entry.offset + entry.size / 2f - y)
                        }
                        drag = currentDrag.copy(y = y, target = closest ?: index)
                    }
                },
                onDragEnd = {
                    val completed = drag
                    drag = null
                    if (completed?.section == section && completed.from == index && completed.target != index) {
                        onMove(completed.target)
                    }
                },
                onDragCancel = { drag = null },
            )
        }
    LauncherPanel("Edit Home", onBack) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("launcher-layout-editor"), state = listState,
            contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text(display, style = MaterialTheme.typography.titleMedium)
                Text("Page ${page + 1} of ${layout.pages.size}", modifier = Modifier.testTag("launcher-layout-page"))
                Text("Hold and drag a shortcut handle to reorder it, or use its arrow buttons.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = page > 0, onClick = { onPage(page - 1) }) { Text("Previous page") }
                    TextButton(enabled = page < layout.pages.lastIndex, onClick = { onPage(page + 1) }) { Text("Next page") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = layout.pages.size < MAX_LAUNCHER_PAGES, onClick = {
                        onEdit(layout.copy(pages = layout.pages + listOf(emptyList())))
                        onPage(layout.pages.size)
                    }) { Text("Add page") }
                    TextButton(enabled = layout.pages.size > 1, onClick = { confirmRemoval = true }) { Text("Remove page") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = current.size < MAX_LAUNCHER_PAGE_ITEMS, onClick = { appPicker = true }) { Text("Add app") }
                    TextButton(enabled = current.size < MAX_LAUNCHER_PAGE_ITEMS, onClick = {
                        folderEdit = LauncherFolderEdit(null, null)
                    }) { Text("Add folder") }
                    if (LauncherItem.Workspace !in current) TextButton(enabled = current.size < MAX_LAUNCHER_PAGE_ITEMS, onClick = {
                        savePage(current + LauncherItem.Workspace)
                    }) { Text("Add NovaOS") }
                }
            }
            items(current.size, key = { LauncherEditSection.PAGE.itemKey(it) }) { index ->
                val item = current[index]
                val section = LauncherEditSection.PAGE
                val move: (Int) -> Unit = { to -> savePage(current.movingLauncherItem(index, to)) }
                LauncherItemEditRow(label(item), index, current.size,
                    onMove = move,
                    dragHandle = dragHandle(section, index, current.size, move)
                        .testTag("launcher-drag-${section.itemKey(index)}"),
                    dropTarget = drag?.let { it.section == section && it.target == index } == true,
                    onDelete = { savePage(current.filterIndexed { position, _ -> position != index }) },
                    onEdit = if (item is LauncherItem.Folder) ({ folderEdit = LauncherFolderEdit(index, item) }) else null,
                )
            }
            item {
                HorizontalDivider()
                Text("Dock", style = MaterialTheme.typography.titleMedium)
                Text("Long-press an app in the drawer or Home to add it to the dock.")
                if (LauncherItem.Workspace !in layout.dock) TextButton(
                    enabled = layout.dock.size < MAX_LAUNCHER_DOCK_ITEMS,
                    onClick = { onEdit(layout.copy(dock = layout.dock + LauncherItem.Workspace)) },
                ) { Text("Add NovaOS to dock") }
            }
            items(layout.dock.size, key = { LauncherEditSection.DOCK.itemKey(it) }) { index ->
                val section = LauncherEditSection.DOCK
                val move: (Int) -> Unit = { to -> onEdit(layout.copy(dock = layout.dock.movingLauncherItem(index, to))) }
                LauncherItemEditRow(label(layout.dock[index]), index, layout.dock.size,
                    onMove = move,
                    dragHandle = dragHandle(section, index, layout.dock.size, move)
                        .testTag("launcher-drag-${section.itemKey(index)}"),
                    dropTarget = drag?.let { it.section == section && it.target == index } == true,
                    onDelete = { onEdit(layout.copy(dock = layout.dock.filterIndexed { position, _ -> position != index })) },
                )
            }
            item {
                HorizontalDivider()
                Text("Android widgets", style = MaterialTheme.typography.titleMedium)
                Text("Select a Home page to move widgets there. Sanctuary pauses their interactive content during protection.",
                    style = MaterialTheme.typography.bodySmall)
                Button(enabled = state.widgets.size < MAX_LAUNCHER_WIDGETS, onClick = { actions.addWidget(wide && state.separateDisplays) }) {
                    Text("Add Android widget")
                }
            }
            items(state.widgets.filter { it.wide == (wide && state.separateDisplays) }, key = { it.id }) { widget ->
                val packageName = widget.provider.substringBefore('/')
                val widgetLabel = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
                ListItem(
                    headlineContent = { Text(widgetLabel) },
                    supportingContent = {
                        Column {
                            Text("Page ${widget.page + 1}")
                            if (widget.page != page) TextButton(onClick = {
                                actions.save(state.copy(widgets = state.widgets.map {
                                    if (it.id == widget.id) it.copy(page = page) else it
                                }))
                            }) { Text("Move to page ${page + 1}") }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { actions.removeWidget(widget) }) {
                            Icon(Icons.Default.DeleteOutline, "Remove $widgetLabel widget")
                        }
                    },
                )
            }
            item {
                HorizontalDivider()
                Text("Clean up Home shortcuts", style = MaterialTheme.typography.titleMedium)
                Text("Review shortcuts added during earlier setup. You can undo cleanup until the pages are edited again.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = layout.pages.any { items -> items.any { it is LauncherItem.App } },
                    onClick = { showCleanup = true },
                ) { Text("Review shortcuts") }
                if (state.shortcutCleanupUndo(wide) != null) {
                    TextButton(onClick = { actions.save(state.undoShortcutCleanup(wide)) }) { Text("Undo cleanup") }
                }
            }
        }
    }
    if (confirmRemoval) AlertDialog(
        onDismissRequest = { confirmRemoval = false },
        title = { Text("Remove this Home page?") },
        text = { Text("Its shortcuts will be removed. Installed apps stay in the drawer. Widgets move to the nearest remaining page.") },
        dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmRemoval = false
            if (actions.save(state.withoutPage(wide, page))) onPage((page - 1).coerceAtLeast(0))
        }) { Text("Remove page") } },
    )
    if (appPicker) LauncherAppPicker(
        apps.filter { LauncherItem.App(it.component) !in current }, decisions,
        onDismiss = { appPicker = false },
        onSelect = { appPicker = false; savePage(current + LauncherItem.App(it.component)) },
    )
    folderEdit?.let { draft ->
        LauncherFolderEditor(draft.value, apps, onDismiss = { folderEdit = null }) { next ->
            val pages = current.toMutableList()
            if (draft.index == null) pages.add(next) else pages[draft.index] = next
            savePage(pages)
            folderEdit = null
        }
    }
    if (showCleanup) LauncherShortcutCleanup(
        layout, ::label, onDismiss = { showCleanup = false },
        onConfirm = { selected ->
            if (actions.save(state.cleanUpShortcuts(wide, selected))) showCleanup = false
        },
    )
}

@Composable
private fun LauncherItemEditRow(
    name: String,
    index: Int,
    count: Int,
    onMove: (Int) -> Unit,
    dragHandle: Modifier,
    dropTarget: Boolean,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().background(
        if (dropTarget) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ), verticalAlignment = Alignment.CenterVertically) {
        if (count > 1) {
            Box(dragHandle.size(48.dp).semantics { contentDescription = "Drag $name" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DragHandle, contentDescription = null)
            }
        }
        Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onEdit != null) TextButton(onClick = onEdit) { Text("Edit") }
        IconButton(enabled = index > 0, onClick = { onMove(index - 1) }) {
            Icon(Icons.Default.ArrowBack, "Move $name earlier")
        }
        IconButton(enabled = index < count - 1, onClick = { onMove(index + 1) }) {
            Icon(Icons.Default.ArrowForward, "Move $name later")
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Remove $name shortcut") }
    }
}

@Composable
private fun LauncherShortcutCleanup(
    layout: LauncherLayout,
    label: (LauncherItem) -> String,
    onDismiss: () -> Unit,
    onConfirm: (Set<LauncherShortcutPosition>) -> Unit,
) {
    val candidates = remember(layout.pages) {
        layout.pages.flatMapIndexed { page, items ->
            items.mapIndexedNotNull { index, item ->
                (item as? LauncherItem.App)?.let { LauncherShortcutPosition(page, index) to it }
            }
        }
    }
    var selected by remember(candidates) { mutableStateOf(emptySet<LauncherShortcutPosition>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review Home shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the shortcuts to remove. Folders, dock shortcuts and widgets stay in place.")
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(candidates) { (position, item) ->
                        val checked = position in selected
                        Row(
                            Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = {
                                selected = if (it) selected + position else selected - position
                            }).heightIn(min = 48.dp).testTag("launcher-cleanup-${position.page}-${position.item}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Text("Page ${position.page + 1} · ${label(item)}")
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected) }) {
                Text(if (selected.size == 1) "Remove 1 shortcut" else "Remove ${selected.size} shortcuts")
            }
        },
    )
}

@Composable
private fun LauncherAppPicker(
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    onDismiss: () -> Unit,
    onSelect: (LauncherApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app shortcut") },
        text = {
            Column {
                OutlinedTextField(query, { query = it.take(100) }, singleLine = true, label = { Text("Find an app") })
                LazyColumn(Modifier.heightIn(max = 270.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.component }) { app ->
                        TextButton(onClick = { onSelect(app) }) {
                            Text(if (decisions[app.packageName]?.canLaunch == false) "${app.label} · Blocked" else app.label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LauncherFolderEditor(
    existing: LauncherItem.Folder?,
    apps: List<LauncherApp>,
    onDismiss: () -> Unit,
    onSave: (LauncherItem.Folder) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var query by remember(existing) { mutableStateOf("") }
    var selection by remember(existing) { mutableStateOf(existing?.apps.orEmpty().toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New folder" else "Edit folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(64) }, label = { Text("Folder name") }, singleLine = true)
                OutlinedTextField(query, { query = it.take(100) }, label = { Text("Find an app") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 270.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.component }) { app ->
                        val checked = app.component in selection
                        LauncherToggle(app.label, checked) { select ->
                            if (!select) selection = selection - app.component
                            else if (selection.size < MAX_LAUNCHER_FOLDER_ITEMS) selection = selection + app.component
                        }
                    }
                }
                Text("${selection.size} selected", style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(enabled = name.isNotBlank() && selection.isNotEmpty(), onClick = {
                onSave(LauncherItem.Folder(name.trim(), selection.toList()))
            }) { Text("Save folder") }
        },
    )
}
