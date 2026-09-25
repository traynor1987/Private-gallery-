@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.media

import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
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
    @Test fun measuredDecryptionModalShowsPercentageAndCancel() {
        var cancelled: (() -> Boolean)? = null
        var report: ((Int) -> Unit)? = null
        var shown by mutableStateOf(true)
        compose.setContent { PrivateGalleryTheme {
            if (shown) FullscreenMediaViewer(listOf(ViewerMediaEntry("progress", "video/mp4")),
                uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT, 0, { shown = false },
                onLoadVideoBytes = { _, check, progress, _ -> cancelled = check; report = progress })
        } }
        compose.runOnIdle { report!!.invoke(42) }
        compose.onNodeWithText("Decrypting video").assertIsDisplayed()
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(cancelled!!.invoke()) }
    }

    @Test fun realEncryptedPayloadPlaysThroughProtectedViewer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "encrypted-video-fixture").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = root }
        val vaultKey = ByteArray(32) { (it + 1).toByte() }
        val source = SyntheticVideo.bytes()
        val repository = uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(isolated, vaultKey)
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        val startRead = java.util.concurrent.CountDownLatch(1)
        var shown by mutableStateOf(true)
        try {
            val imported = uk.co.traynor.privategallery.core.vault.VaultImportCoordinator(repository).acquire(
                uk.co.traynor.privategallery.core.vault.VaultImportSource("synthetic.mp4", "video/mp4", { java.io.ByteArrayInputStream(source) }))
                as uk.co.traynor.privategallery.core.vault.ImportResult.Imported
            val entries = listOf(ViewerMediaEntry(imported.item.id, "video/mp4"))
            compose.setContent { PrivateGalleryTheme {
                if (shown) FullscreenMediaViewer(entries, uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT, 0, { shown = false },
                    onLoadVideoBytes = { _, cancelled, progress, complete ->
                        reader.submit {
                            val result = runCatching {
                                check(startRead.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                repository.readVideoForViewing(imported.item, cancelled) { percent ->
                                    compose.activity.runOnUiThread { if (!cancelled()) progress(percent) }
                                }
                            }
                            compose.activity.runOnUiThread { complete(result) }
                        }
                    })
            } }
            compose.onNodeWithText("Decrypting video").assertIsDisplayed()
            compose.onNodeWithText("0%").assertIsDisplayed()
            startRead.countDown()
            compose.waitUntil(15000) { compose.runOnIdle {
                findPlayer(compose.activity.window.decorView)?.player?.let {
                    it.playerError == null && it.currentPosition >= 500 &&
                        VaultPlaybackDiagnostics.summary().contains("FIRST_FRAME") &&
                        VaultPlaybackDiagnostics.summary().contains("POSITION_ADVANCED")
                } == true
            } }
            compose.onNodeWithText("Decrypting video").assertDoesNotExist()
            compose.onNodeWithText("Preparing video").assertDoesNotExist()
            compose.runOnIdle {
                val summary = VaultPlaybackDiagnostics.summary()
                assertTrue(summary, summary.contains("AUTHENTICATED"))
                shown = false
            }
        } finally {
            startRead.countDown()
            reader.shutdownNow()
            reader.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
            source.fill(0); vaultKey.fill(0); root.deleteRecursively()
        }
    }

    @Test fun changingLifecycleOwnerDoesNotReleaseRetainedPlayer() {
        fun owner(state: androidx.lifecycle.Lifecycle.State = androidx.lifecycle.Lifecycle.State.RESUMED): androidx.lifecycle.LifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
            init { registry.currentState = state }
        }
        var current by mutableStateOf(compose.runOnIdle { owner() })
        val bytes = SyntheticVideo.bytes()
        val item = VaultVideoPlaybackSpec.mediaItem("video/mp4")
        val factory = DataSource.Factory { ByteArrayDataSource(bytes) }
        compose.setContent { CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides current) {
            PrivateGalleryTheme { PrivateVideoPlayer(item, factory) }
        } }
        compose.waitUntil(15000) { compose.runOnIdle {
            findPlayer(compose.activity.window.decorView)?.player?.playbackState in listOf(Player.STATE_READY, Player.STATE_ENDED)
        } }
        val player = compose.runOnIdle { findPlayer(compose.activity.window.decorView)!!.player!! }
        compose.runOnIdle { current = owner() }
        compose.runOnIdle {
            assertSame(player, findPlayer(compose.activity.window.decorView)!!.player)
            assertNotEquals("Retained player must remain prepared", Player.STATE_IDLE, player.playbackState)
            current = owner(androidx.lifecycle.Lifecycle.State.CREATED)
        }
        compose.runOnIdle { assertFalse("Inactive owner must pause retained playback", player.playWhenReady) }
    }

    @Test fun protectedPageReuseLoadsFreshBytesAndPlaysAgain() {
        var active by mutableStateOf(true)
        var loads = 0
        val buffers = mutableListOf<ByteArray>()
        val entries = listOf(ViewerMediaEntry("reuse-video", "video/mp4"))
        compose.setContent { PrivateGalleryTheme {
            ReusableContentHost(active) {
                FullscreenMediaViewer(entries, uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT, 0, {},
                    onLoadProtectedBytes = { _, complete ->
                        loads++
                        complete(Result.success(SyntheticVideo.bytes().also { buffers.add(it) }))
                    })
            }
        } }
        fun awaitPlayback() = compose.waitUntil(15000) {
            compose.runOnIdle { findPlayer(compose.activity.window.decorView)?.player?.let {
                it.playerError == null && (it.playbackState == Player.STATE_READY || it.playbackState == Player.STATE_ENDED)
            } == true }
        }
        awaitPlayback()
        compose.runOnIdle { active = false }
        compose.runOnIdle { assertTrue(buffers.first().all { it == 0.toByte() }); active = true }
        awaitPlayback()
        compose.runOnIdle { assertEquals(2, loads); active = false }
    }

    @Test fun closingProtectedViewerCancelsPendingDecryption() {
        var shown by mutableStateOf(true)
        var cancelled: (() -> Boolean)? = null
        compose.setContent { PrivateGalleryTheme {
            if (shown) FullscreenMediaViewer(
                listOf(ViewerMediaEntry("pending-video", "video/mp4")),
                uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT, 0, { shown = false },
                onLoadVideoBytes = { _, isCancelled, _, _ -> cancelled = isCancelled },
            )
        } }
        compose.runOnIdle { assertNotNull(cancelled); assertFalse(cancelled!!.invoke()); shown = false }
        compose.runOnIdle { assertTrue(cancelled!!.invoke()) }
    }

    @Test fun systemBackClosesDedicatedPlayer() {
        var closed = false
        val bytes = SyntheticVideo.bytes()
        val factory = DataSource.Factory { ByteArrayDataSource(bytes) }
        val item = VaultVideoPlaybackSpec.mediaItem("video/mp4")
        compose.setContent { PrivateGalleryTheme { PrivateVideoPlayer(item, factory, onClose = { closed = true }) } }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue(closed); assertFalse(compose.activity.isFinishing) }
    }

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
