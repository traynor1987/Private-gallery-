package uk.co.traynor.privategallery.ui.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import uk.co.traynor.privategallery.core.ui.status.SystemStatusProvider
import uk.co.traynor.privategallery.core.ui.status.SystemStatusSnapshot
import uk.co.traynor.privategallery.core.ui.status.SystemStatusSnapshot.Transport

/** Read-only system events; no timers, phone-state/location requests, identifiers or routing changes. */
internal class AndroidSystemStatusProvider(context: Context) : SystemStatusProvider {
    private val application = context.applicationContext

    override fun subscribe(observer: Consumer<SystemStatusSnapshot>): AutoCloseable {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Status subscriptions belong to the UI thread" }
        return Subscription(application, observer).also { it.start() }
    }

    private class Subscription(
        private val context: Context,
        private val observer: Consumer<SystemStatusSnapshot>
    ) : AutoCloseable {
        private val main = Handler(Looper.getMainLooper())
        private val closed = AtomicBoolean(false)
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val power = context.getSystemService(PowerManager::class.java)
        private val telephony = context.getSystemService(TelephonyManager::class.java)
        private var percent: Int? = null
        private var plugged = false
        private var charging = false
        private var saver = false
        private var airplane = false
        private var defaultNetwork: Network? = null
        private var defaultCapabilities: NetworkCapabilities? = null
        private val associatedWifi = mutableSetOf<Network>()
        private var networkKnown = false
        private var radio: String? = null
        private var radioManager: TelephonyManager? = null
        private var radioCallback: TelephonyCallback? = null
        // An ephemeral Android routing handle, never exposed, logged or persisted as device metadata.
        private var radioSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        private var receiverRegistered = false
        private var defaultRegistered = false
        private var wifiRegistered = false

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (closed.get()) return
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) readBattery(intent)
                saver = power?.isPowerSaveMode == true
                airplane = readAirplane()
                emit()
            }
        }
        private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (closed.get()) return
                defaultNetwork = network
                defaultCapabilities = null // Never reuse the old route's validation or radio label.
                networkKnown = true
                radio = null
                stopRadio()
                emit()
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (closed.get() || network != defaultNetwork) return
                defaultCapabilities = capabilities
                refreshRadio()
                emit()
            }
            override fun onLost(network: Network) {
                if (closed.get() || network != defaultNetwork) return
                defaultNetwork = null
                defaultCapabilities = null
                radio = null
                stopRadio()
                emit()
            }
        }
        private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (closed.get()) return
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) associatedWifi.add(network)
                else associatedWifi.remove(network)
                emit()
            }
            override fun onLost(network: Network) {
                if (closed.get()) return
                associatedWifi.remove(network)
                emit()
            }
        }

        fun start() {
            saver = power?.isPowerSaveMode == true
            airplane = readAirplane()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            try {
                val sticky = context.registerReceiver(receiver, filter, null, main, Context.RECEIVER_NOT_EXPORTED)
                receiverRegistered = true
                sticky?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }?.let(::readBattery)
            } catch (_: SecurityException) { /* Leave unavailable battery details omitted. */ }
            try {
                connectivity?.registerDefaultNetworkCallback(defaultCallback, main)
                defaultRegistered = connectivity != null
                // One initial snapshot closes the "no network means no callback" gap. No repeated queries.
                defaultNetwork = connectivity?.activeNetwork
                defaultCapabilities = defaultNetwork?.let { connectivity?.getNetworkCapabilities(it) }
                networkKnown = connectivity != null
                connectivity?.registerNetworkCallback(NetworkRequest.Builder()
                    .clearCapabilities().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback, main)
                wifiRegistered = connectivity != null
                refreshRadio()
            } catch (_: RuntimeException) { /* Security/OEM limits: omit unknown network data. */ }
            emit()
        }

        private fun readBattery(intent: Intent) {
            percent = SystemStatusSnapshot.batteryPercent(
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        }

        private fun readAirplane(): Boolean = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: SecurityException) { false }

        private fun refreshRadio() {
            if (defaultCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) {
                radio = null
                stopRadio()
                return
            }
            // DisplayInfoListener reports the platform/carrier's actual status label and requires no
            // phone-state permission on supported current Android. getDataNetworkType is deliberately
            // unused: it requires READ_PHONE_STATE/READ_BASIC_PHONE_STATE. OEM denial stays unknown.
            val active = try { SubscriptionManager.getActiveDataSubscriptionId() }
                catch (_: RuntimeException) { SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            if (!SubscriptionManager.isValidSubscriptionId(active) || telephony == null) {
                radio = null
                stopRadio()
                return
            }
            if (radioSubscription == active && radioCallback != null) return
            stopRadio()
            radio = null
            try {
                val manager = telephony.createForSubscriptionId(active)
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                        if (closed.get() || radioSubscription != active) return
                        // A queued old-SIM callback must never label a newer active data route.
                        val stillActive = try { SubscriptionManager.getActiveDataSubscriptionId() == active }
                            catch (_: RuntimeException) { false }
                        if (!stillActive) { refreshRadio(); emit(); return }
                        radio = displayTechnology(info)
                        emit()
                    }
                }
                radioManager = manager
                radioCallback = callback
                radioSubscription = active
                manager.registerTelephonyCallback(TelephonyManager.INCLUDE_LOCATION_DATA_NONE, context.mainExecutor, callback)
            } catch (_: RuntimeException) { stopRadio(); radio = null }
        }

        private fun stopRadio() {
            val manager = radioManager
            val callback = radioCallback
            radioSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID
            radioManager = null
            radioCallback = null
            if (callback != null) try { manager?.unregisterTelephonyCallback(callback) } catch (_: RuntimeException) { }
        }

        private fun emit() {
            if (closed.get()) return
            val caps = defaultCapabilities
            val transport = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> Transport.WIFI
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> Transport.MOBILE
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> Transport.ETHERNET
                caps != null -> Transport.OTHER
                defaultNetwork != null -> Transport.UNKNOWN // Await this route's capability callback.
                associatedWifi.isNotEmpty() -> Transport.WIFI
                networkKnown -> Transport.DISCONNECTED
                else -> Transport.UNKNOWN
            }
            observer.accept(SystemStatusSnapshot(percent, plugged, charging, saver, airplane, transport,
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                if (transport == Transport.MOBILE) radio else null))
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            val cleanup = {
                if (receiverRegistered) try { context.unregisterReceiver(receiver) } catch (_: RuntimeException) { }
                if (defaultRegistered) try { connectivity?.unregisterNetworkCallback(defaultCallback) } catch (_: RuntimeException) { }
                if (wifiRegistered) try { connectivity?.unregisterNetworkCallback(wifiCallback) } catch (_: RuntimeException) { }
                stopRadio()
                associatedWifi.clear()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) cleanup() else main.post { cleanup() }
        }
    }
}

private fun displayTechnology(info: TelephonyDisplayInfo): String? = when (info.overrideNetworkType) {
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G"
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE+"
    else -> when (info.networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA -> "H"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "H+"
        else -> null
    }
}
