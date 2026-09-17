package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedIndexStoreTest {
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
