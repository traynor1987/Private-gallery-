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
  compose.onNodeWithText("Preview selected result").performClick()
  compose.onNodeWithText("Replace Original…").performClick()
  compose.runOnIdle {assertEquals(0,saves)}
  compose.onNodeWithText("Verify and replace").performClick()
  compose.runOnIdle {assertEquals(1,saves);assertTrue(replaced)}
 }
 @Test fun latePreviewForPreviousSelectedResultIsWipedAndRecycled() {
  var uri by mutableStateOf(Uri.parse("content://synthetic/1"))
  var callback:((Result<Pair<ByteArray,Bitmap>>)->Unit)?=null
  compose.setContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",true,uri,false,"",false,{},{},{},{},{},{callback=it},{_,_,_->},{},{})}}
  compose.onNodeWithText("Preview selected result").performClick()
  compose.runOnIdle {uri=Uri.parse("content://synthetic/2")}
  compose.waitForIdle()
  val bytes=byteArrayOf(7,8);val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
  compose.runOnIdle {checkNotNull(callback)(Result.success(bytes to bitmap));assertTrue(bytes.all {it==0.toByte()});assertTrue(bitmap.isRecycled)}
  compose.onNodeWithContentDescription("Edited result preview").assertDoesNotExist()
  compose.onNodeWithText("Preview selected result").assertIsEnabled()
 }
}
