package uk.co.traynor.privategallery.core.sanctuary

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Only the NovaOS Sanctuary settings screen requests a device-credential pause. */
internal object SanctuaryWorkspacePause {
    private val authorization = SanctuaryPauseAuthorization(SystemClock::elapsedRealtime)

    fun isPaused(sessionId: String): Boolean = authorization.isPaused(sessionId)

    fun authenticate(
        activity: Activity,
        store: SanctuaryStore,
        sessionId: String,
        onFinished: () -> Unit,
    ): CancellationSignal? {
        if (store.refresh()?.id != sessionId || isPaused(sessionId)) return null
        if (activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true) return null

        val request = authorization.request(sessionId)
        val cancellation = CancellationSignal()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val confirmed = !cancellation.isCanceled && !activity.isFinishing && !activity.isDestroyed &&
                    result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
                val granted = authorization.complete(request, store.refresh()?.id, confirmed)
                if (granted) {
                    SanctuaryLauncherPolicy.signalPolicyChange()
                    Handler(Looper.getMainLooper()).postDelayed(
                        SanctuaryLauncherPolicy::signalPolicyChange,
                        SanctuaryPauseAuthorization.PAUSE_DURATION,
                    )
                }
                if (!cancellation.isCanceled && !activity.isDestroyed) onFinished()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authorization.complete(request, null, confirmed = false)
                if (!cancellation.isCanceled && !activity.isDestroyed) onFinished()
            }
        }
        try {
            BiometricPrompt.Builder(activity)
                .setTitle("Pause Android app protection for five minutes")
                .setSubtitle("Confirm your Android screen lock")
                .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                .build()
                .authenticate(cancellation, activity.mainExecutor, callback)
        } catch (_: IllegalArgumentException) {
            authorization.complete(request, null, confirmed = false)
            return null
        } catch (_: IllegalStateException) {
            authorization.complete(request, null, confirmed = false)
            return null
        } catch (_: SecurityException) {
            authorization.complete(request, null, confirmed = false)
            return null
        }
        return cancellation
    }
}

/** A pause expires with its timer, its session, or the app process. */
internal class SanctuaryPauseAuthorization(private val clock: () -> Long) {
    class Request internal constructor(val session: String, val expiresAt: Long)

    private data class Pause(val session: String, val expiresAt: Long)

    private var pending: Request? = null
    private var pause: Pause? = null

    @Synchronized fun request(session: String): Request =
        Request(session, clock() + REQUEST_DURATION).also { pending = it }

    @Synchronized fun complete(request: Request, activeSession: String?, confirmed: Boolean): Boolean {
        if (pending !== request) return false
        pending = null
        val now = clock()
        if (!confirmed || activeSession != request.session || now >= request.expiresAt) return false
        pause = Pause(request.session, now + PAUSE_DURATION)
        return true
    }

    @Synchronized fun isPaused(session: String): Boolean =
        pause?.let { it.session == session && clock() < it.expiresAt } == true

    companion object {
        const val PAUSE_DURATION = 5 * 60_000L
        private const val REQUEST_DURATION = 60_000L
    }
}
