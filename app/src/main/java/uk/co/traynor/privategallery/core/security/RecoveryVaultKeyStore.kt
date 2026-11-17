package uk.co.traynor.privategallery.core.security

import android.content.Context
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

/** Persists only encrypted envelopes around the existing Primary VDEK. */
class RecoveryVaultKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("vault-recovery-envelope", Context.MODE_PRIVATE)
    private val lifecycle = RecoverySetupLifecycle(FailClosedRecoveryPersistence(preferences, object : RecoveryEnvelopePersistence {
        // SharedPreferences may publish in-memory changes even when disk commit fails.
        // Treat this instance as unavailable after failure, never as confirmed recovery.
        private var writeFailed = false
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
