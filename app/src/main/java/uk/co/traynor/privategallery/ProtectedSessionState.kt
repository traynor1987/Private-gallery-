package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModel
import uk.co.traynor.privategallery.core.security.*

/** In-process only: never saved to a Bundle, disk or process-death restoration. */
class ProtectedSessionState : ViewModel() {
    internal val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    internal var key: ByteArray? = null
    internal var route = Route.LOCK
    override fun onCleared() { key?.fill(0); key = null; session.lock() }
}
