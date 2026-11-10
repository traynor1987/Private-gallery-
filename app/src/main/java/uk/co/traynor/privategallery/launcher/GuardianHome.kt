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
import kotlinx.coroutines.delay
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.core.guardian.GuardianCapture
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.guardian.ProbeAccount
import uk.co.traynor.privategallery.core.guardian.SyntheticResult
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

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
) {
    LauncherPanel("Guardian Home", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListItem(headlineContent = { Text("Show Guardian Page") }, trailingContent = {
                Switch(options.showPage, { onChange(options.copy(showPage = it)) },
                    modifier = Modifier.testTag("guardian-show-page"))
            })
            LauncherToggle("Show Sanctuary Countdown", options.countdown) { onChange(options.copy(countdown = it)) }
            LauncherToggle("Show Ready Message Count", options.ready) { onChange(options.copy(ready = it)) }
            LauncherToggle("Show Cooling Down Count", options.cooling) { onChange(options.copy(cooling = it)) }
            LauncherToggle("Show Guardian Health", options.health) { onChange(options.copy(health = it)) }
            LauncherToggle("Hide Sender Names", options.hideSenders) { onChange(options.copy(hideSenders = it)) }
            LauncherToggle("Hide Message Previews", options.hidePreviews) { onChange(options.copy(hidePreviews = it)) }
            Text("Sender and preview preferences will apply when the encrypted inbox is available.")
            Text("Guardian Page Theme · NovaOS Original")
        }
    }
}

internal val GuardianNavy = Color(0xFF0D1727)
internal val GuardianGlass = Color(0xFF172F44)
internal val GuardianBorder = Color(0xFF2B455D)
internal val GuardianMuted = Color(0xFFB6C9DF)
internal val GuardianBlue = Color(0xFF9BCBFF)
private val GuardianMint = Color(0xFF9DE3CE)
private val GuardianAmber = Color(0xFFF0C79B)

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
    LaunchedEffect(context) { GuardianCapture.refresh(context) }
    val access = GuardianNotificationListener.hasAccess(context)
    val dnd = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    val counts = capture.inbox
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

    PrivateGalleryTheme(AppTheme.DARK) {
        Surface(Modifier.fillMaxSize().testTag("launcher-guardian-home"), color = GuardianNavy) {
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
                    GuardianCard("Guardian assessment", badge = if ((learning ?: 0) > 0) "Local estimates" else "Analysis pending") {
                        if (counts != null) Text("${counts.pending} awaiting review", style = MaterialTheme.typography.titleMedium)
                        Text(when {
                            learning == null -> "Nova Brain could not verify the local example store."
                            learning == 0 -> "Teach Nova Brain a few examples to personalise local estimates."
                            else -> "Nova Brain compares messages with your approved examples. You control review and holds."
                        }, color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = GuardianBorder)
                        GuardianLine(Icons.Outlined.Lock, "Message previews are hidden", GuardianBlue)
                    }

                    if (options.health) {
                        val profileStatus = when {
                            listener.results.containsKey(ProbeAccount.UNRESOLVED) -> "Unresolved events observed"
                            listener.results.keys.containsAll(listOf(ProbeAccount.MAIN, ProbeAccount.FRIENDS)) -> "Both observed"
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
                                capture.enabled -> "${capture.retained ?: 0} retained · awaiting classification"
                                else -> "Off"
                            })
                            GuardianHealthLine("Nova Brain", when {
                                capture.busy -> "Checking"
                                learning == null -> "Local learning unavailable"
                                learning == 0 -> "Local Only · awaiting examples"
                                else -> "Local Only · $learning examples"
                            }, (learning ?: 0) > 0)
                            GuardianHealthLine("Cloud analysis", "Unavailable")
                            GuardianHealthLine("NovaOS test dismissal", when (listener.synthetic) {
                                SyntheticResult.DISMISSED -> "Android confirmed removal"
                                SyntheticResult.WAITING, SyntheticResult.DISMISSING -> "Testing"
                                SyntheticResult.DISMISS_FAILED, SyntheticResult.CAPTURE_FAILED,
                                SyntheticResult.NOT_RECEIVED, SyntheticResult.DISCONNECTED -> "Check diagnostics"
                                SyntheticResult.UNSUPPORTED -> "Unavailable"
                                else -> "Not verified"
                            }, listener.synthetic == SyntheticResult.DISMISSED)
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
        colors = CardDefaults.cardColors(containerColor = GuardianGlass),
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
        colors = CardDefaults.cardColors(containerColor = GuardianGlass),
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
