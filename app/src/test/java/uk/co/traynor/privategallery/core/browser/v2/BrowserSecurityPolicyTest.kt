package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSecurityPolicyTest {
    @Test fun `default configuration supports modern web platform without unsafe file access`() {
        val configuration = BrowserSecurityPolicy.defaultConfiguration()

        assertTrue(configuration.javaScript)
        assertTrue(configuration.domStorage)
        assertTrue(configuration.firstPartyCookies)
        assertFalse(configuration.thirdPartyCookies)
        assertFalse(configuration.fileAccess)
        assertFalse(configuration.contentAccess)
        assertFalse(configuration.mixedContent)
        assertFalse(configuration.javascriptBridge)
    }

    @Test fun `only HTTP and HTTPS are navigable and TLS always cancels`() {
        assertTrue(BrowserSecurityPolicy.allowsNavigation("https://example.test"))
        assertTrue(BrowserSecurityPolicy.allowsNavigation("http://example.test"))
        assertFalse(BrowserSecurityPolicy.allowsNavigation("intent://anything"))
        assertFalse(BrowserSecurityPolicy.allowsNavigation("file:///data/private"))
        assertEquals(BrowserTlsDecision.CANCEL, BrowserSecurityPolicy.tlsDecision())
    }

    @Test fun `desktop override preserves engine versions and mobile restores provider identity`() {
        val provider = "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/152.0.0.0 Mobile Safari/537.36"
        val desktop = BrowserSecurityPolicy.userAgent(BrowserUserAgentMode.DESKTOP, provider)
        assertTrue(desktop.contains("Chrome/152.0.0.0"))
        assertTrue(desktop.contains("Safari/537.36"))
        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("Android"))
        assertEquals(provider, BrowserSecurityPolicy.userAgent(BrowserUserAgentMode.MOBILE, provider))
    }
}
