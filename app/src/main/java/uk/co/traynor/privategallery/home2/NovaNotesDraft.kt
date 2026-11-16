package uk.co.traynor.privategallery.home2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext

private enum class NotesConfirmation { DELETE, DISCARD }

@Composable
internal fun NovaNotesDraft(space: SpaceContext, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var library by remember(space) { mutableStateOf<NovaNotesLibrary?>(null) }
    var snapshot by remember(space) { mutableStateOf<NovaNotesLibrary.Snapshot?>(null) }
    var editor by remember(space) { mutableStateOf<NovaNote?>(null) }
    var editorBase by remember(space) { mutableStateOf<NovaNote?>(null) }
    var draft by remember(space) { mutableStateOf<NovaNotesLibrary.Draft?>(null) }
    var query by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var busy by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var confirmation by remember(space) { mutableStateOf<NotesConfirmation?>(null) }
    LaunchedEffect(space, reload) {
        busy = true
        val result = withContext(Dispatchers.IO) {
            runCatching { NovaNotesLibrary(space).let { Triple(it, it.read(), it.readDraft()) } }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (store, saved, recovered) ->
            library = store
            snapshot = saved
            draft = recovered
            editor = recovered.note
            editorBase = null
            status = if (recovered.note == null) null else "Recovered an unsaved draft"
        }.onFailure { status = "Encrypted notes could not be opened." }
        busy = false
    }

    fun checkpoint(note: NovaNote): NovaNotesLibrary.Draft? {
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
        if (note.title.length > 200 || note.body.length > 32_000) {
            status = "Notes support up to 200 title characters and 32,000 body characters."
            return
        }
        editor = note
        checkpoint(note)
    }

    fun persist(delete: Boolean) {
        val store = library ?: return
        val current = editor ?: return
        val pending = checkpoint(current) ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.finishDraft(pending, delete) } }
            if (!space.current()) return@launch
            result.onSuccess { saved ->
                if (saved == null) status = "The saved note changed. Your draft is still available."
                else {
                    snapshot = saved
                    draft = NovaNotesLibrary.Draft(Math.addExact(checkNotNull(pending.revision), 1L), null, null)
                    editor = null
                    editorBase = null
                    status = if (delete) "Note deleted" else "Note saved"
                }
            }.onFailure { status = "Could not save the note. The recovery draft is still available." }
            busy = false
        }
    }

    fun closeEditor() {
        val current = editor ?: return
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
        Text("Nova Notes", style = MaterialTheme.typography.headlineSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
        val note = editor
        val saved = snapshot?.notes.orEmpty()
        if (note == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && snapshot != null && draft?.note == null, onClick = {
                    status = null
                    editorBase = null
                    editor = NovaNote(UUID.randomUUID().toString(), "", "")
                }) { Text("New note") }
                TextButton(enabled = !busy, onClick = { reload++ }) { Text("Reload") }
                TextButton(enabled = !busy, onClick = onBack) { Text("Done") }
            }
            val pending = draft
            if (pending?.note != null) {
                Button(enabled = !busy, onClick = {
                    editor = pending.note
                    editorBase = null
                }) { Text("Resume draft") }
            }
            OutlinedTextField(query, { query = it }, enabled = !busy && snapshot != null,
                label = { Text("Search notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val search = query.trim()
            val filtered = saved.filter { it.title.contains(search, true) || it.body.contains(search, true) }
                .sortedByDescending { it.pinned }
            if (!busy && snapshot != null && filtered.isEmpty()) {
                Text(if (saved.isEmpty()) "No notes yet" else "No matching notes")
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { item ->
                    OutlinedCard(
                        onClick = { status = null; editorBase = item; editor = item },
                        enabled = !busy && pending?.note == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.title.ifBlank { "Untitled note" })
                            if (item.pinned) Text("Pinned", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(note.title, { edit(note.copy(title = it)) }, enabled = !busy,
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("nova-note-title"))
                OutlinedTextField(note.body, { edit(note.copy(body = it)) }, enabled = !busy,
                    label = { Text("Note") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(note.pinned, { edit(note.copy(pinned = !note.pinned)) },
                        { Text("Pinned") }, enabled = !busy)
                    if (saved.any { it.id == note.id }) {
                        TextButton(enabled = !busy, onClick = { confirmation = NotesConfirmation.DELETE }) { Text("Delete") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && (note.title.isNotBlank() || note.body.isNotBlank()), onClick = {
                        persist(delete = false)
                    }) { Text("Save note") }
                    TextButton(enabled = !busy, onClick = { closeEditor() }) { Text("Back") }
                    TextButton(enabled = !busy, onClick = { confirmation = NotesConfirmation.DISCARD }) { Text("Discard draft") }
                }
            }
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
                            status = null
                        }
                    }
                }) { Text(if (deleting) "Delete" else "Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}
