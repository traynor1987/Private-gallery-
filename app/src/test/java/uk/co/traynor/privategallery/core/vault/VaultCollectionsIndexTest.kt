package uk.co.traynor.privategallery.core.vault

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCollectionsIndexTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `collections and memberships round trip without duplicating vault items`() {
        val root = Files.createTempDirectory("private-gallery-collections").toFile()
        val store = EncryptedIndexStore(root, syncOutput = {})
        val item = fixtureItem("one")
        val collection = VaultCollection(id = "trip", name = "Trip", createdAtEpochMillis = 1)
        val snapshot = VaultIndexSnapshot(
            items = listOf(item),
            collections = listOf(collection),
            memberships = listOf(VaultCollectionMembership(collectionId = collection.id, vaultItemId = item.id, addedAtEpochMillis = 2)),
        )

        store.saveSnapshot(snapshot, key)

        val restored = store.loadSnapshot(key)
        assertEquals(listOf(item.id), restored.items.map { it.id })
        assertEquals(listOf(collection), restored.collections)
        assertEquals(snapshot.memberships, restored.memberships)
        assertTrue(restored.memberships.all { membership -> restored.items.count { it.id == membership.vaultItemId } == 1 })
    }

    @Test
    fun `legacy media index loads as empty collections`() {
        val root = Files.createTempDirectory("private-gallery-legacy-index").toFile()
        val store = EncryptedIndexStore(root, syncOutput = {})

        store.save(listOf(fixtureItem("existing")), key)

        val restored = store.loadSnapshot(key)
        assertEquals(listOf("existing"), restored.items.map { it.id })
        assertTrue(restored.collections.isEmpty())
        assertTrue(restored.memberships.isEmpty())
    }

    @Test
    fun `pinned Jenna collection is idempotent and uses stable identity`() {
        val state = VaultCollectionsState.empty()

        val first = state.ensurePinnedJenna()
        val second = first.ensurePinnedJenna()

        assertEquals(first.collections, second.collections)
        assertEquals(1, second.collections.count { it.pinnedDestination == VaultPinnedDestination.JENNA })
        assertEquals(VaultCollectionsState.JENNA_COLLECTION_ID, second.collections.single().id)
    }

    @Test
    fun `removing a membership retains vault item and deleting vault item removes all memberships`() {
        val item = fixtureItem("one")
        val state = VaultCollectionsState(
            VaultIndexSnapshot(
                items = listOf(item),
                collections = listOf(VaultCollection("a", "A", 1), VaultCollection("b", "B", 2)),
                memberships = listOf(
                    VaultCollectionMembership("a", item.id, 3),
                    VaultCollectionMembership("b", item.id, 4),
                ),
            ),
        )

        val removedFromA = state.removeItem("a", item.id)
        assertEquals(listOf(item.id), removedFromA.items.map { it.id })
        assertEquals(listOf("b"), removedFromA.memberships.map { it.collectionId })

        val deleted = removedFromA.removeVaultItem(item.id)
        assertTrue(deleted.items.isEmpty())
        assertTrue(deleted.memberships.isEmpty())
    }

    @Test
    fun `rename and delete collection never change the media list`() {
        val item = fixtureItem("one")
        val state = VaultCollectionsState(
            VaultIndexSnapshot(
                items = listOf(item),
                collections = listOf(VaultCollection("a", "Before", 1)),
            ),
        )

        val renamed = state.rename("a", "After")
        assertEquals("After", renamed.collections.single().name)
        val deleted = renamed.deleteCollection("a")
        assertTrue(deleted.collections.isEmpty())
        assertEquals(listOf(item.id), deleted.items.map { it.id })
    }

    @Test
    fun `collection viewer dataset contains only its membership`() {
        val first = fixtureItem("first")
        val second = fixtureItem("second")
        val state = VaultCollectionsState(
            VaultIndexSnapshot(
                items = listOf(first, second),
                collections = listOf(VaultCollection("a", "A", 1)),
                memberships = listOf(VaultCollectionMembership("a", second.id, 2)),
            ),
        )

        assertEquals(listOf(second.id), state.itemsIn("a").map { it.id })
    }

    private fun fixtureItem(id: String) = VaultItem(
        id = id,
        mimeType = "image/jpeg",
        displayName = "$id.jpg",
        importedAtEpochMillis = 1,
        plaintextSize = 5,
        plaintextSha256 = ByteArray(32),
        payloadNonce = ByteArray(12),
        state = VaultItemState.COMPLETE,
    )
}
