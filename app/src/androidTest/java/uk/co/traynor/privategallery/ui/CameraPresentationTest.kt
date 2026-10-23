package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Presentation callbacks only: no camera binding, permissions, vault or owner media. */
class CameraPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordingKeepsStopAvailableAndDisablesCameraChanges() {
        var captures = 0
        compose.setContent {
            PrivateGalleryTheme {
                CameraCaptureControls(
                    video = true, front = false, audio = false, bound = true, busy = true,
                    saving = false, recording = true, flash = false, hasFlash = true,
                    zoom = 0f, status = "Recording", elapsed = 65,
                    onVideoChange = {}, onSwitchCamera = {}, onCapture = { captures++ },
                    onFlash = {}, onZoom = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Stop recording").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Switch camera").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Flash").assertIsNotEnabled()
        compose.onNodeWithText("Photo").assertIsNotEnabled()
        compose.onNodeWithText("Audio Off").assertExists()
        compose.onNodeWithText("REC 01:05").assertExists()
        compose.runOnIdle { assertEquals(1, captures) }
    }

    @Test fun saveAndRecoveryStatusStayReadableAndCaptureDisabledOnNarrowPane() {
        val message = "Capture needs recovery. Retained bytes were not deleted."
        compose.setContent {
            PrivateGalleryTheme {
                Box(Modifier.width(280.dp)) {
                    CameraCaptureControls(
                        video = false, front = true, audio = false, bound = true, busy = true,
                        saving = true, recording = false, flash = false, hasFlash = false,
                        zoom = 0f, status = message, elapsed = 0,
                        onVideoChange = {}, onSwitchCamera = {}, onCapture = {},
                        onFlash = {}, onZoom = {},
                    )
                }
            }
        }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom").assertExists()
        compose.onNodeWithContentDescription("Switch camera").assertIsNotEnabled()
    }
}
