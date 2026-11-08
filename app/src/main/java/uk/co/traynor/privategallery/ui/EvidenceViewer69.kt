package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.evidence.EvidenceTranscript69

internal class EvidenceViewer69State {
    var original by mutableStateOf(false)
    var advanced by mutableStateOf(false)
}

@Composable
internal fun rememberEvidenceViewer69(evidenceId: String?): EvidenceViewer69State =
    remember(evidenceId) { EvidenceViewer69State() }

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.evidenceViewer69(
    viewer: EvidenceViewer69State,
    controller: EvidenceController,
    record: EvidenceCatalog.Evidence,
    chronology: EvidenceChronology68.Result?,
    busy: Boolean,
    integrity: String?,
    rejected: List<String>,
    onOpenOriginal: () -> Unit,
    onEdit: () -> Unit,
    onAssign: () -> Unit,
    onRotate: () -> Unit,
    onCorrect: (EvidenceCatalog.Ocr, String) -> Unit,
) {
    val extraction = record.ocr().lastOrNull()
    val correction = record.corrections().lastOrNull { it.ocrId() == extraction?.id() }
    val version = correction?.id() ?: extraction?.id()
    val messages = chronology?.messages.orEmpty().mapNotNull { message ->
        message.locations.firstOrNull { it.evidenceId == record.id() && it.transcriptVersion == version }
            ?.let { it to message }
    }.sortedBy { (location, _) -> location.startLine }
    val screenshot = record.mime().startsWith("image/")
    item(key = "viewer-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.name(), style = MaterialTheme.typography.titleLarge)
            if (record.description().isNotBlank()) Text(record.description())
            if (record.notes().isNotBlank()) Text(record.notes(), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = viewer.original, onClick = { viewer.original = true },
                    label = { Text(if (screenshot) "Original screenshot" else "Original file") },
                )
                FilterChip(
                    selected = !viewer.original, onClick = { viewer.original = false },
                    label = { Text(if (screenshot) "Extracted conversation" else "Extracted text") },
                )
            }
            FlowRow {
                if (!viewer.original && extraction != null) TextButton(enabled = !busy, onClick = {
                    onCorrect(extraction, correction?.text() ?: extraction.text())
                }) { Text("Review / correct") }
                TextButton(enabled = !busy, onClick = onEdit) { Text("Edit evidence") }
                TextButton(enabled = !busy, onClick = onAssign) { Text("Add to incident") }
            }
        }
    }
    if (viewer.original) {
        item(key = "viewer-original") {
            Column {
                EvidenceOriginalContent(controller, record.id())
                TextButton(onClick = onOpenOriginal) { Text("View full screen") }
            }
        }
    } else if (extraction == null) {
        item(key = "viewer-awaiting-extraction") {
            Column {
                Text("This evidence has no extracted text yet.")
                TextButton(enabled = !busy, onClick = { controller.extract(record.id()) }) { Text("Extract text") }
            }
        }
    } else {
        item(key = "viewer-conversation-status") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (record.participants().isNotEmpty()) {
                    Text("Confirmed participants", style = MaterialTheme.typography.titleSmall)
                    Text(record.participants().joinToString(" · "))
                }
                Text(
                    if (correction == null) "Extracted from the original. Check uncertain text against the source."
                    else "Uses owner-corrected text. Original OCR remains available in Advanced details.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (messages.isEmpty() && screenshot) {
                    Text("Message boundaries are unclear. Check the original or review the extracted text.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (messages.isEmpty()) {
            item(key = "viewer-unstructured-preview") {
                val body = record.transcript()
                Text(body.take(3_000))
                if (body.length > 3_000) Text("Open Advanced details for the full extraction.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        items(messages, key = { (location, _) -> "viewer-message-${location.startLine}-${location.endLine}" }) { (location, message) ->
            EvidenceViewer69Message(message, location, busy) { at -> controller.confirmMessageTime(location, at) }
        }
        if (correction == null) {
            item(key = "viewer-suggested-edit") {
                val suggestion = remember(record.id(), extraction.id()) {
                    EvidenceTranscript69.suggest(record.id(), extraction.id(), extraction.text())
                }?.takeUnless { it.id in rejected }
                if (suggestion != null) {
                    var reviewing by remember(suggestion.id) { mutableStateOf(false) }
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Suggested OCR cleanup", style = MaterialTheme.typography.titleMedium)
                            Text("Local spacing changes. The original OCR stays untouched.",
                                style = MaterialTheme.typography.bodySmall)
                            if (!reviewing) TextButton(onClick = { reviewing = true }) { Text("Compare suggested text") }
                            else {
                                Text("Original OCR", style = MaterialTheme.typography.titleSmall)
                                Text(extraction.text().take(3_000))
                                Text("Suggested corrected text", style = MaterialTheme.typography.titleSmall)
                                Text(suggestion.text.take(3_000))
                                FlowRow {
                                    if (suggestion.text.length <= 3_000 && extraction.text().length <= 3_000) {
                                        TextButton(enabled = !busy, onClick = {
                                            controller.mutate("Owner-confirmed text saved separately") { current ->
                                                val latest = current.evidence().single { it.id() == record.id() }
                                                require(latest.ocr().lastOrNull()?.id() == extraction.id() &&
                                                    latest.corrections().none { it.ocrId() == extraction.id() })
                                                current.reviseOcr(record.id(), extraction.id(), suggestion.text, EvidenceController.now())
                                            }
                                        }) { Text("Approve correction") }
                                    }
                                    TextButton(enabled = !busy, onClick = { onCorrect(extraction, suggestion.text) }) {
                                        Text("Edit suggestion")
                                    }
                                    TextButton(enabled = !busy, onClick = {
                                        controller.mutate("OCR suggestion dismissed") {
                                            it.rejectOcrSuggestion(record.id(), extraction.id(), suggestion.id, EvidenceController.now())
                                        }
                                    }) { Text("Reject suggestion") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    item(key = "viewer-advanced-toggle") {
        TextButton(onClick = { viewer.advanced = !viewer.advanced }) {
            Text(if (viewer.advanced) "Hide advanced details" else "Advanced details")
        }
    }
    if (viewer.advanced) {
        item(key = "viewer-original-details") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Source and integrity", style = MaterialTheme.typography.titleMedium)
                Text("${record.mime()} · ${record.size()} bytes")
                Text("Imported ${evidenceLocalTime(record.importedAt())}")
                Text("Vault added ${evidenceLocalTime(record.metadata().dateAddedAt())}")
                Text("Source capture claim ${evidenceLocalTime(record.metadata().captureAt())}")
                Text("SHA-256 ${record.sha256()}", style = MaterialTheme.typography.bodySmall)
                Text("${record.metadata().sourceName()} · ${record.metadata().width()} × ${record.metadata().height()}",
                    style = MaterialTheme.typography.bodySmall)
                integrity?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                record.metadata().embedded().forEach { (key, value) ->
                    Text("$key: $value", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow {
                    TextButton(enabled = !busy, onClick = { controller.verify(record.id()) }) { Text("Verify integrity") }
                    TextButton(enabled = !busy, onClick = { controller.extract(record.id()) }) { Text("Extract text again") }
                    TextButton(enabled = !busy, onClick = onRotate) { Text("Rotate extraction") }
                }
            }
        }
        for (original in record.ocr().asReversed()) {
            item(key = "viewer-ocr-${original.id()}") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Original OCR", style = MaterialTheme.typography.titleMedium)
                    Text("${original.engine()} · ${original.pageCount()} pages" + if (original.partial()) " · partial" else "")
                    Text(if (original.confidence() < 0) "Engine score unavailable"
                        else "Engine score ${original.confidence()}/100 · extraction estimate",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = {
                        onCorrect(original, record.corrections().lastOrNull { it.ocrId() == original.id() }?.text() ?: original.text())
                    }) { Text("Correct this extraction") }
                }
            }
            evidenceTranscriptRows("ocr-${original.id()}", original.text())
            items(original.times().withIndex().toList(), key = { "viewer-hint-${original.id()}-${it.index}" }) { (_, hint) ->
                Text("Page ${hint.pageIndex() + 1}: ${hint.text()} · ${hint.provenance()}",
                    style = MaterialTheme.typography.bodySmall)
            }
            for (owner in record.corrections().filter { it.ocrId() == original.id() }.asReversed()) {
                item(key = "viewer-correction-${owner.id()}") {
                    Text("Owner-confirmed text · ${evidenceLocalTime(owner.createdAt())}",
                        style = MaterialTheme.typography.titleSmall)
                }
                evidenceTranscriptRows("correction-${owner.id()}", owner.text())
            }
        }
    }
}

private fun LazyListScope.evidenceTranscriptRows(key: String, text: String) {
    // Bounded text layout per row even for a legacy transcript at the catalogue quota.
    items(text.chunked(2_048).withIndex().toList(), key = { "viewer-transcript-$key-${it.index}" }) { (_, block) ->
        Text(block)
    }
}

@Composable
private fun EvidenceViewer69Message(
    message: EvidenceChronology68.Message,
    location: EvidenceChronology68.MessageLocation,
    busy: Boolean,
    onCorrectTime: (Long) -> Unit,
) {
    var correcting by remember(location) { mutableStateOf(false) }
    val confirmed = message.timestamp.confidence == EvidenceChronology68.Confidence.CONFIRMED
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val heading = when {
                message.kind == EvidenceChronology68.Kind.CALL -> "Call shown in screenshot"
                message.kind == EvidenceChronology68.Kind.UNRESOLVED -> "Message boundary uncertain"
                message.sender != null && message.senderConfidence == EvidenceChronology68.Confidence.CONFIRMED -> message.sender
                message.sender != null -> "Possible sender · ${message.sender}"
                message.direction == EvidenceChronology68.Direction.INCOMING -> "Possibly received"
                message.direction == EvidenceChronology68.Direction.OUTGOING -> "Possibly sent"
                else -> "Sender unknown"
            }
            Text(heading, style = MaterialTheme.typography.titleSmall)
            Text(message.text)
            if (message.visibleClock.isNotBlank()) {
                Text("Visible time · ${message.visibleClock}", style = MaterialTheme.typography.bodySmall)
            }
            val at = message.timestamp.instant?.toEpochMilli()
            val date = message.timestamp.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            val status = when {
                confirmed && at != null -> "${evidenceLocalTime(at)} · owner confirmed"
                date != null -> "$date · ${if (message.timestamp.confidence == EvidenceChronology68.Confidence.HIGH_CONFIDENCE) "high confidence" else "needs review"}"
                else -> "Date unknown"
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (message.sourceUncertain) Text("Check against the screenshot", style = MaterialTheme.typography.bodySmall)
            if (message.visibleClock.isNotBlank() || confirmed) {
                TextButton(enabled = !busy, onClick = { correcting = true }) { Text("Correct time") }
            }
        }
    }
    if (correcting) {
        var value by remember(location) { mutableLongStateOf(message.timestamp.instant?.toEpochMilli() ?: 0L) }
        GalleryAlertDialog(
            onDismissRequest = { correcting = false },
            title = { EvidenceDialogTitle("Correct message date and time") },
            text = { EvidenceDateTimeField("Message date and time", value) { value = it } },
            confirmButton = {
                TextButton(enabled = !busy && value > 0L, onClick = {
                    onCorrectTime(value)
                    correcting = false
                }) { Text("Confirm correction") }
            },
            dismissButton = { TextButton(onClick = { correcting = false }) { Text("Cancel") } },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        )
    }
}
