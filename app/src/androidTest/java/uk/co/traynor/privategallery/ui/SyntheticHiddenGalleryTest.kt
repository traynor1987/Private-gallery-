package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.BuildConfig

class SyntheticHiddenGalleryTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun syntheticPhotoImportViewingFavouritesCollectionsAndReset() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".hiddenprototype"))
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme{if(shown.value) SyntheticHiddenGalleryDialog{shown.value=false}}}
        compose.onNodeWithText("TEST ONLY — INSECURE — SYNTHETIC MEDIA").assertIsDisplayed()
        compose.onNodeWithText("Import test photo").performClick()
        compose.onNodeWithText("Hidden items: 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open test photo").performClick()
        compose.onNodeWithContentDescription("Synthetic photo viewer").assertIsDisplayed()
        compose.onNodeWithText("Back to Hidden gallery").performClick()
        compose.onNodeWithText("Favourite",substring=false).performClick()
        compose.onNodeWithText("Favourites",substring=false).performClick()
        compose.onNodeWithText("Hidden items: 1").assertIsDisplayed()
        compose.onNodeWithText("All",substring=false).performClick()
        compose.onNodeWithText("Test collection name").performTextInput("Demo")
        compose.onNodeWithText("Create collection").performClick()
        compose.onNodeWithText("All",substring=false).performClick()
        compose.onNodeWithText("Move to Demo").performScrollTo().performClick()
        compose.onNodeWithText("Demo",substring=false).performClick()
        compose.onNodeWithText("Hidden items: 1").assertIsDisplayed()
        compose.onNodeWithText("Reset test data").performClick()
        compose.onNodeWithText("Hidden items: 0").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle{assertFalse(shown.value)}
    }
    @Test fun syntheticVideoImportsAndReachesDecoderReady() {
        compose.setContent {MaterialTheme{SyntheticHiddenGalleryDialog{}}}
        compose.onNodeWithText("Import test video").performClick()
        compose.onNodeWithContentDescription("Open test video").performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("Synthetic video ready").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Back to Hidden gallery").performClick()
        compose.onNodeWithText("Hidden items: 1").assertIsDisplayed()
    }
    @Test fun backgroundClosesAndReopeningHasNoDisposableState() {
        val shown=mutableStateOf(true)
        compose.setContent {MaterialTheme{if(shown.value) SyntheticHiddenGalleryDialog{shown.value=false}}}
        compose.onNodeWithText("Import test photo").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(shown.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle{shown.value=true}
        compose.onNodeWithText("Hidden items: 0").assertIsDisplayed()
    }
}
