package uk.co.traynor.privategallery.core.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Presentation only: transfer, integrity policy and installer remain in the existing updater. */
@Composable
internal fun NovaUpdateDownloadInterface(state: NovaUpdateState, onCancel: () -> Unit, onRetry: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == UpdatePhase.DOWNLOADING) visible = true
        if (state.phase in setOf(UpdatePhase.UP_TO_DATE, UpdatePhase.CANCELLED)) visible = false
    }
    val transferring = state.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)
    if (!visible) {
        if (transferring) TextButton(onClick = { visible = true }) { Text("View download") }
        return
    }
    val animated by animateFloatAsState(state.progress ?: 0f, label = "Transferred bytes")
    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text("NovaOS update") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.release?.let { release ->
                    Text("${release.version.raw} · Build ${release.versionCode ?: "—"}", style = MaterialTheme.typography.titleMedium)
                }
                Text(state.message, Modifier.testTag("update-transfer-status"))
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    if (state.progress != null) {
                        Text("${((state.progress ?: 0f) * 100).toInt()}%", Modifier.testTag("update-percent"), style = MaterialTheme.typography.headlineMedium)
                        LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (state.total > 0) "${updateMegabytes(state.bytes)} / ${updateMegabytes(state.total)} MB" else "${updateMegabytes(state.bytes)} MB downloaded · total unavailable",
                        Modifier.testTag("update-byte-count"))
                    state.bytesPerSecond?.takeIf { it > 0 }?.let { Text("${updateMegabytes(it)} MB/s") }
                }
                if (state.phase == UpdatePhase.VERIFYING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Download complete · ${updateMegabytes(state.bytes)} MB")
                    Text("Checking SHA-256, NovaOS package, original signing certificate and build number.")
                }
                if (state.phase == UpdatePhase.READY) Text("Verified · ready for Android installation confirmation")
                if (state.phase == UpdatePhase.INSTALLATION_REQUESTED) Text("Confirm Update in Android. The installed version changes only after installation succeeds.")
                state.release?.notes?.takeIf { it.isNotBlank() }?.let {
                    HorizontalDivider()
                    Text("Release notes", style = MaterialTheme.typography.titleSmall)
                    Text(it)
                }
            }
        },
        confirmButton = {
            if (transferring) TextButton(onClick = onCancel) { Text("Cancel download") }
            else if (state.phase == UpdatePhase.FAILED) TextButton(onClick = onRetry) { Text("Retry download") }
            else if (state.phase == UpdatePhase.READY) TextButton(onClick = onRetry) { Text("Install update") }
        },
        dismissButton = { TextButton(onClick = { visible = false }) { Text(if (transferring) "Keep downloading" else "Close") } },
        properties = DialogProperties(usePlatformDefaultWidth = true),
    )
}
