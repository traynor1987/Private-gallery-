package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserExternalNavigationPolicyTest {
    @Test fun `only explicit app-link schemes are candidates for confirmation`() {
        assertTrue(BrowserExternalNavigationPolicy.isCandidate("intent://example/path#Intent;scheme=example;end"))
        assertTrue(BrowserExternalNavigationPolicy.isCandidate("mailto:person@example.test"))
        assertTrue(BrowserExternalNavigationPolicy.isCandidate("tel:+12025550123"))
        assertFalse(BrowserExternalNavigationPolicy.isCandidate("https://example.test"))
        assertFalse(BrowserExternalNavigationPolicy.isCandidate("javascript:alert(1)"))
        assertFalse(BrowserExternalNavigationPolicy.isCandidate("file:///private"))
        assertFalse(BrowserExternalNavigationPolicy.isCandidate("content://private/item"))
        assertFalse(BrowserExternalNavigationPolicy.isCandidate("unknown:thing"))
    }
}
