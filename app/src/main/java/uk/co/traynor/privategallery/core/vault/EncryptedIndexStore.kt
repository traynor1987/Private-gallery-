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
 * Atomic AES-GCM encrypted Vault metadata ledger.  v2 extends the original
 * media-only v1 payload with collection metadata and memberships. Both
 * formats are readable; only collection mutations rewrite an existing v1
 * ledger as v2.
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
            return deserializeSnapshot(plain.toByteArray())
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
            output.writeInt(FORMAT_VERSION_2)
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
        }
        buffer.toByteArray()
    }

    private fun deserializeSnapshot(bytes: ByteArray): VaultIndexSnapshot = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val first = input.readInt()
        if (first != FORMAT_MARKER) {
            require(first in 0..MAX_ITEMS) { "Invalid vault index size" }
            return VaultIndexSnapshot(input.readItems(first))
        }
        require(input.readInt() == FORMAT_VERSION_2) { "Unsupported vault index format" }
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
        validateSnapshot(VaultIndexSnapshot(items, collections, memberships))
    }

    private fun validateSnapshot(snapshot: VaultIndexSnapshot): VaultIndexSnapshot {
        val itemIds = snapshot.items.mapTo(mutableSetOf()) { it.id }
        val collectionIds = snapshot.collections.mapTo(mutableSetOf()) { it.id }
        require(snapshot.collections.size == collectionIds.size) { "Duplicate collection IDs" }
        require(snapshot.memberships.all { it.collectionId in collectionIds && it.vaultItemId in itemIds }) { "Dangling collection membership" }
        require(snapshot.memberships.map { it.collectionId to it.vaultItemId }.distinct().size == snapshot.memberships.size) { "Duplicate collection membership" }
        require(snapshot.collections.count { it.pinnedDestination == VaultPinnedDestination.JENNA } <= 1) { "Duplicate Jenna collection" }
        return snapshot
    }

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
        const val NO_PINNED_DESTINATION = -1
        const val MAX_ITEMS = 100_000
        const val MAX_COLLECTIONS = 10_000
        const val MAX_MEMBERSHIPS = 1_000_000
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
