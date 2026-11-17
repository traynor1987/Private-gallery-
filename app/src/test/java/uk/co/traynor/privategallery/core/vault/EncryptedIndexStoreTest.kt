package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedIndexStoreTest {
    @Test fun `missing index cannot hide existing encrypted payloads`() {
        val root = Files.createTempDirectory("private-gallery-missing-index").toFile()
        try {
            val payload = File(root, "payloads/retained.vault")
            check(payload.parentFile.mkdirs())
            val encryptedBytes = byteArrayOf(1, 2, 3, 4)
            payload.writeBytes(encryptedBytes)
            val failure = runCatching { EncryptedIndexStore(root).loadSnapshot(ByteArray(32)) }.exceptionOrNull()
            assertTrue("Missing metadata must require recovery", failure is IllegalStateException)
            assertArrayEquals(encryptedBytes, payload.readBytes())
            assertTrue(!File(root, "vault-index.enc").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun `missing index permits a genuinely empty vault`() {
        val root = Files.createTempDirectory("private-gallery-empty-index").toFile()
        try {
            assertTrue(EncryptedIndexStore(root).loadSnapshot(ByteArray(32)).items.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun `corrupt staged index preserves committed encrypted data`() {
        val root = Files.createTempDirectory("private-gallery-index-corruption").toFile()
        try {
            val key = ByteArray(32) { it.toByte() }
            val original = VaultIndexSnapshot(emptyList(), listOf(VaultCollection("original", "Keep", 42)))
            EncryptedIndexStore(root).saveSnapshot(original, key)
            val committed = File(root, "vault-index.enc").readBytes()
            val corruptingStore = EncryptedIndexStore(root, syncOutput = { output ->
                output.fd.sync()
                java.io.RandomAccessFile(File(root, "vault-index.new"), "rw").use { staged ->
                    staged.seek(staged.length() - 1)
                    val last = staged.readByte()
                    staged.seek(staged.length() - 1)
                    staged.writeByte(last.toInt() xor 1)
                }
            })
            val failure = runCatching {
                corruptingStore.saveSnapshot(VaultIndexSnapshot(emptyList()), key)
            }.exceptionOrNull()
            assertTrue("Corrupt ciphertext must be rejected before promotion", failure != null)
            assertArrayEquals(committed, File(root, "vault-index.enc").readBytes())
            assertEquals("Keep", EncryptedIndexStore(root).loadSnapshot(key).collections.single().name)
            assertTrue(!File(root, "vault-index.new").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun `recently deleted timestamp and collection membership survive encrypted round trip`() {
        val root = Files.createTempDirectory("private-gallery-trash").toFile()
        try {
            val key = ByteArray(32) { it.toByte() }
            val item = VaultItem("vault-1", "image/jpeg", "hidden.jpg", 42, 7, ByteArray(32), ByteArray(12),
                VaultItemState.TRASHED, deletedAtEpochMillis = 123456L)
            val collection = VaultCollection("collection", "Keep", 42)
            val store = EncryptedIndexStore(root, syncOutput = {})
            store.saveSnapshot(VaultIndexSnapshot(listOf(item), listOf(collection),
                listOf(VaultCollectionMembership(collection.id, item.id, 43))), key)
            val restored = store.loadSnapshot(key)
            assertEquals(123456L, restored.items.single().deletedAtEpochMillis)
            assertTrue(VaultCollectionsState(restored).itemsIn(collection.id).isEmpty())
            assertEquals(1, restored.memberships.size)
            assert(!File(root, "vault-index.enc").readBytes().decodeToString().contains("hidden.jpg"))
        } finally { root.deleteRecursively() }
    }
    @Test
    fun `index round trip keeps metadata encrypted at rest`() {
        val root = Files.createTempDirectory("private-gallery-index").toFile()
        val store = EncryptedIndexStore(root, syncOutput = {})
        val key = ByteArray(32) { it.toByte() }
        val expected = listOf(
            VaultItem(
                id = "vault-1",
                mimeType = "image/jpeg",
                displayName = "holiday.jpg",
                importedAtEpochMillis = 42,
                plaintextSize = 7,
                plaintextSha256 = ByteArray(32) { it.toByte() },
                payloadNonce = ByteArray(12) { 7 },
                state = VaultItemState.COMPLETE,
            ),
        )

        store.save(expected, key)

        val actual = store.load(key).single()
        assertEquals(expected.single().id, actual.id)
        assertEquals(expected.single().mimeType, actual.mimeType)
        assertEquals(expected.single().displayName, actual.displayName)
        assertEquals(expected.single().importedAtEpochMillis, actual.importedAtEpochMillis)
        assertEquals(expected.single().plaintextSize, actual.plaintextSize)
        assertEquals(expected.single().state, actual.state)
        assertArrayEquals(expected.single().plaintextSha256, actual.plaintextSha256)
        assertArrayEquals(expected.single().payloadNonce, actual.payloadNonce)
        assert(!File(root, "vault-index.enc").readBytes().decodeToString().contains("holiday.jpg"))
    }
}
