package uk.co.traynor.privategallery.ui

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.Decision
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.Observation
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.State

/** Only the external Android boundary is replaceable in component tests. */
internal interface NovaScreenPinningPlatform {
    fun state(): State
    fun deviceLocked(): Boolean?
    fun managedLockTaskPermitted(): Boolean?
    fun requestPinning()
    fun openSettings(action: String)
}

/** Public SDK only. NONE is current inactivity, never a probe for Android's global switch. */
internal class AndroidNovaScreenPinningPlatform(private val activity: ComponentActivity) : NovaScreenPinningPlatform {
    override fun state(): State = when (activity.getSystemService(ActivityManager::class.java)?.lockTaskModeState) {
        ActivityManager.LOCK_TASK_MODE_NONE -> State.NONE
        ActivityManager.LOCK_TASK_MODE_PINNED -> State.PINNED
        ActivityManager.LOCK_TASK_MODE_LOCKED -> State.LOCKED
        else -> State.UNKNOWN
    }
    override fun deviceLocked(): Boolean? = activity.getSystemService(KeyguardManager::class.java)?.let {
        it.isDeviceLocked || it.isKeyguardLocked
    }
    override fun managedLockTaskPermitted(): Boolean? =
        activity.getSystemService(DevicePolicyManager::class.java)?.isLockTaskPermitted(activity.packageName)
    override fun requestPinning() = activity.startLockTask()
    override fun openSettings(action: String) = activity.startActivity(Intent(action))
}

/** The original immutable auth/Space guard is retained for this controller's entire lifetime.
 * Lifecycle/focus callbacks observe only: they cannot initiate or restore a pinning request. */
internal class NovaScreenPinningController(
    private val activity: ComponentActivity,
    private val current: () -> Boolean,
    private val platform: NovaScreenPinningPlatform = AndroidNovaScreenPinningPlatform(activity),
) : AutoCloseable {
    private var closed = false
    var observation by mutableStateOf(Observation(State.UNKNOWN, false, false, null, null))
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val decision: Decision get() = NovaScreenPinningPolicy.decide(observation)
    val canOpenSettings: Boolean get() = observation.let {
        !closed && it.current && it.foreground && it.deviceLocked == false &&
            it.state != State.LOCKED && it.state != State.PINNED
    }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> refresh()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { refresh() }

    init {
        requireMainThread()
        activity.lifecycle.addObserver(lifecycleObserver)
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        refresh()
    }

    fun refresh() {
        requireMainThread()
        if (closed) return
        val state = readOrNull(platform::state) ?: State.UNKNOWN
        val locked = readOrNull(platform::deviceLocked)
        val managed = readOrNull(platform::managedLockTaskPermitted)
        // Read authority after the Binder calls as well as immediately before each OS action.
        observation = Observation(state, authorized(), foreground(), locked, managed)
    }

    /** True means a request was issued; only a subsequent Android observation proves PINNED. */
    fun requestPinning(): Boolean {
        requireMainThread()
        if (closed) return false
        error = null
        refresh()
        if (decision != Decision.REQUEST || !authorized() || !foreground()) return false
        return try {
            platform.requestPinning()
            refresh()
            true
        } catch (_: RuntimeException) {
            error = "Android could not start screen pinning."
            refresh()
            false
        }
    }

    fun openAndroidSettings(): Boolean {
        requireMainThread()
        if (closed) return false
        error = null
        for (action in listOf(Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS)) {
            refresh()
            if (!canOpenSettings || !authorized() || !foreground()) return false
            try {
                platform.openSettings(action)
                return true
            } catch (_: ActivityNotFoundException) {
                // OEMs can omit the specific security Activity; try the public top-level action.
            } catch (_: SecurityException) {
                // Android/device policy remains authoritative; the fallback may also be refused.
            }
        }
        error = "Android Settings could not be opened."
        return false
    }

    override fun close() {
        requireMainThread()
        if (closed) return
        closed = true
        activity.lifecycle.removeObserver(lifecycleObserver)
        activity.window.decorView.viewTreeObserver.takeIf { it.isAlive }
            ?.removeOnWindowFocusChangeListener(focusListener)
        observation = observation.copy(current = false, foreground = false)
    }

    private fun authorized(): Boolean = !closed && readOrNull(current) == true
    private fun foreground(): Boolean = !closed && !activity.isFinishing && !activity.isDestroyed &&
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus()
    private fun <T> readOrNull(read: () -> T): T? = try { read() } catch (_: RuntimeException) { null }
    private fun requireMainThread() = check(Looper.myLooper() === Looper.getMainLooper())
}
