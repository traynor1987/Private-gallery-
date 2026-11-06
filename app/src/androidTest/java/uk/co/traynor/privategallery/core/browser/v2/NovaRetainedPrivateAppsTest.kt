package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCommands
import uk.co.traynor.privategallery.core.privateapps.PrivateAppsController
import uk.co.traynor.privategallery.core.privateapps.PrivateAppStore

/** Real API37 WebViews, with native lifecycle/JS observation and no external page requests. */
@RunWith(AndroidJUnit4::class)
class NovaRetainedPrivateAppsTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun app(name:String,authentication:Boolean=false)=PrivateAppCatalog.App(UUID.randomUUID().toString(),name,
        "https://nova-retention.invalid/${name.lowercase()}","","standalone","",false,false,authentication)
    internal class RecordingView(context:Context):WebView(context) {
        var nativePauses=0
        var pauseScripts=0
        var destructions=0
        val requestedUrls=mutableListOf<String>()
        override fun onPause(){nativePauses++;super.onPause()}
        override fun evaluateJavascript(script:String,resultCallback:ValueCallback<String>?) {
            if(script==BrowserVideoAssistant.PAUSE)pauseScripts++
            super.evaluateJavascript(script,resultCallback)
        }
        override fun loadUrl(url:String){requestedUrls+=url} // All test addresses remain offline.
        override fun destroy(){destructions++;super.destroy()}
        fun reset(){nativePauses=0;pauseScripts=0}
    }
    private class Request:PermissionRequest() {
        var denied=false;var granted=false
        override fun getOrigin()=Uri.parse("https://nova-retention.invalid")
        override fun getResources()=arrayOf(RESOURCE_AUDIO_CAPTURE)
        override fun deny(){denied=true}
        override fun grant(resources:Array<String>){granted=true}
    }
    private fun session(views:MutableMap<String,RecordingView>,maximum:Int=8,network:()->Boolean={true},renderer:()->Boolean={true})=
        BrowserV2Session(instrumentation.targetContext,BrowserVpnGate(network),NoopBrowserV2Listener,
            maximumTabs=maximum,rendererAllowed=renderer,
            webViewFactory=BrowserV2WebViewFactory {context,_,id,_->RecordingView(context).also {views[id]=it}})
    private fun main(test:()->Unit)=instrumentation.runOnMainSync {assertEquals(37,android.os.Build.VERSION.SDK_INT);test()}

    @Test fun defaultBrowserEntryPointsStillPauseOutgoingTabs()=main {
        val views=linkedMapOf<String,RecordingView>();val owner=session(views)
        try {
            val ordinary=owner.tabs.activeTab.id;owner.activeWebView();views.getValue(ordinary).reset()
            owner.openPrivateApp(app("Music"));val music=owner.tabs.activeTab.id
            assertEquals(1,views.getValue(ordinary).nativePauses);assertEquals(1,views.getValue(ordinary).pauseScripts)
            views.getValue(music).reset()
            owner.openPrivateApp(app("Chat"));val chat=owner.tabs.activeTab.id
            assertEquals("Legacy app launch interrupts outgoing media (the old behavior)",1,views.getValue(music).pauseScripts)
            assertEquals(1,views.getValue(music).nativePauses)
            owner.select(music)
            assertEquals(1,views.getValue(chat).pauseScripts);assertEquals(1,views.getValue(chat).nativePauses)
            views.getValue(music).reset();owner.newTab()
            assertEquals(1,views.getValue(music).pauseScripts);assertEquals(1,views.getValue(music).nativePauses)
        } finally {owner.destroyAll()}
    }

    @Test fun novaAppAndStandaloneSwitchesRetainExactPrivateAppWebViewsWithoutPauseInjection()=main {
        val views=linkedMapOf<String,RecordingView>();val owner=session(views)
        val music=app("Music");val chat=app("Chat")
        try {
            val ordinary=owner.tabs.activeTab.id;owner.activeWebView()
            owner.openRetainedPrivateApp(music);val musicId=owner.tabs.activeTab.id;val musicView=views.getValue(musicId);musicView.reset()
            owner.openRetainedPrivateApp(chat);val chatId=owner.tabs.activeTab.id;val chatView=views.getValue(chatId);chatView.reset()
            owner.openRetainedPrivateApp(music)
            assertSame(musicView,owner.activeWebView());assertEquals(3,owner.tabs.tabs.size)
            owner.selectFromNovaHome(ordinary)
            assertNull(owner.privateApp(owner.tabs.activeTab.id));assertSame(views.getValue(ordinary),owner.activeWebView())
            assertEquals(0,musicView.nativePauses);assertEquals(0,musicView.pauseScripts)
            assertEquals(0,chatView.nativePauses);assertEquals(0,chatView.pauseScripts)
            assertEquals(listOf(music.startUrl()),musicView.requestedUrls)
            assertEquals(listOf(chat.startUrl()),chatView.requestedUrls)
            owner.openRetainedPrivateApp(music);musicView.reset();owner.newTabFromNovaHome()
            assertNull(owner.privateApp(owner.tabs.activeTab.id))
            assertEquals(0,musicView.nativePauses);assertEquals(0,musicView.pauseScripts)
            assertEquals(0,musicView.destructions)
        } finally {owner.destroyAll()}
    }

    @Test fun novaEntryStillPausesOrdinaryBrowserAndUnknownSelectionDoesNotMutate()=main {
        val views=linkedMapOf<String,RecordingView>();val owner=session(views)
        try {
            val ordinary=owner.tabs.activeTab.id;val view=owner.activeWebView() as RecordingView;view.reset()
            owner.selectFromNovaHome("missing-tab")
            assertEquals(ordinary,owner.tabs.activeTab.id);assertEquals(0,view.nativePauses)
            owner.openRetainedPrivateApp(app("Music"))
            assertEquals(1,view.nativePauses);assertEquals(1,view.pauseScripts)
        } finally {owner.destroyAll()}
    }

    @Test fun retainedAppsUseExistingBoundedEvictionCloseAndDestroyLifetime()=main {
        val views=linkedMapOf<String,RecordingView>();val owner=session(views,maximum=2)
        try {
            owner.activeWebView();owner.openRetainedPrivateApp(app("First"));val first=owner.tabs.activeTab.id
            owner.openRetainedPrivateApp(app("Second"));val second=owner.tabs.activeTab.id
            views.getValue(second).reset()
            owner.openRetainedPrivateApp(app("Third"));val third=owner.tabs.activeTab.id
            assertEquals(2,owner.tabs.tabs.size);assertFalse(owner.tabs.tabs.any {it.id==first})
            assertEquals(1,views.getValue(first).destructions);assertNull(owner.privateApp(first))
            assertEquals(0,views.getValue(second).nativePauses);assertEquals(0,views.getValue(second).pauseScripts)
            owner.removePrivateApp(checkNotNull(owner.privateApp(second)).id())
            assertEquals(1,views.getValue(second).destructions);assertFalse(owner.tabs.tabs.any {it.id==second})
            owner.destroyAll()
            assertEquals(1,views.getValue(third).destructions);assertNull(owner.privateApp(third))
            val callback=Request();owner.onPermissionRequest(third,callback)
            assertTrue(callback.denied);assertFalse(callback.granted)
        } finally {owner.destroyAll()}
    }

    @Test fun retainedEntryCannotResumeBackgroundOrBypassRendererNetworkAndCallbackGates()=main {
        val views=linkedMapOf<String,RecordingView>();var network=true;var renderer=true
        val owner=session(views,network={network},renderer={renderer});val music=app("Music")
        try {
            owner.openRetainedPrivateApp(music);val first=owner.tabs.activeTab.id
            owner.openRetainedPrivateApp(app("Chat"));val active=owner.tabs.activeTab.id
            views.values.forEach {it.reset()};owner.pauseForBackground()
            views.values.forEach {assertEquals(1,it.nativePauses);assertEquals(1,it.pauseScripts)}
            val count=owner.tabs.tabs.size
            assertThrows(IllegalStateException::class.java){owner.openRetainedPrivateApp(music)}
            assertThrows(IllegalStateException::class.java){owner.selectFromNovaHome(first)}
            assertThrows(IllegalStateException::class.java){owner.newTabFromNovaHome()}
            assertFalse(owner.mediaNetworkingAllowed());assertEquals(count,owner.tabs.tabs.size)
            val background=Request();owner.onPermissionRequest(active,background);assertTrue(background.denied);assertFalse(background.granted)
            owner.resumeForeground();network=false
            assertThrows(IllegalStateException::class.java){owner.openRetainedPrivateApp(music)}
            assertThrows(IllegalStateException::class.java){owner.selectFromNovaHome(first)}
            val blocked=Request();owner.onPermissionRequest(active,blocked);assertTrue(blocked.denied);assertFalse(blocked.granted)
            network=true;renderer=false
            assertThrows(IllegalStateException::class.java){owner.newTabFromNovaHome()}
            assertEquals(count,owner.tabs.tabs.size)
        } finally {owner.destroyAll()}
    }

    @Test fun unchangedControllerStillRequiresFreshPinAndRejectsHiddenOrLaterUnlockCallbacks()=main {
        val views=linkedMapOf<String,RecordingView>();val owner=session(views)
        val app=app("Protected",authentication=true)
        var catalog=PrivateAppCatalog(false,listOf(app));var epoch=1;var hidden=false
        var result:((Boolean)->Unit)?=null
        val store=object:PrivateAppStore {
            override fun owner()=PrivateAppStore.Owner.PRIMARY
            override fun read()=catalog
            override fun update(change:java.util.function.UnaryOperator<PrivateAppCatalog>)=change.apply(catalog).also {catalog=it}
        }
        fun controller():PrivateAppsController {
            val captured=epoch
            return PrivateAppsController(store.owner(),{captured==epoch&&!hidden},
                {action,bytes,id->PrivateAppCommands.execute(store,action,bytes,id)},{error("No fallback navigation")},
                onLaunchApp=owner::openRetainedPrivateApp,authenticate={result=it})
        }
        try {
            val stale=controller();stale.launch(app.id());assertEquals(1,owner.tabs.tabs.size)
            hidden=true;checkNotNull(result)(true);assertEquals(1,owner.tabs.tabs.size)
            hidden=false;stale.launch(app.id());epoch++;checkNotNull(result)(true)
            assertEquals(1,owner.tabs.tabs.size)
            val admitted=controller();admitted.launch(app.id());assertEquals(1,owner.tabs.tabs.size)
            checkNotNull(result)(true);assertEquals(app,owner.privateApp(owner.tabs.activeTab.id))
        } finally {owner.destroyAll()}
    }

    @Test fun retainedSnapshotNeverCreatesColdViewsAndWindowCallbacksCannotEscapeActiveUi()=main {
        val views=linkedMapOf<String,RecordingView>();var interaction=true;var network=true;var renderer=true
        val owner=session(views,network={network},renderer={renderer})
        owner.bindListener(object:BrowserV2Session.Listener by NoopBrowserV2Listener {
            override fun allowWindowInteraction()=interaction
        })
        try {
            assertTrue(owner.createdPrivateAppWebViews().isEmpty());assertTrue(views.isEmpty())
            owner.openRetainedPrivateApp(app("Music"));val music=owner.tabs.activeTab.id
            owner.openRetainedPrivateApp(app("Chat"));val chat=owner.tabs.activeTab.id
            assertEquals(2,owner.createdPrivateAppWebViews().size)
            val count=owner.tabs.tabs.size;val created=views.size
            owner.onWindowFocus(music);assertEquals(chat,owner.tabs.activeTab.id)
            assertNull(owner.onCreateWindow(music,false,true));assertEquals(count,owner.tabs.tabs.size)
            interaction=false // Home flag is read at callback dispatch, before recomposition.
            owner.onWindowFocus(chat);assertNull(owner.onCreateWindow(chat,false,true))
            owner.onCloseWindow(chat);assertTrue(owner.tabs.tabs.any {it.id==chat})
            assertEquals(created,views.size)
            interaction=true;owner.pauseForBackground()
            assertNull(owner.onCreateWindow(chat,false,true));owner.onWindowFocus(music)
            assertEquals(chat,owner.tabs.activeTab.id);assertEquals(created,views.size)
            owner.resumeForeground();network=false
            assertNull(owner.onCreateWindow(chat,false,true))
            network=true;renderer=false
            assertNull(owner.onCreateWindow(chat,false,true))
            renderer=true
            assertNotNull("Live active UI retains normal popup support",owner.onCreateWindow(chat,false,true))
            assertEquals(count+1,owner.tabs.tabs.size)
            owner.destroyAll();val after=views.size
            assertTrue(owner.createdPrivateAppWebViews().isEmpty())
            owner.onWindowFocus(chat);assertNull(owner.onCreateWindow(chat,false,true))
            assertEquals("Destroy leaves metadata but cannot let native callbacks recreate renderers",after,views.size)
        } finally {owner.destroyAll()}
    }

    @Test fun queuedNativeFactoryFocusFromDestroyedOrWrongViewIsNeverForwarded()=main {
        var creations=0;var forwardedFocus=0;var windowEvents=0
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {true},NoopBrowserV2Listener,
            webViewFactory=BrowserV2WebViewFactory {context,callbacks,id,desktop ->
                creations++
                val guarded=object:BrowserWebViewCallbacks by callbacks {
                    override fun onWindowFocus(tabId:String){forwardedFocus++;callbacks.onWindowFocus(tabId)}
                    override fun onStructuralEvent(tabId:String,event:String,details:Map<String,String>){
                        if(event.startsWith("WINDOW_"))windowEvents++
                        callbacks.onStructuralEvent(tabId,event,details)
                    }
                }
                SecureWebViewFactory(guarded).create(context,id,desktop)
            })
        owner.bindListener(object:BrowserV2Session.Listener by NoopBrowserV2Listener {override fun allowWindowInteraction()=true})
        val foreign=WebView(instrumentation.targetContext)
        try {
            val original=owner.activeWebView();val id=owner.tabs.activeTab.id;val chrome=checkNotNull(original.webChromeClient)
            chrome.onRequestFocus(foreign)
            assertEquals(0,forwardedFocus);assertEquals(0,windowEvents)
            fun pending():Map<*,*> = BrowserV2Session::class.java.getDeclaredField("pendingWindowFocus").apply {isAccessible=true}.get(owner) as Map<*,*>
            owner.onWindowFocus(id)
            assertSame("An unattached host retains focus only for its original renderer",original,pending()[id])
            val replacement=checkNotNull(owner.retryActiveWebView())
            assertTrue("Same-tab replacement cannot inherit the original renderer's ticket",pending().isEmpty())
            owner.onWindowFocus(id);assertSame(replacement,pending()[id])
            owner.pauseForBackground();assertTrue("Background retires outstanding native interaction",pending().isEmpty())
            owner.resumeForeground();owner.onWindowFocus(id);assertSame(replacement,pending()[id])
            owner.destroyAll()
            assertTrue("Lock/destroy retires every outstanding native interaction",pending().isEmpty())
            chrome.onRequestFocus(original)
            assertFalse(chrome.onCreateWindow(original,false,true,android.os.Message.obtain()))
            chrome.onCloseWindow(original)
            owner.onWindowFocus(id)
            assertNull(owner.onCreateWindow(id,false,true))
            assertEquals(0,forwardedFocus);assertEquals(0,windowEvents)
            assertEquals("Queued destroyed-view callbacks cannot construct another renderer after the explicit retry",2,creations)
        } finally {foreign.destroy();owner.destroyAll()}
    }

    @Test fun replacedRendererCannotForwardStaleCapabilityLocationOrFileCallbacksIntoSameTab()=main {
        var permissions=0;var locations=0;var files=0
        var deferred=false;var pendingLocation:(()->Unit)?=null;var pendingFile:(()->Unit)?=null
        val uri=Uri.parse("content://synthetic-fixture/not-opened")
        val owner=BrowserV2Session(instrumentation.targetContext,BrowserVpnGate {true},NoopBrowserV2Listener)
        owner.bindListener(object:BrowserV2Session.Listener by NoopBrowserV2Listener {
            override fun onPermissionRequest(request:PermissionRequest){permissions++;request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))}
            override fun onGeolocationRequest(origin:String,callback:android.webkit.GeolocationPermissions.Callback){
                locations++;val answer:()->Unit={callback.invoke(origin,true,false)}
                if(deferred)pendingLocation=answer else answer()
            }
            override fun onShowFileChooser(callback:ValueCallback<Array<Uri>>,params:android.webkit.WebChromeClient.FileChooserParams):Boolean {
                files++;val answer:()->Unit={callback.onReceiveValue(arrayOf(uri))}
                if(deferred)pendingFile=answer else answer()
                return true
            }
        })
        val params=object:android.webkit.WebChromeClient.FileChooserParams() {
            override fun getMode()=MODE_OPEN
            override fun getAcceptTypes()=arrayOf("image/png")
            override fun isCaptureEnabled()=false
            override fun getTitle():CharSequence?=null
            override fun getFilenameHint():String?=null
            override fun createIntent()=android.content.Intent()
        }
        try {
            val original=owner.activeWebView();val id=owner.tabs.activeTab.id;val saved=checkNotNull(original.webChromeClient)
            val live=Request();saved.onPermissionRequest(live);assertTrue(live.granted);assertFalse(live.denied)
            var liveLocation:Boolean?=null;saved.onGeolocationPermissionsShowPrompt("https://native-fixture.invalid"){_,allow,_->liveLocation=allow}
            assertEquals(true,liveLocation)
            var liveFile:Array<Uri>?=null
            assertTrue(saved.onShowFileChooser(original,ValueCallback {liveFile=it},params));assertEquals(listOf(uri),liveFile?.toList())
            assertEquals(listOf(1,1,1),listOf(permissions,locations,files))
            deferred=true
            var delayedLocation:Boolean?=null;val delayedFiles=mutableListOf<Array<Uri>?>()
            saved.onGeolocationPermissionsShowPrompt("https://pending-native.invalid"){_,allow,_->delayedLocation=allow}
            assertTrue(saved.onShowFileChooser(original,ValueCallback {delayedFiles.add(it)},params))
            assertNull(delayedLocation);assertTrue(delayedFiles.isEmpty())
            val replacement=checkNotNull(owner.retryActiveWebView())
            assertEquals(id,owner.tabs.activeTab.id);assertNotSame(original,replacement)
            checkNotNull(pendingLocation).invoke();checkNotNull(pendingFile).invoke()
            assertEquals("A location response queued before destroy must be denied on delivery",false,delayedLocation)
            assertEquals(1,delayedFiles.size);assertNull("A file response queued before destroy must not deliver URIs",delayedFiles.single())
            val stale=Request();saved.onPermissionRequest(stale);assertTrue(stale.denied);assertFalse(stale.granted)
            var staleLocation:Boolean?=null;var retained:Boolean?=null
            saved.onGeolocationPermissionsShowPrompt("https://stale-native.invalid"){_,allow,keep->staleLocation=allow;retained=keep}
            assertEquals(false,staleLocation);assertEquals(false,retained)
            val staleFiles=mutableListOf<Array<Uri>?>()
            assertTrue(saved.onShowFileChooser(original,ValueCallback {staleFiles.add(it)},params))
            assertTrue(checkNotNull(replacement.webChromeClient).onShowFileChooser(original,ValueCallback {staleFiles.add(it)},params))
            assertEquals(2,staleFiles.size);assertTrue(staleFiles.all {it==null})
            assertEquals("Old native requests cannot be presented as requests from the replacement renderer",listOf(1,2,2),listOf(permissions,locations,files))
            assertSame(replacement,owner.activeWebView())
        } finally {owner.destroyAll()}
    }
}
