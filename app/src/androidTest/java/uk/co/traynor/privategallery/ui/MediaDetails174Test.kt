package uk.co.traynor.privategallery.ui
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
class MediaDetails174Test {
 @get:Rule val compose=createComposeRule()
 @Test fun restoreOriginalDateRefreshesDetailsWithoutChangingMediaHash() {
  val hash=ByteArray(32){7}
  var item=VaultItem("id","image/jpeg","private.jpg",1000,100,hash,byteArrayOf(),VaultItemState.COMPLETE,capturedAtEpochMillis=1700000000000,adjustedTakenAtEpochMillis=1800000000000,adjustedTimeZone="UTC")
  var changes=0
  compose.setContent {PrivateGalleryTheme {MediaDetailsDialog("id","Hidden Vault",{_,done->done(Result.success(item))},{_,time,zone,done->item=item.copy(adjustedTakenAtEpochMillis=time,adjustedTimeZone=zone);done(Result.success(Unit))},null,{changes++},{})}}
  compose.onNodeWithText("Date Taken · Adjusted").assertExists()
  compose.onNodeWithText("Restore Original Date").performScrollTo().performClick()
  compose.onNodeWithText("Date Taken · Adjusted").assertDoesNotExist()
  compose.runOnIdle {assertNull(item.adjustedTakenAtEpochMillis);assertEquals(1700000000000L,item.capturedAtEpochMillis);assertArrayEquals(hash,item.plaintextSha256);assertEquals(1,changes)}
 }
}
