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
    fun `fresh install has no favourite and creates no personal collection`() {
        val migrated = VaultCollectionsState.empty().migrateLegacyFavourite()
        assertTrue(migrated.collections.isEmpty())
        assertEquals(null, migrated.favouriteCollectionId)
    }

    @Test fun `legacy Jenna migrates to the one generic favourite without creating media`() {
        val item = fixtureItem("existing")
        val legacy = VaultCollection(VaultCollectionsState.JENNA_COLLECTION_ID, "Jenna", 1, VaultPinnedDestination.JENNA)
        val migrated = VaultCollectionsState(VaultIndexSnapshot(items = listOf(item), collections = listOf(legacy))).migrateLegacyFavourite()
        assertEquals(VaultCollectionsState.JENNA_COLLECTION_ID, migrated.favouriteCollectionId)
        assertEquals(listOf(item.id), migrated.items.map { it.id })
    }

    @Test fun `favourite replacement and deletion preserve collections and media`() {
        val item = fixtureItem("one")
        val initial = VaultCollectionsState(VaultIndexSnapshot(items = listOf(item), collections = listOf(VaultCollection("a", "A", 1), VaultCollection("b", "B", 2))))
        val selected = initial.setFavourite("a").setFavourite("b")
        assertEquals("b", selected.favouriteCollectionId)
        val deleted = selected.deleteCollection("b")
        assertEquals(null, deleted.favouriteCollectionId)
        assertEquals(listOf(item.id), deleted.items.map { it.id })
    }

    @Test
    fun `same item cannot be a member of the same collection twice`() {
        val item = fixtureItem("one")
        val state = VaultCollectionsState(
            VaultIndexSnapshot(items = listOf(item), collections = listOf(VaultCollection("jenna", "Jenna", 1))),
        )

        val updated = state.addItems("jenna", listOf(item.id, item.id)).addItems("jenna", listOf(item.id))

        assertEquals(1, updated.memberships.count { it.collectionId == "jenna" && it.vaultItemId == item.id })
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

    @Test
    fun `image edits round trip in encrypted metadata without changing vault item`() {
        val root = Files.createTempDirectory("private-gallery-edits").toFile()
        val store = EncryptedIndexStore(root, syncOutput = {})
        val item = fixtureItem("photo")
        val crop = NormalizedCrop(.1f, .15f, .9f, .85f)

        store.saveSnapshot(VaultIndexSnapshot(items = listOf(item), imageEdits = mapOf(item.id to ImageEditState(crop))), key)

        val restored = store.loadSnapshot(key)
        assertEquals(item.id, restored.items.single().id)
        assertTrue(item.plaintextSha256.contentEquals(restored.items.single().plaintextSha256))
        assertEquals(crop, restored.imageEdits[item.id]?.crop)
    }

    @Test
    fun `deleting vault item removes protected image edit metadata`() {
        val item = fixtureItem("photo")
        val updated = VaultCollectionsState(
            VaultIndexSnapshot(items = listOf(item), imageEdits = mapOf(item.id to ImageEditState(NormalizedCrop(.1f, .1f, .9f, .9f)))),
        ).removeVaultItem(item.id)

        assertTrue(updated.asSnapshot().imageEdits.isEmpty())
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
