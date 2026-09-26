package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test

class BrowserContentBlockerTest {
    @Test fun blocks_known_third_party_hosts_without_matching_similar_names() {
        val blocker = BrowserContentBlocker()
        assertTrue(blocker.shouldBlock("https://example.com/", "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", false))
        assertTrue(blocker.shouldBlock("https://example.com/", "https://sub.doubleclick.net/ad", false))
        assertFalse(blocker.shouldBlock("https://example.com/", "https://doubleclick.net.evil.example/ad", false))
        assertFalse(blocker.shouldBlock("https://doubleclick.net/", "https://doubleclick.net/script.js", false))
        assertFalse(blocker.shouldBlock("https://example.com/", "https://pagead2.googlesyndication.com/ad", true))
    }

    @Test fun site_bypass_and_global_switch_restore_resources_and_popups() {
        val blocker = BrowserContentBlocker()
        blocker.setSiteBypassed("https://www.example.com/story", true)
        assertFalse(blocker.shouldBlock("https://example.com/other", "https://doubleclick.net/ad", false))
        assertFalse(blocker.shouldBlockPopup("https://example.com/", false))
        assertTrue(blocker.shouldBlock("https://another.example/", "https://doubleclick.net/ad", false))
        assertTrue(blocker.shouldBlockPopup("https://another.example/", false))
        assertFalse(blocker.shouldBlockPopup("https://another.example/", true))
        blocker.enabled = false
        assertFalse(blocker.shouldBlock("https://another.example/", "https://doubleclick.net/ad", false))
        assertFalse(blocker.shouldBlockPopup("https://another.example/", false))
    }

    @Test fun invalid_and_non_web_resources_are_never_intercepted() {
        val blocker = BrowserContentBlocker()
        assertFalse(blocker.shouldBlock("about:blank", "https://doubleclick.net/ad", false))
        assertFalse(blocker.shouldBlock("https://example.com/", "blob:https://doubleclick.net/uuid", false))
        assertFalse(blocker.shouldBlock("https://example.com/", "https://doubleclick.net.evil.test/ad", false))
        assertTrue(blocker.shouldBlockPopup("about:blank", false))
    }
}
