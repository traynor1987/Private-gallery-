package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionManagerTest {
    @Test fun `opening and switching tabs retains each tab WebView handle`() {
        val manager = BrowserSessionManager(maximumTabs = 3)
        val first = manager.activeTab
        manager.attachWebView(first.id, "webview-1")

        val second = manager.newTab("https://example.test")
        manager.attachWebView(second.id, "webview-2")
        manager.select(first.id)

        assertEquals("webview-1", manager.activeTab.webViewHandle)
        manager.select(second.id)
        assertEquals("webview-2", manager.activeTab.webViewHandle)
    }

    @Test fun `closing final tab creates a clean replacement tab`() {
        val manager = BrowserSessionManager()
        val closed = manager.activeTab.id

        manager.close(closed)

        assertEquals(1, manager.tabs.size)
        assertTrue(manager.activeTab.url.isBlank())
        assertFalse(manager.activeTab.id == closed)
    }

    @Test fun `ordinary tab state changes never replace a WebView handle`() {
        val manager = BrowserSessionManager()
        val id = manager.activeTab.id
        manager.attachWebView(id, "stable")

        manager.updateNavigation(id, "https://example.test", "Example", loading = true)
        manager.setDesktopSite(id, true)
        manager.updateNavigation(id, "https://example.test", "Example", loading = false)

        assertEquals("stable", manager.activeTab.webViewHandle)
    }

    @Test fun `renderer failure discards only failed WebView and keeps reload metadata`() {
        val manager = BrowserSessionManager()
        val id = manager.activeTab.id
        manager.attachWebView(id, "dead-view")
        manager.updateNavigation(id, "https://example.test", "Example", loading = false)

        manager.rendererGone(id)

        assertEquals(null, manager.activeTab.webViewHandle)
        assertEquals("https://example.test", manager.activeTab.url)
        assertEquals(BrowserTabFailure.RENDERER_GONE, manager.activeTab.failure)
    }

    @Test fun `WebView creation failure keeps the tab and exposes a recoverable state`() {
        val manager = BrowserSessionManager()
        val id = manager.activeTab.id

        manager.webViewUnavailable(id)

        assertEquals(1, manager.tabs.size)
        assertEquals(id, manager.activeTab.id)
        assertEquals(null, manager.activeTab.webViewHandle)
        assertEquals(BrowserTabFailure.WEBVIEW_UNAVAILABLE, manager.activeTab.failure)

        manager.retryWebView(id)

        assertEquals(null, manager.activeTab.failure)
    }

    @Test fun `restored session never restores a WebView handle or loading state`() {
        val manager = BrowserSessionManager()
        val saved = BrowserTab(id = "saved", url = "https://example.test", title = "Saved", loading = true, webViewHandle = "stale")
        manager.restore(listOf(saved), "saved")

        assertEquals("saved", manager.activeTab.id)
        assertEquals(null, manager.activeTab.webViewHandle)
        assertFalse(manager.activeTab.loading)
    }

    @Test fun `tab limit evicts the oldest non-selected tab metadata`() {
        val manager = BrowserSessionManager(maximumTabs = 2)
        val first = manager.activeTab.id
        val second = manager.newTab("https://second.test")
        manager.newTab("https://third.test")

        assertEquals(2, manager.tabs.size)
        assertFalse(first in manager.tabs.map { it.id })
        assertTrue(second.id in manager.tabs.map { it.id })
    }
}
