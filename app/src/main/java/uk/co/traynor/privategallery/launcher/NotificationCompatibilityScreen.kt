package uk.co.traynor.privategallery.launcher

import android.Manifest
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
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.guardian.ProbeAccount
import uk.co.traynor.privategallery.core.guardian.ProbeResult
import uk.co.traynor.privategallery.core.guardian.SyntheticResult

@Composable
internal fun NotificationCompatibilityScreen(
    foldProbe: Boolean = false,
    onOpenLauncher: (() -> Unit)? = null,
    allowPermissionChanges: Boolean = true,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val status by GuardianNotificationListener.state.collectAsState()
    var access by remember { mutableStateOf(GuardianNotificationListener.hasAccess(context)) }
    var settingsUnavailable by remember { mutableStateOf(false) }
    val cloneSerial by produceState<Long?>(null, context, foldProbe) {
        if (foldProbe) value = withContext(Dispatchers.IO) {
            runCatching {
                LauncherAppDirectory(context).apps()
                    .filter { it.packageName == "com.whatsapp" && it.profile?.isClone == true }
                    .mapNotNull { it.profileSerial }.distinct().singleOrNull()
            }.getOrNull()
        }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        access = GuardianNotificationListener.hasAccess(context)
    }
    LaunchedEffect(status.connected) { access = GuardianNotificationListener.hasAccess(context) }
    var canPost by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED) }
    val posting = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = it }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = GuardianNotificationListener.hasAccess(context)
                canPost = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
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
            OutlinedButton(enabled = allowPermissionChanges, onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                val system = context.packageManager.resolveActivity(intent, PackageManager.MATCH_SYSTEM_ONLY)?.activityInfo
                settingsUnavailable = system == null || runCatching {
                    settings.launch(intent.setComponent(ComponentName(system.packageName, system.name)))
                }.isFailure
            }) { Text("Review Android notification access") }
            if (settingsUnavailable) Text("Open Android Settings to review Notification access.")
            if (!allowPermissionChanges) Text("Permission changes are available outside Sanctuary protection.")
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
                "The five-minute test continues while you switch apps or lock the device. Use harmless messages from both accounts. " +
                    "Try one at a time with Samsung Do Not Disturb off and on. Compare which profile counter changes. " +
                    if (foldProbe) "The test app checks for one Android-reported WhatsApp clone."
                    else "Use Sanctuary’s existing Friends enrollment to identify the Dual Messenger profile.",
            )
            if (foldProbe) {
                Text(if (cloneSerial != null) "Android reports one WhatsApp clone."
                    else "A unique WhatsApp clone is unavailable to this test app.")
            }
            Button(
                onClick = {
                    if (status.running) GuardianNotificationListener.stopProbe()
                    else GuardianNotificationListener.startProbe(context, cloneSerial)
                },
                enabled = access && status.connected,
            ) { Text(if (status.running) "Stop test" else "Start five-minute test") }
            if (status.running) Text("Listening for new WhatsApp test notifications")
            ProbeAccount.entries.forEach { account ->
                val result = status.results[account] ?: ProbeResult()
                Text(account.label, style = MaterialTheme.typography.titleMedium)
                Text("Notification events ${result.posts} · Text available ${result.withText} · Under DND ${result.underDnd}")
                val estimated = if (result.unestimatedEvents > 0 && result.estimatedMessages == 0) "Unavailable"
                    else result.estimatedMessages.toString()
                Text("Estimated unique messages $estimated · Updates ${result.updates} · Group summaries ${result.summaries}")
                if (result.unestimatedEvents > 0) {
                    Text("Events without a reliable message estimate · ${result.unestimatedEvents}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Quiet-channel ranking ${result.quietChannel} · Grouped ${result.grouped} · While locked ${result.whileLocked}")
            }
            OutlinedButton(onClick = GuardianNotificationListener::clearProbe) { Text("Clear probe results") }
            HorizontalDivider()
            Text("NovaOS test notification", style = MaterialTheme.typography.titleMedium)
            Text("These checks use a notification created by this app. They never dismiss WhatsApp or system alerts.")
            if (!canPost) OutlinedButton(enabled = allowPermissionChanges,
                onClick = { posting.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Allow NovaOS test notifications")
            }
            val canTest = canPost && access && status.connected &&
                status.synthetic !in setOf(SyntheticResult.WAITING, SyntheticResult.DISMISSING)
            Button(onClick = { GuardianNotificationListener.postSynthetic(context, dismiss = false) }, enabled = canTest) {
                Text("Test notification presentation")
            }
            OutlinedButton(onClick = { GuardianNotificationListener.postSynthetic(context, dismiss = true) }, enabled = canTest) {
                Text("Test dismissal of NovaOS notification")
            }
            Surface(
                Modifier.fillMaxWidth().testTag("guardian-presentation-banner")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nova Guardian test", style = MaterialTheme.typography.titleSmall)
                    Text(status.synthetic.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            status.syntheticDndMatch?.let { Text(if (it) "Notification matched the current interruption filter."
                else "Android ranking marks the test notification as suppressed by the interruption filter.") }
            Text("Check the Samsung shade and lock screen to confirm visible behaviour on your Fold.")
            onOpenLauncher?.let { open ->
                OutlinedButton(onClick = open) { Text("Open Guardian Home test launcher") }
            }
        }
    }
}
