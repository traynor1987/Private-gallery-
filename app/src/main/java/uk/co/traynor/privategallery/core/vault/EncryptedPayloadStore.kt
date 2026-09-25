package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import uk.co.traynor.privategallery.core.crypto.VaultCipher

data class StoredPayload(
    val id: String,
    val file: File,
    val plaintextSize: Long,
    val plaintextSha256: ByteArray,
    val nonce: ByteArray,
)

/**
 * App-private encrypted payload storage.  Staging files contain ciphertext
 * only; a payload is promoted only after an authenticated decrypt/read-back
 * reproduces the original size and digest.
 */
class EncryptedPayloadStore(
    private val root: File,
    private val cipher: VaultCipher = VaultCipher,
    private val syncOutput: (FileOutputStream) -> Unit = { it.fd.sync() },
) {
    private val payloads = File(root, "payloads")
    private val staging = File(root, "staging")

    fun writeAndVerify(id: String, source: InputStream, key: ByteArray): StoredPayload {
        require(ID_PATTERN.matches(id)) { "Invalid vault item id" }
        payloads.mkdirs()
        staging.mkdirs()
        val temporary = File(staging, "$id.part")
        val destination = File(payloads, "$id.vault")
        check(!destination.exists()) { "Vault payload already exists" }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        var nonce: ByteArray? = null

        try {
            FileOutputStream(temporary).use { output ->
                CountingDigestInputStream(source, digest).use { input ->
                    val header = cipher.encrypt(input, output, key, id.encodeToByteArray())
                    size = input.count
                    nonce = header.nonce
                }
                syncOutput(output)
            }
            val stored = StoredPayload(id, temporary, size, digest.digest(), checkNotNull(nonce))
            check(verify(stored, key)) { "Encrypted payload verification failed" }
            check(temporary.renameTo(destination)) { "Unable to promote verified vault payload" }
            return stored.copy(file = destination)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    fun verify(stored: StoredPayload, key: ByteArray): Boolean = try {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        FileInputStream(stored.file).use { encrypted ->
            cipher.decrypt(
                encrypted,
                DigestOutputStream(digest) { count -> size += count },
                key,
                stored.id.encodeToByteArray(),
                uk.co.traynor.privategallery.core.crypto.EncryptionHeader(stored.nonce),
            )
        }
        size == stored.plaintextSize && digest.digest().contentEquals(stored.plaintextSha256)
    } catch (_: Throwable) {
        false
    }

    fun decryptToBytes(stored: StoredPayload, key: ByteArray, isCancelled: () -> Boolean = { false }): ByteArray =
        decryptToBoundedBytes(stored, key, Int.MAX_VALUE, isCancelled)

    fun decryptWithProgress(stored: StoredPayload, key: ByteArray, isCancelled: () -> Boolean, onProgress: (Int) -> Unit): ByteArray =
        decryptExactly(stored, key, Int.MAX_VALUE, isCancelled, onProgress)

    /** One exact-size plaintext buffer; never publish it before GCM authentication succeeds. */
    fun decryptToBoundedBytes(stored: StoredPayload, key: ByteArray, maxBytes: Int, isCancelled: () -> Boolean): ByteArray =
        decryptExactly(stored, key, maxBytes, isCancelled) {}

    private fun decryptExactly(stored: StoredPayload, key: ByteArray, maxBytes: Int, isCancelled: () -> Boolean, onProgress: (Int) -> Unit): ByteArray {
        require(stored.plaintextSize in 0..maxBytes.toLong()) { "Media exceeds the in-memory size limit" }
        if (isCancelled()) throw java.io.IOException("Media read cancelled")
        val plain = ByteArray(stored.plaintextSize.toInt())
        var position = 0
        var consumed = 0L
        var reported = -1
        val encryptedSize = stored.file.length().coerceAtLeast(1)
        fun report(value: Int) { if (value != reported) { reported = value; onProgress(value) } }
        try {
            report(0)
            FileInputStream(stored.file).use { encrypted ->
                val sink = object : OutputStream() {
                    override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                    override fun write(buffer: ByteArray, offset: Int, length: Int) {
                        if (isCancelled()) throw java.io.IOException("Media read cancelled")
                        if (position.toLong() + length > plain.size) throw java.io.IOException("Invalid media length")
                        buffer.copyInto(plain, position, offset, offset + length); position += length
                    }
                }
                val cancellable = object : java.io.FilterInputStream(encrypted) {
                    private fun checkActive() { if (isCancelled()) throw java.io.IOException("Media read cancelled") }
                    override fun read(): Int {
                        checkActive()
                        return `in`.read().also { if (it >= 0) { consumed++; report((consumed * 100 / encryptedSize).toInt().coerceIn(0, 99)) } }
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        checkActive()
                        return `in`.read(buffer, offset, length).also { if (it > 0) {
                            consumed += it
                            report((consumed * 100 / encryptedSize).toInt().coerceIn(0, 99))
                        } }
                    }
                }
                cipher.decrypt(cancellable, sink, key, stored.id.encodeToByteArray(), uk.co.traynor.privategallery.core.crypto.EncryptionHeader(stored.nonce))
            }
            if (isCancelled()) throw java.io.IOException("Media read cancelled")
            check(position == plain.size) { "Incomplete media" }
            report(100)
            return plain
        } catch (failure: Throwable) { plain.fill(0); throw failure }
    }

    /** Atomically moves a ciphertext out of its live name before index mutation. */
    fun retireForDeletion(id: String): File {
        require(ID_PATTERN.matches(id)) { "Invalid vault item id" }
        val source = File(payloads, "$id.vault")
        val retired = File(payloads, "$id.deleting")
        check(source.exists()) { "Vault payload is missing" }
        check(!retired.exists()) { "Vault deletion is already in progress" }
        check(source.renameTo(retired)) { "Unable to stage encrypted vault payload for deletion" }
        return retired
    }

    fun restoreRetiredPayload(id: String): Boolean {
        val retired = File(payloads, "$id.deleting")
        if (!retired.exists()) return true
        val source = File(payloads, "$id.vault")
        return !source.exists() && retired.renameTo(source)
    }

    /** Removes only incomplete ciphertext staging files; it never touches sources. */
    fun reconcileInterruptedWrites() {
        staging.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /**
     * A retained index record wins over a staged deletion. If the index was
     * committed without the record, a leftover ciphertext is safe to remove.
     */
    fun reconcileInterruptedDeletes(indexedIds: Set<String>) {
        payloads.listFiles()?.filter { it.isFile && it.name.endsWith(".deleting") }?.forEach { retired ->
            val id = retired.name.removeSuffix(".deleting")
            if (id in indexedIds) restoreRetiredPayload(id) else retired.delete()
        }
    }

    private class CountingDigestInputStream(input: InputStream, digest: MessageDigest) :
        java.security.DigestInputStream(input, digest) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) count += it }
    }

    private class DigestOutputStream(
        private val digest: MessageDigest,
        private val onBytes: (Long) -> Unit,
    ) : OutputStream() {
        override fun write(value: Int) {
            digest.update(value.toByte())
            onBytes(1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            digest.update(buffer, offset, length)
            onBytes(length.toLong())
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,120}")
    }
}
