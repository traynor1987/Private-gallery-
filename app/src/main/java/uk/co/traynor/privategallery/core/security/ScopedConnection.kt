package uk.co.traynor.privategallery.core.security

import java.net.HttpURLConnection
import java.io.FilterInputStream
import java.io.InputStream

/** Native disconnect and normal disposal share exactly the original pre-funded ticket. */
class ScopedConnection<T : HttpURLConnection> internal constructor(
    private val guard: ScopedIoGuard,
    private val original: OwnedResource<ReservedValue<T>>,
) : AutoCloseable {
    @Volatile private var retired = false
    val value: T get() {
        check(!retired) { "Original connection retired" }
        guard.check()
        return original.value.value
    }
    /** Dispatch the independent transport before waiting for source close to return. */
    fun input(factory: (T) -> InputStream): InputStream {
        val input = guard.input { factory(value) }
        try {
            return object : FilterInputStream(input) {
                override fun close() {
                    beginClose()
                    try { input.close() } finally { this@ScopedConnection.close() }
                }
            }
        } catch (failure: Throwable) {
            beginClose()
            try { input.close() } finally { close() }
            throw failure
        }
    }
    private fun beginClose() { retired = true; original.close() }
    override fun close() { retired = true; guard.retire(original) }
}
