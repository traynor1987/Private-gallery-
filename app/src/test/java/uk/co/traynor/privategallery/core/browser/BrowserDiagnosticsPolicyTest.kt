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
}
