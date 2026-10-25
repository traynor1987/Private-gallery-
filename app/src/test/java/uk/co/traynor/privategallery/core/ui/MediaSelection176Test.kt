package uk.co.traynor.privategallery.core.ui
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.core.vault.*
class MediaSelection176Test {
 private fun item(id:String,size:Long,added:Long=1000L)=VaultItem(id,"image/png",id,added,size,byteArrayOf(),byteArrayOf(),VaultItemState.COMPLETE)
 @Test fun filtersStayInsideSuppliedDomainAndKeepSort() {
  val items=listOf(item("small",10),item("large",100_000_001))
  assertEquals(listOf("large"),VaultBrowsePolicy.apply(items,kind=MediaKindFilter.LARGE).map {it.id})
  assertEquals(listOf("small"),VaultBrowsePolicy.apply(items,kind=MediaKindFilter.FAVOURITES,favouriteIds=setOf("small","hidden-other")).map {it.id})
  assertTrue(VaultBrowsePolicy.apply(items,kind=MediaKindFilter.RECENT,now=1000L+8*24*3600_000L).isEmpty())
  assertEquals(listOf("large","small"),VaultBrowsePolicy.apply(items,kind=MediaKindFilter.RECENT,sort=MediaSort.NAME,now=2000L).map {it.id})
 }
}
