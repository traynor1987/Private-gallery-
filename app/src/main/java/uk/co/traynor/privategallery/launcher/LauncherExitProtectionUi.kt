package uk.co.traynor.privategallery.launcher

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.delay

private const val HOME_RECOVERY =
    "For Android recovery, open Settings from the system Quick Settings panel. Emergency calling remains available through Android."

@Composable
internal fun LauncherExitProtectionDialogs(protection: LauncherExitProtection) {
    val challenge = protection.challenge
    when {
        protection.configure -> LauncherExitConfiguration(protection)
        challenge != null -> LauncherExitConfirmation(protection, challenge)
        protection.status != null -> GuardianAlertDialog(
            onDismissRequest = protection::cancel,
            title = { Text("Launcher protection") },
            text = { Text("${protection.status}\n\n$HOME_RECOVERY") },
            confirmButton = { TextButton(onClick = protection::cancel) { Text("Close") } },
        )
    }
}

@Composable
private fun LauncherExitConfiguration(protection: LauncherExitProtection) {
    var value by remember(protection.settings) { mutableStateOf(protection.settings) }
    GuardianAlertDialog(
        onDismissRequest = protection::cancel,
        title = { Text("Launcher exit protection") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Protected launcher changes require your Primary PIN, spoken phrase and a five-second hold.")
                LauncherOptions("Exit protection", LauncherExitMode.entries, value.mode, { value = value.copy(mode = it) })
                Text("Android system Settings and device administration can change Home independently. Emergency calling and Android recovery remain available.")
                OutlinedTextField(
                    value.phrase, { value = value.copy(phrase = it.take(96)) },
                    label = { Text("Confirmation phrase") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                LauncherOptions("Cooldown in minutes after three incorrect attempts", LauncherExitCooldown.entries,
                    value.cooldown, { value = value.copy(cooldown = it) })
                LauncherToggle("Allow typing the phrase for accessibility", value.allowTypedPhrase) {
                    value = value.copy(allowTypedPhrase = it)
                }
                Text("The typed alternative still requires the exact phrase and the five-second hold. On-device speech recognition requires microphone permission.")
                protection.status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { protection.saveConfiguration(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = protection::cancel) { Text("Cancel") } },
    )
}

@Composable
private fun LauncherExitConfirmation(protection: LauncherExitProtection, challenge: LauncherExitChallenge) {
    var input by remember(challenge.sessionId, challenge.step) { mutableStateOf("") }
    val pinStep = challenge.step == LauncherExitStep.PIN
    GuardianAlertDialog(
        onDismissRequest = protection::cancel,
        title = { Text("Change default launcher") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (challenge.step) {
                    LauncherExitStep.PIN -> {
                        Text("Step 1 of 3. Enter your Primary NovaOS PIN.")
                        OutlinedTextField(
                            input, { input = it.filter { digit -> digit in '0'..'9' } },
                            label = { Text("Primary PIN") }, singleLine = true, enabled = !protection.verifying,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("launcher-exit-pin"),
                        )
                    }
                    LauncherExitStep.PHRASE -> {
                        Text("Step 2 of 3. Say this phrase.")
                        Text(protection.settings.phrase, style = MaterialTheme.typography.titleSmall)
                        Button(onClick = protection::listen, enabled = !protection.listening) {
                            Text(if (protection.listening) "Listening on this device…" else "Start listening")
                        }
                        Text("If Android asks for microphone permission, reopen Change Default Launcher after granting it.",
                            style = MaterialTheme.typography.bodySmall)
                        if (protection.canTypePhrase) {
                            OutlinedTextField(
                                input, { input = it.take(120) }, label = { Text("Type the same phrase") },
                                modifier = Modifier.fillMaxWidth().testTag("launcher-exit-phrase"),
                                singleLine = true, enabled = !protection.listening,
                            )
                        }
                    }
                    LauncherExitStep.HOLD -> {
                        Text("Step 3 of 3. Hold continuously for five seconds, then release. With a keyboard, focus the control and hold Enter or Space.")
                        LauncherExitHold(
                            challenge.holdStartedMillis,
                            protection::beginHold,
                            protection::endHold,
                        )
                    }
                }
                protection.status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(HOME_RECOVERY, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (pinStep || (challenge.step == LauncherExitStep.PHRASE && protection.canTypePhrase)) {
                TextButton(
                    onClick = {
                        val submitted = input
                        input = ""
                        if (pinStep) protection.verifyPin(submitted.toCharArray())
                        else protection.verifyTypedPhrase(submitted)
                    },
                    enabled = input.isNotBlank() && !protection.verifying && !protection.listening,
                ) { Text("Verify") }
            }
        },
        dismissButton = { TextButton(onClick = protection::cancel) { Text("Cancel") } },
    )
}

@Composable
private fun LauncherExitHold(started: Long?, onStart: () -> Boolean, onEnd: (Boolean) -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(started) {
        progress = 0f
        if (started != null) {
            do {
                progress = ((SystemClock.elapsedRealtime() - started) / LAUNCHER_EXIT_HOLD_MILLIS.toFloat()).coerceIn(0f, 1f)
                if (progress < 1f) delay(50)
            } while (progress < 1f)
        }
    }
    var focused by remember { mutableStateOf(false) }
    var heldKey by remember { mutableIntStateOf(KeyEvent.KEYCODE_UNKNOWN) }
    LaunchedEffect(windowFocused) {
        if (!windowFocused) {
            heldKey = KeyEvent.KEYCODE_UNKNOWN
            onEnd(false)
        }
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (focused) 2.dp else 1.dp, MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("launcher-exit-hold")
            .onFocusChanged {
                focused = it.isFocused
                if (!focused && heldKey != KeyEvent.KEYCODE_UNKNOWN) {
                    heldKey = KeyEvent.KEYCODE_UNKNOWN
                    onEnd(false)
                }
            }
            .onPreviewKeyEvent {
                val key = it.nativeKeyEvent
                when (key.keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER -> Unit
                    else -> return@onPreviewKeyEvent false
                }
                if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0 && heldKey == KeyEvent.KEYCODE_UNKNOWN && onStart()) {
                    heldKey = key.keyCode
                } else if (key.action == KeyEvent.ACTION_UP && heldKey == key.keyCode) {
                    heldKey = KeyEvent.KEYCODE_UNKNOWN
                    onEnd(!key.isCanceled)
                }
                true
            }
            .focusable()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!onStart()) return@awaitEachGesture
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.singleOrNull() ?: break
                            val point = change.position
                            if (change.id != down.id || change.isConsumed ||
                                point.x < 0f || point.y < 0f || point.x >= size.width || point.y >= size.height
                            ) break
                            change.consume()
                            if (!change.pressed) {
                                onEnd(true)
                                break
                            }
                        }
                    } finally {
                        onEnd(false)
                    }
                }
            },
    ) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Text(if (progress >= 1f) "Release to continue" else "Hold for five seconds")
        }
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}
