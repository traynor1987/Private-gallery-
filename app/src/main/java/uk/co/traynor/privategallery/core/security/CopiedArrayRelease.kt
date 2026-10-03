package uk.co.traynor.privategallery.core.security

/** Allocate before copying. The actual array binds without a new callback or release node. */
internal class CopiedArrayRelease(private val readerMutex: Any?) : AutoCloseable {
    private var actual: ByteArray? = null
    internal fun requireEmpty() { check(actual == null) { "Copied array obligation already bound" } }
    internal fun bind(copy: ByteArray) { actual = copy }
    override fun close() {
        val bytes = checkNotNull(actual)
        if (readerMutex == null) bytes.fill(0)
        else synchronized(readerMutex) { bytes.fill(0) }
    }
}
