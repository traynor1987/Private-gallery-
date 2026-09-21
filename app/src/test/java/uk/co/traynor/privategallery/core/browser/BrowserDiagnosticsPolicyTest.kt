package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDiagnosticsPolicyTest {
    @Test fun `structural diagnostic never retains a url or page text`() {
        val event = BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_NAVIGATION, "https://example.test/private?token=secret")

        assertEquals("CHILD_NAVIGATION:web", event)
        assertFalse(event.contains("example"))
        assertFalse(event.contains("secret"))
    }

    @Test fun `subresource failure is classified without retaining its url`() {
        val event = BrowserDiagnosticsPolicy.resourceError(
            url = "https://cdn.example.test/app.js?access_token=secret",
            errorCode = -2,
            isMainFrame = false,
        )

        assertEquals("RESOURCE_ERROR:subresource:host_lookup", event)
        assertFalse(event.contains("example"))
        assertFalse(event.contains("secret"))
    }

    @Test fun `http failures identify document versus subresource without retaining request data`() {
        assertEquals("HTTP_ERROR:main_frame:5xx", BrowserDiagnosticsPolicy.httpError(503, isMainFrame = true))
        assertEquals("HTTP_ERROR:subresource:4xx", BrowserDiagnosticsPolicy.httpError(404, isMainFrame = false))
    }

    @Test fun `resource loading diagnostics retain only same origin relationship`() {
        assertEquals(
            "RESOURCE_LOAD:same_origin",
            BrowserDiagnosticsPolicy.resourceLoad(
                resourceUrl = "https://example.test/assets/app.js?token=secret",
                mainDocumentUrl = "https://example.test/private?session=secret",
            ),
        )
        assertEquals(
            "RESOURCE_LOAD:other_origin",
            BrowserDiagnosticsPolicy.resourceLoad(
                resourceUrl = "https://cdn.example.test/app.js?token=secret",
                mainDocumentUrl = "https://example.test/private?session=secret",
            ),
        )
    }

    @Test fun `console diagnostic classifies an error mechanism but never retains its message`() {
        val event = BrowserDiagnosticsPolicy.consoleMessage("ERROR", "Refused to load because it violates Content Security Policy; private token=secret")

        assertEquals("JS_CONSOLE:error:csp", event)
        assertFalse(event.contains("secret"))
    }

    @Test fun `vpn gate stop diagnostic contains only a connection state category`() {
        val event = BrowserDiagnosticsPolicy.vpnGateStopLoading("CONNECTING")

        assertEquals("VPN_GATE_STOP_LOADING:connecting", event)
        assertFalse(event.contains("profile"))
    }

    @Test fun `webview attachment distinguishes retained instance from a new one without a url`() {
        assertEquals("WEBVIEW_ATTACHMENT:retained", BrowserDiagnosticsPolicy.webViewAttachment(retained = true))
        assertEquals("WEBVIEW_ATTACHMENT:new", BrowserDiagnosticsPolicy.webViewAttachment(retained = false))
    }

    @Test fun `recorder aggregates noisy resource events without losing structural sequence`() {
        val recorder = BrowserDiagnosticRecorder()

        recorder.record("MAIN_PAGE_STARTED:web")
        repeat(3) { recorder.record("RESOURCE_LOAD:other_origin") }
        recorder.record("MAIN_PAGE_FINISHED:web")

        assertEquals(
            listOf(
                "MAIN_PAGE_STARTED:web",
                "RESOURCE_LOAD:other_origin ×3",
                "MAIN_PAGE_FINISHED:web",
            ),
            recorder.snapshot(),
        )
        assertTrue(recorder.snapshot().none { it.contains("secret") })
    }

    @Test fun `recorder keeps resource outcomes visible after noisy request observations`() {
        val recorder = BrowserDiagnosticRecorder()

        repeat(40) { recorder.record("RESOURCE_LOAD:same_origin") }
        recorder.record("RESOURCE_ERROR:subresource:timeout")
        recorder.record("HTTP_ERROR:subresource:5xx")
        recorder.record("JS_CONSOLE:error:csp")

        assertEquals(
            listOf(
                "Resource requests observed: 40",
                "Resource delivery errors: 1",
                "Subresource HTTP error responses: 1",
                "JavaScript console reports: 1",
            ),
            recorder.outcomeSummary(),
        )
    }
}
