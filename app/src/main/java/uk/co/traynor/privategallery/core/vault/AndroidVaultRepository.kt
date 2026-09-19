package uk.co.traynor.privategallery.core.vault

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher

sealed interface ImportResult {
    data class Imported(val item: VaultItem) : ImportResult
    data class Duplicate(val existing: VaultItem) : ImportResult
}

/** Android adapter for local-first vault operations. */
class AndroidVaultRepository(
    private val context: Context,
    private val vaultKey: ByteArray,
) {
    private val root = File(context.filesDir, "vault")
    private val payloads = EncryptedPayloadStore(root)
    private val index = EncryptedIndexStore(root)
    private val resolver: ContentResolver = context.contentResolver

    fun items(): List<VaultItem> = snapshot().items.sortedByDescending { it.importedAtEpochMillis }

    fun collections(): List<VaultCollection> = snapshot().collections.sortedBy { it.createdAtEpochMillis }

    fun itemsInCollection(collectionId: String): List<VaultItem> = VaultCollectionsState(snapshot()).itemsIn(collectionId)

    /** The edit ledger is encrypted metadata; it never changes the payload file. */
    fun imageEdit(itemId: String): ImageEditState? = snapshot().imageEdits[itemId]

    fun applyImageCrop(itemId: String, crop: NormalizedCrop): ImageEditState = synchronized(METADATA_LOCK) {
        val current = snapshot()
        val item = current.items.firstOrNull { it.id == itemId } ?: error("Unknown Vault item")
        require(item.mimeType.startsWith("image/")) { "Only images can be cropped" }
        val prior = current.imageEdits[itemId]?.crop
        val updated = ImageEditState(crop = crop, previousCrop = prior)
        saveSnapshot(current.copy(imageEdits = current.imageEdits + (itemId to updated)))
        updated
    }

    /** Restores the immediately preceding crop, or the original image. */
    fun undoImageCrop(itemId: String): ImageEditState? = synchronized(METADATA_LOCK) {
        val current = snapshot()
        val existing = current.imageEdits[itemId] ?: return@synchronized null
        val previous = existing.previousCrop
        val edits = if (previous == null || previous.isOriginal) {
            current.imageEdits - itemId
        } else {
            current.imageEdits + (itemId to ImageEditState(previous))
        }
        saveSnapshot(current.copy(imageEdits = edits))
        edits[itemId]
    }

    /** Removes presentation metadata only; the authenticated original remains intact. */
    fun resetImageCrop(itemId: String) = synchronized(METADATA_LOCK) {
        val current = snapshot()
        if (itemId !in current.imageEdits) return@synchronized
        saveSnapshot(current.copy(imageEdits = current.imageEdits - itemId))
    }

    /** The stable pinned collection is created once in the encrypted metadata ledger. */
    fun ensureJennaCollection(): VaultCollection = mutateCollections { state ->
        val updated = state.ensurePinnedJenna()
        updated to checkNotNull(updated.collections.singleOrNull { it.pinnedDestination == VaultPinnedDestination.JENNA })
    }

    fun createCollection(name: String): VaultCollection = mutateCollections { state ->
        val updated = state.create(name)
        updated to updated.collections.last()
    }

    fun renameCollection(collectionId: String, name: String) {
        mutateCollections { state -> state.rename(collectionId, name) to Unit }
    }

    /** Deletes organisation only. Underlying encrypted payloads and VaultItems are retained. */
    fun deleteCollection(collectionId: String) {
        mutateCollections { state -> state.deleteCollection(collectionId) to Unit }
    }

    fun addItemsToCollection(collectionId: String, itemIds: Collection<String>) {
        mutateCollections { state -> state.addItems(collectionId, itemIds) to Unit }
    }

    /** Removes organisation membership only. Underlying encrypted payloads and VaultItems are retained. */
    fun removeItemFromCollection(collectionId: String, itemId: String) {
        mutateCollections { state -> state.removeItem(collectionId, itemId) to Unit }
    }

    /**
     * Returns authenticated plaintext only in process memory for protected viewing.
     * Callers must discard the returned bytes when their viewer closes.
     */
    fun readForViewing(item: VaultItem): ByteArray = payloads.decryptToBytes(
        StoredPayload(
            id = item.id,
            file = payloadFile(item),
            plaintextSize = item.plaintextSize,
            plaintextSha256 = item.plaintextSha256,
            nonce = item.payloadNonce,
        ),
        vaultKey,
    )

    fun markDeletePending(item: VaultItem) {
        replaceState(item.id, VaultItemState.DELETE_PENDING)
    }

    fun finishSourceDeletionRequest(item: VaultItem, approved: Boolean) {
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            saveSnapshot(
                current.copy(items = current.items.map { recorded ->
                if (recorded.id == item.id && recorded.state == VaultItemState.DELETE_PENDING) {
                    recorded.copy(state = MoveDeletionState.finishRecordedState(recorded.state, approved))
                } else {
                    recorded
                }
                }),
            )
        }
    }

    /** Import does not delete the selected normal-gallery URI. */
    fun import(uri: Uri): ImportResult {
        val id = UUID.randomUUID().toString()
        val stored = resolver.openInputStream(uri)?.use { input ->
            payloads.writeAndVerify(id, input, vaultKey)
        } ?: error("Selected media is unavailable")
        val item = VaultItem(
            id = id,
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            displayName = displayName(uri),
            importedAtEpochMillis = System.currentTimeMillis(),
            plaintextSize = stored.plaintextSize,
            plaintextSha256 = stored.plaintextSha256,
            payloadNonce = stored.nonce,
            state = VaultItemState.COMPLETE,
            sourceUri = uri.toString(),
        )
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            current.items.firstOrNull { it.plaintextSha256.contentEquals(stored.plaintextSha256) }?.let { duplicate ->
                stored.file.delete()
                return ImportResult.Duplicate(duplicate)
            }
            try {
                saveSnapshot(current.copy(items = current.items + item))
            } catch (failure: Throwable) {
                stored.file.delete()
                throw failure
            }
        }
        return ImportResult.Imported(item)
    }

    /**
     * Decrypts directly into a pending MediaStore entry, verifies that entry,
     * and only then publishes it. The encrypted vault item is retained.
     */
    fun restore(item: VaultItem): Uri {
        val collection = if (item.mimeType.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (item.mimeType.startsWith("video/")) "Movies" else "Pictures")
            }
        }
        val destination = checkNotNull(resolver.insert(collection, values)) { "Unable to create restored media" }
        try {
            resolver.openOutputStream(destination, "w")?.use { output ->
                FileInputStream(payloadFile(item)).use { encrypted ->
                    VaultCipher.decrypt(
                        encrypted,
                        output,
                        vaultKey,
                        item.id.encodeToByteArray(),
                        EncryptionHeader(item.payloadNonce),
                    )
                }
            } ?: error("Unable to write restored media")
            check(verifyMediaStore(destination, item)) { "Restored media verification failed" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(destination, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            return destination
        } catch (failure: Throwable) {
            resolver.delete(destination, null, null)
            throw failure
        }
    }

    /** The vault is removed only after [restore] has returned a verified URI. */
    fun restoreAndRemove(item: VaultItem): Uri = restore(item).also { deleteFromVault(item) }

    fun deleteFromVault(item: VaultItem) {
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            val retired = payloads.retireForDeletion(item.id)
            try {
                saveSnapshot(VaultCollectionsState(current).removeVaultItem(item.id).asSnapshot())
            } catch (failure: Throwable) {
                payloads.restoreRetiredPayload(item.id)
                throw failure
            }
            retired.delete()
        }
    }

    /** Removes interrupted ciphertext only; source gallery media is untouched. */
    fun reconcile() {
        payloads.reconcileInterruptedWrites()
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            payloads.reconcileInterruptedDeletes(current.items.mapTo(mutableSetOf()) { it.id })
            if (current.items.any { it.state == VaultItemState.DELETE_PENDING }) {
                saveSnapshot(
                    current.copy(items = current.items.map {
                        if (it.state == VaultItemState.DELETE_PENDING) it.copy(state = VaultItemState.COMPLETE) else it
                    }),
                )
            }
        }
    }

    private fun replaceState(id: String, state: VaultItemState) {
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            saveSnapshot(current.copy(items = current.items.map { if (it.id == id) it.copy(state = state) else it }))
        }
    }

    private fun snapshot(): VaultIndexSnapshot = index.loadSnapshot(vaultKey)

    private fun saveSnapshot(snapshot: VaultIndexSnapshot) = index.saveSnapshot(snapshot, vaultKey)

    private fun <T> mutateCollections(block: (VaultCollectionsState) -> Pair<VaultCollectionsState, T>): T = synchronized(METADATA_LOCK) {
        val (updated, value) = block(VaultCollectionsState(snapshot()))
        saveSnapshot(updated.asSnapshot())
        value
    }

    private fun verifyMediaStore(uri: Uri, item: VaultItem): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        resolver.openInputStream(uri)?.use { input ->
            DigestInputStream(input, digest).use { digesting ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                while (true) {
                    val read = digesting.read(buffer)
                    if (read < 0) break
                    count += read
                }
            }
        } ?: return false
        return count == item.plaintextSize && digest.digest().contentEquals(item.plaintextSha256)
    }

    private fun payloadFile(item: VaultItem): File = File(File(root, "payloads"), item.id + ".vault")

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: ("media-" + System.currentTimeMillis())

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
        val METADATA_LOCK = Any()
    }
}
