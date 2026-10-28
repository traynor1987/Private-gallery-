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
        assertEquals(context.packageName+".hiddenbrowser",info.taskAffinity)
        assertTrue(info.flags and android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
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
        // The start transaction has installed an Activity record, but the cold process may
        // not exist yet. A single absent PID sample used to pass before a kill/restart loop.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val activities=context.getSystemService(ActivityManager::class.java)
        val child=ComponentName(context,HiddenVpnBrowserActivity::class.java)
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        var absentSince=0L
        var retired=false
        do {
            val now=android.os.SystemClock.elapsedRealtime()
            val alive=activities.runningAppProcesses.orEmpty().any {it.processName==context.packageName+":hidden_vpn_browser"}
            val pending=activities.appTasks.any {task ->
                val info=task.taskInfo ?: return@any true
                info.baseActivity==child || info.topActivity==child
            }
            if(alive || pending)absentSince=0L else {
                if(absentSince==0L)absentSince=now
                retired=now-absentSince>=1_000
            }
            if(!retired)Thread.sleep(100)
        } while(!retired && android.os.SystemClock.elapsedRealtime()<deadline)
        assertTrue("Unauthenticated child process and Activity record must remain retired",retired)
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
