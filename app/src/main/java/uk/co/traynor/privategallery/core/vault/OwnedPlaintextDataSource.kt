@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.vault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import uk.co.traynor.privategallery.core.security.OwnedPlaintext

/** The page owns the source. Media3 receives only bounded copies into its own
 * buffers; no data source ever retains a raw array across Native callbacks. */
internal class OwnedPlaintextDataSource(private val source: OwnedPlaintext) : DataSource {
    private var position = 0L
    private var remaining = 0L
    private var uri: Uri? = null
    override fun addTransferListener(listener: TransferListener) = Unit
    override fun open(spec: DataSpec): Long {
        close()
        val size = source.size.toLong()
        require(spec.position in 0..size) { "Invalid protected media position" }
        position = spec.position
        remaining = if (spec.length == C.LENGTH_UNSET.toLong()) size - position else minOf(spec.length, size - position)
        require(remaining >= 0)
        uri = spec.uri
        return remaining
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check(uri != null) { "Protected media source closed" }
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = source.readAt(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) { position += count; remaining -= count }
        return count
    }
    override fun getUri(): Uri? = uri
    override fun close() { uri = null; position = 0; remaining = 0 }
    companion object { fun factory(source: OwnedPlaintext) = DataSource.Factory { OwnedPlaintextDataSource(source) } }
}
