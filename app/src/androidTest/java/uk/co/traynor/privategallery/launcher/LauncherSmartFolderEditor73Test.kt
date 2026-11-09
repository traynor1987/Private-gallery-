package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LauncherSmartFolderEditor73Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun editsNameCategoryAndManualAppInclusions() {
        val first = LauncherApp("example.first/.Main", "example.first", "First app")
        val second = LauncherApp("example.second/.Main", "example.second", "Second app")
        val initial = LauncherItem.SmartFolder(LauncherCategory.SUGGESTIONS, included = listOf(first.component))
        var saved: LauncherItem.SmartFolder? = null

        compose.setContent {
            MaterialTheme {
                LauncherSmartFolderEditor(
                    initial, listOf(first, second), emptyMap(), onSave = { saved = it }, onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("launcher-smart-folder-editor").assertIsDisplayed()
        compose.onNodeWithText("Category: Suggestions").performClick()
        compose.onNodeWithText("Recently Used").performClick()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("Folder name").performTextInput("My apps")
        compose.onNodeWithText("Save folder").performClick()

        compose.runOnIdle {
            assertEquals(
                LauncherItem.SmartFolder(LauncherCategory.RECENTLY_USED, "My apps", listOf(second.component)),
                saved,
            )
        }
    }
}
