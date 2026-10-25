package uk.co.traynor.privategallery.core.ui

import java.util.Locale
import uk.co.traynor.privategallery.core.vault.VaultItem

enum class MediaKindFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), FAVOURITES("Favourites"), LARGE("Large Files"), RECENT("Recently Added") }
enum class MediaSort(val label:String,val order:Int) {
 NEWEST("Date Added — Newest First",0), OLDEST("Date Added — Oldest First",1),
 TAKEN_NEWEST("Date Taken — Newest First",2), TAKEN_OLDEST("Date Taken — Oldest First",3),
 NAME("Filename — A to Z",4), NAME_DESC("Filename — Z to A",5), LARGEST("Largest First",6), SMALLEST("Smallest First",7);
 companion object {fun decode(value:String?)=entries.firstOrNull {it.name==value} ?: NEWEST}
}

object VaultBrowsePolicy {
    fun apply(
        items: List<VaultItem>,
        query: String = "",
        kind: MediaKindFilter = MediaKindFilter.ALL,
        sort: MediaSort = MediaSort.NEWEST,
        favouriteIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): List<VaultItem> {
        val search = query.trim()
        val matching = items.filter { item ->
            item.displayName.contains(search, ignoreCase = true) && when (kind) {
                MediaKindFilter.ALL -> true
                MediaKindFilter.PHOTOS -> item.mimeType.startsWith("image/")
                MediaKindFilter.VIDEOS -> item.mimeType.startsWith("video/")
                MediaKindFilter.FAVOURITES -> item.id in favouriteIds
                MediaKindFilter.LARGE -> item.plaintextSize > 100_000_000L
                MediaKindFilter.RECENT -> (item.addedAtEpochMillis ?: item.importedAtEpochMillis).let { it > 0 && it <= now && it >= now - 7L * 24 * 60 * 60 * 1000 }
            }
        }
        val comparator = Comparator<VaultItem> {a,b->if(sort.order>=6) MediaOrder.sizeCompare(sort.order,a.plaintextSize,a.id,b.plaintextSize,b.id) else MediaOrder.compare(sort.order,
            a.addedAtEpochMillis ?: a.importedAtEpochMillis,a.adjustedTakenAtEpochMillis ?: a.capturedAtEpochMillis ?: 0,a.displayName,a.id,
            b.addedAtEpochMillis ?: b.importedAtEpochMillis,b.adjustedTakenAtEpochMillis ?: b.capturedAtEpochMillis ?: 0,b.displayName,b.id)}
        return matching.sortedWith(comparator)
    }
}
