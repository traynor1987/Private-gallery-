package uk.co.traynor.privategallery.core.browser

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** An HTTP EOF is success only when every advertised byte arrived. Each retry owns a fresh instance. */
internal class CompleteResponseInputStream(input: InputStream, private val expectedBytes: Long?) : FilterInputStream(input) {
    private var received = 0L
    private fun record(count: Int): Int {
        if (count < 0 && expectedBytes != null && received != expectedBytes) throw IOException("Incomplete media response")
        if (count > 0) {
            received += count
            if (expectedBytes != null && received > expectedBytes) throw IOException("Media response exceeds advertised length")
        }
        return count
    }
    override fun read(): Int {
        val value = `in`.read()
        record(if (value < 0) -1 else 1)
        return value
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        if (length == 0) 0 else record(`in`.read(bytes, offset, length))
}
