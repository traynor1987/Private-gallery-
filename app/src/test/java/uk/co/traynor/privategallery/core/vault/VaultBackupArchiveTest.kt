package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
                stored.nonce, VaultItemState.COMPLETE)
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
