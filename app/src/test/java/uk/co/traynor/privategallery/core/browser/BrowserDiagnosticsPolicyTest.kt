package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrowserDiagnosticsPolicyTest {
    @Test fun `structural diagnostic never retains a url or page text`() {
        val event = BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_NAVIGATION, "https://example.test/private?token=secret")

        assertEquals("CHILD_NAVIGATION:web", event)
        assertFalse(event.contains("example"))
        assertFalse(event.contains("secret"))
    }

    @Test fun `resource failure is classified without retaining its url`() {
        val event = BrowserDiagnosticsPolicy.resourceError(
            url = "https://cdn.example.test/app.js?access_token=secret",
            errorCode = -2,
        )

        assertEquals("RESOURCE_ERROR:host_lookup", event)
        assertFalse(event.contains("example"))
        assertFalse(event.contains("secret"))
    }

    @Test fun `http failures reveal only the status class`() {
        assertEquals("HTTP_ERROR:5xx", BrowserDiagnosticsPolicy.httpError(503))
    }
}
