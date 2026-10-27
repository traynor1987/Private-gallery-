package uk.co.traynor.privategallery.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

class PrivacyShareDialog1762Test {
 @get:Rule val compose=createComposeRule()
 @Test fun darkShareOptionsScrollAndCancelWithoutReadingMedia() {
  var reads=0;var closed=false
  val item=VaultItem("synthetic","image/png","test.png",0,50,ByteArray(32),ByteArray(12),VaultItemState.COMPLETE)
  compose.setContent {PrivateGalleryTheme(AppTheme.DARK) {
   PrivacyShareDialog("synthetic",{_,done->done(Result.success(item))},{_,_->reads++},{closed=true})
  }}
  compose.onNodeWithText("Review share").assertExists()
  compose.onNodeWithText("Remove GPS and optional metadata").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Dates are editable",substring=true).performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Cancel").performClick()
  compose.runOnIdle {assertTrue(closed);assertEquals(0,reads)}
 }
}
