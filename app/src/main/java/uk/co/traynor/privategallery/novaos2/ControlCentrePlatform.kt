package uk.co.traynor.privategallery.novaos2

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener

internal data class ControlDeviceStatus(
    val battery: Int?,
    val charging: Boolean,
    val connection: String,
    val autoRotate: Boolean?,
    val torchOn: Boolean?,
)

internal data class ControlPrivacyPermissions(val camera: Boolean, val microphone: Boolean, val location: String)

/** Reports NovaOS permission grants. Android manages the device-wide activity indicators and dashboard. */
internal fun controlPrivacyPermissions(context: Context): ControlPrivacyPermissions {
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return ControlPrivacyPermissions(
        camera = granted(Manifest.permission.CAMERA),
        microphone = granted(Manifest.permission.RECORD_AUDIO),
        location = when {
            granted(Manifest.permission.ACCESS_FINE_LOCATION) -> "Precise allowed"
            granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> "Approximate allowed"
            else -> "Denied"
        },
    )
}

internal class ControlCentreDevice(context: Context) {
    private val context = context.applicationContext
    private val network = this.context.getSystemService(ConnectivityManager::class.java)
    private val camera = this.context.getSystemService(CameraManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val torch = runCatching {
        val manager = camera ?: return@runCatching null
        manager.cameraIdList.firstOrNull { id ->
            val details = manager.getCameraCharacteristics(id)
            details.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                details.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    fun windowBrightness(): Float? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            .takeIf { it in 0..255 }?.div(255f)
    }.getOrNull()

    fun setTorch(on: Boolean): Boolean {
        val id = torch ?: return false
        val manager = camera ?: return false
        return runCatching { manager.setTorchMode(id, on) }.isSuccess
    }

    fun watch() = callbackFlow {
        var battery: Intent? = null
        var torchOn: Boolean? = null
        var active = true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        fun update() {
            if (!active) return
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val charging = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            trySend(ControlDeviceStatus(
                battery = controlBatteryPercent(level, scale),
                charging = charging,
                connection = networkStatus(),
                autoRotate = runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
                }.getOrNull(),
                torchOn = torchOn,
            ))
        }
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                battery = intent
                update()
            }
        }
        val batteryRegistered = runCatching {
            battery = context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                main.post { update() }
            }

            override fun onLost(network: Network) {
                main.post { update() }
            }
        }
        val networkRegistered = runCatching {
            network?.registerDefaultNetworkCallback(connectivityCallback)
        }.isSuccess
        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (torch == cameraId) {
                    torchOn = enabled
                    update()
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (torch == cameraId) {
                    torchOn = null
                    update()
                }
            }
        }
        val torchRegistered = torch != null && runCatching {
            camera?.registerTorchCallback(context.mainExecutor, torchCallback)
        }.isSuccess
        update()
        awaitClose {
            active = false
            if (batteryRegistered) runCatching { context.unregisterReceiver(batteryReceiver) }
            if (networkRegistered) runCatching { network?.unregisterNetworkCallback(connectivityCallback) }
            if (torchRegistered) runCatching { camera?.unregisterTorchCallback(torchCallback) }
        }
    }

    private fun networkStatus(): String = runCatching {
        val manager = network ?: return@runCatching "Network unavailable"
        val active = manager.activeNetwork ?: return@runCatching "Offline"
        val capabilities = manager.getNetworkCapabilities(active) ?: return@runCatching "Network unavailable"
        val name = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Network"
        }
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) name
        else "$name · Internet unverified"
    }.getOrDefault("Network unavailable")
}

internal fun controlBatteryPercent(level: Int, scale: Int): Int? =
    if (scale > 0 && level in 0..scale) ((100L * level + scale / 2) / scale).toInt() else null

internal enum class ControlTransport { PREVIOUS, PLAY_PAUSE, NEXT }

internal data class ControlPlayback(
    val session: MediaSession.Token,
    val playing: Boolean,
    val actions: Set<ControlTransport>,
)

/** Reuses the existing approved listener only for explicitly enabled media transport. */
internal class ControlCentreMedia(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(this.context, GuardianNotificationListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    fun permissionAvailable(): Boolean = runCatching {
        GuardianNotificationListener.hasAccess(context)
    }.getOrDefault(false)

    private fun sessions(): List<MediaController> =
        if (permissionAvailable()) runCatching { manager?.getActiveSessions(listener).orEmpty() }
            .getOrDefault(emptyList()) else emptyList()

    private fun choose(candidates: List<MediaController>): MediaController? =
        candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull { it.playbackState != null }

    fun watch() = callbackFlow {
        var selected: MediaController? = null
        val playback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val current = selected ?: return
                trySend(current.playbackState?.snapshot(current.sessionToken))
            }

            override fun onSessionDestroyed() {
                trySend(null)
            }
        }
        fun select(candidates: List<MediaController>) {
            selected?.unregisterCallback(playback)
            selected = choose(candidates)
            selected?.registerCallback(playback, main)
            trySend(selected?.let { it.playbackState?.snapshot(it.sessionToken) })
        }
        val observer = MediaSessionManager.OnActiveSessionsChangedListener { select(it.orEmpty()) }
        val registered = runCatching { manager?.addOnActiveSessionsChangedListener(observer, listener, main) }.isSuccess
        select(sessions())
        awaitClose {
            selected?.unregisterCallback(playback)
            if (registered) runCatching { manager?.removeOnActiveSessionsChangedListener(observer) }
        }
    }

    fun send(session: MediaSession.Token, action: ControlTransport): Boolean = runCatching {
        val controller = sessions().firstOrNull { it.sessionToken == session } ?: return false
        val state = controller.playbackState ?: return false
        val snapshot = state.snapshot(session)
        if (action !in snapshot.actions) return false
        when (action) {
            ControlTransport.PREVIOUS -> controller.transportControls.skipToPrevious()
            ControlTransport.NEXT -> controller.transportControls.skipToNext()
            ControlTransport.PLAY_PAUSE -> {
                val supported = if (snapshot.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                if (state.actions and supported == 0L) {
                    val key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    val sent = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
                    controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, key))
                    return sent
                }
                if (snapshot.playing) controller.transportControls.pause() else controller.transportControls.play()
            }
        }
    }.isSuccess
}

private fun PlaybackState.snapshot(session: MediaSession.Token): ControlPlayback {
    val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    val expected = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    val controls = buildSet {
        if (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) add(ControlTransport.PREVIOUS)
        if (actions and (expected or PlaybackState.ACTION_PLAY_PAUSE) != 0L) add(ControlTransport.PLAY_PAUSE)
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) add(ControlTransport.NEXT)
    }
    return ControlPlayback(session, playing, controls)
}
