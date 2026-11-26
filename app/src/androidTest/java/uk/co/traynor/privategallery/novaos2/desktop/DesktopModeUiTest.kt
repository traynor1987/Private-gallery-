package uk.co.traynor.privategallery.novaos2.desktop

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherState
import uk.co.traynor.privategallery.novaos2.LauncherNovaSystemsActions
import uk.co.traynor.privategallery.novaos2.NovaDesktopMode
import uk.co.traynor.privategallery.novaos2.NovaSystemConfig
import uk.co.traynor.privategallery.novaos2.NovaSystemHost
import uk.co.traynor.privategallery.novaos2.NovaSystemId
import uk.co.traynor.privategallery.novaos2.captureNovaSystem
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class DesktopModeUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule { 820 to 920 }).around(compose)

    @Test fun innerSplitPersistsAcrossCoverTransitionAndUsesNativeAppRoute() {
        val persisted = NovaSystemConfig(compose.activity, "desktop-mode")
        persisted.clear()
        var wide by mutableStateOf(true)
        var opened: NovaCoreApp? = null
        try {
            compose.setContent {
                MaterialTheme {
                    Box(Modifier.width(if (wide) 820.dp else 375.dp)) {
                        NovaDesktopMode(desktopHost(wide) { opened = it })
                    }
                }
            }
            val split = compose.onNodeWithTag("desktop-split")
            compose.waitUntil(10_000) { split.isDisplayed() }
            compose.onNodeWithText("Widen left").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onNodeWithText("55 / 45").isDisplayed() }
            assertEquals(55, DesktopSettings.decode(persisted.read()).current.splitPercent)
            captureNovaSystem("desktop-inner-split")

            compose.runOnIdle { wide = false }
            compose.onNodeWithTag("desktop-compact").performScrollTo().assertIsDisplayed()
            captureNovaSystem("desktop-cover-compact")
            compose.onNodeWithText("Nova Notes").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(NovaCoreApp.NOTES, opened) }
            compose.runOnIdle { wide = true }
            compose.onNodeWithText("55 / 45").performScrollTo().assertIsDisplayed()
        } finally { persisted.clear() }
    }

    private fun desktopHost(wide: Boolean, openApp: (NovaCoreApp) -> Unit) = NovaSystemHost(
        NovaLauncherState(), emptyList(), emptyMap(),
        LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
            selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
            exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> }),
        wide = wide, available = setOf(NovaSystemId.DESKTOP), onBack = {}, open = { false },
        platform = LauncherNovaSystemsActions({ true }, { false }, { app -> openApp(app); true }, { false }, { false }),
    )
}
