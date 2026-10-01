package uk.co.traynor.privategallery

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.security.*

/** In-process only: never saved to a Bundle, disk or process-death restoration. */
class ProtectedSessionState : ViewModel() {
    internal val authenticationAttempts = AuthenticationAttemptAuthority()
    internal val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    internal val authority = PrimarySessionAuthority { SystemClock.elapsedRealtime() }
    internal var route = Route.LOCK
    private var backgroundTimer: Job? = null

    internal fun onBackgrounded(timeoutMillis: Long) {
        authority.onBackgrounded(timeoutMillis)
        if (backgroundTimer?.isActive == true) return
        if (timeoutMillis > 0) backgroundTimer = viewModelScope.launch {
            delay(timeoutMillis)
            authority.expireIfNeeded()
            val check = authority.operationOrNull()
            if (check == null) { session.lock() } else check.close()
        }
        else { session.lock() }
    }

    internal fun onForegrounded() {
        backgroundTimer?.cancel(); backgroundTimer = null
        authority.onForegrounded()
        val check = authority.operationOrNull()
        if (check == null) { session.lock() } else check.close()
    }

    override fun onCleared() {
        authenticationAttempts.revoke()
        backgroundTimer?.cancel(); backgroundTimer = null
        authority.revoke()
        session.lock()
    }
}
