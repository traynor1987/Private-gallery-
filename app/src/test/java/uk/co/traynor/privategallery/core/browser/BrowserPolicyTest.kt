package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPolicyTest {
    @Test fun `host-like address becomes https url`() {
        assertEquals("https://example.com", BrowserAddressPolicy.destinationFor("example.com", BrowserSearchEngine.GOOGLE).url)
    }

    @Test fun `https url is retained`() {
        assertEquals("https://example.com/a?q=b", BrowserAddressPolicy.destinationFor("https://example.com/a?q=b", BrowserSearchEngine.GOOGLE).url)
    }

    @Test fun `ordinary words become a search query`() {
        assertEquals(
            "https://www.google.com/search?q=jenna+ortega",
            BrowserAddressPolicy.destinationFor("jenna ortega", BrowserSearchEngine.GOOGLE).url,
        )
    }

    @Test fun `only http and https navigation is accepted`() {
        assertTrue(BrowserNavigationPolicy.isWebUrl("https://example.com"))
        assertTrue(BrowserNavigationPolicy.isWebUrl("http://example.com"))
        assertFalse(BrowserNavigationPolicy.isWebUrl("file:///data/data/uk.co.traynor.privategallery/files/vault"))
        assertFalse(BrowserNavigationPolicy.isWebUrl("intent://anything"))
        assertFalse(BrowserNavigationPolicy.isWebUrl("mailto:test@example.com"))
    }

    @Test fun `back exits fullscreen before browser history`() {
        assertEquals(BrowserBackAction.EXIT_FULLSCREEN, BrowserNavigationPolicy.backAction(fullscreen = true, canGoBack = true))
        assertEquals(BrowserBackAction.GO_BACK, BrowserNavigationPolicy.backAction(fullscreen = false, canGoBack = true))
        assertEquals(BrowserBackAction.FALL_THROUGH, BrowserNavigationPolicy.backAction(fullscreen = false, canGoBack = false))
    }

    @Test fun `download interception is intentionally not an ordinary public download`() {
        assertEquals(BrowserDownloadAction.SHOW_NOT_SUPPORTED, BrowserNavigationPolicy.downloadAction())
    }

    @Test fun `lock clean up follows explicit preference`() {
        assertTrue(BrowserNavigationPolicy.clearDataOnLock(true))
        assertFalse(BrowserNavigationPolicy.clearDataOnLock(false))
    }

    @Test fun `tls errors are never bypassed`() {
        assertEquals(BrowserTlsAction.CANCEL, BrowserNavigationPolicy.tlsErrorAction())
    }

    @Test fun `browser exposes no Vault bridge or local file access`() {
        assertFalse(BrowserWebSecurityPolicy.javaScriptBridgeEnabled)
        assertFalse(BrowserWebSecurityPolicy.fileAccessEnabled)
        assertFalse(BrowserWebSecurityPolicy.contentAccessEnabled)
        assertFalse(BrowserWebSecurityPolicy.multipleWindowsEnabled)
    }

    @Test fun `browser chrome is present before WebView is created`() {
        val state = BrowserScreenState.initial()

        assertTrue(state.showChrome)
        assertTrue(state.showStartSurface)
        assertFalse(state.showError)
    }

    @Test fun `webview initialization failure retains chrome and shows an error`() {
        val state = BrowserScreenState.initializationFailed()

        assertTrue(state.showChrome)
        assertFalse(state.showStartSurface)
        assertTrue(state.showError)
    }
}
