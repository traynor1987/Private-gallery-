package uk.co.traynor.privategallery.ui
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
class ExternalEditingUi176Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun replaceRequiresExplicitConfirmationAfterPreview() {
  var saves=0;var replaced=false
  val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
  compose.setContent {PrivateGalleryTheme {ExternalEditingDialog("Hidden Vault",true,Uri.parse("content://synthetic/1"),false,"",false,
   {},{},{},{},{}, {it(Result.success(byteArrayOf(1) to image))}, {bytes,replace,done ->saves++;replaced=replace;bytes.fill(0);done(Result.success(Unit))},{},{})}}
  compose.onNodeWithText("Preview selected result").performScrollTo().performClick()
  compose.onNodeWithText("Replace Original…").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(0,saves)}
  compose.onNodeWithText("Verify and replace").performClick()
  compose.runOnIdle {assertEquals(1,saves);assertTrue(replaced)}
 }
 @Test fun latePreviewForPreviousSelectedResultIsWipedAndRecycled() {
  var uri by mutableStateOf(Uri.parse("content://synthetic/1"))
  var callback:((Result<Pair<ByteArray,Bitmap>>)->Unit)?=null
  compose.setContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",true,uri,false,"",false,{},{},{},{},{},{callback=it},{_,_,_->},{},{})}}
  compose.onNodeWithText("Preview selected result").performScrollTo().performClick()
  compose.runOnIdle {uri=Uri.parse("content://synthetic/2")}
  compose.waitForIdle()
  val bytes=byteArrayOf(7,8);val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
  compose.runOnIdle {checkNotNull(callback)(Result.success(bytes to bitmap));assertTrue(bytes.all {it==0.toByte()});assertTrue(bitmap.isRecycled)}
  compose.onNodeWithContentDescription("Edited result preview").assertDoesNotExist()
  compose.onNodeWithText("Preview selected result").assertIsEnabled()
 }
 @Test fun compactEntryKeepsHandoffBehindExplicitConsentAndCanCancel() {
  var prepared=0;var closed=0
  compose.setContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",false,null,false,"",false,{prepared++},{},{},{},{},{},{_,_,_->},{},{closed++})}}
  compose.onNodeWithText("Edit in another app").assertExists()
  compose.onNodeWithText("Choose a compatible photo editor, then bring your photo back into this vault.").assertExists()
  compose.onNodeWithText("Open photo editor").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(0,prepared)}
  compose.onNodeWithText("Continue to editor").assertExists()
  compose.onNodeWithText("Keep photo private").performClick()
  compose.runOnIdle {assertEquals(0,prepared)}
  compose.onNodeWithText("Cancel").performClick();compose.runOnIdle {assertEquals(1,closed)}
 }
 @Test fun confirmedEditorChoicePreparesOnceAndImportIsExplicit() {
  var prepared=0;var picked=0
  compose.setContent {PrivateGalleryTheme {ExternalEditingDialog("Hidden Vault",false,null,false,"",false,{prepared++},{},{},{},{picked++},{},{_,_,_->},{},{})}}
  compose.onNodeWithText("Open photo editor").performScrollTo().performClick();compose.onNodeWithText("Continue to editor").performClick()
  compose.runOnIdle {assertEquals(1,prepared);assertEquals(0,picked)}
  compose.onNodeWithText("Import edited photo").performClick();compose.runOnIdle {assertEquals(1,picked)}
 }
 @Test fun forgetRecoveryReceiptAtDoubleFontRequiresExplicitAcknowledgement() {
  var acknowledged=0
  compose.setContent {
   val density=androidx.compose.ui.platform.LocalDensity.current
   CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
    PrivateGalleryTheme {ExternalEditingDialog("Hidden Vault",false,null,false,"Public copy receipt retained.",true,
     {},{},{},{},{},{},{_,_,_->},{},{},acknowledgeReceipt={acknowledged++})}
   }
  }
  compose.onNodeWithText("Forget recovery receipt…").performScrollTo().performClick()
  compose.onNodeWithText("Forget editing recovery?").assertExists()
  compose.onNodeWithText("This removes only this app's recovery receipt. Public photos, Samsung caches and cloud copies may remain. Check and remove them separately.").performScrollTo().assertIsDisplayed()
  compose.runOnIdle {assertEquals(0,acknowledged)}
  compose.onNodeWithText("Keep receipt").performClick()
  compose.runOnIdle {assertEquals(0,acknowledged)}
  compose.onNodeWithText("Forget recovery receipt…").performScrollTo().performClick()
  compose.onNodeWithText("Acknowledge and forget").performClick()
  compose.runOnIdle {assertEquals(1,acknowledged)}
  compose.onNodeWithText("Forget editing recovery?").assertDoesNotExist()
 }

}
