package uk.co.traynor.privategallery.launcher

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class LauncherLibrary73UiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { method ->
        if (method.startsWith("cover")) 375 to 850 else 820 to 920
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    private val apps = (1..5).map { index ->
        LauncherApp("example.game$index/.Main", "example.game$index", "Game $index",
            category = ApplicationInfo.CATEGORY_GAME)
    }

    @Test fun coverLaunchesTopIconAndOpensGroupForRemainingApps() {
        var launched: LauncherApp? = null
        var category by mutableStateOf<LauncherCategory?>(null)
        var query by mutableStateOf("")
        var drawer = false
        val social = LauncherApp("example.social/.Main", "example.social", "Social App",
            category = ApplicationInfo.CATEGORY_SOCIAL)
        compose.setContent {
            LauncherLibrary(
                LauncherAppearance(), launcherLibraryGroups(apps + social), category, query,
                onQuery = { query = it }, onCategory = { category = it }, onBack = { category = null },
                onDrawer = { drawer = true }, onApp = { launched = it },
                decisions = mapOf(apps.first().packageName to Decision.UNAPPROVED),
                gestures = LauncherIconGestures(false) { null },
            )
        }

        val first = categoryBounds(LauncherCategory.SOCIAL)
        val second = categoryBounds(LauncherCategory.GAMES)
        assertEquals(first.top, second.top, 0.01f)
        assertTrue(second.left > first.left)
        assertTrue(first.height < second.height)
        compose.onNodeWithTag("launcher-library-category-SUGGESTIONS").assertDoesNotExist()
        compose.onNodeWithTag("launcher-library-category-RECENTLY_USED").assertDoesNotExist()
        saveCheckpoint("cover")
        compose.onNodeWithTag("launcher-app-${apps.first().component}")
            .assert(hasContentDescription("Game 1, blocked by Sanctuary")).performClick()
        compose.runOnIdle { assertEquals(apps.first(), launched) }
        compose.onNodeWithContentDescription("Open Games, 5 apps").performClick()
        compose.onNodeWithText("Games").assertIsDisplayed()
        compose.onNodeWithTag("launcher-app-${apps.last().component}").performClick()
        compose.runOnIdle { assertEquals(apps.last(), launched) }
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag("launcher-library-search").performTextInput("Game 5")
        compose.onNodeWithTag("launcher-app-${apps.first().component}").assertDoesNotExist()
        compose.onNodeWithTag("launcher-app-${apps.last().component}").assertIsDisplayed()
        compose.onNodeWithText("Drawer").performClick()
        compose.runOnIdle { assertTrue(drawer) }
    }

    @Test fun coverShowsPinsAndTrackingStatusWhenRecentsAreOff() {
        compose.setContent {
            LauncherLibrary(
                LauncherAppearance(), launcherLibraryGroups(apps), LauncherCategory.RECENTLY_USED,
                "", {}, {}, {}, {}, {}, emptyMap(), LauncherIconGestures(false) { null },
                recentHistoryEnabled = false, pinnedApps = apps.take(1),
            )
        }
        compose.onNodeWithText("Pinned").assertIsDisplayed()
        compose.onNodeWithTag("launcher-app-${apps.first().component}").assertIsDisplayed()
        compose.onNodeWithText("App tracking is off").assertIsDisplayed()
    }

    @Test fun innerUsesAtLeastThreeColumns() {
        val allApps = apps + LauncherApp("nova.app/.Main", "nova:calculator", "Nova Calculator")
        compose.setContent {
            LauncherLibrary(
                LauncherAppearance(),
                launcherLibraryGroups(allApps, suggestedApps = apps.take(1), recentlyUsed = apps.takeLast(1)),
                null, "", {}, {}, {}, {}, {}, emptyMap(), LauncherIconGestures(false) { null },
            )
        }
        val first = categoryBounds(LauncherCategory.SUGGESTIONS)
        val second = categoryBounds(LauncherCategory.RECENTLY_USED)
        val third = categoryBounds(LauncherCategory.NOVA_OS)
        assertEquals(first.top, third.top, 0.01f)
        assertTrue(second.left > first.left)
        assertTrue(third.left > second.left)
        saveCheckpoint("inner")
    }

    @Test fun coverDragsFromLibraryToHomeAndKeepsTheDrawer() {
        var state by mutableStateOf(NovaLauncherState(configured = true))
        val actions = LauncherUiActions(
            save = { state = it; true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
            selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
            exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> },
        )
        compose.setContent {
            NovaLauncherScreen(state, apps.take(1), emptyList(), emptyMap(), true, false, actions)
        }
        val homePage = compose.onNodeWithTag("launcher-home-pages")
        val destination = homePage.fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("launcher-open-drawer").performClick()
        compose.onNodeWithTag("launcher-drawer-search").assertIsDisplayed()
        compose.onNodeWithTag("launcher-open-library").performClick()
        val tile = compose.onNodeWithTag("launcher-app-${apps.first().component}")
        val source = tile.fetchSemanticsNode().boundsInRoot.center
        tile.performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(destination - source)
        }
        compose.onNodeWithTag("launcher-drag-preview").assertIsDisplayed()
        homePage.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(LauncherItem.App(apps.first().component)), state.cover.pages.single())
        }
    }

    private fun categoryBounds(category: LauncherCategory) =
        compose.onNodeWithTag("launcher-library-category-${category.name}").fetchSemanticsNode().boundsInRoot

    private fun saveCheckpoint(display: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val folder = File(output, "launcher-library-73").apply { mkdirs() }
        val bitmap = compose.onNodeWithTag("launcher-app-library").captureToImage().asAndroidBitmap()
        try {
            File(folder, "$display.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
