package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68

internal sealed interface EvidenceTimelineItem {
    val key: String
    val date: LocalDate?
    val clock: LocalTime?

    data class Screenshot(
        val record: EvidenceCatalog.Evidence,
        val capture: EvidenceChronology68.Timestamp?,
        val confirmed: Long?,
        val zone: ZoneId,
    ) : EvidenceTimelineItem {
        override val key = "capture-${record.id()}"
        val epoch = confirmed ?: capture?.instant?.toEpochMilli()
            ?: record.metadata().captureAt().takeIf { capture == null && it > 0 }
        private val local = epoch?.let { Instant.ofEpochMilli(it).atZone(zone) }
        override val date = local?.toLocalDate() ?: capture?.date
        override val clock = local?.toLocalTime() ?: capture?.clock
    }

    data class Message(val message: EvidenceChronology68.Message, val zone: ZoneId) : EvidenceTimelineItem {
        private val source = message.locations.first()
        override val key = "message-${source.evidenceId}-${source.transcriptVersion}-${source.startLine}-${source.endLine}"
        private val local = message.timestamp.instant?.atZone(zone)
        override val date = local?.toLocalDate() ?: message.timestamp.date
        override val clock = local?.toLocalTime() ?: message.timestamp.clock
    }
}

@Composable
internal fun EvidenceScreenshotEvent(
    item: EvidenceTimelineItem.Screenshot,
    busy: Boolean,
    onOpen: () -> Unit,
    onCorrect: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.record.name(), style = MaterialTheme.typography.titleMedium)
            Text(when {
                item.confirmed != null -> "Screenshot capture · ${evidenceLocalTime(item.confirmed)} · Owner confirmed"
                item.epoch != null -> "Screenshot capture · ${evidenceLocalTime(item.epoch)}"
                item.date != null -> "Screenshot capture · ${item.date} · Time zone needs review"
                else -> "Capture date unknown"
            })
            Text("Registered ${evidenceLocalTime(item.record.importedAt())}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Open screenshot") }
                TextButton(enabled = !busy, onClick = onCorrect) { Text("Correct capture date") }
            }
        }
    }
}
