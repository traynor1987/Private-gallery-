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
import uk.co.traynor.privategallery.launcher.GuardianTheme

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
    var feedback by remember { mutableStateOf<String?>(null) }

    fun updateLearning(assessment: GuardianAssessment?) {
        val source = record ?: return
        val approved = excerpt.trim()
        if (busy || approved.isBlank()) return
        busy = true
        feedback = null
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    var committed = false
                    access.withRetirementBoundary {
                        access.checkCurrent()
                        val current = access.store.read().evidence().single { it.id() == source.id() }
                        check(current.ocr().isNotEmpty() && approved in current.transcript())
                        val store = GuardianCaptureStore(context)
                        committed = if (assessment == null) store.forgetExample(approved) else store.teach(approved, assessment)
                    }
                    committed
                }.getOrDefault(false)
            }
            val published = access.publishIfCurrent {
                busy = false
                feedback = when {
                    !saved && assessment == null -> "No saved example matches this OCR passage."
                    !saved -> "Could not save this example. Use at least four words from the current OCR transcript."
                    assessment == null -> "Example removed from future local estimates."
                    else -> "Local example saved as ${assessment.label}. Similar passages can affect future estimates."
                }
            }
            if (saved && published) GuardianCapture.refresh(context)
        }
    }

    GuardianTheme {
        EvidenceWorkflowPage(onDismissRequest = onClose, title = { EvidenceDialogTitle("Teach Guardian") }, text = {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Choose a screenshot or incident record. Keep the conversation lines in order, review the OCR excerpt and choose its label.")
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
                        TextButton(enabled = !busy && excerpt.isNotBlank(), onClick = { updateLearning(null) }) {
                            Text("Remove matching local example")
                        }
                        feedback?.let { Text(it) }
                    }
                }
            }
        }, confirmButton = {
            Button(enabled = !busy && record != null && label != null && excerpt.isNotBlank(), onClick = {
                label?.let(::updateLearning)
            }) { Text("Save local example") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
    }
}
