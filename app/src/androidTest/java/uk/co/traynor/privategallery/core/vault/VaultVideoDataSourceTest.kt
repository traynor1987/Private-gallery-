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
}
