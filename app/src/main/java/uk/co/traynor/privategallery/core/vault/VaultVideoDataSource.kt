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
    @Volatile private var closed = false
    val sourceFactory: DataSource.Factory = DataSource.Factory {
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
                reader = ChunkedVaultVideoStore.open(stored, ownedKey)
                position = dataSpec.position
                if (position > checkNotNull(reader).size) throw IOException("Invalid video position")
                remaining = if (dataSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong())
                    checkNotNull(reader).size - position else minOf(dataSpec.length, checkNotNull(reader).size - position)
                uri = dataSpec.uri
                return remaining
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (closed || !allowed()) { close(); throw IOException("Vault locked") }
                if (remaining == 0L) return -1
                val count = checkNotNull(reader).readAt(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
                if (count > 0) { position += count; remaining -= count }
                return count
            }
            override fun getUri(): Uri? = uri
            override fun close() { reader?.close(); reader = null; uri = null; remaining = 0 }
        }
    }
    override fun close() { closed = true; ownedKey.fill(0) }
}
