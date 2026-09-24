@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.media

import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.ui.*
import java.io.File

class PrivateVideoPlayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun protectedMemoryPlaybackAndCleanup() = playback(true)
    @Test fun galleryContentUriPlaybackAndCleanup() = playback(false)
    private fun playback(memory: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = SyntheticVideo.bytes()
        val fixture = File(context.cacheDir, "updates/synthetic-player.mp4").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        var visible by mutableStateOf(true)
        val item = if (memory) VaultVideoPlaybackSpec.mediaItem("video/mp4") else MediaItem.fromUri(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fixture))
        val factory = if (memory) DataSource.Factory { ByteArrayDataSource(bytes) } else null
        var surface: PlayerView? = null
        try {
            compose.setContent { PrivateGalleryTheme { if (visible) PrivateVideoPlayer(item, factory) } }
            compose.waitUntil(15000) {
                var ready = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    surface = findPlayer(compose.activity.window.decorView)
                    ready = surface?.player?.let { it.playbackState == Player.STATE_READY || it.playbackState == Player.STATE_ENDED } == true
                }
                ready
            }
            compose.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, compose.activity.requestedOrientation)
                assertNull(surface!!.player!!.playerError)
                visible = false
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
                assertNull(surface!!.player)
                assertFalse(surface!!.keepScreenOn)
            }
        } finally { bytes.fill(0); fixture.delete() }
    }
    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
