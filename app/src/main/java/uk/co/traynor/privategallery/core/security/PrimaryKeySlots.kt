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

    internal fun prepareBiometricEnrollment(operation: PrimaryOperation): OwnedResource<PreparedBiometricEnvelope> =
        ScopedIoGuard(operation, PrimaryScope.CREDENTIALS).createOwned(OwnedResourceManifest.io("biometric-envelope")) {
            val envelope = PreparedBiometricEnvelope()
            attach("biometric-envelope", envelope)
            envelope
        }

    internal fun persistBiometricEnrollment(operation: PrimaryOperation, envelope: PreparedBiometricEnvelope) {
        operation.requireScope(PrimaryScope.CREDENTIALS)
        // Exact original epoch remains the final authorization boundary. SharedPreferences
        // commit is still a Native-under-gate Task3B C obligation; doFinal is outside it.
        operation.commit { biometric.persistPrepared(envelope) }
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
        return readPrimaryRecoveryEnvelope(operation, recovery::exportEnvelope)
    }
}

/** Recovery records contain encrypted key material, never a plaintext key handoff. */
internal fun readPrimaryRecoveryEnvelope(
    operation: PrimaryOperation,
    read: () -> uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey,
): uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey {
    operation.requireNativeOutsideAuthorityGate()
    operation.requireScope(PrimaryScope.BACKUP)
    var original: ReleaseReservation? = null
    var record: uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey? = null
    var failure: Throwable? = null
    try {
        operation.createOwned(OwnedResourceManifest.io("recovery-record-read")) {
            original = this.original
            val invocation = attach("recovery-record-read", PrimaryRecoveryReadInvocation())
            try {
                operation.requireScope(PrimaryScope.BACKUP)
                operation.requireNativeOutsideAuthorityGate()
                record = read()
            } finally { invocation.returned() }
            invocation
        }
    } catch (problem: Throwable) {
        failure = problem
        throw problem
    } finally {
        original?.let { owner ->
            owner.release()
            try {
                check(owner.terminalRetirement.await(20, java.util.concurrent.TimeUnit.SECONDS) && !owner.failed) {
                    "Recovery metadata read retirement incomplete"
                }
            } catch (retirement: Throwable) {
                if (retirement is InterruptedException) Thread.currentThread().interrupt()
                if (failure != null) failure.addSuppressed(retirement) else throw retirement
            }
        }
    }
    // Only the encrypted record crosses this short, original-epoch authorization barrier.
    return operation.commit { checkNotNull(record) }
}

/** Receipt for this synchronous app-controlled invocation, not internal Keystore retirement. */
private class PrimaryRecoveryReadInvocation : AcknowledgedCloseable {
    private val actualReturn = java.util.concurrent.CompletableFuture<Unit>()
    fun returned() { actualReturn.complete(Unit) }
    override fun closeAcknowledged(): java.util.concurrent.CompletionStage<Unit> = actualReturn
}
