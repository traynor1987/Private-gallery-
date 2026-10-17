package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.crypto.InvalidPinException
import uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

/** Independent PIN entry; optional authenticated presentation. Never persist PIN or unlock state. */
@Composable
internal fun HiddenFoundationDialog(
    storageContext: android.content.Context = LocalContext.current.applicationContext,
    keepEntryOnStop: () -> Boolean = { false },
    entryVisible: Boolean = true,
    unlockedContent: (@Composable (HiddenSession, () -> Unit, () -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = storageContext
    val store = remember(context) { HiddenCredentialStore(context) }
    val session = remember { HiddenSession(android.os.SystemClock::elapsedRealtime) }
    val alive = remember { AtomicBoolean(true) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var configured by remember { mutableStateOf(store.isConfigured) }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val dismiss by rememberUpdatedState(onDismiss)
    val retainForPicker by rememberUpdatedState(keepEntryOnStop)
    val close = {
        alive.set(false); session.lock(); pin = ""; confirmation = ""; unlocked = false
        dismiss()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            if (retainForPicker()) { session.lock(); unlocked = false; pin = ""; confirmation = "" }
            else close()
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            alive.set(false); session.lock(); pin = ""; confirmation = ""
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(unlocked) {
        while (unlocked) {
            delay(250)
            if (!session.isUnlocked) { unlocked = false; message = "Hidden Vault locked." }
        }
    }
    LaunchedEffect(entryVisible) {
        if (!entryVisible) { session.lock(); unlocked = false; pin = ""; confirmation = "" }
    }
    if (!entryVisible) return
    if (unlocked && unlockedContent != null) {
        unlockedContent(session, close) { session.lock(); unlocked = false }
        return
    }
    AlertDialog(
        onDismissRequest = close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Hidden Vault") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (unlocked) Text("Hidden session unlocked. Media import and viewing will arrive in the next milestone.")
                else {
                    Text(if (configured) "Enter your Hidden PIN." else "Choose a different PIN from Primary: 6–12 digits. Hidden has no backup or recovery yet.")
                    OutlinedTextField(pin, { if (!busy) pin = it.filter { c -> c in '0'..'9' }.take(12) },
                        label = { Text("Hidden PIN") }, enabled = !busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (!configured) OutlinedTextField(confirmation, { if (!busy) confirmation = it.filter { c -> c in '0'..'9' }.take(12) },
                        label = { Text("Confirm Hidden PIN") }, enabled = !busy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
                if (message.isNotEmpty()) Text(message)
            }
        },
        confirmButton = {
            if (unlocked) TextButton(onClick = { session.lock(); unlocked = false }) { Text("Lock Hidden") }
            else TextButton(enabled = !busy && pin.length in 6..12 && (configured || pin == confirmation), onClick = {
                val input = pin.toCharArray()
                pin = ""; confirmation = ""; message = ""; busy = true
                val attempt = session.beginAuthentication()
                scope.launch(Dispatchers.IO) {
                    var candidate: ByteArray? = null
                    try {
                        val exists = store.isConfigured
                        check(alive.get()) { "Hidden entry closed" }
                        val authenticatedKey = if (exists) store.unlock(input) else {
                            val primary = PinVaultKeyStore(context)
                            check(primary.isConfigured) { "Primary must be configured first" }
                            store.createDifferentFromPrimary(input) { proposed ->
                                try { primary.unlock(proposed).also { it.fill(0) }; true }
                                catch (_: InvalidPinException) { false }
                            }
                        }
                        candidate = authenticatedKey
                        val accepted = session.complete(attempt, authenticatedKey)
                        withContext(Dispatchers.Main) {
                            if (alive.get()) { configured = true; unlocked = accepted; busy = false }
                        }
                    } catch (_: Exception) {
                        session.lock()
                        withContext(Dispatchers.Main) {
                            if (alive.get()) {
                                configured = store.isConfigured; busy = false
                                message = "Could not unlock or create Hidden. Check the PIN; setup requires a different PIN from Primary. Existing credentials were retained."
                            }
                        }
                    } finally { input.fill('\u0000'); candidate?.fill(0) }
                }
            }) { Text(if (busy) "Verifying…" else if (configured) "Unlock" else "Create Hidden") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Close") } },
    )
}
