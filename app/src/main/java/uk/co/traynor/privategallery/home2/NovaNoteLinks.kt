package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal sealed interface NovaCoreLink {
    val app: NovaCoreApp

    data class Record(override val app: NovaCoreApp, val id: String) : NovaCoreLink
    data class DueDay(val date: LocalDate) : NovaCoreLink {
        override val app = NovaCoreApp.CALENDAR
    }
}

@Composable
internal fun NovaNotePicker(
    space: SpaceContext, onChoose: (String) -> Unit, onDismiss: () -> Unit,
    title: String = "Link a saved note",
    includeDraft: Boolean = false,
) {
    var loaded by remember(space) { mutableStateOf<Result<List<NovaNote>>?>(null) }
    LaunchedEffect(space) {
        val result = withContext(Dispatchers.IO) { runCatching {
            val store = NovaNotesLibrary(space)
            val library = store.read()
            val saved = library.notes.filterNot(library::isProtected)
            val draft = if (includeDraft) store.readDraft().note?.takeUnless { library.requiredProtection(it).isNotEmpty() } else null
            if (draft == null) saved else listOf(draft) + saved.filterNot { it.id == draft.id }
        } }
        if (space.current()) loaded = result
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val result = loaded
            when {
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                result.isFailure -> Text("Saved notes could not be opened.")
                else -> {
                    val notes = result.getOrThrow()
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        if (notes.isEmpty()) item { Text("No saved notes yet.") }
                        items(notes, key = { it.id }) { note ->
                            TextButton(onClick = { if (space.current()) onChoose(note.id) }) {
                                Text(note.title.ifBlank { "Untitled note" })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun NovaRelatedNoteItems(
    space: SpaceContext,
    noteId: String,
    onOpen: (NovaCoreLink.Record) -> Unit,
    onDismiss: () -> Unit,
) {
    var loaded by remember(space, noteId) {
        mutableStateOf<Result<List<Pair<NovaCoreLink.Record, String>>>?>(null)
    }
    LaunchedEffect(space, noteId) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val tasks = NovaTaskLibrary(space).read().tasks.filter { it.noteId == noteId }.map {
                    NovaCoreLink.Record(NovaCoreApp.TASKS, it.id) to it.title
                }
                val events = NovaCalendarLibrary(space).read().events.filter { it.noteId == noteId }.map {
                    NovaCoreLink.Record(NovaCoreApp.CALENDAR, it.id) to it.title
                }
                tasks + events
            }
        }
        if (space.current()) loaded = result
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Linked tasks and events") },
        text = {
            val result = loaded
            when {
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                result.isFailure -> Text("Linked items could not be opened.")
                else -> {
                    val entries = result.getOrThrow()
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        if (entries.isEmpty()) item { Text("Link this note from a task or calendar event.") }
                        items(entries, key = { it.first.app.name + ":" + it.first.id }) { (link, title) ->
                            TextButton(onClick = { if (space.current()) onOpen(link) }) {
                                Text(link.app.label + " · " + title)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
