package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.AndroidVaultRepository
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Single-use foreground authorization, owned and synchronously revoked by MainActivity.
 * Fresh Primary PIN is mandatory. No key/selection survives disposal or process death.
 * Lock order for publication: Hidden repository gate -> this monitor -> Hidden session.
 */
internal class PrimaryCopyAccess(private val context: Context, private val hidden: HiddenSession,
    private val targetFactory: (Context, HiddenSession, ((() -> Unit) -> Unit)) -> HiddenRepository =
        { ctx, session, guard -> HiddenRepository(ctx, session, publishIfAllowed = guard) },
    private val checkpoint: (String) -> Unit = {},
) : AutoCloseable {
    private val monitor = Any()
    private val hiddenOperation = hidden.beginOperation()
    private var closed = false
    private var key: ByteArray? = null
    private val ioGate = Any() // one bounded decrypt/preview/copy at a time

    fun isActive(): Boolean = synchronized(monitor) { !closed && hidden.isCurrent(hiddenOperation) }
    private fun checkActive() { check(isActive()) { "Copy session ended. Authenticate again." } }

    fun authenticate(pin: CharArray) {
        var candidate: ByteArray? = null
        try {
            checkActive()
            val unlocked = PinVaultKeyStore(context).unlock(pin)
            candidate = unlocked
            synchronized(monitor) {
                checkActive()
                key?.fill(0)
                key = unlocked.copyOf()
            }
        } finally { pin.fill('\u0000'); candidate?.fill(0) }
    }

    /** Called only after the Activity's separate Primary authentication boundary. */
    internal fun authorize(authenticatedKey: ByteArray) = synchronized(monitor) {
        checkActive();require(authenticatedKey.size==32)
        key?.fill(0);key=authenticatedKey.copyOf()
    }

    private fun <T> withPrimary(block: (AndroidVaultRepository) -> T): T = synchronized(ioGate) {
        val owned = synchronized(monitor) { checkActive(); checkNotNull(key).copyOf() }
        try { block(AndroidVaultRepository(context, owned)) } finally { owned.fill(0) }
    }

    fun items(): List<VaultItem> = withPrimary { repository ->
        val items = repository.items().filter { it.state == VaultItemState.COMPLETE }
        checkActive(); items
    }

    /** Source thumbnails are local to this picker, never inserted into either vault's cache. */
    fun preview(id: String): Bitmap = withPrimary { repository ->
        val (item, bytes) = repository.readForHiddenCopy(id, HiddenRecordStore.MAX_MEDIA_BYTES) { !isActive() }
        var bitmap: Bitmap? = null
        try {
            bitmap = if (item.mimeType.startsWith("image/")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
                checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(object : MediaDataSource() {
                        override fun getSize() = bytes.size.toLong()
                        override fun close() {}
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            checkActive()
                            if (position < 0 || position >= bytes.size) return -1
                            val count = minOf(size, bytes.size - position.toInt())
                            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
                            return count
                        }
                    })
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        checkNotNull(retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256))
                    } else {
                        val frame = checkNotNull(retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                        try { Bitmap.createScaledBitmap(frame, 256, 256, true) }
                        finally { if (frame.width != 256 || frame.height != 256) frame.recycle() }
                    }
                } finally { retriever.release() }
            }
            checkActive()
            checkNotNull(bitmap)
        } catch (failure: Throwable) { bitmap?.recycle(); throw failure }
        finally { bytes.fill(0) }
    }

    data class Result(val verified: Int, val failed: Int, val total: Int, val moved: Int = 0, val interrupted: Boolean = false)

    fun copy(ids: List<String>, progress: (Int, Int) -> Unit): Result = transfer(ids,false,progress)

    fun transfer(ids: List<String>, move: Boolean, progress: (Int, Int) -> Unit): Result =
        transfer(ids, move, { false }, progress)

    private fun journal() = TransferJournal(java.io.File(context.noBackupFilesDir,"hidden-media-v1/transfers-v1"),hiddenOperation.key()) { commit ->
        synchronized(monitor) { checkActive(); hidden.commit(hiddenOperation) { commit.run() } }
    }
    fun history():List<TransferJournal.Operation> = synchronized(ioGate) { checkActive(); journal().list() }
    fun clearHistory() = synchronized(ioGate) {checkActive();journal().clearFinished()}
    fun cancelRecorded(id:String) = synchronized(ioGate) {checkActive();val j=journal();val op=j.load(id);op.status="CANCELLED";op.ended=System.currentTimeMillis();j.save(op)}
    fun resume(id:String,cancelled:()->Boolean,progress:(Int,Int)->Unit):Result = withPrimary {primary ->
        val j=journal();val op=j.load(id);require(op.status!="CANCELLED")
        if(op.direction=="HIDDEN_TO_PRIMARY") {
            val owned=synchronized(monitor){checkActive();checkNotNull(key).copyOf()}
            try {
                return@withPrimary HiddenRestoreAccess(context,hidden).use {restore ->
                    restore.authorize(owned);val result=restore.resume(id,cancelled,progress)
                    Result(result.verified,result.failed,result.total,result.moved,cancelled())
                }
            } finally {owned.fill(0)}
        }
        require(op.direction=="PRIMARY_TO_HIDDEN")
        runTransfer(primary,j,op,cancelled,progress)
    }

    /** Persist before acquisition. Resume only after fresh independent authentication. */
    fun transfer(ids: List<String>, move: Boolean, cancelled: () -> Boolean, progress: (Int, Int) -> Unit): Result = withPrimary { primary ->
        val j=journal();val op=j.create("PRIMARY_TO_HIDDEN",move,ids.distinct())
        runTransfer(primary,j,op,cancelled,progress)
    }
    private fun runTransfer(primary:AndroidVaultRepository,j:TransferJournal,op:TransferJournal.Operation,
                            cancelled:()->Boolean,progress:(Int,Int)->Unit):Result {
        checkActive()
        val target=targetFactory(context,hidden) {publish -> synchronized(monitor) {checkActive();publish()}}
        op.status="RUNNING";op.ended=0;j.save(op)
        var interrupted=false
        try {
            progress(op.verified(),op.items.size)
            for((index,record) in op.items.withIndex()) {
                if(cancelled()){interrupted=true;break}
                checkActive()
                progress(index,op.items.size)
                try {
                    val marker="transfer176:${op.id}:${record.source}"
                    // A destination committed before process death is discoverable atomically in its index.
                    val existing=target.findTransfer(marker)
                    if(record.phase=="MOVED" || (!op.move && record.phase=="VERIFIED")) {
                        val destination=checkNotNull(record.destination.takeIf {it.isNotEmpty()})
                        target.verify(destination)
                        progress(index+1,op.items.size);continue
                    }
                    if(op.move && record.destination.isNotEmpty()) {
                        val old=primary.recentlyDeleted().firstOrNull {it.id==record.source}
                        if(old!=null) {
                            val destination=target.items().firstOrNull {it.id==record.destination}
                            checkNotNull(destination);target.verify(destination.id)
                            check(destination.plaintextSize==old.plaintextSize && MessageDigest.isEqual(destination.plaintextSha256,old.plaintextSha256))
                            checkpoint("source-removed")
                            record.phase="MOVED";j.save(op);progress(index+1,op.items.size);continue
                        }
                    }
                    primary.withHiddenCopySource(record.source,{!isActive()}) {item,source ->
                        checkActive()
                        val copied=existing ?: target.importMedia(item.displayName,item.mimeType,source,
                            createDistinctCopy=item.vaultOnly,origin=item.origin,vaultOnly=item.vaultOnly,
                            sourceReference=marker,capturedAtEpochMillis=item.capturedAtEpochMillis)
                        if(existing!=null) {
                            // The source lease authenticates EOF even when acquisition is skipped.
                            val buffer=ByteArray(64*1024)
                            try {while(true){checkActive();val n=source.read(buffer);if(n<0)break;check(n>0)}} finally {buffer.fill(0)}
                        }
                        checkpoint("destination-committed")
                        check(copied.state==VaultItemState.COMPLETE)
                        target.verify(copied.id)
                        check(copied.plaintextSize==item.plaintextSize && MessageDigest.isEqual(copied.plaintextSha256,item.plaintextSha256))
                        record.destination=copied.id;record.size=copied.plaintextSize;record.phase="VERIFIED";j.save(op);checkpoint("destination-verified")
                        if(op.move && !cancelled()) {
                            primary.trashVerifiedSource(item) {remove -> synchronized(monitor) {
                                checkActive();check(!cancelled());hidden.commit(hiddenOperation) {remove()}
                            }}
                            checkpoint("source-removed")
                            record.phase="MOVED";j.save(op)
                        }
                    }
                } catch(failure:Exception) {
                    checkActive()
                    // Keep a verified destination identity even when removal or its journal sync failed.
                    // Retry never assumes an uncertain source deletion succeeded.
                    if(record.phase!="MOVED")record.phase="FAILED"
                    j.save(op)
                }
                progress(index+1,op.items.size)
            }
            checkActive()
            interrupted=interrupted||cancelled()
            op.status=if(interrupted)"INTERRUPTED" else if(op.failed()>0 || (op.move && op.items.any {it.phase!="MOVED"}))"PARTIAL" else "COMPLETE"
            op.ended=System.currentTimeMillis();j.save(op)
            return Result(op.verified(),op.failed(),op.items.size,op.items.count {it.phase=="MOVED"},interrupted)
        } finally {
            // No ambiguous journal/media cleanup. Normal authenticated reconcile only removes ciphertext staging.
            runCatching {HiddenRepository(context,hidden).reconcile()}
        }
    }

    override fun close() = synchronized(monitor) {
        closed = true
        key?.fill(0); key = null
        hiddenOperation.close()
    }
}
