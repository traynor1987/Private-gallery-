package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.browser.v2.BrowserVpnGate
import uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener
import java.util.concurrent.atomic.AtomicBoolean

/** PixelCopy-backed captures are essential: semantics still pass when WebView paints over chrome. */
@RunWith(AndroidJUnit4::class)
class BrowserV2InitialPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun staticToRealKeepsChromePixelsBeforeNavigationAndAfterLocalPage() =
        assertPresentationAcrossNavigation(width = 840, identicalEmptyAndAddressGeometry = true)

    @Test fun narrowStaticToRealKeepsChromePixelsAcrossLegitimatePlaceholderReflow() =
        assertPresentationAcrossNavigation(width = 392, identicalEmptyAndAddressGeometry = false)

    private fun assertPresentationAcrossNavigation(width: Int, identicalEmptyAndAddressGeometry: Boolean) {
        val session = mount(static = true, width = width)
        val baseline = chromePixels()
        val geometry = chromeGeometry()

        switchHost("REAL WebView")
        assertChromeGeometry(geometry)
        val before = chromePixels()
        assertSamePixels("Initial WebView must not paint over Browser chrome", baseline, before)
        var retained: WebView? = null
        var parent: Any? = null
        compose.runOnIdle {
            retained = session.activeWebView()
            parent = retained!!.parent
            assertTrue("No document should be loaded to make chrome appear", retained!!.url.isNullOrEmpty())
            assertTrue(retained!!.isAttachedToWindow)
        }
        assertNativeBounds(session)

        loadLocalPage(session)
        val navigatedGeometry = chromeGeometry()
        if (identicalEmptyAndAddressGeometry) {
            assertChromeGeometry(geometry)
        } else {
            // At narrow widths the empty placeholder wraps; a short address removes those lines.
            // Keep horizontal geometry and the chrome/omnibox origins stable, then prove the
            // resulting geometry exactly matches STATIC chrome with the same address below.
            geometry.forEach { (tag, beforeBounds) ->
                val afterBounds = navigatedGeometry.getValue(tag)
                assertEquals(beforeBounds.left, afterBounds.left, 0f)
                assertEquals(beforeBounds.right, afterBounds.right, 0f)
                if (tag == "browser-v2-chrome" || tag == "browser-v2-address") {
                    assertEquals(beforeBounds.top, afterBounds.top, 0f)
                }
            }
        }
        val afterNavigation = chromePixels()
        assertNativeBounds(session)
        compose.runOnIdle {
            assertSame(retained, session.activeWebView())
            assertSame(parent, retained!!.parent)
        }

        // Exercise retained AndroidView removal and reattachment without constructing a new tab.
        switchHost("STATIC host")
        // The omnibox legitimately changes from empty to the local document address.
        // Compare against static chrome in that same state, while geometry stays identical.
        val navigatedReference = chromePixels()
        assertChromeGeometry(navigatedGeometry)
        assertSamePixels("Navigated page must not paint over chrome", navigatedReference, afterNavigation)
        switchHost("REAL WebView")
        assertSamePixels("Retained remount must preserve chrome", navigatedReference, chromePixels())
        assertChromeGeometry(navigatedGeometry)
        assertNativeBounds(session)
        compose.runOnIdle { assertSame(retained, session.activeWebView()) }
    }

    @Test fun directRealMountShowsChromePixelsWithoutAnyNavigationOrInteraction() {
        val session = mount(static = false)
        val initial = chromePixels()
        val geometry = chromeGeometry()
        assertNativeBounds(session)
        compose.runOnIdle {
            assertTrue(session.activeWebView().url.isNullOrEmpty())
            assertEquals("", session.tabs.activeTab.url)
        }
        // The reference is captured only after saving the untouched initial frame above.
        switchHost("STATIC host")
        assertSamePixels("Direct initial mount must already show chrome", chromePixels(), initial)
        assertChromeGeometry(geometry)
    }

    private val staticHost = androidx.compose.runtime.mutableStateOf(false)

    private fun mount(static: Boolean, width: Int = 392): BrowserV2Session {
        staticHost.value = static
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { false }, NoopBrowserV2Listener)
        compose.setContent {
            PrivateGalleryTheme {
                BrowserV2ProductionDestination(
                    session = session,
                    searchEngine = BrowserSearchEngine.GOOGLE,
                    onSaveToVault = { _, _ -> }, onHistoryVisited = { _, _ -> },
                    saveHistory = false, onSaveHistoryChanged = {}, bookmarks = emptyList(),
                    onAddBookmark = { _, _, _ -> }, onRemoveBookmark = {},
                    onLoadHistory = { it(emptyList()) }, onClearHistory = { it() },
                    onOpenBrowserSettings = {}, modifier = Modifier.requiredSize(width.dp, 840.dp),
                    acceptanceProbeEnabled = true, staticContentHost = staticHost.value,
                )
            }
        }
        compose.waitForIdle()
        return session
    }

    private fun switchHost(label: String) {
        compose.runOnIdle { staticHost.value = label == "STATIC host" }
        compose.waitForIdle()
    }

    private fun chromeGeometry() = listOf("browser-v2-chrome", "browser-v2-address", "browser-v2-reload", "browser-v2-tabs", "browser-v2-toolbar")
        .associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }

    private fun assertChromeGeometry(expected: Map<String, androidx.compose.ui.geometry.Rect>) {
        expected.forEach { (tag, bounds) ->
            compose.onNodeWithTag(tag).assertIsDisplayed()
            assertEquals("Stable $tag geometry", bounds, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        }
        listOf("Back", "Forward", "More").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
    }

    private fun chromePixels(): Bitmap {
        val address = compose.onNodeWithTag("browser-v2-chrome").captureToImage().asAndroidBitmap()
        val toolbar = compose.onNodeWithTag("browser-v2-toolbar").captureToImage().asAndroidBitmap()
        // Compare both native chrome regions; the WebView must not overdraw either.
        return Bitmap.createBitmap(maxOf(address.width, toolbar.width), address.height + toolbar.height, Bitmap.Config.ARGB_8888).also {
            val canvas = android.graphics.Canvas(it)
            canvas.drawBitmap(address, 0f, 0f, null)
            canvas.drawBitmap(toolbar, 0f, address.height.toFloat(), null)
        }
    }

    private fun assertSamePixels(message: String, expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        var changed = 0
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            if (expected.getPixel(x, y) != actual.getPixel(x, y)) changed++
        }
        val fraction = changed.toDouble() / (expected.width * expected.height)
        // Allow rasterisation noise; a white overdraw changes almost the whole region.
        assertTrue("$message; changed pixel fraction=$fraction", fraction < 0.01)
    }

    private fun assertNativeBounds(session: BrowserV2Session) {
        val chrome = compose.onNodeWithTag("browser-v2-chrome").fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot
        val androidHost = compose.onNodeWithTag("browser-v2-webview-host").fetchSemanticsNode().boundsInRoot
        assertEquals(host, androidHost)
        assertTrue(host.top >= chrome.bottom)
        val toolbar = compose.onNodeWithTag("browser-v2-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(host.bottom <= toolbar.top)
        compose.runOnIdle {
            val view = session.activeWebView()
            assertEquals(host.width.toInt(), view.width)
            assertEquals(host.height.toInt(), view.height)
            assertEquals(android.view.View.VISIBLE, view.visibility)
            assertEquals(1f, view.alpha, 0f)
            val nativeParent = view.parent as android.view.View
            assertEquals(nativeParent.width, view.width)
            assertEquals(nativeParent.height, view.height)
        }
    }

    private fun loadLocalPage(session: BrowserV2Session) {
        compose.runOnIdle {
            // Test-only in-memory document; production WebView/client/security configuration stays intact.
            session.activeWebView().loadDataWithBaseURL(
                "about:blank",
                "<!doctype html><title>LOCAL FIXTURE</title><style>html,body{margin:0;width:100%;height:100%;background:#34205f}</style>",
                "text/html", "UTF-8", null,
            )
        }
        compose.waitUntil(15_000) {
            compose.runOnIdle { session.tabs.activeTab.title == "LOCAL FIXTURE" && !session.tabs.activeTab.loading }
        }
        val ready = AtomicBoolean(false)
        compose.runOnIdle {
            session.activeWebView().postVisualStateCallback(1, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.set(true) }
            })
        }
        compose.waitUntil(15_000) { ready.get() }
        compose.waitUntil(15_000) {
            val image = compose.onNodeWithTag("browser-v2-page-region").captureToImage().asAndroidBitmap()
            val colour = image.getPixel(image.width / 2, image.height / 2)
            colour == Color.rgb(0x34, 0x20, 0x5f)
        }
    }
}
