package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision

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
                LauncherToggle("Hide blocked apps", state.hideBlocked) { actions.save(state.copy(hideBlocked = it)) }
                LauncherToggle("Protect outside approved apps", outsideProtection, actions.setOutsideProtection)
                Text("Keep the Sanctuary shield over blocked apps opened outside Nova Launcher when Android permits. Standard App Guardian remains available while this is off.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = actions.reviewSanctuary) { Text("Review Sanctuary approved apps") }
                TextButton(onClick = actions.pauseSanctuary) { Text("Pause Sanctuary") }
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
    LauncherPanel("Edit Home", onBack) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("launcher-layout-editor"),
            contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text(display, style = MaterialTheme.typography.titleMedium)
                Text("Page ${page + 1} of ${layout.pages.size}", modifier = Modifier.testTag("launcher-layout-page"))
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
            items(current.size) { index ->
                val item = current[index]
                LauncherItemEditRow(label(item), index, current.size,
                    onMove = { to -> savePage(current.moving(index, to)) },
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
            items(layout.dock.size) { index ->
                LauncherItemEditRow(label(layout.dock[index]), index, layout.dock.size,
                    onMove = { to -> onEdit(layout.copy(dock = layout.dock.moving(index, to))) },
                    onDelete = { onEdit(layout.copy(dock = layout.dock.filterIndexed { position, _ -> position != index })) },
                )
            }
            item {
                HorizontalDivider()
                Text("Android widgets", style = MaterialTheme.typography.titleMedium)
                Text("Widgets appear on the first page. Sanctuary pauses their interactive content during protection.",
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
                    trailingContent = {
                        IconButton(onClick = { actions.removeWidget(widget) }) {
                            Icon(Icons.Default.DeleteOutline, "Remove $widgetLabel widget")
                        }
                    },
                )
            }
        }
    }
    if (confirmRemoval) AlertDialog(
        onDismissRequest = { confirmRemoval = false },
        title = { Text("Remove this Home page?") },
        text = { Text("Its shortcuts will be removed from this page. Installed apps remain available in the drawer.") },
        dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmRemoval = false
            onEdit(layout.copy(pages = layout.pages.filterIndexed { index, _ -> index != page }))
            onPage((page - 1).coerceAtLeast(0))
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
}

@Composable
private fun LauncherItemEditRow(
    name: String,
    index: Int,
    count: Int,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

private fun <T> List<T>.moving(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

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
