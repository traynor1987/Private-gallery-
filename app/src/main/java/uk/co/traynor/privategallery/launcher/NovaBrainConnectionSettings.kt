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
internal fun NovaBrainConnectionSettings(canUse: () -> Boolean = { true }, onConfigurationChanged: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { NovaBrainConnectionStore(context) }
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(BrainConnectionStatus()) }
    var setup by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf("") }
    var configuredOrigin by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Job?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { revision++; pending?.cancel(); token = ""; setup = false; status = BrainConnectionStatus() }
        }
        lifecycle.addObserver(observer)
        onDispose { revision++; pending?.cancel(); token = ""; lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(store) {
        try {
            val saved = withContext(Dispatchers.IO) { store.read() }
            configured = saved != null
            configuredOrigin = saved?.origin.orEmpty()
            origin = configuredOrigin
        } catch (_: Exception) { message = "Saved connection could not be read. Configure it again." }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nova Brain connection", style = MaterialTheme.typography.titleLarge)
            Text(status.message, Modifier.testTag("nova-brain-status"))
            Text("Local learning, Guardian, Evidence Locker and Sanctuary work offline. Connection checks send only your access token to the status endpoint. No messages, evidence or personal feedback are sent.")
            Text("Connecting does not activate Cloud Teacher or paid analysis. Each sensitive request still requires its own preview and explicit consent.")
            if (configured) Text("Server: $configuredOrigin")
            if (status.phase == BrainConnectionPhase.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !saving && status.phase != BrainConnectionPhase.CHECKING, onClick = { if (canUse()) { origin = configuredOrigin; token = ""; setup = true } }) { Text(if (configured) "Configure" else "Connect") }
                if (configured) TextButton(enabled = status.phase != BrainConnectionPhase.CHECKING, onClick = {
                    if (!canUse()) return@TextButton
                    val ticket = ++revision
                    status = BrainConnectionStatus(BrainConnectionPhase.CHECKING, "Checking HTTPS and authentication…")
                    pending = scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val saved = store.read()
                                if (saved == null) BrainConnectionStatus() else NovaBrainConnection().check(saved) { !isActive }
                            } catch (_: Exception) { BrainConnectionStatus(BrainConnectionPhase.ERROR, "Connection configuration unavailable · local processing continues") }
                        }
                        if (ticket == revision && canUse()) status = result
                    }
                }) { Text("Check connection") }
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
            token = ""
            saving = true
            pending = scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.save(configuration) }
                    if (canUse()) { configuredOrigin = configuration.origin; configured = true; setup = false; status = BrainConnectionStatus(); message = "Connection saved · tap Check connection"; onConfigurationChanged() }
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
                            configured = false; configuredOrigin = ""; origin = ""; setup = false; status = BrainConnectionStatus(); message = "Connection removed"; onConfigurationChanged()
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
