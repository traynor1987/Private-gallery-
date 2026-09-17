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

    fun newEncryptCipher(): Cipher = cipher(Cipher.ENCRYPT_MODE, key())

    fun saveAuthenticated(cipher: Cipher, vdek: ByteArray) {
        val encrypted = cipher.doFinal(vdek)
        check(preferences.edit()
            .putString(NONCE, encode(cipher.iv))
            .putString(CIPHERTEXT, encode(encrypted))
            .commit()) { "Unable to save biometric vault envelope" }
    }

    fun newDecryptCipher(): Cipher {
        check(isEnabled) { "Biometric unlock is not enabled" }
        return cipher(Cipher.DECRYPT_MODE, key(), decode(preferences.getString(NONCE, null)))
    }

    fun unwrapAuthenticated(cipher: Cipher): ByteArray =
        cipher.doFinal(decode(preferences.getString(CIPHERTEXT, null)))

    fun disable() {
        preferences.edit().remove(NONCE).remove(CIPHERTEXT).commit()
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
