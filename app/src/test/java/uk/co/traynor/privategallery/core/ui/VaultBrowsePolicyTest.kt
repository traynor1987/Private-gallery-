package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

class VaultBrowsePolicyTest {
    private fun item(id: String, name: String, type: String, date: Long) = VaultItem(
        id, type, name, date, 1, byteArrayOf(), byteArrayOf(), VaultItemState.COMPLETE,
    )
    private val media = listOf(
        item("a", "Beach.jpg", "image/jpeg", 10),
        item("b", "Beach clip.mp4", "video/mp4", 30),
        item("c", "Alpine.jpg", "image/jpeg", 20),
    )
    @Test fun searchAndTypeAreCombined() {
        assertEquals(listOf("a"), VaultBrowsePolicy.apply(media, " BEACH ", MediaKindFilter.PHOTOS, MediaSort.NEWEST).map { it.id })
    }
    @Test fun sortUsesMetadataAndNames() {
        assertEquals(listOf("b", "c", "a"), VaultBrowsePolicy.apply(media).map { it.id })
        assertEquals(listOf("a", "c", "b"), VaultBrowsePolicy.apply(media, sort = MediaSort.OLDEST).map { it.id })
        assertEquals(listOf("c", "b", "a"), VaultBrowsePolicy.apply(media, sort = MediaSort.NAME).map { it.id })
    }
    @Test fun noMatchDoesNotFallBackToAllItems() {
        assertEquals(emptyList<VaultItem>(), VaultBrowsePolicy.apply(media, "missing"))
        assertEquals(3, media.size)
    }
}
