package uk.co.traynor.privategallery.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.traynor.privategallery.core.editor.local.LocalGenerationProgress

@Composable
internal fun LocalGenerationModal(progress: LocalGenerationProgress?, cancelling: Boolean, startedAtMs: Long?, onCancel: () -> Unit) {
    val started = startedAtMs ?: SystemClock.elapsedRealtime()
    var elapsed by remember(started) { mutableLongStateOf(((SystemClock.elapsedRealtime() - started) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(started) { while (true) { elapsed = ((SystemClock.elapsedRealtime() - started) / 1000).coerceAtLeast(0); delay(1000) } }
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.widthIn(max = 420.dp).testTag("local-generation-modal"),
        title = { Text("Creating your edit") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (cancelling) "Stopping safely…" else progress?.stage?.label ?: "Preparing local AI…")
            val percent = if (cancelling) null else progress?.percent
            if (percent == null) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("local-generation-indeterminate"))
            else {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().testTag("local-generation-measured"))
                Text("$percent% · Step ${progress?.completedSteps} of ${progress?.totalSteps}", modifier = Modifier.testTag("local-generation-steps"))
            }
            Text("${elapsed}s elapsed · Processed privately on this device", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = onCancel, enabled = !cancelling) { Text("Cancel") } },
    )
}
