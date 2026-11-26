package uk.co.traynor.privategallery.novaos2.working

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

internal class WorkingPairedDevice(val identifier: String, val label: String?) {
    override fun toString() = "WorkingPairedDevice(<redacted>)"
}

internal fun workingVehiclePermission(context: Context) =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Only the visible selector reads bonded-device names. No discovery or pairing is initiated. */
internal fun workingPairedDevices(context: Context): List<WorkingPairedDevice>? = runCatching {
    if (!workingVehiclePermission(context)) return null
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
    if (!adapter.isEnabled) return null
    adapter.bondedDevices.mapNotNull { device ->
        val id = device.address
        if (!isWorkingVehicleIdentifier(id)) return@mapNotNull null
        val label = device.name?.take(100)?.filterNot(Char::isISOControl)?.trim()
            ?.takeIf { it.isNotEmpty() && !isWorkingVehicleIdentifier(it.uppercase()) }
        WorkingPairedDevice(id, label)
    }.sortedWith(compareBy<WorkingPairedDevice> { it.label.orEmpty().lowercase() }.thenBy { it.identifier })
}.getOrNull()

@Composable
internal fun workingVehicleSignal(identifier: String?, permissionRevision: Int, allowed: () -> Boolean): State<WorkingVehicleSignal> {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val foreground = allowed()
    val latestAllowed = rememberUpdatedState(allowed)
    return produceState(WorkingVehicleSignal.OFF, context, identifier, permissionRevision, lifecycle, foreground) {
        value = WorkingVehicleSignal.OFF
        if (identifier == null || !foreground) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!latestAllowed.value()) return@repeatOnLifecycle
            if (!workingVehiclePermission(context)) {
                value = WorkingVehicleSignal.PERMISSION
                return@repeatOnLifecycle
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) {
                value = WorkingVehicleSignal.UNAVAILABLE
                return@repeatOnLifecycle
            }
            val monitor = VehicleAudioMonitor(context, adapter, identifier) { observed ->
                if (latestAllowed.value()) value = observed
            }
            try {
                if (!monitor.start()) {
                    value = WorkingVehicleSignal.UNAVAILABLE
                    return@repeatOnLifecycle
                }
                delay(4_000)
                monitor.finishInitialCheck()
                awaitCancellation()
            } finally {
                monitor.close()
                value = WorkingVehicleSignal.OFF
            }
        }
    }
}

/** Receiver events are hints. Only a fresh bonded-device check and official profile proxies establish a connection. */
private class VehicleAudioMonitor(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val identifier: String,
    private val publish: (WorkingVehicleSignal) -> Unit,
) : AutoCloseable {
    private val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
    private val requested = mutableSetOf<Int>()
    private val available = mutableSetOf<Int>()
    private val proxies = mutableMapOf<Int, BluetoothProfile>()
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var closed = false
    private var initial = true

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            handler.post {
                if (closed) closeProxy(profile, proxy) else {
                    proxies.put(profile, proxy)?.takeIf { it !== proxy }?.let { closeProxy(profile, it) }
                    available += profile
                    refresh()
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            handler.post {
                if (!closed) {
                    available -= profile
                    refresh()
                }
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed || intent.action !in signals) return
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) requestProfiles()
            refresh()
        }
    }

    fun start(): Boolean = runCatching {
        context.registerReceiver(receiver, IntentFilter().apply { signals.forEach(::addAction) },
            Manifest.permission.BLUETOOTH_CONNECT, handler, Context.RECEIVER_EXPORTED)
        registered = true
        requestProfiles()
        refresh()
    }.isSuccess

    fun finishInitialCheck() {
        initial = false
        refresh()
    }

    private fun requestProfiles() {
        if (runCatching { adapter.state }.getOrNull() != BluetoothAdapter.STATE_ON) return
        for (profile in profiles) {
            if (profile !in requested && runCatching { adapter.getProfileProxy(context, profileListener, profile) }.getOrDefault(false)) {
                requested += profile
            }
        }
    }

    private fun refresh() {
        if (closed) return
        val signal = runCatching { readSignal() }.getOrElse {
            if (workingVehiclePermission(context)) WorkingVehicleSignal.UNAVAILABLE else WorkingVehicleSignal.PERMISSION
        }
        publish(if (signal == WorkingVehicleSignal.UNAVAILABLE && initial) WorkingVehicleSignal.CHECKING else signal)
    }

    private fun readSignal(): WorkingVehicleSignal {
        if (!workingVehiclePermission(context)) return WorkingVehicleSignal.PERMISSION
        when (adapter.state) {
            BluetoothAdapter.STATE_OFF -> return WorkingVehicleSignal.RADIO_OFF
            BluetoothAdapter.STATE_ON -> Unit
            else -> return WorkingVehicleSignal.CHECKING
        }
        val device = adapter.getRemoteDevice(identifier)
        if (device.bondState != BluetoothDevice.BOND_BONDED) return WorkingVehicleSignal.UNPAIRED
        fun connected(profile: Int): Boolean? {
            val observed = if (profile in available) runCatching { proxies[profile]?.getConnectionState(device) }.getOrNull() else null
            return when (observed) {
                BluetoothProfile.STATE_CONNECTED -> true
                BluetoothProfile.STATE_DISCONNECTED -> false
                else -> null
            }
        }
        return verifiedVehicleSignal(connected(BluetoothProfile.A2DP), connected(BluetoothProfile.HEADSET))
    }

    override fun close() {
        closed = true
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        proxies.forEach(::closeProxy)
        proxies.clear()
    }

    private fun closeProxy(profile: Int, proxy: BluetoothProfile) {
        runCatching { adapter.closeProfileProxy(profile, proxy) }
    }

    private companion object {
        val signals = setOf(BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
    }
}
