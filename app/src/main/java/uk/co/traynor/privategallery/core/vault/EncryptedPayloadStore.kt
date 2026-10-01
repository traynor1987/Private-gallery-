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
    private val faults: PrimaryWriteFaults = PrimaryWriteFaults.NONE,
    private val registerResource: (AutoCloseable) -> Unit = {},
) {
    private val payloads = File(root, "payloads")
    private val staging = File(root, "staging")

    fun writeAndVerify(id: String, source: InputStream, key: ByteArray, chunkedVideo: Boolean = false, commit: ((() -> Unit) -> Unit) = { it() }): StoredPayload {
        require(ID_PATTERN.matches(id)) { "Invalid vault item id" }
        if (chunkedVideo) return ChunkedVaultVideoStore.writeAndVerify(id, source, key, root, faults = faults, commit = commit, registerResource = registerResource)
        payloads.mkdirs()
        staging.mkdirs()
        val temporary = File(staging, "$id.part")
        val destination = File(payloads, "$id.vault")
        check(!destination.exists()) { "Vault payload already exists" }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        var nonce: ByteArray? = null

        try {
            faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_WRITE, temporary)
            FileOutputStream(temporary).use { output ->
                CountingDigestInputStream(source, digest).use { input ->
                    val header = cipher.encrypt(input, output, key, id.encodeToByteArray())
                    size = input.count
                    nonce = header.nonce
                }
                faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_WRITE, temporary)
                faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_SYNC, temporary)
                syncOutput(output)
                faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_SYNC, temporary)
            }
            val stored = StoredPayload(id, temporary, size, digest.digest(), checkNotNull(nonce))
            faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_VERIFY, temporary)
            check(verify(stored, key)) { "Encrypted payload verification failed" }
            faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_VERIFY, temporary)
            var promoted = false
            commit {
                faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_PROMOTION, temporary)
                check(!destination.exists()) { "Vault payload already exists" }
                check(temporary.renameTo(destination)) { "Unable to promote verified vault payload" }
                promoted = true
                faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_PROMOTION, destination)
            }
            check(promoted) { "Verified vault payload commit did not execute" }
            return stored.copy(file = destination)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    fun verify(stored: StoredPayload, key: ByteArray): Boolean {
        return try {
        if (ChunkedVaultVideoStore.isChunked(stored.file)) registered(ChunkedVaultVideoStore.open(stored, key)).use { it.verifyAll() }
        else {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        registered(FileInputStream(stored.file)).use { encrypted ->
            cipher.decrypt(
                encrypted,
                DigestOutputStream(digest) { count -> size += count },
                key,
                stored.id.encodeToByteArray(),
                uk.co.traynor.privategallery.core.crypto.EncryptionHeader(stored.nonce),
            )
        }
        size == stored.plaintextSize && digest.digest().contentEquals(stored.plaintextSha256)
        }
    } catch (_: Throwable) {
        false
    }
    }

    /** Re-encrypt a legacy video through a bounded pipe; no plaintext file is ever created. */
    fun migrateLegacyVideo(stored: StoredPayload, key: ByteArray, isCancelled: () -> Boolean): StoredPayload =
        migrateLegacyVideo(stored, key, isCancelled, commit = { it() })

    fun migrateLegacyVideo(stored: StoredPayload, key: ByteArray, isCancelled: () -> Boolean,
                           commit: ((() -> Unit) -> Unit)): StoredPayload {
        if (isCancelled()) throw java.io.IOException("Video migration cancelled")
        if (ChunkedVaultVideoStore.isChunked(stored.file)) return stored
        val migration = File(root, "video-migration-${stored.id}")
        check(!migration.exists()) { "Interrupted video migration needs reconciliation" }
        val input = registered(java.io.PipedInputStream(64 * 1024))
        val output = try { registered(java.io.PipedOutputStream(input)) } catch (failure: Throwable) { input.close(); throw failure }
        val producerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val producer = Thread({
            try {
                registered(FileInputStream(stored.file)).use { encrypted ->
                    output.use { plain ->
                        cipher.decrypt(encrypted, object : OutputStream() {
                            override fun write(value: Int) { if (isCancelled()) throw java.io.IOException("Video migration cancelled"); plain.write(value) }
                            override fun write(bytes: ByteArray, off: Int, len: Int) {
                                if (isCancelled()) throw java.io.IOException("Video migration cancelled")
                                plain.write(bytes, off, len)
                            }
                        }, key, stored.id.encodeToByteArray(), uk.co.traynor.privategallery.core.crypto.EncryptionHeader(stored.nonce))
                    }
                }
            } catch (failure: Throwable) { producerFailure.set(failure); runCatching { output.close() } }
        }, "Vault-video-migration").apply { start() }
        try {
            val migrated = input.use { ChunkedVaultVideoStore.writeAndVerify(stored.id, it, key, migration, stored.nonce, faults = faults, registerResource = registerResource) }
            producer.join()
            producerFailure.get()?.let { throw java.io.IOException("Legacy video authentication failed", it) }
            check(!isCancelled() && migrated.plaintextSize == stored.plaintextSize &&
                MessageDigest.isEqual(migrated.plaintextSha256, stored.plaintextSha256)) { "Video migration verification failed" }
            val retired = File(stored.file.parentFile, "${stored.id}.legacy")
            var installed = false
            var retiredOwned = false
            try {
                commit {
                    check(!isCancelled()) { "Video migration cancelled" }
                    check(!retired.exists() && stored.file.renameTo(retired)) { "Unable to stage legacy video" }
                    retiredOwned = true
                    check(migrated.file.renameTo(stored.file)) { "Unable to install chunked video" }
                    installed = true
                }
                check(installed) { "Video migration commit did not execute" }
                check(!isCancelled() && verify(stored, key)) { "Chunked video authentication failed" }
                var retiredDeleted = false
                commit {
                    check(!isCancelled()) { "Video migration cancelled" }
                    check(retired.delete()) { "Unable to retire verified legacy video" }
                    retiredDeleted = true
                }
                check(retiredDeleted) { "Video migration retirement did not execute" }
            } catch (failure: Throwable) {
                // Safe ciphertext rollback is permitted even after the operation is revoked.
                // If retirement already ran, keep the verified installed copy instead.
                if (retiredOwned && retired.exists()) {
                    stored.file.delete()
                    check(retired.renameTo(stored.file)) { "Unable to restore legacy video" }
                }
                throw failure
            }
            return stored
        } finally {
            runCatching { input.close(); output.close() }
            producer.join()
            migration.deleteRecursively()
        }
    }

    fun reconcileVideoMigrations(indexed: List<VaultItem>, key: ByteArray,
                                 isCancelled: () -> Boolean = { false },
                                 commit: ((() -> Unit) -> Unit) = { it() }) {
        val byId = indexed.associateBy { it.id }
        payloads.listFiles()?.filter { it.name.endsWith(".legacy") }?.forEach { old ->
            check(!isCancelled()) { "Video reconciliation cancelled" }
            val id = old.name.removeSuffix(".legacy")
            val item = byId[id] ?: return@forEach // Unknown ciphertext is retained for explicit reconciliation.
            val current = File(payloads, "$id.vault")
            val stored = StoredPayload(id, current, item.plaintextSize, item.plaintextSha256, item.payloadNonce)
            val currentVerified = current.exists() && verify(stored, key)
            if (!currentVerified) check(verify(stored.copy(file = old), key)) { "Retained legacy video failed authentication" }
            commit {
                check(!isCancelled()) { "Video reconciliation cancelled" }
                if (currentVerified) old.delete()
                else {
                    current.delete()
                    check(old.renameTo(current)) { "Unable to recover legacy video" }
                }
            }
        }
        root.listFiles()?.filter { it.name.startsWith("video-migration-") }?.forEach { migration ->
            val id = migration.name.removePrefix("video-migration-")
            val item = byId[id] ?: return@forEach
            val stored = StoredPayload(id, File(payloads, "$id.vault"), item.plaintextSize, item.plaintextSha256, item.payloadNonce)
            if (stored.file.exists() && verify(stored, key)) commit {
                check(!isCancelled()) { "Video reconciliation cancelled" }
                migration.deleteRecursively()
            }
        }
    }

    fun decryptToBytes(stored: StoredPayload, key: ByteArray, isCancelled: () -> Boolean = { false }): ByteArray =
        decryptToBoundedBytes(stored, key, Int.MAX_VALUE, isCancelled)

    fun decryptWithProgress(stored: StoredPayload, key: ByteArray, isCancelled: () -> Boolean, onProgress: (Int) -> Unit): ByteArray =
        decryptExactly(stored, key, Int.MAX_VALUE, isCancelled, onProgress)

    /** One exact-size plaintext buffer; never publish it before GCM authentication succeeds. */
    fun decryptToBoundedBytes(stored: StoredPayload, key: ByteArray, maxBytes: Int, isCancelled: () -> Boolean): ByteArray =
        decryptExactly(stored, key, maxBytes, isCancelled) {}

    /** Authentication and digest must complete before this app-private file is handed to a chooser. */
    fun decryptToVerifiedFile(stored: StoredPayload, key: ByteArray, destination: File, maxBytes: Long, isCancelled: () -> Boolean) {
        require(stored.plaintextSize in 0..maxBytes) { "Upload exceeds the size limit" }
        check(!destination.exists()) { "Upload copy already exists" }
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            registered(FileInputStream(stored.file)).use { encrypted ->
                FileOutputStream(destination).use { file ->
                    val sink = object : OutputStream() {
                        override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            if (isCancelled()) throw java.io.IOException("Upload cancelled")
                            written += length
                            if (written > maxBytes) throw java.io.IOException("Upload exceeds size limit")
                            digest.update(bytes, offset, length)
                            file.write(bytes, offset, length)
                        }
                    }
                    val input = object : java.io.FilterInputStream(encrypted) {
                        override fun read(): Int { if (isCancelled()) throw java.io.IOException("Upload cancelled"); return `in`.read() }
                        override fun read(b: ByteArray, off: Int, len: Int): Int { if (isCancelled()) throw java.io.IOException("Upload cancelled"); return `in`.read(b, off, len) }
                    }
                    if (ChunkedVaultVideoStore.isChunked(stored.file)) {
                        registered(ChunkedVaultVideoStore.open(stored, key)).use { reader ->
                            val buffer = ByteArray(64 * 1024)
                            var position = 0L
                            while (position < reader.size) {
                                val count = reader.readAt(position, buffer, 0, buffer.size)
                                check(count > 0)
                                sink.write(buffer, 0, count)
                                position += count
                            }
                            buffer.fill(0)
                        }
                    } else cipher.decrypt(input, sink, key, stored.id.encodeToByteArray(), uk.co.traynor.privategallery.core.crypto.EncryptionHeader(stored.nonce))
                    file.fd.sync()
                }
            }
            if (isCancelled() || written != stored.plaintextSize || !digest.digest().contentEquals(stored.plaintextSha256))
                throw java.io.IOException("Upload verification failed")
        } catch (failure: Throwable) { destination.delete(); throw failure }
    }

    private fun decryptExactly(stored: StoredPayload, key: ByteArray, maxBytes: Int, isCancelled: () -> Boolean, onProgress: (Int) -> Unit): ByteArray {
        require(stored.plaintextSize in 0..maxBytes.toLong()) { "Media exceeds the in-memory size limit" }
        if (isCancelled()) throw java.io.IOException("Media read cancelled")
        if (ChunkedVaultVideoStore.isChunked(stored.file)) {
            val plain = ByteArray(stored.plaintextSize.toInt())
            try {
                registered(ChunkedVaultVideoStore.open(stored, key)).use { reader ->
                    var position = 0
                    while (position < plain.size) {
                        if (isCancelled()) throw java.io.IOException("Media read cancelled")
                        val count = reader.readAt(position.toLong(), plain, position, minOf(64 * 1024, plain.size - position))
                        check(count > 0)
                        position += count
                        onProgress((position.toLong() * 100 / plain.size).toInt())
                    }
                }
                check(MessageDigest.getInstance("SHA-256").digest(plain).contentEquals(stored.plaintextSha256))
                return plain
            } catch (failure: Throwable) { plain.fill(0); throw failure }
        }
        val plain = ByteArray(stored.plaintextSize.toInt())
        var position = 0
        var consumed = 0L
        var reported = -1
        val encryptedSize = stored.file.length().coerceAtLeast(1)
        fun report(value: Int) { if (value != reported) { reported = value; onProgress(value) } }
        try {
            report(0)
            registered(FileInputStream(stored.file)).use { encrypted ->
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
     * Without a committed index, unknown retired ciphertext is retained.
     */
    fun reconcileInterruptedDeletes(indexedIds: Set<String>, commit: ((() -> Unit) -> Unit) = { it() }) {
        payloads.listFiles()?.filter { it.isFile && it.name.endsWith(".deleting") }?.forEach { retired ->
            val id = retired.name.removeSuffix(".deleting")
            commit {
                if (id in indexedIds) restoreRetiredPayload(id)
                else if (File(root, "vault-index.enc").isFile) retired.delete()
            }
        }
    }

    /** Registration rejection must close the just-created copied-key reader/descriptor. */
    private fun <T : AutoCloseable> registered(resource: T): T = try {
        registerResource(resource)
        resource
    } catch (failure: Throwable) {
        runCatching { resource.close() }
        throw failure
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
