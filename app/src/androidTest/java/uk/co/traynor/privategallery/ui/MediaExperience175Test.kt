package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.MediaViewerSource
import java.io.ByteArrayOutputStream

class MediaExperience175Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun jpeg():ByteArray {
  val bitmap=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888)
  return try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it);it.toByteArray()}} finally {bitmap.recycle()}
 }
 @Test fun zoomedPanCannotChangePhotoAndFitSwipePreservesSnapshotOrder() {
  val entries=listOf(ViewerMediaEntry("second","image/jpeg"),ViewerMediaEntry("first","image/jpeg"))
  compose.setContent {PrivateGalleryTheme {FullscreenMediaViewer(entries,MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->done(Result.success(jpeg()))})}}
  compose.waitUntil(10000){compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("viewer-image").performTouchInput {doubleClick(Offset(width*.7f,height*.4f))}
  compose.onNodeWithTag("viewer-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Zoom 250%"))
  compose.onNodeWithTag("viewer-image").performTouchInput {swipeLeft()}
  compose.onNodeWithText("1 / 2").assertExists()
  compose.onNodeWithTag("viewer-image").performTouchInput {doubleClick(center)}
  compose.onNodeWithTag("viewer-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Zoom 100%"))
  compose.onNodeWithTag("viewer-image").performTouchInput {swipeLeft()}
  compose.onNodeWithText("2 / 2").assertExists()
  compose.onNodeWithTag("viewer-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Zoom 100%"))
 }
 @Test fun pinchZoomDoesNotNavigateAndLateImageAfterCloseIsWiped() {
  var shown by mutableStateOf(true)
  var complete:((Result<ByteArray>)->Unit)?=null
  val entries=listOf(ViewerMediaEntry("photo","image/jpeg"))
  compose.setContent {PrivateGalleryTheme {if(shown)FullscreenMediaViewer(entries,MediaViewerSource.VAULT,0,{},onLoadProtectedBytes={_,done->complete=done})}}
  compose.waitUntil(10000){complete!=null}
  compose.runOnIdle {complete!!.invoke(Result.success(jpeg()))}
  compose.onNodeWithTag("viewer-image").performTouchInput {
   pinch(start0=Offset(width*.4f,height*.5f),end0=Offset(width*.2f,height*.5f),start1=Offset(width*.6f,height*.5f),end1=Offset(width*.8f,height*.5f))
  }
  compose.onNodeWithTag("viewer-image").assert(SemanticsMatcher("Photo must be zoomed") {node->node.config[SemanticsProperties.StateDescription]!="Zoom 100%"})
  compose.onNodeWithText("1 / 1").assertExists()
  compose.runOnIdle {shown=false}
  val late=jpeg()
  compose.runOnIdle {complete!!.invoke(Result.success(late))}
  compose.runOnIdle {assertTrue("Revoked image callback must wipe its plaintext",late.all {it==0.toByte()})}
 }
 @Test fun favouriteButtonChangesOnlyExplicitlySelectedItem() {
  val selected=mutableSetOf<String>()
  compose.setContent {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{},
   onLoadProtectedBytes={_,done->done(Result.success(jpeg()))},onLoadFavouriteState={id,done->done(id in selected)},
   onToggleFavourite={id,done->if(!selected.add(id))selected.remove(id);done(id in selected)})}}
  compose.onNodeWithContentDescription("Add to Favourite").performClick()
  compose.onNodeWithContentDescription("Remove from Favourite").assertExists().performClick()
  compose.runOnIdle {assertTrue(selected.isEmpty())}
 }
 @Test fun fittedSwipeDownClosesViewerWithoutDeletingMedia() {
  var closed=0;var loads=0
  compose.setContent {PrivateGalleryTheme {FullscreenMediaViewer(listOf(ViewerMediaEntry("photo","image/jpeg")),MediaViewerSource.VAULT,0,{closed++},onLoadProtectedBytes={_,done->loads++;done(Result.success(jpeg()))})}}
  compose.waitUntil(10000){compose.onAllNodesWithText("Loading media…").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("viewer-image").performTouchInput {swipeDown()}
  compose.runOnIdle {assertEquals(1,closed);assertEquals(1,loads)}
 }
}
