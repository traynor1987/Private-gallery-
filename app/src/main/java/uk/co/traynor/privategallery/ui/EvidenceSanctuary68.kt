package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryPatternLearning68

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceSanctuaryLearning68(
    controller: EvidenceController,
    records: List<EvidenceCatalog.Evidence>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val learning = remember(controller) { SanctuaryPatternLearning68(context, controller.access) }
    val scope = rememberCoroutineScope()
    DisposableEffect(learning) { onDispose { learning.close() } }
    var selections by remember { mutableStateOf<Map<String, Set<SanctuaryPatternLearning68.Pattern>>>(emptyMap()) }
    var consent by remember { mutableStateOf<SanctuaryPatternLearning68.Consent?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val approval = consent

    EvidenceWorkflowPage(onDismissRequest = onClose, title = { EvidenceDialogTitle("Sanctuary learning preview") },
        text = {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Mark patterns you personally confirmed in the selected evidence. A category needs at least two distinct originals.")
                    Text("Only category flags are saved in the encrypted profile. This does not turn on monitoring.")
                    if (error) Text("The profile could not be prepared or saved. Check the selection and try again.",
                        color = MaterialTheme.colorScheme.error)
                }
                if (approval == null) items(records, key = { it.id() }) { record ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(record.name(), style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (pattern in SanctuaryPatternLearning68.Pattern.entries) {
                                    val marked = selections[record.id()].orEmpty()
                                    FilterChip(selected = pattern in marked, enabled = !busy, label = { Text(pattern.label) },
                                        onClick = {
                                            val updated = if (pattern in marked) marked - pattern else marked + pattern
                                            selections = if (updated.isEmpty()) selections - record.id()
                                                else selections + (record.id() to updated)
                                        })
                                }
                            }
                        }
                    }
                } else item {
                    if (approval.patterns.isEmpty()) Text("No recurring categories. Edit the selection to continue.")
                    else approval.patterns.forEach { Text(it.label, style = MaterialTheme.typography.titleMedium) }
                    TextButton(enabled = !busy, onClick = { consent = null }) { Text("Edit selection") }
                }
            }
        },
        confirmButton = {
            Button(enabled = !busy && (approval?.patterns?.isNotEmpty() ?: selections.isNotEmpty()), onClick = {
                if (controller.current()) {
                    busy = true
                    error = false
                    val reviewed = selections
                    scope.launch {
                        try {
                            if (approval == null) {
                                val result = withContext(Dispatchers.IO) { learning.prepare(reviewed) }
                                controller.access.publishIfCurrent { consent = result }
                            } else {
                                withContext(Dispatchers.IO) { learning.save(approval) }
                                controller.access.publishIfCurrent(onClose)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            controller.access.publishIfCurrent { error = true; consent = null }
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text(if (approval == null) "Preview categories" else "Save encrypted profile") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
