package uk.co.traynor.privategallery.ui

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.BrowserBookmark
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*

@RunWith(AndroidJUnit4::class)
class BrowserPolishTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun chromeSlidesThroughIntermediateHeightWithoutRemovingContentEarly() {
        var fraction by mutableFloatStateOf(1f)
        compose.setContent { PrivateGalleryTheme {
            BrowserChromeBar(fraction, top = true) {
                androidx.compose.foundation.layout.Box(Modifier.requiredSize(200.dp, 60.dp).testTag("animated-bar"))
            }
        } }
        val full = compose.onNodeWithTag("animated-bar").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { fraction = .5f }
        val middle = compose.onNodeWithTag("animated-bar").fetchSemanticsNode().boundsInRoot.height
        assertTrue("bar must clip progressively", middle > 0 && middle < full)
        compose.runOnIdle { fraction = 0f }
        compose.onNodeWithTag("animated-bar").assertDoesNotExist()
    }

    @Test fun systemBackAtBrowserRootReturnsToGallery() {
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
        var returned = false
        compose.setContent { PrivateGalleryTheme {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {},
                emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {}, onOpenGallery = { returned = true })
        } }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertTrue(returned); assertFalse(compose.activity.isFinishing); session.destroyAll() }
    }

    @Test fun browserSettingsOpenLocallyAndReturnToSamePageAndView() {
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
        var saveHistory by mutableStateOf(false)
        var globalSettingsOpened = false
        compose.setContent { PrivateGalleryTheme {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, saveHistory, { saveHistory = it },
                emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, { globalSettingsOpened = true },
                browserSettings = { BrowserSettingsContent(BrowserSearchEngine.GOOGLE, {}, saveHistory, { saveHistory = it }, false, {}, false, {}, false, {}, {}) })
        } }
        lateinit var original: WebView
        compose.runOnIdle { original = session.activeWebView() }
        val parent = original.parent
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Browser settings").performScrollTo().performClick()
        compose.onNodeWithText("Search engine").assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.runOnIdle { assertTrue(saveHistory) }
        compose.onNodeWithText("Require VPN for browsing").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Browser").performClick()
        compose.runOnIdle { assertFalse(globalSettingsOpened); assertSame(original, session.activeWebView()); assertSame(parent, original.parent) }
        compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
        compose.runOnIdle { session.destroyAll() }
    }

    @Test fun bookmarksFilterAndRemoveWithoutOpeningAnotherPage() {
        var bookmarks by mutableStateOf(listOf(BrowserBookmark("one", "First saved page", "https://one.example", 1), BrowserBookmark("two", "Second saved page", "https://two.example", 2)))
        var opened: String? = null
        compose.setContent { PrivateGalleryTheme { BrowserBookmarksPanel(bookmarks, { opened = it }, { id -> bookmarks = bookmarks.filterNot { it.id == id } }, {}) } }
        compose.onNodeWithText("Search bookmarks").performTextInput("two.example")
        compose.onNodeWithText("First saved page").assertDoesNotExist()
        compose.onNodeWithText("Second saved page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove bookmark").performClick()
        compose.runOnIdle { assertNull(opened); assertEquals(listOf("one"), bookmarks.map { it.id }) }
    }

    @Test fun historyClearNeedsConfirmationAndRetainsDatesAndTitles() {
        var cleared = false
        val entries = listOf(BrowserHistoryEntry("one", "A visited page", "https://one.example", 1700000000000))
        compose.setContent { PrivateGalleryTheme { BrowserHistoryPanel(entries, true, {}, { cleared = true }, {}) } }
        compose.onNodeWithText("A visited page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear history").performClick()
        compose.runOnIdle { assertFalse(cleared) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(cleared) }
        compose.onNodeWithContentDescription("Clear history").performClick()
        compose.onNodeWithText("Clear", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }

    @Test fun realPageScrollHidesThenRevealsChromeWithoutRecreatingWebView() {
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
        compose.setContent { PrivateGalleryTheme {
            BrowserV2ProductionDestination(session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {},
                emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {})
        } }
        lateinit var view: WebView
        compose.runOnIdle {
            view = session.activeWebView()
            view.loadDataWithBaseURL("https://scroll.example/", "<meta name='viewport' content='width=device-width,initial-scale=1'><body style='height:12000px'>Local scroll fixture</body>", "text/html", "UTF-8", null)
        }
        compose.waitUntil(10000) { var ready = false; compose.runOnIdle { ready = view.contentHeight > 5000 && !session.tabs.activeTab.loading }; ready }
        val parent = view.parent
        // A script/programmatic scroll must not hide controls.
        compose.runOnIdle { view.scrollTo(0, 300) }
        compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
        // Compose batches a whole gesture without intervening frames. WebView scroll offsets
        // arrive asynchronously from Chromium, so exercise native input at real gesture cadence.
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(WebView::class.java))
            .perform(androidx.test.espresso.action.ViewActions.swipeUp())
        compose.waitUntil(5000) { compose.runOnIdle { view.scrollY > 300 } }
        compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(WebView::class.java))
            .perform(androidx.test.espresso.action.ViewActions.swipeDown())
        compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
        compose.runOnIdle { assertSame(view, session.activeWebView()); assertSame(parent, view.parent); session.destroyAll() }
    }
}
