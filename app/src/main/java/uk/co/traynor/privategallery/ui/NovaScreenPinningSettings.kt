package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.Decision
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.State

@Composable
internal fun NovaScreenPinningSettings(
    current: () -> Boolean,
    activity: ComponentActivity? = LocalContext.current.playerActivity() as? ComponentActivity,
) {
    // A new captured owner gets a new controller. An old callback never inherits a new unlock.
    val controller = remember(activity, current) { activity?.let { NovaScreenPinningController(it, current) } }
    DisposableEffect(controller) { onDispose { controller?.close() } }
    if (controller == null) Text("Android pinning controls are unavailable.")
    else NovaScreenPinningSettingsContent(controller)
}

@Composable
internal fun NovaScreenPinningSettingsContent(controller: NovaScreenPinningController) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pin NovaOS", style = MaterialTheme.typography.titleMedium)
        Text(when (controller.observation.state) {
            State.NONE -> "NovaOS is not pinned."
            State.PINNED -> "Android reports screen pinning is active."
            State.LOCKED -> "Android reports managed lock task mode."
            State.UNKNOWN -> "Android pinning state is unavailable."
        }, Modifier.testTag("nova-pinning-status"), style = MaterialTheme.typography.bodyMedium)
        Text("Enable App pinning in Android Settings if needed. Android controls confirmation and unpinning. Use the Android unpin gesture shown in its confirmation.",
            style = MaterialTheme.typography.bodySmall)
        when (controller.decision) {
            Decision.MANAGED, Decision.LOCKED -> Text("Pin NovaOS is unavailable under the current device policy.")
            Decision.POLICY_UNKNOWN, Decision.STATE_UNKNOWN -> Text("Pin NovaOS is unavailable while Android's security state cannot be checked.")
            Decision.DEVICE_LOCKED -> Text("Unlock the device before requesting pinning.")
            Decision.REVOKED -> Text("Unlock NovaOS and reopen Settings to continue.")
            else -> Unit
        }
        Button(onClick = { controller.requestPinning() }, enabled = controller.decision == Decision.REQUEST,
            modifier = Modifier.fillMaxWidth().testTag("nova-pinning-request")) { Text("Request Android pinning") }
        OutlinedButton(onClick = { controller.openAndroidSettings() }, enabled = controller.canOpenSettings,
            modifier = Modifier.fillMaxWidth().testTag("nova-pinning-settings")) { Text("Open Android Settings") }
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
