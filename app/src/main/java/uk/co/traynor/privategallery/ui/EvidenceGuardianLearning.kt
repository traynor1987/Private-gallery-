package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.guardian.GuardianAssessment
import uk.co.traynor.privategallery.core.guardian.GuardianCapture
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceGuardianLearning(
    controller: EvidenceController, selected: List<EvidenceCatalog.Evidence>, onClose: () -> Unit,
) {
    val access = controller.access
    if (access.owner != EvidenceCatalog.Owner.PRIMARY || !controller.current()) return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val candidates = remember(selected) { selected.filter { it.ocr().isNotEmpty() && it.transcript().isNotBlank() } }
    var record by remember { mutableStateOf<EvidenceCatalog.Evidence?>(null) }
    var excerpt by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<GuardianAssessment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    EvidenceWorkflowPage(onDismissRequest = onClose, title = { EvidenceDialogTitle("Teach Guardian") }, text = {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Choose one selected Primary Evidence Locker record. Review an excerpt and explicitly label it.")
                Text("Nova Brain stores encrypted phrase features on this device.")
            }
            if (candidates.isEmpty()) item { Text("Run or review OCR on a selected record first.") }
            if (record == null) items(candidates, key = { it.id() }) { candidate ->
                OutlinedButton(modifier = Modifier.testTag("guardian-training-source-${candidate.id()}"), enabled = !busy, onClick = {
                    record = candidate
                    excerpt = candidate.transcript().trim().take(2048)
                }) { Text(candidate.name()) }
            }
            record?.let { current ->
                item {
                    Text(current.name(), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(excerpt, { excerpt = it.take(2048) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), maxLines = 8,
                        label = { Text("Excerpt from reviewed OCR") },
                        supportingText = { Text("Trim to the passage you want Nova Brain to learn.") })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuardianAssessment.entries.forEach { assessment ->
                            FilterChip(selected = label == assessment, enabled = !busy,
                                onClick = { label = assessment }, label = { Text(assessment.label) })
                        }
                    }
                    TextButton(enabled = !busy, onClick = { record = null; excerpt = ""; label = null }) {
                        Text("Choose another record")
                    }
                    if (failed) Text("Could not save this example. Use at least four words from the current OCR transcript.",
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }, confirmButton = {
        Button(enabled = !busy && record != null && label != null && excerpt.isNotBlank(), onClick = {
            val source = record ?: return@Button
            val assessment = label ?: return@Button
            val approved = excerpt.trim()
            busy = true
            failed = false
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        check(access.owner == EvidenceCatalog.Owner.PRIMARY)
                        val current = access.store.read().evidence().single { it.id() == source.id() }
                        check(current.ocr().isNotEmpty() && approved in current.transcript())
                        var committed = false
                        access.withRetirementBoundary {
                            access.checkCurrent()
                            committed = GuardianCaptureStore(context).teach(approved, assessment)
                        }
                        committed
                    }.getOrDefault(false)
                }
                val published = access.publishIfCurrent {
                    busy = false
                    if (saved) onClose() else failed = true
                }
                if (saved && published) GuardianCapture.refresh(context)
            }
        }) { Text("Save local example") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
