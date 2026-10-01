@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.vault

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultVideoDataSourceTest {
    @Test fun media3SeeksAcrossAuthenticatedChunksAndStopsOnLock() {
        val root = Files.createTempDirectory("vault-video-datasource").toFile()
        val key = ByteArray(32) { it.toByte() }
        val plain = ByteArray(1024 * 1024 + 48) { (it % 127).toByte() }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("e31de763-94ed-424c-9d12-05c6bea56161",
                ByteArrayInputStream(plain), key, root)
            var unlocked = true
            VaultVideoSession(stored, key) { unlocked }.use { session ->
                val source = session.sourceFactory.createDataSource()
                val start = 1024L * 1024 - 10
                source.open(DataSpec.Builder().setUri(Uri.parse("memory://private-gallery/video.mp4")).setPosition(start).build())
                val result = ByteArray(40)
                assertEquals(40, source.read(result, 0, result.size))
                assertArrayEquals(plain.copyOfRange(start.toInt(), start.toInt() + 40), result)
                unlocked = false
                assertThrows(java.io.IOException::class.java) { source.read(result, 0, result.size) }
                source.close()
            }
        } finally { root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }
    @Test fun closingSessionActivelyClosesAllLiveDataSourcesWithoutAnotherRead() {
        val root = Files.createTempDirectory("vault-video-close").toFile()
        val key = ByteArray(32) { 17 }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("session-close", ByteArrayInputStream(ByteArray(64) { 3 }), key, root)
            val session = VaultVideoSession(stored, key) { true }
            val first = session.sourceFactory.createDataSource()
            val second = session.sourceFactory.createDataSource()
            val spec = DataSpec.Builder().setUri(Uri.parse("memory://private-gallery/video.mp4")).build()
            first.open(spec); second.open(spec)
            first.read(ByteArray(8), 0, 8); second.read(ByteArray(8), 0, 8)
            // Hold references solely to verify real descriptor closure and mutable-buffer wiping.
            val readers = listOf(first, second).map { source ->
                source.javaClass.getDeclaredField("reader").apply { isAccessible = true }.get(source)
                    as ChunkedVaultVideoStore.Reader
            }
            val descriptors = readers.map { reader ->
                (reader.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(reader)
                    as java.io.RandomAccessFile).fd
            }
            val copiedKeys = readers.map { reader ->
                reader.javaClass.getDeclaredField("ownedKey").apply { isAccessible = true }.get(reader) as ByteArray
            }
            val caches = readers.map { reader ->
                reader.javaClass.getDeclaredField("cached").apply { isAccessible = true }.get(reader) as ByteArray
            }
            assertTrue(descriptors.all { it.valid() })
            session.close()
            assertTrue(descriptors.none { it.valid() })
            copiedKeys.forEach { assertArrayEquals(ByteArray(32), it) }
            caches.forEach { assertArrayEquals(ByteArray(64), it) }
            assertNull(first.uri)
            assertNull(second.uri)
            assertThrows(java.io.IOException::class.java) { first.open(spec) }
            assertThrows(java.io.IOException::class.java) { second.read(ByteArray(8), 0, 8) }
            session.close(); first.close(); second.close()
        } finally { root.deleteRecursively(); key.fill(0) }
    }
}
