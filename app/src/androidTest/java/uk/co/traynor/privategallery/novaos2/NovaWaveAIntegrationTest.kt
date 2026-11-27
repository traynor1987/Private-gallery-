package uk.co.traynor.privategallery.novaos2

import android.os.Debug
import android.os.SystemClock
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.launcher.LauncherItem
import uk.co.traynor.privategallery.launcher.LauncherLayout
import uk.co.traynor.privategallery.launcher.LauncherLook
import uk.co.traynor.privategallery.launcher.LauncherTheme
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherScreen
import uk.co.traynor.privategallery.launcher.NovaLauncherState
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaWaveAIntegrationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("cover")) 375 to 850 else 820 to 920
    }).around(compose)

    private val fixtureApps = listOf(
        LauncherApp("example.notes/.Main", "example.notes", "Notes"),
        LauncherApp("example.maps/.Main", "example.maps", "Maps"),
    )
    private val placement = LauncherLayout(
        pages = listOf(listOf(LauncherItem.Folder("Everyday", fixtureApps.map { it.component })),
            listOf(LauncherItem.Workspace)),
        dock = listOf(LauncherItem.App(fixtureApps.last().component)),
    )
    private val initial = NovaLauncherState(configured = true, separateDisplays = true, cover = placement, inner = placement)

    @Test fun coverSwipeAndThemeContrastRespectSanctuary() = withDefaultControls {
        val protected = mutableStateOf(false)
        val launcher = mutableStateOf(initial.copy(appearance = initial.appearance.copy(theme = LauncherTheme.LIGHT)))
        show(mutableStateOf(375), launcher, protected)
        assertEquals(setOf(NovaSystemId.THEMES, NovaSystemId.CONTROL), novaSystemFeatures.map { it.id }.toSet())

        val inputStart = SystemClock.elapsedRealtime()
        compose.onNodeWithTag("launcher-control-gesture").performTouchInput {
            swipe(Offset(width * .37f, height * .2f), Offset(width * .37f, height + 110f), durationMillis = 260)
        }
        awaitControl(wide = false)
        sample("cover_swipe_to_control_ms", SystemClock.elapsedRealtime() - inputStart)
        sample("cover_control_debug_proportional_kib", Debug.getPss())
        assertBarIcons(darkIcons = true)
        captureControlGesture("wave-a-control-cover-swiped")

        compose.runOnIdle {
            launcher.value = launcher.value.copy(appearance = launcher.value.appearance.copy(theme = LauncherTheme.DARK))
        }
        awaitControl(wide = false)
        assertBarIcons(darkIcons = false)
        compose.runOnIdle { protected.value = true }
        compose.onNodeWithText("Sanctuary active").assertIsDisplayed()
        compose.onAllNodesWithTag("novaos2-panel").assertCountEquals(0)
        compose.onNodeWithContentDescription("Open Control Centre").assertDoesNotExist()
        compose.runOnIdle { assertEquals(placement, launcher.value.cover) }
    }

    @Test fun innerControlSurvivesWidthChangeAndOpensRegisteredThemes() = withDefaultControls {
        val width = mutableStateOf(820)
        val light = initial.copy(appearance = initial.appearance.copy(theme = LauncherTheme.LIGHT))
        val launcher = mutableStateOf(light)
        show(width, launcher, mutableStateOf(false))
        val inputStart = SystemClock.elapsedRealtime()
        compose.onNodeWithContentDescription("Open Control Centre").performClick()
        awaitControl(wide = true)
        sample("inner_tap_to_control_ms", SystemClock.elapsedRealtime() - inputStart)
        captureControlGesture("wave-a-control-inner-from-home")

        compose.runOnIdle { width.value = 375 }
        awaitControl(wide = false)
        compose.onAllNodesWithTag("novaos2-panel").assertCountEquals(1)
        compose.runOnIdle { width.value = 820 }
        awaitControl(wide = true)
        compose.onAllNodesWithTag("novaos2-panel").assertCountEquals(1)
        compose.onNodeWithTag("control-tile-theme").performScrollTo().performClick()
        compose.onNodeWithText("Themes Studio").assertIsDisplayed()
        compose.onNodeWithText("One UI-inspired").performScrollTo().performClick()
        val apply = compose.onNodeWithText("Apply theme").performScrollTo()
        val applyStart = SystemClock.elapsedRealtime()
        apply.performClick()
        compose.onNodeWithText("Theme applied.").performScrollTo().assertIsDisplayed()
        sample("inner_apply_theme_ms", SystemClock.elapsedRealtime() - applyStart)
        assertBarIcons(darkIcons = true)
        compose.onNodeWithText("Appearance preset").performScrollTo()
        captureNovaSystem("wave-a-studio-inner-from-home")
        compose.runOnIdle {
            assertEquals(LauncherLook.ROUNDED, launcher.value.appearance.studio.look)
            assertEquals(light, launcher.value.copy(appearance = light.appearance))
        }
    }

    private fun awaitControl(wide: Boolean) {
        val layout = if (wide) "inner" else "cover"
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("control-centre-$layout").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("control-tile-internet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("control-centre-$layout").assertIsDisplayed()
    }

    private fun assertBarIcons(darkIcons: Boolean) {
        compose.runOnIdle {
            val window = compose.activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(darkIcons, controller.isAppearanceLightStatusBars)
            assertEquals(darkIcons, controller.isAppearanceLightNavigationBars)
        }
    }

    private fun captureControlGesture(name: String) {
        val bottomInset = compose.runOnIdle {
            val insets = checkNotNull(compose.activity.window.decorView.rootWindowInsets)
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        }
        check(bottomInset > 0) { "The visible Android navigation inset is required for contrast evidence" }
        captureNovaSystem(name) { image ->
            (image.height - bottomInset until image.height).maxOf { y ->
                ColorUtils.calculateContrast(image.getPixel(image.width / 2, y), image.getPixel(image.width / 4, y))
            }
        }
    }

    private fun show(width: MutableState<Int>, launcher: MutableState<NovaLauncherState>, protected: MutableState<Boolean>) {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        val actions = LauncherUiActions(save = { launcher.value = it; true }, launchApp = {}, launchShortcut = {},
            openWorkspace = {}, selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {}, exportLayout = {},
            importLayout = {}, renderWidget = { _, _ -> })
        val platforms = LauncherNovaSystemsActions({ !protected.value }, { false }, { false }, { false }, { false })
        val decisions = fixtureApps.associate { it.component to Decision.APPROVED }
        compose.setContent {
            Box(Modifier.width(width.value.dp)) {
                NovaLauncherScreen(launcher.value, fixtureApps, emptyList(), decisions, true, false, actions,
                    launcherProtected = protected.value, novaSystems = platforms)
            }
        }
    }

    private fun withDefaultControls(test: () -> Unit) {
        check(compose.activity.packageName.endsWith(".hiddenprototype"))
        val config = NovaSystemConfig(compose.activity, "control-centre")
        val previous = config.read()
        try {
            config.clear()
            test()
        } finally {
            if (previous == null) config.clear() else config.write(previous)
        }
    }

    private fun sample(label: String, value: Long) {
        File(compose.activity.cacheDir, "novaos2-wave-a-timings.txt").appendText("$label=$value\n")
    }
}
