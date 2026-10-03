package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Job
import java.util.concurrent.CancellationException

/** Preallocated before Job creation. Cancellation return and actual completion are separate. */
internal class ReservedJobRelease : AcknowledgedCloseable {
    private val ready = CountDownLatch(1)
    private val completed = CompletableFuture<Unit>()
    @Volatile private var job: Job? = null
    private val tracking = Any()
    private var hookInstalled = false
    private var finalizationSeen = false
    private var finalizationFailure: Throwable? = null
    private var trackingFailure: Throwable? = null

    fun create(attachOriginal: (ReservedJobRelease) -> Unit = {}, factory: ((Job) -> Unit) -> Job) {
        try {
            val returned = factory { attachJob(it, attachOriginal) }
            val attached = job === returned
            if (job == null) attachJob(returned, attachOriginal) // Own a known malformed result under its funded slot.
            check(attached) { "Job result lacks immediate original attachment" }
        } catch (failure: Throwable) {
            // An attached partial Job retains its REAL completion obligation on factory failure.
            if (job == null) completed.complete(Unit)
            ready.countDown()
            throw failure
        }
    }

    internal fun owns(value: Any): Boolean = job === value
    internal val actualJob: Job get() = checkNotNull(job)

    private fun attachJob(actual: Job, attachOriginal: (ReservedJobRelease) -> Unit) {
        synchronized(this) {
            check(job == null) { "Unreserved duplicate Job attachment" }
            job = actual
        }
        ready.countDown()
        attachOriginal(this) // Bind the actual child before hook installation can throw.
        // Revocation can cancel this exact partial child before hook installation.
        try {
            actual.invokeOnCompletion { cause ->
                synchronized(tracking) {
                    finalizationSeen = true
                    finalizationFailure = failedFinalization(cause)
                }
                acknowledgeIfReady()
            }
            synchronized(tracking) { hookInstalled = true }
            acknowledgeIfReady()
        } catch (failure: Throwable) {
            synchronized(tracking) { trackingFailure = failure }
            acknowledgeIfReady()
            throw failure
        }
    }

    private fun acknowledgeIfReady() {
        val outcome = synchronized(tracking) {
            when {
                trackingFailure != null -> trackingFailure
                hookInstalled && finalizationSeen -> finalizationFailure
                else -> PENDING
            }
        }
        if (outcome === PENDING) return
        if (outcome == null) completed.complete(Unit) else completed.completeExceptionally(outcome as Throwable)
    }

    /** Cancellation is neutral only when the observed cause chain contains no failed teardown. */
    private fun failedFinalization(cause: Throwable?): Throwable? {
        var cursor = cause
        repeat(16) {
            val current = cursor ?: return null
            if (current !is CancellationException || current.suppressed.isNotEmpty()) return cause
            cursor = current.cause
        }
        return cause // Unknown/deep/cyclic failure is never a positive acknowledgement.
    }

    private companion object { val PENDING = Any() }

    fun retireOnCompletion(retire: () -> Unit) {
        completed.whenComplete { _, _ -> retire() }
    }

    override fun closeAcknowledged(): CompletionStage<Unit> {
        // Production createJob attaches only after actual Job binding and ready countdown,
        // so its release worker never waits for unavailable construction. The standalone
        // attach-before-create regression path retains this legacy boundary until removed.
        // Independent transports/children always have separate pre-funded slots.
        ready.await()
        job?.cancel()
        return completed
    }
}
