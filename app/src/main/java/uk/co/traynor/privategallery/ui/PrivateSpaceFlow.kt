package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.domain.*

/** UI callbacks carry no master key, repository, session capability or container metadata. */
internal data class PrivateSpaceActions(
    val exit: () -> Unit,
    val setup: (CharArray) -> Unit,
    val unlock: (CharArray) -> Unit,
    val beginRecovery: () -> Unit,
    val recover: (ByteArray) -> Unit,
    val resetPin: (CharArray) -> Unit,
    val changePin: (CharArray) -> Unit,
    val replaceRecovery: () -> Unit,
    val takeRecoveryDisplay: () -> CharArray?,
    val acknowledgeRecoveryDisplay: () -> Unit,
    val confirmRecovery: (ByteArray) -> Unit,
    val updateSettings: (StrongAuthInterval, SecondaryAutoLock) -> Unit,
    val enrollBiometric: () -> Unit,
    val unlockBiometric: () -> Unit,
    val disableBiometric: () -> Unit,
) {
    constructor(controller: SecondaryController) : this(
        controller::exit, controller::setup, controller::unlock, controller::beginRecovery,
        controller::recover, controller::resetPin, controller::changePin, controller::replaceRecovery,
        controller::takeRecoveryDisplay, controller::acknowledgeRecoveryDisplay, controller::confirmRecovery,
        controller::updateSettings, controller::prepareBiometricEnrollment, controller::prepareBiometricUnlock,
        controller::disableBiometric,
    )
}

/** This subtree is mounted only inside a discovered flow; none of its state is saveable. */
@Composable
internal fun PrivateSpaceFlow(state: SecondaryUiState, actions: PrivateSpaceActions) {
    if (state.route == SecondaryRoute.CLOSED) return
    BackHandler { actions.exit() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (state.route == SecondaryRoute.READY) "Private space" else "Authenticate", style = MaterialTheme.typography.headlineMedium)
            // Route identity, not saved state, owns ephemeral form fields and one-time display copies.
            key(state.route) {
                when (state.route) {
                    SecondaryRoute.CLOSED -> Unit
                    SecondaryRoute.CHECKING -> CircularProgressIndicator()
                    SecondaryRoute.UNAVAILABLE -> Text("This request is unavailable. Your stored data has not been replaced.")
                    SecondaryRoute.SETUP -> {
                        Text("Create an independent PIN with 12–64 digits. Use a different PIN from your main gallery.")
                        Text("Choose a randomly generated long PIN. Memorable patterns are weaker; a PIN is not a high-entropy recovery key.")
                        Text("You will also receive an independent recovery key. Keep it somewhere safe, away from this device.")
                        PinEntry("Create PIN", true, state.busy, actions.setup)
                    }
                    SecondaryRoute.PIN -> {
                        Text("Enter your PIN to continue.")
                        PinEntry("Unlock", false, state.busy, actions.unlock)
                        if (state.biometricAvailable) OutlinedButton(onClick = actions.unlockBiometric, enabled = !state.busy) { Text("Use biometrics") }
                        TextButton(onClick = actions.beginRecovery, enabled = !state.busy) { Text("Use recovery key") }
                    }
                    SecondaryRoute.RECOVERY_DISPLAY -> RecoveryDisplay(actions)
                    SecondaryRoute.RECOVERY_CONFIRM -> {
                        Text("Enter the recovery key you saved to confirm that you have it. Setup is not complete until it is confirmed.")
                        RecoveryEntry("Confirm recovery key", state.busy, actions.confirmRecovery)
                    }
                    SecondaryRoute.RECOVERY_AUTH -> {
                        Text("Enter your independent recovery key. You will then choose a replacement PIN.")
                        RecoveryEntry("Continue", state.busy, actions.recover)
                    }
                    SecondaryRoute.RESET_PIN -> {
                        Text("Choose a replacement PIN with 12–64 digits, different from your main gallery PIN.")
                        PinEntry("Save PIN", true, state.busy, actions.resetPin)
                    }
                    SecondaryRoute.READY -> ReadySpace(state, actions)
                }
            }
            if (state.busy && state.route != SecondaryRoute.CHECKING) CircularProgressIndicator()
            state.error?.let { error -> Text(when (error) {
                SecondaryUiError.UNAVAILABLE -> "This request is unavailable. Try again with your PIN."
                SecondaryUiError.AUTHENTICATION -> "Authentication was not accepted."
                SecondaryUiError.PIN_POLICY -> "Choose a different PIN with 12–64 digits."
                SecondaryUiError.RETRY_LATER -> "Please wait before trying again."
            }, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = actions.exit) { Text(if (state.route == SecondaryRoute.READY) "Lock and return" else "Cancel") }
        }
    }
}

@Composable
private fun PinEntry(label: String, confirm: Boolean, busy: Boolean, submit: (CharArray) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    OutlinedTextField(pin, { pin = it.take(64); mismatch = false }, label = { Text("PIN") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), enabled = !busy)
    if (confirm) OutlinedTextField(repeated, { repeated = it.take(64); mismatch = false }, label = { Text("Confirm PIN") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), enabled = !busy)
    if (mismatch) Text("The PIN entries do not match.", color = MaterialTheme.colorScheme.error)
    Button(enabled = !busy && pin.isNotEmpty(), onClick = {
        if (confirm && pin != repeated) mismatch = true
        else {
            val owned = pin.toCharArray()
            pin = ""; repeated = ""
            submit(owned)
        }
    }) { Text(label) }
}

@Composable
private fun RecoveryDisplay(actions: PrivateSpaceActions) {
    val secret = remember { actions.takeRecoveryDisplay() }
    DisposableEffect(secret) { onDispose { secret?.fill('\u0000') } }
    Text("Save this recovery key now. It will not be shown again.")
    if (secret != null) Text(secret.concatToString(), style = MaterialTheme.typography.bodyLarge)
    else Text("This display has ended. Cancel and authenticate again to restart confirmation.")
    Text("Recovery can restore access only when your encrypted container data still exists. It cannot replace a lost device or a missing backup. This space is not included in the main gallery backup.")
    Button(enabled = secret != null, onClick = {
        secret?.fill('\u0000')
        actions.acknowledgeRecoveryDisplay()
    }) { Text("I saved the key") }
}

@Composable
private fun RecoveryEntry(label: String, busy: Boolean, submit: (ByteArray) -> Unit) {
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    OutlinedTextField(text, { text = it.take(80); invalid = false }, label = { Text("Recovery key") },
        visualTransformation = PasswordVisualTransformation(), enabled = !busy)
    if (invalid) Text("Enter the complete 64-character recovery key.", color = MaterialTheme.colorScheme.error)
    Button(enabled = !busy, onClick = {
        val normalized = text.filterNot(Char::isWhitespace)
        if (normalized.length != 64 || normalized.any { it.digitToIntOrNull(16) == null }) invalid = true
        else {
            val bytes = ByteArray(32) { i -> ((normalized[i * 2].digitToInt(16) shl 4) or normalized[i * 2 + 1].digitToInt(16)).toByte() }
            text = ""
            submit(bytes)
        }
    }) { Text(label) }
}

@Composable
private fun ReadySpace(state: SecondaryUiState, actions: PrivateSpaceActions) {
    var changingPin by remember { mutableStateOf(false) }
    var replacingRecovery by remember { mutableStateOf(false) }
    Text("Your independent encrypted container is ready.")
    Text("Media features are not available here yet. No media from your main gallery is displayed or moved.")
    HorizontalDivider()
    Text("Security", style = MaterialTheme.typography.titleLarge)
    OutlinedButton(onClick = { changingPin = !changingPin }, enabled = !state.busy) { Text("Change PIN") }
    if (changingPin) PinEntry("Save PIN", true, state.busy) { changingPin = false; actions.changePin(it) }
    Text("Biometric convenience")
    Text("Any strong biometric enrolled on this device may be accepted. Android does not tell this app which person authenticated. Your PIN is required after restarting the app and when strong authentication is due.")
    if (state.biometricEnabled) OutlinedButton(onClick = actions.disableBiometric, enabled = !state.busy) { Text("Disable biometrics") }
    else OutlinedButton(onClick = actions.enrollBiometric, enabled = !state.busy) { Text("Enable biometrics") }
    Text("Require PIN", style = MaterialTheme.typography.titleMedium)
    StrongAuthInterval.entries.forEach { interval ->
        GalleryChoiceRow(when (interval) { StrongAuthInterval.EVERY_TIME -> "Every time"; StrongAuthInterval.DAY -> "Every day"; StrongAuthInterval.THREE_DAYS -> "Every 3 days"; StrongAuthInterval.SEVEN_DAYS -> "Every 7 days" }, state.strongAuthInterval == interval) {
            if (!state.busy) actions.updateSettings(interval, state.autoLock)
        }
    }
    Text("Lock after leaving the app", style = MaterialTheme.typography.titleMedium)
    SecondaryAutoLock.entries.forEach { timeout ->
        GalleryChoiceRow(when (timeout) { SecondaryAutoLock.IMMEDIATE -> "Immediately"; SecondaryAutoLock.THIRTY_SECONDS -> "30 seconds"; SecondaryAutoLock.ONE_MINUTE -> "1 minute"; SecondaryAutoLock.FIVE_MINUTES -> "5 minutes" }, state.autoLock == timeout) {
            if (!state.busy) actions.updateSettings(state.strongAuthInterval, timeout)
        }
    }
    Text("Recovery key confirmed", style = MaterialTheme.typography.titleMedium)
    Text("Keep your recovery key safe. It cannot recreate encrypted data that has been lost. Independent backup is not available yet.")
    OutlinedButton(onClick = { replacingRecovery = true }, enabled = !state.busy) { Text("Replace recovery key") }
    if (replacingRecovery) AlertDialog(onDismissRequest = { replacingRecovery = false },
        title = { Text("Replace recovery key?") },
        text = { Text("The current recovery key remains valid until you save and confirm its replacement.") },
        confirmButton = { TextButton(onClick = { replacingRecovery = false; actions.replaceRecovery() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { replacingRecovery = false }) { Text("Cancel") } })
}
