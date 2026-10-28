package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Preview callbacks lend shared-cache bitmaps; disposal must never recycle that cache. */
class BrowserUploadThumbnail1762Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun item() = VaultItem("shared-cache", "image/png", "Synthetic.png", 0, 32,
        ByteArray(32), ByteArray(12), VaultItemState.COMPLETE)

    @Test fun closeAndReopenDoesNotRecycleSharedThumbnail() {
        val visible = mutableStateOf(true)
        val shared = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        var delivered = 0
        compose.setContent { PrivateGalleryTheme(AppTheme.DARK) {
            if (visible.value) BrowserVaultUploadTile(item(), false, { _, callback ->
                delivered++; callback(Result.success(shared))
            }, {})
        } }
        compose.runOnIdle { assertEquals(1, delivered); visible.value = false }
        compose.runOnIdle { assertFalse(shared.isRecycled); visible.value = true }
        compose.runOnIdle { assertEquals(2, delivered); assertFalse(shared.isRecycled); visible.value = false }
        compose.runOnIdle { assertFalse(shared.isRecycled); shared.recycle() }
    }

    @Test fun latePreviewDoesNotRecycleBorrowedCacheEntry() {
        val visible = mutableStateOf(true)
        val shared = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        var pending: ((Result<Bitmap>) -> Unit)? = null
        compose.setContent { PrivateGalleryTheme(AppTheme.LIGHT) {
            if (visible.value) BrowserVaultUploadTile(item(), false, { _, callback -> pending = callback }, {})
        } }
        compose.runOnIdle { assertNotNull(pending); visible.value = false }
        compose.runOnIdle { pending!!(Result.success(shared)); assertFalse(shared.isRecycled); shared.recycle() }
    }
}
