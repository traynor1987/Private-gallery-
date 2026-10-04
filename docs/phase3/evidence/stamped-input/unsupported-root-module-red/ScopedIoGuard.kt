package uk.co.traynor.privategallery.core.security

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Original operation only. No ambient session lookup, timer dependency or destination selection. */
class ScopedIoGuard(private val operation: PrimaryOperation, private val scope: PrimaryScope) {
    fun check() = operation.requireScope(scope)
    fun requireScope(required: PrimaryScope) = operation.requireScope(required)
    // Transitional connections/files still use this API until their complete manifests migrate.
    fun <T : AutoCloseable> own(resource: T): T = operation.own(resource)
    fun <T : AutoCloseable> createOwned(manifest: OwnedResourceManifest, factory: OwnedFactoryScope.() -> T): OwnedResource<T> {
        check()
        return operation.createOwned(manifest, factory)
    }
    /** Internal complete producer input; root0 must be native-created by this exact scope. */
    internal fun <T : InputStream> createOwnedInput(manifest: OwnedResourceManifest,
        factory: OwnedFactoryScope.() -> ReservedValue<T>): OwnedInput {
        var failedOriginal: ReleaseReservation? = null
        val original = try { createOwned(manifest) {
            failedOriginal = this.original
            factory()
        } } catch (failure: Throwable) {
            try { failedOriginal?.let { retire(it) } }
            catch (releaseFailure: Throwable) { failure.addSuppressed(releaseFailure) }
            throw failure
        }
        try { return OwnedInput(this, original, checkedInput(original.value.value, original)) }
        catch (failure: Throwable) { original.close(); throw failure }
    }
    fun <T> commit(action: () -> T): T { check(); return operation.commit(action) }
    fun <T : java.net.HttpURLConnection> connection(factory: () -> T): ScopedConnection<T> {
        val original = createOwned(OwnedResourceManifest.io("connection")) {
            create("connection", { connection: T -> connection.disconnect() }, factory)
        }
        try { return ScopedConnection(this, original) }
        catch (failure: Throwable) { original.close(); throw failure }
    }
    /** Complete native connection/input manifest funded before either child can exist. */
    fun <T : java.net.HttpURLConnection> connectedInput(factory: () -> T,
        prepare: (T) -> Unit = {}, open: (T) -> InputStream = { it.inputStream }): InputStream = connectedOwnedInput(factory, prepare, open).stream
    fun <T : java.net.HttpURLConnection> connectedOwnedInput(factory: () -> T,
        prepare: (T) -> Unit = {}, open: (T) -> InputStream = { it.inputStream }): OwnedInput {
        var input: InputStream? = null
        var failedOriginal: ReleaseReservation? = null
        val original = try { createOwned(OwnedResourceManifest.io("connection", "stream")) {
            failedOriginal = this.original
            val connection = create("connection", { actual: T -> actual.disconnect() }, factory)
            check()
            prepare(connection.value)
            check()
            input = create("stream", { actual: InputStream -> actual.close() }, { open(connection.value) }).value
            connection
        } } catch (failure: Throwable) {
            try { failedOriginal?.let { retire(it) } }
            catch (releaseFailure: Throwable) { failure.addSuppressed(releaseFailure) }
            throw failure
        }
        try { return OwnedInput(this, original, checkedInput(checkNotNull(input), original)) }
        catch (failure: Throwable) { original.close(); throw failure }
    }
    fun <T> commit(scope: PrimaryScope, action: () -> T): T {
        operation.requireScope(scope)
        return operation.commit(action)
    }
    fun input(factory: () -> InputStream): InputStream {
        val owned = createOwned(OwnedResourceManifest.io("stream")) { attach("stream", factory()) }
        return checkedInput(owned.value, owned)
    }
    private fun checkedInput(input: InputStream, owned: OwnedResource<*>): InputStream {
        try {
            return object : FilterInputStream(input) {
                @Volatile private var retired = false
                private fun checkOpen() { check(!retired && !owned.original.retiring) { "Original stream retired" }; check() }
                override fun close() { retired = true; retire(owned) }
                override fun read(): Int { checkOpen(); return `in`.read().also { checkOpen() } }
                override fun skip(n: Long): Long { checkOpen(); return `in`.skip(n).also { checkOpen() } }
                override fun available(): Int { checkOpen(); return `in`.available().also { checkOpen() } }
                override fun mark(readlimit: Int) { checkOpen(); `in`.mark(readlimit); checkOpen() }
                override fun reset() { checkOpen(); `in`.reset(); checkOpen() }
                override fun markSupported(): Boolean { checkOpen(); return `in`.markSupported().also { checkOpen() } }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (off < 0 || len < 0 || off > b.size - len) throw IndexOutOfBoundsException()
                    try { checkOpen(); return `in`.read(b, off, len).also { checkOpen() } }
                    catch (failure: Throwable) { b.fill(0, off, off + len); throw failure }
                }
            }
        } catch (failure: Throwable) { owned.close(); throw failure }
    }
    fun output(factory: () -> OutputStream): OutputStream {
        val owned = createOwned(OwnedResourceManifest.io("stream")) { attach("stream", factory()) }
        try {
            return object : FilterOutputStream(owned.value) {
                @Volatile private var retired = false
                private fun checkOpen() { check(!retired && !owned.original.retiring) { "Original stream retired" }; check() }
                override fun close() { retired = true; retire(owned) }
                override fun write(value: Int) { checkOpen(); out.write(value); checkOpen() }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (off < 0 || len < 0 || off > b.size - len) throw IndexOutOfBoundsException()
                    var offset = off
                    val end = off + len
                    while (offset < end) {
                        checkOpen()
                        val count = minOf(16 * 1024, end - offset)
                        out.write(b, offset, count)
                        offset += count
                    }
                    checkOpen()
                }
                override fun flush() { checkOpen(); out.flush(); checkOpen() }
            }
        } catch (failure: Throwable) { owned.close(); throw failure }
    }

    internal fun retire(owned: OwnedResource<*>) = retire(owned.original)
    internal fun retire(original: ReleaseReservation) {
        original.release()
        // Normal disposal waits outside authority gates for actual close return AND
        // owning accounting return, terminal child markers and slot return.
        // Failed native close cannot become successful .use.
        while (!original.terminalRetirement.await(1, TimeUnit.SECONDS)) {
            if (original.failed) throw IOException("Original protected stream release failed")
        }
    }
}

/** Propagates immutable network authority through provider adapters and dispatcher switches. */
class PrimaryIoContext(val guard: ScopedIoGuard) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PrimaryIoContext>
}
