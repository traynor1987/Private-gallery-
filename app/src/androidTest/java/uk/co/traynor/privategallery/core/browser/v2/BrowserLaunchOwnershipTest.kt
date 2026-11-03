package uk.co.traynor.privategallery.core.browser.v2

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog

/** Production session ownership; renderer disabled so these tests grant no networking authority. */
class BrowserLaunchOwnershipTest {
    private fun app(name: String) = PrivateAppCatalog.App(
        UUID.randomUUID().toString(), name, "https://example.test/$name/",
        "https://example.test/$name/", "standalone", "", false, false, false,
    )

    @Test fun browserAndSavedAppsRestoreTheirOwnSelectedTab() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val session = BrowserV2Session(activity, BrowserVpnGate { false }, NoopBrowserV2Listener, rendererAllowed = { false })
            try {
                val browser = session.tabs.activeTab.id
                val firstApp = app("first")
                val secondApp = app("second")
                session.openPrivateApp(firstApp)
                val first = session.tabs.activeTab.id
                session.onPageState(first, "https://example.test/first/inside", "App page", false, true, false)
                session.openPrivateApp(secondApp)
                val second = session.tabs.activeTab.id
                session.pauseForBackground()
                session.openOrdinaryBrowser()
                assertEquals(browser, session.tabs.activeTab.id)
                assertNull(session.privateApp(browser))
                session.onWindowFocus(first)
                assertEquals(browser, session.tabs.activeTab.id)
                session.openPrivateApp(firstApp)
                assertEquals(first, session.tabs.activeTab.id)
                assertTrue(session.tabs.activeTab.canGoBack)
                assertEquals("https://example.test/first/inside", session.tabs.activeTab.url)
                session.onWindowFocus(second)
                assertEquals(first, session.tabs.activeTab.id)
                session.openPrivateApp(secondApp)
                assertEquals(second, session.tabs.activeTab.id)
            } finally { session.destroyAll() }
        } }
    }

    @Test fun encryptedBrowserRestoreCannotOrphanSavedAppMetadata() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val publications = mutableListOf<BrowserSessionSnapshot>()
            val session = BrowserV2Session(activity, BrowserVpnGate { false }, NoopBrowserV2Listener,
                onMetadataChanged = { publications += it }, rendererAllowed = { false })
            val restored = BrowserV2Session(activity, BrowserVpnGate { false }, NoopBrowserV2Listener, rendererAllowed = { false })
            val directory = java.io.File(activity.cacheDir, "ownership-${UUID.randomUUID()}")
            val key = ByteArray(32) { 11 }
            try {
                val browser = session.tabs.activeTab.id
                session.onPageState(browser, "https://example.test/browser", "Browser", false, true, false)
                session.openPrivateApp(app("saved"))
                val saved = session.tabs.activeTab.id
                session.onPageState(saved, "https://example.test/saved/private", "Saved app", false, true, false)
                assertTrue(publications.all { snapshot -> snapshot.tabs.none { it.id == saved } })
                assertEquals(listOf(browser), session.ordinaryBrowserTabs().map { it.id })
                val store = EncryptedBrowserSessionStore(directory, key)
                store.save(session.metadataSnapshot())
                session.destroyAll()
                assertTrue(session.tabs.tabs.none { it.id == saved })
                assertTrue(restored.restoreMetadataIfUnchanged(checkNotNull(store.load()), restored.runtimeMutationRevision()))
                restored.openOrdinaryBrowser()
                assertEquals(browser, restored.tabs.activeTab.id)
                assertEquals("https://example.test/browser", restored.tabs.activeTab.url)
                assertEquals(listOf(browser), restored.tabs.tabs.map { it.id })
            } finally {
                session.destroyAll(); restored.destroyAll(); key.fill(0); directory.deleteRecursively()
            }
        } }
    }

    @Test fun deferredOrdinaryRestoreCannotReplaceLiveAppContexts() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            // Real renderer identities, with remote navigation blocked throughout this test.
            val session = BrowserV2Session(activity, BrowserVpnGate { false }, NoopBrowserV2Listener)
            try {
                val browser = session.tabs.activeTab.id
                val browserView = session.activeWebView()
                val ordinarySnapshot = session.metadataSnapshot()
                val pendingRevision = session.runtimeMutationRevision()
                var accepted: Boolean? = null
                val pendingRestore = Runnable {
                    accepted = session.restoreMetadataIfUnchanged(ordinarySnapshot, pendingRevision)
                }

                val firstApp = app("first")
                val secondApp = app("second")
                session.openPrivateApp(firstApp)
                val first = session.tabs.activeTab.id
                val firstView = session.activeWebView()
                session.onPageState(first, "https://example.test/first/inside", "Inside", false, true, false)
                session.openPrivateApp(secondApp)
                val second = session.tabs.activeTab.id
                val secondView = session.activeWebView()
                assertEquals(ordinarySnapshot, session.metadataSnapshot())

                pendingRestore.run()

                assertEquals(false, accepted)
                assertEquals(second, session.tabs.activeTab.id)
                assertEquals(firstApp.id(), session.privateApp(first)?.id())
                assertEquals(secondApp.id(), session.privateApp(second)?.id())
                assertSame(firstView, session.webView(first))
                assertSame(secondView, session.webView(second))
                assertSame(browserView, session.webView(browser))
                assertTrue(session.tabs.tabs.single { it.id == first }.canGoBack)

                // An already-retained app selection must also invalidate a later read.
                val selectionRevision = session.runtimeMutationRevision()
                session.openPrivateApp(firstApp)
                assertFalse(session.restoreMetadataIfUnchanged(ordinarySnapshot, selectionRevision))
                assertEquals(first, session.tabs.activeTab.id)
                assertSame(firstView, session.activeWebView())

                val navigationRevision = session.runtimeMutationRevision()
                session.onPageState(first, "https://example.test/first/next", "Next", false, true, false)
                assertFalse(session.restoreMetadataIfUnchanged(ordinarySnapshot, navigationRevision))
                assertEquals(firstApp.id(), session.privateApp(first)?.id())
                assertSame(firstView, session.activeWebView())
            } finally { session.destroyAll() }
        } }
    }

    @Test fun restoreCapturedAfterAppExistsCannotReplaceItsLiveContext() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val session = BrowserV2Session(activity, BrowserVpnGate { false }, NoopBrowserV2Listener)
            try {
                val ordinarySnapshot = session.metadataSnapshot()
                val saved = app("already-open")
                session.openPrivateApp(saved)
                val tab = session.tabs.activeTab.id
                val view = session.activeWebView()
                val capturedAfterAppOpened = session.runtimeMutationRevision()
                assertFalse(session.restoreMetadataIfUnchanged(ordinarySnapshot, capturedAfterAppOpened))
                assertEquals(tab, session.tabs.activeTab.id)
                assertEquals(saved.id(), session.privateApp(tab)?.id())
                assertSame(view, session.activeWebView())
                // No saved disk session may still allow independently-authorized bookmark display.
                assertTrue(session.restoreMetadataIfUnchanged(null, capturedAfterAppOpened))
                assertEquals(tab, session.tabs.activeTab.id)
                assertSame(view, session.activeWebView())
            } finally { session.destroyAll() }
        } }
    }

    @Test fun explicitLegacyChoiceCreatesOnlyOneFreshOrdinaryContext() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val session = BrowserV2Session(activity, BrowserVpnGate { false }, NoopBrowserV2Listener, rendererAllowed = { false })
            try {
                val selected = BrowserTab(id = "ambiguous-old-app", url = "https://example.test/shared", title = "Recovered")
                val original = session.tabs.activeTab.id
                session.openRecoveredOrdinaryTab(selected)
                val recovered = session.tabs.activeTab
                assertNotEquals(selected.id, recovered.id)
                assertNotEquals(original, recovered.id)
                assertEquals(selected.url, recovered.url)
                assertNull(session.privateApp(recovered.id))
                assertEquals(listOf(original, recovered.id), session.metadataSnapshot().tabs.map { it.id })
            } finally { session.destroyAll() }
        } }
    }
}
