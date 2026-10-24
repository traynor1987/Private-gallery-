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

    /** Cancellation finishes the current verified copy, then stops before the next source. */
    fun transfer(ids: List<String>, move: Boolean, cancelled: () -> Boolean, progress: (Int, Int) -> Unit): Result = withPrimary { primary ->
        val selected = ids.distinct()
        require(selected.isNotEmpty())
        val target = targetFactory(context, hidden) { publish ->
            synchronized(monitor) { checkActive(); publish() }
        }
        var verified = 0
        var failed = 0
        var moved = 0
        try {
            progress(0, selected.size)
            for ((index, id) in selected.withIndex()) {
                if (cancelled()) return@withPrimary Result(verified, failed, selected.size, moved, true)
                checkActive()
                progress(index, selected.size)
                try {
                    primary.withHiddenCopySource(id,{!isActive()}) {item,source ->
                        checkActive()
                        val copied=target.importMedia(item.displayName,item.mimeType,source,createDistinctCopy=item.vaultOnly,origin=item.origin,vaultOnly=item.vaultOnly,capturedAtEpochMillis=item.capturedAtEpochMillis)
                        target.verify(copied.id)
                        check(copied.plaintextSize==item.plaintextSize && MessageDigest.isEqual(copied.plaintextSha256,item.plaintextSha256))
                        synchronized(monitor) {checkActive();verified++}
                        if(move && !cancelled()) runCatching {
                            primary.trashVerifiedSource(item) {remove ->
                                synchronized(monitor) {
                                    checkActive(); check(!cancelled())
                                    hidden.commit(hiddenOperation) {remove();moved++}
                                }
                            }
                        }
                    }
                } catch (failure: Exception) {
                    checkActive() // Revocation stops the batch; ordinary item failures allow remaining items.
                    failed++
                }
                progress(index + 1, selected.size)
            }
            checkActive()
            Result(verified, failed, selected.size, moved, cancelled())
        } catch (failure: Exception) {
            if (!cancelled()) throw failure
            Result(verified, failed, selected.size, moved, true)
        } finally {
            // Authenticated cleanup removes orphan ciphertext only; committed items remain recoverable.
            // If Hidden was revoked, its normal next-unlock reconcile performs this cleanup instead.
            runCatching { HiddenRepository(context, hidden).reconcile() }
        }
    }

    override fun close() = synchronized(monitor) {
        closed = true
        key?.fill(0); key = null
        hiddenOperation.close()
    }
}
