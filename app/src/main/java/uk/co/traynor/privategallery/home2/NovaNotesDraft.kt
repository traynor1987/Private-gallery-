package uk.co.traynor.privategallery.home2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.home2.NovaNoteAttachment

private enum class NotesConfirmation { DELETE, DISCARD }

private data class NotebookRequest(val name: String, val protect: Boolean? = null)

@Composable
internal fun NovaNotesDraft(
    space: SpaceContext,
    onBack: () -> Unit,
    openNoteId: String? = null,
    onOpenLink: ((NovaCoreLink) -> Unit)? = null,
    chooseAttachment: ((String) -> Unit)? = null,
    attachmentMessage: String? = null,
    consumeAttachmentMessage: () -> Unit = {},
    openFiles: (() -> Unit)? = null,
    exportAttachment: ((String, NovaNoteAttachment) -> Unit)? = null,
    verifyNotebookPin: ((CharArray) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    var library by remember(space) { mutableStateOf<NovaNotesLibrary?>(null) }
    var snapshot by remember(space) { mutableStateOf<NovaNotesLibrary.Snapshot?>(null) }
    var editor by remember(space) { mutableStateOf<NovaNote?>(null) }
    var editorBase by remember(space) { mutableStateOf<NovaNote?>(null) }
    var draft by remember(space) { mutableStateOf<NovaNotesLibrary.Draft?>(null) }
    var notebook by remember(space) { mutableStateOf<String?>(null) }
    var sortByTitle by remember(space) { mutableStateOf(false) }
    val undo = remember(space) { mutableStateListOf<NovaNote>() }
    val redo = remember(space) { mutableStateListOf<NovaNote>() }
    var query by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var attachmentPreview by remember(space) { mutableStateOf<NovaNoteAttachment?>(null) }
    var showSnippets by remember(space) { mutableStateOf(false) }
    var showLinks by remember(space) { mutableStateOf(false) }
    var resolveConflict by remember(space) { mutableStateOf(false) }
    var busy by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var confirmation by remember(space) { mutableStateOf<NotesConfirmation?>(null) }
    var unlockedNotebooks by remember(space) { mutableStateOf(setOf<String>()) }
    var notebookRequest by remember(space) { mutableStateOf<NotebookRequest?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(space, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlockedNotebooks = emptySet()
                notebookRequest = null
                attachmentPreview = null
                confirmation = null
                showLinks = false
                showSnippets = false
                undo.clear()
                redo.clear()
                query = ""
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun lockedNotebook(note: NovaNote): String? =
        snapshot?.requiredProtection(note)?.firstOrNull { it !in unlockedNotebooks }

    fun canSee(note: NovaNote): Boolean = lockedNotebook(note) == null

    LaunchedEffect(space, reload, openNoteId) {
        busy = true
        val result = withContext(Dispatchers.IO) {
            runCatching { NovaNotesLibrary(space).let { store -> Triple(store, store.read(), runCatching { store.readDraft() }) } }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (store, saved, recovery) ->
            val recovered = recovery.getOrNull()
            library = store
            snapshot = saved
            draft = recovered
            val requested = openNoteId?.let { id -> saved.notes.find { it.id == id } }
            editor = recovered?.note ?: requested
            editorBase = if (recovered?.note == null) requested else null
            resolveConflict = false
            undo.clear()
            redo.clear()
            status = attachmentMessage ?: when {
                recovery.isFailure -> "The recovery draft could not be read. Saved notes are read-only."
                recovered?.note != null && openNoteId != null && recovered.note.id != openNoteId ->
                    "Finish or discard the recovered draft before opening the linked note."
                recovered?.note != null -> "Recovered an unsaved draft"
                openNoteId != null && requested == null -> "The linked note is unavailable."
                else -> null
            }
        }.onFailure {
            library = null
            snapshot = null
            draft = null
            editor = null
            editorBase = null
            status = "Encrypted notes could not be opened."
        }
        if (attachmentMessage != null) consumeAttachmentMessage()
        busy = false
    }

    fun checkpoint(note: NovaNote): NovaNotesLibrary.Draft? {
        if (!canSee(note)) return null
        val store = library ?: return null
        val previous = draft ?: return null
        if (previous.note == note) return previous
        // Commit the editor record before returning from input. Space can retire as soon
        // as the app pauses, so a debounced write would lose the final edit on auto-lock.
        val saved = runCatching { store.writeDraft(previous, editorBase, note) }.getOrNull()
        if (saved == null) status = "Draft was not saved. Keep Notes open and retry."
        else {
            draft = saved
            status = "Draft saved for recovery"
        }
        return saved
    }

    fun edit(note: NovaNote) {
        val before = editor ?: return
        if (!canSee(before) || !canSee(note)) {
            status = "Unlock the destination notebook before moving a note there."
            return
        }
        if (note.title.length > 200 || note.body.length > 32_000 ||
            note.notebook.length > 120 || note.tags.length > 200) {
            status = "That edit exceeds the note field limit."
            return
        }
        if (before == note) return
        if (undo.size == 24) undo.removeAt(0)
        undo.add(before)
        redo.clear()
        editor = note
        checkpoint(note)
    }

    fun undoOrRedo(forward: Boolean) {
        val current = editor ?: return
        val from = if (forward) redo else undo
        val to = if (forward) undo else redo
        if (from.isEmpty()) return
        val target = from.last()
        if (!canSee(current) || !canSee(target)) return
        from.removeAt(from.lastIndex)
        to.add(current)
        editor = target
        checkpoint(target)
    }

    fun persist(delete: Boolean, asCopy: Boolean = false) {
        val store = library ?: return
        val current = editor ?: return
        val pending = checkpoint(current) ?: return
        val copyId = if (asCopy) UUID.randomUUID().toString() else null
        val notebookAccess = unlockedNotebooks
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.finishDraft(pending, delete, copyId, notebookAccess) }
            }
            if (!space.current()) return@launch
            result.onSuccess { saved ->
                if (saved == null) {
                    resolveConflict = true
                    status = "The saved note changed. Save your draft as a new note to keep both versions."
                }
                else {
                    snapshot = saved
                    draft = NovaNotesLibrary.Draft(Math.addExact(checkNotNull(pending.revision), 1L), null, null)
                    editor = null
                    editorBase = null
                    resolveConflict = false
                    status = if (delete) "Note deleted" else "Note saved"
                }
            }.onFailure { status = "Could not save the note. The recovery draft is still available." }
            busy = false
        }
    }

    fun closeEditor() {
        val current = editor ?: return
        if (!canSee(current)) {
            editor = null
            editorBase = null
            return
        }
        if (draft?.note != null || current != (editorBase ?: NovaNote(current.id, "", ""))) {
            if (checkpoint(current) == null) return
            status = "Draft saved for recovery"
        }
        editor = null
        editorBase = null
    }

    BackHandler(enabled = busy || editor != null) {
        if (!busy) closeEditor()
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp).testTag("nova-notes-draft"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoreWorkspaceHeader("Nova Notes", enabled = !busy, onBack = { if (editor != null) closeEditor() else onBack() })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (!busy && snapshot == null) TextButton(onClick = { reload++ }) { Text("Try again") }
        val note = editor
        val canEdit = !busy && draft != null
        val saved = snapshot?.notes.orEmpty()
        if (note == null) {
            NovaCoreAppActions {
                Button(enabled = canEdit && snapshot != null && draft?.note == null, onClick = {
                    status = null
                    editorBase = null
                    undo.clear()
                    redo.clear()
                    editor = NovaNote(UUID.randomUUID().toString(), "", "", notebook = notebook.orEmpty())
                }) { Text("New note") }
            }
            val pending = draft
            if (pending?.note != null) {
                Button(enabled = !busy, onClick = {
                    editor = pending.note
                    editorBase = null
                }) { Text("Resume draft") }
            }
            WorkspaceSearch(query, { query = it }, "Search notes", Modifier.fillMaxWidth(),
                enabled = !busy && snapshot != null)
            val protected = snapshot?.protectedNotebooks.orEmpty()
            val labels = saved.map { it.notebook }.filter(String::isNotBlank).associateBy(::notebookKey)
            val notebooks = (labels.keys + protected).sorted().map { labels[it] ?: it }
            val activeNotebook = notebook?.let(::notebookKey)
            val activeKey = activeNotebook.orEmpty()
            val protectedTab = activeKey in protected
            val mayOpenTab = activeKey in unlockedNotebooks
            if (notebooks.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(activeNotebook == null, { notebook = null }, { Text("All notebooks") })
                    notebooks.forEach { name ->
                        FilterChip(activeNotebook == notebookKey(name), { notebook = name }, {
                            Text(if (notebookKey(name) in protected) "Locked: $name" else name)
                        })
                    }
                }
            }
            if (notebook != null && verifyNotebookPin != null) {
                if (protectedTab && !mayOpenTab) {
                    TextButton(enabled = !busy, onClick = { notebookRequest = NotebookRequest(activeKey) }) {
                        Text("Unlock notebook")
                    }
                } else {
                    TextButton(enabled = !busy, onClick = {
                        notebookRequest = NotebookRequest(activeKey, protect = !protectedTab)
                    }) { Text(if (protectedTab) "Remove notebook protection" else "Protect notebook") }
                }
            }
            FilterChip(sortByTitle, { sortByTitle = !sortByTitle }, { Text("Sort by title") })
            val search = query.trim()
            val matching = saved.filter {
                canSee(it) && (activeNotebook == null || notebookKey(it.notebook) == activeNotebook) &&
                    listOf(it.title, it.body, it.notebook, it.tags).any { value -> value.contains(search, true) }
            }
            val pinnedFirst = compareByDescending<NovaNote> { it.pinned }
            val filtered = matching.sortedWith(if (sortByTitle) pinnedFirst.thenBy { it.title.lowercase() } else pinnedFirst)
            if (!busy && snapshot != null && filtered.isEmpty()) {
                Text(when {
                    protectedTab && !mayOpenTab -> "Unlock this notebook to view its notes."
                    saved.isEmpty() -> "No notes yet"
                    else -> "No matching notes"
                })
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 650.dp -> 3
                    maxWidth >= 310.dp -> 2
                    else -> 1
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val groups = listOf("Pinned" to filtered.filter { it.pinned },
                        (notebook ?: "Library") to filtered.filterNot { it.pinned })
                    for ((title, notes) in groups) {
                        if (notes.isEmpty()) continue
                        item { WorkspaceLabel(title, "${notes.size} notes") }
                        items(notes.chunked(columns), key = { it.first().id }) { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                group.forEach { item ->
                                    val colors = MaterialTheme.colorScheme
                                    WorkspaceNoteCard(
                                        item.title, item.body, item.notebook, item.tags,
                                        item.body.lineSequence().any { it.startsWith("- [") },
                                        if (item.pinned) colors.primaryContainer else colors.secondaryContainer,
                                        if (item.pinned) colors.onPrimaryContainer else colors.onSecondaryContainer,
                                        Modifier.weight(1f), enabled = canEdit && pending?.note == null,
                                        onClick = {
                                            status = null
                                            editorBase = item
                                            undo.clear()
                                            redo.clear()
                                            editor = item
                                        },
                                    ) {
                                        if (item.pinned) Icon(Icons.Default.PushPin, "Pinned", Modifier.size(14.dp))
                                    }
                                }
                                repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        } else if (!canSee(note)) {
            Text("This note is in a protected notebook.")
            if (verifyNotebookPin != null) Button(enabled = !busy, onClick = {
                lockedNotebook(note)?.let { notebookRequest = NotebookRequest(it) }
            }) { Text("Unlock notebook") }
            TextButton(onClick = { closeEditor() }) { Text("Back to notes") }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(note.title, { edit(note.copy(title = it)) }, enabled = canEdit,
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("nova-note-title"))
                OutlinedTextField(note.notebook, { edit(note.copy(notebook = it)) }, enabled = canEdit,
                    label = { Text("Notebook / folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note.tags, { edit(note.copy(tags = it)) }, enabled = canEdit,
                    label = { Text("Tags") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NotesBodyEditor(note, canEdit, undo.isNotEmpty(), redo.isNotEmpty(),
                    onBody = { edit(note.copy(body = it)) }, onUndo = { undoOrRedo(false) }, onRedo = { undoOrRedo(true) })
                TextButton(enabled = canEdit, onClick = { showSnippets = true }) { Text("Insert saved snippet") }
                Text("Attachments", style = MaterialTheme.typography.titleSmall)
                note.attachments.forEach { attachment ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy, onClick = { attachmentPreview = attachment },
                            modifier = Modifier.weight(1f)) { Text(attachment.name) }
                        TextButton(enabled = canEdit, onClick = {
                            edit(note.copy(attachments = note.attachments.filterNot { it.id == attachment.id }))
                        }) { Text("Remove") }
                    }
                }
                if (chooseAttachment != null) {
                    TextButton(enabled = canEdit && note.attachments.size < NovaNoteAttachment.MAX_COUNT, onClick = {
                        if (checkpoint(note) != null) chooseAttachment(note.id)
                    }) { Text("Attach file (up to 24 KiB)") }
                }
                if (openFiles != null) {
                    TextButton(enabled = canEdit, onClick = {
                        if (checkpoint(note) != null) openFiles()
                    }) { Text("Browse Nova Files") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(note.pinned, { edit(note.copy(pinned = !note.pinned)) },
                        { Text("Pinned") }, enabled = canEdit)
                    if (saved.any { it.id == note.id }) {
                        if (onOpenLink != null) TextButton(enabled = !busy, onClick = {
                            showLinks = true
                        }) { Text("Linked items") }
                        TextButton(enabled = canEdit, onClick = { confirmation = NotesConfirmation.DELETE }) { Text("Delete") }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = canEdit && (note.title.isNotBlank() || note.body.isNotBlank() || note.attachments.isNotEmpty()), onClick = {
                        persist(delete = false)
                    }) { Text("Save note") }
                    if (resolveConflict) TextButton(enabled = canEdit, onClick = { persist(delete = false, asCopy = true) }) {
                        Text("Save as a new note")
                    }
                    TextButton(enabled = !busy, onClick = { closeEditor() }) { Text("Back") }
                    TextButton(enabled = canEdit, onClick = { confirmation = NotesConfirmation.DISCARD }) { Text("Discard draft") }
                }
            }
        }
    }

    val snippetNote = editor
    if (showSnippets && snippetNote != null && canSee(snippetNote)) {
        NovaNotesSnippetPicker(space, onClose = { showSnippets = false }) { snippet ->
            showSnippets = false
            val separator = if (snippetNote.body.isEmpty()) "" else "\n"
            edit(snippetNote.copy(body = snippetNote.body + separator + snippet.text))
        }
    }

    attachmentPreview?.let { attachment ->
        val bytes = remember(attachment) {
            if (attachment.mime.startsWith("text/")) attachment.readBytes().let { bytes ->
                try { bytes.decodeToString() } finally { bytes.fill(0) }
            } else null
        }
        AlertDialog(
            onDismissRequest = { attachmentPreview = null },
            title = { Text(attachment.name) },
            text = {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    Text(bytes ?: "${attachment.mime}. An encrypted copy is stored with this note.")
                }
            },
            confirmButton = { TextButton(onClick = { attachmentPreview = null }) { Text("Close") } },
            dismissButton = {
                val note = editor
                if (note != null && snapshot?.requiredProtection(note)?.isEmpty() == true && exportAttachment != null) {
                    TextButton(enabled = !busy && draft != null, onClick = {
                        if (checkpoint(note) != null) {
                            attachmentPreview = null
                            exportAttachment(note.id, attachment)
                        }
                    }) { Text("Save a copy outside NovaOS") }
                }
            },
        )
    }
    val linkedNote = editor
    if (showLinks && linkedNote != null && canSee(linkedNote) && onOpenLink != null) {
        NovaRelatedNoteItems(space, linkedNote.id, onOpen = { link ->
            val before = draft?.note ?: editorBase
            if (before != null && linkedNote != before && checkpoint(linkedNote) == null) {
                showLinks = false
            } else {
                showLinks = false
                onOpenLink(link)
            }
        }, onDismiss = { showLinks = false })
    }

    val request = notebookRequest
    if (request != null && verifyNotebookPin != null) {
        NovaNotebookPinDialog(request, enabled = !busy, onClose = { notebookRequest = null }) { pin ->
            val store = library ?: return@NovaNotebookPinDialog false
            val previous = snapshot ?: return@NovaNotebookPinDialog false
            val accepted = withContext(Dispatchers.IO) {
                runCatching { verifyNotebookPin(pin) }.getOrDefault(false)
            }
            if (!accepted || notebookRequest !== request || !space.current()) return@NovaNotebookPinDialog false
            if (request.protect == null) {
                unlockedNotebooks = unlockedNotebooks + request.name
            } else {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { store.setNotebookProtected(previous, request.name, request.protect) }.getOrNull()
                }
                if (saved == null || !space.current()) return@NovaNotebookPinDialog false
                snapshot = saved
                unlockedNotebooks = unlockedNotebooks - request.name
                status = if (request.protect) "Notebook protected" else "Notebook protection removed"
            }
            notebookRequest = null
            true
        }
    }

    confirmation?.let { action ->
        val deleting = action == NotesConfirmation.DELETE
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (deleting) "Delete this note?" else "Discard unsaved changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (deleting) {
                        persist(delete = true)
                    } else {
                        val previous = draft
                        val cleared = if (previous?.note == null) previous else
                            runCatching { library?.discardDraft(previous) }.getOrNull()
                        if (cleared == null) status = "Could not discard the draft. Try again."
                        else {
                            draft = cleared
                            editor = null
                            editorBase = null
                            resolveConflict = false
                            status = null
                        }
                    }
                }) { Text(if (deleting) "Delete" else "Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NotesBodyEditor(
    note: NovaNote,
    enabled: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBody: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    var input by remember(note.id) { mutableStateOf(TextFieldValue(note.body)) }
    var preview by remember(note.id) { mutableStateOf(false) }
    LaunchedEffect(note.body) {
        if (input.text != note.body) input = TextFieldValue(note.body, TextRange(note.body.length))
    }
    fun setInput(updated: TextFieldValue) {
        if (updated.text.length <= 32_000) input = updated
        if (updated.text != note.body) onBody(updated.text)
    }
    fun wrap(marker: String) {
        val start = input.selection.min
        val end = input.selection.max
        if (start == end) {
            val text = input.text.substring(0, start) + marker + marker + input.text.substring(end)
            setInput(TextFieldValue(text, TextRange(start + marker.length)))
            return
        }
        val selected = input.text.substring(start, end).split('\n')
        val formatted = selected.mapIndexed { index, line ->
            val startsLine = index > 0 || start == 0 || input.text[start - 1] == '\n'
            val checklist = if (startsLine) noteChecklistPrefix.find(line)?.value.orEmpty() else ""
            val content = line.drop(checklist.length)
            if (content.isEmpty()) line else checklist + marker + content + marker
        }.joinToString("\n")
        val text = input.text.substring(0, start) + formatted + input.text.substring(end)
        val singleLine = selected.size == 1 && formatted.startsWith(marker)
        val selection = if (singleLine) TextRange(start + marker.length, start + formatted.length - marker.length)
            else TextRange(start + formatted.length)
        setInput(TextFieldValue(text, selection))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(enabled = enabled && canUndo, onClick = onUndo) { Text("Undo") }
        TextButton(enabled = enabled && canRedo, onClick = onRedo) { Text("Redo") }
        TextButton(enabled = enabled && !preview, onClick = { wrap("**") }) { Text("Bold") }
        TextButton(enabled = enabled && !preview, onClick = { wrap("*") }) { Text("Italic") }
        TextButton(enabled = enabled && !preview, onClick = {
            val text = input.text + if (input.text.isEmpty() || input.text.endsWith('\n')) "- [ ] " else "\n- [ ] "
            setInput(TextFieldValue(text, TextRange(text.length)))
        }) { Text("Add checklist item") }
        TextButton(enabled = enabled, onClick = { preview = !preview }) { Text(if (preview) "Edit text" else "Preview") }
    }
    if (!preview) {
        OutlinedTextField(input, ::setInput, enabled = enabled, label = { Text("Note") }, minLines = 6,
            modifier = Modifier.fillMaxWidth())
    } else {
        val lines = note.body.split('\n')
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            items(lines.size) { index ->
                val line = lines[index]
                val checked = line.startsWith("- [x] ", ignoreCase = true)
                if (checked || line.startsWith("- [ ] ")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = { value ->
                            val updated = lines.toMutableList()
                            updated[index] = (if (value) "- [x] " else "- [ ] ") + line.drop(6)
                            onBody(updated.joinToString("\n"))
                        }, enabled = enabled)
                        Text(renderNoteLine(line.drop(6)))
                    }
                } else Text(renderNoteLine(line))
            }
        }
    }
}

private val noteInlineMarkup = Regex("""\*\*\*([^*]+)\*\*\*|\*\*([^*]+)\*\*|\*([^*]+)\*""")
private val noteChecklistPrefix = Regex("""^- \[[ xX]\] """)

private fun renderNoteLine(line: String) = buildAnnotatedString {
    var position = 0
    for (match in noteInlineMarkup.findAll(line)) {
        append(line.substring(position, match.range.first))
        val text = match.groups[1]?.value ?: match.groups[2]?.value ?: match.groupValues[3]
        val style = when {
            match.groups[1] != null -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(fontWeight = FontWeight.Bold)
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        withStyle(style) { append(text) }
        position = match.range.last + 1
    }
    append(line.substring(position))
}


@Composable
private fun NovaNotesSnippetPicker(space: SpaceContext, onClose: () -> Unit, onInsert: (NovaSnippet) -> Unit) {
    var snippets by remember(space) { mutableStateOf<Result<List<NovaSnippet>>?>(null) }
    LaunchedEffect(space) {
        val result = withContext(Dispatchers.IO) {
            runCatching { NovaClipboardLibrary(space).read(java.time.Instant.now()).snippets }
        }
        if (space.current()) snippets = result
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Insert saved snippet") },
        text = {
            val result = snippets
            val list = result?.getOrNull()
            when {
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                list == null -> Text("Saved snippets could not be opened.")
                list.isEmpty() -> Text("You have no saved snippets in Nova Clipboard.")
                else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(list, key = NovaSnippet::id) { snippet ->
                        TextButton(onClick = { if (space.current()) onInsert(snippet) }) {
                            Text(if (snippet.sensitive) "Insert sensitive snippet" else snippet.title.ifBlank { "Untitled snippet" })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}


@Composable
private fun NovaNotebookPinDialog(
    request: NotebookRequest, enabled: Boolean, onClose: () -> Unit,
    verify: suspend (CharArray) -> Boolean,
) {
    var pin by remember(request) { mutableStateOf("") }
    var failed by remember(request) { mutableStateOf(false) }
    var checking by remember(request) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!checking) onClose() },
        title = {
            Text(when (request.protect) {
                true -> "Protect notebook"
                false -> "Remove notebook protection"
                null -> "Unlock notebook"
            })
        },
        text = {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit) },
                label = { Text("Primary Space PIN") }, singleLine = true, enabled = !checking,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = failed, supportingText = { if (failed) Text("Could not confirm the PIN or update the notebook.") })
        },
        confirmButton = {
            TextButton(enabled = enabled && !checking && pin.isNotBlank(), onClick = {
                val entered = pin.toCharArray()
                pin = ""
                checking = true
                scope.launch {
                    try { failed = !verify(entered) }
                    finally { entered.fill('\u0000'); checking = false }
                }
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(enabled = !checking, onClick = onClose) { Text("Cancel") } },
    )
}
