package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher

enum class VaultItemState { IMPORTING, VERIFIED, DELETE_PENDING, COMPLETE, FAILED, TRASHED }

data class VaultItem(
    val id: String,
    val mimeType: String,
    val displayName: String,
    val importedAtEpochMillis: Long,
    val plaintextSize: Long,
    val plaintextSha256: ByteArray,
    val payloadNonce: ByteArray,
    val state: VaultItemState,
    val sourceUri: String? = null,
    val origin: MediaOrigin = MediaOrigin.IMPORTED,
    val vaultOnly: Boolean = false,
    val deletedAtEpochMillis: Long? = null,
) {
    /** In-process authority only: never part of legacy serialization, equality or copy(). */
    internal var scopedHandle: uk.co.traynor.privategallery.core.security.ScopedItemHandle? = null
        private set
    internal fun bind(handle: uk.co.traynor.privategallery.core.security.ScopedItemHandle): VaultItem {
        check(scopedHandle == null) { "Vault item already bound" }
        scopedHandle = handle
        return this
    }
}

/**
 * Atomic AES-GCM encrypted Vault metadata ledger. v3 adds non-destructive
 * image edit metadata to v2's collections/memberships; v4 adds one generic
 * favourite collection ID. All prior formats are
 * readable and are rewritten only during a later metadata mutation.
 */
class EncryptedIndexStore(
    private val root: File,
    private val syncOutput: (FileOutputStream) -> Unit = { it.fd.sync() },
    private val faults: PrimaryWriteFaults = PrimaryWriteFaults.NONE,
) {
    private val index = File(root, "vault-index.enc")
    private val temporary = File(root, "vault-index.new")

    fun load(key: ByteArray): List<VaultItem> = loadSnapshot(key).items

    fun loadSnapshot(key: ByteArray): VaultIndexSnapshot {
        if (!index.exists()) {
            check(uk.co.traynor.privategallery.core.security.PrimaryStorageInventory.isEmptyOrMissing(root.toPath())) {
                "Primary index missing for existing, partial or unavailable material"
            }
            return VaultIndexSnapshot(emptyList())
        }
        require(index.length() in (EncryptionHeader.NONCE_BYTES + 16L)..MAX_ENCRYPTED_BYTES) { "Invalid encrypted vault index length" }
        FileInputStream(index).use { input ->
            val nonce = input.readExactly(EncryptionHeader.NONCE_BYTES)
            check(nonce.size == EncryptionHeader.NONCE_BYTES) { "Corrupt vault index header" }
            val plain = ByteArrayOutputStream()
            VaultCipher.decrypt(input, object : java.io.OutputStream() {
                override fun write(value: Int) { require(plain.size().toLong() < MAX_PLAINTEXT_BYTES) { "Vault index exceeds size limit" }; plain.write(value) }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    require(plain.size().toLong() + length <= MAX_PLAINTEXT_BYTES) { "Vault index exceeds size limit" }
                    plain.write(buffer, offset, length)
                }
            }, key, INDEX_AAD, EncryptionHeader(nonce))
            val bytes = plain.toByteArray()
            return try {
                deserializeSnapshot(bytes)
            } finally {
                bytes.fill(0)
                plain.reset()
            }
        }
    }

    /** Retained for legacy callers and fixtures; writes the compatible v1 item-only encoding. */
    fun save(items: List<VaultItem>, key: ByteArray, commit: ((() -> Unit) -> Unit) = { it() }) {
        require(items.none { it.vaultOnly || it.origin != MediaOrigin.IMPORTED || it.deletedAtEpochMillis != null || it.state == VaultItemState.TRASHED }) { "Metadata requires the versioned index" }
        saveEncrypted(serializeLegacy(items), key, commit)
    }

    fun saveSnapshot(snapshot: VaultIndexSnapshot, key: ByteArray, commit: ((() -> Unit) -> Unit) = { it() }) = saveEncrypted(serializeSnapshot(snapshot), key, commit)

    private fun saveEncrypted(plaintext: ByteArray, key: ByteArray, commit: ((() -> Unit) -> Unit)) {
        require(plaintext.size.toLong() <= MAX_PLAINTEXT_BYTES) { "Vault index exceeds size limit" }
        root.mkdirs()
        val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
        try {
            faults.checkpoint(WriteCheckpoint.INDEX_BEFORE_WRITE, temporary)
            FileOutputStream(temporary).use { output ->
                output.write(nonce)
                VaultCipher.encrypt(ByteArrayInputStream(plaintext), output, key, INDEX_AAD, nonce)
                faults.checkpoint(WriteCheckpoint.INDEX_AFTER_WRITE, temporary)
                faults.checkpoint(WriteCheckpoint.INDEX_BEFORE_SYNC, temporary)
                syncOutput(output)
                faults.checkpoint(WriteCheckpoint.INDEX_AFTER_SYNC, temporary)
            }
            faults.checkpoint(WriteCheckpoint.INDEX_BEFORE_VERIFY, temporary)
            verifyStagedIndex(plaintext, key)
            faults.checkpoint(WriteCheckpoint.INDEX_AFTER_VERIFY, temporary)
            var promoted = false
            commit {
                faults.checkpoint(WriteCheckpoint.INDEX_BEFORE_PROMOTION, temporary)
                check(temporary.renameTo(index)) { "Unable to commit encrypted vault index" }
                promoted = true
                faults.checkpoint(WriteCheckpoint.INDEX_AFTER_PROMOTION, index)
            }
            check(promoted) { "Encrypted vault index commit did not execute" }
        } finally {
            plaintext.fill(0)
            temporary.delete()
        }
    }

    /** Authenticate read-back before replacing the sole committed legacy index. */
    private fun verifyStagedIndex(expected: ByteArray, key: ByteArray) {
        var position = 0
        FileInputStream(temporary).use { input ->
            val nonce = input.readExactly(EncryptionHeader.NONCE_BYTES)
            VaultCipher.decrypt(input, object : java.io.OutputStream() {
                override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    check(position.toLong() + length <= expected.size) { "Invalid staged vault index length" }
                    for (index in 0 until length) check(buffer[offset + index] == expected[position + index]) { "Staged vault index differs" }
                    position += length
                }
            }, key, INDEX_AAD, EncryptionHeader(nonce))
        }
        check(position == expected.size) { "Incomplete staged vault index" }
    }

    private fun serializeLegacy(items: List<VaultItem>): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(items.size)
            output.writeItems(items)
        }
        buffer.toByteArray()
    }

    private fun serializeSnapshot(snapshot: VaultIndexSnapshot): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(FORMAT_MARKER)
            output.writeInt(FORMAT_VERSION_6)
            output.writeInt(snapshot.items.size)
            output.writeItems(snapshot.items, withProvenance = true, withDeletion = true)
            output.writeInt(snapshot.collections.size)
            snapshot.collections.forEach { collection ->
                output.writeUTF(collection.id)
                output.writeUTF(collection.name)
                output.writeLong(collection.createdAtEpochMillis)
                output.writeInt(collection.pinnedDestination?.ordinal ?: NO_PINNED_DESTINATION)
                output.writeBoolean(collection.coverVaultItemId != null)
                collection.coverVaultItemId?.let(output::writeUTF)
            }
            output.writeInt(snapshot.memberships.size)
            snapshot.memberships.forEach { membership ->
                output.writeUTF(membership.collectionId)
                output.writeUTF(membership.vaultItemId)
                output.writeLong(membership.addedAtEpochMillis)
            }
            output.writeInt(snapshot.imageEdits.size)
            snapshot.imageEdits.toSortedMap().forEach { (itemId, edit) ->
                output.writeUTF(itemId)
                output.writeCrop(edit.crop)
                output.writeBoolean(edit.previousCrop != null)
                edit.previousCrop?.let { previous -> output.writeCrop(previous) }
            }
            output.writeBoolean(snapshot.favouriteCollectionId != null)
            snapshot.favouriteCollectionId?.let(output::writeUTF)
        }
        buffer.toByteArray()
    }

    private fun deserializeSnapshot(bytes: ByteArray): VaultIndexSnapshot = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val first = input.readInt()
        if (first != FORMAT_MARKER) {
            require(first in 0..MAX_ITEMS) { "Invalid vault index size" }
            val snapshot = validateSnapshot(VaultIndexSnapshot(input.readItems(first)))
            require(input.read() == -1) { "Trailing vault index bytes" }
            return snapshot
        }
        val version = input.readInt()
        require(version in FORMAT_VERSION_2..FORMAT_VERSION_6) { "Unsupported vault index format" }
        val items = input.readItems(input.readCount(MAX_ITEMS, "item"), version >= FORMAT_VERSION_5, version >= FORMAT_VERSION_6)
        val collections = List(input.readCount(MAX_COLLECTIONS, "collection")) {
            val id = input.readUTF()
            val name = input.readUTF()
            val createdAt = input.readLong()
            val pinned = input.readInt().let { ordinal ->
                if (ordinal == NO_PINNED_DESTINATION) null else VaultPinnedDestination.entries.getOrNull(ordinal)
                    ?: error("Invalid pinned destination")
            }
            val cover = if (input.readBoolean()) input.readUTF() else null
            VaultCollection(id, name, createdAt, pinned, cover)
        }
        val memberships = List(input.readCount(MAX_MEMBERSHIPS, "membership")) {
            VaultCollectionMembership(input.readUTF(), input.readUTF(), input.readLong())
        }
        val imageEdits = if (version >= FORMAT_VERSION_3) {
            buildMap {
                repeat(input.readCount(MAX_IMAGE_EDITS, "image edit")) {
                    val itemId = input.readUTF()
                    require(put(itemId, ImageEditState(input.readCrop(), if (input.readBoolean()) input.readCrop() else null)) == null) {
                        "Duplicate image edit"
                    }
                }
            }
        } else {
            emptyMap()
        }
        val favourite = if (version >= FORMAT_VERSION_4 && input.readBoolean()) input.readUTF() else null
        val snapshot = validateSnapshot(VaultIndexSnapshot(items, collections, memberships, imageEdits, favourite))
        require(input.read() == -1) { "Trailing vault index bytes" }
        snapshot
    }

    private fun validateSnapshot(snapshot: VaultIndexSnapshot): VaultIndexSnapshot {
        val itemIds = snapshot.items.mapTo(mutableSetOf()) { it.id }
        require(snapshot.items.size == itemIds.size) { "Duplicate Vault item IDs" }
        require(snapshot.items.all { (it.state == VaultItemState.TRASHED) == (it.deletedAtEpochMillis != null) }) { "Invalid deletion metadata" }
        val collectionIds = snapshot.collections.mapTo(mutableSetOf()) { it.id }
        require(snapshot.collections.size == collectionIds.size) { "Duplicate collection IDs" }
        require(snapshot.memberships.all { it.collectionId in collectionIds && it.vaultItemId in itemIds }) { "Dangling collection membership" }
        require(snapshot.memberships.map { it.collectionId to it.vaultItemId }.distinct().size == snapshot.memberships.size) { "Duplicate collection membership" }
        require(snapshot.collections.count { it.pinnedDestination == VaultPinnedDestination.JENNA } <= 1) { "Duplicate Jenna collection" }
        require(snapshot.imageEdits.keys.all { it in itemIds }) { "Dangling image edit" }
        require(snapshot.favouriteCollectionId == null || snapshot.favouriteCollectionId in collectionIds) { "Dangling favourite collection" }
        return snapshot
    }

    private fun DataOutputStream.writeCrop(crop: NormalizedCrop) {
        writeFloat(crop.left)
        writeFloat(crop.top)
        writeFloat(crop.right)
        writeFloat(crop.bottom)
    }

    private fun DataInputStream.readCrop(): NormalizedCrop = NormalizedCrop(readFloat(), readFloat(), readFloat(), readFloat())

    private fun DataOutputStream.writeItems(items: List<VaultItem>, withProvenance: Boolean = false, withDeletion: Boolean = false) {
        items.forEach { item ->
            writeUTF(item.id)
            writeUTF(item.mimeType)
            writeUTF(item.displayName)
            writeLong(item.importedAtEpochMillis)
            writeLong(item.plaintextSize)
            writeInt(item.plaintextSha256.size)
            write(item.plaintextSha256)
            writeInt(item.payloadNonce.size)
            write(item.payloadNonce)
            writeInt(item.state.ordinal)
            writeBoolean(item.sourceUri != null)
            item.sourceUri?.let(::writeUTF)
            if (withProvenance) { writeInt(item.origin.ordinal); writeBoolean(item.vaultOnly) }
            if (withDeletion) { writeBoolean(item.deletedAtEpochMillis != null); item.deletedAtEpochMillis?.let(::writeLong) }
        }
    }

    private fun DataInputStream.readItems(size: Int, withProvenance: Boolean = false, withDeletion: Boolean = false): List<VaultItem> = List(size) {
        val id = readUTF()
        val mimeType = readUTF()
        val displayName = readUTF()
        val importedAt = readLong()
        val plaintextSize = readLong().also { require(it >= 0) { "Invalid plaintext size" } }
        val hash = readExactly(readInt().also { require(it == 32) })
        val payloadNonce = readExactly(readInt().also { require(it == EncryptionHeader.NONCE_BYTES) })
        val state = VaultItemState.entries.getOrNull(readInt()) ?: error("Invalid vault item state")
        val sourceUri = if (readBoolean()) readUTF() else null
        VaultItem(id, mimeType, displayName, importedAt, plaintextSize, hash, payloadNonce, state, sourceUri,
            if (withProvenance) MediaOrigin.entries.getOrNull(readInt()) ?: error("Invalid origin") else MediaOrigin.IMPORTED,
            if (withProvenance) readBoolean() else false,
            if (withDeletion && readBoolean()) readLong() else null)
    }

    private fun DataInputStream.readCount(maximum: Int, label: String): Int = readInt().also { require(it in 0..maximum) { "Invalid $label count" } }

    private companion object {
        const val FORMAT_MARKER = -0x5047_0002
        const val FORMAT_VERSION_2 = 2
        const val FORMAT_VERSION_3 = 3
        const val FORMAT_VERSION_4 = 4
        const val FORMAT_VERSION_5 = 5
        const val FORMAT_VERSION_6 = 6
        const val NO_PINNED_DESTINATION = -1
        const val MAX_PLAINTEXT_BYTES = 64L * 1024 * 1024
        const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + EncryptionHeader.NONCE_BYTES + 16
        const val MAX_ITEMS = 100_000
        const val MAX_COLLECTIONS = 10_000
        const val MAX_MEMBERSHIPS = 1_000_000
        const val MAX_IMAGE_EDITS = MAX_ITEMS
        val INDEX_AAD = "private-gallery:index:v1".encodeToByteArray()
    }
}

private fun java.io.InputStream.readExactly(length: Int): ByteArray {
    val bytes = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(bytes, offset, length - offset)
        if (read < 0) throw java.io.EOFException("Truncated encrypted vault index")
        offset += read
    }
    return bytes
}
