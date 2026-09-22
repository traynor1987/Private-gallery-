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

    @Test fun `desktop site changes only the tab user agent mode`() {
        assertFalse(BrowserSecurityPolicy.userAgent(BrowserUserAgentMode.MOBILE).contains("X11"))
        assertTrue(BrowserSecurityPolicy.userAgent(BrowserUserAgentMode.DESKTOP).contains("X11"))
    }
}
