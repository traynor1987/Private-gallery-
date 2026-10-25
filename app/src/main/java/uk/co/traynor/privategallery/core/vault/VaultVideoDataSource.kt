@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.vault

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

interface ProtectedVideoSession : AutoCloseable { val sourceFactory: DataSource.Factory }

/** Media3 sees only a synthetic URI and authenticated, bounded plaintext chunks. */
class VaultVideoSession(private val stored: StoredPayload, key: ByteArray, private val allowed: () -> Boolean) : ProtectedVideoSession {
    private val ownedKey = key.copyOf()
    @Volatile private var closed = false
    private val readers = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<ChunkedVaultVideoStore.Reader, Boolean>())
    @Synchronized private fun openReader(): ChunkedVaultVideoStore.Reader {
        if (closed || !allowed()) throw IOException("Vault locked")
        val reader = ChunkedVaultVideoStore.open(stored, ownedKey)
        if (closed || !allowed()) { reader.close(); throw IOException("Vault locked") }
        readers.add(reader)
        return reader
    }
    override val sourceFactory: DataSource.Factory = DataSource.Factory {
        check(!closed) { "Vault video session closed" }
        object : DataSource {
            private var reader: ChunkedVaultVideoStore.Reader? = null
            private var position = 0L
            private var remaining = 0L
            private var uri: Uri? = null
            override fun addTransferListener(listener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long {
                if (closed || !allowed()) throw IOException("Vault locked")
                close()
                try {
                    reader = openReader()
                    position = dataSpec.position
                    if (position > checkNotNull(reader).size) throw IOException("Invalid video position")
                    remaining = if (dataSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong())
                        checkNotNull(reader).size - position else minOf(dataSpec.length, checkNotNull(reader).size - position)
                    if (remaining < 0) throw IOException("Invalid video length")
                    uri = dataSpec.uri
                    return remaining
                } catch (failure: Throwable) { close(); throw failure }
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
                try {
                    if (closed || !allowed()) throw IOException("Vault locked")
                    if (length == 0) return 0
                    if (remaining == 0L) return -1
                    val count = checkNotNull(reader).readAt(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
                    if (closed || !allowed()) throw IOException("Vault locked")
                    if (count > 0) { position += count; remaining -= count }
                    return count
                } catch (failure: Throwable) {
                    buffer.fill(0, offset, offset + length)
                    runCatching { close() }.exceptionOrNull()?.let { failure.addSuppressed(it) }
                    throw IOException("Vault video unavailable", failure)
                }
            }
            override fun getUri(): Uri? = uri
            override fun close() {
                val retiring = reader
                try { retiring?.close() }
                finally {
                    if (retiring != null) readers.remove(retiring)
                    reader = null; uri = null; remaining = 0
                }
            }
        }
    }
    @Synchronized override fun close() {
        closed = true
        ownedKey.fill(0)
        var failure: Throwable? = null
        try {
            readers.toList().forEach { reader ->
                try { reader.close() }
                catch (next: Throwable) { if (failure == null) failure = next else failure!!.addSuppressed(next) }
            }
        } finally { readers.clear() }
        failure?.let { throw it }
    }
}
