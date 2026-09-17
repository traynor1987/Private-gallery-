package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.util.Base64
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

/** Persists only an authenticated recovery-key envelope around the existing VDEK. */
class RecoveryVaultKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val isConfigured: Boolean get() = preferences.contains(SALT) && preferences.contains(NONCE) && preferences.contains(CIPHERTEXT)

    /** Returns the recovery secret once. It is never persisted by the app. */
    fun create(vdek: ByteArray): CharArray {
        check(!isConfigured) { "Recovery key already configured" }
        val recoveryKey = RecoveryKey.generate()
        val keyForEnvelope = recoveryKey.copyOf()
        try {
            save(RecoveryEnvelope.create(keyForEnvelope, vdek))
        } finally {
            keyForEnvelope.fill('\u0000')
        }
        return recoveryKey
    }

    fun unlock(recoveryKey: CharArray): ByteArray = RecoveryEnvelope.unwrap(recoveryKey, load())

    private fun load(): RecoveryWrappedKey = RecoveryWrappedKey(
        decode(preferences.getString(SALT, null)),
        decode(preferences.getString(NONCE, null)),
        decode(preferences.getString(CIPHERTEXT, null)),
    )

    private fun save(envelope: RecoveryWrappedKey) {
        check(preferences.edit()
            .putString(SALT, encode(envelope.salt))
            .putString(NONCE, encode(envelope.nonce))
            .putString(CIPHERTEXT, encode(envelope.ciphertext))
            .commit()) { "Unable to persist recovery vault envelope" }
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String?): ByteArray {
        check(value != null) { "Recovery key is not configured" }
        return Base64.decode(value, Base64.NO_WRAP)
    }

    private companion object {
        const val PREFERENCES = "vault-recovery-envelope"
        const val SALT = "salt"
        const val NONCE = "nonce"
        const val CIPHERTEXT = "ciphertext"
    }
}
