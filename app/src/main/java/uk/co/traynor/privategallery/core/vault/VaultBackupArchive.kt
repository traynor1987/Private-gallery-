package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

/** A versioned, ciphertext-only archive. A recovery key is required to authenticate its contents. */
object VaultBackupArchive {
    private const val INDEX = "vault-index.enc"
    private const val MANIFEST = "manifest.json"
    private const val VERSION = 1
    private const val MAX_ITEMS = 100_000
    private const val MAX_METADATA = 64L * 1024 * 1024
    private const val MAX_PAYLOAD = 8L * 1024 * 1024 * 1024
    private const val MAX_TOTAL = 100L * 1024 * 1024 * 1024
    private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** The caller holds the repository metadata lock throughout this operation. */
    fun write(root: File, key: ByteArray, envelope: RecoveryWrappedKey, output: OutputStream,
              progress: (Int, Int) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }) {
        val snapshot = EncryptedIndexStore(root).loadSnapshot(key)
        require(snapshot.items.size <= MAX_ITEMS && snapshot.items.all { it.state in setOf(VaultItemState.COMPLETE, VaultItemState.TRASHED) && ID.matches(it.id) }) {
            "Vault contains an unfinished item"
        }
        val index = File(root, INDEX)
        check(index.isFile && index.length() in 1..MAX_METADATA) { "Encrypted Vault index is unavailable" }
        val store = EncryptedPayloadStore(root)
        val files = snapshot.items.map { item ->
            check(!cancelled()) { "Vault backup cancelled" }
            val file = File(File(root, "payloads"), "${item.id}.vault")
            check(file.isFile && file.length() in 1..MAX_PAYLOAD) { "Encrypted Vault payload is unavailable" }
            check(store.verify(StoredPayload(item.id, file, item.plaintextSize, item.plaintextSha256, item.payloadNonce), key)) {
                "Encrypted Vault payload failed verification"
            }
            "payloads/${item.id}.vault" to file
        }
        val hashes = JSONObject()
        var total = index.length()
        files.forEach { (_, file) ->
            total = Math.addExact(total, file.length())
            require(total <= MAX_TOTAL) { "Vault backup is too large" }
        }
        ZipOutputStream(output).use { zip ->
            // Ciphertext is incompressible; avoid spending CPU and battery on it.
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            (listOf(INDEX to index) + files).forEachIndexed { position, (name, file) ->
                zip.putNextEntry(ZipEntry(name))
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { input -> copyBounded(input, zip, if (name == INDEX) MAX_METADATA else MAX_PAYLOAD, digest, cancelled) }
                zip.closeEntry()
                hashes.put(name, hex(digest.digest()))
                progress(position + 1, files.size + 1)
            }
            val manifest = JSONObject().put("version", VERSION).put("hashes", hashes)
                .put("recovery", JSONObject().put("salt", b64(envelope.salt))
                    .put("nonce", b64(envelope.nonce)).put("ciphertext", b64(envelope.ciphertext)))
                .toString().toByteArray(Charsets.UTF_8)
            require(manifest.size <= MAX_METADATA)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            manifest.fill(0)
            zip.finish()
        }
    }

    /** Stage and authenticate everything before a caller installs the fresh Vault. */
    fun read(input: InputStream, stage: File, recoveryKey: CharArray): RestoredVault {
        require(!stage.exists()) { "Restore staging already exists" }
        check(stage.mkdirs()) { "Unable to stage Vault backup" }
        val observed = mutableMapOf<String, String>()
        var manifestBytes: ByteArray? = null
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && (name == INDEX || name == MANIFEST || validPayloadName(name))) {
                        "Invalid Vault backup entry"
                    }
                    require(name !in observed && !(name == MANIFEST && manifestBytes != null)) { "Duplicate Vault backup entry" }
                    require(observed.size < MAX_ITEMS + 1) { "Too many Vault backup entries" }
                    val limit = if (name == MANIFEST || name == INDEX) MAX_METADATA else MAX_PAYLOAD
                    val digest = MessageDigest.getInstance("SHA-256")
                    if (name == MANIFEST) {
                        val bytes = java.io.ByteArrayOutputStream()
                        val length = copyBounded(zip, bytes, limit, digest)
                        total = Math.addExact(total, length)
                        manifestBytes = bytes.toByteArray()
                    } else {
                        val destination = File(stage, name)
                        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
                        FileOutputStream(destination).use { file ->
                            val length = copyBounded(zip, file, limit, digest)
                            total = Math.addExact(total, length)
                            file.fd.sync()
                        }
                        observed[name] = hex(digest.digest())
                    }
                    require(total <= MAX_TOTAL + MAX_METADATA) { "Vault backup is too large" }
                    zip.closeEntry()
                }
            }
            val manifest = JSONObject(checkNotNull(manifestBytes) { "Missing Vault backup manifest" }.toString(Charsets.UTF_8))
            require(manifest.getInt("version") == VERSION) { "Unsupported Vault backup version" }
            val hashes = manifest.getJSONObject("hashes")
            require(hashes.length() == observed.size && observed.isNotEmpty() && INDEX in observed) { "Incomplete Vault backup" }
            observed.forEach { (name, hash) -> require(hashes.getString(name) == hash) { "Vault backup was altered" } }
            val wrapped = manifest.getJSONObject("recovery").let {
                RecoveryWrappedKey(unb64(it.getString("salt"), 16), unb64(it.getString("nonce"), 12), unb64(it.getString("ciphertext"), 48))
            }
            val key = uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope.unwrap(recoveryKey, wrapped)
            try {
                val snapshot = EncryptedIndexStore(stage).loadSnapshot(key)
                require(snapshot.items.size <= MAX_ITEMS && snapshot.items.all { it.state in setOf(VaultItemState.COMPLETE, VaultItemState.TRASHED) && ID.matches(it.id) }) {
                    "Invalid Vault backup index"
                }
                val expected = snapshot.items.mapTo(mutableSetOf(INDEX)) { "payloads/${it.id}.vault" }
                require(expected == observed.keys) { "Vault backup payloads do not match the index" }
                val store = EncryptedPayloadStore(stage)
                snapshot.items.forEach { item ->
                    check(store.verify(StoredPayload(item.id, File(stage, "payloads/${item.id}.vault"),
                        item.plaintextSize, item.plaintextSha256, item.payloadNonce), key)) { "Vault backup payload failed authentication" }
                }
                return RestoredVault(wrapped, key, stage)
            } catch (failure: Throwable) { key.fill(0); throw failure }
        } catch (failure: Throwable) {
            recoveryKey.fill('\u0000')
            stage.deleteRecursively()
            throw failure
        } finally { manifestBytes?.fill(0) }
    }

    data class RestoredVault(val recoveryEnvelope: RecoveryWrappedKey, val key: ByteArray, val stage: File)

    private fun validPayloadName(name: String): Boolean = name.startsWith("payloads/") &&
        name.endsWith(".vault") && ID.matches(name.removePrefix("payloads/").removeSuffix(".vault")) &&
        name == "payloads/${name.removePrefix("payloads/").removeSuffix(".vault")}.vault"

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long, digest: MessageDigest,
                            cancelled: () -> Boolean = { false }): Long {
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            check(!cancelled()) { "Vault backup cancelled" }
            val read = input.read(buffer)
            if (read < 0) break
            count = Math.addExact(count, read.toLong())
            require(count <= limit) { "Vault backup entry exceeds size limit" }
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        buffer.fill(0)
        return count
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(value: String, size: Int): ByteArray = Base64.getDecoder().decode(value).also { require(it.size == size) }
}
