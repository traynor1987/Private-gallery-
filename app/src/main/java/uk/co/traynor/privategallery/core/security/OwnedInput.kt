package uk.co.traynor.privategallery.core.security

import java.io.InputStream

/** Opaque original input ownership; forwarding views preserve the exact creating guard. */
class OwnedInput internal constructor(
    internal val guard: ScopedIoGuard,
    internal val resource: OwnedResource<*>,
    internal val stream: InputStream,
) : AutoCloseable {
    internal fun adopt(receiver: ScopedIoGuard): InputStream {
        try {
            check(receiver === guard) { "Input belongs to another original guard" }
            check(!resource.original.retiring) { "Original input retired" }
            receiver.check()
            return stream
        } catch (failure: Throwable) { resource.close(); throw failure }
    }
    /** Only nonblocking forwarding views may preserve this ownership stamp. */
    internal fun forward(view: InputStream): OwnedInput {
        try { return OwnedInput(guard, resource, view) }
        catch (failure: Throwable) { resource.close(); throw failure }
    }
    internal fun dispatchRetirement() = resource.close()
    override fun close() = guard.retire(resource)
}
