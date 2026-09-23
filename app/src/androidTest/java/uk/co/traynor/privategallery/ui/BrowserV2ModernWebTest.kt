package uk.co.traynor.privategallery.ui

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.action.ViewActions.click
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real provider and production clients; in-memory HTTPS-origin fixtures never use the network. */
@RunWith(AndroidJUnit4::class)
class BrowserV2ModernWebTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var session: BrowserV2Session
    private val callbackEvents = mutableListOf<String>()

    @After fun cleanup() { if (::session.isInitialized) compose.runOnIdle { session.destroyAll() } }

    @Test fun asynchronousApplicationBootReceivesVersionedProviderIdentity() {
        mount()
        load("""
            <body style='background:blue'><main id='app'></main><script>
            Promise.resolve().then(function(){
              var engine = /Chrome\/(\d+)/.exec(navigator.userAgent);
              if (engine && Number(engine[1]) >= 100) {
                document.getElementById('app').appendChild(document.createElement('button'));
                document.getElementById('app').style.background='white';
              }
            });</script>
        """.trimIndent())
        assertEquals("Version-sensitive application boot must receive the real engine identity", "1", js("document.querySelectorAll('#app button').length"))
        compose.onNodeWithTag("browser-v2-chrome").assertIsDisplayed()
    }

    @Test fun domModalAsyncContentStorageFrameAndCanvasStayInParent() {
        mount()
        load("""
            <main><button id='open' onclick="document.querySelector('dialog').showModal();Promise.resolve().then(()=>document.querySelector('dialog').appendChild(document.createElement('button')))">Open</button></main>
            <dialog></dialog><iframe srcdoc='<p>Local frame</p>'></iframe><canvas width='10' height='10'></canvas>
            <script>localStorage.setItem('fixture','yes');document.querySelector('canvas').getContext('2d').fillRect(0,0,10,10);</script>
        """.trimIndent())
        val parent = compose.runOnIdle { session.activeWebView() }
        js("document.querySelector('#open').click();true")
        assertEquals("true", js("document.querySelector('dialog').open && document.querySelector('dialog button') !== null"))
        assertEquals("true", js("localStorage.getItem('fixture') === 'yes' && document.querySelectorAll('iframe').length === 1 && document.querySelector('canvas').getContext('2d').getImageData(0,0,1,1).data[3] === 255"))
        js("document.querySelector('dialog').close();true")
        assertEquals("false", js("document.querySelector('dialog').open"))
        compose.runOnIdle { assertSame(parent, session.activeWebView()); assertEquals(1, session.tabs.tabs.size) }
    }

    @Test fun childWindowReceivesLiveDocumentAndCloseReturnsToOpener() {
        mount()
        load("<title>OPENER</title><main>Parent</main>")
        val parent = compose.runOnIdle { session.activeWebView() }
        js("window.child = window.open('','_blank');true", parent)
        compose.waitUntil(10_000) { compose.runOnIdle { session.tabs.tabs.size == 2 } }
        // Chromium transfers the pending child asynchronously through WebViewTransport.
        compose.waitUntil(10_000) { js("!!window.child && !window.child.closed", parent) == "true" }
        js("window.child.document.body.innerHTML='<button>Child fixture</button>';true", parent)
        assertEquals("1", js("document.querySelectorAll('button').length"))
        assertTrue(callbackEvents.contains("WINDOW_CREATE_REQUEST"))
        assertTrue(callbackEvents.contains("WINDOW_CREATE_RESULT"))
        js("window.close();true")
        compose.waitUntil(10_000) { compose.runOnIdle { session.tabs.tabs.size == 1 } }
        compose.runOnIdle { assertSame(parent, session.activeWebView()) }
        compose.onNodeWithTag("browser-v2-chrome").assertIsDisplayed()
    }

    @Test fun nativeConfirmDialogCompletesWithoutReplacingParentPage() {
        mount()
        load("<main>Native dialog fixture</main>")
        compose.runOnIdle { session.activeWebView().evaluateJavascript("window.answer=confirm('Local fixture');", null) }
        compose.waitUntil(10_000) { compose.runOnIdle { callbackEvents.contains("JS_DIALOG_CONFIRM") } }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        assertEquals("true", js("window.answer"))
        assertTrue(callbackEvents.contains("JS_DIALOG_CONFIRM"))
        compose.runOnIdle { assertEquals(1, session.tabs.tabs.size) }
    }

    @Test fun windowFocusRequestSelectsExistingTabWithoutCreatingAnother() {
        mount()
        val opener = compose.runOnIdle { session.tabs.activeTab.id }
        compose.runOnIdle { session.newTab() }
        compose.runOnIdle { session.webView(opener).webChromeClient!!.onRequestFocus(session.webView(opener)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(opener, session.tabs.activeTab.id)
            assertEquals(2, session.tabs.tabs.size)
            assertTrue(session.activeWebView().hasFocus())
        }
    }

    private fun mount() {
        session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener,
            webViewFactory = BrowserV2WebViewFactory { context, callbacks, id, desktop ->
                SecureWebViewFactory(object : BrowserWebViewCallbacks by callbacks {
                    override fun onStructuralEvent(tabId: String, event: String, details: Map<String, String>) {
                        callbackEvents += event
                        callbacks.onStructuralEvent(tabId, event, details)
                    }
                }).create(context, id, desktop)
            })
        compose.setContent { PrivateGalleryTheme { BrowserV2ProductionDestination(
            session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(),
            { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
            acceptanceProbeEnabled = false, staticContentHost = false,
        ) } }
        compose.waitForIdle()
    }

    private fun load(body: String) {
        compose.runOnIdle { session.activeWebView().loadDataWithBaseURL("https://fixture.invalid/", "<!doctype html><meta id='fixture-ready' name='viewport' content='width=device-width,initial-scale=1'>$body", "text/html", "UTF-8", null) }
        compose.waitUntil(15_000) { js("document.readyState") == "\"complete\"" && js("document.getElementById('fixture-ready') !== null") == "true" }
    }

    private fun js(script: String, view: WebView = compose.runOnIdle { session.activeWebView() }): String {
        val done = CountDownLatch(1)
        var result = ""
        compose.runOnIdle { view.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue("JavaScript callback completed", done.await(10, TimeUnit.SECONDS))
        return result
    }
}
