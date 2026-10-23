package uk.co.traynor.privategallery.core.camera

import android.content.Context
import java.io.*
import java.security.MessageDigest
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.crypto.VaultCipher
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.vault.*

/** No retained vault key. The destination is fixed at the authenticated Camera action. */
internal class VaultCaptureAccess(
    context: Context,
    private val hidden: HiddenSession?,
    private val primaryKey: () -> ByteArray?,
    private val authorized: () -> Boolean,
) {
    private val app = context.applicationContext
    val domain = if (hidden == null) "primary" else "hidden"
    val label = if (hidden == null) "Primary Vault" else "Hidden Vault"
    private val root = File(app.noBackupFilesDir, "camera-$domain-v1")
    val journal = CaptureJournal(root, domain)
    private val hiddenAuthority = hidden?.authority()
    private val hiddenRepository = hidden?.let { HiddenRepository(app, it) }
    private val revoked = java.util.concurrent.atomic.AtomicBoolean(false)
    fun revoke() { revoked.set(true) }
    fun allowed() = !revoked.get() && authorized() && (hidden?.isUnlocked ?: true)
    private fun checkAllowed() { check(allowed()) { "Camera authentication expired" } }
    private fun <T> checkedResult(result:T, validate:()->Unit):T {
        try { validate(); return result } catch(failure:Throwable) {
            if(result is AutoCloseable)runCatching {result.close()}
            throw failure
        }
    }
    private fun <T> withKey(block: (ByteArray) -> T): T {
        checkAllowed()
        return if (hidden != null) hidden.beginOperation(hiddenAuthority).use { op ->
            check(hidden.isCurrent(op))
            checkedResult(block(op.key())) { check(hidden.isCurrent(op)); checkAllowed() }
        } else {
            val key = checkNotNull(primaryKey())
            try { checkedResult(block(key)) { checkAllowed() } } finally { key.fill(0) }
        }
    }
    fun create(mime: String, audio: Boolean) = withKey { journal.create(it, mime, System.currentTimeMillis(), audio) }
    fun open(id: String) = withKey { journal.open(id, it) }
    fun recover(id: String) = withKey { journal.openForRecovery(id, it) }
    fun audio(): Boolean = withKey { key ->
        val file = File(root, "audio.enc")
        if (!file.exists()) false else runCatching {
            val bytes = file.readBytes(); val output = ByteArrayOutputStream()
            VaultCipher.decrypt(ByteArrayInputStream(bytes, 12, bytes.size - 12), output, key,
                "camera-audio-$domain".toByteArray(), EncryptionHeader(bytes.copyOf(12)))
            val plain = output.toByteArray(); try { plain.contentEquals(byteArrayOf(1)) } finally { plain.fill(0) }
        }.getOrDefault(false)
    }
    fun audio(enabled: Boolean) = withKey { key ->
        root.mkdirs(); val output = ByteArrayOutputStream()
        val header = VaultCipher.encrypt(ByteArrayInputStream(byteArrayOf(if(enabled) 1 else 0)), output, key, "camera-audio-$domain".toByteArray())
        val temp = File(root, "audio.new")
        FileOutputStream(temp).use { it.write(header.nonce); it.write(output.toByteArray()); it.fd.sync() }
        java.nio.file.Files.move(temp.toPath(), File(root,"audio.enc").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    fun import(ticket: CaptureJournal.Ticket): VaultItem {
        checkAllowed(); check(ticket.complete && ticket.size > 0)
        val reference = "capture:v1|${ticket.id}|${ticket.timestamp}|${ticket.duration}|${ticket.audio}"
        val name = "capture-${ticket.timestamp}." + if(ticket.mime.startsWith("video/")) "mp4" else "jpg"
        val item = journal.reader(ticket).use { reader ->
            reader.verify()
            if(hidden != null) {
                val repo = checkNotNull(hiddenRepository)
                (repo.items().singleOrNull { it.sourceUri == reference } ?: reader.stream().use {
                    repo.importMedia(name, ticket.mime, it, createDistinctCopy=true, sourceReference=reference, capturedAtEpochMillis=ticket.timestamp)
                }).also { repo.verify(it.id) }
            } else withKey { key ->
                val repo = AndroidVaultRepository(app, key)
                val existing = repo.items().singleOrNull { it.sourceUri == reference }
                if(existing != null) { repo.verifyTransferDestination(existing) { !allowed() }; existing }
                else when(val result = VaultImportCoordinator(repo).acquire(VaultImportSource(name,ticket.mime,{reader.stream()},
                    sourceReference=reference, createDistinctCopy=true, isCancelled={!allowed()}, capturedAtEpochMillis=ticket.timestamp))) {
                    is ImportResult.Imported -> result.item
                    is ImportResult.Duplicate -> result.existing
                }
            }
        }
        checkAllowed()
        check(item.plaintextSize == ticket.size && MessageDigest.isEqual(item.plaintextSha256,ticket.hash))
        journal.imported(ticket)
        return item
    }
    fun recordings(): List<VaultItem> {
        checkAllowed()
        val items=if(hidden!=null) checkNotNull(hiddenRepository).items() else withKey { AndroidVaultRepository(app,it).items() }
        return items.filter { it.sourceUri?.startsWith("capture:v1|") == true }
    }
}
