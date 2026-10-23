package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReadingCameraViewTest {
    @get:Rule val compose = createComposeRule()
    @Test fun photoUsesOnlyDesignatedAreaAndExitIsAccessible() {
        var captures = 0; var exits = 0
        compose.setContent { PrivateGalleryTheme {
            ReadingCameraView(false, false, false, true, "Ready", 0, false, { captures++ }, { exits++ })
        } }
        compose.onNodeWithText("A quieter way to spend an afternoon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Take photo from reading area").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exit Reading View Camera").performClick()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(1, exits) }
    }
    @Test fun videoHasIntentionalStopAndDisabledInputsDuringFinalisation() {
        var recording by mutableStateOf(true); var saving by mutableStateOf(false); var captures = 0
        compose.setContent { PrivateGalleryTheme {
            ReadingCameraView(true, recording, saving, true, "Recording", 65, true, { captures++ }, {})
        } }
        compose.onNodeWithContentDescription("Stop recording").assertIsEnabled().performClick()
        compose.onNodeWithText("REC 01:05 · Audio On").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, captures); recording = false; saving = true }
        compose.onNodeWithContentDescription("Start recording").assertIsNotEnabled()
    }
}
