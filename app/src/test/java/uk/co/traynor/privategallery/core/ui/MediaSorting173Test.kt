package uk.co.traynor.privategallery.core.ui
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
class MediaSorting173Test {
 private fun item(id:String,added:Long,taken:Long?,name:String=id)=VaultItem(id,"image/jpeg",name,10,0,byteArrayOf(),byteArrayOf(),VaultItemState.COMPLETE,addedAtEpochMillis=added,capturedAtEpochMillis=taken)
 @Test fun dateAddedAndCaptureGiveDifferentExpectedOrders() {
  val oldImportedToday=item("old",2000,100);val recentImportedYesterday=item("recent",1000,900)
  assertEquals(listOf("old","recent"),VaultBrowsePolicy.apply(listOf(recentImportedYesterday,oldImportedToday)).map {it.id})
  assertEquals(listOf("recent","old"),VaultBrowsePolicy.apply(listOf(recentImportedYesterday,oldImportedToday),sort=MediaSort.TAKEN_NEWEST).map {it.id})
 }
 @Test fun missingInvalidAndDuplicateTimesAreStableAtScale() {
  val items=(0..9999).map {item(it.toString().padStart(5,'0'),1,if(it%2==0)null else Long.MAX_VALUE)}
  val expected=items.map {it.id}
  assertEquals(expected,VaultBrowsePolicy.apply(items.reversed(),sort=MediaSort.TAKEN_NEWEST).map {it.id})
 }
 @Test fun allSixSortModesRetainFiltersAndHaveStableResults() {
  val items=listOf(item("a",200,100,"z.jpg"),item("b",100,200,"A.jpg"))
  val expected=listOf(listOf("a","b"),listOf("b","a"),listOf("b","a"),listOf("a","b"),listOf("b","a"),listOf("a","b"))
  MediaSort.entries.forEachIndexed {i,sort->assertEquals(expected[i],VaultBrowsePolicy.apply(items,sort=sort).map {it.id})}
  assertEquals(listOf("b"),VaultBrowsePolicy.apply(items,query="a.JPG",sort=MediaSort.NAME_DESC).map {it.id})
 }
}
