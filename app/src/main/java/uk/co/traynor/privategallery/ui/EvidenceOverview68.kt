package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.evidence.*

@Composable
internal fun EvidenceConnectCollections(controller: EvidenceController, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    val hidden = controller.access.owner == EvidenceCatalog.Owner.HIDDEN
    EvidenceWorkflowPage(onDismissRequest = onClose, title = { EvidenceDialogTitle("Connect Vault collection") },
        text = {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(if (hidden) "Choose a collection in this unlocked Hidden Vault. Processing pauses when Hidden locks."
                        else "Choose a Primary Vault collection. New screenshots are picked up while Evidence Locker is open.")
                    if (state.collections.isEmpty()) Text("Create a collection in this Vault to connect it here.")
                }
                items(state.collections, key = { it.id }) { collection ->
                    val connected = state.connections.any { it.id == collection.id }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(collection.name, modifier = Modifier.weight(1f))
                            TextButton(enabled = !state.busy && !state.syncingCollections, onClick = {
                                if (connected) controller.disconnectCollection(collection.id)
                                else controller.connectCollection(collection.id)
                            }) { Text(if (connected) "Disconnect" else "Connect") }
                        }
                    }
                }
                val unavailable = state.connections.filter { connection -> state.collections.none { it.id == connection.id } }
                items(unavailable, key = { it.id }) { connection ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${connection.name} · unavailable", Modifier.weight(1f))
                        TextButton(enabled = !state.busy && !state.syncingCollections,
                            onClick = { controller.disconnectCollection(connection.id) }) { Text("Disconnect") }
                    }
                }
                item { Text("Disconnecting keeps evidence already registered. Vault originals stay in place.") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        dismissButton = {},
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceConnectedSummary(
    state: EvidenceController.State,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
) {
    val count = state.chronology?.review?.size ?: 0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Connected collections", style = MaterialTheme.typography.titleMedium)
        if (state.connections.isEmpty()) Text("Connect an Evidence Collection to organise new screenshots automatically.")
        else state.connections.forEach { Text(it.name) }
        state.collectionProgress?.let { progress ->
            Text("${progress.processed} of ${progress.discovered} checked · ${progress.registered} added · ${progress.duplicates} duplicates",
                style = MaterialTheme.typography.bodySmall)
            if (progress.failed > 0 || progress.pendingExtraction > 0 || progress.unavailableCollections > 0) {
                Text("${progress.failed} failed · ${progress.pendingExtraction} awaiting OCR · ${progress.unavailableCollections} unavailable collections",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.syncingCollections) TextButton(onClick = onPause) { Text("Pause processing") }
            else if (state.connections.isNotEmpty()) TextButton(enabled = !state.busy, onClick = onRetry) { Text("Check for new evidence") }
            TextButton(onClick = onReview, modifier = Modifier.testTag("evidence-review-queue")) {
                Text(if (count == 0) "Review queue" else "Review queue · $count")
            }
        }
    }
}

@Composable
internal fun EvidenceMessageCard(message: EvidenceChronology68.Message, onOpen: (String) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            message.sender?.let { Text("Possible sender · $it", style = MaterialTheme.typography.titleSmall) }
            Text(message.text)
            Text(message.timestamp.displayLabel(), style = MaterialTheme.typography.bodySmall)
            Text("Visible message time ${message.visibleClock} · ${message.timestamp.confidence.label()}",
                style = MaterialTheme.typography.bodySmall)
            message.locations.forEachIndexed { index, location ->
                TextButton(onClick = { onOpen(location.evidenceId) }) {
                    Text("Screenshot ${index + 1} · lines ${location.startLine}–${location.endLine}")
                }
            }
        }
    }
}

private fun EvidenceChronology68.Timestamp.displayLabel(): String =
    instant?.let { evidenceLocalTime(it.toEpochMilli()) }
        ?: listOfNotNull(date?.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)),
            clock?.toString()).joinToString(" · ").ifBlank { "Date unknown" }

private fun EvidenceChronology68.Confidence.label(): String = when (this) {
    EvidenceChronology68.Confidence.CONFIRMED -> "Confirmed"
    EvidenceChronology68.Confidence.HIGH_CONFIDENCE -> "High confidence"
    EvidenceChronology68.Confidence.NEEDS_REVIEW -> "Needs review"
    EvidenceChronology68.Confidence.UNKNOWN -> "Unknown"
}

private fun EvidenceChronology68.ReviewReason.label(): String = when (this) {
    EvidenceChronology68.ReviewReason.AMBIGUOUS_DATE -> "Message date is unclear"
    EvidenceChronology68.ReviewReason.AMBIGUOUS_ZONE -> "Message time zone is unclear"
    EvidenceChronology68.ReviewReason.INFERRED_DATE -> "Check inferred message date"
    EvidenceChronology68.ReviewReason.CONFLICTING_TIMESTAMPS -> "Timestamp claims disagree"
    EvidenceChronology68.ReviewReason.UNKNOWN_PARTICIPANT -> "Sender needs review"
    EvidenceChronology68.ReviewReason.AMBIGUOUS_OCR -> "Extracted text needs review"
    EvidenceChronology68.ReviewReason.POSSIBLE_DUPLICATE -> "Possible duplicate"
    EvidenceChronology68.ReviewReason.SUGGESTED_INCIDENT -> "Related screenshots"
    EvidenceChronology68.ReviewReason.PROCESSING_LIMIT -> "Some screenshots still need review"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EvidenceReviewQueue68(controller: EvidenceController, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val state by controller.state.collectAsState()
    val analysis = state.chronology
    val messages = remember(analysis) {
        analysis?.messages.orEmpty().flatMap { message -> message.locations.map { it to message } }.toMap()
    }
    var correcting by remember { mutableStateOf<EvidenceChronology68.MessageLocation?>(null) }
    EvidenceWorkflowPage(onDismissRequest = onClose, title = { EvidenceDialogTitle("Review queue") },
        text = {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.chronologyUnavailable) item { Text("Local analysis could not be loaded. Reopen Evidence Locker to retry.") }
                else if (analysis == null) item { Text("Preparing local analysis…") }
                else if (analysis.review.isEmpty()) item { Text("Nothing needs review right now.") }
                items(analysis?.review.orEmpty(), key = EvidenceReview68::key) { review ->
                    val message = messages[review.location]
                    val original = review.evidenceIds.firstOrNull()
                    val instant = message?.timestamp?.instant
                    val dateReview = review.reason in setOf(EvidenceChronology68.ReviewReason.AMBIGUOUS_DATE,
                        EvidenceChronology68.ReviewReason.AMBIGUOUS_ZONE, EvidenceChronology68.ReviewReason.INFERRED_DATE)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                original?.let { EvidenceThumbnail(controller, it) }
                                Column(Modifier.weight(1f)) {
                                    Text(review.reason.label(), style = MaterialTheme.typography.titleMedium)
                                    message?.let { Text(it.text, maxLines = 4); Text(it.timestamp.displayLabel()) }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val location = review.location
                                if (dateReview && location != null && instant != null) {
                                    TextButton(enabled = !state.busy, onClick = {
                                        controller.confirmMessageTime(location, instant.toEpochMilli())
                                    }) { Text("Confirm") }
                                }
                                if (dateReview && location != null && message != null) {
                                    TextButton(enabled = !state.busy, onClick = { correcting = location }) { Text("Correct") }
                                }
                                if (original != null) TextButton(onClick = { onOpen(original) }) { Text("Open evidence") }
                                TextButton(enabled = !state.busy, onClick = { controller.skipReview(review) }) { Text("Skip") }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } }, dismissButton = {},
    )
    correcting?.let { location ->
        var corrected by remember(location) { mutableLongStateOf(messages[location]?.timestamp?.instant?.toEpochMilli() ?: 0L) }
        GalleryAlertDialog(
            onDismissRequest = { correcting = null },
            title = { EvidenceDialogTitle("Correct message date and time") },
            text = { EvidenceDateTimeField("Message date and time", corrected) { corrected = it } },
            confirmButton = { TextButton(enabled = corrected > 0 && !state.busy, onClick = {
                controller.confirmMessageTime(location, corrected)
                correcting = null
            }) { Text("Confirm correction") } },
            dismissButton = { TextButton(onClick = { correcting = null }) { Text("Cancel") } },
            properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn),
        )
    }
}
