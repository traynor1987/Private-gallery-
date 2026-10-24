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
    fun allowed() = !revoked.get() && authorized() && (hidden?.let { session ->
        runCatching { session.beginOperation(hiddenAuthority).use { session.isCurrent(it) } }.getOrDefault(false)
    } ?: true)
    private fun checkAllowed() { check(allowed()) { "Camera authentication expired" } }
    private fun <T> checkedResult(result:T, validate:()->Unit):T {
        try { validate(); return result } catch(failure:Throwable) {
            if(result is AutoCloseable)runCatching {result.close()}
            if(result is android.graphics.Bitmap)result.recycle()
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
    private val optionsGate=Any()
    fun options():CameraOptions = withKey {key ->
        val file=File(root,"options.enc")
        if(!file.exists())CameraOptions() else {
            check(file.length() in 28..256)
            val sealed=file.readBytes();val out=ByteArrayOutputStream()
            try {
                VaultCipher.decrypt(ByteArrayInputStream(sealed,12,sealed.size-12),out,key,"camera-options-$domain-v1".toByteArray(),EncryptionHeader(sealed.copyOf(12)))
                val plain=out.toByteArray();try {CameraOptions.decode(plain)}finally {plain.fill(0)}
            } finally {sealed.fill(0);out.reset()}
        }
    }
    fun options(value:CameraOptions) = synchronized(optionsGate) {withKey {key ->
        check(root.isDirectory||root.mkdirs())
        val plain=value.encode();val out=ByteArrayOutputStream();val staged=File(root,"options-${java.util.UUID.randomUUID()}.new")
        try {
            val header=VaultCipher.encrypt(ByteArrayInputStream(plain),out,key,"camera-options-$domain-v1".toByteArray())
            FileOutputStream(staged).use {it.write(header.nonce);it.write(out.toByteArray());it.fd.sync()}
            val publish={java.nio.file.Files.move(staged.toPath(),File(root,"options.enc").toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);Unit}
            if(hidden!=null)hidden.beginOperation(hiddenAuthority).use {op->hidden.commit(op){checkAllowed();publish()}}
            else kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main.immediate){checkAllowed();publish()}
            java.nio.channels.FileChannel.open(root.toPath(),java.nio.file.StandardOpenOption.READ).use {it.force(true)}
        } finally {plain.fill(0);out.reset();staged.delete()}
    }}
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
        if(hidden == null) {
            // Camera cleanup is stricter than an ordinary import: do not retire the
            // recoverable copy until the destination rename and index are durable.
            val vault=File(app.filesDir,"vault")
            for(directory in listOf(File(vault,"payloads"),vault,app.filesDir)) {
                java.nio.channels.FileChannel.open(directory.toPath(),java.nio.file.StandardOpenOption.READ).use {it.force(true)}
            }
            checkAllowed()
        }
        journal.imported(ticket)
        return item
    }
    fun verifyRecordings():Int {
        val items=recordings()
        if(hiddenRepository!=null)items.forEach {checkAllowed();hiddenRepository.verify(it.id)}
        else withKey {key->val repo=AndroidVaultRepository(app,key);items.forEach {repo.verifyTransferDestination(it){!allowed()}}}
        checkAllowed();return items.size
    }
    /** Fresh owned thumbnail, never a public file or a cross-vault cache. */
    fun recentPreview():android.graphics.Bitmap? = withKey {key ->
        val item=recordings().firstOrNull() ?: return@withKey null
        if(hidden!=null)return@withKey checkNotNull(hiddenRepository).preview(item.id)
        if(!uk.co.traynor.privategallery.core.ui.VaultPreviewPolicy.shouldGenerate(item.mimeType,item.plaintextSize))return@withKey null
        val bytes=AndroidVaultRepository(app,key).readForEditingPreview(item){!allowed()}
        try {
            if(item.mimeType.startsWith("video/")) {
                val source=object:android.media.MediaDataSource() {
                    override fun getSize()=bytes.size.toLong()
                    override fun close()=Unit
                    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
                        checkAllowed();if(position>=bytes.size)return -1
                        val n=minOf(size,bytes.size-position.toInt());bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+n);return n
                    }
                }
                val r=android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(source)
                    if(android.os.Build.VERSION.SDK_INT>=27)r.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,128,128)
                    else null
                } finally {r.release();source.close()}
            } else {
                val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256)sample*=2
                val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply {inSampleSize=sample})
                val orientation=runCatching {android.media.ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt("Orientation",1)}.getOrDefault(1)
                val matrix=android.graphics.Matrix()
                when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)}
                if(bitmap!=null&&orientation in 2..8)android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also {if(it!==bitmap)bitmap.recycle()} else bitmap
            }
        } finally {bytes.fill(0)}
    }
    fun recordings(): List<VaultItem> {
        checkAllowed()
        val items=if(hidden!=null) checkNotNull(hiddenRepository).items() else withKey { AndroidVaultRepository(app,it).items() }
        return items.filter { it.sourceUri?.startsWith("capture:v1|") == true }
    }
}
