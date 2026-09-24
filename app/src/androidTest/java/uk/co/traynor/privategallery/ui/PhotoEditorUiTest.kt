package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.MediaViewerSource
import uk.co.traynor.privategallery.core.editor.PhotoRenderer

class PhotoEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun bytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        return try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
    }
    @Test fun viewerEditEntrySaveCopyAndUnconfiguredAi() {
        val original = bytes()
        var saves = 0
        compose.setContent { PrivateGalleryTheme {
            FullscreenMediaViewer(listOf(ViewerMediaEntry("selected", "image/png")), MediaViewerSource.VAULT, 0, {},
                onLoadProtectedBytes = { id, loaded -> assertEquals("selected", id); loaded(Result.success(original.copyOf())) },
                onSaveEditedCopy = { id, result, cancelled, completed ->
                    assertEquals("selected", id); assertFalse(cancelled()); assertTrue(result.isNotEmpty()); saves++; completed(Result.success(Unit))
                })
        } }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithTag("photo-editor").assertIsDisplayed()
        compose.onNodeWithText("AI Edit").performClick()
        compose.onNodeWithText("AI editing · Not configured").assertIsDisplayed()
        compose.waitUntil(10000) { runCatching { compose.onNodeWithText("Save copy").assertIsEnabled() }.isSuccess }
        compose.onNodeWithText("Save copy").performClick()
        compose.waitUntil(10000) { saves == 1 }
        assertTrue(original.any { it != 0.toByte() })
        original.fill(0)
    }
    @Test fun chromeHidesTogetherAndMoreUsesSheet() {
        val original = bytes()
        compose.setContent { PrivateGalleryTheme {
            FullscreenMediaViewer(listOf(ViewerMediaEntry("selected", "image/png")), MediaViewerSource.VAULT, 0, {},
                onLoadProtectedBytes = { _, loaded -> loaded(Result.success(original.copyOf())) },
                onRestore = {}, onDeleteFromVault = {})
        } }
        compose.onNodeWithContentDescription("Viewer menu").performClick()
        compose.onNodeWithText("Restore a copy").assertIsDisplayed()
        compose.onNodeWithText("Delete from Vault").assertIsDisplayed()
        original.fill(0)
    }
    @Test fun singleTapHidesAndShowsBothBars() {
        val original = bytes()
        compose.setContent { PrivateGalleryTheme {
            FullscreenMediaViewer(listOf(ViewerMediaEntry("selected", "image/png")), MediaViewerSource.VAULT, 0, {}, onLoadProtectedBytes = { _, loaded -> loaded(Result.success(original.copyOf())) })
        } }
        compose.onNodeWithTag("viewer-image").performTouchInput { click(center) }
        compose.waitUntil(3000) { compose.onAllNodesWithTag("viewer-top-bar").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("viewer-bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("viewer-image").performTouchInput { click(center) }
        compose.waitUntil(3000) { compose.onAllNodesWithTag("viewer-top-bar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("viewer-bottom-bar").assertIsDisplayed()
        original.fill(0)
    }
    @Test fun entryWaitsForLegacyCropMetadata() {
        var loaded: ((uk.co.traynor.privategallery.core.vault.ImageEditState?) -> Unit)? = null
        val original = bytes()
        compose.setContent { PrivateGalleryTheme {
            FullscreenMediaViewer(listOf(ViewerMediaEntry("selected", "image/png")), MediaViewerSource.VAULT, 0, {},
                onLoadProtectedBytes = { _, callback -> callback(Result.success(original.copyOf())) },
                onLoadImageEdit = { _, callback -> loaded = callback }, onSaveEditedCopy = { _, _, _, _ -> })
        } }
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.runOnIdle { loaded!!(uk.co.traynor.privategallery.core.vault.ImageEditState(uk.co.traynor.privategallery.core.vault.NormalizedCrop(0f,0f,.5f,1f))) }
        compose.onNodeWithText("Edit").assertIsDisplayed()
        original.fill(0)
    }
    @Test fun replacementFromLaterPageStillShowsNewItem() {
        val original = bytes()
        var entries by mutableStateOf(listOf(ViewerMediaEntry("first", "image/png"), ViewerMediaEntry("second", "image/png")))
        compose.setContent { PrivateGalleryTheme {
            FullscreenMediaViewer(entries, MediaViewerSource.VAULT, if (entries.size > 1) 1 else 0, {}, onLoadProtectedBytes = { _, callback -> callback(Result.success(original.copyOf())) })
        } }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.runOnIdle { entries = listOf(ViewerMediaEntry("copy", "image/png")) }
        compose.onNodeWithText("1 / 1").assertIsDisplayed()
        original.fill(0)
    }
    @Test fun backgroundWipesLoadedSourceBeforeResume() {
        val buffer = bytes()
        compose.setContent { PrivateGalleryTheme { PhotoEditor("selected", { _, loaded -> loaded(Result.success(buffer)) }, onCancel = {}, onSave = { _,_,_ -> }) } }
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(buffer.all { it == 0.toByte() })
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test fun foldSizedCanvasesKeepSaveAndToolsReachable() {
        val original = bytes()
        var dimensions by mutableStateOf(360 to 760)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { PrivateGalleryTheme {
            Box(Modifier.requiredSize(dimensions.first.dp, dimensions.second.dp)) {
                PhotoEditor("selected", { _, done -> done(Result.success(original.copyOf())) }, onCancel = {}, onSave = { _,_,_ -> })
            }
        } } }
        for ((name, size) in listOf("outer-portrait" to (360 to 760), "inner-portrait" to (720 to 760), "outer-landscape" to (760 to 360), "inner-landscape" to (760 to 720))) {
            compose.runOnIdle { dimensions = size }
            compose.onNodeWithText("Save copy").assertIsDisplayed()
            compose.onNodeWithText("Adjust").performScrollTo().performClick()
            compose.onNodeWithText("Brightness").assertIsDisplayed()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Photo preview").fetchSemanticsNodes().isNotEmpty() }
            val bounds = compose.onNodeWithTag("editor-canvas").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width > 100 && bounds.height > 100)
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val output = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(), "additional_test_output").absolutePath
            val folder = java.io.File(output, "editor-v1").apply { mkdirs() }
            // Google ATD starts with drawing disabled; prime capture and invalidate the full
            // test window before the review image, rather than accepting an all-black frame.
            androidx.test.core.app.takeScreenshot().recycle()
            compose.runOnIdle { compose.activity.window.decorView.invalidate() }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            androidx.test.core.app.takeScreenshot().let { bitmap ->
                java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        original.fill(0)
    }

    @Test fun toolbarCallsOnlyChosenActions() {
        var edits = 0; var restores = 0
        compose.setContent { PrivateGalleryTheme { ViewerBottomBar({ edits++ }, { restores++ }, null, null, null, {}) } }
        compose.onNodeWithText("Edit").performClick()
        assertEquals(1, edits); assertEquals(0, restores)
    }
}
