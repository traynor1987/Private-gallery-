package uk.co.traynor.privategallery.core.media

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.v2.*

class BrowserCallbackLifetimeTest {
    @Test fun lateCallbacksAfterPopupCloseAndEvictionCannotCrashOrResurrectTab() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val session = BrowserV2Session(activity, BrowserVpnGate { true }, NoopBrowserV2Listener, maximumTabs = 2)
            val closed = session.newTab().id
            session.close(closed)
            val evicted = session.tabs.activeTab.id
            session.newTab(); session.newTab()
            val selected = session.tabs.activeTab.id
            for (id in listOf(closed, evicted)) {
                session.onTitle(id, "late", false, false)
                session.onPageState(id, "https://example.invalid/", "late", false, false, false)
                session.onRendererGone(id)
                assertNull(session.onCreateWindow(id, false, true))
            }
            assertEquals(selected, session.tabs.activeTab.id)
            assertEquals(2, session.tabs.tabs.size)
            session.destroyAll()
        } }
    }

    @Test fun vpnLossDestroysLiveWebViewAndReconnectCanCreateGatedReplacement() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            var allowed = true
            val session = BrowserV2Session(activity, BrowserVpnGate { allowed }, NoopBrowserV2Listener)
            val view = session.activeWebView()
            val titleClient = view.webChromeClient!!
            val pageClient = view.webViewClient
            allowed = false
            session.enforceNetworkPolicy()
            assertNull(session.tabs.activeTab.webViewHandle)
            // The provider can already have queued callbacks from a destroyed instance.
            titleClient.onReceivedTitle(view, "stale")
            pageClient.onPageFinished(view, "https://example.invalid/late")
            assertNotEquals("stale", session.tabs.activeTab.title)
            allowed = true
            session.resumeForeground()
            assertNotSame(view, session.activeWebView())
            assertTrue(session.mediaNetworkingAllowed())
            session.pauseForBackground()
            assertFalse(session.mediaNetworkingAllowed())
            session.destroyAll()
        } }
    }

    @Test fun fullscreenCallbacksAreOwnedAndCompletedExactlyOnce() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            var allowed = true
            var shown = 0
            var hidden = 0
            var completed = 0
            val session = BrowserV2Session(activity, BrowserVpnGate { allowed }, NoopBrowserV2Listener)
            session.bindListener(object : BrowserV2Session.Listener by NoopBrowserV2Listener {
                override fun onFullscreen(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback) { shown++ }
                override fun onExitFullscreen() { hidden++ }
            })
            val id = session.tabs.activeTab.id
            val callback = android.webkit.WebChromeClient.CustomViewCallback { completed++ }
            session.onShowCustomView(id, android.view.View(activity), callback)
            session.exitFullscreen(); session.onHideCustomView(id); session.exitFullscreen()
            assertEquals(1, shown); assertEquals(1, hidden); assertEquals(1, completed)
            allowed = false
            session.onShowCustomView(id, android.view.View(activity), callback)
            assertEquals(1, shown); assertEquals(2, completed)
            session.destroyAll()
        } }
    }
}
