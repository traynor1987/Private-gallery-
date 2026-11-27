package uk.co.traynor.privategallery.novaos2

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.home2.CalculatorScreen
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.novaos2.desktop.DESKTOP_MAX_COMPONENT_LENGTH
import uk.co.traynor.privategallery.novaos2.desktop.DESKTOP_MAX_PINS
import uk.co.traynor.privategallery.novaos2.desktop.DESKTOP_MAX_RATIO
import uk.co.traynor.privategallery.novaos2.desktop.DESKTOP_MIN_RATIO
import uk.co.traynor.privategallery.novaos2.desktop.DesktopPlacement
import uk.co.traynor.privategallery.novaos2.desktop.publicDesktopDisplays
import uk.co.traynor.privategallery.novaos2.desktop.DesktopPanel
import uk.co.traynor.privategallery.novaos2.desktop.DesktopPreset
import uk.co.traynor.privategallery.novaos2.desktop.DesktopSettings

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun NovaDesktopMode(host: NovaSystemHost) {
    val context = LocalContext.current
    val config = remember(context) { NovaSystemConfig(context, "desktop-mode") }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<DesktopSettings?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var launchDestination by remember { mutableStateOf<DesktopPlacement?>(null) }
    val externalDisplays = desktopDisplays()
    var pinning by remember { mutableStateOf(false) }
    var changePanel by remember { mutableStateOf<Boolean?>(null) }
    var coverLeft by rememberSaveable { mutableStateOf(true) }
    var previewRatio by remember { mutableIntStateOf(50) }
    val focus = remember { FocusRequester() }
    val personalApps = remember(host.apps, context.packageName) {
        host.apps.filter {
            it.profile == null && !it.packageName.startsWith("nova:") && it.packageName != context.packageName &&
                it.component.length <= DESKTOP_MAX_COMPONENT_LENGTH
        }
    }
    val localCalculator = remember {
        movableContentOf<() -> Unit> { onClose ->
            Box(Modifier.height(470.dp)) { CalculatorScreen(launcherSession = true, onHome = onClose) }
        }
    }
    val filePanel = remember {
        movableContentOf<() -> Boolean, () -> Unit> { allowed, onOpen -> DesktopFilesPanel(allowed, onOpen) }
    }

    LaunchedEffect(config) {
        try { settings = withContext(Dispatchers.IO) { DesktopSettings.decode(config.read()) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { notice = "Saved Desktop Mode settings could not be opened." }
    }

    val current = settings
    if (current == null) {
        Text(notice ?: "Opening Desktop Mode…", Modifier.padding(16.dp))
        return
    }
    val layout = current.current
    LaunchedEffect(current.preset, layout.splitPercent) { previewRatio = layout.splitPercent }
    LaunchedEffect(focus) { focus.requestFocus() }

    fun save(next: DesktopSettings, afterSave: () -> Unit = {}) {
        if (busy || !host.foregroundAllowed() || next == current) return
        busy = true
        notice = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { config.write(next.encode()) }
                settings = next
                afterSave()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                previewRatio = current.current.splitPercent
                notice = "Workspace changes could not be saved."
            } finally { busy = false }
        }
    }
    fun launchCore(app: NovaCoreApp) {
        if (!host.openCoreApp(app)) notice = "${app.label} could not open."
    }
    fun launchPinned(app: LauncherApp) {
        if (!host.launch(app)) notice = "${app.label} could not open."
    }

    val pinnedApps = layout.pins.map { id -> id to personalApps.firstOrNull { it.component == id } }
    val openSection: @Composable (Boolean) -> Unit = { left ->
        val panel = if (left) layout.left else layout.right
        DesktopWindow(panel, busy, onSelect = { changePanel = left }) {
            when (panel) {
                DesktopPanel.NOVA_APPS -> {
                    listOf(NovaCoreApp.NOTES, NovaCoreApp.TASKS, NovaCoreApp.CALENDAR, NovaCoreApp.FILES).forEach { app ->
                        Button(onClick = { launchCore(app) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(app.label)
                        }
                    }
                }
                DesktopPanel.PINNED -> {
                    if (pinnedApps.isEmpty()) Text("Pin an installed app to this workspace.")
                    pinnedApps.forEachIndexed { index, (id, app) ->
                        DesktopPinRow(app?.label, onOpen = app?.let { { launchPinned(it) } }, busy = busy,
                            up = index > 0, down = index < pinnedApps.lastIndex,
                            onMove = { delta -> save(current.update(layout.reorder(id, delta))) },
                            onRemove = { save(current.update(layout.copy(pins = layout.pins - id))) })
                    }
                    TextButton(enabled = !busy && layout.pins.size < DESKTOP_MAX_PINS, onClick = { pinning = true }) {
                        Icon(Icons.Default.Add, null)
                        Text("Pin app", Modifier.padding(start = 6.dp))
                    }
                }
                DesktopPanel.FILES -> filePanel(host.foregroundAllowed) { launchCore(NovaCoreApp.FILES) }
                DesktopPanel.CALCULATOR -> localCalculator { changePanel = left }
            }
        }
    }

    Column(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyUp || !event.isAltPressed || !host.foregroundAllowed()) return@onPreviewKeyEvent false
        val preset = when (event.key) {
            Key.One -> DesktopPreset.PRODUCTIVITY
            Key.Two -> DesktopPreset.DEVELOPMENT
            Key.Three -> DesktopPreset.GAMING
            else -> return@onPreviewKeyEvent false
        }
        save(current.copy(preset = preset))
        true
    }.focusable().testTag("desktop-workspace")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NovaSystemSection("Workspaces") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopPreset.entries.forEach { preset ->
                        FilterChip(selected = current.preset == preset, enabled = !busy,
                            onClick = { save(current.copy(preset = preset)) }, label = { Text(preset.label) })
                    }
                }
                Text("Keyboard  Alt+1, Alt+2 or Alt+3", style = MaterialTheme.typography.bodySmall)
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (host.wide && maxWidth >= 640.dp) {
                    val usablePx = with(LocalDensity.current) { (maxWidth - 48.dp).toPx() }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().testTag("desktop-split"), verticalAlignment = Alignment.Top) {
                            Box(Modifier.weight(previewRatio / 100f)) { openSection(true) }
                            DesktopResizeHandle(usablePx, previewRatio, layout.splitPercent, !busy,
                                onResize = { next -> previewRatio = next },
                                onSave = { next -> save(current.update(layout.resize(next))) })
                            Box(Modifier.weight((100 - previewRatio) / 100f)) { openSection(false) }
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                            TextButton(enabled = !busy && layout.splitPercent > DESKTOP_MIN_RATIO,
                                onClick = { save(current.update(layout.resize(layout.splitPercent - 5))) }) { Text("Narrow left") }
                            Text("${layout.splitPercent} / ${100 - layout.splitPercent}", Modifier.testTag("desktop-split-ratio"))
                            TextButton(enabled = !busy && layout.splitPercent < DESKTOP_MAX_RATIO,
                                onClick = { save(current.update(layout.resize(layout.splitPercent + 5))) }) { Text("Widen left") }
                        }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("desktop-compact")) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = coverLeft, onClick = { coverLeft = true }, label = { Text(layout.left.label) })
                        FilterChip(selected = !coverLeft, onClick = { coverLeft = false }, label = { Text(layout.right.label) })
                    }
                    openSection(coverLeft)
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Taskbar", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launchCore(NovaCoreApp.NOTES) }) { Text("Notes") }
                    Button(onClick = { launchCore(NovaCoreApp.FILES) }) { Text("Files") }
                    pinnedApps.mapNotNull { it.second }.forEach { app ->
                        Button(onClick = { launchPinned(app) }) { Text(app.label, maxLines = 1) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = personalApps.isNotEmpty(), onClick = { launchDestination = DesktopPlacement.Adjacent }) {
                        Text("Open beside Nova")
                    }
                    externalDisplays.forEach { (id, name) ->
                        TextButton(enabled = personalApps.isNotEmpty(), onClick = { launchDestination = DesktopPlacement.External(id) }) {
                            Text("Open on $name")
                        }
                    }
                }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        }
    }

    launchDestination?.let { destination ->
        val destinationPresent = destination !is DesktopPlacement.External || externalDisplays.any { it.first == destination.displayId }
        AlertDialog(onDismissRequest = { launchDestination = null }, title = { Text("Choose an app") }, text = {
            Column {
                Text(when {
                    !destinationPresent -> "The selected display disconnected."
                    destination == DesktopPlacement.Adjacent -> "Android chooses the final layout when you request split screen."
                    else -> "Android checks whether this app can open on the selected display."
                })
                if (destinationPresent) LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(personalApps, key = LauncherApp::component) { app ->
                        TextButton(onClick = {
                            launchDestination = null
                            notice = if (host.launchPlaced(app, destination)) null else "Could not open ${app.label} in that location."
                        }, modifier = Modifier.fillMaxWidth()) { Text(app.label) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { launchDestination = null }) { Text("Close") } })
    }

    if (pinning) {
        val candidates = personalApps.filterNot { it.component in layout.pins }
        AlertDialog(onDismissRequest = { pinning = false }, title = { Text("Pin an app") }, text = {
            if (candidates.isEmpty()) Text("No additional eligible apps are installed.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(candidates, key = LauncherApp::component) { app ->
                    TextButton(enabled = !busy && layout.pins.size < DESKTOP_MAX_PINS, onClick = {
                        save(current.update(layout.pin(app.component))) { pinning = false }
                    }, modifier = Modifier.fillMaxWidth()) { Text(app.label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { pinning = false }) { Text("Close") } })
    }
    changePanel?.let { left ->
        AlertDialog(onDismissRequest = { changePanel = null }, title = { Text("Choose a panel") }, text = {
            Column {
                val existing = if (left) layout.left else layout.right
                DesktopPanel.entries.filter { it != existing }.forEach { panel ->
                    TextButton(enabled = !busy, onClick = {
                        save(current.update(layout.choosePanel(left, panel))) { changePanel = null }
                    }, modifier = Modifier.fillMaxWidth()) { Text(panel.label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { changePanel = null }) { Text("Close") } })
    }
}

@Composable
private fun DesktopWindow(
    panel: DesktopPanel,
    busy: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(panel.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSelect, enabled = !busy, modifier = Modifier.semantics {
                    contentDescription = "Change ${panel.label} panel"
                }) { Text("Change") }
            }
            content()
        }
    }
}

@Composable
private fun DesktopResizeHandle(usablePx: Float, percent: Int, savedPercent: Int, enabled: Boolean,
                                onResize: (Int) -> Unit, onSave: (Int) -> Unit) {
    val currentPercent by androidx.compose.runtime.rememberUpdatedState(percent)
    val resize by androidx.compose.runtime.rememberUpdatedState(onResize)
    val save by androidx.compose.runtime.rememberUpdatedState(onSave)
    Box(Modifier.width(48.dp).height(136.dp).semantics { contentDescription = "Drag to resize panels" }
        .pointerInput(usablePx, enabled, savedPercent) {
            if (!enabled || usablePx <= 0) return@pointerInput
            var dragPercent = currentPercent.toFloat()
            detectHorizontalDragGestures(
                onDragStart = { dragPercent = currentPercent.toFloat() },
                onDragEnd = { save(dragPercent.roundToInt()) },
                onDragCancel = { resize(savedPercent) },
                onHorizontalDrag = { change, pixels ->
                    change.consume()
                    dragPercent = (dragPercent + pixels / usablePx * 100f)
                        .coerceIn(DESKTOP_MIN_RATIO.toFloat(), DESKTOP_MAX_RATIO.toFloat())
                    resize(dragPercent.roundToInt())
                },
            )
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DesktopPinRow(label: String?, onOpen: (() -> Unit)?, busy: Boolean, up: Boolean, down: Boolean,
                          onMove: (Int) -> Unit, onRemove: () -> Unit) {
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    val dragThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label ?: "App unavailable", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (onOpen != null) TextButton(onClick = onOpen) { Text("Open") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).semantics { contentDescription = "Drag ${label ?: "shortcut"} to reorder" }
                .pointerInput(dragThreshold, busy, up, down) {
                    if (busy) return@pointerInput
                    var distance = 0f
                    detectVerticalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                        when {
                            distance >= dragThreshold && down -> move(1)
                            distance <= -dragThreshold && up -> move(-1)
                        }
                    }, onVerticalDrag = { change, delta -> change.consume(); distance += delta })
                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragHandle, null) }
            IconButton(enabled = up && !busy, onClick = { onMove(-1) }) { Icon(Icons.Default.ArrowUpward, "Move shortcut up") }
            IconButton(enabled = down && !busy, onClick = { onMove(1) }) { Icon(Icons.Default.ArrowDownward, "Move shortcut down") }
            IconButton(enabled = !busy, onClick = onRemove) { Icon(Icons.Default.Close, "Remove shortcut") }
        }
    }
}

private data class DesktopFileSelection(val type: String?, val bytes: Long?)

@Composable
private fun DesktopFilesPanel(allowed: () -> Boolean, onOpenNova: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<DesktopFileSelection?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && allowed()) scope.launch {
            val result = withContext(Dispatchers.IO) { describeDesktopFile(context, uri) }
            if (allowed()) {
                selected = result
                message = if (result == null) "Android could not read the selected document’s details." else null
            }
        }
    }
    Button(onClick = {
        if (allowed() && runCatching { picker.launch(arrayOf("*/*")) }.isFailure) message = "Android's file picker is unavailable."
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Default.FolderOpen, null)
        Text("Browse Android files", Modifier.padding(start = 8.dp))
    }
    TextButton(onClick = onOpenNova, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Nova Files") }
    selected?.let {
        Text("Selected document", style = MaterialTheme.typography.titleSmall)
        Text(it.type ?: "Type unavailable")
        Text(it.bytes?.let { bytes -> "${NumberFormat.getNumberInstance().format(bytes)} bytes" } ?: "Size unavailable")
        TextButton(onClick = { selected = null }) { Text("Clear selection") }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
}

private fun describeDesktopFile(context: Context, uri: Uri): DesktopFileSelection? = runCatching {
    if (uri.scheme != "content") return null
    val resolver = context.contentResolver
    val bytes = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { row ->
        if (row.moveToFirst() && !row.isNull(0)) row.getLong(0).takeIf { it >= 0 } else null
    }
    DesktopFileSelection(resolver.getType(uri)?.take(100), bytes)
}.getOrNull()

@Composable
private fun desktopDisplays(): List<Pair<Int, String>> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
    fun current() = publicDesktopDisplays(context).map { it.displayId to it.name }
    val displays by produceState(emptyList<Pair<Int, String>>(), context, manager, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) { value = current() }
                override fun onDisplayChanged(displayId: Int) { value = current() }
                override fun onDisplayRemoved(displayId: Int) { value = current() }
            }
            manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            try {
                value = current()
                awaitCancellation()
            } finally { manager.unregisterDisplayListener(listener) }
        }
    }
    return displays
}
