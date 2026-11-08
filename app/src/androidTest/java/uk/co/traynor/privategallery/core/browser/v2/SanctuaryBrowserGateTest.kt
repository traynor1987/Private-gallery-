package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryWhatsApp
import java.util.UUID

class SanctuaryBrowserGateTest {
    private class OfflineView(context: Context) : WebView(context) {
        val loads = mutableListOf<String>()
        override fun loadUrl(url: String) { loads += url }
    }

    @Test fun directAndRedirectRequestsBlockWhileRetainedViewsStayIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val whatsapp = "https://web.whatsapp.com/"
            val safe = "https://approved.invalid/"
            var blocking = false
            val owner = BrowserV2Session(
                instrumentation.targetContext, BrowserVpnGate { true }, NoopBrowserV2Listener,
                webViewFactory = BrowserV2WebViewFactory { context, _, _, _ -> OfflineView(context) },
                destinationRestricted = { blocking && SanctuaryWhatsApp.isRelatedUrl(it) },
            )
            try {
                val app = PrivateAppCatalog.App(UUID.randomUUID().toString(), "Renamed app", whatsapp, "", "standalone", "", false, true, false)
                owner.openRetainedPrivateApp(app)
                val saved = owner.tabs.activeTab.id
                val retained = owner.activeWebView() as OfflineView
                val browser = owner.newTab(safe).id
                val view = owner.activeWebView() as OfflineView
                blocking = true
                owner.enforceDestinationPolicy()
                owner.navigateActive(whatsapp)
                assertEquals(listOf(safe), view.loads)
                assertTrue(owner.onNavigationRequest(browser, whatsapp, true, true))
                assertTrue(owner.interceptRestrictedDestination(whatsapp))
                assertFalse(owner.interceptRestrictedDestination(safe))
                owner.selectFromNovaHome(saved)
                assertEquals(browser, owner.tabs.activeTab.id)
                assertThrows(IllegalStateException::class.java) { owner.openRetainedPrivateApp(app) }
                assertSame(retained, owner.createdPrivateAppWebViews().single().second)
                blocking = false
                owner.enforceDestinationPolicy()
                owner.selectFromNovaHome(saved)
                assertSame(retained, owner.activeWebView())
                assertEquals(listOf(whatsapp), retained.loads)
            } finally {
                owner.destroyAll()
            }
        }
    }

    @Test fun coldRestoreAndUnexpectedBlockedDocumentNeverAttachWhileRestricted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var blocking = true
            val whatsapp = "https://web.whatsapp.com/"
            val owner = BrowserV2Session(
                instrumentation.targetContext, BrowserVpnGate { true }, NoopBrowserV2Listener,
                webViewFactory = BrowserV2WebViewFactory { context, _, _, _ -> OfflineView(context) },
                destinationRestricted = { blocking && SanctuaryWhatsApp.isRelatedUrl(it) },
            )
            try {
                val saved = BrowserTab(url = whatsapp)
                owner.restoreMetadata(BrowserSessionSnapshot(listOf(saved), saved.id))
                assertNull(owner.activeWebViewOrNull())
                assertEquals(whatsapp, owner.metadataSnapshot().tabs.single().url)
                val open = owner.newTab("https://approved.invalid/").id
                owner.onPageState(open, whatsapp, "Blocked page", true, false, false)
                assertNull(owner.activeWebViewOrNull())
                assertNotEquals(whatsapp, owner.tabs.activeTab.url)
                blocking = false
                owner.enforceDestinationPolicy()
                owner.select(saved.id)
                assertEquals(listOf(whatsapp), (owner.activeWebView() as OfflineView).loads)
            } finally {
                owner.destroyAll()
            }
        }
    }
}
