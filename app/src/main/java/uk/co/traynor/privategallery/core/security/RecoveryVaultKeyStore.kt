package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.content.SharedPreferences
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

/** Persists only encrypted envelopes around the existing Primary VDEK. */
class RecoveryVaultKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("vault-recovery-envelope", Context.MODE_PRIVATE)
    private val lifecycle = RecoverySetupLifecycle(FailClosedRecoveryPersistence(preferences, object : RecoveryEnvelopePersistence {
        // SharedPreferences may publish in-memory changes even when disk commit fails.
        // Treat this instance as unavailable after failure, never as confirmed recovery.
        @Volatile private var writeFailed = false
        override val hasReadFailure: Boolean get() = writeFailed
        override fun read(): Map<String, String> = synchronized(preferences) {
            if (writeFailed) return@synchronized mapOf("write_failure" to "unavailable")
            val all = preferences.all
            if (all.values.any { it !is String }) return@synchronized mapOf("invalid_type" to "unavailable")
            all.mapValues { it.value as String }
        }
        override fun commit(expected: Map<String, String>, values: Map<String, String>): Boolean = synchronized(preferences) {
            if (writeFailed || read() != expected) return@synchronized false
            val editor = preferences.edit().clear()
            values.forEach { (name, value) -> editor.putString(name, value) }
            val committed = editor.commit()
            if (!committed) writeFailed = true
            committed
        }
    }))

    /** Fixed store source only. No caller-selected preference/persistence handle. */
    internal val primaryRecordSource: ConfirmedRecordSource get() = ConfirmedRecordSource.primaryStore(this)

    /** Ciphertext-only preparatory read; this does not authorize a credential write
     * or authenticate possession. The following recovery producer must retain its
     * exact original and independently fund all decoding/KDF/Cipher children. */
    internal class ConfirmedRecordSource private constructor(
        private val preferences: SharedPreferences,
        private val lifecycle: RecoverySetupLifecycle,
    ) {
        companion object {
            fun primaryStore(store: RecoveryVaultKeyStore) = ConfirmedRecordSource(store.preferences, store.lifecycle)
        }
        private fun requireOutside(authority: PrimarySessionAuthority) {
            authority.requireNativeOutsideGate()
            check(!Thread.holdsLock(preferences)) { "Recovery read under preference monitor" }
            check(!Thread.holdsLock(PrimaryVaultSetupGuard.storageLock)) { "Recovery read under storage gate" }
            lifecycle.requirePrimaryRecordReadOutside()
        }
        fun readConfirmed(authority: PrimarySessionAuthority, attempt: PrimaryAuthAttempt): OwnedResource<PreparedPrimaryRecoveryRecord> {
            requireOutside(authority)
            try {
                return authority.createAttemptOwned(attempt, OwnedResourceManifest.io("record", "invocation")) {
                    val output = PreparedPrimaryRecoveryRecord(); val invocation = PinCall()
                    try {
                        attach("record", output); attach("invocation", invocation)
                        authority.checkPreparation(attempt); original.requireNativeUse(0, output)
                        lifecycle.requirePrimaryRecordReadAllowed()
                        val values = preferences.all
                        lifecycle.requirePrimaryRecordReadAllowed()
                        authority.checkPreparation(attempt)
                        output.bind(classifyPrimaryRecoveryRecord(values))
                    } finally { output.returned(); invocation.returned() }
                    retireChildren("invocation")
                    lifecycle.requirePrimaryRecordReadAllowed()
                    authority.checkPreparation(attempt); original.requireNativeUse(0, output)
                    output
                }
            } catch (failure: Throwable) {
                if (failure is InterruptedException) Thread.currentThread().interrupt()
                throw failure
            }
        }
    }

    val setupState: RecoverySetupState get() = lifecycle.setupState
    val isConfigured: Boolean get() = setupState == RecoverySetupState.CONFIRMED
    val isLegacyExisting: Boolean get() = lifecycle.isLegacyExisting
    val isPossessionVerified: Boolean get() = lifecycle.isPossessionVerified

    fun create(vdek: ByteArray, commit: (() -> Unit) -> Unit = { it() }): CharArray = lifecycle.create(vdek, commit)
    fun restartPending(vdek: ByteArray, commit: (() -> Unit) -> Unit = { it() }): CharArray = lifecycle.restartPending(vdek, commit)
    fun confirm(secret: CharArray, expectedVdek: ByteArray, commit: (() -> Unit) -> Unit = { it() }) = lifecycle.confirm(secret, expectedVdek, commit)
    fun unlock(recoveryKey: CharArray): ByteArray = lifecycle.unlock(recoveryKey)
    fun exportEnvelope(): RecoveryWrappedKey = lifecycle.exportEnvelope()
    fun installForRestoredVault(envelope: RecoveryWrappedKey) = lifecycle.installForRestoredVault(envelope)
    fun clearFailedRestore() = lifecycle.clearFailedRestore()
}
