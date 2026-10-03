package uk.co.traynor.privategallery.core.security

/** Bounded Native output array. This prototype retains a reader gate across the call. */
internal class OwnedNativeOutputBuffer(private val guard: ScopedIoGuard, size: Int) : AutoCloseable {
    private val gate = Any()
    @Volatile private var retired = false
    private val actual = ByteArray(size.also { require(it in 1..256 * 1024) })
    fun useBytes(consume: (ByteArray) -> Int): Int = synchronized(gate) {
        check(!retired);guard.check();consume(actual).also { guard.check();check(!retired) }
    }
    override fun close() { retired = true;synchronized(gate) { actual.fill(0) } }
}
