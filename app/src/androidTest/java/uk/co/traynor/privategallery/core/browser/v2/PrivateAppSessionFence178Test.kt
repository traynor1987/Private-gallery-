package uk.co.traynor.privategallery.core.browser.v2

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.PrivateAppClearBarrier

class PrivateAppSessionFence178Test {
 @Test fun profile_clear_blocks_renderer_until_latest_completion_and_stale_download_callbacks_are_discarded() {
  ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->scenario.onActivity {activity ->
   val clear=PrivateAppClearBarrier();var callbacks=0
   val session=BrowserV2Session(activity,BrowserVpnGate {!clear.pending()},NoopBrowserV2Listener,rendererAllowed={!clear.pending()})
   session.bindListener(object:BrowserV2Session.Listener by NoopBrowserV2Listener {
    override fun onDownload(url:String,userAgent:String,contentDisposition:String,mimeType:String) {callbacks++}
   })
   val first=session.newTab().id;val second=session.newTab().id
   session.onDownload(first,"https://example.test/old","","","text/plain");assertEquals(0,callbacks)
   session.onDownload(second,"https://example.test/current","","","text/plain");assertEquals(1,callbacks)
   val obsolete=clear.begin();session.destroyAll();val current=clear.begin()
   assertNull(session.activeWebViewOrNull());assertFalse(session.mediaNetworkingAllowed())
   assertFalse(clear.complete(obsolete){});assertNull(session.activeWebViewOrNull())
   session.onDownload(second,"https://example.test/retired","","","text/plain");assertEquals(1,callbacks)
   assertTrue(clear.complete(current){});assertNotNull(session.activeWebViewOrNull())
   session.destroyAll()
  }}
 }
 @Test fun actual_default_profile_clear_removes_cookies_and_local_storage_before_renderer_reentry() {
  org.junit.Assume.assumeTrue(androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DELETE_BROWSING_DATA))
  val origin="https://privateapps-clear.example.test/"
  val prepared=java.util.concurrent.CountDownLatch(1);val deleted=java.util.concurrent.CountDownLatch(1);val checked=java.util.concurrent.CountDownLatch(1)
  val value=java.util.concurrent.atomic.AtomicReference<String>()
  val barrier=PrivateAppClearBarrier()
  lateinit var session:BrowserV2Session
  ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
   scenario.onActivity {activity ->
    session=BrowserV2Session(activity,BrowserVpnGate {!barrier.pending()},NoopBrowserV2Listener,rendererAllowed={!barrier.pending()})
    val view=session.activeWebView()
    view.settings.domStorageEnabled=true;view.settings.javaScriptEnabled=true
    view.webViewClient=object:android.webkit.WebViewClient() {
     override fun onPageFinished(view:android.webkit.WebView,url:String?) {
      view.evaluateJavascript("localStorage.setItem('privateAppClear','present');localStorage.getItem('privateAppClear')") {stored ->
       value.set(stored)
       android.webkit.CookieManager.getInstance().setCookie(origin,"privateAppClear=present; Secure; Path=/") {prepared.countDown()}
      }
     }
    }
    view.loadDataWithBaseURL(origin,"<html><body>Profile clearing fixture</body></html>","text/html","UTF-8",null)
   }
   assertTrue(prepared.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("\"present\"",value.get())
   scenario.onActivity {
    assertTrue(android.webkit.CookieManager.getInstance().getCookie(origin).orEmpty().contains("privateAppClear=present"))
    val ticket=barrier.begin();session.destroyAll();assertNull(session.activeWebViewOrNull())
    androidx.webkit.WebStorageCompat.deleteBrowsingData(android.webkit.WebStorage.getInstance()) {
     barrier.complete(ticket){};deleted.countDown()
    }
   }
   assertTrue(deleted.await(15,java.util.concurrent.TimeUnit.SECONDS))
   scenario.onActivity {
    assertFalse(barrier.pending());assertFalse(android.webkit.CookieManager.getInstance().getCookie(origin).orEmpty().contains("privateAppClear"))
    val fresh=session.activeWebView();fresh.settings.domStorageEnabled=true;fresh.settings.javaScriptEnabled=true
    fresh.webViewClient=object:android.webkit.WebViewClient() {
     override fun onPageFinished(view:android.webkit.WebView,url:String?) {view.evaluateJavascript("localStorage.getItem('privateAppClear')") {stored ->value.set(stored);checked.countDown()}}
    }
    fresh.loadDataWithBaseURL(origin,"<html><body>Fresh profile</body></html>","text/html","UTF-8",null)
   }
   assertTrue(checked.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("null",value.get())
   scenario.onActivity {session.destroyAll()}
  }
 }


 @Test fun reopening_same_private_app_retains_renderer_and_closing_session_removes_binding() {
  val loaded=java.util.concurrent.CountDownLatch(1);val checked=java.util.concurrent.CountDownLatch(1)
  val value=java.util.concurrent.atomic.AtomicReference<String>()
  lateinit var session:BrowserV2Session
  lateinit var renderer:android.webkit.WebView
  lateinit var app:uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App
  var id="";var count=0
  ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
   scenario.onActivity {activity ->
    session=BrowserV2Session(activity,BrowserVpnGate {false},NoopBrowserV2Listener,rendererAllowed={true})
    app=uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App(java.util.UUID.randomUUID().toString(),"Saved App","https://example.test/app/","https://example.test/app/","standalone","",false,false,true)
    session.openPrivateApp(app);id=session.tabs.activeTab.id;count=session.tabs.tabs.size;renderer=session.activeWebView()
    renderer.settings.javaScriptEnabled=true
    renderer.webViewClient=object:android.webkit.WebViewClient() {
     override fun onPageFinished(view:android.webkit.WebView,url:String?) {
      view.evaluateJavascript("document.getElementById('retained').value='private draft';document.getElementById('retained').value") {stored ->value.set(stored);loaded.countDown()}
     }
    }
    renderer.loadDataWithBaseURL(app.startUrl(),"<html><body><input id='retained'></body></html>","text/html","UTF-8",null)
   }
   assertTrue(loaded.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("\"private draft\"",value.get())
   scenario.onActivity {
    session.newTab();session.openPrivateApp(app)
    assertEquals(id,session.tabs.activeTab.id);assertEquals(count+1,session.tabs.tabs.size)
    assertSame(renderer,session.activeWebView());assertEquals(app,session.privateApp(id))
    renderer.evaluateJavascript("document.getElementById('retained').value") {stored ->value.set(stored);checked.countDown()}
   }
   assertTrue(checked.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("\"private draft\"",value.get())
   scenario.onActivity {
    session.removePrivateApp(app.id());assertNull(session.privateApp(id));assertFalse(session.tabs.tabs.any {it.id==id})
    session.openPrivateApp(app);assertNotEquals(id,session.tabs.activeTab.id)
    session.destroyAll();assertNull(session.privateApp(id))
   }
  }
 }

}
