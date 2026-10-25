package uk.co.traynor.privategallery.ui
import androidx.activity.ComponentActivity
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
}
