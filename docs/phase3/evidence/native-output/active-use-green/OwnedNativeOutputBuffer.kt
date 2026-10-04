package uk.co.traynor.privategallery.core.security

/** Original bounded sample/output array; synchronous Native calls run outside its gate.
 * Consumers must not retain backing views or await their own original's retirement.
 * Independently owned Native unblockers never wait for this array's quiescence. */
internal class OwnedNativeOutputBuffer(private val guard: ScopedIoGuard, size: Int) : AutoCloseable {
    private val gate = java.lang.Object()
    @Volatile private var retired = false
    private var activeThread: Thread? = null
    // Last potentially allocating construction step; bind before any next factory step.
    private val actual = ByteArray(size.also { require(it in 1..256 * 1024) })

    fun useBytes(consume: (ByteArray) -> Int): Int {
        guard.check()
        synchronized(gate) {
            check(!retired && activeThread == null) { "Original Native buffer unavailable" }
            activeThread = Thread.currentThread()
        }
        try {
            guard.check()
            val result = consume(actual)
            guard.check()
            synchronized(gate) { check(!retired) { "Original Native buffer retired" } }
            return result
        } finally {
            synchronized(gate) { activeThread = null;gate.notifyAll() }
        }
    }

    override fun close() = synchronized(gate) {
        check(activeThread !== Thread.currentThread()) { "Native invocation cannot retire its own buffer" }
        retired = true
        // wait releases this private gate. Interrupted/failed wipe throws and stays charged.
        while (activeThread != null) gate.wait()
        actual.fill(0)
    }
}
