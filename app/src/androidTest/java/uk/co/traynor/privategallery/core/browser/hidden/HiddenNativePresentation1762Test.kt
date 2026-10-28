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
import org.junit.Rule
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.ui.BrowserViewportRule
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vpn.*

/** Authenticated parent supplies both policy values to the unchanged production child. */
class HiddenNativePresentation1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    @get:Rule val viewport:TestRule=BrowserViewportRule(fontScale=2f) {320 to 640}
    private fun nodes():List<android.view.accessibility.AccessibilityNodeInfo> {
        val result=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun visit(node:android.view.accessibility.AccessibilityNodeInfo?) {if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(instrumentation.uiAutomation.rootInActiveWindow);return result
    }
    private fun actualChrome(width:Int,height:Int) {
        waitFor("Actual native ${width}x$height chrome at OS two-times text") {
            val automation=instrumentation.uiAutomation
            val root=automation.rootInActiveWindow ?: return@waitFor false
            val window=android.graphics.Rect();root.getBoundsInScreen(window)
            if(window.width()!=width || window.height()!=height)return@waitFor false
            if(automation.windows.any {it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.isActive})return@waitFor false
            val tree=nodes()
            val address=tree.firstOrNull {it.isEditable && it.isVisibleToUser && it.isEnabled} ?: return@waitFor false
            val addressBounds=android.graphics.Rect();address.getBoundsInScreen(addressBounds)
            if(addressBounds.width()<48 || addressBounds.height()<48 || !window.contains(addressBounds))return@waitFor false
            for(label in listOf("Go","Reload","Bookmarks","Tabs","Browser settings","More")) {
                var control=tree.firstOrNull {it.isVisibleToUser && (it.contentDescription?.toString()==label || it.text?.toString()==label)} ?: return@waitFor false
                while(!control.isClickable)control=control.parent ?: return@waitFor false
                val bounds=android.graphics.Rect();control.getBoundsInScreen(bounds)
                if(!control.isEnabled || bounds.width()<48 || bounds.height()<48 || !window.contains(bounds))return@waitFor false
            }
            tree.any {node ->
                if(node.className?.toString()!="android.webkit.WebView" || !node.isVisibleToUser)false
                else {val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds);bounds.width()>=48 && bounds.height()>=48 && window.contains(bounds)}
            }
        }
    }
    private fun shell(value:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(value)).use{it.readBytes().toString(Charsets.UTF_8)}
    private fun waitFor(message:String,seconds:Int=30,test:()->Boolean){
        val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L
        while(android.os.SystemClock.elapsedRealtime()<deadline){if(runCatching(test).getOrDefault(false))return;Thread.sleep(100)}
        assertTrue(message,runCatching(test).getOrDefault(false))
    }
    @Test fun nativeChildUsesParentThemeAndHiddenCapturePolicyWithoutCrossProcessPreferenceRead() {
        val config=InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        assumeTrue("Owned VPN fixture required",config!=null);require(config=="/data/local/tmp/hidden-vpn1762.conf")
        val context=instrumentation.targetContext
        val service=instrumentation.uiAutomation.serviceInfo
        service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=service
        assertEquals(2f,context.resources.configuration.fontScale,0.001f)
        assertEquals(160,context.resources.configuration.densityDpi)
        shell("appops set ${context.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(context))
        val profile=(VpnProfileParser.import("Disposable presentation VPN",shell("cat $config")) as VpnProfileImportResult.Accepted).profile
        val engine=WireGuardVpnEngine(OfficialWireGuardBackend(context));val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){72}))
        val policy=AtomicBoolean(false);val theme=AtomicReference(AppTheme.DARK)
        val root=File(context.noBackupFilesDir,"presentation1762-${UUID.randomUUID()}")
        var broker:HiddenVpnBrowserBroker?=null
        try {
            engine.connect(profile);waitFor("Owned VPN handshake",90){engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}
            val manager=context.getSystemService(ConnectivityManager::class.java);var network:Network?=null
            waitFor("Owned Network") {network=manager.allNetworks.singleOrNull{manager.getNetworkCapabilities(it)?.let{caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.ownerUid==Process.myUid()}==true};network!=null}
            ActivityScenario.launch(ComponentActivity::class.java).use{scenario ->
                scenario.onActivity{activity ->
                    val isolated=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=root}
                    broker=HiddenVpnBrowserBroker(isolated,session,checkNotNull(network),{engine.ownsTunnel&&engine.state==VpnConnectionState.CONNECTED},{},hiddenCaptureAllowed={policy.get()},hiddenTheme={theme.get()})
                    broker!!.launch()
                }
                waitFor("Default-off actual native window, authoritative dark theme") {broker!!.childPresentationSnapshot().let{!it.covered&&it.secure&&it.theme==AppTheme.DARK}}
                actualChrome(320,640)
                policy.set(true)
                waitFor("Authenticated Hidden opt-in reaches actual child") {broker!!.childPresentationSnapshot().let{!it.covered&&!it.secure&&it.theme==AppTheme.DARK}}
                shell("wm size 840x392")
                actualChrome(840,392)
                waitFor("Landscape keeps authenticated dark capture policy") {broker!!.childPresentationSnapshot().let{!it.covered&&!it.secure&&it.theme==AppTheme.DARK}}
                theme.set(AppTheme.LIGHT)
                waitFor("Selected light theme replaces dark without preference cache") {broker!!.childPresentationSnapshot().let{!it.covered&&!it.secure&&it.theme==AppTheme.LIGHT}}
                policy.set(false)
                waitFor("Disabling Hidden capture re-protects actual child") {broker!!.childPresentationSnapshot().let{!it.covered&&it.secure}}
                policy.set(true)
                waitFor("Capture opt-in is active before revocation") {broker!!.childPresentationSnapshot().let{!it.covered&&!it.secure}}
                session.lock()
                waitFor("Revoked authentication has no surviving capture window",10){!broker!!.childIsAlive()&&!HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
            }
        }finally{broker?.close();session.lock();engine.disconnect();root.deleteRecursively()}
    }
}
