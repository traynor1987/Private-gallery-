package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** First offline Notes slice, with one editable Primary Space draft. */
@Composable
internal fun NovaNotesDraft(space: SpaceContext, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var store by remember(space) { mutableStateOf<NovaCoreAppDocuments?>(null) }
    var title by remember(space) { mutableStateOf("") }
    var body by remember(space) { mutableStateOf("") }
    var revision by remember(space) { mutableStateOf<Long?>(null) }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var busy by remember(space) { mutableStateOf(true) }
    LaunchedEffect(space) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val documents = NovaCoreAppDocuments(space, NovaCoreApp.NOTES)
                val item = documents.read("draft")
                Triple(documents, item?.revision, item?.json?.let { JSONObject(it) })
            }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (documents, savedRevision, json) ->
            store = documents
            revision = savedRevision
            title = json?.optString("title").orEmpty()
            body = json?.optString("body").orEmpty()
        }.onFailure { status = "The encrypted draft could not be opened." }
        busy = false
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("nova-notes-draft"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nova Notes", style = MaterialTheme.typography.headlineSmall)
        Text("Private draft")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it.take(200); status = null }, enabled = !busy && store != null,
            label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it.take(32_000); status = null }, enabled = !busy && store != null,
            label = { Text("Note") }, minLines = 6, modifier = Modifier.fillMaxWidth())
        status?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && store != null, onClick = {
                val documents = store ?: return@Button
                val expected = revision
                val snapshot = JSONObject().put("title", title).put("body", body).toString()
                busy = true
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { runCatching { documents.save("draft", expected, snapshot) } }
                    if (!space.current()) return@launch
                    saved.onSuccess { item ->
                        if (item != null) {
                            revision = item.revision
                            status = "Draft saved"
                        } else status = "The draft changed elsewhere. Reopen Notes to load the saved draft."
                    }.onFailure { status = "The draft could not be saved." }
                    busy = false
                }
            }) { Text("Save draft") }
            TextButton(onClick = onBack, enabled = !busy) { Text("Done") }
        }
    }
}
