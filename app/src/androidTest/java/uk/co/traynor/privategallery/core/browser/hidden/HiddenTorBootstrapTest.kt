package uk.co.traynor.privategallery.core.browser.hidden

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real Tor bootstrap smoke test, not DNS/packet-routing verification or Fold acceptance. */
class HiddenTorBootstrapTest {
    @Test(timeout=420_000) fun packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased() {
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
            HiddenHttpTransport.tor(port) {connection.alive()}.use {transport ->
                transport.verifyReady()
                val evidence=java.io.File(context.filesDir,"hidden-live-navigation-result.txt")
                val results=mutableListOf<String>()
                try {
                    // Public endpoints are observations, not deterministic fixtures. Never log owner URLs.
                    for((label,url) in listOf(
                        "bbc-https" to "https://bbc.co.uk/",
                        "bbc-onion" to "http://www.bbcnewsd73hkzno2ini43t4gblxvycyac5aw4gnv7t2rccijh7745uqd.onion/"
                    )) {
                        val response=transport.request(url,"GET")
                        val kind=response.responseHeaders?.get(HiddenHttpTransport.RESULT_HEADER)
                        val count=response.data.use {it.read(ByteArray(256))}
                        results.add("$label status=${response.statusCode} local=${kind ?: "none"} bodyBytes=$count")
                        assertNull("$label must be an actual upstream response, not synthetic success",kind)
                        assertTrue("$label returns an upstream HTTP response",response.statusCode in 200..499 && count>0)
                    }
                } finally {
                    evidence.writeText(results.joinToString("\n")+"\n")
                    println("Controlled live Tor observations: "+results.joinToString("; "))
                }
            }
        } finally {instrumentation.runOnMainSync {connection.close()}}
        assertFalse(connection.alive())
        assertEquals(0,connection.port())
    }
}
