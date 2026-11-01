package uk.co.traynor.privategallery.core.evidence

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.net.*
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.privateapps.PrivateAppMetadataTransport
import okhttp3.Request
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.browser.hidden.*
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiRequestBody
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vpn.*

/** The POST under proof always uses the real coordinator's returned route. Reflection is read-only diagnostics. */
class EvidenceOwnedRoute179Test {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val app=instrumentation.targetContext
 private val arguments=InstrumentationRegistry.getArguments()
 private val manager=app.getSystemService(ConnectivityManager::class.java)
 private fun shell(value:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(value)).use{it.readBytes().toString(Charsets.UTF_8)}
 private suspend fun until(label:String,seconds:Int=30,test:()->Boolean){withTimeout(seconds*1000L){while(!test())delay(100)};assertTrue(label,test())}
 private fun networks()=manager.allNetworks.filter{manager.getNetworkCapabilities(it)?.let{c->c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&c.ownerUid==Process.myUid()}==true}
 private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 private fun processes()=app.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().filter{it.uid==Process.myUid()&&it.processName==app.packageName+":hidden_tor"}
 private fun diagnostic(coordinator:EvidenceRouteCoordinator,name:String)=EvidenceRouteCoordinator::class.java.getDeclaredField(name).apply{isAccessible=true}.get(coordinator)
 private fun udp(network:Network,host:String,port:Int,bytes:ByteArray){DatagramSocket().use{socket->
  network.bindSocket(socket);socket.soTimeout=400;socket.send(DatagramPacket(bytes,bytes.size,InetAddress.getByName(host),port))
  try{socket.receive(DatagramPacket(ByteArray(1024),1024));fail("Sink returned data")}catch(expected:SocketTimeoutException){}
 }}
 private fun observation(network:Network?,unix:String?,onion:String?,path:String="/evidence"):JSONObject {
  if(network!=null){
   if(path=="/evidence")return OwnedVpnFixtureClient178.evidence(app,network)
   val current={manager.getNetworkCapabilities(network)?.let{it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&it.ownerUid==Process.myUid()}==true}
   val client=PrivateAppMetadataTransport.vpn(app,network,current).followRedirects(false).retryOnConnectionFailure(false).cache(null)
    .callTimeout(30,TimeUnit.SECONDS).connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS)).build()
   try{return client.newCall(Request.Builder().url("https://native-phase.fixture.invalid$path").build()).execute().use{response->
    check(response.code==200);val bytes=checkNotNull(response.body).byteStream().readNBytes(1_048_577);check(bytes.size<=1_048_576)
    JSONObject(bytes.toString(Charsets.UTF_8))
   }}finally{client.connectionPool.evictAll();client.dispatcher.cancelAll();client.dispatcher.executorService.shutdown()}
  }
  HiddenTorSockets.open(checkNotNull(unix),checkNotNull(onion),80).use{stream->
   stream.output.write("GET $path HTTP/1.1\r\nHost: $onion\r\nConnection: close\r\n\r\n".toByteArray());stream.output.flush()
   val bytes=stream.input.readNBytes(1_048_577);check(bytes.size<=1_048_576)
   val raw=bytes.toString(Charsets.UTF_8);check(raw.lineSequence().first().split(' ')[1]=="200")
   return JSONObject(raw.substringAfter("\r\n\r\n"))
  }
 }
 @Test fun ownedVpnPostCancellationAndAuthorityRevocationNeverFallback()=runBlocking{exercise("vpn_revoke","ownedVpnPostCancellationAndAuthorityRevocationNeverFallback",false,false)}
 @Test fun ownedVpnLossCancelsAdmittedPostAndRejectsOldRoute()=runBlocking{exercise("vpn_loss","ownedVpnLossCancelsAdmittedPostAndRejectsOldRoute",false,true)}
 @Test fun privateUnixTorPostCancellationAndAuthorityRevocationNeverFallback()=runBlocking{exercise("tor_revoke","privateUnixTorPostCancellationAndAuthorityRevocationNeverFallback",true,false)}
 @Test fun torProcessDeathCancelsAdmittedPostAndRejectsOldRoute()=runBlocking{exercise("tor_death","torProcessDeathCancelsAdmittedPostAndRejectsOldRoute",true,true)}

 private suspend fun exercise(case:String,method:String,isTor:Boolean,loss:Boolean)=supervisorScope {
  check(BuildConfig.DEBUG);assertEquals(37,android.os.Build.VERSION.SDK_INT)
  val nonce=UUID.randomUUID().toString().replace("-","")
  val source=checkNotNull(arguments.getString("evidence179SourceSha"));require(source.matches(Regex("[a-f0-9]{40}")))
  val output=File(checkNotNull(arguments.getString("additionalTestOutputDir")))
  val config=checkNotNull(arguments.getString("hiddenVpnConfig"));require(config=="/data/local/tmp/hidden-vpn1762.conf")
  val onionUrl=URI(checkNotNull(arguments.getString("evidence179OnionHttpsUrl")))
  require(onionUrl.scheme=="https"&&onionUrl.host.matches(Regex("[a-z2-7]{56}\\.onion"))&&onionUrl.port==-1&&onionUrl.rawUserInfo==null)
  shell("appops set ${app.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(app));assertNull(manager.boundNetworkForProcess)
  val activity=ActivityScenario.launch(ComponentActivity::class.java)
  val storage=File(app.cacheDir,"owned-route179-$nonce")
  val context=object:ContextWrapper(app){override fun getFilesDir()=File(storage,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(storage,"no-backup").apply{mkdirs()};override fun getApplicationContext():Context=app}
  val session=HiddenSession(SystemClock::elapsedRealtime);assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){79}))
  val access=EvidenceAccess.hidden(context,session){true}
  val engine=WireGuardVpnEngine(OfficialWireGuardBackend(app))
  var coordinator:EvidenceRouteCoordinator?=null;var route:EvidenceRoute?=null
  val operations=JSONArray()
  val result=JSONObject().put("schema",1).put("case",case).put("method",method).put("classname",EvidenceOwnedRoute179Test::class.java.name).put("nonce",nonce).put("source_sha",source).put("mode",if(isTor)"Tor" else "VPN").put("uid",Process.myUid())
  var clock:EvidenceRouteClock179?=null
  try {
   if(case=="vpn_revoke")EvidenceRouteClockLifecycle179(instrumentation,arguments,nonce,source).run(output)
   val routeClock=EvidenceRouteClock179(app,arguments,case,method,nonce,source,instrumentation.javaClass.name);clock=routeClock
   until("Previous transports retired"){HiddenVpnBrowserBroker.admissionRetired()&&!HiddenVpnBrowserBroker.hasDedicatedProcess(app)&&!HiddenTorIsolationService.hasTorProcess(app)&&HiddenTorIsolationService.released()&&networks().isEmpty()}
   val profile=(VpnProfileParser.import("Disposable evidence179",shell("cat $config")) as VpnProfileImportResult.Accepted).profile
   coordinator=withContext(Dispatchers.Main.immediate){EvidenceRouteCoordinator(app,access,
    ownedVpn={if(engine.ownsTunnel&&engine.state==VpnConnectionState.CONNECTED)networks().singleOrNull() else null},
    wireGuardReleased={!engine.ownsTunnel&&networks().isEmpty()},
    prepareVpn={engine.connect(profile);until("Actual WireGuard connected",90){engine.ownsTunnel&&engine.state==VpnConnectionState.CONNECTED}},
    releaseWireGuard={engine.disconnect();Unit},primaryRequiresVpn={false})}
   val connected=checkNotNull(coordinator).connect(if(isTor)"Tor" else "VPN");route=connected
   assertTrue(connected.current());assertEquals(EvidenceCatalog.Owner.HIDDEN,connected.owner)
   val isolation=if(isTor)diagnostic(checkNotNull(coordinator),"isolation") as HiddenTorIsolationConnection else null
   val tor=if(isTor)diagnostic(checkNotNull(coordinator),"tor") as HiddenTorConnection else null
   val pinned=if(isTor)checkNotNull(isolation).network()!! else networks().single()
   val caps=checkNotNull(manager.getNetworkCapabilities(pinned));assertEquals(Process.myUid(),caps.ownerUid)
   val iface=checkNotNull(manager.getLinkProperties(pinned)?.interfaceName);assertTrue(iface.isNotBlank())
   result.put("owner",connected.owner.name).put("route_identity",connected.identity).put("network_handle",pinned.networkHandle).put("network_owner_uid",caps.ownerUid).put("interface",iface).put("process_binding_before_null",manager.boundNetworkForProcess==null)
   var unix:String?=null
   if(isTor){
    val isolated=HiddenTorConnection::class.java.getDeclaredField("isolated").apply{isAccessible=true}.get(tor) as HiddenTorConfiguration
    unix=isolated.unixPath
    val root=File(app.applicationInfo.dataDir).canonicalPath
    assertEquals(File(unix).canonicalPath,unix);assertTrue(unix.startsWith("$root/"))
    val parent=Os.stat(checkNotNull(File(unix).parent));val socket=Os.stat(unix)
    assertEquals(Process.myUid(),parent.st_uid);assertEquals(0,parent.st_mode and 63);assertEquals(Process.myUid(),socket.st_uid);assertEquals(OsConstants.S_IFSOCK,socket.st_mode and OsConstants.S_IFMT)
    assertTrue(checkNotNull(tor).unixReady());assertTrue(tor.alive())
    val proc=processes().single()
    result.put("tor",JSONObject().put("pid",proc.pid).put("uid",proc.uid).put("unix_relative",unix.removePrefix("$root/")).put("parent_uid",parent.st_uid).put("parent_mode",parent.st_mode and 511).put("socket_uid",socket.st_uid).put("socket_type",socket.st_mode and OsConstants.S_IFMT).put("bootstrap",tor.bootstrapProgress()).put("unix_ready",true))
    val before=checkNotNull(isolation).dropCounters()
    udp(pinned,"203.0.113.10",3478,"e179-$nonce-v4".toByteArray());udp(pinned,"2001:db8::10",3478,"e179-$nonce-v6".toByteArray())
    val labels=listOf("e179-$nonce","fixture","invalid")
    val query=byteArrayOf(0x17,0x79,1,0,0,1,0,0,0,0,0,0)+labels.flatMap{listOf(it.length.toByte())+it.toByteArray().toList()}.toByteArray()+byteArrayOf(0,0,1,0,1)
    udp(pinned,"10.252.0.2",53,query)
    until("Actual v4/v6/DNS packets entered TUN"){val a=isolation.dropCounters();a.first>before.first&&a.second>before.second&&a.third>=before.third+3}
    val after=isolation.dropCounters();result.put("sink",JSONObject().put("v4",after.first-before.first).put("v6",after.second-before.second).put("udp",after.third-before.third))
   }
   fun request(phase:String):AiHttpRequest {
    val host=if(isTor)onionUrl.host else "e179-$nonce-$phase.fixture.invalid"
    val path=if(phase=="success")"/evidence179/post" else "/evidence179/slow/$case/$phase"
    val body="{\"case\":\"$case\",\"nonce\":\"$nonce\",\"phase\":\"$phase\"}".toByteArray()
    operations.put(JSONObject().put("phase",phase).put("host",host).put("path",path).put("body_sha256",sha(body)).put("body_length",body.size))
    return AiHttpRequest("POST","https://$host$path",mapOf("Content-Type" to "application/json","X-Evidence179-Case" to nonce),AiRequestBody{it.write(body)},1_048_576)
   }
   suspend fun event(phase:String,kind:String):JSONObject {
    var found:JSONObject?=null
    withTimeout(60_000){while(found==null){
     val observations=withContext(Dispatchers.IO){observation(if(isTor)null else pinned,unix,onionUrl.host).getJSONArray("events")}
     found=(0 until observations.length()).map{observations.getJSONObject(it)}.singleOrNull{it.optString("nonce")==nonce&&it.optString("phase")==phase&&it.optString("kind")==kind}
     if(found==null)delay(100)
    }}
    return checkNotNull(found)
   }
   val response=connected.execute(request("success"));assertEquals(200,response.status);assertArrayEquals("evidence179-post-accepted\n".toByteArray(),response.bytes)
   result.put("response_sha256",sha(response.bytes));response.bytes.fill(0)
   val success=event("success","response");result.put("success_observation",success.getLong("sequence"))
   suspend fun armAction(phase:String,work:Deferred<*>,failedAt:AtomicLong,published:AtomicBoolean):JSONObject {
    check(work.isActive&&!work.isCompleted&&failedAt.get()==0L&&!published.get()){"Request failed before arming action"}
    val token=UUID.randomUUID().toString().replace("-","")
    val sent=SystemClock.elapsedRealtimeNanos()
    val receipt=withContext(Dispatchers.IO){observation(if(isTor)null else pinned,unix,onionUrl.host,"/evidence179/arm/$case/$nonce/$phase/$token").getJSONObject("event")}
    val received=SystemClock.elapsedRealtimeNanos()
    check(receipt.getString("kind")=="action-arm"&&receipt.getString("case")==case&&receipt.getString("nonce")==nonce&&receipt.getString("phase")==phase&&receipt.getString("action_token")==token)
    check(work.isActive&&!work.isCompleted&&failedAt.get()==0L&&!published.get()){"Request failed before destructive action"}
    return JSONObject().put("sequence",receipt.getLong("sequence")).put("token",token).put("sent_elapsed_ns",sent).put("received_elapsed_ns",received)
   }
   fun activeRequest(request:AiHttpRequest,failedAt:AtomicLong,published:AtomicBoolean)=async(Dispatchers.IO){
    try{val received=connected.execute(request);published.set(true);received.bytes.fill(0);Result.success(Unit)}
    catch(failure:Throwable){failedAt.compareAndSet(0,SystemClock.elapsedRealtimeNanos());Result.failure<Unit>(failure)}
   }
   if(!loss){
    val failedAt=AtomicLong();val published=AtomicBoolean()
    val work=activeRequest(request("cancel"),failedAt,published)
    val start=event("cancel","slow-start");val arm=armAction("cancel",work,failedAt,published)
    val clockClaim=routeClock.prepare("cancel",arm)
    check(work.isActive&&!work.isCompleted&&failedAt.get()==0L&&!published.get())
    val action=routeClock.actionTimestamp(clockClaim);work.cancelAndJoin()
    assertTrue(work.isCancelled);assertFalse(published.get());assertTrue(failedAt.get()>=action);assertTrue(connected.current())
    val closed=event("cancel","slow-closed")
    result.put("cancel",JSONObject().put("start",start.getLong("sequence")).put("closed",closed.getLong("sequence")).put("cancelled",work.isCancelled)
     .put("arm",arm).put("live_at_action",true).put("action_elapsed_ns",action).put("request_failed_elapsed_ns",failedAt.get()).put("published",published.get()))
   }
   val phase=if(loss)if(isTor)"death" else "loss" else "revoke"
   val failedAt=AtomicLong();val published=AtomicBoolean()
   val active=activeRequest(request(phase),failedAt,published)
   val admitted=event(phase,"slow-start");val admissionObserved=SystemClock.elapsedRealtimeNanos()
   val targetPid=if(loss&&isTor)processes().single().pid else null
   if(targetPid!=null)assertEquals(result.getJSONObject("tor").getInt("pid"),targetPid)
   val arm=armAction(phase,active,failedAt,published)
   val clockClaim=routeClock.prepare(phase,arm)
   check(active.isActive&&!active.isCompleted&&failedAt.get()==0L&&!published.get())
   val action=routeClock.actionTimestamp(clockClaim)
   if(loss){if(isTor)Process.killProcess(checkNotNull(targetPid)) else engine.disconnect()}else session.lock()
   result.put("retirement",JSONObject().put("phase",phase).put("admitted_sequence",admitted.getLong("sequence")).put("admitted_epoch",admitted.getDouble("epoch"))
    .put("admission_observed_elapsed_ns",admissionObserved).put("arm",arm).put("action_elapsed_ns",action).put("live_at_action",true))
   until("Original route retired"){!connected.current()}
   assertTrue(withTimeout(20_000){active.await()}.isFailure);assertFalse(published.get());assertTrue(failedAt.get()>=action)
   result.getJSONObject("retirement").put("request_failed_elapsed_ns",failedAt.get()).put("published",published.get())
   if(loss){assertTrue(session.isUnlocked);assertTrue(access.current())}else{assertFalse(session.isUnlocked);assertFalse(access.current())}
   if(!isTor&&loss)until("Pinned Network vanished"){manager.getNetworkCapabilities(pinned)==null&&!engine.ownsTunnel}
   if(!isTor&&!loss){assertTrue(engine.ownsTunnel);assertEquals(pinned,networks().single())}
   if(isTor)until("Packaged Tor and sink retired"){processes().isEmpty()&&HiddenTorIsolationService.released()&&manager.getNetworkCapabilities(pinned)==null}
   result.getJSONObject("retirement").put("transport_retired_elapsed_ns",SystemClock.elapsedRealtimeNanos())
   assertTrue(runCatching{connected.execute(request("retired"))}.isFailure)
   result.getJSONObject("retirement").put("active_failed",true).put("old_route_current",connected.current()).put("new_post_rejected",true).put("credentials_retained",session.isUnlocked)
    .put("wireguard_retained",!isTor&&!loss&&engine.ownsTunnel).put("network_lost",!isTor&&loss&&manager.getNetworkCapabilities(pinned)==null).put("tor_absent",isTor&&processes().isEmpty())
    .put("remote_close",if(!isTor&&loss)"unavailable_after_path_loss" else "required_peer_close")
   // Observe a quiet bounded interval after denial, without starting a replacement route/process.
   repeat(10){assertFalse(connected.current());if(isTor)assertTrue(processes().isEmpty());delay(100)}
   assertNull(manager.boundNetworkForProcess);result.put("operations",operations)
  }finally{
   val clockReceipt=retireRouteClock179(clock){
   route?.close();coordinator?.close();access.close();session.lock();engine.disconnect()
   until("All owned services/Networks released",30){!engine.ownsTunnel&&networks().isEmpty()&&HiddenTorIsolationService.released()&&!HiddenTorIsolationService.hasTorProcess(app)&&!HiddenVpnBrowserBroker.hasDedicatedProcess(app)}
   assertNull(manager.boundNetworkForProcess);storage.deleteRecursively();activity.close()
   }
   clockReceipt?.let{result.put("clock",it)}
  }
  result.put("cleanup_retired",true).put("process_binding_after_null",manager.boundNetworkForProcess==null)
  output.mkdirs();val file=File(output,"evidence179-route-$case.json");check(!file.exists());file.writeText(result.toString())
 }
}
