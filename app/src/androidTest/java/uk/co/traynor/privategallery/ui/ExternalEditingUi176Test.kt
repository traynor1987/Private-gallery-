package uk.co.traynor.privategallery.ui
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.ui.AppTheme
class ExternalEditingUi176Test {
 private val compose=createAndroidComposeRule<ComponentActivity>()
 private val viewport=BrowserViewportRule(fontScale=2f) {name ->if(name.contains("Landscape"))840 to 392 else 320 to 640}
 @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
 private fun dialogContent(content:@Composable ()->Unit) {
  compose.setContent(content)
  compose.waitForIdle()
  assertActualDialogFont()
 }
 private fun assertActualDialogFont() {
  val dialogs=compose.onAllNodes(isDialog()).fetchSemanticsNodes().map {it.root as androidx.compose.ui.platform.ViewRootForTest}
  assertTrue("Production dialog windows are mounted",dialogs.isNotEmpty())
  compose.runOnIdle {
   assertEquals("Activity uses the actual OS font scale",2f,compose.activity.resources.configuration.fontScale,0.001f)
   dialogs.forEach {dialog ->assertEquals("Dialog window uses the actual OS font scale",2f,dialog.view.resources.configuration.fontScale,0.001f)}
  }
 }
 @Test fun replaceRequiresExplicitConfirmationAfterPreview() {
  var saves=0;var replaced=false
  val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Hidden Vault",true,Uri.parse("content://synthetic/1"),false,"",false,
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
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",true,uri,false,"",false,{},{},{},{},{},{callback=it},{_,_,_->},{},{})}}
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
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",false,null,false,"",false,{prepared++},{},{},{},{},{},{_,_,_->},{},{closed++})}}
  compose.onNodeWithText("Edit in another app").assertExists()
  compose.onNodeWithText("Choose a compatible photo editor, then bring your photo back into this vault.").assertExists()
  compose.onNodeWithText("Choose another compatible photo editor").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(0,prepared)}
  compose.onNodeWithText("Continue to editor").assertExists()
  compose.onNodeWithText("Keep photo private").performClick()
  compose.runOnIdle {assertEquals(0,prepared)}
  compose.onNodeWithText("Cancel").performClick();compose.runOnIdle {assertEquals(1,closed)}
 }
 @Test fun confirmedEditorChoicePreparesOnceAndImportIsExplicit() {
  var prepared=0;var picked=0
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Hidden Vault",false,null,false,"",false,{prepared++},{},{},{},{picked++},{},{_,_,_->},{},{})}}
  compose.onNodeWithText("Choose another compatible photo editor").performScrollTo().performClick();compose.onNodeWithText("Continue to editor").performClick()
  compose.runOnIdle {assertEquals(1,prepared);assertEquals(0,picked)}
  compose.onNodeWithText("Import edited result").performScrollTo().performClick();compose.runOnIdle {assertEquals(1,picked)}
 }
 @Test fun forgetRecoveryReceiptAtDoubleFontRequiresExplicitAcknowledgement()=acknowledgeReceipt(AppTheme.LIGHT)
 @Test fun forgetRecoveryReceiptLandscapeDarkAtDoubleFontRequiresExplicitAcknowledgement()=acknowledgeReceipt(AppTheme.DARK)
 private fun acknowledgeReceipt(theme:AppTheme) {
  var acknowledged=0
  dialogContent {
   PrivateGalleryTheme(theme) {ExternalEditingDialog("Hidden Vault",false,null,false,"Public copy receipt retained.",true,
    {},{},{},{},{},{},{_,_,_->},{},{},acknowledgeReceipt={acknowledged++})}
  }
  compose.onNodeWithText("Forget recovery receipt…").performScrollTo().performClick()
  compose.onNodeWithText("Forget editing recovery?").assertExists()
  assertActualDialogFont()
  compose.onNodeWithText("This removes only this app's recovery receipt. Public photos, Samsung caches and cloud copies may remain. Check and remove them separately.").performScrollTo().assertIsDisplayed()
  compose.runOnIdle {assertEquals(0,acknowledged)}
  compose.onNodeWithText("Keep receipt").performClick()
  compose.runOnIdle {assertEquals(0,acknowledged)}
  compose.onNodeWithText("Forget recovery receipt…").performScrollTo().performClick()
  assertActualDialogFont()
  compose.onNodeWithText("Acknowledge and forget").performClick()
  compose.runOnIdle {assertEquals(1,acknowledged)}
  compose.onNodeWithText("Forget editing recovery?").assertDoesNotExist()
 }

 @Test fun samsungActionHasSeparateConsentAndDoesNotLaunchGenericChooser() {
  var samsung=0;var generic=0
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",true,null,false,"",false,
   {},{generic++},{},{},{},{},{_,_,_->},{},{},launchSamsung={samsung++})}}
  compose.onNodeWithText("Edit with Samsung Gallery").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(0,samsung);assertEquals(0,generic)}
  compose.onNodeWithText("Continue to editor").performClick()
  compose.runOnIdle {assertEquals(1,samsung);assertEquals(0,generic)}
 }
 @Test fun unavailableSamsungRouteIsNotOffered() {
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",false,null,false,"",false,
   {},{},{},{},{},{},{_,_,_->},{},{})}}
  compose.onNodeWithText("Edit with Samsung Gallery").assertDoesNotExist()
 }
 @Test fun unavailableCompatibleEditorsAreNotOffered() {
  var exports=0
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Primary Vault",false,null,false,"",false,
   {},{},{},{},{},{},{_,_,_->},{},{},exportGallery={exports++},compatibleEditorAvailable=false)}}
  compose.onNodeWithText("Choose another compatible photo editor").assertDoesNotExist()
  compose.onNodeWithText("Import edited result").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Export a copy to Gallery").performScrollTo().performClick()
  compose.runOnIdle {assertEquals(0,exports)}
  compose.onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag("media-operation-dialog"))).performClick()
  compose.runOnIdle {assertEquals(0,exports)}
 }
 @Test fun separateActionsLandscapeDarkRemainReachableAtDoubleFont() {
  dialogContent {PrivateGalleryTheme(AppTheme.DARK) {ExternalEditingDialog("Hidden Vault",true,null,false,"",false,
   {},{},{},{},{},{},{_,_,_->},{},{},launchSamsung={})}}
  val actions=listOf("Edit with Samsung Gallery","Choose another compatible photo editor","Import edited result")
  var width:Float?=null
  actions.forEach { label ->
   val node=compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
   val current=node.fetchSemanticsNode().boundsInRoot.width
   if(width!=null)assertEquals(width!!,current,1f)
   width=current
  }
  compose.onNodeWithText("Cancel").assertIsDisplayed().assertIsEnabled()
 }
 @Test fun returnedWithoutResultExposesGuidedExportWithSeparateConsent() {
  var fallback by mutableStateOf(false)
  var exported=0
  dialogContent {PrivateGalleryTheme {ExternalEditingDialog("Hidden Vault",true,null,false,"No edited result was returned.",false,
   {},{},{},{},{},{},{_,_,_->},{},{},exportGallery={exported++},fallback=fallback)}}
  compose.onNodeWithText("Export a copy to Gallery").assertDoesNotExist()
  compose.runOnIdle {fallback=true}
  compose.onNodeWithText("Export a copy to Gallery").performScrollTo().performClick()
  compose.onNodeWithText("Export a public copy?").assertIsDisplayed()
  compose.runOnIdle {assertEquals(0,exported)}
  compose.onNodeWithText("Export copy").performClick()
  compose.runOnIdle {assertEquals(1,exported)}
 }
}
