package uk.co.traynor.privategallery.novaos2

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.ui.BrowserViewportRule
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherState
import uk.co.traynor.privategallery.novaos2.desktop.DesktopPreset
import uk.co.traynor.privategallery.novaos2.desktop.DesktopSettings

class WaveBNavigationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule { 920 to 600 }).around(compose)

    @Test fun desktopWorkspaceAndActiveShiftKeepIndependentStateDuringNavigation() {
        val desktop = NovaSystemConfig(compose.activity, "desktop-mode")
        val working = NovaSystemConfig(compose.activity, "working-mode")
        desktop.clear()
        working.clear()
        try {
            val actions = LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
                selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
                setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
                exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> })
            val available = novaSystemFeatures.mapTo(mutableSetOf()) { it.id }
            compose.setContent {
                var selected by remember { mutableStateOf<NovaSystemId?>(null) }
                MaterialTheme {
                    NovaSystemsPanel(novaSystemFeatures, selected, NovaSystemHost(
                        NovaLauncherState(), emptyList(), emptyMap(), actions, true, available,
                        { selected = null }, { id -> (id in available).also { if (it) selected = id } },
                        LauncherNovaSystemsActions({ true }, { false }, { false }, { false }, { false }),
                    ))
                }
            }
            compose.onNodeWithText("Working Mode").performClick()
            val start = compose.onNodeWithTag("start-working-shift")
            compose.waitUntil(10_000) { start.isDisplayed() }
            start.performClick()
            val driving = compose.onNodeWithTag("enter-driving-mode")
            compose.waitUntil(10_000) { driving.isDisplayed() }
            val shiftRecord = working.read()
            compose.onNodeWithContentDescription("Back").performClick()

            compose.onNodeWithText("Nova Desktop").performClick()
            val split = compose.onNodeWithTag("desktop-split")
            compose.waitUntil(10_000) { split.isDisplayed() }
            captureNovaSystem("wave-b-desktop-landscape")
            val gaming = compose.onNodeWithText("Gaming")
            compose.waitUntil(10_000) { gaming.isDisplayed() }
            gaming.performClick()
            compose.waitUntil(10_000) { DesktopSettings.decode(desktop.read()).preset == DesktopPreset.GAMING }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Working Mode").performClick()
            compose.waitUntil(10_000) { driving.isDisplayed() }
            driving.assertIsDisplayed()
            assertEquals(shiftRecord, working.read())
        } finally {
            desktop.clear()
            working.clear()
        }
    }
}
