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
                policy.set(true)
                waitFor("Authenticated Hidden opt-in reaches actual child") {broker!!.childPresentationSnapshot().let{!it.covered&&!it.secure&&it.theme==AppTheme.DARK}}
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
