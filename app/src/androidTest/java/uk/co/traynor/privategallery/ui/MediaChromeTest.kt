package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.*
import uk.co.traynor.privategallery.core.ui.MediaKindFilter
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaChromeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun headerRoutesActionsToTheirCallbacks() {
        var adds = 0
        var locks = 0
        var menus = 0
        compose.setContent { PrivateGalleryTheme {
            MediaHeader("Vault", "12 items", onAdd = { adds++ }, onLock = { locks++ }, onMenu = { menus++ })
        } }
        compose.onNodeWithContentDescription("Add media").performClick()
        compose.onNodeWithContentDescription("Lock Vault").performClick()
        compose.onNodeWithContentDescription("Vault menu").performClick()
        compose.runOnIdle { assertEquals(1, adds); assertEquals(1, locks); assertEquals(1, menus) }
    }

    @Test fun sizeChoicesReportTheChosenDensity() {
        var chosen = ThumbnailDensity.COMPACT
        compose.setContent { PrivateGalleryTheme {
            ThumbnailSizeChoices(ThumbnailDensity.COMPACT) { chosen = it }
        } }
        compose.onNodeWithText("Large").performClick()
        compose.runOnIdle { assertEquals(ThumbnailDensity.LARGE, chosen) }
    }

    @Test fun menuSheetExposesUsableActions() {
        var adds = 0
        compose.setContent { PrivateGalleryTheme {
            GalleryMenuSheet("Gallery", onDismiss = {}) {
                SheetAction("Add media", Icons.Default.Add) { adds++ }
            }
        } }
        compose.onNodeWithText("Add media").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, adds) }
    }
    @Test fun searchCanBeEnteredClearedAndFiltered() {
        var query by mutableStateOf("")
        var kind by mutableStateOf(MediaKindFilter.ALL)
        compose.setContent { PrivateGalleryTheme {
            VaultBrowseControls(query, { query = it }, kind, { kind = it })
        } }
        compose.onNodeWithText("Search filenames").performTextInput("holiday")
        compose.runOnIdle { assertEquals("holiday", query) }
        compose.onNodeWithText("Videos").performClick()
        compose.runOnIdle { assertEquals(MediaKindFilter.VIDEOS, kind) }
        compose.onNodeWithText("Clear").performClick()
        compose.runOnIdle { assertEquals("", query) }
    }

}
