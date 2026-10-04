package uk.co.traynor.privategallery.core.security

/** One bounded actual array, created only within an original preclaimed buffer child.
 * The reader mutex permits its independently funded wipe after the actual reader returns.
 * A transport/input unblocker must have its own independent original child. */
internal class OwnedByteBuffer(private val guard: ScopedIoGuard, size: Int) : AutoCloseable {
    private val readerMutex = Any()
    @Volatile private var retired = false
    // Last potentially allocating constructor step; there is no later unbound child.
    private val actual = ByteArray(size.also { require(it in 1..64 * 1024) })

    /** Supported internal consumers must not retain or return borrowed bytes; original disposal owns their wipe. */
    fun <T> useBytes(consume: (ByteArray) -> T): T = synchronized(readerMutex) {
        checkOpen()
        consume(actual).also { checkOpen() }
    }
    private fun checkOpen() { check(!retired) { "Original buffer retired" }; guard.check() }
    override fun close() {
        retired = true
        synchronized(readerMutex) { actual.fill(0) }
    }
}
