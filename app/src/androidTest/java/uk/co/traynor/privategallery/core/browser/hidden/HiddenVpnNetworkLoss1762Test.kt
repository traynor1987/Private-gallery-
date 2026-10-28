package uk.co.traynor.privategallery.core.browser.hidden

import android.content.ContextWrapper
import android.net.*
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vpn.*

/** Actual owned Network disappearance while the unchanged native child has an in-flight request. */
class HiddenVpnNetworkLoss1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private fun shell(value:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(value)).use{it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,test:()->Boolean){
        val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L
        while(android.os.SystemClock.elapsedRealtime()<deadline){if(runCatching(test).getOrDefault(false))return;Thread.sleep(100)}
        assertTrue(message,runCatching(test).getOrDefault(false))
    }
    private fun nodes():List<android.view.accessibility.AccessibilityNodeInfo>{
        val all=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun visit(node:android.view.accessibility.AccessibilityNodeInfo?){if(node==null)return;all+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return all
    }
    private fun touchControl(label:String) {
        var previous:android.graphics.Rect?=null
        val bounds=android.graphics.Rect()
        waitFor("Navigate in native child: visible stable $label") {
            val root=automation.rootInActiveWindow ?: return@waitFor false
            val node=nodes().firstOrNull{it.windowId==root.windowId && it.isEnabled && it.isVisibleToUser &&
                (it.text?.toString()==label || it.contentDescription?.toString()==label)} ?: return@waitFor false
            node.getBoundsInScreen(bounds)
            val window=android.graphics.Rect();root.getBoundsInScreen(window)
            val visible=!bounds.isEmpty && bounds.intersect(window)
            val stable=visible && previous==bounds
            previous=if(visible)android.graphics.Rect(bounds) else null
            stable
        }
        val down=android.os.SystemClock.uptimeMillis()
        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{assertTrue("Ordinary touch $label",automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun evidence(network:Network):org.json.JSONArray {
        val connection=network.openConnection(URL("http://fixture.invalid/evidence")) as java.net.HttpURLConnection
        connection.connectTimeout=3000;connection.readTimeout=3000
        return try{connection.inputStream.use{JSONObject(it.readBytes().toString(Charsets.UTF_8)).getJSONArray("events")}}finally{connection.disconnect()}
    }
    @Test fun lostOwnedNetworkRetiresNativeChildAndCannotReuseOldCapability(){
        val config=InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        assumeTrue("Owned VPN fixture required",config!=null);require(config=="/data/local/tmp/hidden-vpn1762.conf")
        val context=instrumentation.targetContext
        shell("appops set ${context.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(context))
        val imported=VpnProfileParser.import("Disposable lost-network VPN",shell("cat $config")) as VpnProfileImportResult.Accepted
        val engine=WireGuardVpnEngine(OfficialWireGuardBackend(context))
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){71}))
        val permitted=AtomicBoolean(true);val ended=AtomicBoolean()
        val root=File(context.noBackupFilesDir,"network-loss1762-${UUID.randomUUID()}")
        var broker:HiddenVpnBrowserBroker?=null
        try {
            engine.connect(imported.profile)
            waitFor("Real WireGuard handshake",90){engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}
            val manager=context.getSystemService(ConnectivityManager::class.java)
            var network:Network?=null
            waitFor("One immutable app-owned VPN Network") {
                network=manager.allNetworks.singleOrNull{manager.getNetworkCapabilities(it)?.let{caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.ownerUid==Process.myUid()}==true};network!=null
            }
            val pinned=checkNotNull(network)
            ActivityScenario.launch(ComponentActivity::class.java).use{scenario ->
                scenario.onActivity{activity ->
                    val isolated=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=root}
                    broker=HiddenVpnBrowserBroker(isolated,session,pinned,{permitted.get()&&engine.ownsTunnel&&engine.state==VpnConnectionState.CONNECTED&&manager.getNetworkCapabilities(pinned)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true},{ended.set(true)})
                    broker!!.launch()
                }
                waitFor("Native child address") {
                    nodes().firstOrNull{it.isEditable}?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"http://fixture.invalid/network-loss")})==true
                }
                // The shared address bar exposes its Go icon by content description, not Text.
                touchControl("Go")
                waitFor("Real request is in flight before transport loss") {
                    val events=evidence(pinned)
                    (0 until events.length()).any{events.getJSONObject(it).let{event->event.optString("kind")=="slow-start"&&event.optString("path")=="/network-loss"}}
                }
                engine.disconnect()
                waitFor("Owned VPN actually disappears",15){!engine.ownsTunnel&&manager.getNetworkCapabilities(pinned)==null}
                waitFor("Network-loss kills old native lifetime",10){ended.get()&&!broker!!.childIsAlive()&&!HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
                assertFalse(broker!!.valid());assertTrue("Transport loss does not alter independent credentials",session.isUnlocked)
                try {broker!!.launch();fail("Old capability admitted a replacement route")}catch(expected:IllegalStateException){}
                var failed=false
                var stale:java.net.HttpURLConnection?=null
                try{
                    stale=pinned.openConnection(URL("http://fixture.invalid/network-loss")) as java.net.HttpURLConnection
                    stale.connectTimeout=1500;stale.readTimeout=1500;stale.inputStream.use{it.read()}
                }catch(expected:java.io.IOException){failed=true}finally{stale?.disconnect()}
                assertTrue("Dead pinned Network cannot become a direct request",failed)
                assertNull("Parent process was never rebound to a fallback route",manager.boundNetworkForProcess)
                waitFor("Offline browser roots removed",15){!File(context.noBackupFilesDir,"hidden-vpn-webview-v1/${broker!!.token}").exists()&&!File(context.cacheDir,"hidden-vpn-webview-v1/${broker!!.token}").exists()}
                File(context.filesDir,"vpn-network-loss1762-result.json").writeText(JSONObject().put("network_lost",true).put("child_dead",true).put("old_capability_denied",true).put("dead_network_failed",true).put("credentials_retained",true).put("owned_roots_removed",true).toString())
            }
        }finally{permitted.set(false);broker?.close();session.lock();engine.disconnect();root.deleteRecursively()}
    }
}
