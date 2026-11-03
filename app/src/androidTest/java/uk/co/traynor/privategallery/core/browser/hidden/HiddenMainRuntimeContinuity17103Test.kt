package uk.co.traynor.privategallery.core.browser.hidden

import android.app.ActivityManager
import android.content.Context
import android.net.Network
import android.net.VpnService
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.*
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.core.vpn.*
import uk.co.traynor.privategallery.ui.HiddenPreferences
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real Main lifecycle, selected encrypted profile, started Runtime, monitor, child and WireGuard. */
class HiddenMainRuntimeContinuity17103Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private var diagnosticOwner:HiddenVpnBrowserBroker?=null
    private val automation get()=instrumentation.uiAutomation
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,test:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+seconds*1000L
        while(SystemClock.elapsedRealtime()<deadline){if(runCatching(test).getOrDefault(false))return;Thread.sleep(100)}
        val passed=runCatching(test).getOrDefault(false)
        if(!passed)captureFailure(message)
        assertTrue(message,passed)
    }
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun nodes():List<AccessibilityNodeInfo> {
        automation.clearCache()
        val all=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?){if(node==null)return;all+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return all
    }
    private fun visible(text:String)=nodes().any {it.isVisibleToUser && (it.text?.toString()==text || it.contentDescription?.toString()==text)}
    /** Compose labels can be non-clickable descendants of the actual native button. */
    private fun click(text:String) {
        waitFor("Native fixture action $text") {
            var node=nodes().firstOrNull {it.isVisibleToUser && (it.text?.toString()==text || it.contentDescription?.toString()==text)} ?: return@waitFor false
            while(!node.isClickable)node=node.parent ?: return@waitFor false
            node.isEnabled && node.isVisibleToUser && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }
    /** Match the working native-browser fixture: HTML uses exact visible touch, never ACTION_CLICK. */
    private fun clickWeb(text:String) {
        val bounds=android.graphics.Rect()
        var previous:android.graphics.Rect?=null
        var scrollRequested=false
        waitFor("Visible stable website action $text") {
            val fresh=nodes()
            val root=automation.rootInActiveWindow ?: return@waitFor false
            val node=fresh.firstOrNull {it.windowId==root.windowId && it.isEnabled &&
                (it.text?.toString()==text || it.contentDescription?.toString()==text)} ?: return@waitFor false
            node.getBoundsInScreen(bounds)
            val window=android.graphics.Rect();root.getBoundsInScreen(window)
            if(!node.isVisibleToUser || bounds.isEmpty || !bounds.intersect(window)) {
                previous=null
                if(!scrollRequested){scrollRequested=true;node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)}
                return@waitFor false
            }
            val stable=previous==bounds;previous=android.graphics.Rect(bounds);stable
        }
        val down=SystemClock.uptimeMillis()
        for(action in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue("Website touch delivered",automation.injectInputEvent(event,true))}finally {event.recycle()}
        }
    }
    /** Reserved synthetic fixture diagnostics: no URLs, free text, credentials or cleared secure flags. */
    private fun captureFailure(stage:String) {
        runCatching {
            val owner=diagnosticOwner ?: return
            val root=automation.rootInActiveWindow ?: return
            if(root.packageName?.toString()!=context.packageName)return
            val statuses=setOf("SITE_READY","FOREIGN_REJECTED","APPROVAL_RECEIVED","APPROVAL_CANCELLED","WEBSITE_SESSION_OK","REJECTED")
            val geometry=org.json.JSONArray()
            nodes().take(500).forEach {node ->
                val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                geometry.put(JSONObject().put("class",node.className?.toString()).put("window",node.windowId)
                    .put("bounds",bounds.toShortString()).put("visible",node.isVisibleToUser).put("enabled",node.isEnabled)
                    .put("clickable",node.isClickable).put("actions",org.json.JSONArray(node.actionList.map {it.id}))
                    .put("fixture_status",node.text?.toString()?.takeIf {it in statuses} ?: ""))
            }
            val output=File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))
            check(output.isDirectory || output.mkdirs())
            val name="main-continuity17103-failure-${SystemClock.elapsedRealtime()}"
            val secure=owner.childIsAlive() && owner.childPresentationSnapshot().secure
            File(output,"$name.json").writeText(JSONObject().put("stage",stage).put("secure",secure).put("nodes",geometry).toString())
            // A black/platform-redacted secure screenshot is expected and remains useful window evidence.
            if(secure)automation.takeScreenshot()?.let {bitmap ->
                try {File(output,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally {bitmap.recycle()}
            }
        }
    }
    private fun request(network:Network,path:String):JSONObject {
        val connection=network.openConnection(URL("http://fixture.invalid/continuity17103/$path")) as HttpURLConnection
        connection.connectTimeout=3000;connection.readTimeout=3000
        return try {connection.inputStream.use {JSONObject(it.readBytes().toString(Charsets.UTF_8))}}finally {connection.disconnect()}
    }
    private fun probe(client:HiddenContinuityFixtureClient,network:Network) {
        val baseline=request(network,"evidence").getJSONArray("events").length();client.probe()
        waitFor("Actual child native UDP probe has positive baseline") {
            val events=request(network,"evidence").getJSONArray("events")
            (baseline until events.length()).any {events.getJSONObject(it).optBoolean("continuity_probe")}
        }
    }
    private fun broker(runtime:HiddenBrowserRuntime)=HiddenBrowserRuntime::class.java.getDeclaredField("interactiveBroker").apply {isAccessible=true}.get(runtime) as? HiddenVpnBrowserBroker
    private fun front(){context.getSystemService(ActivityManager::class.java).appTasks.single {it.taskInfo?.topActivity?.className==HiddenVpnBrowserActivity::class.java.name}.moveToFront()}
    private fun route(activity:MainActivity,value:Route){MainActivity::class.java.getDeclaredMethod("setRoute",Route::class.java).apply {isAccessible=true}.invoke(activity,value)}
    private data class Fixture(val activity:MainActivity,val state:ProtectedSessionState,val runtime:HiddenBrowserRuntime,val owner:HiddenVpnBrowserBroker)
    private fun fixture(grace:AutoLockTimeout=AutoLockTimeout.MINUTES_5,run:(Fixture)->Unit) {
        val config=InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        assertEquals("/data/local/tmp/hidden-vpn1762.conf",config)
        shell("appops set ${context.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(context))
        waitFor("Prior child retired"){HiddenVpnBrowserBroker.admissionRetired() && !HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
        val profile=(VpnProfileParser.import("Main continuity",shell("cat $config")) as VpnProfileImportResult.Accepted).profile
        val paths=listOf(File(context.filesDir,"vpn-profiles"),File(context.noBackupFilesDir,"hidden-private-apps-v1"),File(context.noBackupFilesDir,"hidden-browser-library-v1"))
        val backups=paths.map {file ->File(file.parentFile,file.name+".fixture-"+UUID.randomUUID()).also {if(file.exists())check(file.renameTo(it))}}
        val preferences=HiddenPreferences(context);val oldGrace=preferences.timeout;preferences.timeout=grace
        var runtime:HiddenBrowserRuntime?=null;var state:ProtectedSessionState?=null;var activity:MainActivity?=null
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario ->
                scenario.onActivity {it.setContent {Text("Continuity fixture host")}}
                instrumentation.waitForIdleSync()
                scenario.onActivity {host ->
                    activity=host
                    val retained=ViewModelProvider(host)[ProtectedSessionState::class.java];state=retained
                    retained.session.setTimeout(AutoLockTimeout.IMMEDIATELY);retained.session.unlock();retained.key=ByteArray(32){86}
                    EncryptedVpnProfileStore(paths[0]).save(VpnProfileSnapshot(listOf(profile),profile.id),checkNotNull(retained.key))
                    retained.hidden.session.onForeground();check(retained.hidden.session.complete(retained.hidden.session.beginAuthentication(),ByteArray(32){91}))
                    retained.hidden.select(Route.BROWSER);retained.hidden.authenticated=true;route(host,Route.BROWSER)
                    HiddenPrivateAppStore(host.noBackupFilesDir,retained.hidden.session,retained.hidden.session.authority(),false).update {
                        it.upsert(PrivateAppCatalog.App(UUID.randomUUID().toString(),"Main continuity","https://site-a.fixture.invalid/continuity17103/site-a","","browser","",false,true,false))
                    }
                    runtime=HiddenBrowserRuntime(host,retained.hidden.session,HiddenConnectionPolicy.Mode.VPN_ONLY).also {it.start()}
                }
                val active=checkNotNull(runtime)
                waitFor("Production Runtime launched actual child",120){broker(active)?.valid()==true && visible("Private Apps")}
                val owner=checkNotNull(broker(active));diagnosticOwner=owner;click("Private Apps");click("Main continuity")
                waitFor("Real HTTPS fixture document ready"){visible("SITE_READY")}
                clickWeb("Start continuity probes")
                val network=checkNotNull(checkNotNull(activity).hiddenOwnedVpnNetwork())
                val baseline=request(network,"evidence").getJSONArray("events").length()
                waitFor("Fresh page, worker and native UDP probes") {
                    val events=request(network,"evidence").getJSONArray("events")
                    val kinds=(baseline until events.length()).map {events.getJSONObject(it).optString("kind")}.toSet()
                    kinds.containsAll(listOf("stun","continuity-probe","continuity-workerprobe"))
                }
                try {run(Fixture(checkNotNull(activity),checkNotNull(state),active,owner))}
                finally {main {active.close();checkNotNull(state).hidden.leave();checkNotNull(state).session.lock();route(checkNotNull(activity),Route.LOCK)}}
            }
        }finally {
            diagnosticOwner=null
            main {runtime?.close();activity?.hiddenBrowserOwner?.close();state?.hidden?.leave()}
            activity?.let {host ->val method=MainActivity::class.java.getDeclaredMethod("getWireGuardEngine").apply {isAccessible=true};(method.invoke(host) as WireGuardVpnEngine).disconnect()}
            preferences.timeout=oldGrace
            paths.zip(backups).forEach {(file,backup)->file.deleteRecursively();if(backup.exists())check(backup.renameTo(file))}
            shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
        }
    }
    private fun suspend(f:Fixture):Network {
        val old=checkNotNull(f.activity.hiddenOwnedVpnNetwork())
        shell("input keyevent KEYCODE_HOME")
        waitFor("Main and Runtime agree on denied retention",10){f.owner.retainingPresentation && f.runtime.retainingPresentation && f.owner.childIsAlive() && f.activity.hiddenOwnedVpnNetwork()==null}
        assertFalse(f.owner.valid());assertTrue(f.owner.childPresentationSnapshot().covered)
        waitFor("Primary expires without losing Hidden ownership"){!f.state.session.isUnlocked && f.state.key==null}
        assertTrue(f.state.hidden.session.isUnlocked)
        return old
    }
    @Test fun productionRuntimePreservesContinuationAfterPrimaryExpiry()=fixture {f ->
        val before=f.owner.childPresentationSnapshot();val oldAuthority=f.state.hidden.session.authority()
        val old=suspend(f);front()
        waitFor("Production reconnect reuses selected profile after Primary key expired",120){f.owner.valid() && visible("SITE_READY") && f.activity.hiddenOwnedVpnNetwork()!=old}
        val after=f.owner.childPresentationSnapshot()
        assertEquals(before.viewIdentity,after.viewIdentity);assertEquals(before.profileIdentity,after.profileIdentity);assertEquals(before.appIdentity,after.appIdentity)
        assertFalse(f.state.hidden.session.isAuthorityCurrent(oldAuthority));assertFalse(f.state.session.isUnlocked)
        clickWeb("Open synthetic approval");waitFor("Owned popup"){visible("Approve synthetic login")};clickWeb("Approve synthetic login")
        waitFor("Original opener owns callback"){visible("APPROVAL_RECEIVED")};clickWeb("Finish website session");waitFor("Protected website session"){visible("WEBSITE_SESSION_OK")}
        // A foreground route loss exercises the Runtime monitor and child NetworkCallback together.
        val restoredNetwork=checkNotNull(f.activity.hiddenOwnedVpnNetwork())
        val engine=MainActivity::class.java.getDeclaredMethod("getWireGuardEngine").apply {isAccessible=true}.invoke(f.activity) as WireGuardVpnEngine
        engine.disconnect()
        waitFor("Foreground route loss resumes the same production child",120){f.owner.valid() && f.activity.hiddenOwnedVpnNetwork()!=null && f.activity.hiddenOwnedVpnNetwork()!=restoredNetwork}
        assertEquals(before.viewIdentity,f.owner.childPresentationSnapshot().viewIdentity)
        click("Panic Exit");waitFor("Current Hidden Panic survives Primary expiry"){!f.state.hidden.session.isUnlocked && !f.owner.childIsAlive()}
        receipt("hidden-continuity17103-main.json",JSONObject().put("production_main_runtime",true).put("primary_expired",true).put("same_view_profile_app",true).put("protected_website_session",true).put("foreground_route_loss",true).put("panic_revoked",true))
    }
    @Test fun productionRuntimeHandlesRouteLossDuringSuspensionHandshake()=fixture {f ->
        val before=f.owner.childPresentationSnapshot();val initial=checkNotNull(f.activity.hiddenOwnedVpnNetwork())
        val timeOffset=request(initial,"evidence").getDouble("server_time")-System.currentTimeMillis()/1000.0
        val reached=CountDownLatch(1);val release=CountDownLatch(1)
        val callback=object:Binder() {
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                data.enforceInterface("debug.continuity17103");assertEquals(1,data.readInt());data.readLong();reached.countDown()
                check(release.await(15,TimeUnit.SECONDS));reply!!.writeNoException();return true
            }
        }
        HiddenContinuityFixtureClient(context).use {client ->
            try {
                probe(client,initial)
                client.gate(callback,target=HiddenVpnBrowserBroker.SUSPEND)
                val started=System.currentTimeMillis()/1000.0+timeOffset
                shell("input keyevent KEYCODE_HOME")
                assertTrue("Actual onStop reached suspend handoff",reached.await(10,TimeUnit.SECONDS))
                val engine=MainActivity::class.java.getDeclaredMethod("getWireGuardEngine").apply {isAccessible=true}.invoke(f.activity) as WireGuardVpnEngine
                engine.disconnect();release.countDown()
                waitFor("Both route-loss callbacks converge on one denied continuation",10){f.owner.retainingPresentation && f.owner.childIsAlive() && f.activity.hiddenOwnedVpnNetwork()==null}
                val denied=System.currentTimeMillis()/1000.0+timeOffset
                assertFalse(f.owner.valid());assertEquals(before.viewIdentity,f.owner.childPresentationSnapshot().viewIdentity)
                front();waitFor("Same production child resumes after concurrent route loss",120){f.owner.valid() && f.activity.hiddenOwnedVpnNetwork()!=null && f.activity.hiddenOwnedVpnNetwork()!=initial}
                assertEquals(before.viewIdentity,f.owner.childPresentationSnapshot().viewIdentity)
                val published=client.publishedAt();assertTrue("Observed actual transport publication",published>0)
                val publicationTime=published/1000.0+(System.currentTimeMillis()-SystemClock.elapsedRealtime())/1000.0+timeOffset
                receipt("hidden-continuity17103-main-suspend-race.json",JSONObject().put("production_main_runtime",true).put("actual_on_stop",true).put("concurrent_route_loss",true).put("native_probe_positive",true).put("same_view",true).put("suspend_started",started).put("denied_confirmed",denied).put("transport_published",publicationTime).put("resumed",System.currentTimeMillis()/1000.0+timeOffset))
            }finally {release.countDown()}
        }
    }
    @Test fun productionRuntimeRejectsVerificationAndBoundCommitRaces() {
        val outcomes=JSONArray()
        for(bound in listOf(false,true))for(action in listOf("background","screen_off","expiry","lock","panic","replacement")) {
            fixture(if(action=="expiry")AutoLockTimeout.SECONDS_30 else AutoLockTimeout.MINUTES_5) {f ->
                val initial=checkNotNull(f.activity.hiddenOwnedVpnNetwork())
                val timeOffset=request(initial,"evidence").getDouble("server_time")-System.currentTimeMillis()/1000.0
                if(!bound)request(initial,"gate?action=arm")
                val reached=CountDownLatch(1);val release=CountDownLatch(1);val pinned=AtomicBoolean();val publishedAt=java.util.concurrent.atomic.AtomicLong()
                val client=HiddenContinuityFixtureClient(context)
                val callback=object:Binder() {
                    override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                        check(Binder.getCallingUid()==Process.myUid());data.enforceInterface("debug.continuity17103");pinned.set(data.readInt()==1);publishedAt.set(data.readLong());reached.countDown()
                        check(release.await(45,TimeUnit.SECONDS));reply!!.writeNoException();return true
                    }
                }
                try {
                    probe(client,initial)
                    if(bound)client.gate(callback,pause=action=="background")
                    val stoppedAt=System.currentTimeMillis()/1000.0+timeOffset
                    val old=suspend(f);val deniedAt=System.currentTimeMillis()/1000.0+timeOffset;val oldEpoch=f.state.hidden.session.authentication()
                    if(bound && action=="expiry")Thread.sleep(20_000) // Real deadline; avoid a 30s blocked child Main/Service callback.
                    front()
                    var fresh:Network?=null
                    if(bound) {assertTrue("Actual bind reaches pre-commit barrier",reached.await(120,TimeUnit.SECONDS));assertTrue(pinned.get());fresh=f.activity.hiddenOwnedVpnNetwork()}
                    else waitFor("Production readiness probe held before publishing Network",90){
                        fresh=f.activity.hiddenOwnedVpnNetwork();fresh?.let {request(it,"gate").getBoolean("held")}==true
                    }
                    assertNotNull(fresh);assertNotEquals(old,fresh);assertFalse("Proxy IO disabled before commit",f.owner.valid())
                    val boundaryAt=System.currentTimeMillis()/1000.0+timeOffset
                    val actionRequestedAt=System.currentTimeMillis()/1000.0+timeOffset
                    var expiryAdvancedAt:Long?=null
                    var expiryStoppedAt:Long?=null
                    var expiryGrace:Long?=null
                    // Unbind first: hard revocation must never revive a service-owned child process.
                    client?.close()
                    when(action) {
                        "background" -> if(!bound)shell("input keyevent KEYCODE_HOME") // bound case invokes the real child onPause at the debug handoff.
                        "screen_off" -> shell("input keyevent KEYCODE_SLEEP")
                        "expiry" -> {
                            val continuation=HiddenVpnBrowserBroker::class.java.getDeclaredField("continuation").apply {isAccessible=true}.get(f.owner) as HiddenBrowserContinuation
                            val presentation=HiddenBrowserRuntime::class.java.getDeclaredField("presentationOwner").apply {isAccessible=true}.get(f.runtime) as MainActivity.HiddenVpnPresentationAuthority
                            val timeout=HiddenBrowserContinuation::class.java.getDeclaredField("timeout").apply {isAccessible=true}
                            expiryStoppedAt=continuation.stoppedAt();expiryGrace=timeout.getLong(continuation)
                            assertEquals(30_000L,checkNotNull(expiryGrace))
                            assertTrue("Expiry cause requires current live child before deadline",f.owner.childIsAlive() && f.activity.ownsHiddenBrowserPresentation(f.runtime,presentation) && f.state.hidden.session.isAuthenticationCurrent(oldEpoch))
                            assertEquals(HiddenBrowserContinuation.State.RECONNECTING,continuation.state())
                            if(!bound) {
                                expiryAdvancedAt=SystemClock.elapsedRealtime()
                                val shift=checkNotNull(expiryStoppedAt)+checkNotNull(expiryGrace)+1-checkNotNull(expiryAdvancedAt)
                                HiddenBrowserContinuation::class.java.getDeclaredField("clock").apply {isAccessible=true}.set(continuation,java.util.function.LongSupplier {SystemClock.elapsedRealtime()+shift})
                            }
                            waitFor(if(bound)"Original real30s deadline" else "Injected monotonic deadline retires actual child before DNS timeout",if(bound)40 else 2){!f.state.hidden.session.isAuthenticationCurrent(oldEpoch)}
                            assertTrue("Expired authentication stays terminal even after cleanup",continuation.state() in setOf(HiddenBrowserContinuation.State.LOCKED,HiddenBrowserContinuation.State.TERMINATED))
                            assertEquals(checkNotNull(expiryStoppedAt),continuation.stoppedAt());assertEquals(checkNotNull(expiryGrace),timeout.getLong(continuation))
                        }
                        "lock" -> main {f.state.hidden.lock()}
                        "panic" -> main {val owner=HiddenBrowserRuntime::class.java.getDeclaredField("presentationOwner").apply {isAccessible=true}.get(f.runtime) as MainActivity.HiddenVpnPresentationAuthority;f.activity.hiddenBrowserPanic(owner,f.runtime)}
                        "replacement" -> main {HiddenBrowserRuntime(f.activity,f.state.hidden.session,HiddenConnectionPolicy.Mode.VPN_ONLY).start()}
                    }
                    release.countDown()
                    if(!bound && action=="background") {
                        request(checkNotNull(fresh),"gate?action=release")
                        Thread.sleep(1000);assertFalse(f.owner.valid());assertTrue(f.owner.childIsAlive())
                        assertTrue(f.owner.childPresentationSnapshot().covered)
                        main {f.runtime.close()}
                    }
                    waitFor("Transition loss rejects old presentation",15){!f.owner.childIsAlive() && !f.owner.valid()}
                    val hardAt=(HiddenVpnBrowserBroker::class.java.getDeclaredField("hardAt").apply {isAccessible=true}.get(f.owner) as java.util.concurrent.atomic.AtomicLong).get()
                    assertTrue(hardAt>=0)
                    expiryAdvancedAt?.let {assertTrue("Hard revocation follows injected expiry, not an earlier route failure",hardAt>=it)}
                    val authorityLostAt=hardAt/1000.0+(System.currentTimeMillis()-SystemClock.elapsedRealtime())/1000.0+timeOffset
                    // Receipt bounds include the entire suspension and verified bind window, without trimming edges.
                    outcomes.put(JSONObject().put("phase",if(bound)"bound_before_commit" else "verification").put("action",action)
                        .put("expiry_mode",if(action=="expiry")if(bound)"real_30s" else "injected_continuation_monotonic" else JSONObject.NULL)
                        .put("expiry_advanced",expiryAdvancedAt?.let {it/1000.0+(System.currentTimeMillis()-SystemClock.elapsedRealtime())/1000.0+timeOffset} ?: JSONObject.NULL)
                        .put("expiry_original_grace_ms",expiryGrace ?: JSONObject.NULL).put("expiry_original_stop_elapsed_ms",expiryStoppedAt ?: JSONObject.NULL)
                        .put("expiry_current_live_before",action=="expiry").put("expiry_original_deadline_preserved",action=="expiry")
                        .put("native_probe_positive",true).put("transport_published",if(bound)publishedAt.get()/1000.0+(System.currentTimeMillis()-SystemClock.elapsedRealtime())/1000.0+timeOffset else JSONObject.NULL).put("boundary_reached",boundaryAt).put("suspend_started",stoppedAt).put("denied_confirmed",deniedAt).put("action_requested",actionRequestedAt).put("authority_loss",authorityLostAt).put("child_dead",System.currentTimeMillis()/1000.0+timeOffset)
                        .put("proxy_denied",true).put("retired",true).put("actual_task_background",!bound && action=="background").put("injected_on_pause",bound && action=="background"))
                }finally {release.countDown();client?.close();f.activity.hiddenOwnedVpnNetwork()?.let {runCatching {request(it,"gate?action=release")}}}
            }
        }
        receipt("hidden-continuity17103-main-races.json",JSONObject().put("production_main_runtime",true).put("cases",outcomes))
    }
    private fun receipt(name:String,value:JSONObject){File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),name).writeText(value.toString())}
}
