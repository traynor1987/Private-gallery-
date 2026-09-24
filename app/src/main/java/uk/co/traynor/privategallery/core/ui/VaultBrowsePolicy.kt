package uk.co.traynor.privategallery.core.ui

import java.util.Locale
import uk.co.traynor.privategallery.core.vault.VaultItem

enum class MediaKindFilter(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos") }
enum class MediaSort(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name") }

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
        val comparator = when (sort) {
            MediaSort.NEWEST -> compareByDescending<VaultItem> { it.importedAtEpochMillis }
            MediaSort.OLDEST -> compareBy<VaultItem> { it.importedAtEpochMillis }
            MediaSort.NAME -> compareBy<VaultItem> { it.displayName.lowercase(Locale.ROOT) }
        }.thenBy { it.id }
        return matching.sortedWith(comparator)
    }
}
