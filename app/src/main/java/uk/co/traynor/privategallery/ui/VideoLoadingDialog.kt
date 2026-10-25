package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Percentage is measured ciphertext progress, never an elapsed-time estimate. */
@Composable
internal fun VideoLoadingOverlay(stage: String, percent: Int?, onCancel: () -> Unit) {
    // Progress is part of the authenticated player surface, never a new focus-owning window.
    Box(Modifier.fillMaxSize().testTag("video-loading-overlay"), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(20.dp).widthIn(max = 420.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stage, style = MaterialTheme.typography.titleLarge)
                if (percent != null) {
                    Text("${percent.coerceIn(0, 100)}%")
                    LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(if (percent >= 99) "Checking the encrypted file before playback." else "Your video stays private while it opens.")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Waiting for the video decoder. You can cancel at any time.")
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
