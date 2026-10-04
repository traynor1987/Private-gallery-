package uk.co.traynor.privategallery.core.security

/** Exact secondary Native child. Synchronous use and native disposal run outside
 * the gate; independent original unblockers must make active calls return.
 * Callbacks must not retain raw values, publish success, or await this original. */
internal class OwnedNativeUse<T : Any> private constructor(
    private val guard: ScopedIoGuard,
    private val original: ReleaseReservation,
    private val childIndex: Int,
    private val disposeNative: (T) -> Unit,
) {
    private val gate = java.lang.Object()
    @Volatile private var actual: T? = null
    private var retired = false
    private var activeThread: Thread? = null

    private fun checkedActual(): T {
        guard.check()
        val value = checkNotNull(actual) { "Original Native binding unavailable" }
        original.requireNativeUse(childIndex, value)
        return value
    }
    fun useInt(action: (T) -> Int): Int = use(action)
    fun useLong(action: (T) -> Long): Long = use(action)
    private fun <R> use(action: (T) -> R): R {
        val value = checkedActual()
        synchronized(gate) {
            check(!retired && activeThread == null) { "Original Native invocation unavailable" }
            activeThread = Thread.currentThread()
        }
        try {
            checkedActual()
            val result = action(value)
            checkedActual()
            synchronized(gate) { check(!retired) { "Original Native child retired" } }
            return result
        } finally {
            synchronized(gate) { activeThread = null; gate.notifyAll() }
        }
    }
    /** The funded adapter supplies exact raw T even before handle binding.
     * Late field binding never resets retired or controls this actual disposer. */
    private fun dispose(value: T) {
        synchronized(gate) {
            check(activeThread !== Thread.currentThread()) { "Native invocation cannot dispose itself" }
            retired = true
            // Interruption throws without native disposal; original charge stays failed.
            while (activeThread != null) gate.wait()
        }
        disposeNative(value)
    }
    companion object {
        internal fun <T : Any> create(guard: ScopedIoGuard, original: ReleaseReservation,
            index: Int, dispose: (T) -> Unit, factory: () -> T): OwnedNativeUse<T> {
            require(index > 0) { "Native invocation requires an original secondary child" }
            val use = OwnedNativeUse(guard, original, index, dispose)
            // All wrappers/method references precede actual Native construction.
            val release: (T) -> Unit = use::dispose
            val acquire: () -> T = { guard.requireNativeConstruction(original, index); factory() }
            val reserved = original.createValue(index, release, acquire)
            // Raw identity and funded disposal already attached; bounded field assignment.
            use.actual = reserved.value
            return use
        }
    }
}
