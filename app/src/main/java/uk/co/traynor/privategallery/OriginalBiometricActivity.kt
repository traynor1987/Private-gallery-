package uk.co.traynor.privategallery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.util.UUID
import java.util.concurrent.Executor
import javax.crypto.Cipher
import uk.co.traynor.privategallery.core.security.OriginalBiometricBackend
import uk.co.traynor.privategallery.core.security.OriginalBiometricCoordinator
import uk.co.traynor.privategallery.core.security.OriginalBiometricLaunchSlot

/** A new Activity owns a new AndroidX biometric ViewModel for every original request.
 * The Intent carries only a one-shot locator; all authority stays in process. */
internal class OriginalBiometricActivity : FragmentActivity() {
    private var original: BiometricLaunch? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        // This one-shot host must never restore an old AndroidX biometric Fragment
        // before the live-original handshake has rejected a recreated/stale launch.
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val launch = BiometricLaunch.claim(intent.getStringExtra(LOCATOR))
        if (launch == null) { finish(); return }
        original = launch
        launch.attachActivity(this, savedInstanceState != null)
    }
    override fun onStop() {
        super.onStop()
        // Real background and recreation fail closed. A translucent host leaves Main started.
        original?.backgrounded()
    }
    override fun onDestroy() {
        val launch = original
        original = null
        super.onDestroy()
        // This dedicated host never hands its AndroidX model to a configuration successor.
        // A throwing supported clear leaves the original charged: no destruction acknowledgement.
        viewModelStore.clear()
        launch?.activityDestroyReturned()
    }
    companion object { const val LOCATOR = "original-biometric-locator" }
}

internal class AndroidOriginalBiometricBackend(
    private val launcher: Activity,
    private val cipher: Cipher,
    private val info: BiometricPrompt.PromptInfo,
) : OriginalBiometricBackend {
    override val supportsAcknowledgedRetirement: Boolean
        get() = uk.co.traynor.privategallery.core.security.AndroidXBiometricRetirementSupport.available
    override fun acquire(events: OriginalBiometricBackend.Events, attach: (OriginalBiometricBackend.Host) -> Unit) {
        uk.co.traynor.privategallery.core.security.AndroidXBiometricRetirementSupport.requireAdmission()
        val launch = BiometricLaunch(events, cipher, info)
        attach(launch) // Own pending launch before publishing its locator or calling Android.
        launch.launch(launcher)
    }
    companion object {
        private val main = Handler(Looper.getMainLooper())
        // Process bounded exclusion survives Main configuration replacement and failed cleanup.
        val coordinator = OriginalBiometricCoordinator({ main.post(it) }) {
            Looper.myLooper() === Looper.getMainLooper()
        }
    }
}

/** Exactly one strongly owned launch slot; no tombstones and no replacement model reuse.
 * A failed/uncertain start remains charged, because absence of onCreate is not retirement. */
internal class BiometricLaunch(
    events: OriginalBiometricBackend.Events,
    cipher: Cipher,
    info: BiometricPrompt.PromptInfo,
) : OriginalBiometricBackend.Host {
    private val locator = UUID.randomUUID().toString()
    private var events: OriginalBiometricBackend.Events? = events
    private var cipher: Cipher? = cipher
    private var info: BiometricPrompt.PromptInfo? = info
    private var activity: OriginalBiometricActivity? = null
    private var prompt: BiometricPrompt? = null
    private var cancelled = false
    private var destroying = false
    private val destroyedReturn = Runnable {
        requireMain()
        activity = null; prompt = null; this@BiometricLaunch.cipher = null; this@BiometricLaunch.info = null
        pending.retire(this)
        val bridge = this@BiometricLaunch.events
        this@BiometricLaunch.events = null
        bridge?.destroyed()
    }
    fun launch(source: Activity) {
        requireMain()
        if (!pending.reserve(this, locator)) {
            // No startActivity invocation entered: absence of an acquired Activity is known.
            // Retire this original only; never clear or cancel the occupied launch slot.
            check(MAIN.post(destroyedReturn))
            error("Original biometric launch pending")
        }
        source.startActivity(Intent(source, OriginalBiometricActivity::class.java)
            .putExtra(OriginalBiometricActivity.LOCATOR, locator))
    }
    fun attachActivity(value: OriginalBiometricActivity, recreated: Boolean) {
        requireMain(); check(activity == null)
        uk.co.traynor.privategallery.core.security.AndroidXBiometricRetirementSupport.requireAdmission()
        activity = value
        if (cancelled || destroying || recreated || events?.canAcquire() != true) { events?.error(); value.finish(); return }
        try {
            check(events?.canAcquire() == true)
            val fragment = Fragment()
            value.supportFragmentManager.beginTransaction().add(fragment, "original-biometric").commitNow()
            val bridge = checkNotNull(events)
            check(bridge.canAcquire())
            prompt = BiometricPrompt(fragment, DIRECT_MAIN, Callback(bridge))
            val originalCipher = checkNotNull(cipher)
            val originalInfo = checkNotNull(info)
            cipher = null; info = null
            check(bridge.canAcquire())
            checkNotNull(prompt).authenticate(originalInfo, BiometricPrompt.CryptoObject(originalCipher))
            if (!bridge.canAcquire()) { cancel(); destroy() }
        } catch (_: Throwable) { events?.error(); value.finish() }
    }
    override fun cancel() {
        requireMain(); cancelled = true
        prompt?.cancelAuthentication()
    }
    override fun destroy() {
        requireMain(); destroying = true
        activity?.finish() // If onCreate is late, its original handle still owns this obligation.
    }
    fun backgrounded() { events?.error(); destroying = true; activity?.finish() }
    fun activityDestroyReturned() {
        requireMain()
        check(MAIN.post(destroyedReturn)) { "Biometric destruction return dispatch unavailable" }
    }
    // Saved AndroidX callbacks capture only the core's detachable bridge.
    private class Callback(private val bridge: OriginalBiometricBackend.Events) : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val cipher = result.cryptoObject?.cipher
            if (cipher == null) bridge.error() else bridge.success(cipher)
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { bridge.error() }
    }
    companion object {
        private val MAIN = Handler(Looper.getMainLooper())
        private val DIRECT_MAIN = Executor { requireMain(); it.run() }
        private val pending = OriginalBiometricLaunchSlot<BiometricLaunch>()
        private fun requireMain() { check(Looper.myLooper() === Looper.getMainLooper()) }
        fun claim(locator: String?): BiometricLaunch? {
            requireMain()
            return pending.claim(locator)
        }
    }
}
