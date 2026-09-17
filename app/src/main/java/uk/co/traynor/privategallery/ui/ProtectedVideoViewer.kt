package uk.co.traynor.privategallery.ui

import android.net.Uri
import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/** Plays a vault video from authenticated in-memory bytes; no plaintext file is exposed. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun ProtectedVideoViewer(bytes: ByteArray, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(bytes) {
        val factory = DataSource.Factory { ByteArrayDataSource(bytes) }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("memory://private-gallery/video")))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Dialog(onDismissRequest = onClose) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
