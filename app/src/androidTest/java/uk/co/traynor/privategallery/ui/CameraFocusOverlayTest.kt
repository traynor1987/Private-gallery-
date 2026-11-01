package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CameraFocusOverlayTest {
    @get:Rule val compose=createComposeRule()
    @Test fun requestedPointUsesPreviewCoordinatesAndFeedbackExpires() {
        compose.mainClock.autoAdvance=false
        var point by mutableStateOf<Offset?>(Offset(.75f,.25f))
        var message by mutableStateOf("Focus not confirmed")
        compose.setContent {
            PrivateGalleryTheme {
                Box(Modifier.requiredSize(320.dp,400.dp)) {
                    CameraFocusOverlay(point,message) {point=null;message=""}
                }
            }
        }
        val area=compose.onNodeWithTag("camera-focus-overlay").fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag("camera-focus-target").fetchSemanticsNode().boundsInRoot
        assertEquals(area.left+area.width*.75f,target.center.x,1f)
        assertEquals(area.top+area.height*.25f,target.center.y,1f)
        compose.onNodeWithText("Focus not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Focus confirmed").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3100)
        compose.waitForIdle()
        compose.onNodeWithTag("camera-focus-target").assertDoesNotExist()
        compose.onNodeWithText("Focus not confirmed").assertDoesNotExist()
    }
}
