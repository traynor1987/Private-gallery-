package uk.co.traynor.privategallery.core.sanctuary

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** The platform binds this service only after the owner enables it in Accessibility settings. */
class SanctuaryAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        SanctuaryAccessibility.connect(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !SanctuaryAccessibility.canObserve(this)) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        // Never access source, node trees, content, text or input events.
        SanctuaryAccessibility.observe(this, packageName, event.eventTime)
    }

    override fun onInterrupt() {
        SanctuaryAccessibility.clearObservation(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        SanctuaryAccessibility.disconnect(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        SanctuaryAccessibility.disconnect(this)
        super.onDestroy()
    }
}

/** Single current observation only. No collection while the Guardian is unarmed. */
internal object SanctuaryAccessibility {
    data class Window(val packageName: String, val uptime: Long)

    @Volatile private var service: SanctuaryAccessibilityService? = null
    private var armedSince: Long? = null
    private var window: Window? = null
    private val transitions = Channel<Unit>(Channel.CONFLATED)

    val connected: Boolean get() = service != null

    fun connect(owner: SanctuaryAccessibilityService) {
        service = owner
        window = null
    }

    fun disconnect(owner: SanctuaryAccessibilityService) {
        if (service === owner) {
            window = null
            service = null
        }
    }

    fun setArmed(armed: Boolean) {
        if (armed && armedSince != null) return
        armedSince = if (armed) SystemClock.uptimeMillis() else null
        window = null
    }

    fun clearObservation(owner: SanctuaryAccessibilityService) {
        if (service === owner) window = null
    }

    fun canObserve(owner: SanctuaryAccessibilityService): Boolean = service === owner && armedSince != null

    fun signalPolicyChange() {
        transitions.trySend(Unit)
    }

    suspend fun awaitTransition(timeout: Long) {
        withTimeoutOrNull(timeout) { transitions.receive() }
    }

    fun observe(owner: SanctuaryAccessibilityService, packageName: String, uptime: Long) {
        if (service !== owner || uptime < (armedSince ?: return)) return
        if (uptime >= (window?.uptime ?: Long.MIN_VALUE)) {
            val changed = window?.packageName != packageName
            window = Window(packageName, uptime)
            if (changed) transitions.trySend(Unit)
        }
    }

    fun recent(now: Long = SystemClock.uptimeMillis()): Window? =
        window?.takeIf { now - it.uptime in 0..OBSERVATION_MAX_AGE_MILLIS }

    fun requestHome(expected: Window): Boolean {
        val owner = service ?: return false
        if (armedSince == null || recent() != expected) return false
        return try {
            owner.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    private const val OBSERVATION_MAX_AGE_MILLIS = 2_000L
}
