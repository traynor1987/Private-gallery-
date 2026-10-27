package uk.co.traynor.privategallery

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

class CollectionDialogs1762Test {
 @get:Rule val compose = createComposeRule()
 @Test fun largeMediaListCanScrollAndSelectInDarkTheme() {
  val media=(0 until 300).map { VaultItem("id-$it","image/png","Photo $it",0,1,ByteArray(32),ByteArray(12),VaultItemState.COMPLETE) }
  var added:List<String>?=null
  compose.setContent { PrivateGalleryTheme(AppTheme.DARK) { ExistingVaultItemsDialog(media,{added=it},{}) } }
  compose.onNodeWithText("Add").assertIsNotEnabled()
  compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Photo 299"))
  compose.onNodeWithText("Photo 299").performClick()
  compose.onNodeWithText("Add").performClick()
  compose.runOnIdle { assertEquals(listOf("id-299"), added) }
 }
 @Test fun largeCollectionListCanChooseLastInLightTheme() {
  val collections=(0 until 300).map { VaultCollection("id-$it","Collection $it",0) }
  var selected:VaultCollection?=null
  compose.setContent { PrivateGalleryTheme(AppTheme.LIGHT) { CollectionPickerDialog(collections,{selected=it},{}) } }
  compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Collection 299"))
  compose.onNodeWithText("Collection 299").performClick()
  compose.runOnIdle { assertEquals("id-299",selected?.id) }
 }
}
