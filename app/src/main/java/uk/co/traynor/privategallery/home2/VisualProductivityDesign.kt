package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun VisualProductivityDesign(module: VisualModule, navigator: VisualNavigator) {
    val container = Modifier.fillMaxWidth().testTag("visual-workspace-${module.id}")
    val isContainerDetail = navigator.detail?.badge?.substringBefore(':') in setOf("book", "folder")
    Column(if (isContainerDetail) container.testTag("visual-detail") else container, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (module.id) {
            "notes" -> NotesWorkspace(navigator)
            "files" -> FilesWorkspace(module, navigator)
            "calendar" -> CalendarWorkspace(module, navigator)
            "tasks" -> TasksWorkspace(module, navigator)
            "clipboard" -> ClipboardWorkspace(module, navigator)
        }
    }
}

private data class SampleNote(
    val id: Int, val title: String, val text: String, val book: String, val tag: String,
    val pinned: Boolean = false, val favourite: Boolean = false, val checks: List<Pair<String, Boolean>> = emptyList(),
)

@Composable
private fun NotesWorkspace(navigator: VisualNavigator) {
    val notes = remember {
        mutableStateListOf(
            SampleNote(1, "Window Garden", "Pick a shelf with morning light and room for a watering tray. Start with three pots and label each planting date.", "Ideas", "Garden", pinned = true),
            SampleNote(2, "Pocket Observatory", "An evening under the northern sky.", "Ideas", "Outdoors",
                checks = listOf("Print a star chart" to true, "Pack a dim torch" to false, "Bring a folding chair" to false)),
            SampleNote(3, "Reading list", "A map of night skies\nA guide to rain gardens\nAn illustrated atlas of clouds", "Reference", "Reading", favourite = true),
        )
    }
    var query by remember { mutableStateOf("") }
    var favourites by remember { mutableStateOf(false) }
    var tag by remember { mutableStateOf<String?>(null) }
    var showTags by remember { mutableStateOf(false) }
    val detail = navigator.detail
    val openedId = detail?.badge?.removePrefix("note:")?.toIntOrNull()
    val opened = notes.firstOrNull { it.id == openedId }
    if (opened != null) {
        NoteEditor(opened) { changed ->
            notes[notes.indexOfFirst { it.id == changed.id }] = changed
        }
        return
    }

    val notebook = detail?.badge?.takeIf { it.startsWith("book:") }?.removePrefix("book:")
    if (navigator.section == 1 && notebook == null) {
        WorkspaceLabel("Notebooks", "2 collections")
        listOf("Ideas", "Reference").forEachIndexed { index, book ->
            val count = notes.count { it.book == book }
            Surface(
                onClick = { navigator.inspect(VisualRow(book, "", "book:$book")) },
                color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("visual-row-$index"),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book, style = MaterialTheme.typography.titleLarge)
                        Text("$count notes", style = MaterialTheme.typography.labelMedium)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        return
    }

    val createNote = {
        val id = (notes.maxOfOrNull { it.id } ?: 0) + 1
        notes.add(SampleNote(id, "", "", notebook ?: "Ideas", "Draft"))
        navigator.inspect(VisualRow("New note", "", "note:$id"))
    }
    if (navigator.section == 2) {
        WorkspaceLabel("Drafts", "This session")
        FilledTonalButton(createNote, modifier = Modifier.heightIn(min = 48.dp).testTag("visual-notes-new")) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New Note")
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkspaceSearch(query, { query = it }, "Search notes", Modifier.weight(1f))
            SmallFloatingActionButton(
                onClick = createNote,
                modifier = Modifier.size(48.dp).testTag("visual-notes-new"),
            ) { Icon(Icons.Default.Add, "New Note") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(!favourites && tag == null, { favourites = false; tag = null }, { Text("All notes") }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(favourites, { favourites = !favourites }, { Text("Favourites") }, modifier = Modifier.heightIn(min = 48.dp))
            Box {
                IconButton({ showTags = true }) {
                    Icon(Icons.Default.Label, if (tag == null) "Filter by tag" else "Tag filter: $tag",
                        tint = if (tag == null) LocalContentColor.current else MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(showTags, { showTags = false }) {
                    (listOf("All tags") + notes.map { it.tag }.distinct()).forEach { item ->
                        DropdownMenuItem({ Text(item) }, {
                            tag = item.takeUnless { it == "All tags" }
                            showTags = false
                        })
                    }
                }
            }
        }
    }
    val visible = notes.filter { note ->
        if (navigator.section == 2) note.tag == "Draft"
        else (notebook == null || note.book == notebook) &&
            (!favourites || note.favourite) && (tag == null || tag == note.tag) &&
            (query.isBlank() || listOf(note.title, note.text, note.book, note.tag).any { it.contains(query.trim(), ignoreCase = true) })
    }
    if (navigator.section == 2 && visible.isEmpty()) {
        Text("No draft notes", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (visible.isEmpty()) {
        Text("No matching notes", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val pinned = visible.filter { it.pinned }
    val rest = visible.filterNot { it.pinned }
    val groups = listOf("Pinned" to pinned, (notebook ?: "Library") to rest).filter { it.second.isNotEmpty() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 650.dp) 3 else if (maxWidth >= 310.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (label, group) ->
                WorkspaceLabel(label, "${group.size} notes")
                group.chunked(columns).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        chunk.forEach { note ->
                            val index = visible.indexOf(note)
                            NotePreview(note, Modifier.weight(1f).testTag("visual-row-$index")) {
                                navigator.inspect(VisualRow(note.title.ifBlank { "Untitled note" }, "", "note:${note.id}"))
                            }
                        }
                        repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotePreview(note: SampleNote, modifier: Modifier, onClick: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val (background, foreground) = when (note.tag) {
        "Garden" -> colours.primaryContainer to colours.onPrimaryContainer
        "Outdoors" -> colours.tertiaryContainer to colours.onTertiaryContainer
        else -> colours.secondaryContainer to colours.onSecondaryContainer
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 132.dp),
        shape = RoundedCornerShape(18.dp),
        color = background,
        contentColor = foreground,
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(if (note.checks.isEmpty()) Icons.Default.StickyNote2 else Icons.Default.Checklist, null, Modifier.size(16.dp))
                Text(note.book, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note.favourite) Icon(Icons.Default.Favorite, "Favourite", Modifier.size(14.dp))
            }
            Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(note.text.ifBlank { "Empty note" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("#${note.tag.lowercase()}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NoteEditor(note: SampleNote, onChange: (SampleNote) -> Unit) {
    var notebooks by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val sample = "Temporary draft · session only"
    Column(Modifier.fillMaxWidth().testTag("visual-detail"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton({ notebooks = true }) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(note.book)
                }
                DropdownMenu(notebooks, { notebooks = false }) {
                    listOf("Ideas", "Reference").forEach { book ->
                        DropdownMenuItem({ Text(book) }, { onChange(note.copy(book = book)); notebooks = false })
                    }
                }
            }
            IconButton({ onChange(note.copy(pinned = !note.pinned)) }) {
                Icon(Icons.Default.PushPin, if (note.pinned) "Unpin sample note" else "Pin sample note", tint = if (note.pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
            IconButton({ onChange(note.copy(favourite = !note.favourite)) }) {
                Icon(if (note.favourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (note.favourite) "Remove sample favourite" else "Favourite sample note")
            }
        }
        TextField(note.title, { onChange(note.copy(title = it)) }, Modifier.fillMaxWidth().testTag("visual-note-title"),
            label = { Text("Title") }, singleLine = true, textStyle = MaterialTheme.typography.headlineMedium)
        TextField(note.text, { onChange(note.copy(text = it)) }, Modifier.fillMaxWidth().testTag("visual-note-body"),
            label = { Text("Note") }, minLines = 5)
        note.checks.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.second, { checked ->
                    onChange(note.copy(checks = note.checks.mapIndexed { i, previous -> if (i == index) previous.first to checked else previous }))
                }, Modifier.testTag("visual-note-check-$index"))
                Text(item.first, textDecoration = if (item.second) TextDecoration.LineThrough else TextDecoration.None)
            }
        }
        TextButton({
            focus.clearFocus()
            onChange(note.copy(checks = note.checks + ("Example checklist item ${note.checks.size + 1}" to false)))
        }) {
            Icon(Icons.Default.Checklist, null)
            Spacer(Modifier.width(8.dp))
            Text("Add checklist item")
        }
        Text(sample, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class SampleFile(val name: String, val type: String, val parent: String, val sizeKb: Int, val date: String)

private val productivityFiles = listOf(
    SampleFile("Brief.pdf", "PDF", "Project Aurora", 280, "14 May"),
    SampleFile("Trail-map.png", "Image", "Project Aurora / Maps", 1800, "13 May"),
    SampleFile("Cloud-atlas.txt", "Text", "Reading room", 18, "12 May"),
    SampleFile("Wind-in-pines.m4a", "Audio", "Field recordings", 5100, "12 May"),
    SampleFile("Evening-loop.m4a", "Audio", "Field recordings", 3500, "11 May"),
)
private val rootFolders = listOf("Project Aurora", "Field recordings", "Reading room")

@Composable
private fun FilesWorkspace(module: VisualModule, navigator: VisualNavigator) {
    val detail = navigator.detail
    val folder = detail?.badge?.takeIf { it.startsWith("folder:") }?.removePrefix("folder:").orEmpty()
    val selected = productivityFiles.firstOrNull { detail?.badge == "file:${it.name}" }
    var grid by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf("Recent") }
    var filter by remember { mutableStateOf("All types") }
    var menu by remember { mutableStateOf(false) }
    if (selected != null) {
        FilePreview(selected)
        return
    }
    if (detail != null && folder.isEmpty()) {
        WorkspaceDetail(detail, Icons.Default.Info)
        return
    }
    if (navigator.section == 2) {
        StorageStrip()
        module.sections[2].rows.forEachIndexed { index, item ->
            WorkspaceRow(item.title, item.badge.orEmpty(), Icons.Default.Info, Modifier.testTag("visual-row-$index")) { navigator.inspect(item) }
        }
        return
    }
    val recent = navigator.section == 1
    if (!recent && folder.isEmpty()) StorageStrip()
    if (!recent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ navigator.openSection(0) }) { Text("Drive") }
            if (folder.isNotEmpty()) {
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                if (folder.contains(" / ")) {
                    TextButton({ navigator.back() }) {
                        Text(folder.substringBefore(" / "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                }
                Text(folder.substringAfterLast(" / "), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    val folders = when {
        recent -> emptyList()
        folder.isEmpty() -> rootFolders
        folder == "Project Aurora" -> listOf("Maps")
        else -> emptyList()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton({ menu = true }) {
                Icon(Icons.Default.Sort, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(sort)
            }
            DropdownMenu(menu, { menu = false }) {
                listOf("Recent", "Name", "Size").forEach { option ->
                    DropdownMenuItem({ Text(option) }, { sort = option; menu = false })
                }
            }
        }
        var types by remember { mutableStateOf(false) }
        Box {
            TextButton({ types = true }) { Text(filter); Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)) }
            DropdownMenu(types, { types = false }) {
                listOf("All types", "PDF", "Image", "Text", "Audio").forEach { option ->
                    DropdownMenuItem({ Text(option) }, { filter = option; types = false })
                }
            }
        }
        if (folders.isNotEmpty()) {
            IconButton({ grid = !grid }) {
                Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, if (grid) "List folders" else "Grid folders")
            }
        }
    }
    if (folders.isNotEmpty()) {
        WorkspaceLabel("Folders", folders.size.toString())
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (!grid) 1 else if (maxWidth >= 640.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folders.chunked(columns).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEach { name ->
                            val path = if (folder.isEmpty()) name else "$folder / $name"
                            val open = { navigator.inspect(VisualRow(name, "", "folder:$path")) }
                            val modifier = Modifier.weight(1f).testTag("visual-files-folder-$name")
                            val count = productivityFiles.count { it.parent == path || it.parent.startsWith("$path /") }
                            if (grid) {
                                Surface(onClick = open, modifier = modifier.heightIn(min = 90.dp), shape = RoundedCornerShape(15.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text(name, style = MaterialTheme.typography.titleSmall)
                                        Text("$count files", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                WorkspaceRow(name, "$count files", Icons.Default.Folder, modifier, open)
                            }
                        }
                        repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    val filtered = productivityFiles.filter { (recent || folder.isEmpty() || it.parent == folder) && (filter == "All types" || it.type == filter) }
    val files = when (sort) {
        "Name" -> filtered.sortedBy { it.name.lowercase() }
        "Size" -> filtered.sortedByDescending { it.sizeKb }
        else -> filtered
    }
    WorkspaceLabel(if (folder.isEmpty()) "Recent files" else "Files", files.size.toString())
    files.forEach { file ->
        WorkspaceRow(file.name, "${file.type} · ${fileSize(file)} · ${file.date}", fileIcon(file.type),
            Modifier.testTag("visual-files-preview-${file.name}")) { navigator.inspect(VisualRow(file.name, "", "file:${file.name}")) }
    }
    if (files.isEmpty()) Text("No matching files", style = MaterialTheme.typography.bodyMedium)
}

private fun fileSize(file: SampleFile) = if (file.sizeKb >= 1000) "${file.sizeKb / 1000.0} MB" else "${file.sizeKb} KB"
private fun fileIcon(type: String): ImageVector = when (type) {
    "PDF" -> Icons.Default.PictureAsPdf
    "Image" -> Icons.Default.Image
    "Audio" -> Icons.Default.AudioFile
    else -> Icons.Default.Description
}

@Composable
private fun StorageStrip() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("visual-files-storage")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkspaceLabel("Sample storage", "28.9 / 64 GB")
            Row(Modifier.fillMaxWidth().height(9.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(12f to colors.primary, 10f to colors.tertiary, 6.9f to colors.secondary, 35.1f to colors.outlineVariant).forEach { (part, color) ->
                    Spacer(Modifier.fillMaxHeight().weight(part).background(color, CircleShape))
                }
            }
            Text("Documents  12 GB   ·   Images  10 GB   ·   Audio  6.9 GB", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FilePreview(file: SampleFile) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("visual-detail"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.testTag("visual-files-preview-${file.name}")) { WorkspaceLabel(file.name, fileSize(file)) }
        Text(file.parent, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            when (file.type) {
                "Image" -> Canvas(Modifier.fillMaxWidth().height(230.dp).padding(18.dp)) {
                    val peaks = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width * .29f, size.height * .29f)
                        lineTo(size.width * .52f, size.height * .72f)
                        lineTo(size.width * .73f, size.height * .37f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(peaks, colors.primaryContainer)
                    drawCircle(colors.tertiary, radius = 20.dp.toPx(), center = Offset(size.width * .72f, size.height * .22f))
                }
                "Audio" -> Canvas(Modifier.fillMaxWidth().height(125.dp).padding(18.dp)) {
                    val bars = listOf(2, 4, 3, 7, 9, 5, 2, 5, 9, 6, 3, 8, 5, 4, 7, 2, 4, 6, 3, 2)
                    val spacing = size.width / bars.size
                    bars.forEachIndexed { index, value ->
                        val height = size.height * value / 10f
                        drawRoundRect(colors.tertiary, Offset(index * spacing, (size.height - height) / 2f),
                            Size(spacing * .55f, height), CornerRadius(4.dp.toPx()))
                    }
                }
                else -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(fileIcon(file.type), null, Modifier.size(36.dp), tint = colors.primary)
                    Text(if (file.type == "PDF") "Project Aurora" else "Cloud atlas", style = MaterialTheme.typography.headlineSmall)
                    Text(if (file.type == "PDF") "A field guide to recording the changing light on a short afternoon walk."
                        else "Cumulus · rounded outlines\nCirrus · fine, feathered lines\nStratus · low, even layers")
                }
            }
        }
        Text("Bundled preview · ${file.type} · ${file.date}", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val calendarEventDays = setOf(12, 14, 16)

@Composable
private fun CalendarWorkspace(module: VisualModule, navigator: VisualNavigator) {
    var day by remember { mutableIntStateOf(14) }
    val colors = MaterialTheme.colorScheme
    val detail = navigator.detail
    if (detail != null) {
        WorkspaceDetail(detail, Icons.Default.Event)
        return
    }

    when (navigator.section) {
        2 -> {
            WorkspaceLabel("Event preview", "14 May")
            val example = module.sections[2].rows
            Surface(shape = RoundedCornerShape(22.dp), color = colors.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Event, null)
                    Text("Studio planning", style = MaterialTheme.typography.headlineSmall)
                    Text("Thu 14 May · 09:30–10:15")
                }
            }
            example.forEachIndexed { index, item ->
                WorkspaceRow(item.title, if (index == 0) "09:30 · 45 min" else "Studio · 15 min before",
                    if (index == 0) Icons.Default.Schedule else Icons.Default.Notifications, Modifier.testTag("visual-row-$index")) {
                    navigator.inspect(item)
                }
            }
        }
        1 -> {
            val week = (day + 3) / 7
            val first = week * 7 - 3
            WorkspaceLabel("May ${first.coerceAtLeast(1)}–${first + 6}", "Week ${18 + week}")
            Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    weekdays.forEachIndexed { index, label ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            val date = first + index
                            if (date > 0) CalendarDay(date, day, onSelect = { day = it })
                            else Spacer(Modifier.height(48.dp))
                        }
                    }
                }
            }
            WorkspaceLabel("$day May", "Week agenda")
            val index = when (day) { 12 -> 0; 13 -> 1; 16 -> 2; else -> -1 }
            val item = module.sections[1].rows.getOrNull(index)
            if (day == 14) CalendarEvents(module.sections[0].rows, navigator)
            else if (item != null) {
                WorkspaceRow(item.title, when (index) { 0 -> "16:00 · Sketch review"; 2 -> "All day · Studio open day"; else -> "No events" },
                    Icons.Default.Event, Modifier.testTag("visual-row-0")) { navigator.inspect(item) }
            } else Text("No sample events", color = colors.onSurfaceVariant)
        }
        else -> {
            WorkspaceLabel("May 2026", "Month")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 660.dp
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1.1f)) { CalendarMonth(day) { day = it } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CalendarAgenda(module, navigator, day)
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CalendarMonth(day) { day = it }
                    CalendarAgenda(module, navigator, day)
                }
            }
        }
    }
}

@Composable
private fun CalendarMonth(selected: Int, onSelect: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.testTag("visual-calendar-month")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdays.forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            repeat(5) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = week * 7 + column - 3
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day in 1..31) CalendarDay(day, selected, onSelect)
                            else Spacer(Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(day: Int, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(48.dp).clickable { onSelect(day) }.testTag("visual-calendar-day-$day"),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.size(35.dp).background(if (day == selected) colors.primaryContainer else Color.Transparent, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(day.toString(), color = if (day == selected) colors.onPrimaryContainer else colors.onSurface)
            if (day in calendarEventDays) Spacer(Modifier.size(4.dp).background(colors.tertiary, CircleShape))
        }
    }
}

@Composable
private fun CalendarAgenda(module: VisualModule, navigator: VisualNavigator, day: Int) {
    WorkspaceLabel("$day May", "Day agenda")
    when (day) {
        14 -> CalendarEvents(module.sections[0].rows, navigator)
        12, 16 -> {
            val item = module.sections[1].rows[if (day == 12) 0 else 2]
            WorkspaceRow(if (day == 12) "Sketch review" else "Studio open day", if (day == 12) "16:00 · Studio" else "All day · Studio",
                Icons.Default.Event, Modifier.testTag("visual-row-0")) { navigator.inspect(item) }
        }
        else -> Text("No sample events", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarEvents(items: List<VisualRow>, navigator: VisualNavigator) {
    val colors = MaterialTheme.colorScheme
    items.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { navigator.inspect(item) }.testTag("visual-row-$index"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.title.substringBefore(" · "), Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp).height(34.dp).background(if (index == 0) colors.primary else colors.tertiary, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(item.title.substringAfter(" · "), style = MaterialTheme.typography.titleMedium)
                Text(if (index == 0) "Studio · 45 min" else "Personal · ${item.badge}", style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .5f))
    }
}

@Composable
private fun TasksWorkspace(module: VisualModule, navigator: VisualNavigator) {
    val completed = remember { mutableStateListOf(false, false, true) }
    var filter by remember { mutableStateOf("All") }
    val colors = MaterialTheme.colorScheme
    navigator.detail?.let {
        WorkspaceDetail(it, Icons.Default.TaskAlt)
        return
    }
    val rows = module.sections[navigator.section].rows
    when (navigator.section) {
        0 -> {
            val total = completed.count { it }
            WorkspaceLabel("Today", "$total of ${completed.size} done")
            Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(completed.size) { index ->
                    Spacer(Modifier.weight(1f).fillMaxHeight().background(
                        if (index < total) colors.primary else colors.outlineVariant, CircleShape,
                    ))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Open", "Done").forEach { label ->
                    FilterChip(filter == label, { filter = label }, { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            rows.forEachIndexed { index, item ->
                if (filter == "All" || completed[index] == (filter == "Done")) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 61.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(completed[index], { completed[index] = it }, Modifier.testTag("visual-task-check-$index"))
                        Column(Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable { navigator.inspect(item) }.testTag("visual-row-$index").padding(vertical = 4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium,
                                textDecoration = if (completed[index]) TextDecoration.LineThrough else TextDecoration.None)
                            Text(listOf("Aurora · 11:00", "Window Garden", "Pocket Observatory")[index], style = MaterialTheme.typography.labelMedium)
                        }
                        if (index == 0) {
                            Icon(Icons.Default.Flag, "High priority", tint = colors.error, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .5f))
                }
            }
        }
        1 -> {
            WorkspaceLabel("Projects", "2 active")
            rows.forEachIndexed { index, item ->
                Surface(onClick = { navigator.inspect(item) }, shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("visual-row-$index")) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        WorkspaceLabel(item.title, item.badge.orEmpty())
                        LinearProgressIndicator(progress = { if (index == 0) .33f else .5f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        else -> {
            WorkspaceLabel("Review", "Sample inbox")
            rows.forEachIndexed { index, item ->
                WorkspaceRow(item.title, item.badge.orEmpty(), if (index == 0) Icons.Default.Inbox else Icons.Default.Schedule,
                    Modifier.testTag("visual-row-$index")) { navigator.inspect(item) }
            }
        }
    }
}

@Composable
private fun ClipboardWorkspace(module: VisualModule, navigator: VisualNavigator) {
    var clipType by remember { mutableStateOf("All") }
    var pinned by remember { mutableStateOf(false) }
    var retention by remember { mutableStateOf("Session") }
    val colors = MaterialTheme.colorScheme
    val detail = navigator.detail
    val recent = module.sections[0].rows
    val rows = module.sections[navigator.section].rows
    if (detail != null) {
        Column(Modifier.fillMaxWidth().testTag("visual-detail"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkspaceLabel(detail.title, detail.badge.orEmpty())
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Text(detail.detail, Modifier.padding(16.dp))
            }
            if (detail == recent[0]) FilledTonalButton({ pinned = !pinned }) {
                Icon(Icons.Default.PushPin, null)
                Spacer(Modifier.width(7.dp))
                Text(if (pinned) "Unpin sample" else "Pin sample")
            }
            Text("Bundled clip · Android clipboard untouched", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        return
    }
    when (navigator.section) {
        2 -> {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, tint = colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("History collection", style = MaterialTheme.typography.titleMedium)
                    Text("Inactive", style = MaterialTheme.typography.labelMedium)
                }
                Switch(checked = false, onCheckedChange = null, enabled = false)
            }
            WorkspaceLabel("Retention preview", retention)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Session", "1 day", "1 week").forEach { option ->
                    FilterChip(retention == option, { retention = option }, { Text(option) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            rows.forEachIndexed { index, item ->
                WorkspaceRow(item.title, item.badge.orEmpty(), Icons.Default.Info, Modifier.testTag("visual-row-$index")) { navigator.inspect(item) }
            }
        }
        else -> {
            WorkspaceLabel(if (navigator.section == 0) "Clip history" else "Pinned clips", "Bundled clips")
            if (navigator.section == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Text", "Links").forEach { option ->
                        FilterChip(clipType == option, { clipType = option }, { Text(option) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            val clips = if (navigator.section == 1) {
                if (pinned) rows.take(1) + recent[0] else rows.take(1)
            } else rows.filter { clipType == "All" || (clipType == "Links") == (it.badge == "Link") }
            clips.forEachIndexed { index, item ->
                WorkspaceRow(item.title, item.detail.substringBefore("\n").substringBefore(". This sentence"),
                    if (item.badge == "Link") Icons.Default.Link else if (navigator.section == 1) Icons.Default.PushPin else Icons.Default.ContentPaste,
                    Modifier.testTag("visual-row-$index")) { navigator.inspect(item) }
            }
        }
    }
}

@Composable
private fun WorkspaceLabel(title: String, meta: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceSearch(query: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onChange, modifier = modifier.testTag("visual-notes-search"),
        singleLine = true, placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (query.isEmpty()) null else { { IconButton({ onChange("") }) { Icon(Icons.Default.Close, "Clear search") } } },
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun WorkspaceRow(title: String, subtitle: String, image: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).background(colors.secondaryContainer, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(image, null, tint = colors.onSecondaryContainer, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceDetail(item: VisualRow, image: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("visual-detail").padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(image, null, tint = colors.primary, modifier = Modifier.size(32.dp))
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        item.badge?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
        Text(item.detail, style = MaterialTheme.typography.bodyLarge)
    }
}
