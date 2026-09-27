package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedIndexStoreTest {
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
