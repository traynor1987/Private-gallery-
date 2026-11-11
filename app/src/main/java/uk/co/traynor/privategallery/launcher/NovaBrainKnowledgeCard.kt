package uk.co.traynor.privategallery.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.core.guardian.NovaBrainValidation

@Composable
internal fun NovaBrainKnowledgeCard() {
    val context = LocalContext.current.applicationContext
    val revision by GuardianCaptureStore.knowledgeChanges.collectAsState()
    var validation by remember(context) { mutableStateOf<NovaBrainValidation?>(null) }
    LaunchedEffect(context, revision) {
        validation = withContext(Dispatchers.IO) {
            runCatching { GuardianCaptureStore(context).knowledgeValidation() }.getOrNull()
        }
    }
    val percent = validation?.knowledgePercent()
    val stage = when {
        percent == null -> "Unmeasured"
        percent < 25 -> "New Brain"
        percent < 50 -> "Learning"
        percent < 80 -> "Developing"
        else -> "Advanced"
    }
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
            Text("Knowledge is based on how well Nova Brain recognises patterns on unseen conversations, handles uncertainty and improves over its baseline.")
            Text("Training counts do not raise the score. A score of 100% describes benchmark performance only.")
        }
    }
}
