package uk.co.traynor.privategallery.launcher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.guardian.ProbeAccount
import uk.co.traynor.privategallery.core.guardian.ProbeResult

@Composable
internal fun NotificationCompatibilityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by GuardianNotificationListener.state.collectAsState()
    var access by remember { mutableStateOf(GuardianNotificationListener.hasAccess(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        access = GuardianNotificationListener.hasAccess(context)
    }
    LaunchedEffect(status.connected) { access = GuardianNotificationListener.hasAccess(context) }
    DisposableEffect(Unit) { onDispose { GuardianNotificationListener.clearProbe() } }
    BackHandler(onBack = onBack)

    LauncherPanel("Notification compatibility", onBack) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    !access -> "Permission Required"
                    !status.connected -> "Compatibility Issue · Android listener disconnected"
                    else -> "Limited Protection · diagnostics only"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Android notification access can expose notification content. During a test, NovaOS counts WhatsApp " +
                    "events and checks whether text is available. It keeps these counts in memory and never displays or saves message text.",
            )
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                val system = context.packageManager.resolveActivity(intent, PackageManager.MATCH_SYSTEM_ONLY)?.activityInfo
                settingsUnavailable = system == null || runCatching {
                    settings.launch(intent.setComponent(ComponentName(system.packageName, system.name)))
                }.isFailure
            }) { Text("Review Android notification access") }
            if (settingsUnavailable) Text("Open Android Settings to review Notification access.")
            val dndAccess = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
            Text("Do Not Disturb policy access · " + if (dndAccess) "Granted" else "Off")
            Text("Android interruption filter · " + when (status.filter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "Off"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority only"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "No interruptions"
                else -> "Unavailable"
            })
            HorizontalDivider()
            Text(
                "Use harmless test messages from both accounts while the device is unlocked. " +
                    "Try one at a time with Samsung Do Not Disturb off and on. Compare which profile counter changes. " +
                    "Use Sanctuary’s existing Friends enrollment to identify the Dual Messenger profile.",
            )
            Button(
                onClick = {
                    if (status.running) GuardianNotificationListener.stopProbe()
                    else GuardianNotificationListener.startProbe(context)
                },
                enabled = access && status.connected,
            ) { Text(if (status.running) "Stop test" else "Start five-minute test") }
            if (status.running) Text("Listening for new WhatsApp test notifications")
            ProbeAccount.entries.forEach { account ->
                val result = status.results[account] ?: ProbeResult()
                Text(account.label, style = MaterialTheme.typography.titleMedium)
                Text("Posts ${result.posts} · Text available ${result.withText} · Under DND ${result.underDnd}")
                Text("Quiet-channel ranking ${result.quietChannel} · Grouped ${result.grouped} · Updates ${result.updates}")
            }
            Text("System dismissal, NovaOS presentation and lock-screen behaviour still need device verification.")
        }
    }
}
