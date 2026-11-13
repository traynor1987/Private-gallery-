package uk.co.traynor.privategallery.home2

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.arcade.SudokuGame
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.launcher.LauncherExitHold

private fun challengePreferences(context: Context) =
    context.getSharedPreferences("nova_sanctuary_exit_challenges", Context.MODE_PRIVATE)

internal fun sanctuaryChallenges(context: Context, mode: SanctuaryMode): SanctuaryChallenges {
    val prefs = challengePreferences(context)
    return SanctuaryChallenges(
        SanctuarySudoku.entries.firstOrNull { it.name == prefs.getString("${mode.name}_sudoku", null) }
            ?: SanctuarySudoku.OFF,
        prefs.getBoolean("${mode.name}_pin", false),
        prefs.getBoolean("${mode.name}_hold", false),
    )
}

@Composable
internal fun SanctuaryChallengeSettings(context: Context) {
    var mode by rememberSaveable { mutableStateOf(SanctuaryMode.GAMING) }
    var rules by remember(mode) { mutableStateOf(sanctuaryChallenges(context, mode)) }
    fun save(value: SanctuaryChallenges) {
        challengePreferences(context).edit().putString("${mode.name}_sudoku", value.sudoku.name)
            .putBoolean("${mode.name}_pin", value.pin).putBoolean("${mode.name}_hold", value.hold).apply()
        rules = value
    }
    Text("Early exit challenges", style = MaterialTheme.typography.titleMedium)
    Text("The confirmation passphrase remains required. Select any additional steps for each mode.")
    SanctuaryMode.entries.forEach { option ->
        FilterChip(mode == option, { mode = option }, { Text(option.label) })
    }
    TextButton(onClick = { save(rules.copy(sudoku = SanctuarySudoku.entries[(rules.sudoku.ordinal + 1) % SanctuarySudoku.entries.size])) }) {
        Text("Sudoku difficulty · ${rules.sudoku.label}")
    }
    val pinConfigured = PinVaultKeyStore(context).isConfigured
    Row {
        Checkbox(rules.pin, { save(rules.copy(pin = it)) }, enabled = pinConfigured || rules.pin)
        Text(if (pinConfigured) "Require Primary Vault PIN" else "Primary Vault PIN unavailable")
    }
    Row {
        Checkbox(rules.hold, { save(rules.copy(hold = it)) })
        Text("Require five-second hold")
    }
}

@Composable
internal fun SanctuaryChallengeSteps(session: SanctuarySession, rules: SanctuaryChallenges, onReady: (Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // A fixed puzzle per session and difficulty prevents cycling by reopening the screen.
    val puzzle = remember(session.id, rules.sudoku) {
        if (rules.sudoku == SanctuarySudoku.OFF) null
        else runCatching { SudokuGame.create(rules.sudoku.ordinal - 1, session.id.hashCode().toLong()) }.getOrNull()
    }
    var board by rememberSaveable(session.id, rules.sudoku.name) { mutableStateOf("0".repeat(81)) }
    var selected by rememberSaveable(session.id) { mutableIntStateOf(-1) }
    val solved = puzzle != null && sanctuarySudokuSolved(puzzle, board)
    var pin by remember { mutableStateOf("") }
    var pinVerified by remember(session.id) { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    var holdStart by remember { mutableStateOf<Long?>(null) }
    var held by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(solved, pinVerified, held, rules) { onReady(rules.passed(solved, pinVerified, held)) }
    if (rules.sudoku != SanctuarySudoku.OFF) {
        Text("Sudoku · ${rules.sudoku.label}")
        if (puzzle == null) Text("The puzzle could not be opened. You can use Android credential recovery below.")
        else {
            (0 until 9).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0 until 9).forEach { column ->
                        val index = row * 9 + column
                        val given = puzzle.given(index)
                        val value = if (given != 0) given else board[index].digitToInt()
                        Surface(
                            onClick = { selected = index }, enabled = given == 0 && !solved,
                            color = if (selected == index || given != 0) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f).heightIn(min = 36.dp).semantics {
                                contentDescription = "Row ${row + 1}, column ${column + 1}, " +
                                    if (value == 0) "empty" else "$value${if (given != 0) ", given" else ""}"
                            },
                        ) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Text(if (value == 0) "·" else "$value") }
                        }
                    }
                }
            }
            for (digits in listOf(1..5, 6..9)) Row(Modifier.fillMaxWidth()) {
                digits.forEach { digit ->
                    TextButton(modifier = Modifier.weight(1f), enabled = selected in 0..80 && !solved, onClick = {
                        if (puzzle.given(selected) == 0) board = board.replaceRange(selected, selected + 1, "$digit")
                    }) { Text("$digit") }
                }
            }
            TextButton(onClick = { board = "0".repeat(81); selected = -1 }) { Text("Reset puzzle") }
            if (solved) Text("Puzzle solved")
        }
    }
    if (rules.pin && !pinVerified) {
        OutlinedTextField(pin, { pin = it.filter { digit -> digit in '0'..'9' }.take(32); pinError = false },
            label = { Text("Primary Vault PIN") }, singleLine = true, enabled = !verifying,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        TextButton(enabled = pin.isNotEmpty() && !verifying, onClick = {
            val entered = pin.toCharArray()
            pin = ""
            verifying = true
            scope.launch {
                try {
                    pinVerified = withContext(Dispatchers.IO) {
                        runCatching { PinVaultKeyStore(context).unlock(entered).fill(0) }.isSuccess
                    }
                    pinError = !pinVerified
                } finally {
                    entered.fill('\u0000')
                    verifying = false
                }
            }.invokeOnCompletion { entered.fill('\u0000') }
        }) { Text("Verify PIN") }
        if (pinError) Text("PIN could not be verified.")
    }
    if (rules.hold && !held) LauncherExitHold(
        holdStart, onStart = { holdStart = SystemClock.elapsedRealtime(); true },
        onEnd = { released ->
            val started = holdStart
            holdStart = null
            if (released && started != null && SystemClock.elapsedRealtime() - started >= 5000L) held = true
        }, tag = "sanctuary-end-hold",
    )
}

@Composable
internal fun SanctuaryChallengeRecovery(
    activity: android.app.Activity,
    store: uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore,
    sessionId: String,
    onEnded: () -> Unit,
) {
    var request by remember(sessionId) { mutableStateOf<android.os.CancellationSignal?>(null) }
    var unavailable by remember(sessionId) { mutableStateOf(false) }
    DisposableEffect(sessionId) { onDispose { request?.cancel() } }
    TextButton(enabled = request == null, onClick = {
        if (activity.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure != true) {
            unavailable = true
            return@TextButton
        }
        val signal = android.os.CancellationSignal()
        request = signal
        unavailable = !runCatching {
            android.hardware.biometrics.BiometricPrompt.Builder(activity)
                .setTitle("End Sanctuary using recovery")
                .setSubtitle("Confirm your Android screen lock to end this session")
                .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build().authenticate(signal, activity.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                            request = null
                            if (!signal.isCanceled && !activity.isDestroyed && !activity.isFinishing &&
                                result.authenticationType == android.hardware.biometrics.BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL &&
                                store.refresh()?.id == sessionId &&
                                store.end(uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore.END_PHRASE)
                            ) onEnded()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            request = null
                        }
                    })
        }.isSuccess
        if (unavailable) request = null
    }) { Text("Recover with Android screen lock") }
    if (unavailable) Text("Android screen-lock recovery is unavailable. Emergency access and normal expiry remain available.")
}
