package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.guardian.*
import uk.co.traynor.privategallery.launcher.GuardianTheme

@Composable
internal fun EvidenceGuardianMessageLink(
    controller: EvidenceController, incident: EvidenceCatalog.Incident, onClose: () -> Unit,
) {
    val access = controller.access
    if (access.owner != EvidenceCatalog.Owner.PRIMARY || !controller.current()) return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<GuardianInboxRow>>(emptyList()) }
    var selected by remember { mutableStateOf<GuardianInboxRow?>(null) }
    var failure by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                access.checkCurrent()
                GuardianCaptureStore(context).inboxRows(GuardianCapture.retention(context), guardianInboxClock(context))
                    .filter { it.assessment != null && it.section != GuardianInboxSection.COOLING }
            }
        }
        access.publishIfCurrent { rows = result.getOrDefault(emptyList()); failure = result.isFailure }
    }
    GuardianTheme {
        EvidenceWorkflowPage(onDismissRequest = onClose, title = { EvidenceDialogTitle("Add Guardian message") }, text = {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Select a classified incoming notification to copy into this encrypted incident. Messages on hold are excluded.")
                    if (failure) Text("Guardian Inbox could not be opened.")
                    else if (rows.isEmpty()) Text("No eligible captured messages.")
                }
                items(rows, key = { it.id }) { row ->
                    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.arrivedAt))
                    FilterChip(selected = selected?.id == row.id, onClick = { selected = row },
                        label = { Text("Incoming · $date · ${row.assessment?.label}") })
                }
            }
        }, confirmButton = {
            Button(enabled = selected != null, onClick = {
                val row = selected ?: return@Button
                val job = controller.mutate("Incoming Guardian notification linked") { catalog ->
                    check(access.owner == EvidenceCatalog.Owner.PRIMARY)
                    val current = catalog.incidents().single { it.id() == incident.id() }
                    val message = GuardianCaptureStore(context)
                        .eligibleEvidenceMessage(row.id, GuardianCapture.retention(context), guardianInboxClock(context))
                        ?: error("Guardian message became unavailable")
                    val event = EvidenceCatalog.Event("guardian-${row.id}", emptyList(), row.arrivedAt,
                        "USER", "Incoming Android notification", message)
                    if (current.events().any { it.id() == event.id() }) catalog
                    else catalog.upsertIncident(withEvents(current, current.events() + event),
                        EvidenceController.now(), "OWNER_LINK_GUARDIAN_NOTIFICATION")
                }
                scope.launch { job.join(); access.publishIfCurrent(onClose) }
            }) { Text("Copy selected message") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
    }
}
