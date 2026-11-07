package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VisualStudioDesign(module: VisualModule, navigator: VisualNavigator) {
    val section = module.sections[navigator.section]
    Column(
        Modifier.fillMaxWidth().testTag("visual-workspace-${module.id}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (module.id) {
            "desktop-mode" -> StudioDesktop(navigator.section)
            "control-centre" -> StudioQuickSettings(navigator.section)
            "themes-studio" -> StudioThemes(navigator.section)
            "lock-studio" -> StudioLockScreen(navigator.section)
            "pip-controls" -> StudioPictureInPicture(navigator.section)
        }
        HorizontalDivider()
        section.rows.forEachIndexed { index, row ->
            TextButton(
                onClick = { navigator.inspect(row) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("visual-row-$index"),
            ) {
                Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(row.title)
            }
        }
    }
}

private data class StudioLook(val name: String, val sky: Color, val horizon: Color, val accent: Color)

private val studioLooks = listOf(
    StudioLook("Aurora", Color(0xFF10273B), Color(0xFF087D75), Color(0xFF9BF5DA)),
    StudioLook("Dusk", Color(0xFF2A1841), Color(0xFF7C4C8E), Color(0xFFFFC2F1)),
    StudioLook("Amber", Color(0xFF38211B), Color(0xFF9A5630), Color(0xFFFFD599)),
)

@Composable
private fun StudioBackdrop(look: StudioLook, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(look.sky, look.horizon)))) {
        drawCircle(look.accent.copy(alpha = .76f), size.minDimension * .13f, Offset(size.width * .76f, size.height * .29f))
        repeat(3) { index ->
            val offset = size.height * (.61f + index * .095f)
            val ridge = Path().apply {
                moveTo(0f, offset)
                cubicTo(size.width * .32f, offset - size.height * .34f, size.width * .62f, offset + size.height * .20f, size.width, offset - size.height * .15f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(ridge, Color(0xFF0C1225).copy(alpha = .37f + index * .12f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioOptions(values: List<String>, active: Int, tag: String, onClick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { index, label ->
            FilterChip(
                selected = active == index,
                onClick = { onClick(index) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-$index"),
            )
        }
    }
}

@Composable
private fun StudioDesktop(section: Int) {
    var split by remember { mutableStateOf(true) }
    var activeWindow by remember { mutableIntStateOf(0) }
    var display by remember { mutableIntStateOf(0) }
    if (section == 1) {
        Text("Display arrangement", style = MaterialTheme.typography.titleMedium)
        StudioOptions(listOf("Fold inner display", "External display"), display, "studio-desktop-display") { display = it }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("1", "2").forEachIndexed { index, name ->
                        val accent = MaterialTheme.colorScheme.primary
                        Box(
                            Modifier.weight(1f).height(88.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (display == index) accent.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, if (display == index) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(name, style = MaterialTheme.typography.headlineMedium) }
                    }
                }
                Text(if (display == 0) "1 · Sample Fold workspace" else "2 · No real display connected", style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }
    if (section == 2) Text("Window layout", style = MaterialTheme.typography.titleMedium)
    Box(
        Modifier.fillMaxWidth().height(278.dp).clip(RoundedCornerShape(20.dp)).testTag("studio-desktop-preview"),
    ) {
        StudioBackdrop(studioLooks[0], Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("NOVA WORKSPACE", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("10:24", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (split || activeWindow == 0) DesktopWindow(false, Modifier.weight(if (split) 1.15f else 1f))
                if (split || activeWindow == 1) DesktopWindow(true, Modifier.weight(1f))
            }
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(16.dp), color = Color(0xDD101B2B),
            ) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        activeWindow = 0
                        split = false
                    }) {
                        Icon(Icons.Default.Edit, "Focus sample notes", tint = studioLooks[0].accent)
                    }
                    IconButton(onClick = {
                        activeWindow = 1
                        split = false
                    }) {
                        Icon(Icons.Default.CalendarToday, "Focus sample calendar", tint = Color(0xFFCCD9FF))
                    }
                    IconButton(onClick = { split = true }) {
                        Icon(Icons.Default.ViewColumn, "Show split workspace", tint = Color.White)
                    }
                }
            }
        }
    }
    StudioOptions(listOf("Split", "Focus"), if (split) 0 else 1, "studio-desktop-layout") {
        split = it == 0
        activeWindow = 0
    }
}

@Composable
private fun DesktopWindow(calendar: Boolean, modifier: Modifier) {
    Surface(
        modifier.fillMaxHeight(), shape = RoundedCornerShape(12.dp),
        color = Color(0xF3111D2D), contentColor = Color(0xFFE9F1FB),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (calendar) Color(0xFFCBD6FF) else studioLooks[0].accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(if (calendar) "Sample calendar" else "Sample notes", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider(color = Color(0xFF405067))
            if (calendar) {
                Text("THU 14", color = Color(0xFFCBD6FF), style = MaterialTheme.typography.titleMedium)
                Text("10:30  Design review", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text("14:00  Reading room", style = MaterialTheme.typography.bodySmall, maxLines = 2)
            } else {
                Text("Reading room", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("✓  Pick a layout", style = MaterialTheme.typography.bodySmall)
                Text("○  Choose lighting", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class StudioQuickTile(val label: String, val icon: ImageVector)

private val studioQuickTiles = listOf(
    StudioQuickTile("Focus", Icons.Default.DoNotDisturbOn),
    StudioQuickTile("Audio", Icons.Default.VolumeUp),
    StudioQuickTile("Layout", Icons.Default.ViewColumn),
    StudioQuickTile("Rotation", Icons.Default.ScreenRotation),
)

@Composable
private fun StudioQuickSettings(section: Int) {
    var order by remember { mutableStateOf(studioQuickTiles.indices.toList()) }
    var enabled by remember { mutableStateOf(setOf(1)) }
    var brightness by remember { mutableFloatStateOf(.6f) }
    var volume by remember { mutableFloatStateOf(.4f) }
    var limited by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme
    when (section) {
        1 -> {
            Text("Tile order", style = MaterialTheme.typography.titleMedium)
            order.forEachIndexed { position, index ->
                val tile = studioQuickTiles[index]
                ListItem(
                    headlineContent = { Text(tile.label) },
                    leadingContent = { Icon(tile.icon, null) },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                order = order.toMutableList().apply {
                                    removeAt(position)
                                    add(position - 1, index)
                                }
                            },
                            enabled = position > 0,
                        ) { Icon(Icons.Default.ArrowUpward, "Move ${tile.label} up") }
                    },
                )
            }
        }
        2 -> {
            Text("Tile visibility", style = MaterialTheme.typography.titleMedium)
            StudioSwitch("Hide sensitive labels", limited) { limited = it }
            ListItem(
                headlineContent = { Text("Audio") },
                supportingContent = { Text(if (limited) "Sample label hidden" else "Sample audio output") },
                leadingContent = { Icon(Icons.Default.VolumeUp, null) },
            )
            Text("Preview only · Android controls disconnected", style = MaterialTheme.typography.labelMedium)
        }
        else -> {
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("studio-control-preview")) {
                val columns = if (maxWidth >= 600.dp) 4 else 2
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    order.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { index ->
                                val tile = studioQuickTiles[index]
                                val selected = index in enabled
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
                                        .toggleable(selected, role = Role.Switch) { on ->
                                            enabled = if (on) enabled + index else enabled - index
                                        }
                                        .testTag("studio-control-tile-$index").padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(tile.icon, null, tint = if (selected) colors.onPrimaryContainer else colors.primary)
                                    Text(tile.label, color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(if (selected) "On" else "Off",
                                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            StudioLevel("Sample brightness", Icons.Default.Brightness6, brightness, "studio-control-brightness") { brightness = it }
            StudioLevel("Sample volume", Icons.Default.VolumeUp, volume, "studio-control-volume") { volume = it }
        }
    }
}

@Composable
private fun StudioLevel(label: String, icon: ImageVector, value: Float, tag: String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text("$label · ${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(value, onChange, Modifier.testTag(tag))
        }
    }
}

@Composable
private fun StudioThemes(section: Int) {
    var palette by remember { mutableIntStateOf(0) }
    var unfold by remember { mutableStateOf(false) }
    var larger by remember { mutableStateOf(false) }
    val look = studioLooks[palette]
    when (section) {
        1 -> {
            Text("Wallpaper crop", style = MaterialTheme.typography.titleMedium)
            StudioOptions(listOf("Cover", "Unfolded"), if (unfold) 1 else 0, "studio-wallpaper-posture") { unfold = it == 1 }
            Box(
                Modifier.fillMaxWidth().height(236.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.widthIn(max = if (unfold) 390.dp else 122.dp)
                        .fillMaxWidth().height(if (unfold) 190.dp else 216.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) { StudioBackdrop(look, Modifier.matchParentSize()) }
            }
        }
        2 -> {
            StudioSwitch("Large sample text", larger) { larger = it }
            Surface(shape = RoundedCornerShape(18.dp), color = look.sky, contentColor = look.accent) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reading sample", style = if (larger) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                    Text("The rain stopped just before the lights came on.", style = if (larger) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                    Text("Aa  ·  0123456789", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        else -> {
            Box(Modifier.fillMaxWidth().height(238.dp).clip(RoundedCornerShape(22.dp)).testTag("studio-theme-preview")) {
                StudioBackdrop(look, Modifier.matchParentSize())
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Sample Home", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text("10:24", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
                        Text("THU · 14 MAY", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(Icons.Default.Edit, Icons.Default.Folder, Icons.Default.CalendarToday, Icons.Default.Lock).forEach { icon ->
                            Box(Modifier.size(36.dp).background(look.accent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, Modifier.size(19.dp), tint = look.sky)
                            }
                        }
                    }
                }
            }
        }
    }
    StudioOptions(studioLooks.map { it.name }, palette, "studio-theme-palette") { palette = it }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(look.sky, look.horizon, look.accent).forEach { color ->
            Box(Modifier.weight(1f).height(12.dp).background(color, RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun StudioLockScreen(section: Int) {
    var detailed by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf(false) }
    var slot by remember { mutableIntStateOf(0) }
    var leftShortcut by remember { mutableIntStateOf(1) }
    var rightShortcut by remember { mutableIntStateOf(2) }
    val labels = listOf("None", "Torch", "Camera")
    val icons = listOf(Icons.Default.Remove, Icons.Default.FlashlightOn, Icons.Default.CameraAlt)
    val look = studioLooks[1]
    val preview: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().testTag("studio-lock-preview"), contentAlignment = Alignment.Center) {
            Box(
                Modifier.widthIn(max = 238.dp).fillMaxWidth().height(284.dp)
                    .clip(RoundedCornerShape(28.dp)).border(3.dp, Color(0xFF38344D), RoundedCornerShape(28.dp)),
            ) {
                StudioBackdrop(look, Modifier.matchParentSize())
                Column(
                    Modifier.fillMaxSize().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(17.dp), tint = look.accent)
                    if (detailed) Text("Thursday · 14 May", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text("10:24", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    if (showAlert) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xDE322141), contentColor = Color.White) {
                            Text("Example alert · Calendar", Modifier.padding(10.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(leftShortcut, rightShortcut).forEach { choice ->
                            Box(Modifier.size(36.dp).background(Color(0xCC322141), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icons[choice], labels[choice], Modifier.size(17.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        when (section) {
            2 -> {
                StudioOptions(listOf("Left shortcut", "Right shortcut"), slot, "studio-lock-slot") { slot = it }
                StudioOptions(labels, if (slot == 0) leftShortcut else rightShortcut, "studio-lock-shortcut") {
                    if (slot == 0) leftShortcut = it else rightShortcut = it
                }
            }
            1 -> StudioSwitch("Show example alert", showAlert, "studio-lock-alert") { showAlert = it }
            else -> {
                StudioOptions(listOf("Minimal", "Date"), if (detailed) 1 else 0, "studio-lock-clock") { detailed = it == 1 }
                StudioSwitch("Show example alert", showAlert, "studio-lock-alert") { showAlert = it }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { preview() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview()
                controls()
            }
        }
    }
}

@Composable
private fun StudioPictureInPicture(section: Int) {
    var right by remember { mutableStateOf(true) }
    var larger by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(true) }
    if (section == 2) {
        Text("Sample playback policy", style = MaterialTheme.typography.titleMedium)
        ListItem(headlineContent = { Text("Public sample clip") }, supportingContent = { Text("Simulated only") },
            leadingContent = { Icon(Icons.Default.PlayCircle, null) })
        ListItem(headlineContent = { Text("Protected Vault media") }, supportingContent = { Text("Excluded from this preview") },
            leadingContent = { Icon(Icons.Default.Lock, null) })
        return
    }
    Box(Modifier.fillMaxWidth().height(239.dp).clip(RoundedCornerShape(22.dp)).testTag("studio-pip-preview")) {
        StudioBackdrop(studioLooks[0], Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sample workspace", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                listOf(.64f, .43f, .53f).forEachIndexed { index, width ->
                    drawRect(Color.White.copy(alpha = .22f), Offset(0f, size.height * .35f * index), Size(size.width * width, 5.dp.toPx()))
                }
            }
        }
        if (visible) {
            Box(
                Modifier.align(if (right) Alignment.BottomEnd else Alignment.BottomStart).padding(12.dp)
                    .size(width = if (larger) 190.dp else 142.dp, height = if (larger) 120.dp else 90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF82A6AD), RoundedCornerShape(12.dp)),
            ) {
                StudioBackdrop(studioLooks[2], Modifier.matchParentSize())
                IconButton(onClick = { visible = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, "Close sample player", tint = Color.White)
                }
                IconButton(onClick = { paused = !paused }, modifier = Modifier.align(Alignment.Center)) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (paused) "Play simulated clip" else "Pause simulated clip", tint = Color.White)
                }
                LinearProgressIndicator(
                    progress = { if (paused) .27f else .62f },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    color = studioLooks[2].accent,
                )
            }
        } else {
            Button(
                onClick = {
                    visible = true
                    paused = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Text("Reopen sample")
            }
        }
    }
    StudioOptions(listOf("Left", "Right"), if (right) 1 else 0, "studio-pip-corner") { right = it == 1 }
    StudioSwitch("Larger sample window", larger, "studio-pip-size") { larger = it }
}

@Composable
private fun StudioSwitch(label: String, value: Boolean, tag: String = "", onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onChange, if (tag.isEmpty()) Modifier else Modifier.testTag(tag))
    }
}
