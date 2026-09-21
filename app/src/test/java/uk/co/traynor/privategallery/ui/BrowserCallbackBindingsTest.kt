package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertSame
import org.junit.Test

class BrowserCallbackBindingsTest {
    @Test fun `returning to Browser reuses the callbacks bound to its existing WebView`() {
        val webViewIdentity = Any()
        val initiallyBound = BrowserCallbacks()

        assertSame(initiallyBound, BrowserCallbackBindings.bind(webViewIdentity, initiallyBound))
        assertSame(initiallyBound, BrowserCallbackBindings.bind(webViewIdentity, BrowserCallbacks()))
    }
}
