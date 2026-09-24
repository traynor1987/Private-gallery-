package uk.co.traynor.privategallery.core.editor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Provider adapters obtain credentials here, never from BuildConfig/source or plaintext preferences.
 * The UI intentionally cannot configure an unknown API contract. Files contain only AES-GCM
 * ciphertext in noBackupFilesDir, bound to the adapter ID. Secret buffers are caller-owned and
 * must be wiped after use. No logging, clipboard, broad FileProvider or public-storage path.
 */
class AiCredentialStore(private val context: Context) {
    private fun file(id: String): File {
        require(id.matches(Regex("[a-z0-9-]{1,64}")))
        return File(context.noBackupFilesDir, "ai-provider-$id.enc")
    }
    fun isConfigured(id: String): Boolean = file(id).isFile
    fun save(id: String, credential: ByteArray) {
        require(credential.isNotEmpty() && credential.size <= 8192)
        val destination = file(id)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(id.toByteArray()) }
        val encrypted = cipher.doFinal(credential)
        val atomic = android.util.AtomicFile(destination)
        val stream = atomic.startWrite()
        try { stream.write(cipher.iv); stream.write(encrypted); atomic.finishWrite(stream) }
        catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
    }
    fun read(id: String): ByteArray? {
        val source = file(id)
        if (!source.exists()) return null
        require(source.length() in 29..8220)
        val encrypted = source.readBytes()
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0,12)))
            updateAAD(id.toByteArray()); doFinal(encrypted,12,encrypted.size-12)
        }
    }
    fun clear(id: String) { android.util.AtomicFile(file(id)).delete() }
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    companion object { private const val ALIAS = "private-gallery-ai-provider-v1" }
}
