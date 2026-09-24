package uk.co.traynor.privategallery.core.media

import java.io.*
import java.security.MessageDigest
import uk.co.traynor.privategallery.core.crypto.*

/** Only ciphertext and opaque hashes on disk. Caller serializes access and owns the key. */
class EncryptedPreviewCache(private val root: File, private val budget: Long = 64L * 1024 * 1024) {
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(id: String, revision: String) = File(root, "${hash(id)}-${hash(revision)}.enc")
    fun get(id: String, revision: String, key: ByteArray): ByteArray? {
        val file = file(id, revision)
        if (!file.exists() || file.length() > MAX_PREVIEW_BYTES + 64) return null
        return runCatching {
            val encoded = file.readBytes()
            val plain = ByteArrayOutputStream()
            VaultCipher.decrypt(ByteArrayInputStream(encoded, 12, encoded.size - 12), plain, key,
                "$id:$revision".toByteArray(), EncryptionHeader(encoded.copyOfRange(0, 12)))
            file.setLastModified(System.currentTimeMillis())
            plain.toByteArray()
        }.getOrElse { file.delete(); null }
    }
    fun put(id: String, revision: String, bytes: ByteArray, key: ByteArray) {
        require(bytes.size <= MAX_PREVIEW_BYTES)
        root.mkdirs()
        remove(id)
        val destination = file(id, revision)
        val temp = File(root, destination.name + ".new")
        val nonce = java.security.SecureRandom().generateSeed(12)
        try {
            FileOutputStream(temp).use { out ->
                out.write(nonce)
                VaultCipher.encrypt(ByteArrayInputStream(bytes), out, key, "$id:$revision".toByteArray(), nonce)
                out.fd.sync()
            }
            check(temp.renameTo(destination))
            var total = root.listFiles().orEmpty().sumOf { it.length() }
            root.listFiles().orEmpty().sortedBy { it.lastModified() }.forEach { file ->
                if (total > budget) { val length = file.length(); if (file.delete()) total -= length }
            }
        } finally { temp.delete() }
    }
    fun remove(id: String) { root.listFiles()?.filter { it.name.startsWith(hash(id) + "-") }?.forEach { it.delete() } }
    companion object { const val MAX_PREVIEW_BYTES = 2 * 1024 * 1024 }
}
