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
import androidx.compose.material3.TextButton
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
import uk.co.traynor.privategallery.core.guardian.GuardianCallSource
import uk.co.traynor.privategallery.core.guardian.GuardianCapture
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
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
            Text("Call notification observations", style = MaterialTheme.typography.titleMedium)
            Text("Only Android call and missed-call notification events visible to the probe are counted.")
            GuardianCallSource.entries.forEach { source ->
                val observed = status.calls[source]
                Text(if (observed == null) "${source.label} · Not observed"
                    else "${source.label} · Events ${observed.events} · Missed ${observed.missed} · Under DND ${observed.underDnd}")
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
            Button(onClick = { GuardianNotificationListener.postSynthetic(context) }, enabled = canTest) {
                Text("Test notification presentation")
            }
            OutlinedButton(onClick = { GuardianNotificationListener.dismissPresentedTest(context) },
                enabled = status.synthetic !in setOf(SyntheticResult.WAITING, SyntheticResult.DISMISSING)) {
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
            val dismissal = status.dismissal
            if (dismissal.posted) Text("Test notification posted")
            if (dismissal.matched) Text("Test notification identity matched")
            if (dismissal.requested) Text("Cancellation requested · ${dismissal.mechanism}")
            dismissal.stillActive?.let { Text(if (it) "Exact notification still active" else "Exact notification removed") }
            dismissal.failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            status.syntheticDndMatch?.let { Text(if (it) "Notification matched the current interruption filter."
                else "Android ranking marks the test notification as suppressed by the interruption filter.") }
            Text("Check the Samsung shade and lock screen to confirm visible behaviour on your Fold.")
            if (allowPermissionChanges) {
                var foldAccepted by remember { mutableStateOf(GuardianNotificationListener.foldDismissalAccepted(context)) }
                var externalError by remember { mutableStateOf<String?>(null) }
                HorizontalDivider()
                Text("External notification dismissal", style = MaterialTheme.typography.titleMedium)
                Text("Use the harmless ADB fixture on your Fold. This test only targets that exact shell notification.")
                OutlinedButton(enabled = access && status.connected && status.synthetic != SyntheticResult.DISMISSING,
                    onClick = {
                        externalError = if (GuardianNotificationListener.dismissExternalTestFixture(context,
                                GuardianNotificationListener.FOLD_FIXTURE_TAG)) null
                            else "No matching dismissible test fixture was found."
                    }) { Text("Dismiss harmless external fixture") }
                if (status.foldFixtureVerified && !foldAccepted) {
                    OutlinedButton(onClick = {
                        foldAccepted = GuardianNotificationListener.confirmFoldDismissal(context)
                    }) { Text("I saw the fixture disappear from Samsung’s panel") }
                }
                var mainAccepted by remember { mutableStateOf(GuardianNotificationListener.mainDismissalAccepted(context)) }
                var automatic by remember { mutableStateOf(GuardianNotificationListener.automaticDismissalEnabled(context)) }
                if (foldAccepted && !mainAccepted) {
                    Text("Arm the test, then send the exact phrase below as a single message to Main WhatsApp. The test expires after 60 seconds.")
                    status.mainTestMessage?.let { phrase ->
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(phrase) }
                    }
                    OutlinedButton(enabled = status.connected, onClick = {
                        externalError = if (GuardianNotificationListener.armMainDismissalTest(context)) null
                            else "Connect the listener and enable encrypted capture before testing."
                    }) { Text("Arm one Main WhatsApp dismissal test") }
                }
                if (status.mainTestRemoved && !mainAccepted) {
                    OutlinedButton(onClick = { mainAccepted = GuardianNotificationListener.confirmMainDismissal(context) }) {
                        Text("I saw the Main WhatsApp test disappear on my Fold")
                    }
                }
                LauncherToggle("Automatically dismiss eligible Main WhatsApp messages", automatic) { enabled ->
                    if (GuardianNotificationListener.setAutomaticDismissal(context, enabled)) automatic = enabled
                    else externalError = "Verify the Main WhatsApp test on your Fold before enabling automatic dismissal."
                }
                status.mainDismissal?.let { Text(it) }
                Text("Samsung may briefly show a notification before Guardian can dismiss it. Friends notifications remain available.")

                externalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider()
            GuardianCaptureControls(allowChanges = allowPermissionChanges, listenerReady = access && status.connected)
            onOpenLauncher?.let { open ->
                OutlinedButton(onClick = open) { Text("Open Guardian Home test launcher") }
            }
        }
    }
}

@Composable
private fun GuardianCaptureControls(allowChanges: Boolean, listenerReady: Boolean) {
    val context = LocalContext.current
    val health by GuardianCapture.state.collectAsState()
    val captureStage by GuardianCapture.mainCaptureStage.collectAsState()
    var consent by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(context) { GuardianCapture.refresh(context) }

    Text("Encrypted notification capture", style = MaterialTheme.typography.titleMedium)
    Text("Choose to capture Main WhatsApp, or only conversations selected in Monitored Contacts. " +
        "SMS/MMS always requires a selected conversation. Friends WhatsApp is excluded.")
    Text(when {
        health.busy -> "Checking encrypted archive"
        health.limited -> "Limited Protection · encrypted capture or storage could not be fully verified"
        health.enabled && !listenerReady -> "Limited Protection · notification access or listener connection required"
        health.enabled -> "Capture enabled · ${health.retained ?: 0} retained"
        else -> "Capture off · ${health.retained ?: 0} retained"
    })
    if (health.enabled) Text(if (health.includesMain) "Scope · Main WhatsApp and selected SMS/MMS" else "Scope · Selected conversations only")
    captureStage?.let { Text(it.label) }
    OutlinedButton(
        enabled = allowChanges && !health.busy && (health.enabled || listenerReady),
        onClick = {
            if (health.enabled) GuardianCapture.setEnabled(context, false) else consent = true
        },
    ) { Text(if (health.enabled) "Stop encrypted capture" else "Enable encrypted capture") }
    if (health.enabled && allowChanges) {
        TextButton(enabled = listenerReady && !health.busy, onClick = { consent = true }) { Text("Change capture scope") }
    }
    Text("Retention · ${health.retentionDays} days")
    GuardianCaptureStore.RETENTION_DAYS.forEach { days ->
        OutlinedButton(
            enabled = allowChanges && !health.busy && days != health.retentionDays,
            onClick = { GuardianCapture.setRetention(context, days) },
        ) { Text("Keep for $days days") }
    }
    OutlinedButton(enabled = allowChanges && !health.busy, onClick = { confirmClear = true }) {
        Text("Clear encrypted capture")
    }
    if (consent && allowChanges) GuardianAlertDialog(
        onDismissRequest = { consent = false },
        title = { Text("Choose encrypted capture scope") },
        text = {
            Column {
                Text("Main WhatsApp captures supported incoming messages even before you select contacts. " +
                    "Explicit contact exclusions still apply. Friends WhatsApp is excluded from both options.")
                TextButton(enabled = listenerReady, onClick = {
                    consent = false
                    GuardianCapture.setEnabled(context, true, includeMain = true)
                }) { Text("Capture Main WhatsApp") }
            }
        },
        confirmButton = { TextButton(enabled = listenerReady, onClick = {
            consent = false
            GuardianCapture.setEnabled(context, true)
        }) { Text("Selected conversations only") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } },
    )
    if (confirmClear && allowChanges) GuardianAlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear encrypted capture?") },
        text = { Text("This deletes the saved notification archive and its encryption key.") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            GuardianCapture.clear(context)
        }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
