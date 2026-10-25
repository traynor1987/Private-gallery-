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
            val deadline=SystemClock.elapsedRealtime()+HiddenBrowserRuntime.STARTUP_TIMEOUT_MS
            val phases=mutableListOf<Int>()
            var port=0
            while(port==0 && SystemClock.elapsedRealtime()<deadline) {
                val progress=connection.bootstrapProgress()
                if(phases.lastOrNull()!=progress) {phases.add(progress);println("Tor numeric bootstrap phase: $progress")}
                port=connection.port();if(port==0)Thread.sleep(500)
            }
            assertTrue("Real Tor circuit did not bootstrap within the production startup budget; numeric phases=$phases; no route fallback is allowed",port in 1..65535)
            assertTrue(connection.alive())
            HiddenHttpTransport.tor(port) {connection.alive()}.use {transport ->
                transport.verifyReady()
                val evidence=java.io.File(context.filesDir,"hidden-live-navigation-result.txt")
                val results=mutableListOf<String>()
                val arguments=InstrumentationRegistry.getArguments()
                val onionUrl=arguments.getString("hiddenOnionProbeUrl")
                    ?: "https://www.bbcnewsd73hkzno2ini43t4gblxvycyac5aw4gnv7t2rccijh7745uqd.onion/"
                val expected=arguments.getString("hiddenOnionProbeExpected")
                try {
                    // Public endpoints are observations, not deterministic fixtures. Never log owner URLs.
                    for((label,url) in listOf(
                        "bbc-https" to "https://bbc.co.uk/",
                        "onion-probe" to onionUrl
                    )) {
                        val started=SystemClock.elapsedRealtime()
                        val response=transport.request(url,"GET")
                        val kind=response.responseHeaders?.get(HiddenHttpTransport.RESULT_HEADER)
                        val bytes=ByteArray(256)
                        val count=response.data.use {input ->
                            var completed=0
                            while(completed<bytes.size) {
                                val read=input.read(bytes,completed,bytes.size-completed)
                                if(read<0)break
                                completed+=read
                            }
                            completed
                        }
                        val failure=response.responseHeaders?.get(HiddenHttpTransport.FAILURE_HEADER) ?: "none"
                        results.add("$label status=${response.statusCode} local=${kind ?: "none"} failure=$failure elapsedMs=${SystemClock.elapsedRealtime()-started} bodyBytes=$count")
                        assertNull("$label must be an actual upstream response, not synthetic success",kind)
                        assertTrue("$label returns an upstream HTTP response",response.statusCode in 200..499 && count>0)
                        if(label=="onion-probe" && expected!=null) {
                            assertEquals(200,response.statusCode)
                            assertEquals("Exact response from our real onion service",expected,String(bytes,0,count,Charsets.UTF_8))
                        }
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
