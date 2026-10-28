package uk.co.traynor.privategallery.core.browser.hidden

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

/** No VPN/profile requirement: cold rejection, manifest isolation and policy boundaries. */
class HiddenVpnBoundary1762Test {
    @Test fun childAndUploadProviderHaveIndependentNonExportedBoundaries() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val info=context.packageManager.getActivityInfo(ComponentName(context,HiddenVpnBrowserActivity::class.java),0)
        assertFalse(info.exported)
        assertEquals(context.packageName+":hidden_vpn_browser",info.processName)
        val provider=context.packageManager.resolveContentProvider(context.packageName+".hiddenbrowser",0)!!
        assertFalse(provider.exported);assertFalse(provider.grantUriPermissions)
        assertEquals(context.packageName,provider.processName)
        assertFalse("Production activation awaits actual VPN evidence",HiddenVpnActivationGate.INTERACTIVE_VERIFIED)
    }
    @Test fun noCapabilityLaunchCannotBindPrimaryOrRetainChild() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(ConnectivityManager::class.java)
        val before=manager.boundNetworkForProcess
        val pid=Process.myPid()
        context.startActivity(Intent(context,HiddenVpnBrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Cold failed bootstrap must exit. The primary instrumentation process must stay alive.
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        var alive=true
        do {
            Thread.sleep(100)
            alive=context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().any {it.processName==context.packageName+":hidden_vpn_browser"}
        } while(alive && android.os.SystemClock.elapsedRealtime()<deadline)
        assertFalse("Child must not survive unauthenticated launch",alive)
        assertEquals(pid,Process.myPid());assertEquals(before,manager.boundNetworkForProcess)
    }
    @Test fun uploadUriCannotRecoverAuthorityAfterProcessOrSessionLoss() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val uri=Uri.parse("content://${context.packageName}.hiddenbrowser/retired-session/retired-grant")
        try {context.contentResolver.openInputStream(uri)?.close();fail("Stale upload capability must fail closed")} catch(expected:RuntimeException) {} catch(expected:java.io.FileNotFoundException) {}
    }
    @Test fun proxyRejectsPrivateAndAmbiguousDnsDestinationsIncludingUlaAndCgnat() {
        listOf("0.0.0.0","127.0.0.1","10.0.0.1","172.16.0.1","192.168.0.1","169.254.1.1","100.64.0.1","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1").forEach {assertFalse(it,HiddenVpnProxy.publicAddress(InetAddress.getByName(it)))}
        assertTrue(HiddenVpnProxy.publicAddress(InetAddress.getByName("203.0.113.10")))
    }
}
