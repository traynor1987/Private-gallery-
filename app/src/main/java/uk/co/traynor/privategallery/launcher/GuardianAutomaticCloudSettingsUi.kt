package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.CloudTeacherClient
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore
import uk.co.traynor.privategallery.core.guardian.GuardianAutomaticCloudSettings
import uk.co.traynor.privategallery.core.guardian.GuardianCloudJob
import uk.co.traynor.privategallery.core.guardian.GuardianConversationKey

@Composable
internal fun GuardianAutomaticCloudSettingsUi(canManage: () -> Boolean) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val settings = remember(app) { GuardianAutomaticCloudSettings(app) }
    val connection = remember(app) { NovaBrainConnectionStore(app) }
    var consent by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var scopes by remember { mutableIntStateOf(0) }
    var connectionSaved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<List<String>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) {
            runCatching { connection.read() to settings.read() }.getOrNull()
        }
        if (!canManage()) return
        val configured = result?.first
        val policy = result?.second?.takeIf { it.origin == configured?.origin }
        connectionSaved = configured != null
        enabled = policy != null
        scopes = policy?.scopes?.size ?: 0
    }

    fun change(allow: Boolean) {
        if (!canManage() || busy) return
        busy = true
        scope.launch {
            val changed = withContext(Dispatchers.IO) {
                runCatching {
                    if (!canManage()) return@runCatching false
                    if (allow) settings.enable(checkNotNull(connection.read()).origin)
                    else {
                        settings.disable()
                        GuardianCloudJob.cancel(app)
                    }
                    true
                }.getOrDefault(false)
            }
            reload()
            if (canManage()) notice = if (changed) null else "Could not save the encrypted setting."
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    GuardianCard("Automatic Cloud Assistance") {
        Text(if (enabled) "On · $scopes approved conversations" else "Off")
        Text(if (connectionSaved) "Nova Brain connection saved" else "Connect Nova Brain in Settings first.")
        if (enabled) {
            Text("Approve or revoke an individual WhatsApp conversation from a reviewed message.")
            TextButton(enabled = !busy, onClick = { change(false) }) { Text("Turn off automatic assistance") }
        } else {
            TextButton(enabled = connectionSaved && !busy, onClick = { consent = true }) { Text("Review and enable") }
        }
        TextButton(enabled = connectionSaved && !busy, onClick = {
            if (canManage()) {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CloudTeacherClient().status(checkNotNull(connection.read())) }.getOrNull()
                    }
                    if (canManage()) status = if (result == null) listOf("Authenticated server status unavailable.")
                        else listOf(if (result.canAnalyze && result.automaticAllowed) "Authenticated · automatic analysis available"
                                else "Authenticated · automatic analysis unavailable") + result.lines()
                    busy = false
                }
            }
        }) { Text("Check cloud status and API usage") }
        status.forEach { Text(it, color = GuardianMuted) }
        notice?.let { Text(it) }
    }
    if (consent) GuardianAlertDialog(
        onDismissRequest = { consent = false },
        title = { Text("Allow automatic cloud assistance?") },
        text = { Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text("For individually approved WhatsApp conversations, NovaOS may send one short, uncertain notification excerpt to your VPS and OpenAI without asking again. Known sender names are redacted. Other names may still appear in the wording.")
            Text("Attachments, histories, Vault data and personal impact feedback stay out of automatic requests. Local protection runs immediately. Server approval and budget limits apply.")
            Text("Turning this off stops future requests. Deleting local evidence cannot recall data already processed by your VPS or OpenAI.")
        } },
        confirmButton = { TextButton(onClick = { consent = false; change(true) }) { Text("Enable automatic assistance") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } },
    )
}

@Composable
internal fun GuardianAutomaticScope(key: GuardianConversationKey, canManage: () -> Boolean) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val settings = remember(app) { GuardianAutomaticCloudSettings(app) }
    var approved by remember(key) { mutableStateOf<Boolean?>(null) }
    var busy by remember(key) { mutableStateOf(false) }
    var confirm by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val state = withContext(Dispatchers.IO) {
            runCatching { settings.read()?.let { key in it.scopes } }.getOrNull()
        }
        if (canManage()) approved = state
    }
    fun apply(allow: Boolean) {
        if (!canManage() || busy) return
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val config = NovaBrainConnectionStore(app).read() ?: return@runCatching false
                    canManage() && settings.setScope(config.origin, key, allow)
                }.getOrDefault(false)
            }
            if (canManage()) {
                if (saved) approved = allow
                failed = !saved
            }
            busy = false
        }
    }
    approved?.let { allowed ->
        TextButton(enabled = !busy, onClick = { if (allowed) apply(false) else confirm = true }) {
            Text(if (allowed) "Stop automatic cloud assistance for this conversation" else "Allow automatic cloud assistance for this conversation")
        }
        if (failed) Text("This conversation scope could not be saved.")
    }
    if (confirm) GuardianAlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Approve this conversation?") },
        text = { Text("Future uncertain, eligible notification excerpts from this conversation may be sent to your VPS and OpenAI under your automatic assistance settings.") },
        confirmButton = { TextButton(onClick = { confirm = false; apply(true) }) { Text("Approve conversation") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
