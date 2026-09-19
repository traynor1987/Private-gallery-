package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

class CropCanvasGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun draggingCornerUpdatesNormalizedCropImmediately() {
        var crop by mutableStateOf(NormalizedCrop.ORIGINAL)
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CropCanvas(bitmap, crop, { crop = it }, Modifier.size(300.dp))
        }

        compose.onRoot().performTouchInput {
            down(Offset(12f, 12f))
            moveBy(Offset(70f, 70f))
            up()
        }

        compose.runOnIdle {
            assertTrue(crop.left > 0f)
            assertTrue(crop.top > 0f)
        }
    }

    @Test fun draggingLeftEdgeChangesOnlyLeftBound() {
        var crop by mutableStateOf(NormalizedCrop(.2f, .2f, .8f, .8f))
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CropCanvas(bitmap, crop, { crop = it }, Modifier.size(300.dp))
        }
        compose.onRoot().performTouchInput {
            down(Offset(60f, 150f))
            moveBy(Offset(35f, 0f))
            up()
        }
        compose.runOnIdle {
            assertTrue(crop.left > .2f)
            assertTrue(crop.top == .2f && crop.bottom == .8f && crop.right == .8f)
        }
    }
}
