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
        assertTrue(message,runCatching(predicate).getOrDefault(false))
    }
    private fun nodes():List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?) {if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return result
    }
    private fun click(text:String) {
        waitFor("Visible actionable '$text'") {
            val node=nodes().firstOrNull {it.text?.toString()==text} ?: return@waitFor false
            var target:AccessibilityNodeInfo?=node
            while(target!=null && !target.isClickable)target=target.parent
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true
        }
    }
    private fun navigate(url:String) {
        waitFor("Dedicated browser address input") {
            val field=nodes().firstOrNull {it.isEditable} ?: return@waitFor false
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,url)})
        }
        click("Go")
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
        val context=instrumentation.targetContext
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
        try {
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
                scenario.onActivity {activity ->
                    val isolated=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=root}
                    val repo=HiddenRepository(isolated,session)
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
                navigate(origin);click("Run JavaScript POST")
                waitFor("Native JavaScript fetch transmits the exact POST body over VPN",30) {observed(network,origin){it.optString("path")=="/post" && it.optBoolean("javascript_body")}}
                navigate("$origin/form");click("Submit form")
                waitFor("Native HTML form reaches fixture",30){observed(network,origin){it.optString("path")=="/form-result" && it.optBoolean("form_body")}}
                navigate("$origin/upload");click("Choose File")
                waitFor("Hidden vault chooser") {nodes().any {it.text?.toString()=="CI eligible upload.png"}}
                assertFalse("Restricted item never appears in egress chooser",nodes().any {it.text?.toString()=="CI restricted upload.png"})
                click("CI eligible upload.png");click("Allow upload");click("Upload media")
                waitFor("Multipart body reaches VPN fixture",30) {observed(network,origin){it.optString("path")=="/uploaded" && it.optBoolean("multipart_png")}}
                navigate("$origin/slow")
                waitFor("Slow response active before revocation",30){observed(network,origin){it.optString("kind")=="slow-start"}}
                // Explicit Hidden lock and broker close are production revocation paths. No route rebind.
                session.lock();allowed.set(false);broker!!.close()
                waitFor("Child owner ended",10){ended.get()}
                assertFalse(broker!!.active)
                assertFalse(broker!!.valid())
                assertTrue("Main process remains independent from child binding",manager.boundNetworkForProcess==null)
                // Host fixture/captures assert the stalled request closes and no direct-origin/DNS packets occur.
            }
        } finally {
            allowed.set(false);broker?.close();session.lock();engine.disconnect()
            waitFor("CI VPN teardown",15){!engine.ownsTunnel}
            root.deleteRecursively()
        }
    }
}
