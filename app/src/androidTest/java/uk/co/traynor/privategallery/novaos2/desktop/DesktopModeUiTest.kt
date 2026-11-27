package uk.co.traynor.privategallery.novaos2.desktop

import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import uk.co.traynor.privategallery.novaos2.NovaSystemsPanel
import uk.co.traynor.privategallery.novaos2.novaSystemFeatures
import uk.co.traynor.privategallery.launcher.LauncherApp
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

    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("cover")) 375 to 850 else 820 to 920
    }).around(compose)

    @Test fun innerSplitPersistsAcrossCoverTransitionAndUsesNativeAppRoute() {
        val persisted = NovaSystemConfig(compose.activity, "desktop-mode")
        persisted.clear()
        var wide by mutableStateOf(true)
        var opened: NovaCoreApp? = null
        try {
            compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
            compose.setContent {
                PrivateGalleryTheme(AppTheme.LIGHT) {
                    Surface(Modifier.width(if (wide) 820.dp else 375.dp)) {
                        NovaSystemsPanel(novaSystemFeatures, NovaSystemId.DESKTOP, desktopHost(wide) { opened = it })
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
            compose.onNodeWithText("Nova Notes").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(NovaCoreApp.NOTES, opened) }
            compose.runOnIdle { wide = true }
            compose.onNodeWithText("55 / 45").performScrollTo().assertIsDisplayed()
        } finally { persisted.clear() }
    }

    @Test fun coverKeyboardShortcutSelectsGamingInTheProductionShell() {
        val config = NovaSystemConfig(compose.activity, "desktop-mode")
        config.clear()
        try {
            compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
            compose.setContent {
                PrivateGalleryTheme(AppTheme.DARK) {
                    NovaSystemsPanel(novaSystemFeatures, NovaSystemId.DESKTOP, desktopHost(false) {})
                }
            }
            val compact = compose.onNodeWithTag("desktop-compact")
            compose.waitUntil(10_000) { compact.isDisplayed() }
            compose.runOnIdle {
                val now = android.os.SystemClock.uptimeMillis()
                for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                    compose.activity.dispatchKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_3, 0, KeyEvent.META_ALT_ON))
                }
            }
            compose.waitUntil(10_000) { DesktopSettings.decode(config.read()).preset == DesktopPreset.GAMING }
            compose.onNodeWithText("Pin app").performScrollTo().assertIsDisplayed()
            captureNovaSystem("desktop-cover-compact")
        } finally { config.clear() }
    }

    @Test fun AndroidPlacementUsesLauncherIntentAndDeniesUnavailableOrPrivateTargets() {
        val component = ComponentName("example.desktop", "example.desktop.Main")
        val adjacent = desktopLauncherIntent(component, adjacent = true)
        assertEquals(component, adjacent.component)
        assertTrue(adjacent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(Intent.ACTION_MAIN, adjacent.action)
        assertTrue(adjacent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(adjacent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
        assertFalse(desktopLauncherIntent(component, adjacent = false).flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
        val activity = compose.activity
        val synthetic = LauncherApp(component.flattenToString(), component.packageName, "Unavailable")
        assertFalse(activity.startDesktopApp(synthetic, DesktopPlacement.Adjacent))
        assertFalse(activity.startDesktopApp(synthetic.copy(packageName = activity.packageName), DesktopPlacement.External(9)))
    }

    @Test fun taskbarChecksForegroundAndReportsRejectedPlacement() {
        val app = LauncherApp("demo.desktop/.Viewer", "demo.desktop", "Pinned viewer")
        var active = true
        var requests = 0
        val host = desktopHost(wide = true, apps = listOf(app), foreground = { active }, placed = { target, placement ->
            assertEquals(app, target)
            assertEquals(DesktopPlacement.Adjacent, placement)
            requests++
            false
        }) { }
        compose.setContent { MaterialTheme { NovaDesktopMode(host) } }
        val beside = compose.onNodeWithText("Open beside Nova")
        compose.waitUntil(10_000) { beside.isDisplayed() }
        beside.performClick()
        compose.onNodeWithText("Pinned viewer").performClick()
        compose.onNodeWithText("Could not open Pinned viewer in that location.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, requests)
            active = false
            assertFalse(host.launchPlaced(app, DesktopPlacement.Adjacent))
            assertEquals(1, requests)
        }
    }

    @Test fun savedPresetAndCoverPanelSurviveStateRestoration() {
        val config = NovaSystemConfig(compose.activity, "desktop-mode")
        config.clear()
        try {
            val restore = StateRestorationTester(compose)
            restore.setContent {
                MaterialTheme { NovaDesktopMode(desktopHost(false) {}) }
            }
            val gaming = compose.onNodeWithText("Gaming")
            compose.waitUntil(10_000) { gaming.isDisplayed() }
            gaming.performClick()
            compose.waitUntil(10_000) { DesktopSettings.decode(config.read()).preset == DesktopPreset.GAMING }
            compose.onNodeWithText("Nova apps").performClick()
            val notes = compose.onNodeWithText("Nova Notes")
            notes.performScrollTo().assertIsDisplayed()
            restore.emulateSavedInstanceStateRestore()
            compose.waitUntil(10_000) { compose.onNodeWithText("Gaming").isDisplayed() }
            notes.performScrollTo().assertIsDisplayed()
        } finally { config.clear() }
    }

    private fun desktopHost(
        wide: Boolean,
        apps: List<LauncherApp> = emptyList(),
        foreground: () -> Boolean = { true },
        placed: (LauncherApp, DesktopPlacement) -> Boolean = { _, _ -> false },
        openApp: (NovaCoreApp) -> Unit,
    ) = NovaSystemHost(
        NovaLauncherState(), apps, emptyMap(),
        LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
            selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
            exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> }),
        wide = wide, available = setOf(NovaSystemId.DESKTOP), onBack = {}, open = { false },
        platform = LauncherNovaSystemsActions(foreground, { false }, { app -> openApp(app); true }, { false }, { false }, placed),
    )
}
