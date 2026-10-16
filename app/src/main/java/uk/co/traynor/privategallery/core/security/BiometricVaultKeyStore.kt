package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional Android Keystore envelope for the VDEK. The caller must obtain an
 * authorised [Cipher] from BiometricPrompt before encrypting or decrypting.
 */
class BiometricVaultKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = preferences.contains(CIPHERTEXT) && preferences.contains(NONCE)

    fun newEncryptCipher(): Cipher {
        AndroidXBiometricRetirementSupport.requireAdmission()
        return cipher(Cipher.ENCRYPT_MODE, key())
    }

    internal fun persistPrepared(operation: PrimaryOperation, envelope: PreparedBiometricEnvelope) {
        persistPrimaryBiometricPreferences(operation) {
            val encoded = envelope.encoded()
            preferences.edit().putString(NONCE, encoded.first).putString(CIPHERTEXT, encoded.second)
        }
    }

    fun newDecryptCipher(): Cipher {
        AndroidXBiometricRetirementSupport.requireAdmission()
        check(isEnabled) { "Biometric unlock is not enabled" }
        return cipher(Cipher.DECRYPT_MODE, key(), decode(preferences.getString(NONCE, null)))
    }

    fun unwrapAuthenticated(cipher: Cipher): ByteArray =
        cipher.doFinal(decode(preferences.getString(CIPHERTEXT, null)))

    fun disable() {
        withRecoveryDisable { enter, disable -> enter(); disable() }
    }

    /** Claim before the caller's PIN mutation. Wider recovery provider ownership
     * and its existing authority-gated persistence remain Task3B obligations. */
    internal fun withRecoveryDisable(action: (() -> Unit, () -> Unit) -> Unit) {
        withPrimaryBiometricRecoveryDisable(
            { preferences.edit().remove(NONCE).remove(CIPHERTEXT).commit() }, action,
        )
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private fun cipher(mode: Int, key: SecretKey, nonce: ByteArray? = null): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (nonce == null) init(mode, key) else init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
        }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String?): ByteArray {
        check(value != null) { "Missing biometric vault envelope" }
        return Base64.decode(value, Base64.NO_WRAP)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "private_gallery_biometric_v1"
        const val PREFERENCES = "vault-biometric-envelope"
        const val NONCE = "nonce"
        const val CIPHERTEXT = "ciphertext"
        const val TAG_BITS = 128
    }
}

/** Bounded encrypted result, attached before Cipher use. Revocation wipes a late result. */
internal class PreparedBiometricEnvelope : AcknowledgedCloseable {
    private var retired = false
    private var producing = false
    private val done = java.util.concurrent.CompletableFuture<Unit>()
    private var nonce: ByteArray? = null
    private var ciphertext: ByteArray? = null
    fun produce(cipher: Cipher, key: ByteArray) {
        synchronized(this) { check(!retired && !producing && ciphertext == null); producing = true }
        var encrypted: ByteArray? = null
        var iv: ByteArray? = null
        try {
            check(key.size == 32)
            encrypted = ByteArray(48)
            check(cipher.doFinal(key, 0, key.size, encrypted, 0) == 48) { "Invalid biometric ciphertext" }
            iv = cipher.iv
            check(checkNotNull(iv).size == 12) { "Invalid biometric nonce" }
            synchronized(this) {
                check(!retired)
                ciphertext = encrypted; nonce = iv
                encrypted = null; iv = null
            }
        } finally {
            encrypted?.fill(0); iv?.fill(0)
            synchronized(this) { producing = false }
            finishRetirement()
        }
    }
    @Synchronized fun encoded(): Pair<String, String> {
        check(!retired && !producing)
        return Base64.encodeToString(checkNotNull(nonce), Base64.NO_WRAP) to
            Base64.encodeToString(checkNotNull(ciphertext), Base64.NO_WRAP)
    }
    override fun closeAcknowledged(): java.util.concurrent.CompletionStage<Unit> {
        synchronized(this) { retired = true }
        finishRetirement()
        return done
    }
    private fun finishRetirement() {
        val finished = synchronized(this) {
            if (retired && !producing) {
                nonce?.fill(0); ciphertext?.fill(0); nonce = null; ciphertext = null; true
            } else false
        }
        if (finished) done.complete(Unit)
    }
}

/** Exact production enrollment leaf, extracted to exercise Native borrower interleavings. */
internal fun producePrimaryBiometricEnvelope(operation: PrimaryOperation, envelope: PreparedBiometricEnvelope, cipher: Cipher) {
    val temporary = ScopedIoGuard(operation, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
    try {
        // The actual 32-byte snapshot is owned before allocation, copied atomically with
        // original-epoch validation, then borrowed outside the gate until Native returns.
        temporary.withProduced({ operation.commit { operation.key.copyOf() } }) { snapshot ->
            envelope.produce(cipher, snapshot)
        }
    } finally {
        temporary.close()
        try {
            check(temporary.retirement.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
                "Biometric enrollment key retirement incomplete"
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }
}
