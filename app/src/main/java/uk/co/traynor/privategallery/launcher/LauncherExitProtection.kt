package uk.co.traynor.privategallery.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.crypto.InvalidPinException
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

internal enum class LauncherExitCooldown(val minutes: Int) { ONE(1), FIVE(5), FIFTEEN(15) }

internal enum class LauncherExitMode(private val title: String) {
    SANCTUARY("Only during Sanctuary"),
    ALWAYS("Always require protection"),
    DISABLED("Disabled");

    fun requiresProtection(session: String?) = this == ALWAYS || this == SANCTUARY && session != null

    override fun toString() = title
}

internal data class LauncherExitSettings(
    val phrase: String = "I choose to change my Home app",
    val cooldown: LauncherExitCooldown = LauncherExitCooldown.FIVE,
    val allowTypedPhrase: Boolean = false,
    val mode: LauncherExitMode = LauncherExitMode.SANCTUARY,
)

private class LauncherExitStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nova-launcher-exit", Context.MODE_PRIVATE)

    fun settings(): LauncherExitSettings {
        val defaults = LauncherExitSettings()
        return LauncherExitSettings(
            phrase = prefs.getString("phrase", null) ?: defaults.phrase,
            cooldown = LauncherExitCooldown.entries.firstOrNull { it.name == prefs.getString("cooldown", null) }
                ?: defaults.cooldown,
            allowTypedPhrase = prefs.getBoolean("typed", false),
            mode = LauncherExitMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: defaults.mode,
        )
    }

    fun saveSettings(value: LauncherExitSettings): Boolean = prefs.edit().putString("phrase", value.phrase)
        .putString("cooldown", value.cooldown.name).putBoolean("typed", value.allowTypedPhrase).putString("mode", value.mode.name).commit()

    fun remaining(): Long = readAttempts().remaining(now(), duration())

    fun fail(): Long? {
        val time = now()
        val duration = duration()
        val attempts = readAttempts().failed(time, duration)
        val deadline = attempts.deadline
        val saved = prefs.edit().putInt("failures", attempts.failures)
            .putLong("untilElapsed", deadline?.elapsed ?: 0L).putLong("untilWall", deadline?.wall ?: 0L)
            .putInt("boot", deadline?.boot ?: -1).commit()
        return attempts.remaining(time, duration).takeIf { saved }
    }

    fun clearFailures(): Boolean =
        prefs.edit().remove("failures").remove("untilElapsed").remove("untilWall").remove("boot").commit()

    private fun duration() = settings().cooldown.minutes * 60_000L

    private fun readAttempts() = LauncherExitAttempts(
        prefs.getInt("failures", 0),
        prefs.getLong("untilWall", 0L).takeIf { it > 0L }?.let {
            LauncherExitTime(prefs.getLong("untilElapsed", 0L), it, prefs.getInt("boot", -1))
        },
    )

    private fun now() = LauncherExitTime(
        SystemClock.elapsedRealtime(),
        System.currentTimeMillis(),
        runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1),
    )
}

internal class LauncherExitProtection(
    private val activity: ComponentActivity,
    private val sessionId: () -> String?,
    private val foreground: () -> Boolean,
    private val openAndroidHomeSettings: () -> Unit,
) {
    private val store = LauncherExitStore(activity)
    private val keys = PinVaultKeyStore(activity.applicationContext)
    var settings by mutableStateOf(LauncherExitSettings())
        private set
    var configure by mutableStateOf(false)
        private set
    var challenge by mutableStateOf<LauncherExitChallenge?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var verifying by mutableStateOf(false)
        private set
    var listening by mutableStateOf(false)
        private set
    val canTypePhrase: Boolean
        get() = settings.mode != LauncherExitMode.ALWAYS && settings.allowTypedPhrase
    private var pendingAction: (() -> Unit)? = null
    private var pinJob: Job? = null
    private var speechTimeout: Job? = null
    private var speech: SpeechRecognizer? = null
    private var microphoneRequest: LauncherExitChallenge? = null
    private val microphone = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = microphoneRequest
        microphoneRequest = null
        if (pending != null && isCurrent(pending)) {
            if (granted) beginRecognition(pending)
            else status = "Microphone permission is required to verify the spoken phrase."
        }
    }

    fun request(action: () -> Unit = openAndroidHomeSettings) {
        cancel()
        if (!foreground()) return
        val active = runCatching(sessionId).getOrElse {
            status = "NovaOS could not check Sanctuary protection."
            return
        }
        settings = runCatching(store::settings).getOrElse {
            status = "Launcher protection settings are temporarily unavailable."
            return
        }
        if (!settings.mode.requiresProtection(active)) {
            action()
            return
        }
        val remaining = runCatching(store::remaining).getOrElse {
            status = "Launcher protection settings are temporarily unavailable."
            return
        }
        if (remaining > 0) {
            status = cooldownMessage(remaining)
        } else if (!keys.isConfigured) {
            status = "Set up your Primary NovaOS PIN before using protected launcher switching."
        } else {
            pendingAction = action
            challenge = LauncherExitChallenge(active)
        }
    }

    fun requestConfiguration() {
        cancel()
        if (!foreground()) return
        if (!runCatching { sessionId() == null }.getOrDefault(false)) {
            status = "End Sanctuary before changing launcher exit protection."
            return
        }
        settings = store.settings()
        if (settings.mode == LauncherExitMode.ALWAYS) request { configure = true }
        else configure = true
    }

    fun saveConfiguration(value: LauncherExitSettings) {
        if (!configure || !foreground() || !runCatching { sessionId() == null }.getOrDefault(false)) {
            cancel()
            return
        }
        val phrase = value.phrase.trim()
        if (phrase.length !in 5..96 || phrase.count(Char::isLetter) < 3 || phrase.any(Char::isISOControl)) {
            status = "Enter a phrase between 5 and 96 characters."
        } else if (!store.saveSettings(value.copy(phrase = phrase))) {
            status = "Launcher protection settings could not be saved."
        } else cancel()
    }

    fun verifyPin(pin: CharArray) {
        val current = challenge
        if (current?.step != LauncherExitStep.PIN || verifying || !isCurrent(current)) {
            pin.fill('\u0000')
            return
        }
        verifying = true
        status = null
        pinJob = activity.lifecycleScope.launch {
            val verified = withContext(Dispatchers.Default) {
                runCatching { keys.unlock(pin).fill(0) }.also { pin.fill('\u0000') }
            }
            if (!isCurrent(current)) return@launch
            verifying = false
            when {
                verified.isSuccess -> challenge = current.verifyPin(true)
                verified.exceptionOrNull() is InvalidPinException -> fail(current, "The Primary PIN did not match.")
                else -> {
                    challenge = null
                    status = "The Primary PIN could not be verified."
                }
            }
        }.also { job -> job.invokeOnCompletion { pin.fill('\u0000') } }
    }

    fun listen() {
        val current = challenge ?: return
        if (current.step != LauncherExitStep.PHRASE || listening || microphoneRequest != null || !isCurrent(current)) return
        status = null
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphoneRequest = current
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        } else beginRecognition(current)
    }

    fun verifyTypedPhrase(value: String) {
        val current = challenge ?: return
        if (current.step != LauncherExitStep.PHRASE || !canTypePhrase ||
            listening || microphoneRequest != null || !isCurrent(current)
        ) return
        verifyPhrase(current, value)
    }

    private fun verifyPhrase(current: LauncherExitChallenge, transcript: String) {
        val next = current.verifyPhrase(transcript, settings.phrase)
        if (next == null) fail(current, "The confirmation phrase did not match.")
        else {
            challenge = next
            status = null
        }
    }

    private fun beginRecognition(current: LauncherExitChallenge) {
        if (!isCurrent(current) || current.step != LauncherExitStep.PHRASE) return
        val started = runCatching {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) return@runCatching false
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            speech = recognizer
            listening = true
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    if (speech !== recognizer) return
                    stopRecognition()
                    if (!isCurrent(current)) return
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        fail(current, "The confirmation phrase was not recognized.")
                    } else status = "On-device speech recognition is temporarily unavailable."
                }
                override fun onResults(results: Bundle?) {
                    if (speech !== recognizer) return
                    stopRecognition()
                    if (!isCurrent(current)) return
                    verifyPhrase(current, results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
            true
        }.getOrDefault(false)
        if (started) {
            speechTimeout = activity.lifecycleScope.launch {
                delay(15_000)
                stopRecognition()
                if (isCurrent(current)) status = "On-device speech recognition did not respond."
            }
        } else {
            stopRecognition()
            status = "On-device speech recognition is unavailable on this device."
        }
    }

    fun beginHold(): Boolean {
        val current = challenge ?: return false
        if (!isCurrent(current)) return false
        val next = current.beginHold(SystemClock.elapsedRealtime()) ?: return false
        status = null
        challenge = next
        return true
    }

    fun endHold(complete: Boolean) {
        val current = challenge ?: return
        if (current.holdStartedMillis == null) return
        challenge = current.cancelHold()
        if (!complete || !foreground()) return
        val active = runCatching(sessionId).getOrElse { cancel(); return }
        if (active != current.sessionId) {
            cancel()
            status = "Sanctuary changed. Open Home settings again."
            return
        }
        if (!current.finishHold(SystemClock.elapsedRealtime(), active)) {
            status = "Hold continuously for five seconds before releasing."
            return
        }
        // Consume this challenge before dispatching any Android intent.
        val action = pendingAction
        cancel()
        if (runCatching(store::clearFailures).getOrDefault(false)) action?.invoke()
        else status = "Launcher protection settings could not be saved."
    }

    fun onSessionChanged(active: String?) {
        val current = challenge
        if ((current != null && current.sessionId != active) || (configure && active != null)) cancel()
    }

    fun cancel() {
        pendingAction = null
        challenge = null
        configure = false
        status = null
        verifying = false
        microphoneRequest = null
        pinJob?.cancel()
        pinJob = null
        stopRecognition()
    }

    private fun stopRecognition() {
        val previous = speech
        speech = null
        listening = false
        speechTimeout?.cancel()
        speechTimeout = null
        if (previous != null) runCatching { previous.destroy() }
    }

    private fun isCurrent(expected: LauncherExitChallenge): Boolean =
        challenge === expected && foreground() && runCatching { sessionId() == expected.sessionId }.getOrDefault(false)

    private fun fail(current: LauncherExitChallenge, error: String) {
        val remaining = runCatching(store::fail).getOrNull()
        challenge = if (remaining == 0L) LauncherExitChallenge(current.sessionId) else null
        status = when {
            remaining == null -> "Launcher protection settings are temporarily unavailable."
            remaining > 0L -> cooldownMessage(remaining)
            else -> error
        }
    }

    private fun cooldownMessage(millis: Long) = "Too many attempts. Try again in ${(millis + 999) / 1_000} seconds."
}
