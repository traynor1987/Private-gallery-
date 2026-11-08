package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun evidenceLocalDateRange(from: LocalDate?, through: LocalDate?, zone: ZoneId): Pair<Long, Long> {
    require(from == null || through == null || !through.isBefore(from))
    return (from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L) to
        (through?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1) ?: 0L)
}

internal fun evidenceLocalTime(value: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    if (value <= 0) "Unknown / uncertain"
    else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(zone).format(Instant.ofEpochMilli(value))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EvidenceDateField(label: String, date: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { choosing = true }) {
            Text(date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: label)
        }
        if (date != null) TextButton(onClick = { onSelect(null) }) { Text("Clear $label") }
    }
    if (choosing) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayMode = DisplayMode.Input,
        )
        GalleryAlertDialog(
            onDismissRequest = { choosing = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { EvidenceDialogTitle(label) },
            text = { DatePicker(picker, Modifier.fillMaxWidth().testTag("evidence-local-date"), title = null, headline = null) },
            confirmButton = {
                TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                    val day = checkNotNull(picker.selectedDateMillis)
                    onSelect(Instant.ofEpochMilli(day).atZone(ZoneOffset.UTC).toLocalDate())
                    choosing = false
                }) { Text("Choose date") }
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun EvidenceDateTimeField(label: String, epochMillis: Long, onChange: (Long) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    var invalid by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { invalid = false; choosing = true }) {
                Text(if (epochMillis > 0) evidenceLocalTime(epochMillis, zone) else "Choose date and time")
            }
            if (epochMillis > 0) TextButton(onClick = { onChange(0L) }) { Text("Clear") }
        }
        if (invalid) Text("That local time is ambiguous or unavailable. Choose a different time.",
            color = MaterialTheme.colorScheme.error)
    }
    if (choosing) {
        val initial = remember(epochMillis) {
            if (epochMillis > 0) Instant.ofEpochMilli(epochMillis).atZone(zone) else java.time.ZonedDateTime.now(zone)
        }
        MediaDisplayDateTimeDialog(
            initial = initial,
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            onDismiss = { choosing = false },
        ) { day, hour, minute ->
            val time = day.atTime(hour, minute)
            val offsets = zone.rules.getValidOffsets(time)
            choosing = false
            invalid = offsets.size != 1
            offsets.singleOrNull()?.let { onChange(time.toInstant(it).toEpochMilli()) }
        }
    }
}
