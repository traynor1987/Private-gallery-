package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MediaPickerPresentation17103Test {
    @get:Rule val compose = createComposeRule()
    @Test fun boundedThumbnailPreviewOffersInfoWithoutSelecting() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        var selections = 0
        compose.setContent {
            MaterialTheme {
                GalleryMediaSelectionTile("photo", "Example photo", false, false, true,
                    { callback -> callback(Result.success(bitmap)) }, { selections++ })
            }
        }
        compose.onNodeWithContentDescription("Preview Example photo").performClick()
        compose.onNodeWithText("Thumbnail preview").assertIsDisplayed()
        compose.onNodeWithText("Info").performClick()
        compose.onNode(hasText("Example photo") and hasAnyAncestor(hasTestTag("gallery-full-page-dialog"))).assertExists()
        compose.runOnIdle { check(selections == 0) }
        compose.onNodeWithText("Close preview").performClick()
    }
    @Test fun knownVideoDurationAndCountRemainVisible() {
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    GalleryPickerControls(2, 8, true, {})
                    GalleryMediaSelectionTile("video", "Example video", true, true, false,
                        {}, {}, durationMillis = 65000L)
                }
            }
        }
        compose.onNodeWithText("2 selected · 8 available").assertIsDisplayed()
        compose.onNodeWithText("Sort: Name").assertIsDisplayed()
        compose.onNodeWithText("1:05").assertIsDisplayed()
    }

}
