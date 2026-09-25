package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
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

    @Test fun `bounded editor decrypt rejects size before allocation and checks cancellation`() {
        val root = Files.createTempDirectory("editor-bound").toFile()
        try {
            val store = EncryptedPayloadStore(root, syncOutput = {})
            val key = key()
            val input = ByteArray(1000) { 5 }
            val stored = store.writeAndVerify("bounded", ByteArrayInputStream(input), key)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { store.decryptToBoundedBytes(stored, key, 500) { false } }
            org.junit.Assert.assertThrows(java.io.IOException::class.java) { store.decryptToBoundedBytes(stored, key, 1000) { true } }
            var checks = 0
            org.junit.Assert.assertThrows(java.io.IOException::class.java) { store.decryptToBoundedBytes(stored, key, 1000) { ++checks > 1 } }
            assertArrayEquals(input, store.decryptToBoundedBytes(stored, key, 1000) { false })
            assertTrue(root.walkTopDown().filter { it.isFile }.all { it.extension == "vault" })
        } finally { root.deleteRecursively() }
    }

    @Test fun `viewing rejects metadata size mismatch and damaged authentication`() {
        val root = Files.createTempDirectory("viewing-length").toFile()
        try {
            val store = EncryptedPayloadStore(root, syncOutput = {})
            val stored = store.writeAndVerify("video", ByteArrayInputStream(ByteArray(8192) { 7 }), key())
            org.junit.Assert.assertThrows(Exception::class.java) { store.decryptToBytes(stored.copy(plaintextSize = 4096), key()) }
            org.junit.Assert.assertThrows(Exception::class.java) { store.decryptToBytes(stored.copy(plaintextSize = 16384), key()) }
            stored.file.appendBytes(byteArrayOf(1))
            org.junit.Assert.assertThrows(Exception::class.java) { store.decryptToBytes(stored, key()) }
        } finally { root.deleteRecursively() }
    }

    @Test fun `decrypt progress is monotonic and complete only after authentication`() {
        val root = Files.createTempDirectory("video-progress").toFile()
        try {
            val store = EncryptedPayloadStore(root, syncOutput = {})
            val original = ByteArray(512 * 1024) { (it % 251).toByte() }
            val stored = store.writeAndVerify("progress", ByteArrayInputStream(original), key())
            val progress = mutableListOf<Int>()
            assertArrayEquals(original, store.decryptWithProgress(stored, key(), { false }, progress::add))
            assertEquals(0, progress.first()); assertEquals(100, progress.last())
            assertTrue(progress.any { it in 1..99 })
            assertTrue(progress.zipWithNext().all { (a, b) -> b > a })
            stored.file.appendBytes(byteArrayOf(1))
            progress.clear()
            org.junit.Assert.assertThrows(Exception::class.java) { store.decryptWithProgress(stored, key(), { false }, progress::add) }
            assertFalse(progress.contains(100))
        } finally { root.deleteRecursively() }
    }

    private fun key() = ByteArray(32) { it.toByte() }
}
