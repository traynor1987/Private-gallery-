package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.CollectionCoverDialog
import uk.co.traynor.privategallery.core.vault.*

class CollectionCoverPicker17103Test {
 @get:Rule val compose=createComposeRule()
 private fun item()=VaultItem("cover-a","image/png","Photo A",0,10,ByteArray(32),ByteArray(12),VaultItemState.COMPLETE)
 @Test fun thumbnailChoiceCommitsOnlyAfterExplicitConfirm() {
  var writes=0;var chosen:String?=null;var mounted by mutableStateOf(true)
  val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
  compose.setContent{PrivateGalleryTheme{if(mounted)CollectionCoverDialog(listOf(item()),null,{chosen=it;writes++},{},{_,done->done(Result.success(bitmap))})}}
  try {
   compose.onNodeWithContentDescription("Photo A").performClick()
   compose.runOnIdle{assertEquals(0,writes)}
   compose.onNodeWithText("Confirm cover").performClick()
   compose.runOnIdle{assertEquals(1,writes);assertEquals("cover-a",chosen)}
  }finally{compose.runOnIdle{mounted=false};compose.waitForIdle();bitmap.recycle()}
 }
 @Test fun cancelDiscardsDraftCoverSelection() {
  var writes=0;var closed=false
  compose.setContent{PrivateGalleryTheme{CollectionCoverDialog(listOf(item()),null,{writes++},{closed=true})}}
  compose.onNodeWithContentDescription("Photo A").performClick()
  compose.onNodeWithText("Cancel").performClick()
  compose.runOnIdle{assertTrue(closed);assertEquals(0,writes)}
 }
}
