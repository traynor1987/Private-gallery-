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
import java.io.FilterInputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.io.OutputStream
import java.util.UUID
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.PrimaryOperation
import uk.co.traynor.privategallery.core.security.ScopedItemHandle
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore

sealed interface ImportResult {
    data class Imported(val item: VaultItem) : ImportResult
    data class Duplicate(val existing: VaultItem) : ImportResult
}

/** Android adapter for local-first vault operations. */
class AndroidVaultRepository(
    private val context: Context,
    private val operation: PrimaryOperation,
) : VaultImportSink {
    private val vaultKey: ByteArray get() { operation.checkValid(); return operation.key }
    private fun checkValid() = operation.checkValid()
    private fun cancelledBy(cancelled: () -> Boolean): () -> Boolean = { !operation.isCurrent || cancelled() }
    private fun verifiedBytes(read: () -> ByteArray): ByteArray {
        checkValid()
        val bytes = read()
        try {
            checkValid()
            val reference = java.lang.ref.WeakReference(bytes)
            operation.ownForSession(AutoCloseable { reference.get()?.fill(0) })
            return bytes
        } catch (failure: Throwable) { bytes.fill(0); throw failure }
    }
    private fun revision(item: VaultItem) = item.plaintextSha256.joinToString("") { "%02x".format(it) }
    private fun bind(item: VaultItem) = item.bind(operation.handle(item.id, revision(item)))
    fun scopedHandle(item: VaultItem): ScopedItemHandle = checkNotNull(item.scopedHandle) { "Unscoped Primary item" }.also(operation::validate)
    private fun resolve(handle: ScopedItemHandle): VaultItem {
        var recorded: VaultItem? = null
        return operation.resolve(handle, {
            snapshot().items.single { it.id == handle.itemId }.also { recorded = it }.let(::revision)
        }) { checkNotNull(recorded) }
    }
    private fun resolve(item: VaultItem) = resolve(scopedHandle(item))
    fun validateItem(item: VaultItem): ScopedItemHandle = scopedHandle(resolve(item))
    fun readForViewing(handle: ScopedItemHandle, cancelled: () -> Boolean = { false }): ByteArray = readForViewing(resolve(handle), cancelled)
    fun readForEditing(handle: ScopedItemHandle, cancelled: () -> Boolean): ByteArray = readForEditing(resolve(handle), cancelled)
    fun deleteFromVault(handle: ScopedItemHandle) = deleteFromVault(resolve(handle))
    fun prepareBrowserUpload(handle: ScopedItemHandle, destination: File, cancelled: () -> Boolean) = prepareBrowserUpload(resolve(handle), destination, cancelled)

    private val container = uk.co.traynor.privategallery.core.security.LegacyPrimaryContainer(context)
    private val root = container.root
    private val payloads = EncryptedPayloadStore(root, registerResource = { operation.own(it) })
    private val index = EncryptedIndexStore(root)
    private val resolver: ContentResolver = context.contentResolver

    fun items(): List<VaultItem> = snapshot().items.filter { it.state != VaultItemState.TRASHED }
        .sortedByDescending { it.importedAtEpochMillis }

    fun recentlyDeleted(): List<VaultItem> = snapshot().items.filter { it.state == VaultItemState.TRASHED }
        .sortedByDescending { it.deletedAtEpochMillis }

    /** Keeps ciphertext and encrypted collection membership for 30 days. */
    fun moveToRecentlyDeleted(handle: ScopedItemHandle, now: Long = System.currentTimeMillis()) = synchronized(METADATA_LOCK) {
        val itemId = resolve(handle).id
        require(now > 0)
        val current = snapshot()
        check(current.items.any { it.id == itemId && it.state == VaultItemState.COMPLETE }) { "Vault item is unavailable" }
        saveSnapshot(current.copy(items = current.items.map {
            if (it.id == itemId) it.copy(state = VaultItemState.TRASHED, deletedAtEpochMillis = now) else it
        }))
    }

    fun restoreRecentlyDeleted(handle: ScopedItemHandle) = synchronized(METADATA_LOCK) {
        val itemId = resolve(handle).id
        val current = snapshot()
        check(current.items.any { it.id == itemId && it.state == VaultItemState.TRASHED }) { "Deleted item is unavailable" }
        saveSnapshot(current.copy(items = current.items.map {
            if (it.id == itemId) it.copy(state = VaultItemState.COMPLETE, deletedAtEpochMillis = null) else it
        }))
    }

    fun deleteExpiredRecentlyDeleted(now: Long = System.currentTimeMillis()) = synchronized(METADATA_LOCK) {
        snapshot().items.filter { it.state == VaultItemState.TRASHED &&
            RecentlyDeletedPolicy.expired(it.deletedAtEpochMillis, now) }
            .forEach { deleteFromVault(it) }
    }

    /** Holds metadata stable while ciphertext is authenticated and copied to a user-selected document. */
    fun exportBackup(recoveryKey: CharArray, envelope: RecoveryWrappedKey, output: OutputStream,
                     progress: (Int, Int) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }) = synchronized(METADATA_LOCK) {
        operation.requireScope(PrimaryScope.BACKUP)
        operation.requireScope(PrimaryScope.EGRESS)
        val recovered = RecoveryEnvelope.unwrap(recoveryKey, envelope)
        try {
            check(MessageDigest.isEqual(recovered, vaultKey)) { "Recovery key does not match this Vault" }
            snapshot() // Fail closed before creating metadata if ciphertext exists without its index.
            if (!File(root, "vault-index.enc").exists()) index.saveSnapshot(VaultIndexSnapshot(emptyList()), vaultKey, operation::commit)
            VaultBackupArchive.write(root, vaultKey, envelope, operation.own(output), progress, cancelledBy(cancelled))
        } finally { recovered.fill(0) }
    }

    fun collections(): List<VaultCollection> = snapshot().collections.sortedBy { it.createdAtEpochMillis }

    fun itemsInCollection(collectionId: String): List<VaultItem> = VaultCollectionsState(snapshot()).itemsIn(collectionId)

    /** The edit ledger is encrypted metadata; it never changes the payload file. */
    fun imageEdit(handle: ScopedItemHandle): ImageEditState? = snapshot().imageEdits[resolve(handle).id]

    fun applyImageCrop(handle: ScopedItemHandle, crop: NormalizedCrop): ImageEditState = synchronized(METADATA_LOCK) {
        val itemId = resolve(handle).id
        val current = snapshot()
        val item = current.items.firstOrNull { it.id == itemId } ?: error("Unknown Vault item")
        require(item.mimeType.startsWith("image/")) { "Only images can be cropped" }
        val prior = current.imageEdits[itemId]?.crop
        val updated = ImageEditState(crop = crop, previousCrop = prior)
        saveSnapshot(current.copy(imageEdits = current.imageEdits + (itemId to updated)))
        updated
    }

    /** Restores the immediately preceding crop, or the original image. */
    fun undoImageCrop(handle: ScopedItemHandle): ImageEditState? = synchronized(METADATA_LOCK) {
        val itemId = resolve(handle).id
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
    fun resetImageCrop(handle: ScopedItemHandle) = synchronized(METADATA_LOCK) {
        val itemId = resolve(handle).id
        val current = snapshot()
        if (itemId !in current.imageEdits) return@synchronized
        saveSnapshot(current.copy(imageEdits = current.imageEdits - itemId))
    }

    /** Applies legacy migration without manufacturing a collection on fresh installations. */
    fun migrateLegacyFavourite(): VaultCollection? = mutateCollections { state ->
        val updated = state.migrateLegacyFavourite()
        updated to updated.favouriteCollectionId?.let { id -> updated.collections.singleOrNull { it.id == id } }
    }

    /** Compatibility entry point for the pre-favourite UI; it no longer creates Jenna. */
    @Deprecated("Use migrateLegacyFavourite or favouriteCollection")
    fun ensureJennaCollection(): VaultCollection? = migrateLegacyFavourite()

    fun favouriteCollection(): VaultCollection? = VaultCollectionsState(snapshot()).let { state ->
        state.favouriteCollectionId?.let { id -> state.collections.singleOrNull { it.id == id } }
    }

    fun setFavouriteCollection(collectionId: String) {
        mutateCollections { state -> state.setFavourite(collectionId) to Unit }
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

    fun addItemsToCollection(collectionId: String, handles: Collection<ScopedItemHandle>) {
        val itemIds = handles.map { resolve(it).id }
        mutateCollections { state -> state.addItems(collectionId, itemIds) to Unit }
    }

    /** Removes organisation membership only. Underlying encrypted payloads and VaultItems are retained. */
    fun removeItemFromCollection(collectionId: String, handle: ScopedItemHandle) {
        val itemId = resolve(handle).id
        mutateCollections { state -> state.removeItem(collectionId, itemId) to Unit }
    }

    /**
     * Returns authenticated plaintext only in process memory for protected viewing.
     * Callers must discard the returned bytes when their viewer closes.
     */
    fun readForViewing(item: VaultItem, cancelled: () -> Boolean = { false }): ByteArray = verifiedBytes {
        val item = resolve(item)
        payloads.decryptToBytes(
        StoredPayload(
            id = item.id,
            file = payloadFile(item),
            plaintextSize = item.plaintextSize,
            plaintextSha256 = item.plaintextSha256,
            nonce = item.payloadNonce,
        ),
        vaultKey,
        cancelledBy(cancelled),
    ) }

    fun prepareBrowserUpload(item: VaultItem, destination: File, cancelled: () -> Boolean) {
        operation.requireScope(PrimaryScope.EGRESS)
        val item = resolve(item)
        requireEgress(item.id, VaultEgress.SHARE)
        val current = items().firstOrNull { it.id == item.id && it.state == VaultItemState.COMPLETE }
            ?: throw java.io.IOException("Vault item unavailable")
        operation.ownForSession(AutoCloseable { destination.delete() })
        payloads.decryptToVerifiedFile(
            StoredPayload(current.id, payloadFile(current), current.plaintextSize, current.plaintextSha256, current.payloadNonce),
            vaultKey, destination, 256L * 1024 * 1024, cancelledBy(cancelled),
        )
    }

    fun readVideoForViewing(item: VaultItem, cancelled: () -> Boolean, progress: (Int) -> Unit): ByteArray = verifiedBytes {
        val item = resolve(item)
        payloads.decryptWithProgress(
            StoredPayload(item.id, payloadFile(item), item.plaintextSize, item.plaintextSha256, item.payloadNonce),
            vaultKey, cancelledBy(cancelled), progress,
        ) }

    /** Migrates legacy ciphertext in bounded memory, then serves authenticated chunks to Media3. */
    fun openVideoSession(item: VaultItem, allowed: () -> Boolean, cancelled: () -> Boolean): VaultVideoSession = synchronized(METADATA_LOCK) {
        val item = resolve(item)
        val recorded = snapshot().items.singleOrNull { it.id == item.id && it.state == VaultItemState.COMPLETE }
            ?: error("Vault video unavailable")
        require(recorded.mimeType.startsWith("video/"))
        val stored = StoredPayload(recorded.id, payloadFile(recorded), recorded.plaintextSize,
            recorded.plaintextSha256, recorded.payloadNonce)
        payloads.migrateLegacyVideo(stored, vaultKey, cancelledBy(cancelled), operation::commit)
        check(allowed()) { "Vault locked" }
        operation.ownForSession(VaultVideoSession(stored, vaultKey) { operation.isCurrent && allowed() })
    }

    fun readForEditingPreview(item: VaultItem, cancelled: () -> Boolean): ByteArray = verifiedBytes {
        val item = resolve(item)
        payloads.decryptToBoundedBytes(
        StoredPayload(item.id, payloadFile(item), item.plaintextSize, item.plaintextSha256, item.payloadNonce),
        vaultKey, 64 * 1024 * 1024, cancelledBy(cancelled),
    ) }

    fun readForEditing(item: VaultItem, cancelled: () -> Boolean): ByteArray = verifiedBytes {
        val item = resolve(item)
        payloads.decryptToBoundedBytes(
        StoredPayload(item.id, payloadFile(item), item.plaintextSize, item.plaintextSha256, item.payloadNonce),
        vaultKey, uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES, cancelledBy(cancelled),
    ) }

    fun markDeletePending(item: VaultItem) {
        val item = resolve(item)
        replaceState(item.id, VaultItemState.DELETE_PENDING)
    }

    fun finishSourceDeletionRequest(item: VaultItem, approved: Boolean) {
        val item = resolve(item)
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
    fun import(uri: Uri): ImportResult = importVerified(
        VaultImportSource(
            displayName = displayName(uri),
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            openStream = { checkNotNull(resolver.openInputStream(uri)) { "Selected media is unavailable" } },
            sourceReference = uri.toString(),
        ),
    )

    /** Derivative provenance is assigned against the encrypted parent record, below the UI. */
    fun importEditedCopy(parent: ScopedItemHandle, bytes: ByteArray, remoteAi: Boolean, keepAiInVault: Boolean, cancelled: () -> Boolean): VaultItem {
        return importAiEditedCopy(parent, bytes, if (remoteAi) uk.co.traynor.privategallery.core.editor.AiEditProvenance(uk.co.traynor.privategallery.core.editor.AiProcessing.CLOUD, "legacy-cloud", null) else null, keepAiInVault, cancelled)
    }

    fun importAiEditedCopy(parent: ScopedItemHandle, bytes: ByteArray, ai: uk.co.traynor.privategallery.core.editor.AiEditProvenance?, keepAiInVault: Boolean, cancelled: () -> Boolean): VaultItem {
        val parent = resolve(parent)
        val parentId = parent.id
        require(parent.mimeType.startsWith("image/"))
        val source = VaultImportSource(
            displayName = "edited-photo.png", mimeType = "image/png", openStream = { java.io.ByteArrayInputStream(bytes) },
            sourceReference = "editedFrom:$parentId" + (ai?.let { "|ai:${it.providerId}|model:${it.modelId.orEmpty()}" } ?: ""), createDistinctCopy = true, isCancelled = cancelled,
            origin = when {
                ai?.processing == uk.co.traynor.privategallery.core.editor.AiProcessing.ON_DEVICE -> MediaOrigin.LOCAL_AI_EDIT
                ai != null -> MediaOrigin.REMOTE_AI_EDIT
                parent.origin in setOf(MediaOrigin.REMOTE_AI_EDIT, MediaOrigin.LOCAL_AI_EDIT) -> parent.origin
                else -> MediaOrigin.LOCAL_EDIT
            },
            vaultOnly = VaultEgressPolicy.derivativeRestricted(parent, ai != null, keepAiInVault),
        )
        return (VaultImportCoordinator(this).acquire(source) as ImportResult.Imported).item
    }

    /** A new synthetic image has no parent; egress policy is committed with encrypted metadata. */
    fun importAiGeneratedImage(bytes: ByteArray, modelId: String, keepAiInVault: Boolean, cancelled: () -> Boolean): VaultItem {
        require(bytes.size in 1024..(32 * 1024 * 1024) && modelId.matches(Regex("[a-z0-9/.:\\-]{1,160}")))
        val source = VaultImportSource("generated-image.png", "image/png", { java.io.ByteArrayInputStream(bytes) },
            sourceReference = "ai:replicate|model:$modelId", origin = MediaOrigin.REMOTE_AI_GENERATED,
            vaultOnly = keepAiInVault, createDistinctCopy = true, isCancelled = cancelled)
        return (VaultImportCoordinator(this).acquire(source) as ImportResult.Imported).item
    }

    override fun importVerified(source: VaultImportSource): ImportResult = synchronized(PRIMARY_IO_LOCK) importScope@ {
        operation.requireScope(PrimaryScope.WRITE)
        checkValid()
        val initial = snapshot()
        val id = UUID.randomUUID().toString()
        val prefix = ByteArrayOutputStream(64)
        val io = uk.co.traynor.privategallery.core.security.ScopedIoGuard(operation, PrimaryScope.WRITE)
        io.check()
        val stored = io.input(source.openScopedStream?.invoke(io) ?: source.openStream()).use { input ->
            payloads.writeAndVerify(id, PrefixCapturingInputStream(input, prefix), vaultKey,
                chunkedVideo = source.mimeType.startsWith("video/"), commit = { action ->
                    if (source.isCancelled()) throw java.io.IOException("Vault acquisition cancelled")
                    operation.commit(action)
                })
        }
        val item = VaultItem(
            id = id,
            mimeType = VaultMimePolicy.effectiveType(source.mimeType, source.displayName, prefix.toByteArray()),
            displayName = source.displayName,
            importedAtEpochMillis = System.currentTimeMillis(),
            plaintextSize = stored.plaintextSize,
            plaintextSha256 = stored.plaintextSha256,
            payloadNonce = stored.nonce,
            state = VaultItemState.COMPLETE,
            sourceUri = source.sourceReference,
            origin = source.origin, vaultOnly = source.vaultOnly,
        )
        synchronized(METADATA_LOCK) {
            val current = if (File(root, "vault-index.enc").exists()) snapshot() else initial
            current.items.firstOrNull { !source.createDistinctCopy && it.state != VaultItemState.TRASHED && it.plaintextSha256.contentEquals(stored.plaintextSha256) }?.let { duplicate ->
                stored.file.delete()
                return@importScope ImportResult.Duplicate(duplicate)
            }
            try {
                if (source.isCancelled()) throw java.io.IOException("Vault acquisition cancelled")
                saveSnapshot(current.copy(items = current.items + item))
            } catch (failure: Throwable) {
                stored.file.delete()
                throw failure
            }
        }
        ImportResult.Imported(bind(item))
    }

    /** Captures only bytes already streaming into encrypted staging; no plaintext file exists. */
    private class PrefixCapturingInputStream(delegate: InputStream, private val prefix: ByteArrayOutputStream) : FilterInputStream(delegate) {
        override fun read(): Int = super.read().also { value -> if (value >= 0 && prefix.size() < 64) prefix.write(value) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { count ->
            if (count > 0 && prefix.size() < 64) prefix.write(buffer, offset, minOf(count, 64 - prefix.size()))
        }
    }

    /**
     * Decrypts directly into a pending MediaStore entry, verifies that entry,
     * and only then publishes it. The encrypted vault item is retained.
     */
    /** Authoritative encrypted metadata is resolved here; callers cannot pass an unrestricted copy. */
    fun requireEgress(handle: ScopedItemHandle, action: VaultEgress): VaultItem = requireEgress(resolve(handle).id, action)

    private fun requireEgress(itemId: String, action: VaultEgress): VaultItem {
        operation.requireScope(PrimaryScope.EGRESS)
        val recorded = snapshot().items.singleOrNull { it.id == itemId } ?: error("Unknown Vault item")
        check(recorded.state != VaultItemState.TRASHED) { "Item is in Recently Deleted" }
        VaultEgressPolicy.requireAllowed(recorded, action)
        return recorded
    }

    fun restore(item: VaultItem, cancelled: () -> Boolean = { false }, publishIfAllowed: ((() -> Unit) -> Unit) = { it() }): Uri =
        restoreAllowed(requireEgress(resolve(item).id, VaultEgress.RESTORE), cancelled, publishIfAllowed)

    private fun restoreAllowed(item: VaultItem, cancelled: () -> Boolean, publishIfAllowed: ((() -> Unit) -> Unit)): Uri {
        fun checkActive() { checkValid(); if (cancelled()) throw java.io.IOException("Restore cancelled") }
        checkActive()
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
            checkActive()
            resolver.openOutputStream(destination, "w")?.let(operation::own)?.use { output ->
                val guarded = object : java.io.FilterOutputStream(output) {
                    override fun write(b: Int) { checkActive(); out.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) { checkActive(); out.write(b, off, len) }
                }
                val stored = StoredPayload(item.id, payloadFile(item), item.plaintextSize, item.plaintextSha256, item.payloadNonce)
                if (ChunkedVaultVideoStore.isChunked(stored.file)) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    operation.own(ChunkedVaultVideoStore.open(stored, vaultKey)).use { reader ->
                        val buffer = ByteArray(64 * 1024)
                        var position = 0L
                        try {
                            while (position < reader.size) {
                                checkActive()
                                val count = reader.readAt(position, buffer, 0, buffer.size)
                                check(count > 0)
                                digest.update(buffer, 0, count)
                                guarded.write(buffer, 0, count)
                                position += count
                            }
                        } finally { buffer.fill(0) }
                    }
                    check(digest.digest().contentEquals(item.plaintextSha256)) { "Restored video verification failed" }
                } else operation.own(FileInputStream(payloadFile(item))).use { encrypted ->
                    VaultCipher.decrypt(encrypted, guarded, vaultKey, item.id.encodeToByteArray(), EncryptionHeader(item.payloadNonce))
                }
            } ?: error("Unable to write restored media")
            checkActive()
            check(verifyMediaStore(destination, item)) { "Restored media verification failed" }
            publishIfAllowed {
                checkActive()
                operation.commit { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(destination, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } }
            }
            return destination
        } catch (failure: Throwable) {
            resolver.delete(destination, null, null)
            throw failure
        }
    }

    /** The vault is removed only after [restore] has returned a verified URI. */
    fun restoreAndRemove(item: VaultItem, cancelled: () -> Boolean = { false }, publishIfAllowed: ((() -> Unit) -> Unit) = { it() }): Uri =
        restore(item, cancelled, publishIfAllowed).also {
            if (!cancelled()) deleteFromVault(item)
        }

    fun deleteFromVault(item: VaultItem) {
        operation.requireScope(PrimaryScope.WRITE)
        val item = resolve(item)
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            val retired = operation.commit { payloads.retireForDeletion(item.id) }
            try {
                saveSnapshot(VaultCollectionsState(current).removeVaultItem(item.id).asSnapshot())
            } catch (failure: Throwable) {
                payloads.restoreRetiredPayload(item.id)
                throw failure
            }
            operation.commit { retired.delete() }
            uk.co.traynor.privategallery.core.media.EncryptedPreviewCache(File(root, "previews")).let { cache -> cache.remove(item.id); cache.remove("primary:${item.id}") }
        }
    }

    /** Removes interrupted ciphertext only; source gallery media is untouched. */
    fun reconcile() = synchronized(PRIMARY_IO_LOCK) {
        checkValid()
        operation.commit { payloads.reconcileInterruptedWrites() }
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            payloads.reconcileVideoMigrations(current.items, vaultKey, { !operation.isCurrent }, operation::commit)
            payloads.reconcileInterruptedDeletes(current.items.mapTo(mutableSetOf()) { it.id }, operation::commit)
            if (current.items.any { it.state == VaultItemState.DELETE_PENDING }) {
                saveSnapshot(
                    current.copy(items = current.items.map {
                        if (it.state == VaultItemState.DELETE_PENDING) it.copy(state = VaultItemState.COMPLETE) else it
                    }),
                )
            }
            deleteExpiredRecentlyDeleted()
        }
    }

    private fun replaceState(id: String, state: VaultItemState) {
        synchronized(METADATA_LOCK) {
            val current = snapshot()
            saveSnapshot(current.copy(items = current.items.map { if (it.id == id) it.copy(state = state) else it }))
        }
    }

    private fun snapshot(): VaultIndexSnapshot {
        operation.requireScope(PrimaryScope.READ)
        checkValid()
        val value = index.loadSnapshot(vaultKey)
        checkValid()
        return value.copy(items = value.items.map(::bind))
    }

    private fun saveSnapshot(snapshot: VaultIndexSnapshot) {
        operation.requireScope(PrimaryScope.WRITE)
        index.saveSnapshot(snapshot, vaultKey, operation::commit)
    }

    private fun <T> mutateCollections(block: (VaultCollectionsState) -> Pair<VaultCollectionsState, T>): T = synchronized(METADATA_LOCK) {
        val (updated, value) = block(VaultCollectionsState(snapshot()))
        saveSnapshot(updated.asSnapshot())
        value
    }

    private fun verifyMediaStore(uri: Uri, item: VaultItem): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        resolver.openInputStream(uri)?.let(operation::own)?.use { input ->
            DigestInputStream(input, digest).use { digesting ->
                val buffer = ByteArray(DEFAULT_BUFFER)
                try {
                    while (true) {
                        checkValid()
                        val read = digesting.read(buffer)
                        if (read < 0) break
                        count += read
                    }
                    checkValid()
                } finally { buffer.fill(0) }
            }
        } ?: return false
        return count == item.plaintextSize && digest.digest().contentEquals(item.plaintextSha256)
    }

    private fun payloadFile(item: VaultItem): File {
        checkValid()
        require(item.id.matches(Regex("[A-Za-z0-9_-]{1,160}"))) { "Invalid Primary object identifier" }
        return File(File(root, "payloads"), item.id + ".vault")
    }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: ("media-" + System.currentTimeMillis())

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        private val METADATA_LOCK = Any()
        private val PRIMARY_IO_LOCK = Any()
        /** Fresh installation only. No existing Vault or configured device key is overwritten. */
        fun restoreBackup(context: Context, input: InputStream, recoveryKey: CharArray, newPin: CharArray,
                          keys: PinVaultKeyStore, recoveryKeys: RecoveryVaultKeyStore, commit: ((() -> Unit) -> Unit) = { it() }): ByteArray = synchronized(PRIMARY_IO_LOCK) {
            require(newPin.size >= 6 && newPin.all(Char::isDigit)) { "Choose a PIN of at least six digits" }
            val container = uk.co.traynor.privategallery.core.security.LegacyPrimaryContainer(context)
            val root = container.root
            val stage = container.restoreStaging
            check(!keys.hasEnvelopeMaterial) { "Vault is already configured" }
            if (stage.exists()) check(stage.deleteRecursively()) { "Unable to clear interrupted restore" }
            var rollbackPin: (() -> Unit)? = null
            var recoveryInstalled = false
            try {
                val restored = VaultBackupArchive.read(input, stage, recoveryKey)
                try {
                    if (root.exists()) {
                        // A process may have stopped after the ciphertext directory was renamed,
                        // before its device key envelopes were saved. Only the same authenticated
                        // archive can finish that transaction; never overwrite existing ciphertext.
                        check(matchesExistingBackup(root, stage)) { "Existing Vault differs from selected backup" }
                        stage.deleteRecursively()
                    } else {
                        commit {
                            check(stage.renameTo(root)) { "Unable to install restored Vault" }
                        }
                    }
                    if (recoveryKeys.isConfigured) {
                        val existing = recoveryKeys.exportEnvelope()
                        check(MessageDigest.isEqual(existing.salt, restored.recoveryEnvelope.salt) &&
                            MessageDigest.isEqual(existing.nonce, restored.recoveryEnvelope.nonce) &&
                            MessageDigest.isEqual(existing.ciphertext, restored.recoveryEnvelope.ciphertext)) {
                            "Existing recovery envelope differs from selected backup"
                        }
                    } else {
                        commit {
                            recoveryKeys.installForRestoredVault(restored.recoveryEnvelope)
                            recoveryInstalled = true
                        }
                    }
                    commit {
                        rollbackPin = keys.installPinForRestoredVault(newPin, restored.key)
                    }
                    return@synchronized restored.key.copyOf()
                } finally { restored.key.fill(0) }
            } catch (failure: Throwable) {
                rollbackPin?.invoke()
                if (recoveryInstalled) recoveryKeys.clearFailedRestore()
                // Keep authenticated installed ciphertext if an envelope/attempt later fails.
                // A retry must authenticate the same archive and match this root exactly.
                // Serialized transaction ownership prevents a late rollback touching a newer restore.
                stage.deleteRecursively()
                throw failure
            } finally { newPin.fill('\u0000'); recoveryKey.fill('\u0000') }
        }

        private fun matchesExistingBackup(existing: File, staged: File): Boolean {
            fun digest(file: File): ByteArray = MessageDigest.getInstance("SHA-256").let { hash ->
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        hash.update(buffer, 0, count)
                    }
                }
                hash.digest()
            }
            fun files(root: File): Map<String, File> = buildMap {
                File(root, "vault-index.enc").takeIf { it.isFile }?.let { put("vault-index.enc", it) }
                File(root, "payloads").listFiles()?.filter { it.isFile && it.name.endsWith(".vault") }
                    ?.forEach { put("payloads/${it.name}", it) }
            }
            val current = files(existing)
            val incoming = files(staged)
            if (current.keys != incoming.keys || "vault-index.enc" !in current) return false
            return current.all { (name, file) ->
                val candidate = checkNotNull(incoming[name])
                file.length() == candidate.length() &&
                    MessageDigest.isEqual(digest(file), digest(candidate))
            }
        }
    }
}
