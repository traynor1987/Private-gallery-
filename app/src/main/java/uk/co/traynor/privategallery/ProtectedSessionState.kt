package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import uk.co.traynor.privategallery.core.security.*

/** In-process only: never saved to a Bundle, disk or process-death restoration. */
class ProtectedSessionState : ViewModel() {
    internal val hidden = uk.co.traynor.privategallery.ui.HiddenVaultState()
    internal val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    @Volatile internal var key: ByteArray? = null
    internal var automaticBiometricPromptAttempted = false
    internal var route = Route.LOCK
    internal var afterAuthentication = Route.VAULT
    internal var previewConcealmentEpoch by androidx.compose.runtime.mutableIntStateOf(0)
    internal var homeVisible by androidx.compose.runtime.mutableStateOf(false)
    internal var homePrimaryAuthentication:Long?=null
    internal var homeHiddenAuthentication:uk.co.traynor.privategallery.core.hidden.HiddenSession.Authentication?=null
    internal var primaryGallery by androidx.compose.runtime.mutableStateOf(false)
    override fun onCleared() { hidden.leave(); key?.fill(0); key = null; session.lock() }
}
