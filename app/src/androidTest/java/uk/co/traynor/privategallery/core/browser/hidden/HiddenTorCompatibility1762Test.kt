package uk.co.traynor.privategallery.core.browser.hidden

import android.content.*
import android.net.VpnService
import android.os.*
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.torproject.jni.TorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Regressions for exact tor-android0.4.9.14 adapter and pre-TUN foreground startup failure. */
class HiddenTorCompatibility1762Test {
    @Test fun isolatedUnixListenerDoesNotEnterDependencyPrivateTcpParser() {
        assertTrue("Review adapter again when dependency changes",TorService.VERSION_NAME.startsWith("0.4.9.14"))
        val service=HiddenTorService()
        HiddenTorService::class.java.getDeclaredField("isolated").apply {isAccessible=true}.setBoolean(service,true)
        val parser=TorService::class.java.getDeclaredMethod("getPortFromGetInfo",String::class.java).apply {isAccessible=true}
        // Invoke the actual pinned dependency parser: it dispatches virtually into our adapter.
        // A fabricated integer parser in this test would not catch a regression in dispatch.
        assertEquals("Unix endpoint is not a TCP port",0,parser.invoke(service,"net/listeners/socks"))
        assertEquals("",service.getInfo("net/listeners/socks"))
        assertNull("Other control metadata is delegated, not fabricated readiness",service.getInfo("status/circuit-established"))
    }
    @Test fun rejectedForegroundStartupCannotStrandIsolationOwnerOrAdmitTun() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow")).use {it.readBytes()}
        assertNull(VpnService.prepare(context))
        assertTrue("No other Tor isolation fixture may overlap this case",HiddenTorIsolationService.released())
        val completed=CountDownLatch(1);var failed:Throwable?=null
        lateinit var connection:ServiceConnection
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                connection=object:ServiceConnection {
                    override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                        try {
                            val service=(binder as HiddenTorIsolationService.OwnerBinder).service()
                            var rejected=false
                            try {service.establish({true},{},{throw SecurityException("Synthetic foreground rejection")})}catch(expected:SecurityException){rejected=true}
                            assertTrue("Foreground platform failure must propagate",rejected)
                            assertNull("No TUN admitted after foreground rejection",service.network())
                            assertNull(service.relay)
                            assertTrue("Failed startup releases singleton immediately",HiddenTorIsolationService.released())
                        }catch(error:Throwable){failed=error}finally{completed.countDown()}
                    }
                    override fun onServiceDisconnected(name:ComponentName){}
                }
                assertTrue(activity.bindService(Intent(activity,HiddenTorIsolationService::class.java),connection,Context.BIND_AUTO_CREATE))
            }
            assertTrue("Failure callback returned",completed.await(5,TimeUnit.SECONDS))
            scenario.onActivity {it.unbindService(connection)}
            failed?.let {throw it}
        }
    }
}
