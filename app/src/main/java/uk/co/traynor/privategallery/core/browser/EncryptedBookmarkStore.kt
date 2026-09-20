package uk.co.traynor.privategallery.core.browser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher

data class BrowserBookmark(val id: String, val title: String, val url: String, val createdAtEpochMillis: Long)

/** Encrypted, local-only bookmarks. URLs/titles never enter diagnostics or Browser history. */
class EncryptedBookmarkStore(private val root: File, private val key: ByteArray) {
    private val file = File(root, "browser-bookmarks.enc")
    private val temporary = File(root, "browser-bookmarks.new")

    fun list(): List<BrowserBookmark> = read().sortedByDescending { it.createdAtEpochMillis }

    fun add(title: String, url: String): BrowserBookmark {
        require(BrowserNavigationPolicy.isWebUrl(url)) { "Only HTTP(S) bookmarks are supported" }
        val current = read()
        current.firstOrNull { it.url == url }?.let { return it }
        val bookmark = BrowserBookmark(UUID.randomUUID().toString(), title.trim().take(240).ifBlank { url }, url, System.currentTimeMillis())
        write(current + bookmark)
        return bookmark
    }

    fun remove(id: String) = write(read().filterNot { it.id == id })

    private fun read(): List<BrowserBookmark> {
        if (!file.exists()) return emptyList()
        val plain = ByteArrayOutputStream()
        try {
            FileInputStream(file).use { input ->
                val nonce = ByteArray(EncryptionHeader.NONCE_BYTES)
                var offset = 0
                while (offset < nonce.size) {
                    val count = input.read(nonce, offset, nonce.size - offset)
                    if (count < 0) break
                    offset += count
                }
                require(offset == nonce.size) { "Corrupt bookmark store" }
                VaultCipher.decrypt(input, plain, key, AAD, EncryptionHeader(nonce))
            }
            val bytes = plain.toByteArray()
            return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val count = data.readInt().also { require(it in 0..10_000) }
                List(count) { BrowserBookmark(data.readUTF(), data.readUTF(), data.readUTF(), data.readLong()) }
            }
        } finally { plain.reset() }
    }

    private fun write(values: List<BrowserBookmark>) {
        val plain = ByteArrayOutputStream().use { output -> DataOutputStream(output).use { data ->
            data.writeInt(values.size); values.forEach { value -> data.writeUTF(value.id); data.writeUTF(value.title); data.writeUTF(value.url); data.writeLong(value.createdAtEpochMillis) }
        }; output.toByteArray() }
        root.mkdirs(); val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
        try {
            FileOutputStream(temporary).use { out -> out.write(nonce); VaultCipher.encrypt(ByteArrayInputStream(plain), out, key, AAD, nonce); out.fd.sync() }
            check(temporary.renameTo(file)) { "Unable to commit bookmarks" }
        } finally { plain.fill(0); temporary.delete() }
    }

    private companion object { val AAD = "private-gallery:browser-bookmarks:v1".encodeToByteArray() }
}
