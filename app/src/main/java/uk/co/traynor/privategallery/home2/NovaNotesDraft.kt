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
    var query by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var busy by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var confirmation by remember(space) { mutableStateOf<NotesConfirmation?>(null) }
    LaunchedEffect(space, reload) {
        busy = true
        val result = withContext(Dispatchers.IO) {
            runCatching { NovaNotesLibrary(space).let { it to it.read() } }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (store, saved) ->
            library = store
            snapshot = saved
            status = null
        }.onFailure { status = "Encrypted notes could not be opened." }
        busy = false
    }

    fun persist(notes: List<NovaNote>, successMessage: String) {
        val store = library ?: return
        val previous = snapshot ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.save(previous, notes) } }
            if (!space.current()) return@launch
            result.onSuccess { saved ->
                if (saved == null) {
                    status = "Notes changed elsewhere. Return to the list and reload before editing again."
                } else {
                    snapshot = saved
                    editor = null
                    status = successMessage
                }
            }.onFailure { status = "Notes could not be saved. The encrypted library may be full or unavailable." }
            busy = false
        }
    }

    fun closeEditor() {
        val current = editor ?: return
        val saved = snapshot?.notes?.find { it.id == current.id } ?: NovaNote(current.id, "", "")
        if (current != saved) confirmation = NotesConfirmation.DISCARD
        else editor = null
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
                Button(enabled = !busy && snapshot != null, onClick = {
                    status = null
                    editor = NovaNote(UUID.randomUUID().toString(), "", "")
                }) { Text("New note") }
                TextButton(enabled = !busy, onClick = { reload++ }) { Text("Reload") }
                TextButton(enabled = !busy, onClick = onBack) { Text("Done") }
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
                        onClick = { status = null; editor = item },
                        enabled = !busy,
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
                OutlinedTextField(note.title, { editor = note.copy(title = it.take(200)) }, enabled = !busy,
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note.body, { editor = note.copy(body = it.take(32_000)) }, enabled = !busy,
                    label = { Text("Note") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(note.pinned, { editor = note.copy(pinned = !note.pinned) },
                        { Text("Pinned") }, enabled = !busy)
                    if (saved.any { it.id == note.id }) {
                        TextButton(enabled = !busy, onClick = { confirmation = NotesConfirmation.DELETE }) { Text("Delete") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && (note.title.isNotBlank() || note.body.isNotBlank()), onClick = {
                        val notes = if (saved.any { it.id == note.id }) {
                            saved.map { if (it.id == note.id) note else it }
                        } else listOf(note) + saved
                        persist(notes, "Note saved")
                    }) { Text("Save note") }
                    TextButton(enabled = !busy, onClick = { closeEditor() }) { Text("Back to notes") }
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
                        persist(snapshot?.notes.orEmpty().filterNot { it.id == editor?.id }, "Note deleted")
                    } else editor = null
                }) { Text(if (deleting) "Delete" else "Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}
