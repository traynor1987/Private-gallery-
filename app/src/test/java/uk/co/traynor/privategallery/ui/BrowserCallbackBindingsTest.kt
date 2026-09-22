package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertSame
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.BrowserAcceptanceDebugConsole

class BrowserCallbackBindingsTest {
    @Test fun `returning to Browser reuses the callbacks bound to its existing WebView`() {
        val webViewIdentity = Any()
        val initiallyBound = BrowserCallbacks()

        assertSame(initiallyBound, BrowserCallbackBindings.bind(webViewIdentity, initiallyBound))
        assertSame(initiallyBound, BrowserCallbackBindings.bind(webViewIdentity, BrowserCallbacks()))
    }

    @Test fun `returning to Browser retains its acceptance trace with the bound callbacks`() {
        val webViewIdentity = Any()
        val initiallyBound = BrowserCallbacks()
        val trace = BrowserAcceptanceDebugConsole(enabled = true) { 1L }
        initiallyBound.acceptanceTrace = trace

        val rebound = BrowserCallbackBindings.bind(webViewIdentity, BrowserCallbacks())

        assertSame(trace, rebound.acceptanceTrace)
    }
}
