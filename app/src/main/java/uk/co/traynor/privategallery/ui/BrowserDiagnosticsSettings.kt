package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.BuildConfig

/** Acceptance-only controls; state belongs to the app, independently of Browser attachment. */
@Composable
internal fun BrowserDiagnosticsSettings(
    staticContentHost: Boolean,
    onStaticContentHostChanged: (Boolean) -> Unit,
    layoutColours: Boolean,
    onLayoutColoursChanged: (Boolean) -> Unit,
) {
    if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
    BrowserDiagnosticsControls(staticContentHost, onStaticContentHostChanged, layoutColours, onLayoutColoursChanged)
}

@Composable
internal fun BrowserDiagnosticsControls(
    staticContentHost: Boolean,
    onStaticContentHostChanged: (Boolean) -> Unit,
    layoutColours: Boolean,
    onLayoutColoursChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Browser diagnostics", style = MaterialTheme.typography.titleMedium)
        Text("Acceptance build only. These controls reset when the app restarts.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Static content host")
                Text(if (staticContentHost) "STATIC host" else "REAL WebView", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = staticContentHost,
                onCheckedChange = onStaticContentHostChanged,
                modifier = Modifier.testTag("browser-debug-static-host"),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Layout colours", Modifier.weight(1f))
            Switch(
                checked = layoutColours,
                onCheckedChange = onLayoutColoursChanged,
                modifier = Modifier.testTag("browser-debug-layout-colours"),
            )
        }
    }
}
