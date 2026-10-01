package uk.co.traynor.privategallery.core.security

import android.content.Context
import java.security.MessageDigest

/** Primary credentials only. Existing envelope encodings and preference names stay unchanged. */
internal class PrimaryKeySlots(context: Context) {
    val containerId = ContainerId.PRIMARY
    internal val pin = PinVaultKeyStore(context)
    internal val recovery = RecoveryVaultKeyStore(context)
    private val biometric = BiometricVaultKeyStore(context)

    fun changePin(operation: PrimaryOperation, oldPin: CharArray, newPin: CharArray) {
        operation.requireScope(PrimaryScope.CREDENTIALS)
        val authenticated = pin.unlock(oldPin.copyOf())
        try {
            check(MessageDigest.isEqual(authenticated, operation.key)) { "Primary slot mismatch" }
            pin.changePin(oldPin, newPin, operation::commit)
        } finally { authenticated.fill(0); oldPin.fill('\u0000'); newPin.fill('\u0000') }
    }

    fun prepareRecovery(operation: PrimaryOperation, restartPending: Boolean): CharArray {
        operation.requireScope(PrimaryScope.CREDENTIALS)
        return if (restartPending) recovery.restartPending(operation.key, operation::commit)
        else recovery.create(operation.key, operation::commit)
    }

    fun confirmRecovery(operation: PrimaryOperation, secret: CharArray) {
        operation.requireScope(PrimaryScope.CREDENTIALS)
        recovery.confirm(secret, operation.key, operation::commit)
    }

    fun enrollBiometric(operation: PrimaryOperation, cipher: javax.crypto.Cipher) {
        operation.requireScope(PrimaryScope.CREDENTIALS)
        operation.commit { biometric.saveAuthenticated(cipher, operation.key) }
    }

    /** Recovery is authentication, not a current unlocked-session lookup. Capture its attempt. */
    fun recover(secret: CharArray, newPin: CharArray, commitAttempt: ((() -> Unit) -> Unit)): ByteArray {
        val recovered = recovery.unlock(secret)
        try {
            commitAttempt { pin.replacePinForRecoveredVault(newPin, recovered); biometric.disable() }
            return recovered
        } catch (failure: Throwable) { recovered.fill(0); throw failure }
    }

    fun recoveryEnvelope(operation: PrimaryOperation): uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey {
        operation.requireScope(PrimaryScope.BACKUP)
        return operation.commit { recovery.exportEnvelope() }
    }
}
