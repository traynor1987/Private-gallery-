package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine

/**
 * Render-level regression coverage for the real Browser destination composable.
 * The factory deliberately fails so this test proves that browser chrome is never gated on
 * a device WebView provider being available.
 */
@RunWith(AndroidJUnit4::class)
class BrowserHomeRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun browserChromeAndFailureStateRemainVisibleWhenWebViewCannotStart() {
        compose.setContent {
            PrivateGalleryTheme {
                BrowserHome(
                    existingWebView = null,
                    searchEngine = BrowserSearchEngine.GOOGLE,
                    onWebViewReady = {},
                    onFullscreenExitChanged = {},
                    webViewFactory = BrowserWebViewFactory { _, _ ->
                        throw IllegalStateException("Test WebView unavailable")
                    },
                )
            }
        }

        compose.onNodeWithTag("browser-root").assertIsDisplayed()
        compose.onNodeWithText("Browser").assertIsDisplayed()
        compose.onNodeWithTag("browser-address").assertIsDisplayed()
        compose.onNodeWithTag("browser-controls").assertIsDisplayed()
        compose.onNodeWithTag("browser-page-region").assertIsDisplayed()
        compose.onNodeWithText("Go").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Browser unavailable").assertIsDisplayed()
    }
}
