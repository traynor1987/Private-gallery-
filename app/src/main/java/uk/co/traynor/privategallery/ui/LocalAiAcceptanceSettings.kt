package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import uk.co.traynor.privategallery.core.editor.AiProviderRegistry
import uk.co.traynor.privategallery.core.editor.local.LocalAiDiagnostics
import uk.co.traynor.privategallery.core.editor.local.LocalBackendOverride
import uk.co.traynor.privategallery.core.editor.local.LocalBackendSettings

/** Memory-only Debug/Acceptance controls; these choices do not claim device support. */
@Composable
internal fun LocalAiAcceptanceSettings() {
    if (!LocalBackendSettings.enabled) return
    val selected by LocalBackendSettings.override.collectAsState()
    val diagnostics by LocalAiDiagnostics.summary.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { AiProviderRegistry.local?.refreshDiagnostics() }
    Text("On-device AI diagnostics", style = MaterialTheme.typography.titleMedium)
    Text("Acceptance backend · resets when the app restarts", style = MaterialTheme.typography.bodySmall)
    Column(Modifier.selectableGroup()) {
        LocalBackendOverride.entries.forEach { backend ->
            val label = when (backend) {
                LocalBackendOverride.AUTO -> "Auto"
                LocalBackendOverride.VULKAN -> "GPU (Vulkan)"
                LocalBackendOverride.CPU -> "CPU"
            }
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = backend == selected,
                    onClick = { LocalBackendSettings.setOverride(backend) },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = backend == selected, onClick = null)
                Text(label)
            }
        }
    }
    Text(
        "Compare the same lightweight model, input image, prompt and dimensions at 20 steps. Run CPU as the baseline, then GPU (Vulkan). Use the recorded actual backend and measured timings; an unavailable forced backend cannot provide a comparison. No benchmark has been established here.",
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = { AiProviderRegistry.local?.refreshDiagnostics() }) {
        Text("Refresh AI memory diagnostics")
    }
    TextButton(onClick = {
        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("Private Gallery local AI diagnostics", diagnostics))
    }) { Text("Copy diagnostics") }
    Text(diagnostics, style = MaterialTheme.typography.bodySmall)
}
