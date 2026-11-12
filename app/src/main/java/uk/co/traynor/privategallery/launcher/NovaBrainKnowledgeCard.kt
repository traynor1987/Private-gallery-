package uk.co.traynor.privategallery.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.core.guardian.NovaBrainValidation
import uk.co.traynor.privategallery.core.guardian.NOVA_BRAIN_VALIDATION_VERSION
import uk.co.traynor.privategallery.core.guardian.PersonalImpactSummary
import uk.co.traynor.privategallery.core.guardian.PersonalImpactValidation
import uk.co.traynor.privategallery.core.guardian.WaitingOutcome

@Composable
internal fun NovaBrainKnowledgeCard(showPersonalImpact: Boolean = false) {
    val context = LocalContext.current.applicationContext
    val revision by GuardianCaptureStore.knowledgeChanges.collectAsState()
    var validation by remember(context) { mutableStateOf<NovaBrainValidation?>(null) }
    var impact by remember(showPersonalImpact) {
        mutableStateOf<Pair<PersonalImpactSummary, PersonalImpactValidation?>?>(null)
    }
    LaunchedEffect(context, revision, showPersonalImpact) {
        impact = if (showPersonalImpact) withContext(Dispatchers.IO) {
            runCatching { GuardianCaptureStore(context).personalImpactReport() }.getOrNull()
        } else null
        validation = withContext(Dispatchers.IO) {
            runCatching { GuardianCaptureStore(context).knowledgeValidation() }.getOrNull()
        }
    }
    val percent = validation?.knowledgePercent()
    val stage = if (percent == null) "Not yet validated" else "Validated"
    var details by remember { mutableStateOf(false) }
    GuardianCard("Nova Brain knowledge", badge = stage) {
        if (percent == null) {
            Text("A score appears after enough unseen conversations have been evaluated.")
        } else {
            val progress by animateFloatAsState(percent / 100f, label = "Knowledge level")
            Text("$percent%")
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = { details = !details }) {
            Text(if (details) "Hide learning details" else "View learning details")
        }
        if (details) {
            impact?.let { (summary, result) ->
                Text("Personal impact baseline · " + if (result == null) "Not yet validated"
                    else "${(100.0 * result.correct / result.tested).roundToInt()}% on ${result.tested} held-out conversations")
                if (summary.reports > 0) {
                    Text("Warning helpful · ${summary.useful} of ${summary.warningAnswers} yes/no answers")
                    Text("Owner corrections · ${summary.falsePositives} false positives, ${summary.falseNegatives} missed warnings")
                    val outcomes = WaitingOutcome.entries.zip(listOf("Better", "No difference", "Worse", "Not applicable"))
                    Text("Waiting outcomes · " + outcomes.joinToString { (key, label) -> "$label ${summary.waiting[key] ?: 0}" })
                    Text("These are owner reports. They do not change the communication score or protection settings.")
                }
            }
            Text("Knowledge is based on how well Nova Brain recognises patterns on unseen conversations, handles uncertainty and improves over its baseline.")
            Text("Training counts do not raise the score. The benchmark measures observable cues and cannot establish intent or make a diagnosis.")
            validation?.takeIf { percent != null }?.let { report ->
                Text("${report.conversations} conversations · ${report.upsetting + report.ordinary} examples · rules v$NOVA_BRAIN_VALIDATION_VERSION")
                Text("Validated ${Instant.ofEpochMilli(report.validatedAtMillis)}")
                Text("Accuracy ${(report.accuracy * 100).roundToInt()}% · ${report.falsePositives} false positives · ${report.falseNegatives} false negatives")
                report.categoryResults.forEach { (category, counts) ->
                    Text("${category.label}: ${counts.truePositive} matched, ${counts.falsePositive} overflagged, ${counts.falseNegative} missed")
                }
            }
        }
    }
}
