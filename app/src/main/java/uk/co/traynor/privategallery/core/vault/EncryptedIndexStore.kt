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

enum class VaultItemState {
    IMPORTING,
    VERIFIED,
    DELETE_PENDING,
    COMPLETE,
    FAILED,
}

data class VaultItem(
    val id: String,
    val mimeType: String,
    val displayName: String,
    val importedAtEpochMillis: Long,
    val plaintextSize: Long,
    val plaintextSha256: ByteArray,
    val payloadNonce: ByteArray,
    val state: VaultItemState,
)

/**
 * Small encrypted metadata ledger.  The nonce is prefixed to the ciphertext;
 * all sensitive item metadata remains inside AES-GCM authenticated payload.
 */
class EncryptedIndexStore(
    private val root: File,
    private val syncOutput: (FileOutputStream) -> Unit = { it.fd.sync() },
) {
    private val index = File(root, "vault-index.enc")
    private val temporary = File(root, "vault-index.new")

    fun load(key: ByteArray): List<VaultItem> {
        if (!index.exists()) return emptyList()
        FileInputStream(index).use { input ->
            val nonce = input.readNBytes(EncryptionHeader.NONCE_BYTES)
            check(nonce.size == EncryptionHeader.NONCE_BYTES) { "Corrupt vault index header" }
            val plain = ByteArrayOutputStream()
            VaultCipher.decrypt(input, plain, key, INDEX_AAD, EncryptionHeader(nonce))
            return deserialize(plain.toByteArray())
        }
    }

    fun save(items: List<VaultItem>, key: ByteArray) {
        root.mkdirs()
        val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
        val plaintext = serialize(items)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(nonce)
                VaultCipher.encrypt(
                    ByteArrayInputStream(plaintext),
                    output,
                    key,
                    INDEX_AAD,
                    nonce,
                )
                syncOutput(output)
            }
            check(temporary.renameTo(index)) { "Unable to commit encrypted vault index" }
        } finally {
            temporary.delete()
        }
    }

    private fun serialize(items: List<VaultItem>): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(items.size)
            items.forEach { item ->
                output.writeUTF(item.id)
                output.writeUTF(item.mimeType)
                output.writeUTF(item.displayName)
                output.writeLong(item.importedAtEpochMillis)
                output.writeLong(item.plaintextSize)
                output.writeInt(item.plaintextSha256.size)
                output.write(item.plaintextSha256)
                output.writeInt(item.payloadNonce.size)
                output.write(item.payloadNonce)
                output.writeInt(item.state.ordinal)
            }
        }
        buffer.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): List<VaultItem> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val size = input.readInt()
        require(size in 0..100_000) { "Invalid vault index size" }
        List(size) {
            val id = input.readUTF()
            val mimeType = input.readUTF()
            val displayName = input.readUTF()
            val importedAt = input.readLong()
            val plaintextSize = input.readLong()
            val hash = input.readNBytes(input.readInt().also { require(it == 32) })
            val payloadNonce = input.readNBytes(input.readInt().also { require(it == EncryptionHeader.NONCE_BYTES) })
            val state = VaultItemState.entries.getOrNull(input.readInt()) ?: error("Invalid vault item state")
            VaultItem(id, mimeType, displayName, importedAt, plaintextSize, hash, payloadNonce, state)
        }
    }

    private companion object {
        val INDEX_AAD = "private-gallery:index:v1".encodeToByteArray()
    }
}
