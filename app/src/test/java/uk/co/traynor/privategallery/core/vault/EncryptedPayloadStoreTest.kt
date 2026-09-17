package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedPayloadStoreTest {
    @Test
    fun `verified import stores only encrypted payload and decrypts exactly`() {
        val root = Files.createTempDirectory("private-gallery-test").toFile()
        val source = "private photo bytes".encodeToByteArray()
        val store = EncryptedPayloadStore(root, syncOutput = {})
        val vaultKey = key()

        val stored = store.writeAndVerify("item-1", ByteArrayInputStream(source), vaultKey)

        assertTrue(stored.file.exists())
        assertFalse(stored.file.readBytes().contentEquals(source))
        assertArrayEquals(source, store.decryptToBytes(stored, vaultKey))
    }

    @Test
    fun `tampered payload fails verification and is never treated as readable`() {
        val root = Files.createTempDirectory("private-gallery-test").toFile()
        val vaultKey = key()
        val store = EncryptedPayloadStore(root, syncOutput = {})
        val stored = store.writeAndVerify("item-2", ByteArrayInputStream(ByteArray(128) { it.toByte() }), vaultKey)

        stored.file.appendBytes(byteArrayOf(1))

        assertFalse(store.verify(stored, vaultKey))
    }

    @Test
    fun `reconciliation removes only encrypted incomplete staging files`() {
        val root = Files.createTempDirectory("private-gallery-test").toFile()
        val staging = File(root, "staging").apply { mkdirs() }
        File(staging, "interrupted.part").writeBytes(byteArrayOf(1, 2, 3))
        val store = EncryptedPayloadStore(root, syncOutput = {})

        store.reconcileInterruptedWrites()

        assertFalse(File(staging, "interrupted.part").exists())
    }

    @Test
    fun `interrupted deletion restores payload while its index record remains`() {
        val root = Files.createTempDirectory("private-gallery-test").toFile()
        val vaultKey = key()
        val store = EncryptedPayloadStore(root, syncOutput = {})
        val stored = store.writeAndVerify("item-3", ByteArrayInputStream("protected".encodeToByteArray()), vaultKey)

        store.retireForDeletion("item-3")
        store.reconcileInterruptedDeletes(setOf("item-3"))

        assertTrue(stored.file.exists())
        assertArrayEquals("protected".encodeToByteArray(), store.decryptToBytes(stored, vaultKey))
    }

    private fun key() = ByteArray(32) { it.toByte() }
}
