package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.VaultHome
import uk.co.traynor.privategallery.VaultMediaTile
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

class GalleryProfessional171Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val item = VaultItem("synthetic", "image/jpeg", "Synthetic photo", 0L, 32L, byteArrayOf(), byteArrayOf(), VaultItemState.COMPLETE)

    @Test fun failedThumbnailShowsDistinctErrorInsteadOfEndlessLoading() {
        compose.setContent { PrivateGalleryTheme { Box(Modifier.size(120.dp)) {
            VaultMediaTile(item, { _, done -> done(Result.failure(IllegalStateException())) }, 0, onClick = {})
        } } }
        compose.onNodeWithContentDescription("Preview unavailable", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Loading preview", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun lateHiddenThumbnailIsRecycledAfterLeavingTheVault() {
        var done: ((Result<Bitmap>) -> Unit)? = null
        var shown by mutableStateOf(true)
        compose.setContent { PrivateGalleryTheme { CompositionLocalProvider(LocalSensitivePreviews provides true) {
            if (shown) Box(Modifier.size(120.dp)) { VaultMediaTile(item, { _, callback -> done = callback }, 0, onClick = {}) }
        } } }
        compose.waitUntil { done != null }
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { done!!(Result.success(bitmap)); assertTrue(bitmap.isRecycled) }
        compose.onNodeWithContentDescription("Synthetic photo").assertDoesNotExist()
    }

    @Test fun staleRevisionCannotReplaceTheNewProtectedThumbnail() {
        val callbacks = mutableListOf<(Result<Bitmap>) -> Unit>()
        var revision by mutableStateOf(0)
        compose.setContent { PrivateGalleryTheme { CompositionLocalProvider(LocalSensitivePreviews provides true) {
            Box(Modifier.size(120.dp)) { VaultMediaTile(item, { _, callback -> callbacks.add(callback) }, revision, onClick = {}) }
        } } }
        compose.waitUntil { callbacks.size == 1 }
        compose.runOnIdle { revision = 1 }
        compose.waitUntil { callbacks.size == 2 }
        val old = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val current = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        compose.runOnIdle { callbacks[1](Result.success(current)); callbacks[0](Result.success(old)); assertTrue(old.isRecycled); assertFalse(current.isRecycled) }
        compose.onNodeWithContentDescription("Loading preview", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun coverLightCollectionsAndSelection() = viewport(392, 840, AppTheme.LIGHT)
    @Test fun innerDarkCollectionsAndSelection() = viewport(840, 900, AppTheme.DARK)
    @Test fun coverLandscapeDarkCollectionsAndSelection() = viewport(840, 392, AppTheme.DARK)
    @Test fun innerLandscapeLightCollectionsAndSelection() = viewport(900, 840, AppTheme.LIGHT)

    private fun viewport(width: Int, height: Int, theme: AppTheme) {
        var membershipChanges = 0
        val collection = VaultCollection("trips", "Weekend photographs", 0L)
        compose.setContent { PrivateGalleryTheme(theme) {
            Surface(Modifier.requiredSize(width.dp, height.dp).testTag("gallery171-viewport")) {
                VaultHome(onLock = {}, onImport = { _, _ -> }, onLoadItems = { it(listOf(item)) },
                    onLoadCollections = { it(listOf(collection)) }, onCreateCollection = { _, _ -> },
                    onAddItemsToCollection = { _, _, _ -> membershipChanges++ }, onRemoveItemsFromCollection = { _, _, _ -> membershipChanges++ },
                    onRenameCollection = { _, _, _ -> }, onDeleteCollection = { _, _ -> }, onLoadCollectionItems = { _, done -> done(listOf(item)) },
                    onLoadPreview = { _, done -> done(Result.success(Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(83,115,145)) })) },
                    cropRevision = 0, biometricEnabled = false, onEnrollBiometrics = {}, onSetFavouriteCollection = { _, _ -> },
                    onFavouriteStateChanged = {}, onOpenViewer = { _, _, _ -> }, onCamera = {}, onRecoverCamera = {})
            }
        } }
        compose.onNodeWithContentDescription("Synthetic photo").assertIsDisplayed()
        compose.onNodeWithText("Search filenames").assertIsDisplayed()
        screenshot("grid-$width-$height-$theme")
        compose.onNodeWithText("Collections").performClick()
        compose.onNodeWithText("Weekend photographs").assertIsDisplayed()
        compose.onNodeWithText("1 item").assertExists()
        screenshot("collections-$width-$height-$theme")
        compose.onNodeWithContentDescription("Manage Weekend photographs").performClick()
        compose.onNodeWithText("Manage collection").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, membershipChanges) }
    }

    private fun screenshot(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val root = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath
        val folder = java.io.File(root,"release171").apply { mkdirs() }
        compose.onNodeWithTag("gallery171-viewport").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
    }
}
