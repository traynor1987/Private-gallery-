package uk.co.traynor.privategallery.core.security

/** Preallocated disposal adapter for native values that do not implement AutoCloseable. */
class ReservedValue<T : Any> internal constructor(
    private val dispose: (T) -> Unit,
) : AutoCloseable {
    @Volatile private var actual: T? = null
    val value: T get() = checkNotNull(actual) { "Original native value unavailable" }
    internal fun owns(value: Any): Boolean = actual === value
    internal fun bind(value: T) { actual = value }

    override fun close() = dispose(value)
}
