package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.ui.AppTheme
import java.io.File

/** UI fixtures never navigate remotely. The unavailable provider exercises the real recovery UI. */
class BrowserProfessionalUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewport(width: Int, height: Int, theme: AppTheme, fontScale: Float = 1f) {
        val session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener,
            webViewFactory = BrowserV2WebViewFactory { _, _, _, _ -> error("Synthetic unavailable provider") })
        var history by mutableStateOf(false)
        var clearCount = 0
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    PrivateGalleryTheme(theme) {
                        BrowserV2ProductionDestination(
                            session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, history, { history = it },
                            emptyList(), { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
                            modifier = Modifier.requiredSize(width.dp, height.dp),
                            browserSettings = {
                                BrowserSettingsContent(BrowserSearchEngine.GOOGLE, {}, history, { history = it },
                                    false, {}, false, {}, false, {}, { clearCount++ })
                            },
                        )
                    }
                }
            }
            listOf("Back", "Forward", "Bookmarks", "Tabs", "Browser settings", "More").forEach { label ->
                val node = compose.onNodeWithContentDescription(label)
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val minimum = with(compose.density) { 48.dp.toPx() }
                assertTrue("$label must retain a 48dp target", bounds.width >= minimum - 1 && bounds.height >= minimum - 1)
            }
            compose.onNodeWithText("Retry", substring = false).performScrollTo().assertIsDisplayed()
            capture("primary-$width-$height-$theme-$fontScale")
            // Settings remain reachable with a failed provider, and opening them does not replace the tab.
            val tabId = session.tabs.activeTab.id
            compose.onNodeWithContentDescription("Browser settings").performClick()
            compose.onNodeWithText("Save browsing history").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(history) }
            compose.onNodeWithText("Clear browsing data").performScrollTo().assertIsDisplayed()
            capture("primary-settings-$width-$height-$theme-$fontScale", "browser-panel")
            compose.onNodeWithText("Clear browsing data").performClick()
            compose.runOnIdle { assertEquals(0, clearCount) }
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle { assertEquals(0, clearCount) }
            compose.onNodeWithText("Clear browsing data").performClick()
            compose.onNodeWithText("Clear", substring = false).performClick()
            compose.runOnIdle { assertEquals(1, clearCount) }
            compose.onNodeWithContentDescription("Back to Browser").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(tabId, session.tabs.activeTab.id) }
            compose.onNodeWithTag("browser-v2-toolbar").assertIsDisplayed()
            // Omnibox focus must keep navigation available and must not submit a network request.
            compose.onNodeWithTag("browser-v2-address").performClick().performTextInput("Synthetic draft")
            compose.onNodeWithContentDescription("More").assertIsDisplayed()
        } finally {
            compose.runOnIdle { session.destroyAll() }
        }
    }

    private fun capture(name: String, tag: String = "browser-v2-root") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
        val folder = File(output, "release171").apply { mkdirs() }
        compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun coverLight() = viewport(392, 840, AppTheme.LIGHT)
    @Test fun innerDark() = viewport(840, 900, AppTheme.DARK)
    @Test fun coverLandscapeLargeText() = viewport(840, 392, AppTheme.DARK, 1.3f)
    @Test fun innerLandscapeLight() = viewport(900, 840, AppTheme.LIGHT)
}
