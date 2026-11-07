package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val aqua = Color(0xFF69E4CC)
private val violet = Color(0xFFC4ADFF)
private val amber = Color(0xFFFFCA82)
private val rose = Color(0xFFFFA8B6)
private val blue = Color(0xFF9FC2FF)

@Composable
internal fun VisualUtilityDesign(
    module: VisualModule,
    navigator: VisualNavigator,
    onRealApp: (String) -> Unit,
) {
    val section = module.sections[navigator.section]
    val detail = navigator.detail
    var contactQuery by remember { mutableStateOf("") }
    var contactsFilter by remember { mutableIntStateOf(0) }
    var mediaFilter by remember { mutableIntStateOf(0) }
    var mediaPosition by remember { mutableFloatStateOf(42f) }
    Box(Modifier.fillMaxWidth().testTag("visual-workspace-${module.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (detail != null) {
                UtilityDetail(module, detail)
            } else when (module.id) {
                "contacts" -> ContactsWorkspace(
                    module, navigator, contactQuery, { contactQuery = it }, contactsFilter, { contactsFilter = it },
                )
                "notifications" -> NotificationWorkspace(section, navigator)
                "media" -> MediaWorkspace(module, navigator, mediaFilter, { mediaFilter = it }, mediaPosition, { mediaPosition = it })
                "downloads" -> DownloadsWorkspace(section, navigator)
                "screen-recorder" -> RecorderWorkspace(section, navigator)
                "share-hub" -> ShareWorkspace(section, navigator)
                "automations" -> AutomationWorkspace(section, navigator)
                "app-usage" -> UsageWorkspace(section, navigator)
                "update-centre" -> UpdateWorkspace(section, navigator, onRealApp)
                "browser-privacy" -> BrowserPrivacyWorkspace(section, navigator)
            }
        }
    }
}

@Composable
private fun UtilityPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = color) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun UtilityLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun UtilityGlyph(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val iconColor = if (MaterialTheme.colorScheme.surface.luminance() > .45f) {
        Color(tint.red * .35f, tint.green * .35f, tint.blue * .35f)
    } else tint
    Box(
        modifier.size(40.dp).background(tint.copy(alpha = .17f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun UtilityLine(
    row: VisualRow,
    index: Int,
    navigator: VisualNavigator,
    icon: ImageVector,
    tint: Color,
    supporting: String = row.badge.orEmpty(),
) {
    Surface(
        onClick = { navigator.inspect(row) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("visual-row-$index"),
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.padding(vertical = 6.dp, horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            UtilityGlyph(icon, tint)
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (supporting.isNotBlank()) Text(supporting, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UtilitySearch(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = change, singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(label) }, shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun UtilityOptions(choices: List<String>, selected: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        choices.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selected,
                onClick = { change(index) },
                label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun UtilitySwitch(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = change)
    }
}

@Composable
private fun UtilityDetail(module: VisualModule, row: VisualRow) {
    Column(Modifier.fillMaxWidth().testTag("visual-detail"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (module.id) {
            "contacts" -> {
                val contact = row.title.count { it == ' ' } == 1 && row.title in setOf("Mira Fenwick", "Rowan Vale", "Iona Reed")
                if (contact) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ContactAvatar(row.title, 76)
                        Spacer(Modifier.height(12.dp))
                        Text(row.title, style = MaterialTheme.typography.headlineMedium)
                        Text(row.badge.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    UtilityPanel {
                        UtilityLabel("About")
                        Text(row.detail)
                        HorizontalDivider()
                        Text("Contact methods · None in sample", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    val members = visualUtilities.first { it.id == "contacts" }.sections[0].rows
                        .filter { it.badge == row.title.substringAfterLast(' ') }
                    Text(row.title, style = MaterialTheme.typography.headlineSmall)
                    if (members.isEmpty()) Text(row.detail)
                    members.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ContactAvatar(member.title)
                            Text(member.title)
                        }
                    }
                }
            }
            "media" -> {
                MediaArtwork(row.title, Modifier.fillMaxWidth().height(210.dp))
                Text(row.title, style = MaterialTheme.typography.headlineSmall)
                Text(row.detail)
            }
            "downloads", "share-hub" -> UtilityPanel {
                UtilityGlyph(if (module.id == "downloads") Icons.Default.FileDownload else Icons.Default.Description, blue)
                Text(row.title, style = MaterialTheme.typography.headlineSmall)
                row.badge?.let { UtilityLabel(it) }
                Text(row.detail)
            }
            else -> {
                Text(row.title, style = MaterialTheme.typography.headlineSmall)
                row.badge?.let { UtilityLabel(it) }
                Text(row.detail)
            }
        }
        Text("Example detail · no action has been performed", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContactAvatar(name: String, size: Int = 42) {
    val initials = name.split(' ').take(2).joinToString("") { it.take(1) }
    val color = when (name.firstOrNull()) { 'M' -> violet; 'R' -> aqua; else -> amber }
    Box(Modifier.size(size.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = Color(0xFF202031), fontWeight = FontWeight.Bold,
            style = if (size > 50) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ContactsWorkspace(
    module: VisualModule,
    navigator: VisualNavigator,
    query: String,
    setQuery: (String) -> Unit,
    filter: Int,
    setFilter: (Int) -> Unit,
) {
    val section = module.sections[navigator.section]
    when (navigator.section) {
        0 -> {
            UtilitySearch(query, setQuery, "Search sample contacts")
            UtilityOptions(listOf("All contacts", "Workshop"), filter, setFilter)
            val matches = section.rows.withIndex().filter {
                it.value.title.contains(query.trim(), ignoreCase = true) && (filter == 0 || it.value.badge == "Workshop")
            }.sortedBy { it.value.title }
            if (matches.isEmpty()) Text("No matching sample contacts", Modifier.padding(12.dp))
            matches.forEach { (index, row) ->
                UtilityLabel(row.title.take(1))
                Surface(
                    onClick = { navigator.inspect(row) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("visual-row-$index"),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContactAvatar(row.title)
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall)
                            UtilityLabel(row.badge.orEmpty())
                        }
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        1 -> section.rows.forEachIndexed { index, row ->
            UtilityPanel { UtilityLine(row, index, navigator, if (index == 2) Icons.Default.Star else Icons.Default.Group, violet) }
        }
        2 -> UtilityPanel {
            UtilityLabel("Import sources · Offline")
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Badge, violet) }
        }
    }
}

@Composable
private fun NotificationWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var hidePreview by remember { mutableStateOf(true) }
    when (navigator.section) {
        0 -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                UtilityLabel("2 sample alerts")
            }
            section.rows.forEachIndexed { index, row ->
                UtilityPanel {
                    UtilityLine(row, index, navigator, if (index == 0) Icons.Default.CheckCircle else Icons.Default.Schedule,
                        if (index == 0) amber else blue, if (index == 0) "Studio Board · Checklist review" else "Nova · Morning summary")
                }
            }
            UtilityLabel("You're caught up in this sample")
        }
        1 -> {
            UtilityPanel {
                UtilityLabel("TODAY · SCHEDULE PREVIEW")
                section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Notifications, amber) }
            }
        }
        2 -> {
            UtilityPanel {
                UtilityLabel("LOCK SCREEN PREVIEW")
                Text("09:41", style = MaterialTheme.typography.displayMedium)
                Text(if (hidePreview) "Studio Board · New reminder" else "Studio Board · Review the workshop checklist")
                UtilitySwitch("Hide sample message body", hidePreview) { hidePreview = it }
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Lock, amber) }
        }
    }
}

@Composable
private fun MediaArtwork(title: String, modifier: Modifier) {
    val palette = when {
        title.contains("Harbour", ignoreCase = true) -> listOf(Color(0xFF173653), Color(0xFF477E8F), Color(0xFFB9F0D0))
        title.contains("Window", ignoreCase = true) -> listOf(Color(0xFF391F42), Color(0xFFAA5C62), Color(0xFFFFCA8E))
        else -> listOf(Color(0xFF202245), Color(0xFF5465A7), Color(0xFFBDD9FF))
    }
    Canvas(modifier.clip(RoundedCornerShape(14.dp))) {
        drawRect(Brush.verticalGradient(palette.take(2)))
        drawCircle(palette.last().copy(alpha = .80f), radius = size.minDimension * .15f,
            center = Offset(size.width * .72f, size.height * .32f))
        for (band in 0..2) {
            val y = size.height * (.48f + band * .14f)
            val curve = Path().apply {
                moveTo(0f, y)
                cubicTo(size.width * .34f, y - size.height * .33f, size.width * .57f, y + size.height * .3f, size.width, y)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(curve, palette[band].copy(alpha = .58f))
        }
    }
}

@Composable
private fun MediaWorkspace(
    module: VisualModule,
    navigator: VisualNavigator,
    filter: Int,
    setFilter: (Int) -> Unit,
    position: Float,
    setPosition: (Float) -> Unit,
) {
    val section = module.sections[navigator.section]
    when (navigator.section) {
        0 -> {
            UtilityOptions(listOf("All", "Image", "Audio", "Video"), filter, setFilter)
            val items = section.rows.withIndex().filter { filter == 0 || it.index == filter - 1 }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 500.dp) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.chunked(columns).forEach { page ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            page.forEach { (index, row) ->
                                Surface(
                                    onClick = { navigator.inspect(row) },
                                    modifier = Modifier.weight(1f).testTag("visual-row-$index"),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MediaArtwork(row.title, Modifier.fillMaxWidth().aspectRatio(1.22f))
                                        Text(row.title, Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.titleSmall)
                                        Text(row.badge.orEmpty(), Modifier.padding(start = 3.dp, bottom = 3.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            repeat(columns - page.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        1 -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MediaArtwork("Harbour loop", Modifier.fillMaxWidth(.7f).height(154.dp))
                Icon(Icons.Default.Headphones, "Sample album artwork", tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Text("Harbour loop", style = MaterialTheme.typography.headlineSmall)
            UtilityLabel("Player preview · No playback")
            Slider(position, setPosition, valueRange = 0f..140f)
            Text("%d:%02d / 2:20".format(position.toInt() / 60, position.toInt() % 60), style = MaterialTheme.typography.labelMedium)
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.QueueMusic, aqua) }
        }
        2 -> {
            UtilityLabel("COLLECTIONS")
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Collections, aqua) }
        }
    }
}

@Composable
private fun DownloadsWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var paused by remember { mutableStateOf(false) }
    when (navigator.section) {
        0 -> {
            UtilityPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Transfers", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    UtilityLabel("Sample queue")
                }
                UtilityLine(section.rows[0], 0, navigator, Icons.Default.PictureAsPdf, blue,
                    if (paused) "6 MB of 10 MB · Paused preview" else "6 MB of 10 MB · In progress preview")
                LinearProgressIndicator(progress = { .6f }, modifier = Modifier.fillMaxWidth(), color = blue)
                TextButton(onClick = { paused = !paused }) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                    Text(if (paused) "Resume demo" else "Pause demo", Modifier.padding(start = 8.dp))
                }
            }
            UtilityLabel("WAITING AND INTERRUPTED")
            section.rows.drop(1).forEachIndexed { offset, row ->
                UtilityLine(row, offset + 1, navigator, if (offset == 0) Icons.Default.Schedule else Icons.Default.ErrorOutline,
                    if (offset == 0) amber else rose)
            }
        }
        1 -> {
            UtilityLabel("COMPLETED · SAMPLE FILES")
            section.rows.forEachIndexed { index, row ->
                UtilityLine(row, index, navigator, if (index == 0) Icons.Default.Image else Icons.Default.FactCheck,
                    aqua, if (index == 0) "1.8 MB · Image" else "Review policy")
            }
        }
        2 -> {
            UtilityPanel {
                UtilityLabel("DESTINATION PREVIEW")
                Text("Downloads", style = MaterialTheme.typography.titleLarge)
                Text("14.2 GB used / 32 GB sample storage", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { .44f }, modifier = Modifier.fillMaxWidth(), color = blue)
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Folder, blue) }
        }
    }
}

@Composable
private fun RecorderWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var scope by remember { mutableIntStateOf(0) }
    var audio by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    when (navigator.section) {
        0 -> {
            val viewfinder = Color(0xFF222039)
            Box(
                Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(18.dp)).background(viewfinder),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val stroke = rose.copy(alpha = .72f)
                    val x = size.width * .075f
                    val y = size.height * .17f
                    val corner = size.height * .15f
                    listOf(Offset(x, y), Offset(size.width - x, y), Offset(x, size.height - y),
                        Offset(size.width - x, size.height - y)).forEach { point ->
                        val inwardX = if (point.x < size.width / 2f) corner else -corner
                        val inwardY = if (point.y < size.height / 2f) corner else -corner
                        drawLine(stroke, point, point.copy(x = point.x + inwardX), 2.dp.toPx())
                        drawLine(stroke, point, point.copy(y = point.y + inwardY), 2.dp.toPx())
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (count == 0) Icon(Icons.Default.Videocam, null, tint = rose, modifier = Modifier.size(36.dp))
                    else Text("$count", color = Color.White, style = MaterialTheme.typography.displayMedium)
                    Text(if (count == 0) "Capture inactive" else "Countdown preview", color = Color.White)
                }
            }
            UtilityOptions(listOf("Whole screen", "Single app"), scope) { scope = it }
            UtilitySwitch("Sample system audio", audio) { audio = it }
            OutlinedButton(
                onClick = { count = if (count == 0) 3 else count - 1 },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (count == 0) "Preview countdown" else if (count == 1) "End preview" else "Advance preview")
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Tune, rose) }
        }
        1 -> {
            Box(Modifier.fillMaxWidth().height(146.dp), contentAlignment = Alignment.Center) {
                MediaArtwork("Window light", Modifier.matchParentSize())
                Icon(Icons.Default.VideoLibrary, "Example clip thumbnail", tint = Color.White, modifier = Modifier.size(47.dp))
            }
            UtilityLabel("FICTIONAL CLIP · 00:18 · MUTED")
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Videocam, rose) }
        }
        2 -> {
            UtilityPanel {
                UtilityGlyph(Icons.Default.Shield, rose)
                Text("Capture off", style = MaterialTheme.typography.headlineSmall)
                UtilityLabel("No screen or microphone access")
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Lock, rose) }
        }
    }
}

@Composable
private fun ShareWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var includeDate by remember { mutableStateOf(false) }
    var includeAuthor by remember { mutableStateOf(false) }
    when (navigator.section) {
        0 -> {
            UtilityPanel(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text("Share draft", style = MaterialTheme.typography.headlineSmall)
                Text("To", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                UtilityLabel("No recipient · Demo destination")
                HorizontalDivider()
                UtilityLabel("2 SAMPLE ATTACHMENTS")
                section.rows.forEachIndexed { index, row ->
                    UtilityLine(row, index, navigator, if (index == 0) Icons.Default.Description else Icons.Default.Image,
                        aqua, if (index == 0) "PDF · 230 KB" else "Image · 1.8 MB")
                }
                FilledTonalButton(onClick = { navigator.openSection(1) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Review sample")
                }
            }
        }
        1 -> {
            UtilityPanel {
                UtilityLabel("INCLUDED IN EXAMPLE")
                UtilitySwitch("Include creation date", includeDate) { includeDate = it }
                UtilitySwitch("Include author label", includeAuthor) { includeAuthor = it }
                HorizontalDivider()
                Text("Preview fields · File name${if (includeDate) ", creation date" else ""}${if (includeAuthor) ", author" else ""}",
                    style = MaterialTheme.typography.bodySmall)
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.FactCheck, aqua) }
        }
        2 -> {
            UtilityLabel("HANDOFF HISTORY · SAMPLE")
            section.rows.forEachIndexed { index, row ->
                UtilityLine(row, index, navigator, if (index == 0) Icons.Default.Cancel else Icons.Default.History, aqua)
            }
        }
    }
}

@Composable
private fun AutomationWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var day by remember { mutableIntStateOf(1) }
    var simulated by remember { mutableStateOf(false) }
    when (navigator.section) {
        0 -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My routines", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(onClick = { navigator.openSection(1) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Text("Build demo")
                }
            }
            section.rows.forEachIndexed { index, row ->
                UtilityPanel {
                    UtilityLine(row, index, navigator, Icons.Default.Bolt, amber, "Inactive sample")
                    UtilityLabel(if (index == 0) "FRIDAY  →  CHECKLIST REVIEW" else "SHORTCUT  →  WORKSPACE SUGGESTION")
                }
            }
        }
        1 -> {
            UtilityLabel("WEEKLY TRIGGER")
            UtilityOptions(listOf("Tuesday", "Friday", "Sunday"), day) {
                day = it
                simulated = false
            }
            section.rows.forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("0${index + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.weight(1f)) {
                        UtilityPanel {
                            UtilityLine(row, index, navigator,
                                listOf(Icons.Default.Schedule, Icons.Default.FilterAlt, Icons.Default.Bolt)[index], amber,
                                listOf("${listOf("Tuesday", "Friday", "Sunday")[day]} · 17:30", "Checklist selected", "Show sample suggestion")[index])
                        }
                    }
                }
            }
            Button(onClick = {
                simulated = true
                navigator.openSection(2)
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Simulate this rule")
            }
        }
        2 -> {
            UtilityPanel {
                UtilityGlyph(if (!simulated || day == 1) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline, amber)
                Text(if (simulated && day != 1) "Sample skipped" else "Sample matched", style = MaterialTheme.typography.headlineSmall)
                UtilityLabel(if (simulated && day != 1) "Only Friday matches the sample checklist" else "Friday · Show review suggestion")
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Timeline, amber) }
        }
    }
}

@Composable
private fun UsageBars(trend: VisualTrend, selected: Int, onSelect: (Int) -> Unit) {
    val peak = trend.points.maxOf { it.value }.coerceAtLeast(1)
    UtilityPanel {
        Text(trend.title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            trend.points.forEachIndexed { index, point ->
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    color = if (selected == index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 6.dp)) {
                        Text("${point.value}", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth(.52f).height(82.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height((80f * point.value / peak).coerceAtLeast(2f).dp)
                                .background(violet, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                        }
                        Text(point.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (selected >= 0) UtilityLabel("${trend.points[selected].label} · ${trend.points[selected].value} minutes")
    }
}

@Composable
private fun UsageWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var selected by remember { mutableIntStateOf(-1) }
    var retention by remember { mutableIntStateOf(0) }
    val accents = listOf(violet, aqua, amber)
    when (navigator.section) {
        0 -> {
            UtilityPanel {
                UtilityLabel("ACTIVE TIME · SAMPLE")
                Text("1 h 25 min", style = MaterialTheme.typography.displaySmall)
                Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(35f, 30f, 20f).forEachIndexed { index, minutes ->
                        Spacer(Modifier.weight(minutes).height(10.dp).background(accents[index]))
                    }
                }
            }
            UtilityLabel("MOST USED")
            section.rows.forEachIndexed { index, row ->
                UtilityLine(row, index, navigator, listOf(Icons.Default.Dashboard, Icons.Default.Book, Icons.Default.Edit)[index], accents[index])
            }
        }
        1 -> {
            section.trend?.let { UsageBars(it, selected) { index -> selected = index } }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.BarChart, violet) }
        }
        2 -> {
            UtilityLabel("RETENTION PREVIEW · NO TRACKING")
            UtilityOptions(listOf("7 days", "30 days", "90 days"), retention) { retention = it }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.AdminPanelSettings, violet) }
        }
    }
}

@Composable
private fun UpdateWorkspace(section: VisualSection, navigator: VisualNavigator, onRealApp: (String) -> Unit) {
    when (navigator.section) {
        0 -> {
            UtilityPanel(color = MaterialTheme.colorScheme.secondaryContainer) {
                UtilityGlyph(Icons.Default.SystemUpdate, blue)
                UtilityLabel("REVIEW CHANNEL · SAMPLE")
                Text("Studio review build", style = MaterialTheme.typography.headlineMedium)
                Text("Illustrative release · Update checks off", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { navigator.openSection(1) }) { Text("View upgrade checklist") }
            }
            section.rows.forEachIndexed { index, row ->
                UtilityLine(row, index, navigator, if (index == 0) Icons.Default.SystemUpdate else Icons.Default.Article, blue)
            }
            OutlinedButton(onClick = { onRealApp("backup") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Nova Backup") }
        }
        1 -> {
            UtilityLabel("UPGRADE CHECKLIST · EXAMPLE")
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.FactCheck, blue) }
        }
        2 -> {
            UtilityPanel {
                UtilityLabel("RELEASE HISTORY · EXAMPLE")
                section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.History, blue) }
            }
        }
    }
}

@Composable
private fun BrowserPrivacyWorkspace(section: VisualSection, navigator: VisualNavigator) {
    var blocked by remember { mutableStateOf(true) }
    when (navigator.section) {
        0 -> {
            UtilityPanel {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Text("archive.example", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("EXAMPLE", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UtilityGlyph(Icons.Default.Shield, aqua)
                    Column(Modifier.weight(1f)) {
                        Text("Site privacy", style = MaterialTheme.typography.titleLarge)
                        UtilityLabel("Live inspection off")
                    }
                }
                TextButton(onClick = { navigator.openSection(1) }) { Text("Open sample site controls") }
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Language, aqua) }
        }
        1 -> {
            UtilityPanel {
                UtilityLabel("ARCHIVE.EXAMPLE")
                UtilitySwitch("Block camera in example", blocked) { blocked = it }
                Text(if (blocked) "Example camera status · Blocked" else "Example camera status · Ask", style = MaterialTheme.typography.bodySmall)
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Tune, aqua) }
        }
        2 -> {
            UtilityPanel {
                UtilityLabel("ROUTING WALKTHROUGH")
                listOf("Example site", "Route unavailable in demo", "Live connection uninspected").forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UtilityGlyph(listOf(Icons.Default.Language, Icons.Default.AltRoute, Icons.Default.Shield)[index], aqua)
                        Text(label)
                    }
                }
            }
            section.rows.forEachIndexed { index, row -> UtilityLine(row, index, navigator, Icons.Default.Policy, aqua) }
        }
    }
}
