package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherSmartFolderPresentation73UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun expandedFoldersUseTwoColumnsAndUpdateLiveWithoutChangingSlots() {
        val folders = listOf(
            LauncherItem.SmartFolder(LauncherCategory.SUGGESTIONS),
            LauncherItem.SmartFolder(LauncherCategory.RECENTLY_USED),
            LauncherItem.SmartFolder(LauncherCategory.RECENTLY_ADDED),
            LauncherItem.SmartFolder(LauncherCategory.NOVA_OS),
        )
        val item = LauncherApp("example.calendar/.Main", "example.calendar", "Calendar")
        val expanded = mutableStateOf(true)
        val members = mutableStateOf(emptyList<LauncherApp>())
        val opened = mutableListOf<LauncherItem>()
        compose.setContent {
            LauncherHomeGrid(
                folders, 4, LauncherAppearance(), emptyMap(), emptyMap(), { opened += it },
                smartMembers = { if (it == folders.first()) members.value else emptyList() },
                expandedSmartFolders = expanded.value,
            )
        }
        val bounds = compose.onAllNodesWithTag("launcher-expanded-smart-folder", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, bounds.size)
        assertEquals(bounds[0].top, bounds[1].top, .1f)
        assertTrue(bounds[2].top > bounds[0].top)
        compose.runOnIdle { members.value = listOf(item) }
        compose.onNodeWithTag("launcher-expanded-preview-${item.component}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(folders.first().name).performClick()
        compose.runOnIdle {
            assertEquals(listOf<LauncherItem>(folders.first()), opened)
            expanded.value = false
        }
        compose.onAllNodesWithTag("launcher-expanded-smart-folder", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription(folders.first().name).assertIsDisplayed()
    }
}
