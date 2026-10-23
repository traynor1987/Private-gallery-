package uk.co.traynor.privategallery.ui

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import uk.co.traynor.privategallery.core.camera.VaultCameraController
import uk.co.traynor.privategallery.core.camera.VaultCaptureAccess
import uk.co.traynor.privategallery.core.ui.AppTheme
import java.io.File
import java.util.UUID
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun coverPortraitLight() = viewport(392, 840, AppTheme.LIGHT)
    @Test fun innerPortraitDark() = viewport(840, 900, AppTheme.DARK)
    @Test fun coverLandscapeDarkLargeText() = viewport(840, 392, AppTheme.DARK, 1.3f)
    @Test fun innerLandscapeLightLargeText() = viewport(900, 840, AppTheme.LIGHT, 1.3f)

    private fun viewport(width: Int, height: Int, theme: AppTheme, fontScale: Float = 1f) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "camera-presentation-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext() = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val access = VaultCaptureAccess(context, null, { ByteArray(32) { 4 } }, { true })
        val controller = VaultCameraController(context, access)
        var controls by mutableStateOf(false)
        var video by mutableStateOf(true)
        var front by mutableStateOf(false)
        var flash by mutableStateOf(false)
        var zoom by mutableFloatStateOf(0f)
        var captures = 0
        val name = "$width-$height-$theme-$fontScale"
        try {
            compose.setContent {
                CameraViewport(width, height, theme, fontScale) {
                    if (!controls) VaultCameraScreen(controller, onExit = {})
                    else {
                        // A synthetic preview avoids requesting permission or binding camera hardware.
                        // Both layouts use the production controls and their actual panel dimensions.
                        val panel: @Composable () -> Unit = {
                            CameraCaptureControls(
                                video = video, front = front, audio = false, bound = true, busy = false,
                                saving = false, recording = false, flash = flash, hasFlash = true,
                                zoom = zoom, status = "Saved encrypted", elapsed = 0,
                                onVideoChange = { video = it }, onSwitchCamera = { front = !front },
                                onCapture = { captures++ }, onFlash = { flash = !flash }, onZoom = { zoom = it },
                            )
                        }
                        if (width >= 480 && width > height) Row(Modifier.fillMaxSize()) {
                            SyntheticPreview(Modifier.weight(1f).fillMaxHeight())
                            Column(Modifier.width(248.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { panel() }
                        } else Column(Modifier.fillMaxSize()) {
                            SyntheticPreview(Modifier.weight(1f).fillMaxWidth())
                            Column(Modifier.fillMaxWidth().heightIn(max = height.dp * 0.6f).verticalScroll(rememberScrollState())) { panel() }
                        }
                    }
                }
            }
            compose.onNodeWithText("Vault Camera").assertIsDisplayed()
            capture("entry-photo-$name")
            compose.onNodeWithText("Black-Screen Camera").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Video", substring = false).performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("No preview.", substring = true).performScrollTo().assertIsDisplayed()
            capture("entry-black-video-$name")
            compose.onNodeWithContentDescription("Record audio").performScrollTo().assertIsDisplayed().assertIsOff()
            compose.onNodeWithText("4K", substring = false).performScrollTo().performClick().assertIsSelected()
            capture("entry-video-settings-$name")
            compose.onNodeWithContentDescription("Capture vibration").performScrollTo().performClick().assertIsOn()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Open camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open camera").performScrollTo().assertIsDisplayed().assertIsEnabled()
            capture("entry-open-$name")
            compose.onNodeWithText("Recover interrupted captures").performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("Recording details").performScrollTo().assertIsDisplayed()
            // Open camera is intentionally never clicked: entry screenshots need no permissions.
            compose.runOnIdle { assertTrue(!controller.entered); assertTrue(access.journal.entries().isEmpty()); controls = true }
            compose.onNodeWithText("Saved encrypted").performScrollTo().assertIsDisplayed()
            capture("controls-status-$name")
            val shutter = compose.onNodeWithContentDescription("Start recording")
            shutter.performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            assertFullyInsideViewport(shutter)
            compose.onNodeWithContentDescription("Switch camera").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Flash").performScrollTo().assertIsDisplayed().performClick()
            capture("controls-actions-$name")
            compose.onNodeWithContentDescription("Zoom").performScrollTo().assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.5f) }
            compose.onNodeWithText("Photo", substring = false).performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Take photo").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(2, captures)
                assertTrue(front)
                assertTrue(flash)
                assertEquals(0.5f, zoom, 0.001f)
            }
        } finally {
            compose.runOnIdle { controller.revoke() }
            root.deleteRecursively()
        }
    }

    private fun assertFullyInsideViewport(node: SemanticsNodeInteraction) {
        val viewport = compose.onNodeWithTag("camera-viewport").fetchSemanticsNode().boundsInRoot
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("Control must fit horizontally", bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        assertTrue("Control must fit vertically after scrolling", bounds.top >= viewport.top - 1 && bounds.bottom <= viewport.bottom + 1)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Text can wrap, but no component should silently truncate its own label.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result -> assertTrue("Clipped text: ${result.layoutInput.text.text}", !result.hasVisualOverflow) }
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
        val folder = File(output, "release171").apply { mkdirs() }
        // AndroidX enables rendering on ATD; all visible contents are synthetic test-owned UI.
        androidx.test.core.app.takeScreenshot().let { bitmap ->
            File(folder, "camera-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

}


@Composable
private fun CameraViewport(width: Int, height: Int, theme: AppTheme, fontScale: Float, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val original = LocalDensity.current
        // Fit the whole requested logical viewport on the emulator. requiredSize alone would
        // extend inner-display and landscape fixtures outside a portrait emulator's screenshot.
        val density = original.density * minOf(maxWidth.value / width, maxHeight.value / height)
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            PrivateGalleryTheme(theme) {
                Surface(Modifier.requiredSize(width.dp, height.dp).testTag("camera-viewport"), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

@Composable
private fun SyntheticPreview(modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(contentAlignment = Alignment.Center) { Text("Synthetic camera preview", style = MaterialTheme.typography.labelSmall) }
    }
}
