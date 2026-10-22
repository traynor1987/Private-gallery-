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
    @Test fun `browser upload copy is authenticated bounded and removed on failure`() {
        val root = Files.createTempDirectory("browser-upload").toFile()
        try {
            val store = EncryptedPayloadStore(root, syncOutput = {})
            val original = ByteArray(4096) { (it % 251).toByte() }
            val stored = store.writeAndVerify("upload", ByteArrayInputStream(original), key())
            val copy = File(root, "upload.bin")
            store.decryptToVerifiedFile(stored, key(), copy, 4096, { false })
            assertArrayEquals(original, copy.readBytes())
            copy.delete()
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                store.decryptToVerifiedFile(stored, key(), copy, 1024, { false })
            }
            assertFalse(copy.exists())
            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                store.decryptToVerifiedFile(stored, key(), copy, 4096, { true })
            }
            assertFalse(copy.exists())
            stored.file.appendBytes(byteArrayOf(7))
            org.junit.Assert.assertThrows(Exception::class.java) {
                store.decryptToVerifiedFile(stored, key(), copy, 4096, { false })
            }
            assertFalse(copy.exists())
        } finally { root.deleteRecursively() }
    }
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

    @Test fun `streaming verification rejects forged digest size nonce and key`() {
        val root=Files.createTempDirectory("verification-contract").toFile()
        try {
            val store=EncryptedPayloadStore(root,syncOutput={})
            val stored=store.writeAndVerify("legacy",ByteArrayInputStream(ByteArray(128*1024){it.toByte()}),key())
            assertTrue(store.verify(stored,key()))
            assertFalse(store.verify(stored.copy(plaintextSize=stored.plaintextSize-1),key()))
            assertFalse(store.verify(stored.copy(plaintextSha256=ByteArray(32)),key()))
            assertFalse(store.verify(stored.copy(nonce=ByteArray(12)),key()))
            assertFalse(store.verify(stored,ByteArray(32){99}))
        } finally {root.deleteRecursively()}
    }

    @Test fun `bounded writer produces byte-identical V1 GCM ciphertext and reader accepts V1 ciphertext`() {
        val root=Files.createTempDirectory("legacy-gcm-interop").toFile()
        try {
            val bytes=ByteArray(128*1024+13){(it%251).toByte()}
            val store=EncryptedPayloadStore(root,syncOutput={})
            val stored=store.writeAndVerify("interoperable",ByteArrayInputStream(bytes),key())
            val expected=java.io.ByteArrayOutputStream()
            uk.co.traynor.privategallery.core.crypto.VaultCipher.encrypt(ByteArrayInputStream(bytes),expected,key(),stored.id.encodeToByteArray(),stored.nonce)
            assertArrayEquals(expected.toByteArray(),stored.file.readBytes())
            assertArrayEquals(bytes,store.decryptToBytes(stored,key()))
            stored.file.writeBytes(expected.toByteArray())
            assertTrue(store.verify(stored,key()))
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {store.writeAndVerify("short-key",ByteArrayInputStream(bytes),ByteArray(16))}
        } finally {root.deleteRecursively()}
    }

    private fun key() = ByteArray(32) { it.toByte() }
}
