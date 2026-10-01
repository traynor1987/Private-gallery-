@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.vault

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/** Media3 sees only a synthetic URI and authenticated, bounded plaintext chunks. */
class VaultVideoSession(private val stored: StoredPayload, key: ByteArray, private val allowed: () -> Boolean) : AutoCloseable {
    private val ownedKey = key.copyOf()
    private val gate = Any()
    private var closed = false
    private val liveSources = mutableSetOf<Source>()
    val sourceFactory: DataSource.Factory = DataSource.Factory {
        synchronized(gate) {
            check(!closed) { "Vault video session closed" }
            Source()
        }
    }

    /** A single gate prevents session closure from racing open, chunk decryption or Reader use. */
    private inner class Source : DataSource {
        private var reader: ChunkedVaultVideoStore.Reader? = null
        private var position = 0L
        private var remaining = 0L
        private var uri: Uri? = null
        override fun addTransferListener(listener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = synchronized(gate) {
            closeLocked()
            if (closed || !allowed()) throw IOException("Vault locked")
            try {
                reader = ChunkedVaultVideoStore.open(stored, ownedKey)
                liveSources.add(this)
                position = dataSpec.position
                if (position > checkNotNull(reader).size) throw IOException("Invalid video position")
                remaining = if (dataSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong())
                    checkNotNull(reader).size - position else minOf(dataSpec.length, checkNotNull(reader).size - position)
                if (remaining < 0) throw IOException("Invalid video length")
                uri = dataSpec.uri
                remaining
            } catch (failure: Throwable) { closeLocked(); throw failure }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = synchronized(gate) {
            if (closed || !allowed()) { closeLocked(); throw IOException("Vault locked") }
            if (remaining == 0L) return@synchronized -1
            try {
                val count = checkNotNull(reader).readAt(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
                if (closed || !allowed()) throw IOException("Vault locked")
                if (count > 0) { position += count; remaining -= count }
                count
            } catch (failure: Throwable) {
                buffer.fill(0, offset, offset + length)
                closeLocked()
                throw failure
            }
        }
        override fun getUri(): Uri? = synchronized(gate) { uri }
        override fun close(): Unit = synchronized(gate) { closeLocked() }
        fun closeLocked() {
            val closing = reader
            reader = null; uri = null; remaining = 0
            liveSources.remove(this)
            closing?.close()
        }
    }

    override fun close(): Unit = synchronized(gate) {
        if (closed) return@synchronized
        closed = true
        var failure: Throwable? = null
        liveSources.toList().forEach { source ->
            try { source.closeLocked() } catch (caught: Throwable) { if (failure == null) failure = caught }
        }
        liveSources.clear()
        ownedKey.fill(0)
        failure?.let { throw it }
    }
}
