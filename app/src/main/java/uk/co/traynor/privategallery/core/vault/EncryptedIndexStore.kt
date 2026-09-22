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

enum class VaultItemState { IMPORTING, VERIFIED, DELETE_PENDING, COMPLETE, FAILED }

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
)

/**
 * Atomic AES-GCM encrypted Vault metadata ledger. v3 adds non-destructive
 * image edit metadata to v2's collections/memberships; v4 adds one generic
 * favourite collection ID. All prior formats are
 * readable and are rewritten only during a later metadata mutation.
 */
class EncryptedIndexStore(
    private val root: File,
    private val syncOutput: (FileOutputStream) -> Unit = { it.fd.sync() },
) {
    private val index = File(root, "vault-index.enc")
    private val temporary = File(root, "vault-index.new")

    fun load(key: ByteArray): List<VaultItem> = loadSnapshot(key).items

    fun loadSnapshot(key: ByteArray): VaultIndexSnapshot {
        if (!index.exists()) return VaultIndexSnapshot(emptyList())
        FileInputStream(index).use { input ->
            val nonce = input.readExactly(EncryptionHeader.NONCE_BYTES)
            check(nonce.size == EncryptionHeader.NONCE_BYTES) { "Corrupt vault index header" }
            val plain = ByteArrayOutputStream()
            VaultCipher.decrypt(input, plain, key, INDEX_AAD, EncryptionHeader(nonce))
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
    fun save(items: List<VaultItem>, key: ByteArray) = saveEncrypted(serializeLegacy(items), key)

    fun saveSnapshot(snapshot: VaultIndexSnapshot, key: ByteArray) = saveEncrypted(serializeSnapshot(snapshot), key)

    private fun saveEncrypted(plaintext: ByteArray, key: ByteArray) {
        root.mkdirs()
        val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(nonce)
                VaultCipher.encrypt(ByteArrayInputStream(plaintext), output, key, INDEX_AAD, nonce)
                syncOutput(output)
            }
            check(temporary.renameTo(index)) { "Unable to commit encrypted vault index" }
        } finally {
            plaintext.fill(0)
            temporary.delete()
        }
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
            output.writeInt(FORMAT_VERSION_4)
            output.writeInt(snapshot.items.size)
            output.writeItems(snapshot.items)
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
            return VaultIndexSnapshot(input.readItems(first))
        }
        val version = input.readInt()
        require(version in FORMAT_VERSION_2..FORMAT_VERSION_4) { "Unsupported vault index format" }
        val items = input.readItems(input.readCount(MAX_ITEMS, "item"))
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
        validateSnapshot(VaultIndexSnapshot(items, collections, memberships, imageEdits, favourite))
    }

    private fun validateSnapshot(snapshot: VaultIndexSnapshot): VaultIndexSnapshot {
        val itemIds = snapshot.items.mapTo(mutableSetOf()) { it.id }
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

    private fun DataOutputStream.writeItems(items: List<VaultItem>) {
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
        }
    }

    private fun DataInputStream.readItems(size: Int): List<VaultItem> = List(size) {
        val id = readUTF()
        val mimeType = readUTF()
        val displayName = readUTF()
        val importedAt = readLong()
        val plaintextSize = readLong()
        val hash = readExactly(readInt().also { require(it == 32) })
        val payloadNonce = readExactly(readInt().also { require(it == EncryptionHeader.NONCE_BYTES) })
        val state = VaultItemState.entries.getOrNull(readInt()) ?: error("Invalid vault item state")
        val sourceUri = if (readBoolean()) readUTF() else null
        VaultItem(id, mimeType, displayName, importedAt, plaintextSize, hash, payloadNonce, state, sourceUri)
    }

    private fun DataInputStream.readCount(maximum: Int, label: String): Int = readInt().also { require(it in 0..maximum) { "Invalid $label count" } }

    private companion object {
        const val FORMAT_MARKER = -0x5047_0002
        const val FORMAT_VERSION_2 = 2
        const val FORMAT_VERSION_3 = 3
        const val FORMAT_VERSION_4 = 4
        const val NO_PINNED_DESTINATION = -1
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
