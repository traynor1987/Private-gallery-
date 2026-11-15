package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class LauncherFolder80UiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { method ->
        if (method.startsWith("inner")) 820 to 920 else 375 to 820
    }

    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    private val catalogue = (1..30).map {
        LauncherApp("example.folder$it/.Main", "example.folder$it", "Delivery app $it")
    }
    private val apps = catalogue.associateBy(LauncherApp::component)
    private val allKeys = catalogue.map(LauncherApp::component)
    private val grid get() = compose.onNodeWithTag("launcher-folder-apps")
    private fun app(index: Int) = compose.onNodeWithContentDescription(catalogue[index].label)

    @Test fun coverGridRetainsEveryAppAndLaunchesTheFinalShortcutAfterScrolling() {
        var launched: String? = null
        compose.setContent {
            LauncherFolderPanel(
                LauncherItem.Folder("Work", allKeys), apps, { true }, emptyMap(), LauncherAppearance(),
                LauncherFolderAppearance(), { launched = it }, {}, null,
            )
        }
        compose.onNodeWithText("Work").assertIsDisplayed()
        val first = app(0).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val third = app(2).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val fourth = app(3).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, third.top, 2f)
        assertTrue(third.left > first.left && fourth.top > first.top)
        grid.performScrollToIndex(catalogue.lastIndex)
        app(catalogue.lastIndex).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(allKeys.last(), launched) }
    }

    @Test fun innerPanelExpandsUsingTheAvailableWidthAndKeepsItsTitle() {
        compose.setContent {
            LauncherFolderPanel(
                LauncherItem.Folder("Work", allKeys.take(8)), apps, { true }, emptyMap(),
                LauncherAppearance(), LauncherFolderAppearance(), {}, {}, null,
            )
        }
        compose.onNodeWithContentDescription("Expand folder").performClick()
        val first = app(0).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val sixth = app(5).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val seventh = app(6).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, sixth.top, 2f)
        assertTrue(sixth.left > first.left && seventh.top > first.top)
        compose.onNodeWithText("Work").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compact folder").assertIsDisplayed()
    }

    @Test fun editsPreserveUnavailableShortcutsAndCommitOnlyExplicitChanges() {
        val unavailable = "example.unavailable/.Main"
        val original = LauncherItem.Folder("Work", allKeys.take(2) + unavailable)
        var model by mutableStateOf(original)
        compose.setContent {
            LauncherFolderPanel(
                model, apps, { true }, emptyMap(), LauncherAppearance(), LauncherFolderAppearance(),
                {}, {}, { model = it; true },
            )
        }
        compose.onNodeWithContentDescription("Edit folder").performClick()
        compose.onNodeWithContentDescription("Remove Delivery app 2").performClick()
        compose.onNodeWithText("Add apps").performClick()
        compose.onNodeWithText("Delivery app 3").performClick()
        compose.onNodeWithContentDescription("Back to editing").performClick()
        compose.onNodeWithText("Folder name").performTextReplacement("Courier apps")
        compose.runOnIdle { assertEquals(original, model) }
        compose.onNodeWithText("Save folder").performClick()
        compose.runOnIdle {
            assertEquals(LauncherItem.Folder("Courier apps", listOf(allKeys[0], unavailable, allKeys[2])), model)
        }
    }

    @Test fun sanctuaryHidesExcludedAppsAndLeavesTheFolderReadOnly() {
        compose.setContent {
            LauncherFolderPanel(
                LauncherItem.Folder("Work", allKeys.take(3)), apps, { it != allKeys[2] },
                mapOf(allKeys[1] to Decision.UNAPPROVED), LauncherAppearance(),
                LauncherFolderAppearance(mode = LauncherFolderMode.LIST, labels = false), {}, {}, null,
            )
        }
        app(0).assertIsDisplayed()
        compose.onNodeWithContentDescription("Delivery app 2, blocked by Sanctuary").assertIsDisplayed()
        app(2).assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit folder").assertDoesNotExist()
    }
}
