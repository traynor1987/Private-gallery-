package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext

private data class HiddenSearchResult(
    val label: String,
    val details: String,
    val destination: NovaCoreLink.Record,
)

@Composable
internal fun HiddenSpaceSearch(space: SpaceContext, onBack: () -> Unit, onOpen: (NovaCoreLink) -> Unit) {
    check(space.spaceId == "hidden")
    var query by remember(space) { mutableStateOf("") }
    var results by remember(space) { mutableStateOf<List<HiddenSearchResult>?>(null) }
    var failed by remember(space) { mutableStateOf(false) }
    LaunchedEffect(space, query) {
        val term = query.trim()
        results = null
        failed = false
        if (term.isEmpty()) return@LaunchedEffect
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                buildList {
                    val notes = NovaNotesLibrary(space).read()
                    notes.notes.filterNot(notes::isProtected).forEach { note ->
                        if (listOf(note.title, note.body, note.notebook, note.tags).any { it.contains(term, true) })
                            add(HiddenSearchResult(note.title.ifBlank { "Untitled note" }, "Notes", NovaCoreLink.Record(NovaCoreApp.NOTES, note.id)))
                    }
                    NovaTaskLibrary(space).read().tasks.forEach { task ->
                        if (listOf(task.title, task.description, task.project, task.tags).any { it.contains(term, true) })
                            add(HiddenSearchResult(task.title, "Tasks", NovaCoreLink.Record(NovaCoreApp.TASKS, task.id)))
                    }
                    NovaCalendarLibrary(space).read().events.forEach { event ->
                        if (listOf(event.title, event.description).any { it.contains(term, true) })
                            add(HiddenSearchResult(event.title, "Calendar", NovaCoreLink.Record(NovaCoreApp.CALENDAR, event.id)))
                    }
                    NovaClipboardLibrary(space).read(Instant.now()).snippets.filterNot { it.sensitive }.forEach { snippet ->
                        if (listOf(snippet.title, snippet.text, snippet.category).any { it.contains(term, true) })
                            add(HiddenSearchResult(snippet.title.ifBlank { "Untitled snippet" }, "Clipboard", NovaCoreLink.Record(NovaCoreApp.CLIPBOARD, snippet.id)))
                    }
                }.take(100)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            space.publishIfCurrent {
                results = outcome.getOrNull()
                failed = outcome.isFailure
            }
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CoreWorkspaceHeader("Search Hidden Space", enabled = true, onBack = onBack)
        WorkspaceSearch(query, { query = it.take(120) }, "Search private records", Modifier.fillMaxWidth())
        when {
            failed -> Text("Private records could not be opened.")
            query.isBlank() -> Unit
            results == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            results?.isEmpty() == true -> Text("No matching records.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results.orEmpty(), key = { it.destination.app.storageId + ":" + it.destination.id }) { hit ->
                CoreWorkspaceCard(onClick = { if (space.current()) onOpen(hit.destination) },
                    enabled = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(hit.label, style = MaterialTheme.typography.titleSmall)
                        Text(hit.details, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
