package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** One credential comparison/verification key, never a session-admission capability.
 * Trusted synchronous factory/consumer must not retain arrays or start unjoined use.
 * Its original exists before provider entry; every exit requests normal retirement. */
internal class OwnedTemporaryKey internal constructor(
    private val guard: ScopedIoGuard,
    private val owned: OwnedResource<OriginalTemporaryKey>,
) : AutoCloseable {
    val retirement: RetirementAcknowledgement get() = owned.retirement
    fun withProduced(factory: () -> ByteArray, consume: (ByteArray) -> Unit) {
        var entered = false
        try {
            guard.requireNativeOutsideAuthorityGate()
            guard.requireScope(PrimaryScope.CREDENTIALS)
            owned.original.beginTemporaryKey(owned.value)
            entered = true
            owned.value.bind(factory())
            guard.check()
            owned.original.requireNativeUse(0, owned.value)
            owned.value.consume(consume)
            guard.check()
            owned.original.requireNativeUse(0, owned.value)
        } finally {
            // Both real synchronous callbacks have unwound, including exceptional exits.
            if (entered) owned.value.producerReturned()
            close()
        }
    }
    override fun close() = owned.close()
}

/** Inert before attachment; cancellation invalidation never invokes callbacks under gates. */
internal class OriginalTemporaryKey : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var used = false
    private var producing = false
    private var retired = false
    private var key: ByteArray? = null
    internal fun begin() = synchronized(gate) {
        check(!used && !retired) { "Temporary key unavailable" }
        used = true; producing = true
    }
    internal fun bind(value: ByteArray) = synchronized(gate) {
        if (value.size != 32) { value.fill(0); error("Invalid temporary key") }
        check(producing && key == null)
        key = value
    }
    internal fun consume(action: (ByteArray) -> Unit) {
        val value = synchronized(gate) { check(producing && !retired); checkNotNull(key) }
        action(value)
    }
    internal fun producerReturned() {
        synchronized(gate) { producing = false }
        finishIfReady()
    }
    internal fun invalidate() = synchronized(gate) {
        retired = true
        if (!producing) { key?.fill(0); key = null }
    }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        invalidate(); finishIfReady(); return done
    }
    private fun finishIfReady() {
        val ready = synchronized(gate) {
            if (retired && !producing) { key?.fill(0); key = null; true } else false
        }
        if (ready) done.complete(Unit)
    }
}
