package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import uk.co.traynor.privategallery.core.crypto.PinEnvelope
import uk.co.traynor.privategallery.core.crypto.PinWrappedKey

/** Persists only the PIN-wrapped vault data-encryption key. */
class PinVaultKeyStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = preferences.contains(SALT)

    fun create(pin: CharArray): ByteArray = synchronized(uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore.gate) {
        val vdek = ByteArray(VAULT_KEY_BYTES)
        try {
            check(!isConfigured) { "Vault already configured" }
            rejectHiddenPin(pin)
            SecureRandom().nextBytes(vdek)
            save(PinEnvelope.create(pin, vdek))
            vdek
        } catch (failure: Throwable) { vdek.fill(0); throw failure }
        finally { pin.fill('\u0000') }
    }

    fun unlock(pin: CharArray): ByteArray = PinEnvelope.unwrap(pin, load())

    fun changePin(oldPin: CharArray, newPin: CharArray) = synchronized(uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore.gate) {
        var vdek: ByteArray? = null
        try {
            val recovered = unlock(oldPin)
            vdek = recovered
            rejectHiddenPin(newPin)
            save(PinEnvelope.create(newPin, recovered))
        } finally { oldPin.fill('\u0000'); newPin.fill('\u0000'); vdek?.fill(0) }
    }

    /** Replaces only the PIN envelope after the same VDEK was recovered offline. */
    fun replacePinForRecoveredVault(newPin: CharArray, vdek: ByteArray) = synchronized(uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore.gate) {
        try {
            rejectHiddenPin(newPin)
            save(PinEnvelope.create(newPin, vdek))
        } finally { newPin.fill('\u0000') }
    }

    private fun rejectHiddenPin(pin: CharArray) {
        check(!uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore(context).pinMatches(pin.copyOf())) {
            "Use a different Primary PIN"
        }
    }

    fun clearFailedRestore() {
        check(preferences.edit().clear().commit()) { "Unable to roll back PIN envelope" }
    }

    private fun load(): PinWrappedKey = PinWrappedKey(
        decode(preferences.getString(SALT, null)),
        decode(preferences.getString(NONCE, null)),
        decode(preferences.getString(CIPHERTEXT, null)),
    )

    private fun save(envelope: PinWrappedKey) {
        check(
            preferences.edit()
                .putString(SALT, encode(envelope.salt))
                .putString(NONCE, encode(envelope.nonce))
                .putString(CIPHERTEXT, encode(envelope.ciphertext))
                .commit(),
        ) { "Unable to persist vault key envelope" }
    }

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
