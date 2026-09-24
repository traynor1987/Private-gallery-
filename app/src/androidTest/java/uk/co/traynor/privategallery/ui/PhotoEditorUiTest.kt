package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
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
    @Test fun toolbarCallsOnlyChosenActions() {
        var edits = 0; var restores = 0
        compose.setContent { PrivateGalleryTheme { ViewerBottomBar({ edits++ }, { restores++ }, null, null, null, {}) } }
        compose.onNodeWithText("Edit").performClick()
        assertEquals(1, edits); assertEquals(0, restores)
    }
}
