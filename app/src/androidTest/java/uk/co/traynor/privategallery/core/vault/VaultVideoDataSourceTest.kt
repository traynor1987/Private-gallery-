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
    @Test fun revocationDuringReadRejectsAndWipesDeliveredBytes() {
        val root = Files.createTempDirectory("vault-revoke-during-read").toFile()
        val key = ByteArray(32) { 7 }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("revocation-race", ByteArrayInputStream(ByteArray(128) { 85 }), key, root)
            var unlocked = true
            var revoke = false
            VaultVideoSession(stored, key) {
                val captured = unlocked
                if (revoke) { unlocked = false; revoke = false }
                captured
            }.use { session ->
                val source = session.sourceFactory.createDataSource()
                source.open(DataSpec.Builder().setUri(Uri.parse("memory://private-gallery/video.mp4")).build())
                val result = ByteArray(32) { 42 }
                revoke = true
                assertThrows(java.io.IOException::class.java) { source.read(result, 0, result.size) }
                assertTrue("No bytes may escape after in-flight authority revocation", result.all { it == 0.toByte() })
                source.close()
            }
        } finally { root.deleteRecursively(); key.fill(0) }
    }

    @Test fun sessionCloseRetiresLiveReaderKeysAndCachedPlaintext() {
        val root = Files.createTempDirectory("vault-close-live-reader").toFile()
        val key = ByteArray(32) { 7 }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("close-live-reader", ByteArrayInputStream(ByteArray(128) { 85 }), key, root)
            val session = VaultVideoSession(stored, key) { true }
            val source = session.sourceFactory.createDataSource()
            source.open(DataSpec.Builder().setUri(Uri.parse("memory://private-gallery/video.mp4")).build())
            val output = ByteArray(1)
            assertEquals(1, source.read(output, 0, 1))
            val reader = source.javaClass.getDeclaredField("reader").apply { isAccessible = true }.get(source)!!
            val readerKey = reader.javaClass.getDeclaredField("ownedKey").apply { isAccessible = true }.get(reader) as ByteArray
            val cache = reader.javaClass.getDeclaredField("cached").apply { isAccessible = true }.get(reader) as ByteArray
            session.close()
            assertTrue("Closing the session retires reader key copies", readerKey.all { it == 0.toByte() })
            assertTrue("Closing the session wipes the actual decoded chunk", cache.all { it == 0.toByte() })
            assertThrows(java.io.IOException::class.java) { source.read(output, 0, 1) }
            source.close(); output.fill(0)
        } finally { root.deleteRecursively(); key.fill(0) }
    }

}
