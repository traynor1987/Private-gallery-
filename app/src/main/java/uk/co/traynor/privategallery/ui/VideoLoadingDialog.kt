package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Percentage is measured ciphertext progress, never an elapsed-time estimate. */
@Composable
internal fun VideoLoadingDialog(stage: String, percent: Int?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stage) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (percent != null) {
                    Text("${percent.coerceIn(0, 100)}%")
                    LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(if (percent >= 99) "Checking the encrypted file before playback." else "Your video stays private while it opens.")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Waiting for the video decoder. You can cancel at any time.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
