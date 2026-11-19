package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.guardian.*
import java.text.DateFormat
import java.util.Date

/** Hosted only inside the authenticated, FLAG_SECURE Inbox; no message bodies or network calls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuardianRetentionScreen89(
    rows: List<GuardianInboxRow>, days: Int, busy: Boolean,
    report: GuardianRecoveryReport89?, notice: String?, showSenders: Boolean,
    onRetention: (Int) -> Unit, onOpen: (GuardianInboxRow) -> Unit,
    onDelete: (Set<String>, Boolean) -> Unit, onArchive: (Set<String>) -> Unit,
    onInspect: () -> Unit, onRestore: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var trainingOnly by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteTraining by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    val visible = rows.filter { !trainingOnly || it.savedForTraining }
    LazyColumn(Modifier.fillMaxSize().testTag("guardian-retention89"), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)) {
        item { GuardianCard("Messages, retention & recovery") {
            Text("${rows.size} encrypted captures · ${rows.count { it.savedForTraining }} saved for training")
            Text("${GuardianRetention89.label(days)}. Timed messages move to History; no automatic permanent deletion.")
            Text("History keeps the encrypted original. Hiding notification previews does not delete it. Only a confirmed permanent deletion erases a capture.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuardianRetention89.choices.forEach { value ->
                    OutlinedButton(enabled = !busy && days != value, onClick = { onRetention(value) }) {
                        Text(if (value == 0) "Keep Forever" else "$value days")
                    }
                }
            }
            Text("Capture retention is separate from Android alert timeout and Personal Impact feedback retention.")
            Text("Storage is bounded. If full, new captures fail visibly; existing messages are never evicted. Saved training originals survive Inbox clearing. Deleting learning labels does not delete originals.")
            TextButton(enabled = !busy, onClick = onInspect) { Text("Check encrypted storage & recovery") }
            report?.let {
                Text("Stored: ${it.stored} · Protected copies: ${it.copies} · Missing captures available to restore: ${it.recoverable} · Unreadable copies: ${it.unreadable}")
                Text("Only authenticated encrypted data can be recovered. Feature-only learning cannot reconstruct a deleted message. Ordinary Android backup excludes this private storage.")
                if (it.recoverable > 0) TextButton(enabled = !busy, onClick = { restoring = true }) { Text("Review recovery") }
            }
            notice?.let { Text(it) }
        } }
        item { GuardianCard("Manage retained messages") {
            Row { Checkbox(trainingOnly, { trainingOnly = it }); Text("Saved training originals only") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(enabled = !busy, onClick = { selected = visible.mapTo(linkedSetOf()) { it.id } }) { Text("Select all shown") }
                TextButton(onClick = { selected = emptySet() }) { Text("Clear selection") }
                TextButton(enabled = !busy && selected.isNotEmpty(), onClick = { onArchive(selected); selected = emptySet() }) { Text("Archive selected") }
                TextButton(enabled = !busy && selected.isNotEmpty(), onClick = { deleteTraining = false; deleting = true }) { Text("Delete selected…") }
            }
            Text("${selected.size} selected. Archive moves copies to History. Active four-hour holds remain protected.")
        } }
        items(visible, key = { it.id }) { row -> GuardianCard(if (row.savedForTraining) "Saved training original" else "Encrypted capture") {
            Row {
                Checkbox(row.id in selected, { checked -> selected = if (checked) selected + row.id else selected - row.id })
                Column(Modifier.weight(1f)) {
                    if (showSenders) row.senderName?.let { Text(it) }
                    Text(DateFormat.getDateTimeInstance().format(Date(row.arrivedAt)))
                    Text("${row.section.name.lowercase()} · ${row.ownerLabel?.name?.lowercase() ?: "No owner label"}")
                    TextButton(enabled = !busy, onClick = { onOpen(row) }) { Text("Review / Teach Guardian") }
                }
            }
        } }
    }
    if (deleting) GuardianAlertDialog(onDismissRequest = { deleting = false }, title = { Text("Permanently delete selected captures?") },
        text = { Column {
            Text("Ordinary captures and their on-device recovery copies will be erased. Saved training originals are kept in History unless you explicitly include them. Active holds are skipped. Separate Evidence Locker items and external backups are unchanged.")
            Row { Checkbox(deleteTraining, { deleteTraining = it }); Text("Also permanently delete saved training originals and their Inbox learning labels") }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = { deleting = false; onDelete(selected, deleteTraining); selected = emptySet() }) { Text("Permanently delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    if (restoring) GuardianAlertDialog(onDismissRequest = { restoring = false }, title = { Text("Restore surviving encrypted captures?") },
        text = { Text("Restore only missing, authenticated originals from protected on-device copies into History. Known deletion tombstones are respected. Old copies may predate an earlier deletion whose tombstone no longer exists. Nothing is uploaded or invented.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { restoring = false; onRestore() }) { Text("Restore to History") } },
        dismissButton = { TextButton(onClick = { restoring = false }) { Text("Cancel") } })
}
