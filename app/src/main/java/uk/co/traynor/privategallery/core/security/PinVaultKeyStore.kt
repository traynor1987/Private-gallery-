package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import uk.co.traynor.privategallery.core.crypto.PinEnvelope
import uk.co.traynor.privategallery.core.crypto.PinWrappedKey

/** Persists only the PIN-wrapped vault data-encryption key. */
class PinVaultKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val filesDir = context.filesDir

    val hasEnvelopeMaterial: Boolean
        get() = listOf(SALT, NONCE, CIPHERTEXT).any(preferences::contains)

    val isConfigured: Boolean
        get() = !PrimaryVaultSetupGuard.canCreate(filesDir, hasEnvelopeMaterial)

    fun create(pin: CharArray): ByteArray = synchronized(preferences) {
        check(!isConfigured) { "Vault already configured" }
        val vdek = ByteArray(VAULT_KEY_BYTES).also(SecureRandom()::nextBytes)
        try {
            val envelope = PinEnvelope.create(pin, vdek)
            check(!isConfigured) { "Vault setup admission changed" }
            save(envelope)
            vdek
        } catch (failure: Throwable) { vdek.fill(0); throw failure }
    }

    fun unlock(pin: CharArray): ByteArray = synchronized(preferences) { PinEnvelope.unwrap(pin, load()) }

    fun changePin(oldPin: CharArray, newPin: CharArray) = synchronized(preferences) {
        save(PinEnvelope.changePin(oldPin, newPin, load()))
    }

    /** Replaces only the PIN envelope after the same VDEK was recovered offline. */
    fun replacePinForRecoveredVault(newPin: CharArray, vdek: ByteArray) = synchronized(preferences) {
        save(PinEnvelope.create(newPin, vdek))
    }

    /** Fresh archive installation never overwrites another credential flow's envelope.
     * The rollback capability can remove only the exact bytes installed by this call. */
    fun installPinForRestoredVault(newPin: CharArray, vdek: ByteArray): () -> Unit = synchronized(preferences) {
        check(!hasEnvelopeMaterial) { "PIN envelope changed during restore" }
        val envelope = PinEnvelope.create(newPin, vdek)
        val installed = encoded(envelope)
        try { save(envelope) }
        catch (failure: Throwable) {
            clearIfOwned(installed)
            throw failure
        }
        val rollback: () -> Unit = { synchronized(preferences) { clearIfOwned(installed) } }
        rollback
    }

    private fun clearIfOwned(installed: Map<String, String>) {
        if (preferences.all == installed) {
            check(preferences.edit().clear().commit()) { "Unable to roll back owned PIN envelope" }
        }
    }

    private fun load(): PinWrappedKey = PinWrappedKey(
        decode(preferences.getString(SALT, null)),
        decode(preferences.getString(NONCE, null)),
        decode(preferences.getString(CIPHERTEXT, null)),
    )

    private fun save(envelope: PinWrappedKey) {
        val record = encoded(envelope)
        check(
            preferences.edit()
                .putString(SALT, record.getValue(SALT))
                .putString(NONCE, record.getValue(NONCE))
                .putString(CIPHERTEXT, record.getValue(CIPHERTEXT))
                .commit(),
        ) { "Unable to persist vault key envelope" }
    }

    private fun encoded(envelope: PinWrappedKey) = mapOf(
        SALT to encode(envelope.salt), NONCE to encode(envelope.nonce), CIPHERTEXT to encode(envelope.ciphertext),
    )

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String?): ByteArray {
        check(value != null) { "Vault is not configured" }
        return Base64.decode(value, Base64.NO_WRAP)
    }

    private companion object {
        const val PREFERENCES = "vault-key-envelope"
        const val SALT = "salt"
        const val NONCE = "nonce"
        const val CIPHERTEXT = "ciphertext"
        const val VAULT_KEY_BYTES = 32
    }
}
