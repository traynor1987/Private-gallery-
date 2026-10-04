package uk.co.traynor.privategallery.core.security

/** Original bounded sample/output array; synchronous Native calls run outside its gate.
 * Consumers must not retain backing views or await their own original's retirement.
 * Independently owned Native unblockers never wait for this array's quiescence.
 * Failed callbacks/results clear the original after callback return, before releasing
 * this borrow. That clearing is not Native or original retirement acknowledgement. */
internal class OwnedNativeOutputBuffer(private val guard: ScopedIoGuard, private val original: ReleaseReservation,
    private val childIndex: Int, size: Int) : AutoCloseable {
    private val gate = java.lang.Object()
    @Volatile private var retired = false
    private var activeThread: Thread? = null
    init { guard.requireNativeConstruction(original, childIndex) }
    // Last potentially allocating construction step; bind before any next factory step.
    private val actual = ByteArray(size.also { require(it in 1..256 * 1024) })

    private fun checkOpen() { guard.check(); original.requireNativeUse(childIndex, this) }
    /** Permanent identity/shape preflight; never borrows or exposes backing bytes. */
    internal fun requireStagingBinding(expectedGuard:ScopedIoGuard,expectedOriginal:ReleaseReservation,size:Int) {
        check(guard===expectedGuard && original===expectedOriginal){"Foreign staging ciphertext workspace"}
        require(actual.size==size){"Fixed staging ciphertext workspace required"}
        guard.requireNativeOutsideAuthorityGate();checkOpen()
    }

    fun useBytes(consume: (ByteArray) -> Int): Int {
        checkOpen()
        synchronized(gate) {
            check(!retired && activeThread == null) { "Original Native buffer unavailable" }
            activeThread = Thread.currentThread()
        }
        try {
            checkOpen()
            val result = consume(actual)
            checkOpen()
            synchronized(gate) { check(!retired) { "Original Native buffer retired" } }
            return result
        } catch (failure: Throwable) {
            // Only this admitted exclusive borrow reaches here. The synchronous
            // callback has unwound; another rejected borrower cannot wipe live JNI.
            actual.fill(0)
            throw failure
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
