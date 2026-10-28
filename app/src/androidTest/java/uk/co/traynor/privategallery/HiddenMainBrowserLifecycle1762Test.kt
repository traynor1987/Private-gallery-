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
}
