package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import java.util.UUID

/** Real session dispatch with offline renderers. No website, cookie or profile settings changed. */
class PrivateAppBrowserSeparationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private class OfflineView(context:Context):WebView(context) {
        val loads=mutableListOf<String>()
        var destroyed=false
        override fun loadUrl(url:String) {loads+=url}
        override fun destroy() {destroyed=true;super.destroy()}
    }
    private fun app()=PrivateAppCatalog.App(UUID.randomUUID().toString(),"App fixture","https://app.invalid/start","","standalone","",false,true,false)
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)

    @Test fun appNavigationNeverWritesStandaloneHistoryOrRestoreMetadataAndCopyGetsItsOwnTab()=main {
        val views=linkedMapOf<String,OfflineView>();val visits=mutableListOf<String>();val published=mutableListOf<BrowserSessionSnapshot>()
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {true},
            object:BrowserV2Session.Listener by NoopBrowserV2Listener {
                override fun onHistoryVisit(title:String,url:String) {visits+=url}
            },onMetadataChanged={published+=it},webViewFactory=BrowserV2WebViewFactory {context,_,id,_->OfflineView(context).also {views[id]=it}})
        try {
            val ordinary=owner.tabs.activeTab.id
            owner.onPageState(ordinary,"https://browser.invalid/","Ordinary",false,false,false)
            owner.openRetainedPrivateApp(app());val appId=owner.tabs.activeTab.id;val appView=owner.activeWebView()
            assertTrue(owner.hasPrivateAppTabs())
            owner.onPageState(appId,"https://app.invalid/second","App page",false,true,false)
            assertEquals(listOf("https://browser.invalid/"),visits)
            assertTrue("Every publication excludes known app identity, including initial load",published.all {snapshot ->snapshot.tabs.none {it.id==appId}})
            assertEquals(listOf(ordinary),owner.metadataSnapshot().tabs.map {it.id})
            assertEquals(ordinary,owner.metadataSnapshot().selectedTabId)
            val before=owner.tabs.tabs.single {it.id==appId}
            val copied=owner.openActivePrivateAppInBrowser()
            assertNotEquals(appId,copied.id);assertNull(owner.privateApp(copied.id))
            assertEquals(before,owner.tabs.tabs.single {it.id==appId})
            assertSame(appView,owner.createdPrivateAppWebViews().single().second)
            assertEquals(listOf("https://app.invalid/second"),views.getValue(copied.id).loads)
            assertFalse(owner.tabs.activeTab.canGoBack)
            owner.onPageState(copied.id,"https://app.invalid/second","Copied destination",false,false,false)
            assertEquals(listOf("https://browser.invalid/","https://app.invalid/second"),visits)
            assertEquals(copied.id,owner.metadataSnapshot().selectedTabId)
            owner.destroyAll()
            assertFalse(owner.hasPrivateAppTabs())
            assertFalse(owner.tabs.tabs.any {it.id==appId})
            assertEquals(setOf(ordinary,copied.id),owner.metadataSnapshot().tabs.map {it.id}.toSet())
        } finally {owner.destroyAll()}
    }

    @Test fun allAppSnapshotIsEmptyWithoutCreatingAnotherTabOrRenderer()=main {
        var renderers=0
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {true},NoopBrowserV2Listener,
            maximumTabs=2,webViewFactory=BrowserV2WebViewFactory {context,_,_,_->renderers++;OfflineView(context)})
        try {
            owner.openRetainedPrivateApp(app());owner.openRetainedPrivateApp(app())
            val before=renderers;val ids=owner.tabs.tabs.map {it.id}
            val snapshot=owner.metadataSnapshot()
            assertTrue(snapshot.tabs.isEmpty());assertNull(snapshot.selectedTabId)
            assertEquals(ids,owner.tabs.tabs.map {it.id});assertEquals(before,renderers)
            owner.pauseForBackground()
            assertThrows(IllegalStateException::class.java) {owner.openActivePrivateAppInBrowser()}
            assertEquals(ids,owner.tabs.tabs.map {it.id})
        } finally {owner.destroyAll()}
    }

    @Test fun nativePopupNavigationKeepsItsInitiatingAppHistoryOwner()=main {
        val visits=mutableListOf<String>()
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {true},
            object:BrowserV2Session.Listener by NoopBrowserV2Listener {
                override fun allowWindowInteraction()=true
                override fun onHistoryVisit(title:String,url:String) {visits+=url}
            },webViewFactory=BrowserV2WebViewFactory {context,_,_,_->OfflineView(context)})
        try {
            val ordinary=owner.tabs.activeTab.id;val app=app()
            owner.openRetainedPrivateApp(app);val parent=owner.tabs.activeTab.id
            assertNotNull(owner.onCreateWindow(parent,false,true))
            val child=owner.tabs.activeTab.id
            assertNotEquals(parent,child);assertEquals(app.id(),owner.privateApp(child)?.id())
            owner.onPageState(child,"https://app.invalid/popup","Popup fixture",false,false,false)
            assertTrue(visits.isEmpty())
            assertEquals(listOf(ordinary),owner.metadataSnapshot().tabs.map {it.id})
        } finally {owner.destroyAll()}
    }

    @Test fun previouslyUnclassifiedRestoredTabsArePreservedWithoutDomainBasedPurging()=main {
        val visits=mutableListOf<String>()
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {true},
            object:BrowserV2Session.Listener by NoopBrowserV2Listener {
                override fun onHistoryVisit(title:String,url:String) {visits+=url}
            },webViewFactory=BrowserV2WebViewFactory {context,_,_,_->OfflineView(context)})
        try {
            val restored=BrowserTab(url="https://app.invalid/previously-saved",title="Existing browser tab")
            owner.restoreMetadata(BrowserSessionSnapshot(listOf(restored),restored.id))
            assertFalse(owner.hasPrivateAppTabs())
            assertEquals(listOf(restored),owner.metadataSnapshot().tabs)
            owner.onPageState(restored.id,restored.url,restored.title,false,false,false)
            assertEquals(listOf(restored.url),visits)
            owner.openRetainedPrivateApp(app())
            assertEquals(listOf(restored),owner.metadataSnapshot().tabs)
        } finally {owner.destroyAll()}
    }

    @Test fun restoreAdmissionSeesColdAppBindingEvenWhenOrdinarySnapshotIsUnchanged()=main {
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {false},NoopBrowserV2Listener,
            rendererAllowed={false})
        try {
            val before=owner.metadataSnapshot()
            assertFalse(owner.hasPrivateAppTabs())
            owner.openPrivateApp(app())
            assertEquals(before,owner.metadataSnapshot())
            assertTrue(owner.hasPrivateAppTabs())
            assertTrue(owner.createdPrivateAppWebViews().isEmpty())
        } finally {owner.destroyAll()}
    }
}
