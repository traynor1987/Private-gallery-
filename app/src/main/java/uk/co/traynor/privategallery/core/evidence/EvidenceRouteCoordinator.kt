package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.net.Network
import android.net.VpnService
import android.os.Binder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.browser.hidden.*

internal interface EvidenceRouteConnection:AutoCloseable { suspend fun connect(mode:String):EvidenceRoute }

/** Starts existing owned transports, never browser renderers/profiles or a direct Hidden connection. */
internal class EvidenceRouteCoordinator(
 private val context:Context,private val access:EvidenceAccess,
 private val ownedVpn:()->Network?,private val wireGuardReleased:()->Boolean,
 private val prepareVpn:suspend ()->Unit,private val releaseWireGuard:()->Unit,
 private val primaryRequiresVpn:()->Boolean,
):EvidenceRouteConnection {
 private val ended=AtomicBoolean()
 private val main=Handler(Looper.getMainLooper())
 private var isolation:HiddenTorIsolationConnection?=null
 private var tor:HiddenTorConnection?=null
 private var route:EvidenceRoute?=null
 private var probe:HiddenHttpTransport?=null
 private val torOwner=Binder()
 private var starting=false
 private var retirement:AutoCloseable?=null
 init {retirement=access.onRetire{close()};if(ended.get())retirement?.close()}
 private fun current()=!ended.get()&&access.current()
 private fun requireMain(){check(Looper.myLooper()===Looper.getMainLooper())}
 override suspend fun connect(mode:String):EvidenceRoute=withContext(Dispatchers.Main.immediate) {
  requireMain();check(current()){"Evidence transport authority retired"}
  require(!starting&&route==null){"Close the previous evidence transport first"}
  starting=true
  try {
   withTimeout(180000) {
    when(mode) {
     "System" -> {
      require(access.owner==EvidenceCatalog.Owner.PRIMARY&&!primaryRequiresVpn()){"This vault requires its authorised VPN transport"}
      EvidenceNetwork.primarySystem{current()&&!primaryRequiresVpn()}
     }
     "VPN" -> {
      require(VpnService.prepare(context)==null){"Android VPN permission is required before connecting"}
      check(!HiddenTorIsolationService.hasTorProcess(context)&&HiddenTorIsolationService.released()){"Another private Tor transport is active"}
      prepareVpn();check(current())
      var network=ownedVpn()
      while(network==null){check(current());delay(200);network=ownedVpn()}
      val pinned=network
      val authorized={current()&&ownedVpn()==pinned}
      // Existing HTTPS+DNS readiness verification uses the identical pinned Network.
      val readiness=HiddenHttpTransport.vpn(context,pinned,authorized);probe=readiness
      try{withContext(Dispatchers.IO){readiness.verifyReady()}}finally{readiness.close();probe=null}
      check(authorized())
      EvidenceNetwork.ownedVpn(access.owner,context,pinned,authorized,onClose={close()})
     }
     "Tor" -> {
      require(access.owner==EvidenceCatalog.Owner.HIDDEN){"Tor evidence route belongs to Hidden"}
      require(VpnService.prepare(context)==null){"Android isolation permission is required before connecting"}
      check(HiddenVpnBrowserBroker.admissionRetired()&&!HiddenVpnBrowserBroker.hasDedicatedProcess(context)&&!HiddenTorIsolationService.hasTorProcess(context)&&HiddenTorIsolationService.released()){"Another private transport is active"}
      releaseWireGuard()
      while(!wireGuardReleased()){check(current());delay(100)}
      check(current())
      val boundary=HiddenTorIsolationConnection(context,{current()},{close()})
      isolation=boundary;boundary.start()
      var network=boundary.network()
      while(network==null){check(current());delay(100);network=boundary.network()}
      val pinned=network;check(current())
      val configuration=boundary.configuration(torOwner)
      val client=HiddenTorConnection(context,configuration);tor=client;client.start()
      while(!withContext(Dispatchers.IO){client.unixReady()}){check(current());delay(250)}
      val authorized={current()&&client.alive()&&boundary.network()==pinned}
      check(authorized())
      EvidenceNetwork.tor(context,configuration.unixPath,authorized,onClose={close()})
     }
     else -> error("Choose an authorised evidence transport")
    }.also{connected ->
     if(!current()){connected.close();throw CancellationException("Evidence route retired")}
     route=connected
    }
   }
  }catch(failure:Throwable){close();throw failure}finally{starting=false}
 }
 override fun close(){
  // Admission closes immediately even if Android service cleanup must be posted to Main.
  if(!ended.compareAndSet(false,true))return
  route?.close()
  val cleanup=Runnable {
   route?.close();route=null
   probe?.close();probe=null
   tor?.close();tor=null
   isolation?.close();isolation=null
   retirement?.close()
  }
  if(Looper.myLooper()===Looper.getMainLooper())cleanup.run()else main.post(cleanup)
 }
}
