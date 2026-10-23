package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asAndroidBitmap
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
    @Test fun horizontalAndVerticalDragsDoNotCapture() {
        var captures = 0
        compose.setContent { PrivateGalleryTheme {
            ReadingCameraView(false, false, false, true, "Ready", 0, false, { captures++ }, {})
        } }
        val area=compose.onNodeWithContentDescription("Take photo from reading area").performScrollTo()
        area.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0,captures) }
        area.performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0,captures) }
        area.performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,captures) }
    }
    @Test fun readingCoverPortrait() = readingViewport(392,840)
    @Test fun readingCoverLandscape() = readingViewport(840,392)
    @Test fun readingInnerPortrait() = readingViewport(840,900)
    @Test fun readingInnerLandscape() = readingViewport(900,840)
    private fun readingViewport(width:Int,height:Int) {
        var captures=0
        compose.setContent { PrivateGalleryTheme {
            CompositionLocalProvider(LocalDensity provides Density(1f,1.3f)) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopStart) {
                    Box(Modifier.requiredSize(width.dp,height.dp).testTag("reading-viewport")) {
                        ReadingCameraView(true,false,false,true,"Ready",0,false,{captures++},{})
                    }
                }
            }
        } }
        compose.onNodeWithContentDescription("Exit Reading View Camera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Start recording").assertIsDisplayed().performClick()
        val bounds=compose.onNodeWithTag("reading-viewport").fetchSemanticsNode().boundsInRoot
        for(label in listOf("Exit Reading View Camera","Start recording")) {
            val node=compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(node.left>=bounds.left && node.right<=bounds.right && node.top>=bounds.top && node.bottom<=bounds.bottom)
        }
        compose.runOnIdle {assertEquals(1,captures)}
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val output=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
        val folder=java.io.File(output,"release172").apply {mkdirs()}
        compose.onNodeWithTag("reading-viewport").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(folder,"reading-$width-$height.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

}
