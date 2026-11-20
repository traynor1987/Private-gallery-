package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import uk.co.traynor.privategallery.core.guardian.*

internal enum class GuardianAlertPage(val title: String) {
    PRESENTATION("Notification Presentation"), PRIVACY("Notification Privacy"), RETENTION("Alert retention"),
}

@Composable
internal fun GuardianAlertSettings(page: GuardianAlertPage, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { GuardianSafeNotifications.prefs(context) }
    var presentation by remember { mutableStateOf(GuardianSafeNotifications.presentationMode(context)) }
    var holdUncertain by remember { mutableStateOf(GuardianCapture.holdUncertainByDefault(context)) }
    var retention by remember { mutableStateOf(GuardianSafeNotifications.retention(context)) }
    var sender by remember { mutableStateOf(GuardianSafeNotifications.senderMode(context)) }
    var lockScreen by remember { mutableStateOf(GuardianSafeNotifications.lockScreen(context)) }
    var preview by remember { mutableStateOf(preferences.getBoolean("alert_validated_preview", false)) }

    fun changeVisibility(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
        GuardianSafeNotifications.cancel(context)
    }

    GuardianAlertDialog(
        onDismissRequest = onBack,
        title = { Text(page.title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (page) {
                    GuardianAlertPage.PRESENTATION -> {
                        GuardianPresentationMode.entries.forEach { option ->
                            AlertOption(option.label, presentation == option) {
                                preferences.edit().putString("presentation_mode", option.name).apply()
                                presentation = option
                            }
                        }
                        Text("Smart Filtering keeps ordinary WhatsApp alerts. Nova Guardian Notifications replaces eligible low-risk alerts when Guardian can post an alert.")
                        Text("Both modes replace supported held notifications with a neutral alert, subject to your profile, channel and quiet-hours settings.")
                        LauncherToggle("Hold uncertain messages by default", holdUncertain) {
                            GuardianCapture.setHoldUncertainByDefault(context, it)
                            holdUncertain = it
                        }
                        Text("Monitored Contacts can set their own uncertainty and quiet collection rules.")
                        Text("Enable dismissal for your WhatsApp profile in Notification compatibility. Enable Guardian alerts for that profile in Safe Notifications settings.")
                        Text("Notification Privacy controls safe previews. Android can show the original preview before Guardian receives and verifies the notification.")
                    }
                    GuardianAlertPage.RETENTION -> {
                        Text("Choose how long new alerts stay in the Samsung notification shade. Android controls the brief heads-up banner.")
                        GuardianAlertRetention.entries.forEach { option ->
                            AlertOption(option.label, retention == option) {
                                retention = option
                                preferences.edit().putString("alert_retention", option.name).apply()
                            }
                        }
                        Text("Swiping away an alert or selecting Review later leaves its encrypted record in Guardian. Android may also expire long-lived alerts.")
                    }
                    GuardianAlertPage.PRIVACY -> {
                        Text("Sender identity", style = MaterialTheme.typography.titleSmall)
                        GuardianAlertSender.entries.forEach { option ->
                            AlertOption(option.label, sender == option) {
                                sender = option
                                changeVisibility("alert_sender", option.name)
                            }
                        }
                        Text("Set nicknames and individual overrides in Monitored Contacts.")
                        LauncherToggle("Allow message previews after validation", preview) {
                            preview = it
                            preferences.edit().putBoolean("alert_validated_preview", it).apply()
                            GuardianSafeNotifications.cancel(context)
                        }
                        Text("Only eligible low-concern messages can appear after local validation. Android may retain visible previews in notification history.")
                        Text("Lock screen", style = MaterialTheme.typography.titleSmall)
                        GuardianAlertLockScreen.entries.forEach { option ->
                            AlertOption(option.label, lockScreen == option) {
                                lockScreen = option
                                changeVisibility("alert_lock_screen", option.name)
                            }
                        }
                        GuardianCard("Example alert") {
                            Text("Nova Guardian", style = MaterialTheme.typography.titleSmall)
                            val name = guardianAlertSender(sender, "Sample sender", "Sample nickname")
                            Text(listOfNotNull(name, "Main WhatsApp").joinToString(" · "))
                            Text("Message protected until Guardian review.")
                            Text("Lock screen · " + when (lockScreen) {
                                GuardianAlertLockScreen.HIDDEN -> "Alert hidden"
                                GuardianAlertLockScreen.SENDER -> name ?: "Protected notification"
                                GuardianAlertLockScreen.FOLLOW -> "Same as unlocked"
                            }, color = GuardianMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onBack) { Text("Done") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

@Composable
private fun AlertOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Text(label)
    }
}

@Composable
internal fun GuardianAlertContactOptions(contact: GuardianContact, busy: Boolean, save: (GuardianContact) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    var alias by remember(contact.alertAlias) { mutableStateOf(contact.alertAlias.orEmpty()) }
    TextButton(enabled = !busy, onClick = { edit = !edit }) {
        Text("Alert identity · ${contact.alertSender?.label ?: "Use Guardian default"}")
    }
    if (edit) {
        AlertOption("Use Guardian default", contact.alertSender == null) {
            if (!busy) save(contact.copy(alertSender = null))
        }
        GuardianAlertSender.entries.forEach { option ->
            AlertOption(option.label, contact.alertSender == option) {
                if (!busy) save(contact.copy(alertSender = option))
            }
        }
        OutlinedTextField(alias, { alias = it.take(100) }, label = { Text("Private nickname") }, singleLine = true)
        TextButton(enabled = !busy, onClick = { save(contact.copy(alertAlias = guardianSenderDisplayName(alias))) }) {
            Text("Save nickname")
        }
    }
}
