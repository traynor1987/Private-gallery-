package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2WebViewFactory
import uk.co.traynor.privategallery.core.browser.v2.BrowserVpnGate
import uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener

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
