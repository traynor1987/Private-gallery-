package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** One synchronous Native leaf with no raw getter or acknowledgement cell/future on the facade.
 * Trusted Unit consumers must not retain the borrowed value; every caller requires an audit. */
internal class OwnedMainThreadValue<T : Any> internal constructor(
    private val owned: OwnedResource<OriginalMainValue<T>>,
) : AutoCloseable {
    val retirement: RetirementAcknowledgement get() = owned.retirement
    val releaseFailed: Boolean get() = owned.releaseFailed
    fun useValue(action: (T) -> Unit) = owned.value.useValue(action)
    override fun close() = owned.close()
}

/** Private acknowledgement source: posting and synchronous Native return are distinct. */
internal class OriginalMainValue<T : Any> internal constructor(
    private val guard: ScopedIoGuard,
    private val original: ReleaseReservation,
    private val post: (Runnable) -> Boolean,
    private val isMain: () -> Boolean,
    private val dispose: (T) -> Unit,
) : AcknowledgedCloseable {
    private val gate = Any()
    private val acknowledged = CompletableFuture<Unit>()
    private var factoryPending = true
    private var actual: T? = null
    private var using = false
    private var retired = false
    private var scheduled = false
    private var postingReturned = false
    private var postingSucceeded = false
    private var callbackClaimed = false
    private var callbackReturned = false
    private var failure: Throwable? = null
    // Allocated before Native construction, not when a release worker begins.
    private val invocation = Runnable {
        val value = synchronized(gate) {
            if (callbackClaimed) null else { callbackClaimed = true; actual }
        } ?: return@Runnable
        var thrown: Throwable? = null
        try {
            guard.requireNativeOutsideAuthorityGate()
            check(isMain()) { "Original Native release requires Main" }
            dispose(value)
        } catch (problem: Throwable) { thrown = problem }
        synchronized(gate) {
            callbackReturned = true
            if (thrown == null) actual = null
            if (failure == null) failure = thrown
        }
        completeIfReady()
    }

    /** Metadata-only association; the reservation gate may be held here. */
    internal fun bind(value: T) = synchronized(gate) {
        check(factoryPending && actual == null) { "Original Main value already bound" }
        actual = value
    }
    internal fun finishFactory() {
        synchronized(gate) { factoryPending = false }
        scheduleIfReady()
    }
    internal fun failFactory(problem: Throwable, entered: Boolean) {
        synchronized(gate) {
            factoryPending = false
            if (entered && failure == null) failure = problem
        }
        scheduleIfReady()
    }
    internal fun useValue(action: (T) -> Unit) {
        guard.requireNativeOutsideAuthorityGate()
        guard.check()
        check(isMain()) { "Original Native use requires Main" }
        val value = synchronized(gate) {
            check(!retired && !original.retiring && !factoryPending && !using) { "Original Main value unavailable" }
            checkNotNull(actual).also { using = true }
        }
        try {
            guard.requireMainNativeUse(original, this, value)
            action(value)
            guard.requireMainNativeUse(original, this, value)
        } finally {
            synchronized(gate) { using = false }
            scheduleIfReady()
        }
    }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }
        scheduleIfReady()
        return acknowledged
    }
    private fun scheduleIfReady() {
        val shouldPost = synchronized(gate) {
            if (!retired || factoryPending || using || scheduled || actual == null) false
            else { scheduled = true; true }
        }
        if (shouldPost) {
            var success = false
            var thrown: Throwable? = null
            try {
                success = post(invocation)
                if (!success) thrown = IllegalStateException("Original Main posting unavailable")
            } catch (problem: Throwable) { thrown = problem }
            synchronized(gate) {
                postingReturned = true
                postingSucceeded = success
                if (failure == null) failure = thrown
            }
        }
        completeIfReady()
    }
    private fun completeIfReady() {
        var ready = false
        var problem: Throwable? = null
        synchronized(gate) {
            if (retired && !factoryPending && !using) {
                problem = failure
                ready = problem != null || (actual == null && !scheduled) ||
                    (postingReturned && postingSucceeded && callbackReturned)
            }
        }
        // Completion invokes owning callbacks; never do it beneath the private gate.
        if (ready) {
            val captured = problem
            if (captured == null) acknowledged.complete(Unit) else acknowledged.completeExceptionally(captured)
        }
    }
}
