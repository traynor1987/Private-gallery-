package uk.co.traynor.privategallery.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.guardian.*
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode

@Composable
internal fun GuardianCallShieldScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val roles = remember(context) { context.getSystemService(RoleManager::class.java) }
    fun hasRole() = roles?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    fun hasContacts() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    var roleHeld by remember { mutableStateOf(hasRole()) }
    var contactsGranted by remember { mutableStateOf(hasContacts()) }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = hasRole()
    }
    val contactsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsGranted = hasContacts()
    }
    val store = remember(context) { GuardianCaptureStore(context) }
    val scope = rememberCoroutineScope()
    var configuration by remember { mutableStateOf<Pair<Boolean, List<GuardianPhoneRule>>?>(null) }
    var sanctuaryModes by remember { mutableStateOf<Set<String>?>(null) }
    var historyEnabled by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<GuardianCallActivity>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var number by remember { mutableStateOf("") }
    LaunchedEffect(store) {
        configuration = withContext(Dispatchers.IO) { runCatching { store.phoneShieldSettings() }.getOrNull() }
        sanctuaryModes = withContext(Dispatchers.IO) { runCatching { store.phoneShieldSanctuaryModes() }.getOrNull() }
        historyEnabled = withContext(Dispatchers.IO) { runCatching { store.callHistoryEnabled() }.getOrDefault(false) }
        if (configuration == null || sanctuaryModes == null) error = "Call Shield settings could not be opened."
    }
    fun save(enabled: Boolean, rules: List<GuardianPhoneRule>) {
        if (busy) return
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { store.savePhoneShieldSettings(enabled, rules) }.isSuccess }
            if (saved) configuration = enabled to rules
            error = if (saved) null else "Call Shield settings could not be saved."
            busy = false
        }
    }
    fun saveSanctuaryModes(modes: Set<String>) {
        if (busy) return
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { store.savePhoneShieldSanctuaryModes(modes) }.isSuccess }
            if (saved) sanctuaryModes = modes
            error = if (saved) null else "Sanctuary overrides could not be saved."
            busy = false
        }
    }
    GuardianAlertDialog(
        onDismissRequest = onClose,
        title = { Text("Call Shield") },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mobile calls", style = MaterialTheme.typography.titleMedium)
                Text(if (roleHeld) "Android call screening enabled" else "Android call screening inactive")
                if (!roleHeld && roles?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true) {
                    OutlinedButton(onClick = {
                        roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    }) { Text("Enable Android call screening") }
                }
                if (!contactsGranted) {
                    Text("Android may omit calls from saved contacts unless you grant Contacts access.")
                    TextButton(onClick = { contactsRequest.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Allow screening of saved contacts")
                    }
                }
                configuration?.let { (enabled, rules) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Protect selected callers")
                        Switch(checked = enabled, enabled = !busy, onCheckedChange = { save(it, rules) })
                    }
                    OutlinedTextField(number, { number = it.take(40) }, label = { Text("Phone number") }, singleLine = true)
                    val canonical = guardianPhoneNumber(number)
                    TextButton(enabled = !busy && canonical != null && rules.size < 100 &&
                        rules.none { it.number == canonical }, onClick = {
                        val value = canonical ?: return@TextButton
                        save(enabled, rules + GuardianPhoneRule(value, GuardianCallMode.SILENCE))
                    }) { Text("Add number") }
                    rules.forEach { rule ->
                        HorizontalDivider()
                        Text(rule.number)
                        val platforms = listOf(
                            Triple("Mobile calls", rule.mode, { mode: GuardianCallMode -> rule.copy(mode = mode) }),
                            Triple("Main WhatsApp", rule.mainWhatsApp, { mode: GuardianCallMode -> rule.copy(mainWhatsApp = mode) }),
                            Triple("Dual Messenger WhatsApp", rule.friendsWhatsApp, { mode: GuardianCallMode -> rule.copy(friendsWhatsApp = mode) }),
                        )
                        platforms.forEach { (platform, currentMode, update) ->
                            Text(platform, style = MaterialTheme.typography.labelLarge)
                            GuardianCallMode.entries.forEach { mode ->
                                FilterChip(selected = currentMode == mode, enabled = !busy, onClick = {
                                    save(enabled, rules.map { if (it.number == rule.number) update(mode) else it })
                                }, label = { Text(mode.label) })
                            }
                        }
                        TextButton(enabled = !busy, onClick = { save(enabled, rules - rule) }) { Text("Remove number") }
                    }
                }
                Text("Silence asks Android to ring silently. Block asks Android to reject the call. Use the number format shown for the incoming call.")
                Text("WhatsApp Silence and Block both attempt to dismiss supported incoming-call notifications. Calls can still ring or vibrate. Guardian needs Notification Access and an exact caller number in the notification.")
                Text(if (GuardianNotificationListener.hasAccess(context)) "Notification Access granted" else "Notification Access missing")
                Text("Dual Messenger uses the Friends WhatsApp profile configured in Sanctuary.")
                sanctuaryModes?.let { modes ->
                    HorizontalDivider()
                    Text("Sanctuary overrides", style = MaterialTheme.typography.titleMedium)
                    Text("During selected modes, protected mobile numbers set to Silence are temporarily blocked.")
                    SanctuaryMode.entries.forEach { mode ->
                        FilterChip(selected = mode.name in modes, enabled = !busy, onClick = {
                            saveSanctuaryModes(if (mode.name in modes) modes - mode.name else modes + mode.name)
                        }, label = { Text(mode.label) })
                    }
                }
                HorizontalDivider()
                Text("Private activity", style = MaterialTheme.typography.titleMedium)
                Text("Keeps up to 100 action requests for seven days. Call outcomes are unconfirmed.")
                LauncherToggle("Record call protection requests", historyEnabled) { enabled ->
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { runCatching { store.setCallHistoryEnabled(enabled) }.isSuccess }
                        if (saved) historyEnabled = enabled else error = "Could not change call history settings."
                    }
                }
                TextButton(onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { store.callHistory() } }
                        result.onSuccess { history = it }.onFailure { error = "Could not read private call activity." }
                    }
                }) { Text("Review private activity") }
                history?.let { rows ->
                    if (rows.isEmpty()) Text("No saved call protection requests.")
                    rows.forEach { row ->
                        val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.time))
                        Text("${row.number} · ${row.platform.label} · $time · ${row.mode.label} requested")
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val cleared = withContext(Dispatchers.IO) { runCatching { store.clearCallHistory() }.isSuccess }
                            if (cleared) history = emptyList() else error = "Could not delete call activity."
                        }
                    }) { Text("Delete call activity") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}
