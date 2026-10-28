package uk.co.traynor.privategallery

import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.hidden.*
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.core.vpn.*
import uk.co.traynor.privategallery.ui.HiddenPreferences

/** Real Main lifecycle and unmodified production activation gate. Synthetic in-memory auth only.
 * Select this suite after isolated native transport verification permits activation. */
class HiddenMainBrowserLifecycle1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,predicate:()->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L
        while(android.os.SystemClock.elapsedRealtime()<deadline){if(runCatching(predicate).getOrDefault(false))return;Thread.sleep(100)}
        assertTrue(message,runCatching(predicate).getOrDefault(false))
    }
    private fun route(activity:MainActivity,value:Route) {
        MainActivity::class.java.getDeclaredMethod("setRoute",Route::class.java).apply{isAccessible=true}.invoke(activity,value)
    }
    @Test fun realMainToNativeChildThenHomeAppliesIndependentLockPolicies() {
        val config=InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        assumeTrue("Owned VPN fixture required",config!=null)
        require(config=="/data/local/tmp/hidden-vpn1762.conf")
        assertTrue("Production interactive activation must have passed prior security gates",HiddenVpnActivationGate.INTERACTIVE_VERIFIED)
        val context=instrumentation.targetContext
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull(VpnService.prepare(context))
        val imported=VpnProfileParser.import("Disposable Main lifecycle VPN",shell("cat $config")) as VpnProfileImportResult.Accepted
        val mode=context.getSharedPreferences("hidden-browser-v16",0)
        val oldMode=mode.getString("mode",null)
        val policy=HiddenPreferences(context);val oldTimeout=policy.timeout
        policy.timeout=AutoLockTimeout.SECONDS_30
        mode.edit().putString("mode",HiddenConnectionPolicy.Mode.VPN_ONLY.name).commit()
        var engine:WireGuardVpnEngine?=null
        try {
            ActivityScenario.launch(MainActivity::class.java).use{scenario ->
                lateinit var state:ProtectedSessionState
                lateinit var activity:MainActivity
                scenario.onActivity{main ->
                    activity=main;state=ViewModelProvider(main)[ProtectedSessionState::class.java]
                    state.session.setTimeout(AutoLockTimeout.IMMEDIATELY);state.session.unlock()
                    assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){63}))
                    state.hidden.select(Route.BROWSER);state.hidden.authenticated=true
                    engine=MainActivity::class.java.getDeclaredMethod("getWireGuardEngine").apply{isAccessible=true}.invoke(main) as WireGuardVpnEngine
                    engine!!.connect(imported.profile)
                }
                waitFor("Main-owned WireGuard peer handshake",90){engine!!.ownsTunnel && engine!!.state==VpnConnectionState.CONNECTED}
                scenario.onActivity{route(it,Route.BROWSER)}
                waitFor("Production Runtime launches the real native browser",120){activity.hiddenBrowserOwner?.interactiveActivityActive==true}
                waitFor("Main has actually stopped for its authenticated child") {activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED}
                assertTrue("Child handoff must retain independent Hidden auth",state.hidden.session.isUnlocked)
                assertTrue("Handoff must not count as Primary background",state.session.isUnlocked)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                waitFor("Child Home applies immediate Primary lock",10){!state.session.isUnlocked}
                assertTrue("Hidden keeps its separate 30-second grace",state.hidden.session.isUnlocked)
                try {state.hidden.session.beginOperation().close();fail("Background Hidden operation admitted")}catch(expected:IllegalStateException){}
                waitFor("Revoked native child exits",15){!HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
                // Real foreground restart must retain or expire the original deadline, never renew it.
                context.startActivity(android.content.Intent(context,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                waitFor("Main returns through real onStart") {activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)}
                assertFalse("Primary immediate lock survives restart",state.session.isUnlocked)
                state.hidden.session.lock()
            }
        }finally{
            engine?.disconnect();policy.timeout=oldTimeout
            mode.edit().apply{if(oldMode==null)remove("mode")else putString("mode",oldMode)}.commit()
        }
    }
    @Test fun realMainVpnTorVpnSwitchRetiresOldProcessAndDoesNotRestoreCookies() {
        val config=InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        val onion=InstrumentationRegistry.getArguments().getString("hiddenOnionProbeUrl")
        assumeTrue("Both controlled transports required",config!=null && onion!=null)
        require(config=="/data/local/tmp/hidden-vpn1762.conf")
        assertTrue(HiddenVpnActivationGate.INTERACTIVE_VERIFIED && HiddenVpnActivationGate.TOR_NATIVE_VERIFIED)
        val context=instrumentation.targetContext
        val automation=instrumentation.uiAutomation
        val info=automation.serviceInfo;info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.serviceInfo=info
        fun nodes():List<android.view.accessibility.AccessibilityNodeInfo> {
            val all=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            fun visit(node:android.view.accessibility.AccessibilityNodeInfo?){if(node==null)return;all+=node;repeat(node.childCount){visit(node.getChild(it))}}
            visit(automation.rootInActiveWindow);return all
        }
        fun click(text:String) {
            var target:android.view.accessibility.AccessibilityNodeInfo?=null
            waitFor("Mode control '$text'") {
                target=nodes().firstOrNull {it.isEnabled && (it.text?.toString()==text || it.contentDescription?.toString()==text)}
                target?.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                target!=null
            }
            val window=checkNotNull(target).windowId
            val bounds=android.graphics.Rect()
            var previousBounds:android.graphics.Rect?=null
            waitFor("Fresh stable mode control '$text'") {
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
            val at=android.os.SystemClock.uptimeMillis()
            for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(at,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try {assertTrue("Actual touch delivered to '$text'",automation.injectInputEvent(event,true))}finally{event.recycle()}
            }
        }
        fun navigate(url:String) {
            waitFor("Native address field") {nodes().firstOrNull{it.isEditable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,url)})==true}
            click("Go")
        }
        fun broker(activity:MainActivity):HiddenVpnBrowserBroker? = activity.hiddenBrowserOwner?.let {owner ->
            HiddenBrowserRuntime::class.java.getDeclaredField("interactiveBroker").apply{isAccessible=true}.get(owner) as? HiddenVpnBrowserBroker
        }
        fun evidence(network:android.net.Network):org.json.JSONArray {
            val connection=network.openConnection(java.net.URL("http://fixture.invalid/evidence")) as java.net.HttpURLConnection
            connection.connectTimeout=3000;connection.readTimeout=3000
            return try{connection.inputStream.use{org.json.JSONObject(it.readBytes().toString(Charsets.UTF_8)).getJSONArray("events")}}finally{connection.disconnect()}
        }
        shell("appops set ${context.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(context))
        val imported=VpnProfileParser.import("Disposable mode switch VPN",shell("cat $config")) as VpnProfileImportResult.Accepted
        val mode=context.getSharedPreferences("hidden-browser-v16",0);val oldMode=mode.getString("mode",null)
        val policy=HiddenPreferences(context);val oldTimeout=policy.timeout
        mode.edit().putString("mode",HiddenConnectionPolicy.Mode.VPN_ONLY.name).commit();policy.timeout=AutoLockTimeout.MINUTES_5
        var engine:WireGuardVpnEngine?=null
        try {ActivityScenario.launch(MainActivity::class.java).use{scenario ->
            lateinit var activity:MainActivity
            lateinit var state:ProtectedSessionState
            scenario.onActivity{main ->
                activity=main;state=ViewModelProvider(main)[ProtectedSessionState::class.java]
                state.session.setTimeout(AutoLockTimeout.MINUTES_5);state.session.unlock()
                assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){23}))
                state.hidden.select(Route.BROWSER);state.hidden.authenticated=true
                engine=MainActivity::class.java.getDeclaredMethod("getWireGuardEngine").apply{isAccessible=true}.invoke(main) as WireGuardVpnEngine
                val controller=MainActivity::class.java.getDeclaredMethod("getBrowserVpnController").apply{isAccessible=true}.invoke(main) as BrowserVpnController
                controller.select(imported.profile);engine!!.connect(imported.profile)
            }
            waitFor("Initial Main-owned VPN handshake",90){engine!!.ownsTunnel && engine!!.state==VpnConnectionState.CONNECTED}
            scenario.onActivity{route(it,Route.BROWSER)}
            waitFor("Production VPN child",120){broker(activity)?.active==true}
            val first=checkNotNull(broker(activity));val firstToken=first.token
            assertEquals(true to true,first.childSecuritySnapshot())
            navigate("http://fixture.invalid/cookie");waitFor("VPN cookie response"){nodes().any{it.text?.toString()=="cookie set"}}
            click("Done")
            waitFor("Main returns for immediate mode selection",15){activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)}
            click("Hidden browser settings");click("Tor Only")
            waitFor("Production Tor-only child after WireGuard release",120){activity.hiddenBrowserOwner?.mode==HiddenConnectionPolicy.Mode.TOR_ONLY && broker(activity)?.active==true}
            assertFalse("Tor switch releases the previous WireGuard transport",engine!!.ownsTunnel)
            val tor=checkNotNull(broker(activity));assertNotEquals(firstToken,tor.token)
            assertFalse("Old VPN authority cannot survive new mode admission",first.valid())
            assertEquals(false to true,tor.childSecuritySnapshot())
            navigate(checkNotNull(onion).trimEnd('/')+"/script-probe")
            waitFor("Native Tor page survives production mode switch",60){nodes().any{it.text?.toString()=="Tor script probe"}}
            click("Done")
            waitFor("Main returns while Tor teardown may still be pending",15){activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)}
            if(nodes().none{it.text?.toString()=="VPN Only"})click("Hidden browser settings")
            click("VPN Only")
            waitFor("Fresh production VPN child",120){activity.hiddenBrowserOwner?.mode==HiddenConnectionPolicy.Mode.VPN_ONLY && broker(activity)?.active==true}
            val lastBroker=checkNotNull(broker(activity));assertNotEquals(firstToken,lastBroker.token);assertNotEquals(tor.token,lastBroker.token)
            assertEquals(true to true,lastBroker.childSecuritySnapshot())
            assertFalse("Old Tor authority cannot survive new mode admission",tor.valid())
            assertTrue("Tor sink is retired before the fresh VPN child",HiddenTorIsolationService.released())
            val network=checkNotNull(activity.hiddenOwnedVpnNetwork())
            val before=evidence(network).length()
            navigate("http://fixture.invalid/cookie-echo")
            waitFor("New VPN profile does not restore previous mode-session cookies") {
                val events=evidence(network)
                (before until events.length()).any{events.getJSONObject(it).let{event ->event.optString("path")=="/cookie-echo" && !event.optBoolean("cookie_present")}}
            }
            assertTrue(state.session.isUnlocked);assertTrue(state.hidden.session.isUnlocked)
            val returningAuthority=state.hidden.session.authority()
            click("Browser settings");click("Downloads");click("Manage in Hidden Vault")
            waitFor("Downloads returns through actual Main foreground into authenticated Hidden Vault",15) {
                activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.RESUMED &&
                    state.hidden.selected && state.hidden.destination==Route.VAULT &&
                    state.hidden.session.isAuthorityCurrent(returningAuthority)
            }
            waitFor("Downloads return retires its actual native child",15) {
                !lastBroker.valid() && !lastBroker.childIsAlive() && !HiddenVpnBrowserBroker.hasDedicatedProcess(context)
            }
            instrumentation.runOnMainSync{state.hidden.session.lock();state.session.lock()}
        }}finally{
            engine?.disconnect();policy.timeout=oldTimeout
            mode.edit().apply{if(oldMode==null)remove("mode")else putString("mode",oldMode)}.commit()
        }
    }

}
