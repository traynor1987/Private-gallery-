package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChunkedVaultVideoStoreTest {
    @Test fun legacyVideoMigratesWithoutChangingIndexNonceOrPlaintextDigest() {
        val root = Files.createTempDirectory("private-gallery-legacy-video").toFile()
        val key = ByteArray(32) { (it + 3).toByte() }
        val plain = ByteArray(1024 * 1024 + 19) { (it % 197).toByte() }
        try {
            val store = EncryptedPayloadStore(root, syncOutput = {})
            val legacy = store.writeAndVerify("06b9676c-c671-4a50-a6d2-6919e33df624", ByteArrayInputStream(plain), key)
            assertFalse(ChunkedVaultVideoStore.isChunked(legacy.file))
            val migrated = store.migrateLegacyVideo(legacy, key) { false }
            assertArrayEquals(legacy.nonce, migrated.nonce)
            assertTrue(ChunkedVaultVideoStore.isChunked(migrated.file))
            assertTrue(store.verify(legacy, key))
            assertArrayEquals(plain, store.decryptToBoundedBytes(migrated, key, plain.size + 1) { false })
            assertFalse(root.walkTopDown().any { it.isFile && it.name.endsWith(".legacy") })
        } finally { root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }

    @Test fun boundedReaderAuthenticatesChunksAcrossSeekAndEndOfFile() {
        val root = Files.createTempDirectory("private-gallery-video-chunks").toFile()
        val key = ByteArray(32) { (it + 1).toByte() }
        val plain = ByteArray(2 * 1024 * 1024 + 73) { (it % 251).toByte() }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("b34677f4-81cd-409c-b825-608d8f4309ab", ByteArrayInputStream(plain), key, root)
            assertTrue(ChunkedVaultVideoStore.isChunked(stored.file))
            ChunkedVaultVideoStore.open(stored, key).use { reader ->
                assertTrue(reader.verifyAll())
                val target = ByteArray(170)
                assertEquals(170, reader.readAt(1024L * 1024 - 60, target, 0, target.size))
                assertArrayEquals(plain.copyOfRange(1024 * 1024 - 60, 1024 * 1024 + 110), target)
                assertEquals(73, reader.readAt(2L * 1024 * 1024, target, 0, target.size))
                assertEquals(-1, reader.readAt(plain.size.toLong(), target, 0, target.size))
            }
            java.io.RandomAccessFile(stored.file, "rw").use { file ->
                file.seek(ChunkedVaultVideoStore.HEADER_BYTES.toLong() + 30)
                file.writeByte(file.readByte().toInt() xor 1)
            }
            assertFalse(ChunkedVaultVideoStore.open(stored, key).use { it.verifyAll() })
        } finally { root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }
}
