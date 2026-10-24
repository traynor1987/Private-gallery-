@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        check(dataSpec.uri.scheme == "https") { "Unsupported web media source" }
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
    onMore: (() -> Unit)? = null,
    onReady: (() -> Unit)? = null,
    onWebViewFallback: (() -> Unit)? = null,
    recordVaultDiagnostics: Boolean = false,
    initialPositionMs: Long = 0,
    onRememberPosition: ((Long) -> Unit)? = null,
) {
    androidx.activity.compose.BackHandler(onClose != null) { onClose?.invoke() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentGate by rememberUpdatedState(networkAllowed)
    val ready by rememberUpdatedState(onReady)
    var error by remember(item) { mutableStateOf(false) }
    var errorCode by remember(item) { mutableStateOf<Int?>(null) }
    var preparing by remember(item) { mutableStateOf(true) }
    var fill by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    val rememberPosition by rememberUpdatedState(onRememberPosition)
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var remaining by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(true) }
    val player = remember(item, sourceFactory) {
        // Media3 can log source exceptions containing URLs. Keep private media out of logcat.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        val factory = sourceFactory ?: if (networkAllowed != null) DataSource.Factory {
            GatedMediaDataSource(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(false)
                .setConnectTimeoutMs(15000).setReadTimeoutMs(15000).createDataSource()) { foreground && currentGate?.invoke() == true }
        } else DefaultDataSource.Factory(context)
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                // Attach state/error listeners before starting preparation below.
            }
    }
    LaunchedEffect(player) {
        var reportedAdvancement = false
        val initialPosition = player.currentPosition
        while (isActive) {
            if (recordVaultDiagnostics && !reportedAdvancement && player.currentPosition >= initialPosition + 250) {
                VaultPlaybackDiagnostics.record(VaultPlaybackEvent.POSITION_ADVANCED)
                reportedAdvancement = true
            }
            remaining = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { duration ->
                val seconds = ((duration - player.currentPosition).coerceAtLeast(0) / 1000)
                "−%d:%02d".format(seconds / 60, seconds % 60)
            }
            delay(500)
        }
    }
    DisposableEffect(player) {
        fun record(event: VaultPlaybackEvent, code: Int? = null) {
            if (recordVaultDiagnostics) VaultPlaybackDiagnostics.record(event, code)
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                record(VaultPlaybackEvent.PLAYER_STATE, state)
                preparing = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) ready?.invoke()
            }
            override fun onRenderedFirstFrame() { record(VaultPlaybackEvent.FIRST_FRAME) }
            override fun onTracksChanged(tracks: Tracks) {
                val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                record(VaultPlaybackEvent.VIDEO_TRACK_SUPPORTED, if (video.any { it.isSupported }) 1 else 0)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                record(VaultPlaybackEvent.PLAY_WHEN_READY, if (playWhenReady) 1 else 0)
            }
            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                record(VaultPlaybackEvent.SUPPRESSION, playbackSuppressionReason)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(failure: PlaybackException) {
                record(VaultPlaybackEvent.PLAYER_ERROR, failure.errorCode)
                errorCode = failure.errorCode
                preparing = false
                error = true
            }
        }
        player.addListener(listener)
        error = false; errorCode = null; preparing = true
        record(VaultPlaybackEvent.PLAYER_PREPARING)
        player.setMediaItem(item,initialPositionMs.coerceAtLeast(0)); player.prepare(); player.playWhenReady = true
        onDispose {
            rememberPosition?.invoke(if(player.playbackState == Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0))
            player.removeListener(listener); player.release(); record(VaultPlaybackEvent.RELEASED)
        }
    }
    DisposableEffect(player,context) {
        val activity = context.playerActivity() as? uk.co.traynor.privategallery.MainActivity
        val stop: () -> Unit = { player.pause() }
        activity?.sensitivePlaybackStops?.add(stop)
        onDispose { activity?.sensitivePlaybackStops?.remove(stop) }
    }
    LaunchedEffect(seekFeedback) { if(seekFeedback!=null) { delay(900); seekFeedback=null } }
    DisposableEffect(player, lifecycle) {
        foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recordVaultDiagnostics) VaultPlaybackDiagnostics.record(if (foreground) VaultPlaybackEvent.LIFECYCLE_STARTED else VaultPlaybackEvent.LIFECYCLE_STOPPED)
        var stopped = !foreground
        if (!foreground) { player.pause(); if (networkAllowed != null) player.stop() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (recordVaultDiagnostics) VaultPlaybackDiagnostics.record(VaultPlaybackEvent.LIFECYCLE_STOPPED)
                stopped = true; foreground = false; player.pause()
                if (networkAllowed != null) player.stop()
            }
            if (event == Lifecycle.Event.ON_START) {
                if (recordVaultDiagnostics) VaultPlaybackDiagnostics.record(VaultPlaybackEvent.LIFECYCLE_STARTED)
                foreground = true
                if (stopped && networkAllowed != null) { preparing = false; error = true }
            }
        }
        lifecycle.addObserver(observer)
        // A lifecycle-owner change must not release a player still retained by remember.
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        val activity = context.playerActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    DisposableEffect(immersive) {
        val activity = context.playerActivity()
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (immersive) {
            bars?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else bars?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { PlayerView(it).apply {
                this.player = player; useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowNextButton(false); setShowPreviousButton(false)
                val detector = android.view.GestureDetector(it,object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                        // Restrict to the middle video surface; never intercept native timeline/controller buttons.
                        if(event.y < height*.22f || event.y > height*.72f || player.duration <= 0 || !player.isCurrentMediaItemSeekable) return false
                        val delta = if(event.x < width/2f) -10000L else 10000L
                        player.seekTo(uk.co.traynor.privategallery.core.ui.ViewerExperience.seek(player.currentPosition,delta,player.duration))
                        seekFeedback = if(delta<0) "−10 seconds" else "+10 seconds"
                        return true
                    }
                })
                setOnTouchListener { _, event -> detector.onTouchEvent(event) }
            } }, update = { it.player = player; it.keepScreenOn = playing; it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                onRelease = { it.player = null; it.keepScreenOn = false }, modifier = Modifier.fillMaxSize())
            if (error) Surface(Modifier.align(Alignment.Center).padding(20.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (networkAllowed != null) "This stream is unavailable here. Protected or session-dependent media must stay in Browser." else "Unable to play this video on this device.")
                    errorCode?.let { Text("Playback error $it", style = MaterialTheme.typography.labelMedium) }
                    if (onWebViewFallback != null) TextButton(onClick = onWebViewFallback) { Text("Continue in Browser video view") }
                    TextButton(onClick = { if (currentGate?.invoke() != false) { error = false; errorCode = null; preparing = true; player.prepare(); player.play() } }) { Text("Retry") }
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = .5f)).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            IconButton(onClick = { fill = !fill }) { Icon(Icons.Default.AspectRatio, if (fill) "Fit video" else "Fill screen", tint = Color.White) }
            Box {
                TextButton(onClick = { showSpeed = true }) { Text("${speed}×",color=Color.White) }
                DropdownMenu(expanded=showSpeed,onDismissRequest={showSpeed=false}) {
                    listOf(.5f,1f,1.5f,2f).forEach { value -> DropdownMenuItem(text={Text("${value}×")},onClick={speed=value;player.setPlaybackSpeed(value);showSpeed=false}) }
                }
            }
            IconButton(onClick = { muted = !muted; player.volume = if (muted) 0f else 1f }) { Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Unmute" else "Mute", tint = Color.White) }
            IconButton(onClick = { immersive = !immersive }) { Icon(if (immersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle fullscreen", tint = Color.White) }
            if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "Media actions", tint = Color.White) }
        }
        if (preparing && !error) VideoLoadingDialog("Preparing video", null) {
            preparing = false
            onClose?.invoke() ?: player.pause()
        }
        seekFeedback?.let { Text(it,Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.7f)).padding(16.dp),color=Color.White) }
        remaining?.let { Text("$it remaining", Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 16.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}
