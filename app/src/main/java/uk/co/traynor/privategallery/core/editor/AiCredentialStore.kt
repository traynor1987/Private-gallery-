package uk.co.traynor.privategallery.core.editor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Provider adapters obtain credentials here, never from BuildConfig/source or plaintext preferences.
 * Owner setup verifies the documented provider before saving. Files contain only AES-GCM
 * ciphertext in noBackupFilesDir, bound to the adapter ID. Secret buffers are caller-owned and
 * must be wiped after use. No logging, clipboard, broad FileProvider or public-storage path.
 */
class AiCredentialStore private constructor(private val context: Context, private val alias: String) {
    constructor(context: Context) : this(context, ALIAS)
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
    fun clear(id: String) {
        android.util.AtomicFile(file(id)).delete()
        check(!file(id).exists()) { "Unable to remove provider configuration." }
    }
    /** Evidence-only two-phase capability. Callers serialize the canonical owner/file.
     * commit does bounded same-directory renames only; Keystore, file writes/fsync,
     * directory fsync and ciphertext deletion MUST run outside authentication monitors.
     * The public save/read/clear API, alias, filenames and AES-GCM AAD remain unchanged.
     */
    internal interface PreparedChange : AutoCloseable {
        fun commit()
        fun finish()
    }
    internal fun stageEvidence(id: String, credential: ByteArray): PreparedChange {
        requireEvidence(id)
        require(credential.isNotEmpty() && credential.size <= 8192)
        val destination = file(id)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD(id.toByteArray())
        }
        val encrypted = cipher.doFinal(credential)
        var stage: File? = null
        try {
            val prepared = File.createTempFile(destination.name + ".stage-", ".enc", destination.parentFile)
            stage = prepared
            FileOutputStream(prepared).use { stream ->
                stream.write(cipher.iv); stream.write(encrypted); stream.flush(); stream.fd.sync()
            }
            return EvidenceChange(destination, prepared)
        } catch (failure: Throwable) {
            stage?.let { if (!it.delete() && it.exists()) failure.addSuppressed(IOException("Unable to clean credential stage.")) }
            throw failure
        } finally { encrypted.fill(0) }
    }
    internal fun removeEvidence(id: String): PreparedChange {
        requireEvidence(id)
        return EvidenceChange(file(id), null)
    }
    private fun requireEvidence(id: String) {
        require(id == "evidence-openai-179-primary" || id == "evidence-openai-179-hidden")
    }
    private class EvidenceChange(private val destination: File, private val stage: File?) : PreparedChange {
        private val displaced = mutableListOf<Pair<File, File>>()
        // Allocate random names before entering the caller's authentication boundary.
        private val candidates = (listOf(File(destination.path + ".bak"), File(destination.path + ".new")) +
            if (stage == null) listOf(destination) else emptyList()).map { source ->
            source to File(destination.parentFile, destination.name + ".retired-" + UUID.randomUUID())
        }
        private var committed = false
        private var attempted = false
        override fun commit() {
            check(!attempted); attempted = true
            try {
                // Quarantine AtomicFile recovery/staging companions as well, so
                // no later AtomicFile recovery can resurrect removed/replaced bytes.
                for ((source, tombstone) in candidates) {
                    if (source.exists()) {
                        android.system.Os.rename(source.path, tombstone.path)
                        displaced.add(source to tombstone)
                    }
                }
                // Atomic replace: all ciphertext writes and fd.sync already finished.
                if (stage != null) android.system.Os.rename(stage.path, destination.path)
                committed = true
            } catch (failure: Throwable) {
                for ((source, tombstone) in displaced.asReversed()) {
                    try { android.system.Os.rename(tombstone.path, source.path) }
                    catch (rollback: Throwable) { failure.addSuppressed(rollback) }
                }
                throw IOException("Credential transition failed; check configuration before retry.", failure)
            }
        }
        override fun finish() {
            check(committed)
            try {
                syncDirectory(checkNotNull(destination.parentFile))
                cleanOwnedCiphertext()
                syncDirectory(checkNotNull(destination.parentFile))
            } catch (failure: Throwable) {
                throw IOException("Credential changed; durability or cleanup is uncertain. Check configuration before retry.", failure)
            }
        }
        private fun cleanOwnedCiphertext() {
            // A failed rollback may leave the only old ciphertext in a tombstone.
            // Preserve it for recovery; it is never considered configured/current.
            val retired = if (committed) displaced.map { it.second } else emptyList()
            for (owned in retired + listOfNotNull(stage)) {
                if (!owned.delete() && owned.exists()) throw IOException("Unable to clean retired credential ciphertext.")
            }
        }
        override fun close() { cleanOwnedCiphertext() }
    }
    private fun key(): SecretKey = synchronized(KEY_GATE) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // All consumers share this alias, including separate owner/provider/store
        // instances. Lookup and first creation must use the same process-wide fence.
        // An existing inaccessible/wrong-type entry fails closed; never replace it.
        if (store.containsAlias(alias)) {
            return@synchronized (store.getKey(alias, null) as? SecretKey
                ?: throw IllegalStateException("Credential protection key is unavailable."))
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    companion object {
        private const val ALIAS = "private-gallery-ai-provider-v1"
        private val KEY_GATE = Any()
        private fun syncDirectory(directory: File) {
            val fd = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY or android.system.OsConstants.O_DIRECTORY, 0)
            try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
        }
    }
}
