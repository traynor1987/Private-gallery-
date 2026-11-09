package uk.co.traynor.privategallery.launcher

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Process
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
        val productivity = (1..2).map { index ->
            LauncherApp("example.productivity$index/.Main", "example.productivity$index", "Productivity $index",
                category = ApplicationInfo.CATEGORY_PRODUCTIVITY)
        }
        val utilities = (1..4).map { index ->
            LauncherApp("example.utility$index/.Main", "example.utility$index", "Utility $index",
                category = ApplicationInfo.CATEGORY_MAPS)
        }
        compose.setContent {
            LauncherLibrary(
                LauncherAppearance(), launcherLibraryGroups(apps + social + productivity + utilities), category, query,
                onQuery = { query = it }, onCategory = { category = it }, onBack = { category = null },
                onDrawer = { drawer = true }, onApp = { launched = it },
                decisions = mapOf(apps.first().packageName to Decision.UNAPPROVED),
                gestures = LauncherIconGestures(false) { null },
            )
        }

        val first = categoryBounds(LauncherCategory.SOCIAL)
        val second = categoryBounds(LauncherCategory.PRODUCTIVITY)
        assertEquals(first.top, second.top, 0.01f)
        assertTrue(second.left > first.left)
        listOf(second, categoryBounds(LauncherCategory.GAMES), categoryBounds(LauncherCategory.UTILITIES)).forEach {
            assertEquals(first.height, it.height, 0.01f)
            assertEquals(first.width, it.width, 0.01f)
        }
        val loneAppBounds = compose.onNodeWithTag("launcher-app-${social.component}").fetchSemanticsNode().boundsInRoot
        val groupedAppBounds = compose.onNodeWithTag("launcher-app-${apps.first().component}").fetchSemanticsNode().boundsInRoot
        assertTrue(loneAppBounds.width > groupedAppBounds.width)
        compose.onNodeWithTag("launcher-library-category-SUGGESTIONS").assertDoesNotExist()
        compose.onNodeWithTag("launcher-library-category-RECENTLY_USED").assertDoesNotExist()
        saveCheckpoint("cover")
        compose.onNodeWithTag("launcher-app-${apps.first().component}")
            .assert(hasContentDescription("Game 1, blocked by Sanctuary")).performClick()
        compose.runOnIdle { assertEquals(apps.first(), launched) }
        compose.onNodeWithContentDescription("See all Games apps").performClick()
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

    @Test fun coverFocusedSearchShowsAlphabeticalListAndJumpsByLetter() {
        val zed = LauncherApp("example.zed/.Main", "example.zed", "Zed")
        val clone = zed.copy(
            component = "${zed.component}#42",
            profile = LauncherProfile(Process.myUserHandle(), 42, "Dual Messenger"),
        )
        val apps = (1..45).map { index ->
            LauncherApp("example.app$index/.Main", "example.app$index", "Alpha $index")
        } + listOf(zed, clone)
        var query by mutableStateOf("")
        var launched: LauncherApp? = null
        compose.setContent {
            LauncherLibrary(
                LauncherAppearance(), launcherLibraryGroups(apps), null, query,
                { query = it }, {}, {}, {}, { launched = it }, emptyMap(), LauncherIconGestures(false) { null },
            )
        }

        compose.onNodeWithTag("launcher-library-search").performClick()
        compose.onNodeWithTag("launcher-library-alphabetical-list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Jump to Z").performClick()
        compose.waitUntil(5_000) { compose.onNodeWithTag("launcher-app-${zed.component}").isDisplayed() }
        compose.onNodeWithTag("launcher-library-search").performTextInput("Zed")
        compose.onNodeWithTag("launcher-app-${apps.first().component}").assertDoesNotExist()
        compose.onNodeWithTag("launcher-app-${zed.component}").assertIsDisplayed()
        compose.onNodeWithTag("launcher-app-${clone.component}")
            .assert(hasContentDescription("Zed, Dual Messenger")).performClick()
        compose.runOnIdle { assertEquals(clone, launched) }
    }

    @Test fun coverExpandedOtherCategoryRendersLargeCollectionLazily() {
        val apps = (1..150).map { index ->
            LauncherApp("example.app$index/.Main", "example.app$index", "Unknown $index")
        }
        compose.setContent {
            LauncherLibrary(
                LauncherAppearance(), launcherLibraryGroups(apps), LauncherCategory.OTHER, "",
                {}, {}, {}, {}, {}, emptyMap(), LauncherIconGestures(false) { null },
            )
        }

        val last = launcherLibraryMatches(apps, "").last()
        compose.onNodeWithTag("launcher-app-${last.component}").assertDoesNotExist()
        compose.onNodeWithTag("launcher-library-category-apps").performScrollToIndex(apps.lastIndex)
        compose.onNodeWithTag("launcher-app-${last.component}").assertIsDisplayed()
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
