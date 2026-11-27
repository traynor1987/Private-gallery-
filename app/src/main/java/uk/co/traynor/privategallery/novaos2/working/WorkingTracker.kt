package uk.co.traynor.privategallery.novaos2.working

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.HexFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.launcher.LauncherWidget
import uk.co.traynor.privategallery.novaos2.NovaSystemHost
import uk.co.traynor.privategallery.novaos2.NovaSystemSection

private const val TRACKER_NAMESPACE = "site.chatgpt.traynor1987.dominosshifttracker"
internal const val SHIFT_TRACKER_PACKAGE = "$TRACKER_NAMESPACE.stable"
private const val MEDIUM = "$SHIFT_TRACKER_PACKAGE/$TRACKER_NAMESPACE.MediumShiftWidget"
private const val LARGE = "$SHIFT_TRACKER_PACKAGE/$TRACKER_NAMESPACE.LargeShiftWidget"
private val trackerCertificate = HexFormat.of().parseHex(
    "fdfd0db43b2d1ef9a7516aad5c78f62a16c1f6ae048eccb8c7024f3ceda5d4e1"
)

private fun PackageManager.trustsShiftTracker() = runCatching {
    hasSigningCertificate(SHIFT_TRACKER_PACKAGE, trackerCertificate, PackageManager.CERT_INPUT_SHA256)
}.getOrDefault(false)

internal fun trackerSummaryWidget(widgets: List<LauncherWidget>, wide: Boolean): LauncherWidget? =
    widgets.filter { it.provider == MEDIUM || it.provider == LARGE }
        .minWithOrNull(compareBy<LauncherWidget> { it.wide != wide }.thenBy { it.provider != MEDIUM }.thenBy { it.id })

/** Display the owner's bound widget. Shift Tracker owns its remote views, actions and data. */
@Composable
internal fun WorkingTracker(host: NovaSystemHost) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycle == Lifecycle.State.RESUMED && host.foregroundAllowed()
    val installed = host.apps.any { it.profile == null && it.packageName == SHIFT_TRACKER_PACKAGE }
    var trusted by remember(installed, foreground) { mutableStateOf(false) }
    var open by remember(foreground) { mutableStateOf(false) }
    val widget = trackerSummaryWidget(host.state.widgets, host.wide)
    LaunchedEffect(installed, foreground, host.apps) {
        trusted = installed && foreground && withContext(Dispatchers.IO) { context.packageManager.trustsShiftTracker() }
    }
    NovaSystemSection("Shift Tracker summary") {
        when {
            !installed -> Text("The stable Shift Tracker app is unavailable in this profile.")
            !trusted -> Text("A verified Shift Tracker installation is required.")
            widget == null -> {
                Text("Add Shift Tracker’s medium or large summary widget to Nova Home to use it here.")
                OutlinedButton(onClick = { if (host.foregroundAllowed()) host.actions.addWidget(host.wide) }) {
                    Text("Choose a Home widget")
                }
            }
            else -> {
                Text("View the delivery count and estimated pay supplied by your Shift Tracker widget.")
                OutlinedButton(onClick = {
                    if (host.foregroundAllowed() && context.packageManager.trustsShiftTracker()) open = true
                }) { Text("Show shift summary") }
            }
        }
    }
    if (open && foreground && trusted && widget != null) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp - 160).coerceAtLeast(120)
        val height = (if (widget.provider == LARGE) 265 else 160).coerceAtMost(maxHeight)
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Shift Tracker") },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            text = { host.actions.renderWidget(widget, Modifier.fillMaxWidth().height(height.dp)) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
    }
}
