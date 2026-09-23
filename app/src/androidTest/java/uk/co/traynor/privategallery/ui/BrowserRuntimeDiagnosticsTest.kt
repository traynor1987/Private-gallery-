package uk.co.traynor.privategallery.ui

import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.v2.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserRuntimeDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun snapshotsDistinguishDomModalAndRuntimeFailureWithoutPrivatePayloads() {
        lateinit var view: WebView
        lateinit var probe: BrowserRuntimeProbe
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        compose.runOnIdle {
            val session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
            view = session.activeWebView()
            compose.activity.setContentView(view)
            probe = BrowserRuntimeProbe(view, { true }) { event, details -> events += event to details }
            view.loadDataWithBaseURL("https://fixture.invalid/", "<body><p>PRIVATE_SENTINEL</p><dialog>PRIVATE_DIALOG</dialog><iframe srcdoc='<p>private frame</p>'></iframe><canvas></canvas><script>window.fixtureReady=true;function rejectFixture(){Promise.reject('PRIVATE_REJECTION')}</script></body>", "text/html", "UTF-8", null)
        }
        try {
            compose.waitUntil(15_000) { js(view, "window.fixtureReady===true && document.readyState==='complete'") == "true" }
            snapshot(probe)
            js(view, "document.querySelector('dialog').showModal();rejectFixture();true")
            compose.waitUntil(10_000) {
                snapshot(probe)
                events.any { it.first == "RUNTIME_SNAPSHOT" && it.second["promise_rejections"] == "1" }
            }
            val report = events.toString()
            assertFalse(report.contains("PRIVATE"))
            assertFalse(report.contains("fixture.invalid"))
            assertTrue(events.any { it.first == "DOM_MODAL_COUNT_CHANGE" && it.second["count"] == "1" })
            assertTrue(events.any { it.first == "IFRAME_COUNT_CHANGE" && it.second["count"] == "1" })
            assertTrue(events.any { it.first == "RUNTIME_SNAPSHOT" && it.second["promise_rejections"] == "1" })
            assertTrue(events.any { it.first == "RUNTIME_SNAPSHOT" && (it.second["visible_elements"]?.toInt() ?: 0) > 0 })
        } finally { compose.runOnIdle { probe.dispose(); (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy() } }
    }

    @Test fun structuralProbeFindsAncestorOpacityClippingAndCoverWithoutReadingContent() {
        lateinit var view: WebView
        lateinit var probe: BrowserRuntimeProbe
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        compose.runOnIdle {
            view = WebView(compose.activity)
            view.settings.javaScriptEnabled = true
            compose.activity.setContentView(view)
            probe = BrowserRuntimeProbe(view, { true }) { event, details -> events += event to details }
            view.loadDataWithBaseURL("https://fixture.invalid/", """<meta name="viewport" content="width=device-width,initial-scale=1"><body style="margin:0"><main style="height:100vh;background:white"><button>PRIVATE_TEXT</button></main><section style="opacity:0"><button>PRIVATE_HIDDEN</button></section><section style="height:1px;overflow:hidden"><div style="margin-top:40px;height:50px">PRIVATE_CLIPPED</div></section><div id="cover" style="position:fixed;inset:0;background:blue;z-index:9999"></div><script>window.fixtureReady=true</script></body>""", "text/html", "UTF-8", null)
        }
        try {
            compose.waitUntil(15_000) { js(view, "window.fixtureReady===true && document.readyState==='complete'") == "true" }
            snapshot(probe)
            val before = events.last { it.first == "RUNTIME_SNAPSHOT" }.second
            assertTrue((before["ancestor_hidden"]?.toInt() ?: 0) > 0)
            assertTrue((before["clipped_elements"]?.toInt() ?: 0) > 0)
            assertTrue((before["covering_layers"]?.toInt() ?: 0) > 0)
            assertTrue((before["sample_obscured"]?.toInt() ?: 0) > 0)
            js(view, "document.getElementById('cover').remove();true")
            snapshot(probe)
            val after = events.last { it.first == "RUNTIME_SNAPSHOT" }.second
            assertEquals("0", after["covering_layers"])
            assertTrue((after["sample_unobscured"]?.toInt() ?: 0) > (before["sample_unobscured"]?.toInt() ?: 0))
            assertFalse(events.toString().contains("PRIVATE"))
            assertFalse(events.toString().contains("fixture.invalid"))
        } finally { compose.runOnIdle { probe.dispose(); (view.parent as? android.view.ViewGroup)?.removeView(view); view.destroy() } }
    }

    @Test fun concurrentAndDisabledSnapshotsAlwaysCompleteWithoutDuplicateSampling() {
        lateinit var view: WebView
        lateinit var probe: BrowserRuntimeProbe
        var enabled = true
        var samples = 0
        val completed = CountDownLatch(2)
        compose.runOnIdle {
            view = WebView(compose.activity)
            view.settings.javaScriptEnabled = true
            probe = BrowserRuntimeProbe(view, { enabled }) { event, _ -> if (event == "RUNTIME_SNAPSHOT") samples++ }
            probe.capture("AUTO") { completed.countDown() }
            probe.capture("OWNER") { completed.countDown() }
        }
        try {
            assertTrue(completed.await(10, TimeUnit.SECONDS))
            assertEquals(1, samples)
            var disabledCompleted = false
            compose.runOnIdle { enabled = false; probe.capture("OWNER") { disabledCompleted = true } }
            assertTrue(disabledCompleted)
            assertEquals(1, samples)
        } finally { compose.runOnIdle { probe.dispose(); view.destroy() } }
    }

    @Test fun permissionCompletionFiltersUnknownResourcesAndIgnoresLateResults() {
        val grants = mutableListOf<List<String>>()
        var denied = 0
        val request = object : PermissionRequest() {
            override fun getOrigin() = Uri.parse("https://fixture.invalid")
            override fun getResources() = arrayOf(RESOURCE_VIDEO_CAPTURE, "future.private.capability")
            override fun grant(resources: Array<out String>) { grants += resources.toList() }
            override fun deny() { denied++ }
        }
        val results = mutableListOf<String>()
        val wrapped = BrowserPermissionRequest(request) { decision, _ -> results += decision }
        wrapped.grant(request.resources)
        wrapped.deny()
        assertEquals(listOf(listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)), grants)
        assertEquals(0, denied)
        assertEquals(listOf("granted"), results)
        val canceled = BrowserPermissionRequest(request) { decision, _ -> results += decision }
        canceled.canceled()
        canceled.grant(request.resources)
        assertEquals(1, grants.size)
        assertEquals(listOf("granted", "canceled"), results)
    }

    @Test fun clearingCurrentTraceRetainsClearlySeparatedPreviousFatalEvidence() {
        val file = java.io.File(compose.activity.filesDir, "browser-v2-fatal-report.txt")
        val prior = file.takeIf { it.exists() }?.readBytes()
        try {
            file.writeText("Historical fixture exception")
            val session = BrowserV2Session(compose.activity, BrowserVpnGate { false }, NoopBrowserV2Listener)
            session.clearAcceptanceReport()
            val report = session.acceptanceReport()
            assertTrue(report.indexOf("CURRENT SESSION") < report.indexOf("PREVIOUS PROCESS FATAL REPORT"))
            assertTrue(report.contains("HISTORICAL EVIDENCE"))
            assertTrue(report.contains("Historical fixture exception"))
            assertTrue(file.exists())
        } finally { if (prior == null) file.delete() else file.writeBytes(prior) }
    }

    private fun snapshot(probe: BrowserRuntimeProbe) {
        val done = CountDownLatch(1)
        compose.runOnIdle { probe.capture("TEST") { done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
    }
    private fun js(view: WebView, code: String): String {
        val done = CountDownLatch(1); var value = ""
        compose.runOnIdle { view.evaluateJavascript(code) { value = it; done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS)); return value
    }
}
