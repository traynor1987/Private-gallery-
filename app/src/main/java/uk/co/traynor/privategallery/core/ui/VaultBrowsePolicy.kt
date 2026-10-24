package uk.co.traynor.privategallery.core.ui

import java.util.Locale
import uk.co.traynor.privategallery.core.vault.VaultItem

enum class MediaKindFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }
enum class MediaSort(val label:String,val order:Int) {
 NEWEST("Date Added — Newest First",0), OLDEST("Date Added — Oldest First",1),
 TAKEN_NEWEST("Date Taken — Newest First",2), TAKEN_OLDEST("Date Taken — Oldest First",3),
 NAME("Filename — A to Z",4), NAME_DESC("Filename — Z to A",5);
 companion object {fun decode(value:String?)=entries.firstOrNull {it.name==value} ?: NEWEST}
}

object VaultBrowsePolicy {
    fun apply(
        items: List<VaultItem>,
        query: String = "",
        kind: MediaKindFilter = MediaKindFilter.ALL,
        sort: MediaSort = MediaSort.NEWEST,
    ): List<VaultItem> {
        val search = query.trim()
        val matching = items.filter { item ->
            item.displayName.contains(search, ignoreCase = true) && when (kind) {
                MediaKindFilter.ALL -> true
                MediaKindFilter.PHOTOS -> item.mimeType.startsWith("image/")
                MediaKindFilter.VIDEOS -> item.mimeType.startsWith("video/")
            }
        }
        val comparator = Comparator<VaultItem> {a,b->MediaOrder.compare(sort.order,
            a.addedAtEpochMillis ?: a.importedAtEpochMillis,a.capturedAtEpochMillis ?: 0,a.displayName,a.id,
            b.addedAtEpochMillis ?: b.importedAtEpochMillis,b.capturedAtEpochMillis ?: 0,b.displayName,b.id)}
        return matching.sortedWith(comparator)
    }
}
