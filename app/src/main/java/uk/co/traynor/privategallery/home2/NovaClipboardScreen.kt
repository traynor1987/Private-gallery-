package uk.co.traynor.privategallery.home2

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext

private enum class ClipboardConfirmation { DELETE, CLEAR, DISCARD, RETENTION }

@Composable
internal fun NovaClipboardScreen(space: SpaceContext, onBack: () -> Unit, openSnippetId: String? = null) {
    val scope = rememberCoroutineScope()
    val hidden = space.spaceId == "hidden"
    val clipboard = remember(space) {
        if (hidden) null else space.getSystemService(ClipboardManager::class.java)
    }
    var store by remember(space) { mutableStateOf<NovaClipboardLibrary?>(null) }
    var snapshot by remember(space) { mutableStateOf<NovaClipboardLibrary.Snapshot?>(null) }
    var editor by remember(space) { mutableStateOf<NovaSnippet?>(null) }
    var query by remember(space) { mutableStateOf("") }
    var favouritesOnly by remember(space) { mutableStateOf(false) }
    var category by remember(space) { mutableStateOf<String?>(null) }
    var reveal by remember(space) { mutableStateOf(false) }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var busy by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var pendingRetention by remember(space) { mutableStateOf<SnippetRetention?>(null) }
    var confirmation by remember(space) { mutableStateOf<ClipboardConfirmation?>(null) }

    LaunchedEffect(space, reload) {
        busy = true
        val opened = withContext(Dispatchers.IO) {
            runCatching { NovaClipboardLibrary(space).let { it to it.read(Instant.now()) } }
        }
        if (!space.current()) return@LaunchedEffect
        opened.onSuccess { (library, saved) ->
            store = library
            snapshot = saved
            if (editor == null) editor = saved.snippets.firstOrNull { it.id == openSnippetId }
            status = null
        }.onFailure { status = "Encrypted snippets could not be opened." }
        busy = false
    }

    fun persist(
        snippets: List<NovaSnippet>,
        message: String,
        retention: SnippetRetention? = null,
        protectAllCopies: Boolean? = null,
    ) {
        val library = store ?: return
        val previous = snapshot ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    library.save(previous, retention ?: previous.retention, snippets, Instant.now(),
                        protectAllCopies ?: previous.protectAllCopies)
                }
            }
            if (!space.current()) return@launch
            result.onSuccess { saved ->
                if (saved == null) status = "Snippets changed elsewhere. Close the editor and reload."
                else {
                    snapshot = saved
                    editor = null
                    reveal = false
                    status = message
                }
            }.onFailure { status = "The encrypted snippet library may be full or unavailable." }
            busy = false
        }
    }

    fun closeEditor() {
        val current = editor ?: return
        val saved = snapshot?.snippets?.find { it.id == current.id }
        if (current != saved && (saved != null || current.title.isNotBlank() ||
                current.text.isNotBlank() || current.category.isNotBlank())) {
            confirmation = ClipboardConfirmation.DISCARD
        } else {
            editor = null
            reveal = false
        }
    }

    fun copy(snippet: NovaSnippet) {
        if (!space.current()) return
        val target = clipboard ?: return
        status = runCatching {
            val clip = ClipData.newPlainText("Nova Clipboard", snippet.text)
            if (snippet.sensitive || snapshot?.protectAllCopies == true) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            target.setPrimaryClip(clip)
            "Copied to Android clipboard"
        }.getOrDefault("Android clipboard is unavailable.")
    }

    fun paste(snippet: NovaSnippet) {
        if (!space.current()) return
        val clip = runCatching { clipboard?.primaryClip }.getOrNull()
        if (clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) {
            status = "Android marked this clipboard item as sensitive."
            return
        }
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank() || text.length > 16_000) {
            status = "The clipboard has no supported text under 16,000 characters."
            return
        }
        editor = snippet.copy(text = text)
        status = null
    }

    BackHandler(enabled = busy || editor != null) { if (!busy) closeEditor() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp).testTag("nova-clipboard"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoreWorkspaceHeader("Nova Clipboard", enabled = !busy, onBack = { if (editor != null) closeEditor() else onBack() })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (!busy && snapshot == null) TextButton(onClick = { reload++ }) { Text("Try again") }
        val current = editor
        val snippets = snapshot?.snippets.orEmpty()
        if (current == null) {
            NovaCoreAppActions {
                Button(enabled = !busy && snapshot != null, onClick = {
                    status = null
                    reveal = false
                    editor = NovaSnippet(UUID.randomUUID().toString(), "", "", "", false, false, Instant.now())
                }) { Text("New snippet") }
            }
            WorkspaceSearch(query, { query = it }, "Search snippets", Modifier.fillMaxWidth(),
                enabled = !busy && snapshot != null)
            if (snapshot != null) {
                val privacyEnabled = snapshot?.protectAllCopies == true
                if (!hidden) FilterChip(privacyEnabled, {
                    persist(snippets, "Clipboard privacy updated", protectAllCopies = !privacyEnabled)
                }, { Text("Protect all snippet copies") }, enabled = !busy)
                Text("Retention for unpinned snippets")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SnippetRetention.entries.forEach { retention ->
                        FilterChip(snapshot?.retention == retention, onClick = {
                            pendingRetention = retention
                            confirmation = ClipboardConfirmation.RETENTION
                        }, label = { Text(retention.label) }, enabled = !busy)
                    }
                }
            }
            val categories = snippets.map { it.category }.filter(String::isNotBlank).distinct().sorted()
            val activeCategory = category?.takeIf { it in categories }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(favouritesOnly, { favouritesOnly = !favouritesOnly }, { Text("Favourites") })
                if (categories.isNotEmpty()) {
                    FilterChip(activeCategory == null, { category = null }, { Text("All categories") })
                    categories.forEach { label ->
                        FilterChip(activeCategory == label, { category = label }, { Text(label) })
                    }
                }
            }
            val search = query.trim()
            val visible = snippets.filter {
                (!favouritesOnly || it.pinned) && (activeCategory == null || it.category == activeCategory) &&
                    (it.title.contains(search, true) || it.category.contains(search, true) ||
                        (!it.sensitive && it.text.contains(search, true)))
            }.sortedWith(compareByDescending<NovaSnippet> { it.pinned }.thenByDescending { it.updated })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!busy && snapshot != null && visible.isEmpty()) item { Text("No matching snippets") }
                items(visible, key = { it.id }) { snippet ->
                    CoreWorkspaceCard(onClick = { reveal = false; status = null; editor = snippet },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(snippet.title.ifBlank { if (snippet.sensitive) "Sensitive snippet" else "Untitled snippet" })
                            val metadata = listOf(snippet.category, if (snippet.pinned) "Pinned" else "",
                                if (snippet.sensitive) "Protected" else "").filter(String::isNotBlank)
                            if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                            if (!snippet.sensitive) Text(snippet.text.take(100), maxLines = 2)
                        }
                    }
                }
                if (snippets.isNotEmpty()) item {
                    TextButton(enabled = !busy, onClick = { confirmation = ClipboardConfirmation.CLEAR }) {
                        Text("Clear saved snippets")
                    }
                }
            }
        } else {
            val saved = snippets.find { it.id == current.id }
            val protectedEditing = current.sensitive || snapshot?.protectAllCopies == true
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current.title, { editor = current.copy(title = it.take(200)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, enabled = !busy, singleLine = true)
                OutlinedTextField(current.category, { editor = current.copy(category = it.take(120)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Category") }, enabled = !busy, singleLine = true)
                OutlinedTextField(current.text, { editor = current.copy(text = it.take(16_000)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Snippet") }, minLines = 3,
                    enabled = !busy && (!current.sensitive || reveal),
                    keyboardOptions = if (hidden || protectedEditing) KeyboardOptions(
                        keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
                    visualTransformation = if (protectedEditing) PasswordVisualTransformation()
                        else VisualTransformation.None)
                if (protectedEditing && (!current.sensitive || reveal)) Text(current.text)
                if (current.sensitive && !reveal) {
                    TextButton(enabled = !busy, onClick = { reveal = true }) { Text("Reveal to edit") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(current.sensitive, { editor = current.copy(sensitive = !current.sensitive); reveal = false },
                        { Text("Sensitive") }, enabled = !busy && (!current.sensitive || reveal))
                    FilterChip(current.pinned, { editor = current.copy(pinned = !current.pinned) },
                        { Text("Pinned") }, enabled = !busy)
                }
                if (!hidden && (!current.sensitive || reveal)) {
                    TextButton(enabled = !busy, onClick = { paste(current) }) { Text("Paste Android clipboard text") }
                    if (current.text.isNotBlank()) {
                        TextButton(enabled = !busy, onClick = { copy(current) }) { Text("Copy to Android clipboard") }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !busy && current.text.isNotBlank(), onClick = {
                        val edited = current.copy(updated = Instant.now())
                        val next = if (saved == null) snippets + edited
                            else snippets.map { if (it.id == edited.id) edited else it }
                        persist(next, "Snippet saved")
                    }) { Text("Save snippet") }
                    TextButton(enabled = !busy, onClick = { closeEditor() }) { Text("Back") }
                    if (saved != null) {
                        TextButton(enabled = !busy, onClick = { confirmation = ClipboardConfirmation.DELETE }) { Text("Delete") }
                    }
                }
            }
        }
    }

    confirmation?.let { action ->
        val title = when (action) {
            ClipboardConfirmation.DELETE -> "Delete this snippet?"
            ClipboardConfirmation.CLEAR -> "Delete all saved snippets?"
            ClipboardConfirmation.DISCARD -> "Discard unsaved changes?"
            ClipboardConfirmation.RETENTION -> "Apply retention and delete expired snippets?"
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(title) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (action) {
                        ClipboardConfirmation.DELETE -> persist(snapshot?.snippets.orEmpty()
                            .filterNot { it.id == editor?.id }, "Snippet deleted")
                        ClipboardConfirmation.CLEAR -> persist(emptyList(), "Saved snippets cleared")
                        ClipboardConfirmation.DISCARD -> { editor = null; reveal = false }
                        ClipboardConfirmation.RETENTION -> pendingRetention?.let {
                            persist(snapshot?.snippets.orEmpty(), "Retention changed", it)
                        }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}
