@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import uk.co.traynor.privategallery.core.media.WebMediaPolicy

internal fun Context.playerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.playerActivity()
    else -> null
}

/** Network gate wraps every manifest/segment open and read, with no browser credentials. */
class GatedMediaDataSource(private val delegate: DataSource, private val allowed: () -> Boolean) : DataSource {
    override fun addTransferListener(listener: TransferListener) = delegate.addTransferListener(listener)
    override fun open(dataSpec: DataSpec): Long {
        WebMediaPolicy.requireNetwork(allowed())
        check(dataSpec.uri.scheme in setOf("http", "https")) { "Unsupported web media source" }
        return delegate.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!allowed()) { delegate.close(); WebMediaPolicy.requireNetwork(false) }
        return delegate.read(buffer, offset, length)
    }
    override fun getUri() = delegate.uri
    override fun getResponseHeaders() = delegate.responseHeaders
    override fun close() = delegate.close()
}

/** Shared player: content URI, authenticated in-memory Vault buffer, or explicitly selected web URL. */
@Composable
fun PrivateVideoPlayer(
    item: MediaItem,
    sourceFactory: DataSource.Factory? = null,
    networkAllowed: (() -> Boolean)? = null,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentGate by rememberUpdatedState(networkAllowed)
    var error by remember(item) { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var immersive by remember { mutableStateOf(true) }
    val player = remember(item, sourceFactory) {
        val factory = sourceFactory ?: if (networkAllowed != null) DataSource.Factory {
            GatedMediaDataSource(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
                .setConnectTimeoutMs(15000).setReadTimeoutMs(15000).createDataSource()) { currentGate?.invoke() == true }
        } else DefaultDataSource.Factory(context)
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                setMediaItem(item); prepare(); playWhenReady = true
            }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) { error = true }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener); lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    DisposableEffect(immersive) {
        val activity = context.playerActivity()
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        if (immersive) {
            bars?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else bars?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (onClose != null) TextButton(onClick = onClose) { Text("Back") }
            TextButton(onClick = { fill = !fill }) { Text(if (fill) "Fill" else "Fit") }
            TextButton(onClick = { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> .5f; else -> 1f }; player.setPlaybackSpeed(speed) }) { Text("${speed}×") }
            TextButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) { Text(if (muted) "Unmute" else "Mute") }
            TextButton(onClick = { immersive = !immersive }) { Text(if (immersive) "Show bars" else "Fullscreen") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { PlayerView(it).apply {
                this.player = player; useController = true; keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowNextButton(false); setShowPreviousButton(false)
            } }, update = { it.player = player; it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                onRelease = { it.player = null; it.keepScreenOn = false }, modifier = Modifier.fillMaxSize())
            if (error) Surface(Modifier.align(Alignment.Center).padding(20.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (networkAllowed != null) "This stream is unavailable here. Protected or session-dependent media must stay in Browser." else "Unable to play this video on this device.")
                    TextButton(onClick = { if (currentGate?.invoke() != false) { error = false; player.prepare(); player.play() } }) { Text("Retry") }
                }
            }
        }
    }
}
