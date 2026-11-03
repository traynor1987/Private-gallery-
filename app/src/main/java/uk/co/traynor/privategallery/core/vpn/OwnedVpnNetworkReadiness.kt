package uk.co.traynor.privategallery.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Process
import java.io.Closeable

/** Observes framework usability across Main stop/start; callbacks never query synchronous snapshots. */
internal class OwnedVpnNetworkReadiness(context:Context):Closeable {
    private val manager=context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val gate=OwnedVpnNetworkReadinessGate<Network>()
    private val registrationLock=Any()
    private var attempted=false
    private var closed=false
    private var callback:ConnectivityManager.NetworkCallback?=null

    private fun registerOnce()=synchronized(registrationLock) {
        if(closed || attempted)return@synchronized
        attempted=true
        val epoch=gate.beginRegistration()
        val observer=object:ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network:Network){gate.available(epoch,network)}
            override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){
                gate.capabilities(epoch,network,capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && capabilities.ownerUid==Process.myUid())
            }
            override fun onLinkPropertiesChanged(network:Network,properties:LinkProperties){
                val dns=properties.dnsServers
                gate.linkProperties(epoch,network,!properties.interfaceName.isNullOrBlank() && dns.size in 1..8 && dns.all {
                    !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isMulticastAddress
                })
            }
            override fun onBlockedStatusChanged(network:Network,blocked:Boolean){gate.blocked(epoch,network,blocked)}
            override fun onLost(network:Network){gate.lost(epoch,network)}
        }
        callback=observer
        try {
            // Observe owned VPNs without default NOT_VPN/TRUSTED/NOT_RESTRICTED exclusions.
            val request=NetworkRequest.Builder().clearCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN).build()
            manager.registerNetworkCallback(request,observer)
        }catch(_:RuntimeException) {
            gate.registrationFailed(epoch);callback=null
            runCatching {manager.unregisterNetworkCallback(observer)}
        }
    }
    fun isReady(network:Network):Boolean {registerOnce();return gate.isReady(network)}
    override fun close() {
        val observer=synchronized(registrationLock) {
            closed=true;gate.close();callback.also {callback=null}
        }
        observer?.let {runCatching {manager.unregisterNetworkCallback(it)}}
    }
}
