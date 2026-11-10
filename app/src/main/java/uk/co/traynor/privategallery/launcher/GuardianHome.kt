package uk.co.traynor.privategallery.launcher

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.guardian.ProbeAccount
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
            Text("Guardian Page Theme · Follow System Theme")
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
) {
    val context = LocalContext.current
    val listener by GuardianNotificationListener.state.collectAsState()
    val access = GuardianNotificationListener.hasAccess(context)
    val dnd = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    var inbox by remember { mutableStateOf(false) }
    val main = apps.filter { it.packageName == "com.whatsapp" && it.profileSerial == null }
    val friends = apps.filter { it.packageName == "com.whatsapp" && it.profile?.isClone == true }
    fun rule(entries: List<LauncherApp>): String {
        val rules = entries.mapNotNull { decisions[it.component] }.distinct()
        return when {
            entries.isEmpty() || rules.size != 1 -> "Unavailable"
            sanctuary == null || sanctuary.paused -> "Protection inactive"
            rules.single().canLaunch -> "Allowed"
            else -> "Blocked"
        }
    }
    PrivateGalleryTheme {
        Surface(Modifier.fillMaxSize().testTag("launcher-guardian-home"), color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Nova Guardian", style = MaterialTheme.typography.headlineSmall)
                            Text("Powered by Sanctuary", style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = onSettings) { Text("Settings") }
                    }
                    GuardianCard("Sanctuary") {
                        Text(when {
                            sanctuary == null -> "Inactive"
                            sanctuary.paused -> "Launcher protection paused"
                            sanctuary.mode == SanctuaryMode.PANIC -> "Panic Protection"
                            else -> "Active"
                        })
                        if (sanctuary != null) {
                            Text(sanctuary.mode.label)
                            if (options.countdown) {
                                val seconds = sanctuary.remainingMillis.coerceAtLeast(0L) / 1000L
                                Text(java.util.Locale.getDefault().let { locale ->
                                    String.format(locale, "%d:%02d:%02d remaining", seconds / 3600, seconds / 60 % 60, seconds % 60)
                                })
                            }
                            if (!sanctuary.paused) {
                                Text("Approved launcher apps · " +
                                    apps.count { decisions[it.component] == Decision.APPROVED })
                                Text("Main WhatsApp · ${rule(main)}")
                                Text("Friends WhatsApp · ${rule(friends)}")
                            }
                        }
                        TextButton(onClick = onSanctuary) { Text("Open Sanctuary") }
                    }
                    GuardianCard("Analysis unavailable") {
                        Text("Message classification and encrypted notification storage are unavailable in this build.")
                    }
                    if (options.ready || options.cooling) GuardianCard("Message summary") {
                        if (options.ready) Text("Ready to Read · Unavailable")
                        if (options.cooling) Text("Cooling Down · Unavailable")
                    }
                    OutlinedButton(onClick = { inbox = true }) { Text("Guardian Inbox") }
                    if (options.health) GuardianCard("Guardian Health") {
                        Text(when {
                            !access -> "Permission Required"
                            !listener.connected -> "Compatibility Issue"
                            else -> "Limited Protection · capture diagnostics only"
                        })
                        Text("Notification access · ${if (access) "Granted" else "Off"}")
                        Text("Listener · ${if (listener.connected && access) "Connected" else "Disconnected"}")
                        Text("DND policy access · ${if (dnd) "Granted" else "Off"}")
                        Text("Main WhatsApp · ${if (listener.results.containsKey(ProbeAccount.MAIN)) "Observed in this probe" else "Awaiting observation"}")
                        Text("Friends WhatsApp · ${if (listener.results.containsKey(ProbeAccount.FRIENDS)) "Observed in this probe" else "Awaiting observation"}")
                        Text("AI classification · Unavailable")
                        Text("NovaOS test dismissal · ${listener.synthetic.label}")
                    }
                }
            }
        }
        if (inbox) AlertDialog(
            onDismissRequest = { inbox = false },
            title = { Text("Guardian Inbox") },
            text = { Text("Ready, Cooling Down, Protected and History will be available after encrypted notification storage is enabled.") },
            confirmButton = { TextButton(onClick = { inbox = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun GuardianCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            body()
        }
    }
}
