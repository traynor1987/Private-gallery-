package uk.co.traynor.privategallery.core.browser.hidden

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real Tor bootstrap smoke test, not DNS/packet-routing verification or Fold acceptance. */
class HiddenTorBootstrapTest {
    @Test(timeout=150_000) fun packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val service=context.packageManager.getServiceInfo(ComponentName(context,HiddenTorService::class.java),0)
        assertFalse(service.exported)
        assertEquals(context.packageName+":hidden_tor",service.processName)
        val connection=HiddenTorConnection(context)
        try {
            instrumentation.runOnMainSync {connection.start()}
            val deadline=SystemClock.elapsedRealtime()+90_000
            var port=0
            while(port==0 && SystemClock.elapsedRealtime()<deadline) {port=connection.port();if(port==0)Thread.sleep(500)}
            assertTrue("Real Tor circuit did not bootstrap within the browser's90s limit; no route fallback is allowed",port in 1..65535)
            assertTrue(connection.alive())
        } finally {instrumentation.runOnMainSync {connection.close()}}
        assertFalse(connection.alive())
        assertEquals(0,connection.port())
    }
}
