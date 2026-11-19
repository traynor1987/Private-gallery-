package uk.co.traynor.privategallery.launcher

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore
import uk.co.traynor.privategallery.core.guardian.GuardianAutomaticCloudSettings
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.core.guardian.GuardianCapture
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.guardian.ProbeAccount
import uk.co.traynor.privategallery.core.guardian.SyntheticResult
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode

internal data class GuardianHomeOptions(
    val showPage: Boolean = true,
    val countdown: Boolean = true,
    val ready: Boolean = true,
    val cooling: Boolean = true,
    val health: Boolean = true,
    val hideSenders: Boolean = true,
    val hidePreviews: Boolean = true,
) {
    private fun flags() = listOf(showPage, countdown, ready, cooling, health, hideSenders, hidePreviews)

    fun save(context: Context) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        KEYS.zip(flags()).forEach { (key, value) -> edit.putBoolean(key, value) }
        edit.apply()
    }

    companion object {
        private const val PREFS = "nova_guardian_home"
        private val KEYS = listOf("show_page", "countdown", "ready", "cooling", "health", "hide_senders", "hide_previews")

        fun read(context: Context): GuardianHomeOptions {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun flag(index: Int) = prefs.getBoolean(KEYS[index], true)
            return GuardianHomeOptions(flag(0), flag(1), flag(2), flag(3), flag(4), flag(5), flag(6))
        }
    }
}

@Composable
internal fun GuardianHomeSettings(
    options: GuardianHomeOptions, onChange: (GuardianHomeOptions) -> Unit, onBack: () -> Unit,
    onDiagnostics: () -> Unit = {},
) {
    val activity = LocalActivity.current
    LauncherPanel("Guardian Settings", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListItem(headlineContent = { Text("Show Guardian Page") }, trailingContent = {
                Switch(options.showPage, { onChange(options.copy(showPage = it)) },
                    modifier = Modifier.testTag("guardian-show-page"))
            })
            if (activity != null) {
                GuardianCard("Notifications") {
                    TextButton(onClick = {
                        activity.startActivity(GuardianInboxActivity.safeNotificationsIntent(activity))
                    }) { Text("Safe Notifications Centre") }
                    GuardianAlertPage.entries.forEach { page ->
                        TextButton(onClick = { GuardianInboxActivity.openSettings(activity, page.name) }) {
                            Text(page.title)
                        }
                    }
                    TextButton(onClick = onDiagnostics) { Text("Notification Compatibility") }
                }
                GuardianCard("Guardian tools") {
                    TextButton(onClick = { GuardianInboxActivity.open(activity, GuardianInboxSection.PENDING) }) {
                        Text("Guardian Inbox")
                    }
                    TextButton(onClick = { GuardianInboxActivity.openSettings(activity, "message-retention") }) {
                        Text("Messages, retention & recovery")
                    }
                    TextButton(onClick = { GuardianInboxActivity.openSettings(activity, "contacts") }) {
                        Text("Monitored Contacts")
                    }
                    TextButton(onClick = { GuardianInboxActivity.openPersonalImpact(activity) }) {
                        Text("Personal Impact Learning")
                    }
                    TextButton(onClick = { GuardianInboxActivity.openSettings(activity, "call-shield") }) {
                        Text("Call Shield")
                    }
                    TextButton(onClick = onDiagnostics) { Text("Guardian Health and Diagnostics") }
                    NovaBrainKnowledgeCard()
                }
            }
            LauncherToggle("Show Sanctuary Countdown", options.countdown) { onChange(options.copy(countdown = it)) }
            LauncherToggle("Show Ready Message Count", options.ready) { onChange(options.copy(ready = it)) }
            LauncherToggle("Show Cooling Down Count", options.cooling) { onChange(options.copy(cooling = it)) }
            LauncherToggle("Show Guardian Health", options.health) { onChange(options.copy(health = it)) }
        }
    }
}

@Composable
internal fun GuardianHome(
    options: GuardianHomeOptions,
    sanctuary: LauncherSanctuaryStatus?,
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    onSettings: () -> Unit,
    onSanctuary: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val listener by GuardianNotificationListener.state.collectAsState()
    val capture by GuardianCapture.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            GuardianCaptureStore.archiveChanges.collect { GuardianCapture.refresh(context) }
        }
    }
    val access = GuardianNotificationListener.hasAccess(context)
    val dnd = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    val counts = capture.inbox
    val cloudHealth by produceState<Pair<String, String>?>(null, context, options.health) {
        if (options.health) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val record = NovaBrainConnectionStore(context).readRecord()
                    val check = record?.lastAuthenticated
                    val optedIn = GuardianAutomaticCloudSettings(context).read()?.origin == record?.configuration?.origin &&
                        record != null
                    val connection = when {
                        record == null -> "Not configured"
                        check == null -> "Awaiting authenticated check"
                        check.staging -> "Last authenticated · staging"
                        else -> "Last authenticated · production"
                    }
                    val analysis = when {
                        !optedIn -> "Automatic assistance off"
                        check == null -> "Awaiting server status"
                        !check.analysisEnabled -> "Disabled by server at last check"
                        else -> "Server allowed analysis at last check"
                    }
                    connection to analysis
                }.getOrDefault("Connection details unavailable" to "Availability unknown")
            }
        }
    }
    LaunchedEffect(counts) {
        val remaining = counts?.refreshAfterMillis ?: return@LaunchedEffect
        delay(remaining)
        GuardianCapture.refresh(context)
    }
    fun openInbox(section: GuardianInboxSection) {
        activity?.let { GuardianInboxActivity.open(it, section) }
    }
    val main = apps.filter { it.packageName == "com.whatsapp" && it.profileSerial == null }
    val friends = apps.filter { it.packageName == "com.whatsapp" && it.profile?.isClone == true }
    fun rule(entries: List<LauncherApp>): String {
        val rules = entries.map { decisions.forLauncherApp(it) }.distinct()
        val rule = rules.singleOrNull() ?: return "Unavailable"
        return if (rule.canLaunch) "Allowed" else "Restricted"
    }

    GuardianTheme {
        Surface(Modifier.fillMaxSize().testTag("launcher-guardian-home"), color = GuardianNavy, contentColor = GuardianPrimary) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val spacing = if (maxWidth < 360.dp) 12.dp else 16.dp
                Column(
                    Modifier.widthIn(max = 680.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Shield, null, Modifier.size(28.dp), tint = GuardianBlue)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Nova Guardian", Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Powered by Sanctuary", color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, "Settings", tint = GuardianMuted) }
                    }

                    if (activity != null) OutlinedButton(
                        onClick = { activity.startActivity(GuardianInboxActivity.safeNotificationsIntent(activity)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Inbox, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Safe Notifications Centre")
                    }
                    NovaBrainKnowledgeCard()

                    val protection = when {
                        sanctuary == null -> "Protection inactive"
                        sanctuary.paused -> "Launcher protection paused"
                        sanctuary.mode == SanctuaryMode.PANIC -> "Panic Protection"
                        else -> "Active"
                    }
                    val active = sanctuary != null && !sanctuary.paused
                    GuardianCard("Sanctuary", badge = protection, badgeColor = if (active) GuardianMint else GuardianMuted) {
                        if (sanctuary != null && options.countdown) {
                            val seconds = sanctuary.remainingMillis.coerceAtLeast(0L) / 1000L
                            val duration = String.format(java.util.Locale.getDefault(), "%d:%02d:%02d remaining",
                                seconds / 3600, seconds / 60 % 60, seconds % 60)
                            GuardianLine(Icons.Outlined.AccessTime, duration, GuardianBlue)
                        }
                        if (active) {
                            Text("Main WhatsApp · ${rule(main)}   •   Friends · ${rule(friends)}",
                                color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = onSanctuary, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("Open Sanctuary")
                        }
                    }

                    if (options.ready || options.cooling) {
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            if (options.ready) GuardianMessageTile("Ready to Read", counts?.ready, Icons.Outlined.Inbox, GuardianMint,
                                Modifier.weight(1f).fillMaxHeight().testTag("guardian-ready-tile")) {
                                openInbox(GuardianInboxSection.READY)
                            }
                            if (options.cooling) GuardianMessageTile("Cooling Down", counts?.cooling, Icons.Outlined.Lock, GuardianAmber,
                                Modifier.weight(1f).fillMaxHeight().testTag("guardian-cooling-tile")) {
                                openInbox(GuardianInboxSection.COOLING)
                            }
                        }
                    }

                    val learning = capture.learningCount
                    GuardianCard("Guardian assessment", badge = "Local estimates") {
                        if (counts == null) {
                            Text(if (capture.busy) "Checking archive…" else "Archive counts unavailable", color = GuardianMuted)
                        } else {
                            Text("${counts.total} messages retained", style = MaterialTheme.typography.titleMedium)
                            Text("${counts.pending} awaiting analysis · ${counts.protected} protected · ${counts.history} in history",
                                color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                            if (counts.protected > 0) {
                                TextButton(onClick = { openInbox(GuardianInboxSection.PROTECTED) }) {
                                    Text("Review protected messages")
                                }
                            }
                        }
                        Text(when {
                            learning == null -> "Nova Brain could not verify the local example store."
                            learning == 0 -> "Teach Nova Brain a few examples to personalise local estimates."
                            else -> "Nova Brain compares messages with your approved examples. You control review and holds."
                        }, color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = GuardianBorder)
                        GuardianLine(Icons.Outlined.Lock, "Guardian previews are hidden. Samsung notification previews are separate.", GuardianBlue)
                    }

                    if (options.health) {
                        val profileStatus = when {
                            listener.results.containsKey(ProbeAccount.UNRESOLVED) -> "Unresolved events observed"
                            listener.observedProfiles.containsAll(listOf(ProbeAccount.MAIN, ProbeAccount.FRIENDS)) -> "Both observed"
                            ProbeAccount.MAIN in listener.observedProfiles -> "Main observed · Friends awaiting verification"
                            ProbeAccount.FRIENDS in listener.observedProfiles -> "Friends observed · Main awaiting verification"
                            else -> "Awaiting verification"
                        }
                        GuardianCard("Guardian health", badge = when {
                            !access -> "Permission required"
                            !listener.connected -> "Listener disconnected"
                            else -> "Diagnostics only"
                        }) {
                            val connected = access && listener.connected
                            GuardianHealthLine("Notification listener", if (connected) "Connected" else "Disconnected", connected)
                            GuardianHealthLine("Notification access", if (access) "Granted" else "Off", access)
                            GuardianHealthLine("DND integration", if (dnd) "Policy access granted" else "Policy access off", dnd)
                            GuardianHealthLine("WhatsApp profiles", profileStatus, profileStatus == "Both observed")
                            GuardianHealthLine("Encrypted capture", when {
                                capture.limited -> "Check diagnostics"
                                capture.busy -> "Checking"
                                capture.enabled && !connected -> "Listener required"
                                capture.enabled -> "${capture.retained?.let { "$it retained" } ?: "Count unavailable"} · ${if (capture.includesMain) "Main WhatsApp" else "Selected conversations only"}"
                                else -> "Off"
                            })
                            GuardianHealthLine("Nova Brain", when {
                                capture.busy -> "Checking"
                                learning == null -> "Local learning unavailable"
                                learning == 0 -> "Local rules · ready for examples"
                                else -> "Local Only · $learning examples"
                            }, (learning ?: 0) > 0)
                            GuardianHealthLine("Retention", if (capture.retentionDays == 0) "Keep Forever" else "${capture.retentionDays} days in Inbox")
                            GuardianHealthLine("Last retained capture", guardianHealthTime(capture.diagnostics?.lastCaptureAt))
                            GuardianHealthLine("Latest Inbox decision", capture.diagnostics?.decision ?: "No retained capture")
                            GuardianHealthLine("Cloud connection", cloudHealth?.first ?: "Checking")
                            GuardianHealthLine("Cloud analysis", cloudHealth?.second ?: "Checking")
                            GuardianHealthLine("Last retained cloud advisory", guardianHealthTime(capture.diagnostics?.lastCloudAt))
                            GuardianHealthLine("NovaOS test dismissal", when (listener.synthetic) {
                                SyntheticResult.DISMISSED -> "Android confirmed removal"
                                SyntheticResult.WAITING, SyntheticResult.DISMISSING -> "Testing"
                                SyntheticResult.DISMISS_FAILED, SyntheticResult.CAPTURE_FAILED,
                                SyntheticResult.NOT_RECEIVED, SyntheticResult.DISCONNECTED -> "Check diagnostics"
                                SyntheticResult.UNSUPPORTED -> "Unavailable"
                                else -> "Not verified"
                            }, listener.synthetic == SyntheticResult.DISMISSED)
                            GuardianHealthLine("Main WhatsApp dismissal", when {
                                !listener.connected -> "Listener disconnected"
                                GuardianNotificationListener.automaticDismissalEnabled(context) ->
                                    listener.mainDismissal ?: "Automatic dismissal enabled"
                                GuardianNotificationListener.mainDismissalAccepted(context) -> "Automatic dismissal off"
                                else -> listener.mainDismissal ?: "Automatic dismissal requires Fold verification"
                            })
                            TextButton(onClick = onDiagnostics, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text("View Diagnostics")
                            }
                        }
                    }

                    OutlinedButton(onClick = { openInbox(GuardianInboxSection.PENDING) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Inbox, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Guardian Inbox")
                    }
                }
            }
        }

    }
}

private fun guardianHealthTime(at: Long?): String = at?.let {
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(it))
} ?: "None"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuardianCard(
    title: String,
    badge: String? = null,
    badgeColor: Color = GuardianMuted,
    body: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = guardianCardColors(),
        border = BorderStroke(1.dp, GuardianBorder.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                if (badge != null) {
                    Surface(shape = RoundedCornerShape(20.dp), color = badgeColor.copy(alpha = .12f), contentColor = badgeColor) {
                        Text(badge, Modifier.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            body()
        }
    }
}

@Composable
private fun GuardianMessageTile(
    title: String, count: Int?, icon: ImageVector, accent: Color, modifier: Modifier, onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen, modifier = modifier.heightIn(min = 104.dp), shape = RoundedCornerShape(22.dp),
        colors = guardianCardColors(),
        border = BorderStroke(1.dp, GuardianBorder.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = accent)
            Text(count?.toString() ?: "Unavailable",
                style = if (count == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = GuardianMuted)
        }
    }
}

@Composable
private fun GuardianHealthLine(label: String, value: String, verified: Boolean = false) {
    GuardianLine(
        if (verified) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
        "$label · $value",
        if (verified) GuardianMint else GuardianMuted,
    )
}

@Composable
private fun GuardianLine(icon: ImageVector, label: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
