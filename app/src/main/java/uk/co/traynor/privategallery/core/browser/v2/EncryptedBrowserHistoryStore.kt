package uk.co.traynor.privategallery.core.browser.v2

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

data class BrowserHistoryEntry(val id: String, val title: String, val url: String, val visitedAtEpochMillis: Long)

/** Encrypted local history. It deliberately shares no file or format with V1 bookmarks. */
class EncryptedBrowserHistoryStore(private val root: File, private val key: ByteArray) {
    private val file = File(root, "browser-history-v2.enc")

    fun list(): List<BrowserHistoryEntry> = synchronized(HISTORY_LOCK) { read().sortedByDescending { it.visitedAtEpochMillis } }
    fun add(title: String, url: String): BrowserHistoryEntry {
        require(BrowserSecurityPolicy.allowsNavigation(url)) { "Only HTTP(S) history is supported" }
        val entry = BrowserHistoryEntry(UUID.randomUUID().toString(), title.trim().take(240).ifBlank { url }, url, System.currentTimeMillis())
        synchronized(HISTORY_LOCK) { write((read() + entry).takeLast(MAX_ENTRIES)) }
        return entry
    }
    fun clear() = synchronized(HISTORY_LOCK) { write(emptyList()) }

    private fun read(): List<BrowserHistoryEntry> {
        if (!file.exists()) return emptyList()
        val decrypted = ByteArrayOutputStream()
        try {
            FileInputStream(file).use { input ->
                val nonce = ByteArray(EncryptionHeader.NONCE_BYTES)
                require(input.read(nonce) == nonce.size) { "Corrupt Browser history" }
                VaultCipher.decrypt(input, decrypted, key, AAD, EncryptionHeader(nonce))
            }
            val bytes = decrypted.toByteArray()
            try {
                return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                    val count = data.readInt().also { require(it in 0..MAX_ENTRIES) }
                    List(count) { BrowserHistoryEntry(data.readUTF(), data.readUTF(), data.readUTF(), data.readLong()) }
                }
            } finally { bytes.fill(0) }
        } finally { decrypted.reset() }
    }

    private fun write(values: List<BrowserHistoryEntry>) {
        val plain = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(values.size)
                values.forEach { data.writeUTF(it.id); data.writeUTF(it.title); data.writeUTF(it.url); data.writeLong(it.visitedAtEpochMillis) }
            }
            output.toByteArray()
        }
        var pending: File? = null
        try {
            check(root.isDirectory || root.mkdirs()) { "Browser history storage unavailable" }
            // Each commit owns its pending file; the old shared .new path raced across visits.
            val temporary = File.createTempFile("browser-history-v2-", ".new", root)
            pending = temporary
            val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
            FileOutputStream(temporary).use { output ->
                output.write(nonce)
                VaultCipher.encrypt(ByteArrayInputStream(plain), output, key, AAD, nonce)
                output.fd.sync()
            }
            check(temporary.renameTo(file)) { "Unable to commit Browser history" }
        } finally { plain.fill(0); pending?.delete() }
    }

    private companion object {
        val HISTORY_LOCK = Any()
        const val MAX_ENTRIES = 2_000
        val AAD = "private-gallery:browser-history:v2".encodeToByteArray()
    }
}

/** Browser history is optional; storage failures must not terminate the Vault process. */
internal fun recordBrowserHistoryVisit(store: EncryptedBrowserHistoryStore, title: String, url: String): Boolean =
    try { store.add(title, url); true } catch (_: Exception) { false }
