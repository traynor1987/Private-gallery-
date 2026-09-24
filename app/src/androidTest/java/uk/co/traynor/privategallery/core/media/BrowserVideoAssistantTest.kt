package uk.co.traynor.privategallery.core.media

import android.content.pm.ActivityInfo
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.ui.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BrowserVideoAssistantTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var session: BrowserV2Session
    private fun mount() {
        compose.runOnIdle { session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener) }
        compose.setContent { PrivateGalleryTheme {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {})
        } }
    }
    private fun js(source: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        compose.runOnIdle { session.activeWebView().evaluateJavascript(source) { result = it; latch.countDown() } }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return result
    }
    private fun load(html: String, url: String = "https://video-fixture.invalid/") {
        compose.runOnIdle { session.activeWebView().loadDataWithBaseURL(url, "<meta name='viewport' content='width=device-width,initial-scale=1'>$html", "text/html", "UTF-8", null) }
        compose.waitUntil(10000) { js("document.readyState") == "\"complete\"" }
    }
    private fun media(): Pair<String, String>? {
        val latch = CountDownLatch(1)
        var result: Pair<String, String>? = null
        compose.runOnIdle { session.requestPlayableMedia { result = it; latch.countDown() } }
        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS)); return result
    }
    @Test fun pageAddressIsNeverMediaAndBlobDrmCookieSourcesKeepWebView() {
        mount()
        try {
            load("<p>No video</p>", "https://video-fixture.invalid/fake.mp4")
            assertNull(media())
            load("<video src='blob:https://video-fixture.invalid/ephemeral' controls></video>")
            assertNull(media())
            val original = compose.runOnIdle { session.activeWebView() }
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithText("Play in Private Gallery").performClick()
            compose.onNodeWithContentDescription("Exit video view").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertSame(original, session.activeWebView())
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
            }
            load("<video preload='none' src='https://video-fixture.invalid/movie.mp4'></video>")
            assertNotNull(media())
            js("Object.defineProperty(document.querySelector('video'),'mediaKeys',{value:{}});true")
            assertNull(media())
            load("<video preload='none' src='https://cookie-fixture.invalid/movie.mp4'></video>")
            compose.runOnIdle { android.webkit.CookieManager.getInstance().setCookie("https://cookie-fixture.invalid/", "session=fixture") }
            assertNull(media())
            assertFalse(compose.runOnIdle { session.acceptanceReport() }.contains("movie.mp4"))
        } finally { compose.runOnIdle { session.destroyAll() } }
    }
    @Test fun playingVideoAffordanceUsesNativeCustomViewAndRestoresPortrait() {
        mount()
        try {
            val data = android.util.Base64.encodeToString(SyntheticVideo.bytes(), android.util.Base64.NO_WRAP)
            load("<body style='margin:0'><video muted autoplay loop controls style='width:100%;height:240px' src='data:video/mp4;base64,$data'></video></body>")
            js("document.querySelector('video').play().catch(function(){});true")
            compose.waitUntil(15000) { js("!!document.querySelector('[data-pg-video-view]')") == "true" }
            val coords = org.json.JSONArray(js("(function(){var r=document.querySelector('[data-pg-video-view]').getBoundingClientRect();return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerHeight];})()"))
            compose.onNodeWithTag("browser-v2-webview-host").performTouchInput {
                click(Offset(width * coords.getDouble(0).toFloat(), height * coords.getDouble(1).toFloat()))
            }
            compose.waitUntil(10000) { compose.runOnIdle { compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR } }
            compose.runOnIdle { session.exitFullscreen() }
            compose.waitUntil(10000) { compose.runOnIdle { compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT } }
            assertEquals("false", js("!!document.fullscreenElement"))
        } finally { compose.runOnIdle { session.destroyAll() } }
    }
}
