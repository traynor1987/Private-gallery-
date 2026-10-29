package uk.co.traynor.privategallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*

class RecentlyDeletedUi177Test {
    @get:Rule val compose=createComposeRule()
    @Test fun emptyRequiresConfirmationAndBulkRestoreStaysInOriginCallbacks() {
        val item=VaultItem("test-id","image/png","fixture.png",1000,4,ByteArray(32),ByteArray(12),VaultItemState.TRASHED,deletedAtEpochMillis=1000)
        var deletes=0;var restores=0
        compose.setContent {PrivateGalleryTheme(uk.co.traynor.privategallery.core.ui.AppTheme.DARK) {
            RecentlyDeletedPanel(listOf(item),0,{_,done ->done(Result.failure(IllegalStateException("fixture")))},{it(30)},{_,done ->done("saved")},
                {items,delete,done ->assertEquals(listOf(item.id),items.map{it.id});if(delete)deletes++ else restores++;done("done")},{})
        }}
        compose.onNodeWithText("Empty Recently Deleted").performClick()
        compose.runOnIdle {assertEquals(0,deletes)}
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {assertEquals(0,deletes)}
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Restore (1)").performClick()
        compose.runOnIdle {assertEquals(1,restores);assertEquals(0,deletes)}
        compose.onNodeWithText("Empty Recently Deleted").performClick()
        compose.onNodeWithText("Delete permanently",useUnmergedTree=true).performClick()
        compose.runOnIdle {assertEquals(1,deletes)}
        compose.onNodeWithText("Retention").performClick()
        compose.onNodeWithText("Never automatically delete").assertExists()
        compose.onNodeWithText("7 days").assertExists()
    }
}
