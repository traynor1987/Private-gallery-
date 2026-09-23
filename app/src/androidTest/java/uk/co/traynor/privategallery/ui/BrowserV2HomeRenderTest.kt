package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2WebViewFactory
import uk.co.traynor.privategallery.core.browser.v2.BrowserVpnGate
import uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener
import android.webkit.WebView

/** Exercises the V2 composable used by the production Browser destination, not the retired V1 UI. */
@RunWith(AndroidJUnit4::class)
class BrowserV2HomeRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun browserV2ChromeSurvivesAnUnavailableWebViewProvider() {
        compose.setContent { BrowserV2Fixture(360.dp, 720.dp, staticContentHost = false) }

        compose.onNodeWithTag("browser-v2-root").assertIsDisplayed()
        compose.onNodeWithText("PRIVATE GALLERY").assertIsDisplayed()
        compose.onNodeWithText("BROWSER").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-address").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-reload").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-tabs").assertIsDisplayed()
        compose.onNodeWithText("BROWSER UNAVAILABLE").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test fun browserV2ChromeFitsFoldOuterAndInnerWidths() {
        compose.setContent { BrowserV2Fixture(392.dp, 840.dp) }
        compose.onNodeWithTag("browser-v2-address").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-page-region").assertIsDisplayed()

        compose.setContent { BrowserV2Fixture(840.dp, 900.dp) }
        compose.onNodeWithTag("browser-v2-address").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-page-region").assertIsDisplayed()
    }

    @Test fun productionBrowserRouteShowsEveryAcceptanceLayerWithStaticHost() {
        listOf(392.dp to 840.dp, 840.dp to 900.dp).forEach { (width, height) ->
            compose.setContent { BrowserV2Fixture(width, height) }

            listOf("BROWSER_ROUTE", "BROWSER_V2_ROOT", "BROWSER_CHROME", "CONTENT_HOST", "BROWSER CONTENT HOST")
                .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            compose.onNodeWithTag("browser-v2-root").assertIsDisplayed()
            compose.onNodeWithTag("browser-v2-address").assertIsDisplayed()
            compose.onNodeWithTag("browser-v2-static-content-host").assertIsDisplayed()

            val chrome = compose.onNodeWithTag("browser-v2-chrome").fetchSemanticsNode().boundsInRoot
            val host = compose.onNodeWithTag("browser-v2-static-content-host").fetchSemanticsNode().boundsInRoot
            assert(host.width > 0f && host.height > 0f)
            assert(host.top >= chrome.bottom)
        }
    }

    @Test fun realWebViewTypingKeepsChromeAndHostBoundsWithoutNavigating() {
        val session = BrowserV2Session(
            appContext = compose.activity,
            vpnGate = BrowserVpnGate { true },
            listener = NoopBrowserV2Listener,
        )
        compose.setContent {
            PrivateGalleryTheme {
                BrowserV2ProductionDestination(
                    session = session,
                    searchEngine = BrowserSearchEngine.GOOGLE,
                    onSaveToVault = { _, done -> done("Saved") },
                    onHistoryVisited = { _, _ -> },
                    saveHistory = false,
                    onSaveHistoryChanged = {},
                    bookmarks = emptyList(),
                    onAddBookmark = { _, _, done -> done("Saved") },
                    onRemoveBookmark = {},
                    onLoadHistory = { it(emptyList()) },
                    onClearHistory = { it() },
                    onOpenBrowserSettings = {},
                    modifier = Modifier.requiredSize(392.dp, 840.dp),
                    acceptanceProbeEnabled = false,
                    staticContentHost = false,
                )
            }
        }
        compose.waitForIdle()

        lateinit var originalWebView: WebView
        compose.runOnIdle { originalWebView = session.activeWebView() }
        val originalParent = originalWebView.parent
        assertNotNull("Real WebView must be attached to AndroidView", originalParent)
        val regionBounds = compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot
        val webViewBounds = compose.onNodeWithTag("browser-v2-webview-host").fetchSemanticsNode().boundsInRoot
        val chromeBounds = compose.onNodeWithTag("browser-v2-chrome").fetchSemanticsNode().boundsInRoot
        assertEquals(regionBounds.left, webViewBounds.left, 1f)
        assertEquals(regionBounds.top, webViewBounds.top, 1f)
        assertEquals(regionBounds.right, webViewBounds.right, 1f)
        assertEquals(regionBounds.bottom, webViewBounds.bottom, 1f)
        assertTrue("WebView must begin below chrome", webViewBounds.top >= chromeBounds.bottom)

        compose.onNodeWithTag("browser-v2-address").performClick()
        compose.onNodeWithTag("browser-v2-address").performTextInput("example.org")
        compose.waitForIdle()

        compose.onNodeWithTag("browser-v2-address").assertTextEquals("example.org")
        compose.onNodeWithText("PRIVATE GALLERY").assertIsDisplayed()
        compose.onNodeWithText("BROWSER").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Typing must not navigate", "", session.tabs.activeTab.url)
            assertSame("The retained WebView instance must survive text recomposition", originalWebView, session.activeWebView())
            assertSame("Typing must not reparent the WebView", originalParent, originalWebView.parent)
        }
    }

    @Test fun realWebViewSubmitKeepsChromeAndRetainedViewWhenNetworkingIsBlocked() {
        val session = BrowserV2Session(
            appContext = compose.activity,
            vpnGate = BrowserVpnGate { false },
            listener = NoopBrowserV2Listener,
        )
        compose.setContent {
            PrivateGalleryTheme {
                BrowserV2ProductionDestination(
                    session = session,
                    searchEngine = BrowserSearchEngine.GOOGLE,
                    onSaveToVault = { _, done -> done("Saved") },
                    onHistoryVisited = { _, _ -> },
                    saveHistory = false,
                    onSaveHistoryChanged = {},
                    bookmarks = emptyList(),
                    onAddBookmark = { _, _, done -> done("Saved") },
                    onRemoveBookmark = {},
                    onLoadHistory = { it(emptyList()) },
                    onClearHistory = { it() },
                    onOpenBrowserSettings = {},
                    modifier = Modifier.requiredSize(392.dp, 840.dp),
                    acceptanceProbeEnabled = false,
                    staticContentHost = false,
                )
            }
        }
        compose.waitForIdle()
        lateinit var retainedWebView: WebView
        compose.runOnIdle { retainedWebView = session.activeWebView() }

        compose.onNodeWithTag("browser-v2-address").performClick()
        compose.onNodeWithTag("browser-v2-address").performTextInput("bbc.co.uk")
        compose.onNodeWithTag("browser-v2-address").performImeAction()
        compose.waitForIdle()

        compose.onNodeWithText("PRIVATE GALLERY").assertIsDisplayed()
        compose.onNodeWithText("BROWSER").assertIsDisplayed()
        compose.onNodeWithTag("browser-v2-webview-host").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("VPN-gated submit must not start navigation", "", session.tabs.activeTab.url)
            assertSame("Submit must retain the mounted WebView", retainedWebView, session.activeWebView())
        }
    }

    @Test fun staticHostOmniboxSubmitDoesNotCrashOrNavigate() {
        val session = BrowserV2Session(
            appContext = compose.activity,
            vpnGate = BrowserVpnGate { true },
            listener = NoopBrowserV2Listener,
            webViewFactory = BrowserV2WebViewFactory { _, _, _, _ ->
                throw IllegalStateException("Static host test must not create WebView")
            },
        )
        compose.setContent {
            PrivateGalleryTheme {
                BrowserV2ProductionDestination(
                    session = session,
                    searchEngine = BrowserSearchEngine.GOOGLE,
                    onSaveToVault = { _, done -> done("Saved") },
                    onHistoryVisited = { _, _ -> },
                    saveHistory = false,
                    onSaveHistoryChanged = {},
                    bookmarks = emptyList(),
                    onAddBookmark = { _, _, done -> done("Saved") },
                    onRemoveBookmark = {},
                    onLoadHistory = { it(emptyList()) },
                    onClearHistory = { it() },
                    onOpenBrowserSettings = {},
                    modifier = Modifier.requiredSize(392.dp, 840.dp),
                    acceptanceProbeEnabled = true,
                    staticContentHost = true,
                )
            }
        }
        compose.onNodeWithTag("browser-v2-address").performClick()
        compose.onNodeWithTag("browser-v2-address").performTextInput("bbc.co.uk")
        compose.onNodeWithTag("browser-v2-address").performImeAction()
        compose.waitForIdle()

        compose.onNodeWithTag("browser-v2-address").assertTextEquals("bbc.co.uk")
        compose.onNodeWithText("BROWSER CONTENT HOST").assertIsDisplayed()
        compose.onNodeWithText("Static content host is active. Switch to Real WebView before navigating.").assertIsDisplayed()
        compose.runOnIdle { assertEquals("", session.tabs.activeTab.url) }
    }

    @androidx.compose.runtime.Composable
    private fun BrowserV2Fixture(
        width: androidx.compose.ui.unit.Dp,
        height: androidx.compose.ui.unit.Dp,
        staticContentHost: Boolean = true,
    ) {
        val session = androidx.compose.runtime.remember {
            BrowserV2Session(
                appContext = compose.activity,
                vpnGate = BrowserVpnGate { true },
                listener = NoopBrowserV2Listener,
                webViewFactory = BrowserV2WebViewFactory { _, _, _, _ ->
                    throw IllegalStateException("Test WebView unavailable")
                },
            )
        }
        PrivateGalleryTheme {
            BrowserV2ProductionDestination(
                session = session,
                searchEngine = BrowserSearchEngine.GOOGLE,
                onSaveToVault = { _, done -> done("Saved") },
                onHistoryVisited = { _, _ -> },
                saveHistory = false,
                onSaveHistoryChanged = {},
                bookmarks = emptyList(),
                onAddBookmark = { _, _, done -> done("Saved") },
                onRemoveBookmark = {},
                onLoadHistory = { loaded -> loaded(emptyList()) },
                onClearHistory = { done -> done() },
                onOpenBrowserSettings = {},
                modifier = Modifier.requiredSize(width, height),
                acceptanceProbeEnabled = true,
                staticContentHost = staticContentHost,
            )
        }
    }
}
