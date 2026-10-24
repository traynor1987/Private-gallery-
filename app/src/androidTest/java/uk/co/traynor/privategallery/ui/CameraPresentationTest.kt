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

    @Test fun textBoundsCheckRejectsClippingAndTruncationButAcceptsVisibleText() {
        compose.setContent {
            PrivateGalleryTheme {
                Column(Modifier.width(240.dp)) {
                    Text("Fully visible", Modifier.testTag("text-visible"))
                    Text("This line cannot fit", Modifier.width(48.dp).testTag("text-width"), softWrap = false)
                    Text("Clipped height", Modifier.height(1.dp).testTag("text-height"))
                    Text("This paragraph needs several lines", Modifier.width(48.dp).testTag("text-max-lines"), maxLines = 1)
                    Text("This paragraph needs several lines", Modifier.width(48.dp).testTag("text-ellipsis"),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        fun clipping(tag: String): List<String> {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals("Expected one text layout for $tag", 1, results.size)
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            val result = results.single()
            val clipping = cameraTextClipping(result, node.size)
            println("Camera negative control $tag: node=${node.size} layout=${result.size} constraints=${result.layoutInput.constraints} clipping=$clipping")
            return clipping
        }
        assertTrue("Visible text must pass even when its paragraph is wider than the text node", clipping("text-visible").isEmpty())
        assertTrue("The checker must reject an unwrapped line wider than its node", clipping("text-width").any { it.contains("width") })
        assertTrue("The checker must reject a line taller than its node", clipping("text-height").any { it.contains("height") })
        assertTrue("The checker must reject content removed by maxLines", clipping("text-max-lines").contains("maxLines exceeded"))
        assertTrue("The checker must reject ellipsized content", clipping("text-ellipsis").any { it.contains("ellipsized") })
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
                        CameraViewfinderLayout(Modifier.fillMaxSize(),
                            preview = { SyntheticPreview(Modifier.fillMaxSize()) }, controls = panel)
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
            compose.onNodeWithText("4K", substring = false).assertDoesNotExist()
            compose.onNodeWithText("Open camera to discover supported resolutions.").performScrollTo().assertIsDisplayed()
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
        val folder = File(output, "release171").apply { mkdirs() }
        // AndroidX enables rendering on ATD; all visible contents are synthetic test-owned UI.
        androidx.test.core.app.takeScreenshot().let { bitmap ->
            File(folder, "camera-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        // Keep screenshots and exact metrics even when a later assertion fails.
        val metrics = mutableListOf<String>()
        val overflows = mutableListOf<String>()
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        compose.runOnIdle {
            nodes.forEach { node ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result ->
                    val detail = "text=${result.layoutInput.text.text} size=${result.size} paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}" +
                        " overflowWidth=${result.didOverflowWidth} overflowHeight=${result.didOverflowHeight}" +
                        " maxLinesExceeded=${result.multiParagraph.didExceedMaxLines} density=${result.layoutInput.density}" +
                        " constraints=${result.layoutInput.constraints} lines=" + (0 until result.lineCount).joinToString { line ->
                            "[$line: left=${result.getLineLeft(line)}, right=${result.getLineRight(line)}, top=${result.getLineTop(line)}, bottom=${result.getLineBottom(line)}]"
                        }
                    val clipping = cameraTextClipping(result, node.size)
                    metrics += "$detail node=${node.size} clipping=$clipping"
                    if (clipping.isNotEmpty()) overflows += "$detail clipping=$clipping"
                }
            }
        }
        File(folder, "camera-$name-layout.txt").writeText(metrics.joinToString("\n"))
        assertTrue("Clipped text:\n${overflows.joinToString("\n")}", overflows.isEmpty())
    }

}


@Composable
private fun CameraViewport(width: Int, height: Int, theme: AppTheme, fontScale: Float, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val original = LocalDensity.current
        // CI provides a 1200 x 1800dp display. Keep its native density: scaling every
        // fixture to fill that display introduced non-device fractional text densities.
        check(maxWidth >= width.dp && maxHeight >= height.dp) {
            "Camera viewport ${width}x${height}dp needs a larger test display; available ${maxWidth}x${maxHeight}"
        }
        CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale)) {
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

/** GetTextLayoutResult may retain the full paragraph constraint width after a Text node
 * shrinks to its content. Inspect the rendered line extents, not paragraph width. */
private fun cameraTextClipping(
    semanticsResult: androidx.compose.ui.text.TextLayoutResult,
    nodeSize: androidx.compose.ui.unit.IntSize,
): List<String> = buildList {
    // Plain String Text semantics reconstructs a MultiParagraph directly from the
    // constraints, bypassing TextMeasurer's softWrap=false width handling. Re-measure
    // with every original layout input so the checker examines the actual line shape.
    val input = semanticsResult.layoutInput
    val result = androidx.compose.ui.text.TextMeasurer(
        input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0,
    ).measure(
        text = input.text, style = input.style, overflow = input.overflow,
        softWrap = input.softWrap, maxLines = input.maxLines,
        placeholders = input.placeholders, constraints = input.constraints,
        layoutDirection = input.layoutDirection, density = input.density,
        fontFamilyResolver = input.fontFamilyResolver, skipCache = true,
    )
    // The actual un-clipped layout node remains the drawing boundary.
    val width = minOf(result.size.width, nodeSize.width).toFloat()
    val height = minOf(result.size.height, nodeSize.height).toFloat()
    if (result.multiParagraph.didExceedMaxLines) add("maxLines exceeded")
    for (line in 0 until result.lineCount) {
        if (result.isLineEllipsized(line)) add("line $line ellipsized")
        if (result.getLineLeft(line) < 0f || result.getLineRight(line) > width) {
            add("line $line exceeds width")
        }
        if (result.getLineTop(line) < 0f || result.getLineBottom(line) > height) {
            add("line $line exceeds height")
        }
    }
}
