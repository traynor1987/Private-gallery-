package uk.co.traynor.privategallery.core.security

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Original operation only. No ambient session lookup, timer dependency or destination selection. */
class ScopedIoGuard(private val operation: PrimaryOperation, private val scope: PrimaryScope) {
    fun check() = operation.requireScope(scope)
    fun <T : AutoCloseable> own(resource: T): T = operation.own(resource)
    fun <T> commit(action: () -> T): T { check(); return operation.commit(action) }
    fun input(input: InputStream): InputStream = own(object : FilterInputStream(input) {
        // A revoked network connection may already have disconnected the underlying stream.
        override fun close() { try { `in`.close() } catch (_: java.io.IOException) { } }
        override fun read(): Int { check(); return `in`.read().also { check() } }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            check()
            try { return `in`.read(b, off, len).also { check() } }
            catch (failure: Throwable) { b.fill(0, off, off + len); throw failure }
        }
    })
    fun output(output: OutputStream): OutputStream = own(object : FilterOutputStream(output) {
        override fun close() { try { out.close() } catch (_: java.io.IOException) { } }
        override fun write(value: Int) { check(); out.write(value); check() }
        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            val end = off + len
            while (offset < end) {
                check()
                val count = minOf(16 * 1024, end - offset)
                out.write(b, offset, count)
                offset += count
            }
            check()
        }
        override fun flush() { check(); out.flush(); check() }
    })
}

/** Propagates immutable network authority through provider adapters and dispatcher switches. */
class PrimaryIoContext(val guard: ScopedIoGuard) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PrimaryIoContext>
}
