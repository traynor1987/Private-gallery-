package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import uk.co.traynor.privategallery.core.security.OwnedResource
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
import uk.co.traynor.privategallery.core.security.ScopedIoGuard

/** Authenticated random access for new Vault videos; each decrypted chunk stays in bounded memory. */
object ChunkedVaultVideoStore {
    private val MAGIC = "PGVIDEO1".toByteArray(Charsets.US_ASCII)
    const val CHUNK_BYTES = 1024 * 1024
    const val HEADER_BYTES = 8 + 4 + 4 + 8 + 12 + 32
    private const val VERSION = 1
    private const val TAG_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val CHUNK_OVERHEAD = NONCE_BYTES + TAG_BYTES
    private val random = SecureRandom()

    fun isChunked(file: File): Boolean = file.isFile && file.length() >= HEADER_BYTES &&
        RandomAccessFile(file, "r").use { input -> ByteArray(MAGIC.size).also(input::readFully).contentEquals(MAGIC) }

    fun writeAndVerify(id: String, source: InputStream, key: ByteArray, root: File,
                       existingHeaderNonce: ByteArray? = null,
                       faults: PrimaryWriteFaults = PrimaryWriteFaults.NONE,
                       commit: ((() -> Unit) -> Unit) = { it() },
                       registerResource: (AutoCloseable) -> Unit = {}): StoredPayload {
        require(id.matches(Regex("[A-Za-z0-9-]{1,120}")) && key.size == 32)
        val staging = File(root, "staging").apply { mkdirs() }
        val payloads = File(root, "payloads").apply { mkdirs() }
        val temporary = File(staging, "$id.part")
        val destination = File(payloads, "$id.vault")
        check(!destination.exists()) { "Vault payload already exists" }
        val headerNonce = existingHeaderNonce?.copyOf() ?: ByteArray(NONCE_BYTES).also(random::nextBytes)
        require(headerNonce.size == NONCE_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        var chunks = 0
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_WRITE, temporary)
            RandomAccessFile(temporary, "rw").use { file ->
                file.setLength(0)
                file.write(ByteArray(HEADER_BYTES))
                while (true) {
                    var filled = 0
                    while (filled < buffer.size) {
                        val read = source.read(buffer, filled, buffer.size - filled)
                        if (read < 0) break
                        if (read > 0) filled += read
                        else {
                            val one = source.read()
                            if (one < 0) break
                            buffer[filled++] = one.toByte()
                        }
                    }
                    if (filled == 0) break
                    val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
                    val ciphertext = encrypt(key, nonce, aad(id, chunks, filled), buffer, filled)
                    file.write(nonce)
                    file.write(ciphertext)
                    ciphertext.fill(0)
                    digest.update(buffer, 0, filled)
                    size = Math.addExact(size, filled.toLong())
                    chunks = Math.addExact(chunks, 1)
                    buffer.fill(0)
                }
                val header = header(id, size, headerNonce, key)
                file.seek(0)
                file.write(header)
                faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_WRITE, temporary)
                faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_SYNC, temporary)
                file.fd.sync()
                faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_SYNC, temporary)
                header.fill(0)
            }
            val stored = StoredPayload(id, temporary, size, digest.digest(), headerNonce)
            faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_VERIFY, temporary)
            val reader = open(stored, key)
            try { registerResource(reader) } catch (failure: Throwable) { reader.close(); throw failure }
            check(reader.use { it.verifyAll() }) { "Encrypted video verification failed" }
            faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_VERIFY, temporary)
            var promoted = false
            commit {
                faults.checkpoint(WriteCheckpoint.PAYLOAD_BEFORE_PROMOTION, temporary)
                check(!destination.exists()) { "Vault payload already exists" }
                check(temporary.renameTo(destination)) { "Unable to promote encrypted video" }
                promoted = true
                faults.checkpoint(WriteCheckpoint.PAYLOAD_AFTER_PROMOTION, destination)
            }
            check(promoted) { "Encrypted video commit did not execute" }
            return stored.copy(file = destination)
        } catch (failure: Throwable) { temporary.delete(); throw failure }
        finally { buffer.fill(0) }
    }

    fun open(stored: StoredPayload, key: ByteArray): Reader = Reader(stored, key.copyOf(), RandomAccessFile(stored.file, "r"), true)

    /** The caller keeps the returned original for the whole read; its three physical
     * children are funded before the copied key or descriptor can exist. */
    fun openOwned(guard: ScopedIoGuard, stored: StoredPayload, key: ByteArray): OwnedResource<Reader> =
        guard.createOwned(OwnedResourceManifest.io("reader", "key", "descriptor")) {
            val ownedKey = copyBytes("key", key)
            val descriptor = create("descriptor", { it.close() }) { RandomAccessFile(stored.file, "r") }
            attach("reader", Reader(stored, ownedKey, descriptor.value, false))
        }

    class Reader private constructor(
        private val stored: StoredPayload,
        private val ownedKey: ByteArray,
        private val file: RandomAccessFile,
        private val ownsDependencies: Boolean,
    ) : AutoCloseable {
        val size: Long

        init {
            try {
                val bytes = ByteArray(HEADER_BYTES).also(file::readFully)
                val header = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN)
                val magic = ByteArray(MAGIC.size).also(header::get)
                require(magic.contentEquals(MAGIC) && header.int == VERSION && header.int == CHUNK_BYTES) { "Unsupported video payload" }
                size = header.long
                val nonce = ByteArray(NONCE_BYTES).also(header::get)
                val signature = ByteArray(32).also(header::get)
                require(size >= 0 && nonce.contentEquals(stored.nonce) && size == stored.plaintextSize) { "Invalid video header" }
                val expected = headerMac(stored.id, bytes.copyOfRange(0, HEADER_BYTES - 32), ownedKey)
                require(MessageDigest.isEqual(signature, expected)) { "Video header authentication failed" }
                expected.fill(0); signature.fill(0); bytes.fill(0)
                val count = if (size == 0L) 0L else (size - 1) / CHUNK_BYTES + 1
                require(count <= Int.MAX_VALUE && file.length() == HEADER_BYTES + size + count * CHUNK_OVERHEAD) {
                    "Invalid video payload length"
                }
            } catch (failure: Throwable) { close(); throw failure }
        }

        @Synchronized fun readAt(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
            require(position >= 0 && offset in 0..destination.size && length >= 0 && length <= destination.size - offset)
            if (length == 0) return 0
            if (position >= size) return -1
            var written = 0
            var cursor = position
            while (written < length && cursor < size) {
                val chunkIndex = cursor / CHUNK_BYTES
                val chunk = loadChunk(chunkIndex)
                val count = try {
                    val within = (cursor % CHUNK_BYTES).toInt()
                    minOf(length - written, chunk.size - within).also {
                        chunk.copyInto(destination, offset + written, within, within + it)
                    }
                } finally {
                    chunk.fill(0)
                }
                cursor += count
                written += count
            }
            return written
        }

        @Synchronized fun verifyAll(): Boolean = try {
            val digest = MessageDigest.getInstance("SHA-256")
            val count = if (size == 0L) 0L else (size - 1) / CHUNK_BYTES + 1
            for (index in 0 until count) {
                val chunk = loadChunk(index)
                try { digest.update(chunk) } finally { chunk.fill(0) }
            }
            digest.digest().contentEquals(stored.plaintextSha256)
        } catch (_: Throwable) { false }

        private fun loadChunk(index: Long): ByteArray {
            val length = minOf(CHUNK_BYTES.toLong(), size - index * CHUNK_BYTES).toInt()
            require(length > 0)
            val offset = HEADER_BYTES + index * (CHUNK_BYTES.toLong() + CHUNK_OVERHEAD)
            file.seek(offset)
            val nonce = ByteArray(NONCE_BYTES).also(file::readFully)
            val encrypted = ByteArray(length + TAG_BYTES).also(file::readFully)
            try {
                val plain = decrypt(ownedKey, nonce, aad(stored.id, index.toInt(), length), encrypted)
                check(plain.size == length)
                return plain
            } finally { encrypted.fill(0); nonce.fill(0) }
        }

        @Synchronized override fun close() {
            if (ownsDependencies) {
                ownedKey.fill(0)
                file.close()
            }
        }
    }

    private fun header(id: String, size: Long, nonce: ByteArray, key: ByteArray): ByteArray {
        val unsigned = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.write(MAGIC); out.writeInt(VERSION); out.writeInt(CHUNK_BYTES); out.writeLong(size); out.write(nonce)
        } }.toByteArray()
        return unsigned + headerMac(id, unsigned, key)
    }

    private fun headerMac(id: String, unsigned: ByteArray, key: ByteArray): ByteArray {
        val derive = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val macKey = derive.doFinal("private-gallery:video-header:v1".toByteArray())
        return try { Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(macKey, "HmacSHA256")) }
            .doFinal(id.toByteArray() + unsigned) } finally { macKey.fill(0) }
    }

    private fun aad(id: String, index: Int, length: Int): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out -> out.writeUTF("private-gallery:video-chunk:v1"); out.writeUTF(id); out.writeInt(index); out.writeInt(length) }
    }.toByteArray()

    private fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray, length: Int): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plain, 0, length)

    private fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, encrypted: ByteArray): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(encrypted)

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
        }
}
