package uk.co.traynor.privategallery.core.browser.hidden

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Network
import android.net.VpnService
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.*
import java.net.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession

/** Real packaged Tor + sink + protected relay proxy + JS-off native child. Never legacy GET proof. */
class NativeTorIsolation1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation get()=FixtureAccessibility178.automation(instrumentation)
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,predicate:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+seconds*1000L
        while(SystemClock.elapsedRealtime()<deadline){if(runCatching(predicate).getOrDefault(false))return;Thread.sleep(100)}
        val satisfied=runCatching(predicate).getOrDefault(false)
        if(!satisfied)nodes().take(100).forEach {node ->
            val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
            android.util.Log.e("NativeTor1762", "class=${node.className} text=${node.text} description=${node.contentDescription} visible=${node.isVisibleToUser} enabled=${node.isEnabled} clickable=${node.isClickable} window=${node.windowId} bounds=$bounds")
        }
        assertTrue(message,satisfied)
    }
    private fun nodes():List<AccessibilityNodeInfo> {
        automation.clearCache()
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?){if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return result
    }
    private fun click(text:String){waitFor("Action $text"){
        var node=nodes().firstOrNull {it.text?.toString()==text || it.contentDescription?.toString()==text} ?: return@waitFor false
        while(!node.isClickable){node=node.parent ?: return@waitFor false}
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }}
    /** WebView virtual ACTION_CLICK can accept a container action without activating HTML.
     * Tap the exact visible virtual node, as a user does; server/body assertions prove effect. */
    private fun clickWeb(text:String) {
        var target:AccessibilityNodeInfo?=null
        waitFor("Visible web control $text") {
            target=nodes().firstOrNull {it.isVisibleToUser && it.isEnabled && (it.text?.toString()==text || it.contentDescription?.toString()==text)}
            target?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            target!=null
        }
        val window=checkNotNull(target).windowId
        val bounds=android.graphics.Rect()
        var previousBounds:android.graphics.Rect?=null
        waitFor("Fresh visible web touch target $text") {
            val root=automation.rootInActiveWindow ?: return@waitFor false
            if(root.windowId!=window)return@waitFor false
            val fresh=nodes().firstOrNull {it.windowId==window && it.isVisibleToUser && it.isEnabled && (it.text?.toString()==text || it.contentDescription?.toString()==text)} ?: return@waitFor false
            fresh.getBoundsInScreen(bounds)
            val visible=android.graphics.Rect();root.getBoundsInScreen(visible)
            val inWindow=!bounds.isEmpty && bounds.intersect(visible)
            val stable=inWindow && previousBounds==bounds
            previousBounds=if(inWindow)android.graphics.Rect(bounds) else null
            stable
        }
        android.util.Log.i("NativeTor1762", "touch text=$text window=$window bounds=$bounds")
        val at=android.os.SystemClock.uptimeMillis()
        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(at,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue("Web touch delivered",automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun navigate(url:String){
      var focusTouched=false
      var previousFocusBounds:android.graphics.Rect?=null
      waitFor("Native Tor address publication"){
        val field=nodes().firstOrNull {it.isEditable && it.isVisibleToUser && it.isEnabled} ?: return@waitFor false
        if(!field.isFocused) {
            if(!focusTouched) {
                val bounds=android.graphics.Rect();field.getBoundsInScreen(bounds)
                val root=automation.rootInActiveWindow ?: return@waitFor false
                val screen=android.graphics.Rect();root.getBoundsInScreen(screen)
                if(root.windowId!=field.windowId || bounds.isEmpty || !screen.contains(bounds))return@waitFor false
                val stable=previousFocusBounds==bounds
                previousFocusBounds=android.graphics.Rect(bounds)
                if(!stable)return@waitFor false
                val at=android.os.SystemClock.uptimeMillis()
                for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                    val event=android.view.MotionEvent.obtain(at,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try {assertTrue("Address focus touch delivered",automation.injectInputEvent(event,true))}finally{event.recycle()}
                }
                focusTouched=true
            }
            return@waitFor false
        }
        if(field.text?.toString()!=url) {
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,url)})
            return@waitFor false
        }
        true
    };android.util.Log.i("NativeTor1762","Requested address published before Go=true");clickWeb("Go")}
    private fun evidence(path:String,host:String):JSONObject {
        HiddenTorSockets.open(path,host,80).use {stream ->
            stream.output.write("GET /evidence HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray());stream.output.flush()
            val raw=stream.input.readBytes().toString(Charsets.UTF_8)
            require(raw.startsWith("HTTP/1.0 200") || raw.startsWith("HTTP/1.1 200"))
            return JSONObject(raw.substringAfter("\r\n\r\n"))
        }
    }
    private fun observed(path:String,host:String,predicate:(JSONObject)->Boolean):Boolean {
        val events=evidence(path,host).getJSONArray("events")
        return (0 until events.length()).any {predicate(events.getJSONObject(it))}
    }
    private fun udp(network:Network,address:InetAddress,port:Int,bytes:ByteArray) {
        java.net.DatagramSocket().use {socket ->network.bindSocket(socket);socket.soTimeout=350;socket.send(DatagramPacket(bytes,bytes.size,address,port))
            try {socket.receive(DatagramPacket(ByteArray(1024),1024));fail("Sink returned network data")}catch(expected:SocketTimeoutException){}
        }
    }
    @Test(timeout=360_000) fun packagedTorNativeFormsUploadsDownloadsAndNetworkSink() {
        val rootUrl=InstrumentationRegistry.getArguments().getString("hiddenOnionProbeUrl")
        assumeTrue("Controlled real onion required",rootUrl!=null)
        check(BuildConfig.DEBUG)
        val origin=URI(checkNotNull(rootUrl));val host=checkNotNull(origin.host);require(host.endsWith(".onion"))
        val base="http://$host"
        val context=instrumentation.targetContext
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull("Local Tor isolation needs Android VPN consent, never a provider profile",VpnService.prepare(context))
        val info=automation.serviceInfo;info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.serviceInfo=info
        val session=HiddenSession(SystemClock::elapsedRealtime);assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){41}))
        val allowed=AtomicBoolean(true);val ended=AtomicBoolean()
        val storage=File(context.noBackupFilesDir,"native-tor-test-${UUID.randomUUID()}")
        var isolation:HiddenTorIsolationConnection?=null;var tor:HiddenTorConnection?=null;var broker:HiddenVpnBrowserBroker?=null
        var retainedUpload:ParcelFileDescriptor?=null
        try {
            waitFor("Previous native mode fully retired",15){HiddenTorIsolationService.released() && !HiddenTorIsolationService.hasTorProcess(context) && !HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
            ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
                scenario.onActivity {isolation=HiddenTorIsolationConnection(context,{allowed.get()},{allowed.set(false)}).also {it.start()}}
                waitFor("Owned IPv4+IPv6 DNS sink Network"){isolation?.network()!=null}
                val network=checkNotNull(isolation!!.network())
                val config=isolation!!.configuration(Binder())
                assertTrue("Tor SOCKS is app-private Unix",config.unixPath.startsWith(File(context.applicationInfo.dataDir,"ts").canonicalPath+"/"))
                scenario.onActivity {tor=HiddenTorConnection(context,config).also {it.start()}}
                var reportedProgress=-1
                var progressCheckedAt=0L
                waitFor("Packaged Tor bootstrap through authenticated protected numeric relay bridge",120){
                    val now=SystemClock.elapsedRealtime()
                    if(now-progressCheckedAt>=1000) {
                        progressCheckedAt=now
                        val progress=tor?.bootstrapProgress() ?: 0
                        if(progress!=reportedProgress){android.util.Log.i("NativeTor1762","Bootstrap progress=$progress");reportedProgress=progress}
                    }
                    tor?.unixReady()==true
                }
                val path=config.unixPath
                // Bootstrap is not fixture availability. Establish this controlled onion through
                // the real app-private SOCKS path before asking WebView to render the probe.
                val expected=checkNotNull(InstrumentationRegistry.getArguments().getString("hiddenOnionProbeExpected"))
                HiddenTorSockets.open(path,host,80,handshakeTimeoutMs=60_000).use {stream ->
                    stream.output.write("GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray());stream.output.flush()
                    val bytes=stream.input.readNBytes(4097)
                    assertTrue("Controlled readiness response is bounded",bytes.size<=4096)
                    val raw=try {bytes.toString(Charsets.UTF_8)}finally{bytes.fill(0)}
                    assertEquals("Controlled onion returns HTTP200 through the owned private Tor path","200",raw.lineSequence().first().split(' ').getOrNull(1))
                    assertEquals("Controlled onion fixture payload is ready",expected,raw.substringAfter("\r\n\r\n").trim())
                }
                val repoContext=object:ContextWrapper(context){override fun getNoBackupFilesDir()=storage}
                val repo=HiddenRepository(repoContext,session)
                val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
                val png=try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.recycle()}
                val sourceHash=java.security.MessageDigest.getInstance("SHA-256").digest(png).joinToString(""){"%02x".format(it)}
                val source=try {repo.importMedia("Tor eligible fixture.png","image/png",png.inputStream())}finally{png.fill(0)}
                scenario.onActivity {activity ->
                    val ownedContext=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=storage}
                    broker=HiddenVpnBrowserBroker(ownedContext,session,network,{allowed.get() && tor?.unixReady()==true && isolation?.network()==network},{ended.set(true)},mode=HiddenConnectionPolicy.Mode.TOR_ONLY,torUnixPath=path)
                }
                checkNotNull(broker).verifyTorReady()
                scenario.onActivity {broker!!.launch()}
                waitFor("Native JS-off child is attached to immutable sink"){broker!!.childSecuritySnapshot()==(false to true)}
                navigate("$base/script-probe")
                // Match the native 60-second page deadline plus scheduling grace. A 30-second
                // wait can terminate a still-authorized onion load before the browser's failure surface.
                waitFor("Tor script probe rendered within native page deadline",65){nodes().any {it.text?.toString()=="Tor script probe"}}
                assertFalse("Native Tor JS is disabled",broker!!.childSecuritySnapshot().first)
                navigate("$base/interactive");clickWeb("Submit Tor form")
                waitFor("Native JS-free form body",60){observed(path,host){it.optString("path")=="/form-result" && it.optBoolean("form_ok")}}
                navigate("$base/interactive");clickWeb("Choose Tor upload")
                waitFor("Actual JS-off child renders its upload chooser") {nodes().any {it.text?.toString()=="Choose from Hidden Vault"}}
                click("Tor eligible fixture.png");click("Allow upload")
                // Inspect only the grant already minted by the real separate owner confirmation.
                @Suppress("UNCHECKED_CAST")
                val grants=HiddenVpnBrowserBroker::class.java.getDeclaredField("uploads").apply {isAccessible=true}.get(broker) as Map<String,String>
                var granted:String?=null
                waitFor("Separate owner confirmation mints the actual selected upload grant") {granted=grants.entries.singleOrNull {it.value==source.id}?.key;granted!=null}
                val grant=checkNotNull(granted)
                val uri=android.net.Uri.parse("content://${context.packageName}.hiddenbrowser/${broker!!.token}/$grant")
                retainedUpload=checkNotNull(context.contentResolver.openFileDescriptor(uri,"r")).use {ParcelFileDescriptor.dup(it.fileDescriptor)}
                val descriptor=checkNotNull(retainedUpload).fileDescriptor
                assertEquals("Actual provider descriptor has original length",source.plaintextSize,retainedUpload!!.statSize)
                val descriptorFlags=android.system.Os.fcntlInt(descriptor,android.system.OsConstants.F_GETFL,0)
                assertTrue("Actual duplicated provider descriptor bypasses the kernel page cache",descriptorFlags and HiddenVpnBrowserBroker.uploadDirectIoFlag() != 0)
                val original=repo.read(source.id)
                val readBack=ByteArray(original.size)
                try {
                    val middle=original.size/2
                    assertEquals(middle.toLong(),android.system.Os.lseek(descriptor,middle.toLong(),android.system.OsConstants.SEEK_SET))
                    var read=0
                    while(read<original.size-middle) {val count=android.system.Os.read(descriptor,readBack,read,original.size-middle-read);assertTrue(count>0);read+=count}
                    assertArrayEquals("Provider seeks forward over authenticated encrypted original",original.copyOfRange(middle,original.size),readBack.copyOfRange(0,read))
                    assertEquals(0L,android.system.Os.lseek(descriptor,0,android.system.OsConstants.SEEK_SET))
                    read=0
                    while(read<original.size) {val count=android.system.Os.read(descriptor,readBack,read,original.size-read);assertTrue(count>0);read+=count}
                    val hash=java.security.MessageDigest.getInstance("SHA-256").digest(readBack).joinToString(""){"%02x".format(it)}
                    assertEquals("Provider seeks backward and preserves original bytes",sourceHash,hash)
                } finally {original.fill(0);readBack.fill(0)}
                clickWeb("Upload Tor image")
                waitFor("Native multipart original image bytes",60){observed(path,host){it.optString("path")=="/uploaded" && it.optBoolean("multipart_png") && it.optString("upload_sha256")==sourceHash}}
                navigate("$base/cookie");waitFor("Tor cookie response rendered",60){nodes().any {it.text?.toString()=="Tor cookie set"}};navigate("$base/cookie-echo")
                waitFor("Tor profile cookie returns only to its own origin",60){observed(path,host){it.optString("path")=="/cookie-echo" && it.optBoolean("cookie_present")}}
                navigate("$base/media.png");click("Save GET file")
                waitFor("Tor GET media is verified before encrypted vault publication",60){repo.items().any {it.id!=source.id}}
                val saved=repo.items().first {it.id!=source.id};repo.verify(saved.id)
                val bytes=repo.read(saved.id)
                try {val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)};assertEquals(evidence(path,host).getString("png_sha256"),hash)}finally{bytes.fill(0)}
                assertFalse("No inline script request was executed",observed(path,host){it.optString("path")=="/script-must-not-run"})
                val before=isolation!!.dropCounters()
                // These explicit bound attempts must actually enter the sink, not merely fail upstream.
                udp(network,InetAddress.getByAddress(byteArrayOf(203.toByte(),0,113,10)),3478,"private-gallery-tor-sink-only".toByteArray())
                udp(network,InetAddress.getByName("2001:db8::10"),3478,"private-gallery-tor-sink-only-v6".toByteArray())
                val query=byteArrayOf(0x17,0x62,1,0,0,1,0,0,0,0,0,0,3,116,111,114,7,105,110,118,97,108,105,100,0,0,1,0,1)
                udp(network,InetAddress.getByAddress(byteArrayOf(10,252.toByte(),0,2)),53,query)
                waitFor("Direct v4/v6/UDP/DNS packets were discarded by owned TUN") {val after=isolation!!.dropCounters();after.first>before.first && after.second>before.second && after.third>=before.third+3}
                navigate("$base/slow");waitFor("Active Tor request before revoke",60){observed(path,host){it.optString("kind")=="slow-start"}}
                val native=context.getSystemService(android.app.ActivityManager::class.java).runningAppProcesses.single {it.uid==Process.myUid() && it.processName==context.packageName+":hidden_tor"}
                Process.killProcess(native.pid)
                waitFor("Abrupt Tor process death is observed",10){tor?.alive()!=true}
                assertEquals("Sink remains owned until explicit lifetime retirement",network,isolation!!.network())
                isolation!!.close()
                waitFor("No Tor/native browser remains before sink retirement",15){ended.get() && HiddenTorIsolationService.released() && !HiddenTorIsolationService.hasTorProcess(context) && !HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
                assertFalse(broker!!.valid());session.lock();allowed.set(false);broker!!.close();tor!!.close()
                val probe=ByteArray(1)
                val readWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
                try {
                    val revokedFlags=android.system.Os.fcntlInt(checkNotNull(retainedUpload).fileDescriptor,android.system.OsConstants.F_GETFL,0)
                    assertTrue("Held upload duplicate retains direct I/O after revocation",revokedFlags and HiddenVpnBrowserBroker.uploadDirectIoFlag() != 0)
                    assertEquals("The held duplicate remains independently seekable",0L,android.system.Os.lseek(checkNotNull(retainedUpload).fileDescriptor,0,android.system.OsConstants.SEEK_SET))
                    val denied=readWorker.submit<Boolean> {
                        val result=runCatching {android.system.Os.read(checkNotNull(retainedUpload).fileDescriptor,probe,0,1)}
                        android.util.Log.i("NativeTor1762","revoked upload read count=${result.getOrNull()} errno=${(result.exceptionOrNull() as? android.system.ErrnoException)?.errno}")
                        result.isFailure
                    }
                    assertTrue("An independently duplicated open upload FD rejects actual rereads after revocation",denied.get(3,java.util.concurrent.TimeUnit.SECONDS))
                } finally {probe.fill(0);retainedUpload?.close();retainedUpload=null;readWorker.shutdownNow()}
                File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"native-tor1762-result.json").writeText(JSONObject().put("mode","TOR_ONLY").put("pid",Process.myPid()).put("javascript",false).put("form",true).put("multipart",true).put("download_verified",true).put("discard_v4",true).put("discard_v6",true).put("discard_dns_udp",true).put("session_revoked",true).put("tor_process_death",true).toString())
            }
        } finally {allowed.set(false);broker?.close();retainedUpload?.close();tor?.close();isolation?.close();session.lock();waitFor("Tor sink release",15){HiddenTorIsolationService.released()};storage.deleteRecursively()}
    }
}
