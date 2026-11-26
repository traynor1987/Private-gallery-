package uk.co.traynor.privategallery.novaos2

import android.media.session.MediaSession
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.intelligence.IntelligenceGuardian
import uk.co.traynor.privategallery.intelligence.intelligenceGuardian
import uk.co.traynor.privategallery.intelligence.intelligencePrivateAllowed
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.launcher.LauncherBuiltIn
import uk.co.traynor.privategallery.launcher.LauncherTheme
import uk.co.traynor.privategallery.launcher.forLauncherApp

@Composable
internal fun NovaControlCentre(host: NovaSystemHost) {
    val context = LocalContext.current
    val device = remember(context) { ControlCentreDevice(context) }
    val media = remember(context) { ControlCentreMedia(context) }
    val store = remember(context) { NovaSystemConfig(context, "control-centre") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val window = LocalActivity.current?.window
    var layout by remember { mutableStateOf<ControlCentreLayout?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ControlDeviceStatus?>(null) }
    var playback by remember { mutableStateOf<ControlPlayback?>(null) }
    var mediaAccess by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<IntelligenceGuardian?>(null) }
    var summaryLoading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectingApps by rememberSaveable { mutableStateOf(false) }
    var brightness by remember(window) { mutableStateOf<Float?>(null) }
    var restoredBrightness by remember(window) { mutableStateOf<Float?>(null) }
    val enabled = layout?.mediaEnabled == true
    val available = ControlTile.entries.filter {
        when (it) {
            ControlTile.WORKING -> NovaSystemId.WORKING in host.available
            ControlTile.PRIVACY -> NovaSystemId.PRIVACY in host.available
            else -> true
        }
    }.toSet()

    fun permitted() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        host.foregroundAllowed() && intelligencePrivateAllowed(context)

    LaunchedEffect(lifecycle, store) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loading = true
            layout = withContext(Dispatchers.IO) { runCatching { ControlCentreLayout.decode(store.read()) }.getOrNull() }
            loading = false
        }
    }
    LaunchedEffect(lifecycle, device) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            brightness = device.windowBrightness()
            device.watch().collect { status = it }
        }
    }
    LaunchedEffect(lifecycle, media, enabled) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            mediaAccess = media.permissionAvailable()
            try {
                if (enabled && mediaAccess && permitted()) media.watch().collect { playback = it }
            } finally { playback = null }
        }
    }
    LaunchedEffect(lifecycle, context, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            summaryLoading = true
            try {
                val next = if (!permitted()) null else withContext(Dispatchers.IO) {
                    if (!intelligencePrivateAllowed(context)) return@withContext null
                    runCatching {
                        val zone = ZoneId.systemDefault()
                        intelligenceGuardian(context, LocalDate.now(zone), zone)
                    }.getOrNull()
                }
                summary = next.takeIf { permitted() }
            } finally { summaryLoading = false }
        }
    }
    fun resetBrightness() {
        val original = restoredBrightness ?: return
        val current = window ?: return
        current.attributes = current.attributes.apply { screenBrightness = original }
        restoredBrightness = null
        brightness = device.windowBrightness()
    }
    DisposableEffect(lifecycle, window) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) resetBrightness()
        }
        lifecycle.addObserver(listener)
        onDispose {
            lifecycle.removeObserver(listener)
            resetBrightness()
        }
    }

    fun save(next: ControlCentreLayout) {
        if (saving || !permitted()) return
        saving = true
        message = null
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) { runCatching { store.write(next.encode()) }.isSuccess }
                if (success) layout = next else message = "Control arrangement could not be saved."
            } finally { saving = false }
        }
    }

    fun launchSettings(action: String, fallback: String = Settings.ACTION_SETTINGS) {
        if (permitted() && !host.openSystemSettings(action) && !host.openSystemSettings(fallback)) {
            message = "Android could not open these settings."
        }
    }

    fun act(tile: ControlTile) {
        if (!permitted()) return
        message = null
        when (tile) {
            ControlTile.INTERNET -> launchSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS)
            ControlTile.VOLUME -> launchSettings(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
            ControlTile.ROTATION -> launchSettings(Settings.ACTION_DISPLAY_SETTINGS)
            ControlTile.FLASHLIGHT -> {
                val on = status?.torchOn
                if (on == null || !device.setTorch(!on)) message = "The flashlight is unavailable."
            }
            ControlTile.SANCTUARY -> host.actions.openSanctuary()
            ControlTile.WORKING -> if (!host.open(NovaSystemId.WORKING)) message = "Working Mode could not be opened."
            ControlTile.PRIVACY -> if (!host.open(NovaSystemId.PRIVACY)) message = "Privacy Dashboard could not be opened."
            ControlTile.THEME -> {
                if (NovaSystemId.THEMES in host.available) {
                    if (!host.open(NovaSystemId.THEMES)) message = "Themes Studio could not be opened."
                } else {
                    val current = host.state.appearance
                    val themes = LauncherTheme.entries
                    val next = themes[(current.theme.ordinal + 1) % themes.size]
                    if (!host.actions.save(host.state.copy(appearance = current.copy(theme = next)))) {
                        message = "The launcher theme could not be changed."
                    }
                }
            }
        }
    }

    val current = layout
    val allowedApps = remember(host.apps, host.decisions) {
        host.apps.filter {
            it.packageName != context.packageName && !it.packageName.startsWith("nova:") &&
                ControlPlacement.validApp(it.component) && host.decisions.forLauncherApp(it)?.canLaunch == true
        }
    }
    val notifications = remember(host.apps, host.decisions) {
        host.apps.firstOrNull { app -> LauncherBuiltIn.find(context.packageName, app.component) ==
            LauncherBuiltIn.SAFE_NOTIFICATIONS && host.decisions.forLauncherApp(app)?.canLaunch == true }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(if (host.wide) "control-centre-inner" else "control-centre-cover"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (current == null) {
            if (!loading) Text("Control Centre could not read its saved settings.")
            return@Column
        }
        val placement = current.placement(host.wide)
        val deviceControls: @Composable () -> Unit = {
            NovaSystemSection("Device") {
                ControlSystemStatus(status) { launchSettings(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
                if (window != null && brightness != null) {
                    Text("NovaOS window brightness")
                    Slider(
                        value = (brightness ?: .04f).coerceAtLeast(.04f),
                        valueRange = .04f..1f,
                        onValueChange = { requested ->
                            if (permitted()) {
                                if (restoredBrightness == null) restoredBrightness = window.attributes.screenBrightness
                                brightness = requested
                                window.attributes = window.attributes.apply { screenBrightness = requested }
                            }
                        },
                        modifier = Modifier.testTag("control-window-brightness"),
                    )
                    if (restoredBrightness != null) TextButton(onClick = ::resetBrightness) { Text("Use system brightness") }
                } else TextButton(onClick = { launchSettings(Settings.ACTION_DISPLAY_SETTINGS) }) { Text("Display settings") }
            }
            NovaSystemSection("Quick actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !saving, onClick = {
                        editing = !editing
                        if (!editing) selectingApps = false
                    }) { Text(if (editing) "Done" else "Arrange quick actions") }
                }
                if (editing) ControlLayoutEditor(
                    placement, available, host.wide, !saving, { change ->
                        save(current.changePlacement(host.wide, change))
                    },
                )
                else {
                    val shown = placement.visible(available)
                    ControlTileGrid(shown, status, host, ::act)
                    if (ControlTile.FLASHLIGHT in shown && status != null && status?.torchOn == null) {
                        Text("The flashlight is unavailable.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            NovaSystemSection("Quick launch") {
                val pinned = placement.pinnedApps.mapNotNull { id -> allowedApps.firstOrNull { it.component == id } }
                if (pinned.isEmpty()) Text("No apps pinned for this display.")
                pinned.forEach { app ->
                    TextButton(onClick = {
                        if (permitted() && !host.launch(app)) message = "The selected app is unavailable."
                    }) { Text(app.controlName()) }
                }
                val unavailable = placement.pinnedApps.filter { id -> pinned.none { it.component == id } }
                unavailable.forEach { id ->
                    TextButton(enabled = !saving, onClick = {
                        save(current.changePlacement(host.wide) { it.toggleApp(id) })
                    }) { Text("Remove unavailable pin") }
                }
                TextButton(enabled = !saving, onClick = { selectingApps = !selectingApps }) {
                    Text(if (selectingApps) "Close app picker" else "Choose pinned apps")
                }
                if (selectingApps) {
                    if (allowedApps.isEmpty()) Text("No approved apps are available.")
                    else ControlAppPicker(allowedApps, placement, saving) { app ->
                        save(current.changePlacement(host.wide) { it.toggleApp(app.component) })
                    }
                }
            }
        }

        val otherControls: @Composable () -> Unit = {
            NovaSystemSection("Media") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Media controls", Modifier.weight(1f).padding(top = 12.dp))
                    Switch(checked = current.mediaEnabled, enabled = !saving, onCheckedChange = {
                        save(current.copy(mediaEnabled = it))
                    }, modifier = Modifier.semantics { contentDescription = "Enable media controls" })
                }
                if (enabled) {
                    if (!mediaAccess) Text("Media controls need Android notification access.")
                    else ControlPlaybackControls(playback) { session, command ->
                        if (permitted() && !media.send(session, command)) {
                            message = "The audio app could not perform that action."
                        }
                    }
                }
                if (enabled && !mediaAccess) TextButton(onClick = {
                    launchSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }) { Text("Open notification access settings") }
                TextButton(onClick = {
                    launchSettings(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
                }) { Text("Sound settings") }
            }
            NovaSystemSection("Notifications") {
                when {
                    summaryLoading -> Text("Checking Guardian…")
                    summary == null -> Text("Guardian summary is unavailable.")
                    else -> summary?.let { ControlNoticeCards(it) }
                }
                TextButton(enabled = !summaryLoading, onClick = { if (permitted()) refresh++ }) { Text("Refresh summary") }
                notifications?.let { app ->
                    TextButton(onClick = {
                        if (permitted() && !host.launch(app)) message = "Safe Notifications could not be opened."
                    }) { Text("Open Safe Notifications history") }
                }
            }
        }
        if (host.wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { deviceControls() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { otherControls() }
            }
        } else {
            deviceControls()
            otherControls()
        }
    }
}

@Composable
private fun ControlSystemStatus(status: ControlDeviceStatus?, onInternet: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.BatteryStd, null, modifier = Modifier.size(20.dp))
        Text(status?.battery?.let { "$it%${if (status.charging) " · Charging" else ""}" } ?: "Battery unavailable")
    }
    TextButton(onClick = onInternet) { Text(status?.connection ?: "Network unavailable") }
}

@Composable
private fun ControlTileGrid(order: List<ControlTile>, status: ControlDeviceStatus?, host: NovaSystemHost, onAction: (ControlTile) -> Unit) {
    if (order.isEmpty()) Text("No quick actions shown. Use Arrange quick actions to add one.")
    order.chunked(2).forEach { tiles ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { tile ->
                FilledTonalButton(
                    onClick = { onAction(tile) },
                    enabled = tile != ControlTile.FLASHLIGHT || status?.torchOn != null,
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp).testTag("control-tile-${tile.name.lowercase()}"),
                ) {
                    Icon(tile.icon(), null, Modifier.size(18.dp))
                    Text(tile.label(status, host), Modifier.padding(start = 6.dp))
                }
            }
            if (tiles.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ControlLayoutEditor(
    placement: ControlPlacement,
    available: Set<ControlTile>,
    wide: Boolean,
    enabled: Boolean,
    change: ((ControlPlacement) -> ControlPlacement) -> Unit,
) {
    Text(if (wide) "Inner display arrangement" else "Cover display arrangement", style = MaterialTheme.typography.bodySmall)
    val ordered = placement.order.filter { it in available }
    ordered.forEachIndexed { index, tile ->
        val title = tile.name.lowercase().replaceFirstChar(Char::uppercaseChar)
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth()) {
            TextButton(enabled = enabled && index > 0, onClick = { change { it.move(tile, -1, available) } },
                modifier = Modifier.semantics { contentDescription = "Move $title earlier" }) { Text("Earlier") }
            TextButton(enabled = enabled && index < ordered.lastIndex, onClick = { change { it.move(tile, 1, available) } },
                modifier = Modifier.semantics { contentDescription = "Move $title later" }) { Text("Later") }
            TextButton(enabled = enabled, onClick = { change { it.toggle(tile) } },
                modifier = Modifier.testTag("control-arrange-${tile.name.lowercase()}")
                    .semantics { contentDescription = if (tile in placement.hidden) "Show $title" else "Hide $title" }) {
                Text(if (tile in placement.hidden) "Show" else "Hide")
            }
        }
    }
}

@Composable
private fun ControlAppPicker(apps: List<LauncherApp>, placement: ControlPlacement, saving: Boolean, onToggle: (LauncherApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    androidx.compose.material3.OutlinedTextField(
        value = query, onValueChange = { query = it.take(80) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), label = { Text("Find an app") },
    )
    val matching = apps.filter { it.controlName().contains(query.trim(), ignoreCase = true) }
    if (matching.isEmpty()) Text("No apps match this search.")
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
        matching.forEach { app ->
            val pinned = app.component in placement.pinnedApps
            TextButton(enabled = !saving && (pinned || placement.pinnedApps.size < ControlPlacement.PIN_LIMIT),
                onClick = { onToggle(app) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (pinned) "Unpin ${app.controlName()}" else "Pin ${app.controlName()}")
            }
        }
    }
    Text("${placement.pinnedApps.size} of ${ControlPlacement.PIN_LIMIT} pinned", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ControlPlaybackControls(
    playback: ControlPlayback?,
    onPlay: (MediaSession.Token, ControlTransport) -> Unit,
) {
    if (playback == null) {
        Text("No controllable media session.")
        return
    }
    Text(if (playback.playing) "Media playing" else "Media paused")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(enabled = ControlTransport.PREVIOUS in playback.actions,
            onClick = { onPlay(playback.session, ControlTransport.PREVIOUS) }) {
            Icon(Icons.Default.SkipPrevious, "Previous")
        }
        IconButton(enabled = ControlTransport.PLAY_PAUSE in playback.actions,
            onClick = { onPlay(playback.session, ControlTransport.PLAY_PAUSE) }) {
            Icon(if (playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playback.playing) "Pause" else "Play")
        }
        IconButton(enabled = ControlTransport.NEXT in playback.actions,
            onClick = { onPlay(playback.session, ControlTransport.NEXT) }) {
            Icon(Icons.Default.SkipNext, "Next")
        }
    }
}

@Composable
internal fun ControlNoticeCards(summary: IntelligenceGuardian) {
    val categories = listOf(
        Triple("Low concern", summary.safe, "Guardian assessed these alerts as low concern."),
        Triple("Held", summary.held, "Guardian keeps held notifications in protected review."),
        Triple("Awaiting assessment", summary.pending, "These alerts have yet to receive a local assessment."),
    )
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    categories.forEach { (label, count, explanation) ->
        TextButton(onClick = { expanded = if (expanded == label) null else label }) { Text("$label · $count") }
        if (expanded == label) Text(explanation, style = MaterialTheme.typography.bodySmall)
    }
}

private fun LauncherApp.controlName(): String = profileLabel?.let { "$label · $it" } ?: label

private fun ControlTile.icon() = when (this) {
    ControlTile.INTERNET -> Icons.Default.Wifi
    ControlTile.VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
    ControlTile.FLASHLIGHT -> Icons.Default.FlashlightOn
    ControlTile.ROTATION -> Icons.Default.ScreenRotation
    ControlTile.SANCTUARY -> Icons.Default.NightsStay
    ControlTile.WORKING -> Icons.Default.Work
    ControlTile.THEME -> Icons.Default.Palette
    ControlTile.PRIVACY -> Icons.Default.Security
}

private fun ControlTile.label(status: ControlDeviceStatus?, host: NovaSystemHost): String = when (this) {
    ControlTile.INTERNET -> "Internet"
    ControlTile.VOLUME -> "Volume"
    ControlTile.FLASHLIGHT -> if (status?.torchOn == true) "Torch on" else "Flashlight"
    ControlTile.ROTATION -> when (status?.autoRotate) {
        true -> "Auto-rotate on"
        false -> "Auto-rotate off"
        null -> "Rotation settings"
    }
    ControlTile.SANCTUARY -> "Sanctuary"
    ControlTile.WORKING -> "Working Mode"
    ControlTile.THEME -> if (NovaSystemId.THEMES in host.available) "Themes Studio"
        else "Theme · ${host.state.appearance.theme.name.lowercase()}"
    ControlTile.PRIVACY -> "Privacy"
}
