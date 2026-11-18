package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.brain.*

@Composable
internal fun NovaBrainConnectionSettings(canUse: () -> Boolean = { true }, onConfigurationChanged: () -> Unit = {},
    connection: NovaBrainConnection = NovaBrainConnection(), clock: () -> java.time.Instant = java.time.Instant::now) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { NovaBrainConnectionStore(context) }
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(BrainConnectionStatus()) }
    var lastAuthenticated by remember { mutableStateOf<BrainAuthenticatedCheck?>(null) }
    var setup by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf("") }
    var configuredOrigin by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Job?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    fun reloadConfiguration() {
        if (saving || status.phase == BrainConnectionPhase.CHECKING) return
        val ticket = ++revision
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.readRecord() }
                if (ticket == revision) {
                    configured = saved != null
                    configuredOrigin = saved?.configuration?.origin.orEmpty()
                    if (!setup) origin = configuredOrigin
                    lastAuthenticated = saved?.lastAuthenticated
                }
            } catch (_: Exception) {
                if (ticket == revision) message = "Saved connection could not be read securely. Configure it again or retry."
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { revision++; pending?.cancel(); token = ""; setup = false; status = BrainConnectionStatus() }
            if (event == Lifecycle.Event.ON_START) reloadConfiguration()
        }
        lifecycle.addObserver(observer)
        onDispose { revision++; pending?.cancel(); token = ""; lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(store) { reloadConfiguration() }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nova Brain connection", style = MaterialTheme.typography.titleLarge)
            Text(when (status.phase) {
                BrainConnectionPhase.CONNECTED -> "Connected · authenticated test passed"
                BrainConnectionPhase.AUTHENTICATION_REQUIRED -> "Authentication required"
                BrainConnectionPhase.OFFLINE -> "Server unavailable"
                BrainConnectionPhase.ERROR -> "Connection error"
                BrainConnectionPhase.CHECKING -> "Testing connection…"
                BrainConnectionPhase.DISCONNECTED -> if (configured) "Disconnected · test the saved connection" else "Disconnected · no backend configured"
            }, Modifier.testTag("nova-brain-status"), style = MaterialTheme.typography.titleMedium)
            if (status.phase !in setOf(BrainConnectionPhase.DISCONNECTED, BrainConnectionPhase.CHECKING)) Text(status.message)
            Text("Backend environment: " + (lastAuthenticated?.let { if (it.staging) "Staging" else "Production configuration" } ?: "Not yet verified"), Modifier.testTag("nova-brain-environment"))
            Text("Last successful authenticated connection: " + (lastAuthenticated?.at?.let {
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date.from(it))
            } ?: "Never"), Modifier.testTag("nova-brain-last-authenticated"))
            if (lastAuthenticated != null) Text("Environment and server analysis below describe that authenticated response, not a live deployment inspection.", style = MaterialTheme.typography.bodySmall)
            Text(if (status.phase == BrainConnectionPhase.CONNECTED) "Offline fallback: Ready · local protection remains available" else "Offline mode: Active · local protection remains available", Modifier.testTag("nova-brain-offline"))
            Text("Cloud analysis: " + (lastAuthenticated?.let { if (it.analysisEnabled) "Server reports available · owner consent still required" else "Disabled by server" } ?: "Not verified"), Modifier.testTag("nova-brain-analysis"))
            Text("Automatic Cloud Teacher: Off · connection tests do not activate paid analysis")
            Text("Data sharing: No automatic messages, evidence or private feedback uploads", Modifier.testTag("nova-brain-privacy"))
            Text("Guardian and Evidence Locker remain local-first. Their existing optional cloud requests require a reviewed excerpt and explicit consent. Sanctuary works offline.")
            Text("Connection tests send only your access token to the status endpoint. Local Knowledge evaluation is separate from backend connectivity.")
            if (configured) Text("HTTPS backend configured")
            if (status.phase == BrainConnectionPhase.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !saving && status.phase != BrainConnectionPhase.CHECKING, onClick = { if (canUse()) { origin = configuredOrigin; token = ""; setup = true } }) { Text(if (configured) "Configure" else "Connect") }
                TextButton(modifier = Modifier.weight(1f), enabled = configured && !saving && status.phase != BrainConnectionPhase.CHECKING, onClick = {
                    if (!canUse()) return@TextButton
                    val ticket = ++revision
                    status = BrainConnectionStatus(BrainConnectionPhase.CHECKING, "Checking HTTPS and authentication…")
                    pending = scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val saved = store.read()
                                if (saved == null) BrainConnectionStatus() to null else {
                                    val checked = connection.check(saved) { !isActive }
                                    ensureActive()
                                    val receipt = if (checked.phase == BrainConnectionPhase.CONNECTED) store.rememberAuthenticated(saved, checked, clock()) else null
                                    if (checked.phase == BrainConnectionPhase.CONNECTED && receipt == null) BrainConnectionStatus(BrainConnectionPhase.DISCONNECTED, "Configuration changed · test the current connection again") to null
                                    else checked to receipt
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { BrainConnectionStatus(BrainConnectionPhase.ERROR, "Secure connection configuration unavailable · configure or retry. Local processing continues.") to null }
                        }
                        if (ticket == revision && canUse()) { status = result.first; result.second?.let { lastAuthenticated = it } }
                    }
                }) { Text("Test connection") }
            }
            message?.let { Text(it) }
        }
    }
    if (setup) AlertDialog(
        onDismissRequest = { if (!saving) { token = ""; setup = false } },
        title = { Text("Connect Nova Brain") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use your existing HTTPS backend and its owner token. Saving does not send any data or activate analysis.")
                OutlinedTextField(origin, { origin = it.take(512); message = null }, enabled = !saving, label = { Text("HTTPS server address") }, singleLine = true)
                OutlinedTextField(token, { token = it.take(256); message = null }, enabled = !saving, label = { Text("Backend owner token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text("The address and token are encrypted using Android Keystore and excluded from backups. Never enter an OpenAI API key here.")
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(enabled = !saving && runCatching { NovaBrainConfiguration(origin.trim(), token) }.isSuccess, onClick = {
            if (!canUse()) return@TextButton
            val configuration = NovaBrainConfiguration(origin.trim(), token)
            revision++; pending?.cancel()
            token = ""
            saving = true
            pending = scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.save(configuration) }
                    if (canUse()) { configuredOrigin = configuration.origin; configured = true; lastAuthenticated = null; setup = false; status = BrainConnectionStatus(); message = "Connection saved · tap Test connection"; onConfigurationChanged() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "Connection could not be saved securely. Retry." }
                finally { saving = false }
            }
        }) { Text("Save securely") } },
        dismissButton = {
            Row {
                if (configured) TextButton(enabled = !saving, onClick = {
                    if (!canUse()) return@TextButton
                    revision++; pending?.cancel(); token = ""; saving = true
                    pending = scope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.clear() }
                            configured = false; configuredOrigin = ""; origin = ""; lastAuthenticated = null; setup = false; status = BrainConnectionStatus(); message = "Connection removed"; onConfigurationChanged()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "Connection could not be removed. Retry." }
                        finally { saving = false }
                    }
                }) { Text("Disconnect") }
                TextButton(enabled = !saving, onClick = { token = ""; setup = false }) { Text("Cancel") }
            }
        },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}
