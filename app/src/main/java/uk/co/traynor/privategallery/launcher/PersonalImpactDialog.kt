package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import uk.co.traynor.privategallery.core.guardian.*

@Composable
internal fun PersonalImpactDialog(
    existing: PersonalImpact?,
    pastImpact: MessageImpact?,
    busy: Boolean,
    onSave: (PersonalImpact) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    evidenceOnly: Boolean = false,
) {
    var feedback by remember(existing) { mutableStateOf(existing ?: PersonalImpact()) }
    GuardianAlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Personal impact") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Your answers stay on this device. Each question is optional.")
                pastImpact?.let {
                    val label = when (it) {
                        MessageImpact.UPSETTING -> "upsetting"
                        MessageImpact.NEUTRAL -> "neutral"
                        MessageImpact.FINE -> "completely fine"
                    }
                    Text("Most of your previous included reports for this conversation were $label.")
                }
                ImpactQuestion("How did that message actually feel?", MessageImpact.entries.zip(
                    listOf("Upsetting", "Neutral", "Completely fine")), feedback.impact, busy) {
                    feedback = feedback.copy(impact = it)
                }
                ImpactQuestion("Was Guardian's warning helpful?", WarningHelp.entries.zip(
                    listOf("Yes", "No", "Unsure")), feedback.warning, busy) {
                    feedback = feedback.copy(warning = it)
                }
                ImpactQuestion("Did waiting before responding help?", WaitingOutcome.entries.zip(
                    listOf("I felt better", "No difference", "I felt worse", "Not applicable")), feedback.waiting, busy) {
                    feedback = feedback.copy(waiting = it)
                }
                ImpactQuestion("Correct the warning", WarningCorrection.entries.zip(
                    listOf("False positive", "Missed warning")), feedback.correction, busy) {
                    feedback = feedback.copy(correction = it)
                }
                if (evidenceOnly) Text("This evidence report is included in your local totals. Conversation predictions use linked Guardian feedback.")
                else Row {
                    Checkbox(feedback.excluded, { feedback = feedback.copy(excluded = it) }, enabled = !busy)
                    Text("Exclude this entry from learning")
                }
                if (existing != null) TextButton(enabled = !busy, onClick = onDelete) { Text("Delete feedback") }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && feedback.hasFeedback(), onClick = { onSave(feedback) }) { Text("Save feedback") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Close") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

@Composable
private fun <T> ImpactQuestion(
    question: String,
    options: List<Pair<T, String>>,
    selected: T?,
    busy: Boolean,
    onSelect: (T?) -> Unit,
) {
    Text(question)
    options.forEach { (value, label) ->
        FilterChip(selected == value, { onSelect(value) }, { Text(label) }, enabled = !busy)
    }
    TextButton(enabled = selected != null && !busy, onClick = { onSelect(null) }) { Text("Skip") }
}
