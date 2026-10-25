package uk.co.traynor.privategallery.ui
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.VaultMediaGrid
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId
import uk.co.traynor.privategallery.core.vault.*

class MediaSelection176Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun item(id:String,size:Long,time:Long)=VaultItem(id,"image/png",id,time,size,byteArrayOf(),byteArrayOf(),VaultItemState.COMPLETE)
 @Test fun dateSelectionIncludesBothLocalBoundariesAndNeverAddsOutsideScope() {
  val day=LocalDate.now();val zone=ZoneId.systemDefault()
  val start=day.atStartOfDay(zone).toInstant().toEpochMilli();val next=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  var selection=setOf<String>()
  compose.setContent {PrivateGalleryTheme {MediaSelectionDialog(listOf(item("start",1,start),item("end",1,next-1),item("tomorrow",1,next)),"Hidden filtered photos",{}, {selection=it})}}
  compose.onNodeWithText("2 matching items").assertExists()
  compose.onNodeWithText("Select matches").performClick()
  compose.runOnIdle {assertEquals(setOf("start","end"),selection)}
 }
 @Test fun sizeSelectionUsesMetadataAndStrictPresetBoundary() {
  var selection=setOf<String>()
  compose.setContent {PrivateGalleryTheme {MediaSelectionDialog(listOf(item("equal",100_000_000,1),item("larger",100_000_001,1),item("unknown",-1,1)),"Primary filtered results",{}, {selection=it})}}
  compose.onNodeWithText("File size").performClick()
  compose.onNodeWithText("1 matching items").assertExists()
  compose.onNodeWithText("Select matches").performClick()
  compose.runOnIdle {assertEquals(setOf("larger"),selection)}
 }

 @Test fun draggingAcrossVisibleThumbnailsSelectsStableRangeWithoutReadingPayloads() {
  val media=(0..5).map {item("item-$it",100,1000)}
  var selected by mutableStateOf<Set<String>>(emptySet());var opened=false
  compose.setContent {PrivateGalleryTheme {
   VaultMediaGrid(media,{_,done->done(Result.failure(IllegalStateException("synthetic no preview")))},0,selected,{selected=selected+it},{_,_,_->opened=true},
    Modifier.requiredSize(392.dp,600.dp).testTag("drag-grid"),selectionMode=true,onDragSelection={selected=it})
  }}
  val grid=compose.onNodeWithTag("drag-grid").fetchSemanticsNode().boundsInRoot
  val start=compose.onNodeWithContentDescription("item-0").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
  val end=compose.onNodeWithContentDescription("item-2").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
  compose.onNodeWithTag("drag-grid").performTouchInput {
   down(start)
   repeat(70){moveTo(start,10)} // Canonical stationary hold, with actual pointer events.
   moveTo((start+end)/2f,100);moveTo(end,100);up()
  }
  compose.runOnIdle {assertEquals(setOf("item-0","item-1","item-2"),selected);assertFalse(opened)}
  compose.onNodeWithContentDescription("item-5").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick){assertTrue(it())}
  compose.runOnIdle {assertEquals(setOf("item-0","item-1","item-2","item-5"),selected);assertFalse(opened)}
 }
}
