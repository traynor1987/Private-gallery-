package uk.co.traynor.privategallery.core.security

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.AbstractCoroutine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/**
 * A session teardown is not optional producer work: even cancellation before the
 * first dispatcher turn must enter its finally and await the actual cleanup.
 * The inert active root is created only inside the original funded Job factory.
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal fun PrimaryOperation.createSessionCleanupJob(
    context: CoroutineContext,
    cleanup: suspend () -> Unit,
): Job {
    var actual: SessionCleanupCoroutine? = null
    try {
        return createSessionOwnedJob { attach ->
            SessionCleanupCoroutine(context, cleanup).also {
                actual = it // Retain before attachment/hook installation can throw.
                attach(it)
            }
        }
    } finally {
        // A stale factory postcheck can reject after original attachment. The
        // same cancelled coroutine must still run its teardown in that case.
        actual?.begin()
    }
}

@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private class SessionCleanupCoroutine(context: CoroutineContext, private val cleanup: suspend () -> Unit) :
    AbstractCoroutine<Unit>(context, initParentJob = true, active = true) {
    fun begin() = start(CoroutineStart.ATOMIC, this) {
        try { awaitCancellation() }
        finally { withContext(NonCancellable) { cleanup() } }
    }

    // The original ReservedJobRelease observes and retains this failure. Avoid
    // reporting a potentially sensitive exception through a global log handler.
    override fun handleJobException(exception: Throwable): Boolean = true
}
