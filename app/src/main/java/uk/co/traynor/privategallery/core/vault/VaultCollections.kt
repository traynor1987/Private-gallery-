package uk.co.traynor.privategallery.core.vault

import java.util.UUID

enum class VaultPinnedDestination { JENNA }

data class VaultCollection(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val pinnedDestination: VaultPinnedDestination? = null,
    val coverVaultItemId: String? = null,
)

data class VaultCollectionMembership(
    val collectionId: String,
    val vaultItemId: String,
    val addedAtEpochMillis: Long,
)

data class VaultIndexSnapshot(
    val items: List<VaultItem>,
    val collections: List<VaultCollection> = emptyList(),
    val memberships: List<VaultCollectionMembership> = emptyList(),
    /** Per-item presentation state; encrypted together with the index ledger. */
    val imageEdits: Map<String, ImageEditState> = emptyMap(),
    val favouriteCollectionId: String? = null,
)

/** Pure membership operations. Payload files are deliberately not represented here. */
class VaultCollectionsState(private val snapshot: VaultIndexSnapshot) {
    val favouriteCollectionId: String? get() = snapshot.favouriteCollectionId
    val items: List<VaultItem> get() = snapshot.items
    val collections: List<VaultCollection> get() = snapshot.collections
    val memberships: List<VaultCollectionMembership> get() = snapshot.memberships

    fun ensurePinnedJenna(now: Long = System.currentTimeMillis()): VaultCollectionsState =
        if (collections.any { it.pinnedDestination == VaultPinnedDestination.JENNA }) this
        else withSnapshot(snapshot.copy(collections = collections + VaultCollection(JENNA_COLLECTION_ID, "Jenna", now, VaultPinnedDestination.JENNA), favouriteCollectionId = snapshot.favouriteCollectionId ?: JENNA_COLLECTION_ID))

    fun setFavourite(collectionId: String): VaultCollectionsState {
        require(collections.any { it.id == collectionId }) { "Unknown collection" }
        return withSnapshot(snapshot.copy(favouriteCollectionId = collectionId))
    }

    fun create(name: String, now: Long = System.currentTimeMillis()): VaultCollectionsState =
        withSnapshot(snapshot.copy(collections = collections + VaultCollection(UUID.randomUUID().toString(), cleanName(name), now)))

    fun rename(collectionId: String, name: String): VaultCollectionsState =
        withSnapshot(snapshot.copy(collections = collections.map { if (it.id == collectionId) it.copy(name = cleanName(name)) else it }))

    fun deleteCollection(collectionId: String): VaultCollectionsState = withSnapshot(
        snapshot.copy(
            collections = collections.filterNot { it.id == collectionId },
            memberships = memberships.filterNot { it.collectionId == collectionId },
            favouriteCollectionId = snapshot.favouriteCollectionId.takeUnless { it == collectionId },
        ),
    )

    fun addItems(collectionId: String, vaultItemIds: Collection<String>, now: Long = System.currentTimeMillis()): VaultCollectionsState {
        require(collections.any { it.id == collectionId }) { "Unknown collection" }
        val knownItems = items.mapTo(mutableSetOf()) { it.id }
        val existing = memberships.mapTo(mutableSetOf()) { it.collectionId to it.vaultItemId }
        val additions = vaultItemIds.distinct()
            .filter { it in knownItems && (collectionId to it) !in existing }
            .map { VaultCollectionMembership(collectionId, it, now) }
        return withSnapshot(snapshot.copy(memberships = memberships + additions))
    }

    fun removeItem(collectionId: String, vaultItemId: String): VaultCollectionsState =
        withSnapshot(snapshot.copy(memberships = memberships.filterNot { it.collectionId == collectionId && it.vaultItemId == vaultItemId }))

    fun removeVaultItem(vaultItemId: String): VaultCollectionsState = withSnapshot(
        snapshot.copy(
            items = items.filterNot { it.id == vaultItemId },
            memberships = memberships.filterNot { it.vaultItemId == vaultItemId },
            imageEdits = snapshot.imageEdits - vaultItemId,
        ),
    )

    fun itemsIn(collectionId: String): List<VaultItem> {
        val byId = items.associateBy { it.id }
        return memberships.filter { it.collectionId == collectionId }
            .sortedByDescending { it.addedAtEpochMillis }
            .mapNotNull { byId[it.vaultItemId] }
    }

    fun asSnapshot(): VaultIndexSnapshot = snapshot

    private fun withSnapshot(value: VaultIndexSnapshot) = VaultCollectionsState(value)

    private fun cleanName(value: String): String = value.trim().also { require(it.isNotEmpty()) { "Collection name is required" } }

    companion object {
        const val JENNA_COLLECTION_ID = "pinned-collection:jenna"
        fun empty() = VaultCollectionsState(VaultIndexSnapshot(emptyList()))
    }
}
