package uk.co.traynor.privategallery.ui

import android.net.Uri
import android.annotation.SuppressLint
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import kotlin.math.min

/**
 * Seekable Media3 source backed solely by bytes already held in process memory.
 * It never opens a file, content URI, or network connection.
 */
@SuppressLint("UnsafeOptInUsageError")
class ByteArrayDataSource(private val bytes: ByteArray) : BaseDataSource(false) {
    private val media = InMemoryMediaBytes(bytes)
    private var openedUri: Uri? = null
    private var position = 0
    private var remaining = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val requestedPosition = dataSpec.position
        if (requestedPosition < 0 || requestedPosition > media.size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        openedUri = dataSpec.uri
        position = requestedPosition.toInt()
        remaining = media.availableAt(position, dataSpec.length)
        transferStarted(dataSpec)
        return remaining.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0) return C.RESULT_END_OF_INPUT
        val copied = min(length, remaining)
        media.copyAt(position, buffer, offset, copied)
        position += copied
        remaining -= copied
        bytesTransferred(copied)
        return copied
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        if (openedUri != null) {
            openedUri = null
            transferEnded()
        }
    }
}

/** Small JVM-testable range helper used by the protected Media3 source. */
class InMemoryMediaBytes(private val bytes: ByteArray) {
    val size: Int get() = bytes.size

    fun availableAt(position: Int, requestedLength: Long): Int {
        require(position in 0..bytes.size)
        return when (requestedLength) {
            C.LENGTH_UNSET.toLong() -> bytes.size - position
            else -> min(requestedLength, (bytes.size - position).toLong()).toInt()
        }
    }

    fun copyAt(position: Int, output: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return 0
        val copied = min(length, bytes.size - position)
        bytes.copyInto(output, offset, position, position + copied)
        return copied
    }
}
