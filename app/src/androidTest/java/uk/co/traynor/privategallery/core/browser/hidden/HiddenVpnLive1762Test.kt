package uk.co.traynor.privategallery.core.browser.hidden

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vpn.*

/** Mandatory opt-in CI fixture: real official WireGuard backend + unchanged production child.
 * No assumptions/skips once hiddenVpnConfig is supplied. Host packet/DNS evidence is separate. */
class HiddenVpnLive1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private fun shell(command:String):String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,predicate:()->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L
        while(android.os.SystemClock.elapsedRealtime()<deadline) {if(runCatching(predicate).getOrDefault(false))return;Thread.sleep(100)}
        val satisfied=runCatching(predicate).getOrDefault(false)
        if(!satisfied)captureUi("timeout-"+message)
        assertTrue(message,satisfied)
    }
    /** Disposable fixture evidence only. Never changes secure-window policy or session state. */
    private fun captureUi(label:String) {
        runCatching {
            val output=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: instrumentation.targetContext.getExternalFilesDir(null)!!.absolutePath,"native-live1762").apply {mkdirs()}
            val name=label.replace(Regex("[^A-Za-z0-9_-]"),"-").take(90)
            fun redact(value:CharSequence?):String = value?.toString().orEmpty().take(512)
                .replace(Regex("""(?i)(?:[a-z][a-z0-9+.-]*://|www\.)[^\s]*"""),"[address redacted]")
            File(output,"$name.xml").outputStream().use {stream ->
                val xml=android.util.Xml.newSerializer().apply {setOutput(stream,"UTF-8");startDocument("UTF-8",true)}
                xml.startTag(null,"snapshot");xml.attribute(null,"label",name)
                val display=instrumentation.targetContext.resources.displayMetrics
                xml.attribute(null,"display","${display.widthPixels}x${display.heightPixels}@${display.densityDpi}")
                var remaining=512
                fun visit(node:AccessibilityNodeInfo?,depth:Int) {
                    if(node==null || depth>40 || remaining--<=0)return
                    val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                    xml.startTag(null,"node")
                    for((key,value) in mapOf("class" to node.className,"text" to redact(node.text),"description" to redact(node.contentDescription),
                        "window" to node.windowId,"bounds" to bounds.flattenToString(),"enabled" to node.isEnabled,"visible" to node.isVisibleToUser,
                        "clickable" to node.isClickable,"selected" to node.isSelected,"focused" to node.isFocused,"stateDescription" to redact(node.stateDescription),
                        "actions" to node.actionList.joinToString {it.id.toString()}))xml.attribute(null,key,value.toString())
                    repeat(node.childCount){visit(node.getChild(it),depth+1)}
                    xml.endTag(null,"node")
                }
                visit(automation.rootInActiveWindow,0)
                xml.endTag(null,"snapshot");xml.endDocument();xml.flush()
            }
            // FLAG_SECURE is retained: a platform-redacted/black screenshot is useful window evidence.
            automation.takeScreenshot()?.let {bitmap ->try {File(output,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}}
            android.util.Log.i("HiddenVpnLive1762","UI evidence $name captured; addresses redacted; secure policy retained")
        }.onFailure {android.util.Log.e("HiddenVpnLive1762","UI evidence unavailable: ${it.javaClass.simpleName}")}
    }
    private fun nodes():List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?) {if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return result
    }
    /** WebView virtual ACTION_CLICK can accept a container action without activating HTML.
     * Tap the exact visible virtual node, as a user does; server/body assertions prove effect. */
    private fun selectedTabAncestor(node:AccessibilityNodeInfo):Boolean {
        var ancestor:AccessibilityNodeInfo?=node
        repeat(12) {
            val card=ancestor ?: return false
            // Compose exports the production Current label but not selected=true on this provider.
            if(card.isClickable && (0 until card.childCount).any {card.getChild(it)?.text?.toString()=="Current"})return true
            ancestor=card.parent
        }
        return false
    }
    private fun click(text:String,index:Int=0,selectedTab:Boolean=false) {
        var target:AccessibilityNodeInfo?=null
        waitFor("Visible web control $text") {
            target=nodes().filter {it.isEnabled && (!selectedTab || selectedTabAncestor(it)) && (it.text?.toString()==text || it.contentDescription?.toString()==text)}.getOrNull(index)
            target?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            target!=null
        }
        val window=checkNotNull(target).windowId
        val bounds=android.graphics.Rect()
        var previousBounds:android.graphics.Rect?=null
        waitFor("Fresh visible web touch target $text") {
            val root=automation.rootInActiveWindow ?: return@waitFor false
            if(root.windowId!=window)return@waitFor false
            val fresh=nodes().filter {it.windowId==window && it.isVisibleToUser && it.isEnabled && (!selectedTab || selectedTabAncestor(it)) && (it.text?.toString()==text || it.contentDescription?.toString()==text)}.getOrNull(index) ?: return@waitFor false
            fresh.getBoundsInScreen(bounds)
            val visible=android.graphics.Rect();root.getBoundsInScreen(visible)
            val inWindow=!bounds.isEmpty && bounds.intersect(visible)
            val stable=inWindow && previousBounds==bounds
            previousBounds=if(inWindow)android.graphics.Rect(bounds) else null
            stable
        }
        val at=android.os.SystemClock.uptimeMillis()
        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(at,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue("Web touch delivered",automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    /** Compose controls export a real click action; HTML controls continue to use exact touch. */
    private fun clickNative(text:String,selectedTab:Boolean=false) {
        waitFor("Native production action $text") {
            var node=nodes().firstOrNull {(!selectedTab || selectedTabAncestor(it)) && (it.text?.toString()==text || it.contentDescription?.toString()==text)} ?: return@waitFor false
            while(!node.isClickable)node=node.parent ?: return@waitFor false
            if(!node.isEnabled || !node.isVisibleToUser)return@waitFor false
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    private fun closeSelectedTabAndReturn() {
        waitFor("Selected tab close control belongs to selected production card") {
            val visible=nodes()
            visible.count {it.text?.toString()=="Current"}==1 &&
                visible.count {it.contentDescription?.toString()=="Close tab" && selectedTabAncestor(it)}==1
        }
        clickNative("Close tab",selectedTab=true)
        waitFor("Actual tab removal completes before dismissing panel") {nodes().any {it.text?.toString()=="1 open · up to 8 tabs"}}
        click("Back to Browser")
        waitFor("Tabs panel actually dismissed") {nodes().none {it.contentDescription?.toString()=="Back to Browser"}}
    }
    private fun navigate(url:String) {
        fun addressField():AccessibilityNodeInfo? {
            var node=nodes().firstOrNull {it.contentDescription?.toString()=="Go"}
            while(node!=null && !node.isEditable)node=node.parent
            return node
        }
        var previousState:String?=null
        var loggedStates=0
        waitFor("Dedicated browser address input") {
            var field=addressField()
            var focusAttempted=false;var focusAccepted=false
            var textAttempted=false;var textAccepted=false
            if(field!=null && field.isVisibleToUser && field.isEnabled) {
                if(!field.isFocused) {
                    focusAttempted=true;focusAccepted=field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    field=addressField()
                }
                if(field!=null && field.isFocused && field.text?.toString()!=url) {
                    textAttempted=true
                    textAccepted=field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,url)})
                }
            }
            val fresh=addressField()
            val focused=fresh?.isFocused==true
            val textMatches=fresh?.text?.toString()==url
            val state="fieldPresent=${fresh!=null} focused=$focused textMatches=$textMatches focusAttempted=$focusAttempted focusAccepted=$focusAccepted textAttempted=$textAttempted textAccepted=$textAccepted"
            if(state!=previousState && loggedStates<12) {
                android.util.Log.i("HiddenVpnLive1762","Address input $state")
                previousState=state;loggedStates++
            }
            focused && textMatches
        }
        clickNative("Go")
    }
    private fun evidence(network:Network,origin:String):JSONObject = (network.openConnection(URL("$origin/evidence")) as java.net.HttpURLConnection).let {connection ->
        connection.connectTimeout=3000;connection.readTimeout=3000
        try {connection.inputStream.use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}} finally {connection.disconnect()}
    }
    private fun observed(network:Network,origin:String,predicate:(JSONObject)->Boolean):Boolean {
        val events=evidence(network,origin).getJSONArray("events")
        return (0 until events.length()).any {predicate(events.getJSONObject(it))}
    }
    @Test fun nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn() {
        val arguments=InstrumentationRegistry.getArguments()
        val configPath=arguments.getString("hiddenVpnConfig")
        assumeTrue("Dedicated real VPN fixture is opt-in",configPath!=null)
        check(BuildConfig.DEBUG) {"Disposable debug fixture only"}
        require(configPath=="/data/local/tmp/hidden-vpn1762.conf")
        val origin=arguments.getString("hiddenVpnOrigin") ?: error("Fixture origin required")
        require(origin=="http://fixture.invalid")
        val displayOverride=shell("wm size").lineSequence().firstOrNull {it.startsWith("Override size:")}?.substringAfter(":")?.trim()
        val context=instrumentation.targetContext
        // The later Primary-cookie isolation read initializes its WebView provider. Do that
        // before opening the child, outside the live child accessibility traversal.
        instrumentation.runOnMainSync {android.webkit.CookieManager.getInstance()}
        val info=automation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.serviceInfo=info
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull("CI must grant Android's VPN consent before backend startup",VpnService.prepare(context))
        val configuration=shell("cat $configPath")
        val imported=VpnProfileParser.import("Disposable CI VPN",configuration) as VpnProfileImportResult.Accepted
        val engine=WireGuardVpnEngine(OfficialWireGuardBackend(context))
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){23}))
        val allowed=AtomicBoolean(true)
        val ended=AtomicBoolean()
        val root=File(context.noBackupFilesDir,"hidden-vpn1762-${UUID.randomUUID()}")
        var broker:HiddenVpnBrowserBroker?=null
        lateinit var downloadRepo:HiddenRepository
        try {
            // Match production Runtime readiness: process absence alone precedes lease release.
            // Observe the owner; never clear/reset the previous browser's retirement lease.
            waitFor("Previous native admission lease and processes retire") {
                HiddenVpnBrowserBroker.admissionRetired() && !HiddenVpnBrowserBroker.hasDedicatedProcess(context) &&
                    !HiddenTorIsolationService.hasTorProcess(context) && HiddenTorIsolationService.released()
            }
            engine.connect(imported.profile)
            waitFor("Authenticated WireGuard peer handshake",90){engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}
            val manager=context.getSystemService(ConnectivityManager::class.java)
            var pinned:Network?=null
            waitFor("Exactly one app-owned VPN Network") {
                pinned=manager.allNetworks.singleOrNull {candidate ->manager.getNetworkCapabilities(candidate)?.let {it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.ownerUid==Process.myUid()}==true};pinned!=null
            }
            val network=checkNotNull(pinned)
            assertEquals("203.0.113.10",network.getAllByName("fixture.invalid").single().hostAddress)
            ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
                var parentTask=-1
                scenario.onActivity {parentTask=it.taskId}
                scenario.onActivity {activity ->
                    val isolated=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=root}
                    val repo=HiddenRepository(isolated,session)
                    downloadRepo=repo
                    val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
                    val png=try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {bitmap.recycle()}
                    try {
                        repo.importMedia("CI eligible upload.png","image/png",png.inputStream(),createDistinctCopy=true)
                        repo.importMedia("CI restricted upload.png","image/png",png.inputStream(),createDistinctCopy=true,vaultOnly=true)
                    } finally {png.fill(0)}
                    broker=HiddenVpnBrowserBroker(isolated,session,network,
                        {allowed.get() && engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED && manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true},
                        {ended.set(true)})
                    broker!!.launch()
                }
                val secureOrigin = "https://native-phase.fixture.invalid"
                navigate("$secureOrigin/parity-one")
                waitFor("First actual page navigation") {observed(network,origin){it.optString("path")=="/parity-one"}}
                waitFor("Fixture page is rendered before rejected address entry") {nodes().any {it.text?.toString()=="Run JavaScript POST" && it.isVisibleToUser}}
                fun searchDnsCount():Int {
                    val events=evidence(network,origin).getJSONArray("events")
                    return (0 until events.length()).count {index ->
                        val event=events.getJSONObject(index)
                        event.optString("kind")=="dns" && event.optString("name") in setOf("duckduckgo.com","www.duckduckgo.com","www.google.com")
                    }
                }
                val searchDnsBefore=searchDnsCount()
                for(rejected in listOf("file:///private-test.jpg","content://private-test/item","https://")) {
                    navigate(rejected)
                    var stableSince=0L
                    waitFor("Rejected explicit address keeps the actual fixture page and owned session") {
                        val visible=nodes()
                        val unchanged=visible.any {it.text?.toString()=="Only public HTTP(S) addresses are supported. Onion sites require Tor Only." && it.isVisibleToUser} &&
                            visible.any {it.text?.toString()=="Run JavaScript POST" && it.isVisibleToUser} && broker!!.valid() && !ended.get()
                        val now=android.os.SystemClock.elapsedRealtime()
                        if(!unchanged)stableSince=0 else if(stableSince==0L)stableSince=now
                        unchanged && now-stableSince>=500
                    }
                    assertTrue("Rejected input leaves broker capability valid",broker!!.valid())
                    assertFalse("Rejected input does not retire session",ended.get())
                }
                assertEquals("Rejected explicit addresses never initiate search-provider DNS",searchDnsBefore,searchDnsCount())
                navigate("$secureOrigin/parity-two")
                waitFor("Second actual page navigation") {observed(network,origin){it.optString("path")=="/parity-two"}}
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                waitFor("System Back traverses WebView instead of retiring child") {nodes().any {it.isEditable && it.text?.toString()=="$secureOrigin/parity-one"}}
                assertFalse("Web back preserves owned session",ended.get())
                navigate("$secureOrigin/parity-error")
                waitFor("Main-frame HTTP error exposes shared recovery") {nodes().any {it.text?.toString()=="Page unavailable"}}
                captureUi("error-before-retry")
                clickNative("Retry page")
                waitFor("Retry uses actual WebView through VPN",30) {
                    val events=evidence(network,origin).getJSONArray("events")
                    (0 until events.length()).count {events.getJSONObject(it).optString("path")=="/parity-error"}>=2
                }
                navigate(secureOrigin)
                waitFor("Fixture document is rendered before Find") {nodes().any {it.text?.toString()=="Run JavaScript POST" && it.isVisibleToUser} && nodes().any {it.contentDescription?.toString()=="Reload"}}
                click("Browser settings");click("Find in page")
                var findField:AccessibilityNodeInfo?=null
                waitFor("Production find label resolves to its own editable field") {
                    var node=nodes().firstOrNull {it.text?.toString()=="Find in page"} ?: return@waitFor false
                    while(!node.isEditable)node=node.parent ?: return@waitFor false
                    findField=node
                    node.isVisibleToUser && node.isEnabled
                }
                assertTrue(checkNotNull(findField).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"POST")}))
                waitFor("Actual Find field contains the requested query") {
                    var node=nodes().firstOrNull {it.text?.toString()=="Find in page"} ?: return@waitFor false
                    while(!node.isEditable)node=node.parent ?: return@waitFor false
                    node.text?.toString()=="POST"
                }
                waitFor("Real WebView find reports matches") {nodes().any {it.text?.toString()?.matches(Regex("[1-9][0-9]* of [1-9][0-9]* matches"))==true}}
                click("Close find")
                click("Browser settings");click("Desktop layout off");click("Back to Browser")
                navigate("$secureOrigin/desktop-parity")
                waitFor("Selected tab uses real desktop user agent") {observed(network,origin){it.optString("path")=="/desktop-parity" && it.optString("user_agent").contains("X11; Linux x86_64")}}
                click("Tabs");click("New tab")
                navigate("$secureOrigin/mobile-parity")
                waitFor("New tab independently retains mobile user agent") {observed(network,origin){it.optString("path")=="/mobile-parity" && it.optString("user_agent").contains("Android")}}
                click("Tabs");closeSelectedTabAndReturn()
                click("Browser settings")
                waitFor("Restored desktop tab reports its own setting") {nodes().any {it.text?.toString()=="Desktop layout on"}}
                click("Back to Browser")
                navigate(secureOrigin)
                click("Run JavaScript POST")
                waitFor("Dedicated child task preserves its parent task") {
                    val tasks=context.getSystemService(android.app.ActivityManager::class.java).appTasks.mapNotNull {it.taskInfo}
                    val child=android.content.ComponentName(context,HiddenVpnBrowserActivity::class.java)
                    tasks.any {it.taskId!=parentTask && it.baseActivity==child && it.topActivity==child} && tasks.any {it.taskId==parentTask}
                }
                waitFor("HTTPS CONNECT carries JavaScript POST and exact body",30) {
                    observed(network,origin){it.optString("path")=="/post" && it.optBoolean("javascript_body") && it.optBoolean("tls")}
                }
                click("Run worker POST")
                waitFor("Service worker sends POST over the same authenticated VPN proxy",30) {
                    observed(network,origin){it.optString("path")=="/worker-post" && it.optBoolean("worker_body") && it.optBoolean("tls")}
                }
                click("Frame alert")
                waitFor("Website modal names the actual iframe origin") {
                    nodes().any {it.text?.toString()=="https://dialog-frame.fixture.invalid"}
                }
                click("OK")
                click("Confirm")
                waitFor("Native website confirmation names the request") {nodes().any {it.text?.toString()=="Fixture confirm"}}
                click("Cancel")
                waitFor("Cancellation returns to the native website") {nodes().any {it.text?.toString()=="cancelled"}}
                click("Prompt")
                waitFor("Native prompt response field") {nodes().any {it.text?.toString()=="Fixture prompt"}}
                waitFor("Prompt editable receives explicit response") {
                    nodes().firstOrNull {it.isEditable}?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"native-profile-prompt")})==true
                }
                click("OK")
                waitFor("Only confirmed prompt result reaches JavaScript") {nodes().any {it.text?.toString()=="native-profile-prompt"}}
                // Real native tabs retain the selected page and expose distinct close controls.
                click("Tabs");click("New tab");click("Tabs")
                waitFor("Two native tabs are listed") {nodes().any {it.text?.toString()=="2 open · up to 8 tabs"}}
                closeSelectedTabAndReturn()
                waitFor("Closing selected tab restores actual page address") {nodes().any {it.isEditable && it.text?.toString()?.startsWith(secureOrigin)==true}}
                click("Run WebSocket")
                waitFor("Native secure WebSocket frame crosses the authenticated VPN proxy",30) {
                    observed(network,origin){it.optString("kind")=="websocket" && it.optBoolean("tls") && it.optBoolean("exact_body")}
                }
                click("Run RTC route probe")
                waitFor("WebRTC route probe completes without a failed API probe",30) {
                    observed(network,origin){it.optBoolean("probe_complete")}
                }
                navigate("$secureOrigin/basic")
                waitFor("Website authentication uses actual visible HTTPS origin") {nodes().any {it.text?.toString()=="Sign in to native-phase.fixture.invalid"}}
                waitFor("Native website credentials can be entered") {
                    val fields=nodes().filter {it.isEditable}
                    if(fields.size!=2)return@waitFor false
                    fields[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"fixture-user")}) &&
                        fields[1].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"fixture-pass")})
                }
                click("Sign in")
                waitFor("Only confirmed dummy credentials reach fixture over TLS") {observed(network,origin){it.optString("path")=="/basic" && it.optBoolean("basic_present") && it.optBoolean("tls")}}
                navigate("$secureOrigin/cookie")
                waitFor("Cookie-setting response is rendered") {nodes().any {it.text?.toString()=="cookie set"}}
                navigate("$secureOrigin/cookie-echo")
                waitFor("Cookie remains usable inside Hidden VPN profile",30) {
                    observed(network,origin){it.optString("path")=="/cookie-echo" && it.optBoolean("cookie_present")}
                }
                instrumentation.runOnMainSync {
                    assertNull("Hidden child cookies must not enter Primary default profile",android.webkit.CookieManager.getInstance().getCookie(secureOrigin))
                }
                navigate(origin);click("Run JavaScript POST")
                waitFor("Native JavaScript fetch transmits the exact POST body over VPN",30) {observed(network,origin){it.optString("path")=="/post" && it.optBoolean("javascript_body")}}
                navigate("$origin/form");click("Submit form")
                waitFor("Native HTML form reaches fixture",30){observed(network,origin){it.optString("path")=="/form-result" && it.optBoolean("form_body")}}
                val formEventsBefore=evidence(network,origin).getJSONArray("events").let {events ->(0 until events.length()).count {events.getJSONObject(it).optString("path")=="/form-result"}}
                click("Reload")
                waitFor("Native form reload asks before repeating POST") {nodes().any {it.text?.toString()=="Resend form data?"}}
                click("Cancel")
                assertEquals("Cancel must not repeat form submission",formEventsBefore,evidence(network,origin).getJSONArray("events").let {events ->(0 until events.length()).count {events.getJSONObject(it).optString("path")=="/form-result"}})
                click("Reload")
                waitFor("Second reload still requires confirmation") {nodes().any {it.text?.toString()=="Resend form data?"}}
                click("Resend")
                waitFor("Explicit resend performs exactly another form request") {evidence(network,origin).getJSONArray("events").let {events ->(0 until events.length()).count {events.getJSONObject(it).optString("path")=="/form-result"}}>formEventsBefore}
                navigate("$origin/upload");click("Choose Hidden upload")
                waitFor("Hidden vault chooser") {nodes().any {it.text?.toString()=="CI eligible upload.png" || it.contentDescription?.toString()=="CI eligible upload.png"}}
                waitFor("Eligible encrypted Hidden thumbnail is available in the shared picker") {
                    var tile=nodes().firstOrNull {it.contentDescription?.toString()=="CI eligible upload.png" && it.isVisibleToUser && it.isEnabled}
                    while(tile!=null && !tile.isClickable)tile=tile.parent
                    tile?.let {it.isVisibleToUser && it.isEnabled && it.stateDescription?.toString()=="Preview loaded"}==true
                }
                assertFalse("Restricted item never appears in egress chooser",nodes().any {it.text?.toString()=="CI restricted upload.png" || it.contentDescription?.toString()=="CI restricted upload.png"})
                click("CI eligible upload.png");click("Allow upload");click("Upload media")
                waitFor("Multipart body reaches VPN fixture",30) {observed(network,origin){it.optString("path")=="/uploaded" && it.optBoolean("multipart_png")}}
                navigate("$origin/media.png")
                waitFor("A browser download requires explicit confirmation") {nodes().any {it.text?.toString()=="Save this GET media URL?"}}
                assertFalse("No media is imported before explicit confirmation",downloadRepo.items().any {it.displayName=="Hidden download"})
                click("Save GET media")
                waitFor("Supported GET image imported into encrypted Hidden Vault",30) {downloadRepo.items().any {it.displayName=="Hidden download"}}
                val downloaded=downloadRepo.items().single {it.displayName=="Hidden download"}
                downloadRepo.verify(downloaded.id)
                downloadRepo.withTransferSource(downloaded.id,{false}) {_,input ->
                    val plaintext=input.readBytes()
                    try {
                        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(plaintext).joinToString(""){"%02x".format(it)}
                        assertEquals("5e3d382db4dd83d59aa5742793ad6b7903409e865c83bcbc54835049f043bc15",digest)
                    } finally {plaintext.fill(0)}
                }
                navigate(secureOrigin)
                waitFor("Native page before Fold viewport changes") {nodes().any {it.text?.toString()=="Run JavaScript POST"}}
                val processes=context.getSystemService(android.app.ActivityManager::class.java)
                fun childPid()=processes.runningAppProcesses.orEmpty().singleOrNull {it.processName==context.packageName+":hidden_vpn_browser"}?.pid
                val admittedPid=childPid();assertNotNull(admittedPid)
                for(viewport in listOf("392x840","840x900","840x392")) {
                    shell("wm size $viewport")
                    waitFor("Native Fold chrome remains usable at $viewport") {nodes().any {it.contentDescription?.toString()=="Go" && it.isVisibleToUser} && nodes().any {it.contentDescription?.toString()=="Tabs" && it.isVisibleToUser}}
                    assertEquals("Fold transition must retain the admitted immutable-network process",admittedPid,childPid())
                    assertEquals(true to true,broker!!.childSecuritySnapshot())
                    val before=evidence(network,origin).getJSONArray("events").length()
                    click("Run JavaScript POST")
                    waitFor("Native traffic still uses VPN after Fold viewport change") {
                        val events=evidence(network,origin).getJSONArray("events")
                        (before until events.length()).any {events.getJSONObject(it).let {event ->event.optString("path")=="/post" && event.optBoolean("javascript_body") && event.optBoolean("tls")}}
                    }
                }
                shell(if(displayOverride==null)"wm size reset" else "wm size $displayOverride")
                navigate(secureOrigin)
                click("Start continuous RTC")
                waitFor("Continuous native UDP probe started") {observed(network,origin){it.optBoolean("continuous_started")}}
                waitFor("At least three fresh STUN datagrams before Hidden revocation") {
                    val events=evidence(network,origin).getJSONArray("events")
                    val list=(0 until events.length()).map {events.getJSONObject(it)}
                    val start=list.lastOrNull {it.optBoolean("continuous_started")}?.optDouble("time") ?: return@waitFor false
                    list.count {it.optString("kind")=="stun" && it.optDouble("time")>=start}>=3
                }
                click("Start slow fetch")
                waitFor("Slow HTTPS request remains active alongside RTC") {
                    val events=evidence(network,origin).getJSONArray("events")
                    val list=(0 until events.length()).map {events.getJSONObject(it)}
                    val start=list.lastOrNull {it.optBoolean("continuous_started")}?.optDouble("time") ?: return@waitFor false
                    list.any {it.optString("kind")=="slow-start" && it.optDouble("time")>=start}
                }
                val lockEnter=android.os.SystemClock.elapsedRealtime()
                // Signal delivery itself must terminate the native process. Do not help it by
                // closing the broker, changing allowed, or disconnecting WireGuard first.
                session.lock()
                val lockReturn=android.os.SystemClock.elapsedRealtime()
                waitFor("Hidden-only lock promptly terminates the claimed native process",2){!broker!!.childIsAlive() && childPid()==null}
                val deathObserved=android.os.SystemClock.elapsedRealtime()
                assertTrue("Revocation is not the retired3/5second graceful timeout",deathObserved-lockReturn<1000)
                assertEquals("Underlying WireGuard remains connected",VpnConnectionState.CONNECTED,engine.state)
                assertEquals(network,pinned)
                val barrier=(network.openConnection(URL("$origin/probe-revocation")) as java.net.HttpURLConnection)
                try {
                    barrier.connectTimeout=3000;barrier.readTimeout=3000;barrier.requestMethod="POST";barrier.doOutput=true
                    barrier.outputStream.use {it.write("child-revoked-udp-barrier".toByteArray())}
                    assertEquals("Parent can still use the exact VPN after child revocation",200,barrier.responseCode)
                    barrier.inputStream.close()
                }finally{barrier.disconnect()}
                Thread.sleep(1000)
                val after=evidence(network,origin).getJSONArray("events")
                val events=(0 until after.length()).map {after.getJSONObject(it)}
                val barrierTime=events.last {it.optBoolean("revocation_barrier")}.getDouble("time")
                assertFalse("No ongoing STUN after confirmed death barrier",events.any {it.optString("kind")=="stun" && it.optDouble("time")>barrierTime+0.150})
                assertFalse("No child continuous-phase DNS after confirmed death barrier",events.any {it.optString("kind")=="dns" && it.optString("name")=="continuous-phase.fixture.invalid" && it.optDouble("time")>barrierTime+0.150})
                waitFor("Parent revoked proxy HTTP streams and completed cleanup",10){ended.get()}
                assertFalse(broker!!.active);assertFalse(broker!!.valid())
                // Revocation intentionally supersedes graceful clear acknowledgement. Whole
                // owned roots must disappear after kernel-confirmed child death instead.
                waitFor("Owned browser data and cache roots removed after child death",15) {
                    !File(context.noBackupFilesDir,"hidden-vpn-webview-v1/${broker!!.token}").exists() &&
                        !File(context.cacheDir,"hidden-vpn-webview-v1/${broker!!.token}").exists()
                }
                File(checkNotNull(arguments.getString("additionalTestOutputDir")),"vpn-native-revocation1762-result.json").writeText(JSONObject()
                    .put("lock_enter_ms",lockEnter).put("lock_return_ms",lockReturn).put("death_observed_ms",deathObserved)
                    .put("child_pid",admittedPid).put("wireguard_retained",engine.state==VpnConnectionState.CONNECTED)
                    .put("revocation_barrier_seconds",barrierTime).put("post_barrier_stun",0).put("owned_roots_removed",true).toString())
                assertTrue("Main process remains independent from child binding",manager.boundNetworkForProcess==null)
                // Host fixture/captures assert the stalled request closes and no direct-origin/DNS packets occur.
            }
        } finally {
            shell(if(displayOverride==null)"wm size reset" else "wm size $displayOverride")
            allowed.set(false);broker?.close();session.lock();engine.disconnect()
            waitFor("CI VPN teardown",15){!engine.ownsTunnel}
            root.deleteRecursively()
        }
    }
}
