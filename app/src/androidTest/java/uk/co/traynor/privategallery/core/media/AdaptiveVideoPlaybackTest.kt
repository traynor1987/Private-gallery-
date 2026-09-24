@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.media

import androidx.activity.ComponentActivity
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.ui.ByteArrayDataSource
import uk.co.traynor.privategallery.ui.GatedMediaDataSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AdaptiveVideoPlaybackTest {
    @Test fun hlsManifestAndSegmentPrepareThroughNetworkGate() = prepare("fixture.m3u8")
    @Test fun dashManifestAndSegmentsPrepareThroughNetworkGate() = prepare("fixture.mpd")
    private fun prepare(name: String) {
        val ready = CountDownLatch(1)
        val opens = AtomicInteger()
        var failure: PlaybackException? = null
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var player: ExoPlayer
            scenario.onActivity { activity ->
                val factory = DataSource.Factory { GatedMediaDataSource(object : DataSource {
                    private var current: ByteArrayDataSource? = null
                    override fun addTransferListener(listener: TransferListener) = Unit
                    override fun open(spec: DataSpec): Long {
                        val bytes = SyntheticAdaptiveVideo.files[spec.uri.lastPathSegment] ?: throw java.io.IOException("Missing synthetic fixture")
                        opens.incrementAndGet()
                        return ByteArrayDataSource(bytes).also { current = it }.open(spec)
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int) = requireNotNull(current).read(buffer, offset, length)
                    override fun getUri() = current?.uri
                    override fun close() { current?.close(); current = null }
                }) { true } }
                player = ExoPlayer.Builder(activity).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                    override fun onPlayerError(error: PlaybackException) { failure = error; ready.countDown() }
                })
                val url = "https://fixture.invalid/$name"
                player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(WebMediaPolicy.mime(url)).build())
                player.prepare()
            }
            try {
                assertTrue("Adaptive source did not prepare", ready.await(15, TimeUnit.SECONDS))
                assertNull(failure)
                assertTrue("Manifest and segment must both be read", opens.get() >= 2)
            } finally { scenario.onActivity { player.release() } }
        }
    }
}
