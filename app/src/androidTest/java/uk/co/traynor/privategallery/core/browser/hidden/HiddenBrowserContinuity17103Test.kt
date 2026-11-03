package uk.co.traynor.privategallery.core.browser.hidden

import android.app.ActivityManager
import android.content.ContextWrapper
import android.net.*
import android.os.*
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.privateapps.HiddenPrivateAppStore
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.vpn.*
import java.io.File
import java.net.URL
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.*

/** Actual production child/Binder/proxy fixture; no in-process WebView substitutes or real accounts. */
class HiddenBrowserContinuity17103Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private var exploring=false
    private val automation get()=if(exploring)FixtureAccessibility178.automation(instrumentation) else instrumentation.uiAutomation
    private fun shell(cmd:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(cmd)).use {it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,check:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+seconds*1000L
        while(SystemClock.elapsedRealtime()<until){if(runCatching(check).getOrDefault(false))return;Thread.sleep(100)}
        assertTrue(message,runCatching(check).getOrDefault(false))
    }
    private fun nodes():List<android.view.accessibility.AccessibilityNodeInfo> {
        val result=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun visit(node:android.view.accessibility.AccessibilityNodeInfo?){if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return result
    }
    private fun visible(text:String)=nodes().any {it.isVisibleToUser && (it.text?.toString()==text || it.contentDescription?.toString()==text)}
    private fun click(text:String) {
        var last:android.graphics.Rect?=null;val bounds=android.graphics.Rect()
        waitFor("Visible stable touch target $text") {
            val node=nodes().firstOrNull {it.isVisibleToUser && it.isEnabled && (it.text?.toString()==text || it.contentDescription?.toString()==text)} ?: return@waitFor false
            node.getBoundsInScreen(bounds);val stable=!bounds.isEmpty && last==bounds;last=android.graphics.Rect(bounds);stable
        }
        val down=SystemClock.uptimeMillis()
        for(action in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{assertTrue(automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun evidence(network:Network):JSONObject {
        val connection=network.openConnection(URL("http://fixture.invalid/continuity17103/evidence")) as java.net.HttpURLConnection
        connection.connectTimeout=3000;connection.readTimeout=3000
        return try{connection.inputStream.use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}}finally {connection.disconnect()}
    }
    private var eventBaseline=0
    private fun eventCount(network:Network,kind:String,phase:String):Int {
        val events=evidence(network).getJSONArray("events")
        return (eventBaseline until events.length()).count {events.getJSONObject(it).let {e ->e.optString("kind")==kind && e.optString("phase")==phase && e.optBoolean("valid")}}
    }
    private fun network(manager:ConnectivityManager)=manager.allNetworks.singleOrNull {manager.getNetworkCapabilities(it)?.let {c ->c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && c.ownerUid==Process.myUid()}==true}

    @Test fun sameOwnedViewsResumeAcrossBackgroundAndOldEpochStaysDead()=fixture(false)
    @Test fun expiredGraceRetiresRetainedChild()=fixture(true)
    @Test fun retainedChildProcessDeathCannotResumeOldContinuation()=fixture(false,true)
    @Test fun panicDuringDeniedReconnectRevokesHiddenWithoutIoAuthority()=fixture(false,panicDenied=true)

    @Test fun touchExplorationCanLockDeniedReconnectWithoutUncoveringPage() {
        try {fixture(false,panicDenied=true,lockDenied=true)}
        finally {if(exploring){uk.co.traynor.privategallery.CiAccessibilityService178.requestTouchExploration(false);exploring=false}}
    }
    private fun fixture(expire:Boolean,processDeath:Boolean=false,panicDenied:Boolean=false,lockDenied:Boolean=false) {
        val config=InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        assertEquals("Real controlled VPN fixture is mandatory","/data/local/tmp/hidden-vpn1762.conf",config)
        shell("appops set ${context.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(context))
        val profile=(VpnProfileParser.import("Continuity fixture",shell("cat $config")) as VpnProfileImportResult.Accepted).profile
        val engine=WireGuardVpnEngine(OfficialWireGuardBackend(context));val manager=context.getSystemService(ConnectivityManager::class.java)
        val offset=AtomicLong();val session=HiddenSession {SystemClock.elapsedRealtime()+offset.get()}
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){39}))
        val root=File(context.noBackupFilesDir,"continuity17103-${UUID.randomUUID()}")
        val apps=HiddenPrivateAppStore(root,session,session.authority(),false)
        for(phase in listOf("a","b"))apps.update {it.upsert(PrivateAppCatalog.App(UUID.randomUUID().toString(),"Continuity $phase","https://site-$phase.fixture.invalid/continuity17103/site-$phase","","browser","",false,true,false))}
        val route=AtomicReference<Network>();val ended=AtomicBoolean();val executor=Executors.newSingleThreadExecutor()
        val privacy=uk.co.traynor.privategallery.ui.PrivacyShieldPreferences(context)
        val oldPanicEnabled=privacy.enabled;val oldShortcut=privacy.shortcut
        if(panicDenied){privacy.enabled=true;privacy.shortcut=false}
        val resumed=AtomicInteger();val failed=AtomicReference<Throwable?>();var broker:HiddenVpnBrowserBroker?=null;var workerClient:HiddenContinuityFixtureClient?=null
        try {
            waitFor("Previous child retirement",20){HiddenVpnBrowserBroker.admissionRetired() && !HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
            waitFor("Previous owned route retired",20){network(manager)==null}
            engine.connect(profile);waitFor("Initial authoritative WireGuard handshake",90){engine.state==VpnConnectionState.CONNECTED && network(manager)!=null}
            route.set(checkNotNull(network(manager)))
            ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
                scenario.onActivity {activity ->
                    val owned=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=root}
                    broker=HiddenVpnBrowserBroker(owned,session,route.get(),
                        {session.isUnlocked && engine.state==VpnConnectionState.CONNECTED && network(manager)==route.get()},
                        {ended.set(true)},onPanic={session.lock()},suspendTransport={pinned ->
                            engine.disconnect()
                            val deadline=SystemClock.elapsedRealtime()+2500
                            while(SystemClock.elapsedRealtime()<deadline && (engine.ownsTunnel || manager.getNetworkCapabilities(pinned)!=null))Thread.sleep(10)
                            !engine.ownsTunnel && manager.getNetworkCapabilities(pinned)==null
                        },requestReconnect={if(!panicDenied)executor.execute {
                            try {
                                engine.connect(profile)
                                waitFor("Fresh peer-verified replacement route",90){engine.state==VpnConnectionState.CONNECTED && network(manager)!=null}
                                val fresh=checkNotNull(network(manager));assertNotEquals(route.get(),fresh);route.set(fresh)
                                HiddenHttpTransport.vpn(context,fresh){session.isUnlocked && network(manager)==fresh}.use {it.verifyReady()}
                                checkNotNull(broker).prepareResume(fresh);checkNotNull(broker).publishVerifiedResume()
                            }catch(t:Throwable){failed.set(t);broker?.close()}
                        }},backgroundGrace={if(expire)30_000L else 300_000L},onResumed={resumed.incrementAndGet()})
                    checkNotNull(broker).launch()
                }
                val owner=checkNotNull(broker)
                eventBaseline=evidence(route.get()).getJSONArray("events").length()
                waitFor("Native browser ready"){owner.valid() && visible("Private Apps")}
                click("Private Apps")
                val phases=if(expire || processDeath || panicDenied)listOf("a") else listOf("a","b")
                for(phase in phases) {
                    click("Continuity $phase")
                    waitFor("Site owns initial state"){visible("SITE_READY") && eventCount(route.get(),"continuity-begin",phase)==1}
                    val opener=owner.childPresentationSnapshot();assertTrue(opener.appIdentity!=0)
                    click("Reject foreign callback");waitFor("Foreign origin/state rejected"){visible("FOREIGN_REJECTED")}
                    assertEquals(0,eventCount(route.get(),"continuity-callback",phase))
                    click("Start continuity probes")
                    waitFor("Real JS and worker requests precede suspension"){eventCount(route.get(),"continuity-probe",phase)>1 && eventCount(route.get(),"continuity-workerprobe",phase)>0}
                    waitFor("Real nonproxy UDP precedes suspension") {val events=evidence(route.get()).getJSONArray("events");(eventBaseline until events.length()).any {events.getJSONObject(it).optString("kind")=="stun"}}
                    click("Open synthetic approval");waitFor("Actual second-origin popup"){visible("Approve synthetic login")}
                    if(phase=="b") {
                        click("Cancel synthetic login");waitFor("Cancelled approval does not create website session"){visible("APPROVAL_CANCELLED")}
                        assertEquals(0,eventCount(route.get(),"continuity-session",phase))
                        click("Open synthetic approval");waitFor("Retry remains owned by same opener"){visible("Approve synthetic login")}
                    }
                    val popup=owner.childPresentationSnapshot();assertEquals(opener.viewCount+1,popup.viewCount);assertEquals(opener.appIdentity,popup.appIdentity)
                    val oldAuthority=session.authority();val oldNetwork=route.get()
                    val proxy=HiddenVpnBrowserBroker::class.java.getDeclaredField("proxy").apply {isAccessible=true}.get(owner) as HiddenVpnProxy
                    val oldPort=proxy.port;val oldPassword=proxy.password
                    val sample=evidence(oldNetwork);val timeOffset=sample.getDouble("server_time")-System.currentTimeMillis()/1000.0
                    val worker=if(!expire && !processDeath && !panicDenied && phase=="a")HiddenContinuityFixtureClient(context).also {workerClient=it;it.arm()} else null
                    val pinCounter=HiddenVpnBrowserBroker::class.java.getDeclaredField("lastPinAttempt").apply {isAccessible=true}
                    val pinBefore=pinCounter.getLong(owner)
                    shell("input keyevent KEYCODE_HOME")
                    waitFor("Confirmed denied continuation retains live child",10){owner.retainingPresentation && owner.childIsAlive() && manager.getNetworkCapabilities(oldNetwork)==null}
                    worker?.let {waitFor("Old worker captured before stop"){it.status()==1}}
                    val suspendedAt=System.currentTimeMillis()/1000.0+timeOffset
                    assertFalse(owner.valid());assertFalse(session.isAuthorityCurrent(oldAuthority))
                    val covered=owner.childPresentationSnapshot();assertTrue(covered.secure && covered.covered)
                    assertEquals(popup.viewIdentity,covered.viewIdentity);assertEquals(popup.profileIdentity,covered.profileIdentity)
                    if(panicDenied) {
                        context.getSystemService(ActivityManager::class.java).appTasks.single {it.taskInfo.topActivity?.className==HiddenVpnBrowserActivity::class.java.name}.moveToFront()
                        waitFor("Current child is reconnecting with IO still denied") {owner.continuationState()==HiddenBrowserContinuation.State.RECONNECTING && owner.retainingPresentation && !owner.valid()}
                        val denied=owner.childPresentationSnapshot();assertTrue(denied.secure && denied.covered)
                        if(lockDenied) {
                            exploring=true;FixtureAccessibility178.automation(instrumentation)
                            uk.co.traynor.privategallery.CiAccessibilityService178.requestTouchExploration(true)
                            waitFor("Real touch exploration enabled over denied foreground"){context.getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled}
                        }
                        assertFalse("Page stays absent from accessible tree",visible("Approve synthetic login"))
                        if(lockDenied) {
                            val control=nodes().single {it.isVisibleToUser && it.text?.toString()=="Lock Hidden Space"}
                            assertTrue(control.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                        }else click("Panic Exit")
                        waitFor("Panic control works without IO authority",15){ended.get() && !owner.childIsAlive() && !session.isUnlocked}
                        assertEquals(0,resumed.get());assertFalse(session.isAuthorityCurrent(oldAuthority))
                        File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),(if(lockDenied)"hidden-continuity17103-accessible-lock.json" else "hidden-continuity17103-denied-panic.json")).writeText(JSONObject().put("sensitive_descendants_concealed",true).put("shortcut_disabled",true).put("touch_exploration",lockDenied).put("io_denied",true).put("panic_revoked",!lockDenied).put("lock_revoked",lockDenied).put("child_dead",true).put("hidden_locked",true).put("no_resume_commit",true).toString())
                        return@use
                    }
                    if(processDeath) {
                        val child=context.getSystemService(ActivityManager::class.java).runningAppProcesses.single {it.uid==Process.myUid() && it.processName==context.packageName+":hidden_vpn_browser"}
                        Process.killProcess(child.pid)
                        waitFor("Death retires actual retained continuation",15){ended.get() && !owner.childIsAlive() && !owner.retainingPresentation}
                        assertFalse(session.isAuthorityCurrent(oldAuthority));assertFalse(owner.valid())
                        assertThrows(IllegalStateException::class.java){owner.prepareResume(oldNetwork)}
                        waitFor("Dead child owned roots removed",15){!File(context.noBackupFilesDir,"hidden-vpn-webview-v1/${owner.token}").exists() && !File(context.cacheDir,"hidden-vpn-webview-v1/${owner.token}").exists()}
                        File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"hidden-continuity17103-death.json").writeText(JSONObject().put("child_dead",true).put("continuation_dead",true).put("old_authority_dead",true).put("owned_roots_removed",true).toString())
                        return@use
                    }
                    if(expire) {
                        offset.set(30_001)
                        waitFor("Expiry hard-revokes retained actual child",10){!session.isUnlocked && ended.get() && !owner.childIsAlive()}
                        File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"hidden-continuity17103-expiry.json").writeText(JSONObject().put("expired",true).put("child_dead",true).put("old_authority_dead",!session.isAuthorityCurrent(oldAuthority)).toString())
                        return@use
                    }
                    Thread.sleep(2000)
                    val foregroundAt=System.currentTimeMillis()/1000.0+timeOffset
                    val task=context.getSystemService(ActivityManager::class.java).appTasks.single {it.taskInfo.topActivity?.className==HiddenVpnBrowserActivity::class.java.name}
                    task.moveToFront()
                    waitFor("Same child resumes after fresh verified route",100){owner.valid() && resumed.get()>=(if(phase=="a")1 else 2) && visible("Approve synthetic login")}
                    failed.get()?.let {throw AssertionError("Resume adapter failed",it)}
                    worker?.use {it.release();waitFor("Stale worker rejected before PIN operation"){it.status()==2};assertEquals(pinBefore,pinCounter.getLong(owner))}
                    assertFalse(session.isAuthorityCurrent(oldAuthority));assertNotEquals(oldNetwork,route.get())
                    val restored=owner.childPresentationSnapshot()
                    assertEquals(popup.viewIdentity,restored.viewIdentity);assertEquals(popup.profileIdentity,restored.profileIdentity);assertEquals(popup.viewCount,restored.viewCount);assertEquals(popup.appIdentity,restored.appIdentity)
                    val oldAccepted=runCatching {Socket("127.0.0.1",oldPort).use {socket ->socket.soTimeout=1000
                        val credentials=java.util.Base64.getEncoder().encodeToString("hidden:$oldPassword".toByteArray())
                        socket.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nProxy-Authorization: Basic $credentials\r\n\r\n".toByteArray())
                        socket.getInputStream().bufferedReader().readLine()?.contains(" 200 ")==true}}
                        .getOrDefault(false)
                    assertFalse("Revoked proxy credentials never inherit new route",oldAccepted)
                    val after=evidence(route.get()).getJSONArray("events")
                    assertFalse("No HTTP/DNS/worker/UDP fixture traffic while confirmed suspended",(0 until after.length()).any {after.getJSONObject(it).getDouble("time") in (suspendedAt+0.25)..(foregroundAt-0.25)})
                    click("Approve synthetic login");waitFor("Original opener accepts owned state"){visible("APPROVAL_RECEIVED")}
                    assertEquals(opener.viewIdentity,owner.childPresentationSnapshot().viewIdentity)
                    click("Finish website session");waitFor("Final website session, not approval UI"){visible("WEBSITE_SESSION_OK") && eventCount(route.get(),"continuity-callback",phase)==1 && eventCount(route.get(),"continuity-session",phase)==1}
                    click("Finish website session");waitFor("One-use callback replay is rejected"){visible("REJECTED")}
                    assertEquals(1,eventCount(route.get(),"continuity-begin",phase))
                    if(phase=="a") {if(visible("Expand app controls"))click("Expand app controls");click("Private Apps library")}
                }
                if(!expire && !processDeath && !panicDenied) {
                    click("Panic Exit")
                    waitFor("Real Panic UI revokes authenticated child",15){ended.get() && !owner.childIsAlive() && !session.isUnlocked}
                }
                if(!expire && !processDeath && !panicDenied)File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),"hidden-continuity17103-result.json").writeText(JSONObject()
                    .put("stale_worker_denied_before_key_io",true).put("panic_revoked",true).put("actual_child",true).put("same_view_profile_opener_app",true).put("sites",2).put("website_sessions",2)
                    .put("suspended_io_denied",true).put("old_authority_and_proxy_dead",true).put("fresh_verified_network",true)
                    .put("cancelled_approval_no_session",true).put("authenticated_session_verified",true).put("foreign_callback_rejected",true).put("one_use_post_replay_rejected",true).put("no_get_replay",true).toString())
            }
        }finally {workerClient?.close();privacy.enabled=oldPanicEnabled;privacy.shortcut=oldShortcut;broker?.close();session.lock();engine.disconnect();executor.shutdownNow();root.deleteRecursively()}
    }
}
