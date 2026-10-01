package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryKey

class VaultBackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    /** Mutations caught: swallowing stream errors, accepting partial archives, or deleting source ciphertext. */
    @Test fun backupOutputFailureAndCancellationRetainSourceAndPermitAuthenticatedRetry() {
        val root = temporary.newFolder("fault-source")
        val key = ByteArray(32) { (it + 19).toByte() }
        val secret = RecoveryKey.generate()
        val envelope = RecoveryEnvelope.create(secret.copyOf(), key)
        try {
            seedFaultSource(root, key)
            val before = ciphertextDigests(root)
            for (limit in listOf(0, 128, 4096)) {
                var written = 0
                val failing = object : OutputStream() {
                    override fun write(value: Int) {
                        if (written++ >= limit) throw IOException("synthetic ENOSPC")
                    }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        repeat(length) { write(bytes[offset + it].toInt()) }
                    }
                }
                assertThrows(IOException::class.java) { VaultBackupArchive.write(root, key, envelope, failing) }
                assertEquals(before, ciphertextDigests(root))
            }
            var cancelled = false
            assertThrows(IllegalStateException::class.java) {
                VaultBackupArchive.write(root, key, envelope, ByteArrayOutputStream(),
                    progress = { _, _ -> cancelled = true }, cancelled = { cancelled })
            }
            assertEquals(before, ciphertextDigests(root))
            val archive = ByteArrayOutputStream().also { VaultBackupArchive.write(root, key, envelope, it) }.toByteArray()
            val restored = VaultBackupArchive.read(archive.inputStream(), File(temporary.root, "fault-retry"), secret.copyOf())
            try {
                assertArrayEquals(key, restored.key)
                assertEquals(before, ciphertextDigests(restored.stage))
                assertEquals(2, EncryptedIndexStore(restored.stage).loadSnapshot(restored.key).items.size)
            } finally { restored.key.fill(0); archive.fill(0) }
        } finally { key.fill(0); secret.fill('\u0000') }
    }

    /** Mutations caught: promoting a truncated reader result, retaining failed staging, corrupting independent backup. */
    @Test fun interruptedRestoreReadsCleanOnlyOwnedStageAndFreshReaderAuthenticatesRetry() {
        val root = temporary.newFolder("read-fault-source")
        val key = ByteArray(32) { (it + 37).toByte() }
        val secret = RecoveryKey.generate()
        val envelope = RecoveryEnvelope.create(secret.copyOf(), key)
        try {
            seedFaultSource(root, key)
            val before = ciphertextDigests(root)
            val archive = ByteArrayOutputStream().also { VaultBackupArchive.write(root, key, envelope, it) }.toByteArray()
            val retained = File(temporary.root, "independent-backup.pgvault").apply { writeBytes(archive) }
            val retainedDigest = digest(retained)
            for (limit in listOf(0, 128, archive.size / 2)) {
                var readCount = 0
                val interrupted = object : FilterInputStream(archive.inputStream()) {
                    override fun read(): Int {
                        if (readCount >= limit) throw IOException("synthetic interrupted read")
                        return super.read().also { if (it >= 0) readCount++ }
                    }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        if (readCount >= limit) throw IOException("synthetic interrupted read")
                        return super.read(bytes, offset, minOf(length, limit - readCount)).also { if (it > 0) readCount += it }
                    }
                }
                val stage = File(temporary.root, "read-fault-$limit")
                assertThrows(IOException::class.java) { VaultBackupArchive.read(interrupted, stage, secret.copyOf()) }
                assertFalse(stage.exists())
                assertEquals(before, ciphertextDigests(root))
                assertEquals(retainedDigest, digest(retained))
                val reconstructed = VaultBackupArchive.read(retained.inputStream(), stage, secret.copyOf())
                try {
                    assertEquals(before, ciphertextDigests(stage))
                    assertArrayEquals(key, reconstructed.key)
                } finally { reconstructed.key.fill(0); stage.deleteRecursively() }
            }
            archive.fill(0)
        } finally { key.fill(0); secret.fill('\u0000') }
    }

    private fun seedFaultSource(root: File, key: ByteArray) {
        val items = listOf(false, true).map { video ->
            val id = UUID.randomUUID().toString()
            val plaintext = ByteArray(if (video) 128 * 1024 else 1024) { (it % 251).toByte() }
            val stored = EncryptedPayloadStore(root).writeAndVerify(id, plaintext.inputStream(), key, chunkedVideo = video)
            plaintext.fill(0)
            VaultItem(id, if (video) "video/mp4" else "image/png", "synthetic.media", 1,
                stored.plaintextSize, stored.plaintextSha256, stored.nonce, VaultItemState.COMPLETE)
        }
        EncryptedIndexStore(root).saveSnapshot(VaultIndexSnapshot(items), key)
    }

    private fun digest(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun ciphertextDigests(root: File): Map<String, String> = root.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(root).invariantSeparatorsPath to digest(it) }

    @Test fun encryptedRoundTripPreservesCollectionsAndAuthenticatesEveryPayload() {
        val root = temporary.newFolder("original")
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val recovery = RecoveryKey.generate()
        val envelope = RecoveryEnvelope.create(recovery.copyOf(), key)
        val items = listOf("image/jpeg" to ByteArray(128) { it.toByte() },
            "video/mp4" to ByteArray(1024 * 1024) { (it % 251).toByte() }).map { (mime, plain) ->
            val id = UUID.randomUUID().toString()
            val stored = EncryptedPayloadStore(root).writeAndVerify(id, ByteArrayInputStream(plain), key)
            VaultItem(id, mime, "$id.media", 123L, stored.plaintextSize, stored.plaintextSha256,
                stored.nonce, if (mime.startsWith("video/")) VaultItemState.TRASHED else VaultItemState.COMPLETE,
                deletedAtEpochMillis = if (mime.startsWith("video/")) 456L else null)
        }
        val collection = VaultCollection(UUID.randomUUID().toString(), "Keep", 123L)
        EncryptedIndexStore(root).saveSnapshot(VaultIndexSnapshot(items, listOf(collection),
            listOf(VaultCollectionMembership(collection.id, items.first().id, 123L))), key)
        val bytes = ByteArrayOutputStream().also { VaultBackupArchive.write(root, key, envelope, it) }.toByteArray()
        val destination = File(temporary.root, "staged")
        val restored = VaultBackupArchive.read(ByteArrayInputStream(bytes), destination, recovery.copyOf())
        try {
            assertEquals(items.map { it.id }, EncryptedIndexStore(destination).loadSnapshot(restored.key).items.map { it.id })
            assertEquals(collection.name, EncryptedIndexStore(destination).loadSnapshot(restored.key).collections.single().name)
            assertEquals(456L, EncryptedIndexStore(destination).loadSnapshot(restored.key).items.last().deletedAtEpochMillis)
            assertTrue(java.security.MessageDigest.isEqual(key, restored.key))
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Keep"))
        } finally { restored.key.fill(0); key.fill(0); recovery.fill('\u0000'); bytes.fill(0) }
    }

    @Test fun wrongRecoveryKeyAndUnlistedEntriesLeaveNoStagingVault() {
        val root = temporary.newFolder("source")
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val recovery = RecoveryKey.generate()
        val envelope = RecoveryEnvelope.create(recovery.copyOf(), key)
        EncryptedIndexStore(root).saveSnapshot(VaultIndexSnapshot(emptyList()), key)
        val archive = ByteArrayOutputStream().also { VaultBackupArchive.write(root, key, envelope, it) }.toByteArray()
        val stage = File(temporary.root, "bad-key")
        assertThrows(Exception::class.java) {
            VaultBackupArchive.read(ByteArrayInputStream(archive), stage, RecoveryKey.generate())
        }
        assertFalse(stage.exists())

        val malicious = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("payloads/../../escape.vault"))
                zip.write(1)
                zip.closeEntry()
            }
        }.toByteArray()
        val escaped = File(temporary.root, "escape.vault")
        assertThrows(Exception::class.java) {
            VaultBackupArchive.read(ByteArrayInputStream(malicious), File(temporary.root, "malicious"), recovery.copyOf())
        }
        assertFalse(escaped.exists())
        key.fill(0); recovery.fill('\u0000')
    }
}
