package uk.co.traynor.privategallery.ui

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.vault.*

/** Real encrypted Primary persistence behind the production protected review surface. */
class PrivateAppScreenshot49Test {
 @get:Rule val compose=createComposeRule()
 private fun assertProtectedDialog() {
  androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
   .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure ->
    if(failure!=null)throw failure
    val flags=(view.rootView.layoutParams as android.view.WindowManager.LayoutParams).flags
    assertTrue(flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
   }
 }
 @Test fun review_cancel_save_and_view_read_the_committed_primary_item() {
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(base.noBackupFilesDir,"screenshot-review-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getFilesDir()=root}
  val key=ByteArray(32){49}
  val bitmap=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
  val png=java.io.ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bitmap.recycle()
  val repo=AndroidVaultRepository(context,key)
  var request by mutableStateOf<PrivateAppScreenshotRequest?>(null)
  var consumed=0;var reads=0;var receipt:String?=null;var live=true
  fun capture()=PrivateAppScreenshotRequest(
   VaultImportSource("browser-screenshot.png","image/png",{png.inputStream()},vaultOnly=true,onConsumed={consumed++}),
   "Primary Vault",{live},
   {source,done ->done(runCatching {
    val item=(VaultImportCoordinator(repo).acquire(source) as ImportResult.Imported).item
    receipt=item.id;item.id
   })},
   {id ->reads++;assertEquals(receipt,id);repo.readForViewing(repo.items().single{it.id==id})})
  try {
   compose.setContent{PrivateGalleryTheme{request?.let{PrivateAppScreenshotReview(it){request=null}}}}
   compose.runOnIdle{request=capture()}
   compose.waitUntil(5000){compose.onAllNodesWithContentDescription("Protected screenshot preview").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Review screenshot").assertIsDisplayed()
   assertProtectedDialog()
   assertTrue(repo.items().isEmpty())
   compose.onNodeWithText("Cancel").performClick()
   compose.runOnIdle{assertEquals(1,consumed);assertTrue(repo.items().isEmpty());request=capture()}
   compose.waitUntil(5000){compose.onAllNodesWithText("Save screenshot").fetchSemanticsNodes().any{!it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)}}
   compose.onNodeWithText("Save screenshot").performClick()
   compose.onNodeWithText("Screenshot saved").assertIsDisplayed()
   assertProtectedDialog()
   val item=repo.items().single();assertEquals(receipt,item.id);assertTrue(item.vaultOnly);assertNull(item.sourceUri)
   assertEquals(VaultItemState.COMPLETE,item.state)
   compose.onNodeWithText("View Screenshot").performClick()
   compose.waitUntil(5000){compose.onAllNodesWithText("Saved screenshot").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithContentDescription("Protected screenshot preview").assertIsDisplayed()
   assertProtectedDialog()
   compose.runOnIdle{assertEquals(1,reads)}
   compose.onNodeWithText("Done").performClick()
   compose.runOnIdle{assertEquals(2,consumed)}
   assertEquals(1,repo.items().size)
   compose.runOnIdle{request=capture()}
   compose.waitUntil(5000){compose.onAllNodesWithContentDescription("Protected screenshot preview").fetchSemanticsNodes().isNotEmpty()}
   compose.runOnIdle{live=false}
   compose.waitUntil(5000){compose.onAllNodesWithText("Review screenshot").fetchSemanticsNodes().isEmpty()}
   compose.runOnIdle{assertEquals(3,consumed);assertEquals(1,reads)}
   assertEquals("Revoked preview cannot publish another item",1,repo.items().size)
  }finally{compose.runOnIdle{request=null};compose.waitForIdle();png.fill(0);key.fill(0);root.deleteRecursively()}
 }
}
