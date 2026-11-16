package uk.co.traynor.privategallery.launcher

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision

internal enum class LauncherFolderMode(val label: String) {
    COMPACT("Compact grid"), EXPANDED("Expanded grid"), LIST("List view");

    fun columns(wide: Boolean): Int = when (this) {
        COMPACT -> if (wide) 4 else 3
        EXPANDED -> if (wide) 6 else 4
        LIST -> 1
    }
}

internal data class LauncherFolderAppearance(
    val mode: LauncherFolderMode = LauncherFolderMode.COMPACT,
    val iconSize: LauncherIconSize? = null,
    val labels: Boolean = true,
    val opaque: Boolean = true,
) {
    fun save(context: Context) {
        prefs(context).edit().putString("mode", mode.name).putString("icon_size", iconSize?.name)
            .putBoolean("labels", labels).putBoolean("opaque", opaque).apply()
    }

    companion object {
        private fun prefs(context: Context) =
            context.getSharedPreferences("nova_launcher_folder_appearance", Context.MODE_PRIVATE)

        fun read(context: Context): LauncherFolderAppearance {
            val prefs = prefs(context)
            return LauncherFolderAppearance(
                mode = LauncherFolderMode.entries.find { it.name == prefs.getString("mode", null) }
                    ?: LauncherFolderMode.COMPACT,
                iconSize = LauncherIconSize.entries.find { it.name == prefs.getString("icon_size", null) },
                labels = prefs.getBoolean("labels", true),
                opaque = prefs.getBoolean("opaque", true),
            )
        }
    }
}

@Composable
internal fun LauncherFolderAppearanceSettings() {
    val context = LocalContext.current
    var appearance by remember(context) { mutableStateOf(LauncherFolderAppearance.read(context)) }
    fun update(next: LauncherFolderAppearance) {
        appearance = next
        next.save(context)
    }

    Text("Folder appearance", style = MaterialTheme.typography.titleMedium)
    FolderChoice("Folder layout", appearance.mode, LauncherFolderMode.entries, LauncherFolderMode::label) {
        update(appearance.copy(mode = it))
    }
    FolderChoice("Folder icon size", appearance.iconSize, listOf(null) + LauncherIconSize.entries, {
        it?.name?.lowercase()?.replaceFirstChar(Char::uppercaseChar) ?: "Match Home"
    }) { update(appearance.copy(iconSize = it)) }
    LauncherToggle("Show folder app labels", appearance.labels) { update(appearance.copy(labels = it)) }
    LauncherToggle("Solid folder background", appearance.opaque) { update(appearance.copy(opaque = it)) }
}

@Composable
private fun <T> FolderChoice(title: String, value: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$title · ${label(value)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
internal fun LauncherFolderPanel(
    folder: LauncherItem.Folder,
    apps: Map<String, LauncherApp>,
    visible: (String) -> Boolean,
    decisions: Map<String, Decision>,
    appearance: LauncherAppearance,
    preferences: LauncherFolderAppearance,
    onLaunch: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: ((LauncherItem.Folder) -> Boolean)?,
    onMoveOut: ((String) -> Boolean)? = null,
) {
    var mode by rememberSaveable(preferences.mode) { mutableStateOf(preferences.mode) }
    var editing by rememberSaveable(folder) { mutableStateOf(false) }
    var picking by rememberSaveable(folder) { mutableStateOf(false) }
    var name by rememberSaveable(folder) { mutableStateOf(folder.name) }
    var keys by rememberSaveable(folder) { mutableStateOf(ArrayList(folder.apps)) }
    var error by remember(folder) { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entering = true }
    LaunchedEffect(onSave != null) {
        if (onSave == null) {
            editing = false
            picking = false
            name = folder.name
            keys = ArrayList(folder.apps)
        }
    }
    val scale by animateFloatAsState(if (entering) 1f else .96f, label = "Folder open")
    val alpha by animateFloatAsState(if (entering) 1f else .65f, label = "Folder opacity")
    val iconAppearance = appearance.copy(
        iconStyle = LauncherIconStyle.FULL_ICONS,
        iconSize = preferences.iconSize ?: appearance.iconSize,
        labels = preferences.labels,
    )
    val members = folder.apps.filter(visible).mapNotNull(apps::get)
    val editVisible = editing && onSave != null
    val pickVisible = editVisible && picking
    Dialog(onDismissRequest = {
        if (pickVisible) picking = false else onDismiss()
    }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val wide = maxWidth >= LauncherExpandedWidth.dp
            val panelWidth = if (wide && (mode == LauncherFolderMode.EXPANDED || editVisible)) 640.dp
                else if (wide) 480.dp else 440.dp
            val panelHeight = maxHeight * if (mode == LauncherFolderMode.EXPANDED || editVisible) .93f else .73f
            Box(Modifier.matchParentSize().pointerInput(pickVisible, onDismiss) {
                detectTapGestures { if (pickVisible) picking = false else onDismiss() }
            })
            val color = MaterialTheme.colorScheme.surfaceContainerHigh
                .copy(alpha = if (preferences.opaque) 1f else .86f)
            Surface(
                Modifier.align(Alignment.Center).padding(horizontal = 14.dp).widthIn(max = panelWidth)
                    .fillMaxWidth().heightIn(max = panelHeight)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .testTag("launcher-folder-panel"),
                shape = RoundedCornerShape(28.dp),
                color = color,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(
                                when {
                                    pickVisible -> "Add applications"
                                    editVisible -> "Edit folder"
                                    else -> folder.name
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!editVisible) Text(
                                if (members.size == 1) "1 app" else "${members.size} apps",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!editVisible) {
                            IconButton(onClick = {
                                mode = if (mode == LauncherFolderMode.EXPANDED) preferences.mode
                                    .takeUnless { it == LauncherFolderMode.EXPANDED } ?: LauncherFolderMode.COMPACT
                                    else LauncherFolderMode.EXPANDED
                            }) {
                                val expanded = mode == LauncherFolderMode.EXPANDED
                                Icon(if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                    if (expanded) "Compact folder" else "Expand folder")
                            }
                            if (onSave != null) IconButton(onClick = { editing = true }) {
                                Icon(Icons.Default.Edit, "Edit folder")
                            }
                        }
                        IconButton(onClick = { if (pickVisible) picking = false else onDismiss() }) {
                            Icon(Icons.Default.Close, if (pickVisible) "Back to editing" else "Close folder")
                        }
                    }
                    when {
                        pickVisible -> FolderAppPicker(apps.values.toList(), keys, Modifier.weight(1f, fill = false)) {
                            if (keys.size < MAX_LAUNCHER_FOLDER_ITEMS && it !in keys) keys = ArrayList(keys + it)
                        }
                        editVisible -> {
                            OutlinedTextField(
                                name, { name = it.take(64) }, label = { Text("Folder name") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), singleLine = true,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { picking = true }, enabled = keys.size < MAX_LAUNCHER_FOLDER_ITEMS) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Add apps")
                                }
                                FolderChoice("Sort", "Stored order", listOf("A–Z", "Z–A"), { it }) { sort ->
                                    val known = keys.mapNotNull(apps::get).sortedWith(
                                        compareBy(String.CASE_INSENSITIVE_ORDER, LauncherApp::label),
                                    ).let { if (sort == "Z–A") it.asReversed() else it }.iterator()
                                    keys = ArrayList(keys.map { if (it in apps) known.next().component else it })
                                }
                            }
                            val moveOut = onMoveOut?.takeIf { keys == folder.apps && name == folder.name }
                            if (moveOut != null) Text("Drag an app below the list to move it beside this folder.",
                                style = MaterialTheme.typography.bodySmall)
                            FolderReorderList(keys, apps, Modifier.weight(1f, fill = false), onMove = { from, to ->
                                keys = ArrayList(keys.movingLauncherItem(from, to))
                            }, onRemove = { keys = ArrayList(keys - it) }, onMoveOut = moveOut?.let { move ->
                                { component -> move(component).also { error = !it } }
                            })
                            if (error) Text("The folder changed or could not be saved.",
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    editing = false
                                    name = folder.name
                                    keys = ArrayList(folder.apps)
                                    error = false
                                }) { Text("Cancel") }
                                Button(enabled = keys.isEmpty() || name.isNotBlank(), onClick = {
                                    val next = LauncherItem.Folder(name.trim(), keys.toList())
                                    if (onSave?.invoke(next) == true) editing = false else error = true
                                }) { Text("Save folder") }
                            }
                        }
                        members.isEmpty() -> Text("No available apps in this folder.", Modifier.padding(16.dp))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(mode.columns(wide)),
                            modifier = Modifier.weight(1f, fill = false).testTag("launcher-folder-apps"),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(members, key = LauncherApp::component) { app ->
                                val blocked = decisions.forLauncherApp(app)?.canLaunch == false
                                val launch = { onLaunch(app.component) }
                                if (mode == LauncherFolderMode.LIST) {
                                    FolderListApp(app, iconAppearance, blocked, launch)
                                } else {
                                    LauncherTile(
                                        LauncherItem.App(app.component), iconAppearance, apps, decisions, launch,
                                        modifier = Modifier.testTag("launcher-folder-app-${app.component}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderListApp(
    app: LauncherApp,
    appearance: LauncherAppearance,
    blocked: Boolean,
    onLaunch: () -> Unit,
) {
    val label = app.profileLabel?.let { "${app.label} · $it" } ?: app.label
    val description = if (blocked) "$label, blocked by Sanctuary" else label
    val size = (60.dp * appearance.iconSize.previewScale)
    Row(
        Modifier.fillMaxWidth().testTag("launcher-folder-app-${app.component}")
            .clickable(role = Role.Button, onClick = onLaunch)
            .semantics { contentDescription = description }.heightIn(min = 56.dp).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherArtworkIcon(app, appearance, Modifier.size(size))
        if (appearance.labels) Text(label, Modifier.weight(1f).clearAndSetSemantics {},
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (blocked) Icon(Icons.Default.Lock, null)
    }
}

@Composable
private fun FolderAppPicker(apps: List<LauncherApp>, keys: List<String>, modifier: Modifier, onAdd: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier) {
        OutlinedTextField(query, { query = it.take(100) }, label = { Text("Find an app") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), singleLine = true)
        val available = remember(apps, keys, query) {
            apps.filter { it.component !in keys && it.label.contains(query, ignoreCase = true) }
        }
        if (available.isEmpty()) Text("No matching apps.", Modifier.padding(12.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(available, key = LauncherApp::component) { app ->
                TextButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = keys.size < MAX_LAUNCHER_FOLDER_ITEMS,
                    onClick = { onAdd(app.component) },
                ) {
                    Text(app.profileLabel?.let { "${app.label} · $it" } ?: app.label)
                }
            }
        }
    }
}

@Composable
private fun FolderReorderList(
    keys: List<String>,
    apps: Map<String, LauncherApp>,
    modifier: Modifier,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    onMoveOut: ((String) -> Boolean)?,
) {
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    val moveOut by rememberUpdatedState(onMoveOut)
    val exitDistance = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableIntStateOf(-1) }
    LazyColumn(modifier.testTag("launcher-folder-order"), state = list) {
        itemsIndexed(keys, key = { _, key -> key }) { index, key ->
            val app = apps[key]
            val label = app?.let { it.profileLabel?.let { profile -> "${it.label} · $profile" } ?: it.label }
                ?: "Unavailable application"
            val highlight = dragging != null && target == index
            Surface(color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(
                        Modifier.size(48.dp).semantics {
                            contentDescription = "Reorder $label"
                            customActions = buildList {
                                if (onMoveOut != null) add(CustomAccessibilityAction("Move out of folder") { moveOut?.invoke(key) == true })
                                if (index > 0) add(CustomAccessibilityAction("Move up") { move(index, index - 1); true })
                                if (index < keys.lastIndex) add(CustomAccessibilityAction("Move down") { move(index, index + 1); true })
                            }
                        }.pointerInput(key, index) {
                            var position = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                    position = item?.let { it.offset + it.size / 2f } ?: 0f
                                    dragging = key
                                    target = index
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    position += delta.y
                                    target = list.layoutInfo.visibleItemsInfo.minByOrNull {
                                        kotlin.math.abs(it.offset + it.size / 2f - position)
                                    }?.index ?: index
                                },
                                onDragEnd = {
                                    if (position > list.layoutInfo.viewportEndOffset + exitDistance && moveOut != null) {
                                        moveOut?.invoke(key)
                                    } else if (target in keys.indices) move(index, target)
                                    dragging = null
                                    target = -1
                                },
                                onDragCancel = { dragging = null; target = -1 },
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.DragHandle, null) }
                    IconButton(onClick = { onRemove(key) }) {
                        Icon(Icons.Default.RemoveCircleOutline, "Remove $label")
                    }
                }
            }
        }
    }
}
