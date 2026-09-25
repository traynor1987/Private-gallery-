package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.*

internal class LocalWorkerStopTimeout : Exception()

internal suspend fun <T> withLocalWorkerLifetime(
    dispatcher: CoroutineDispatcher,
    publish: (CompletableDeferred<Unit>) -> Unit,
    timeoutMillis: Long = 5000,
    operation: suspend (CompletableDeferred<Unit>) -> T,
): T {
    val stopped = CompletableDeferred<Unit>()
    var entered = false
    try { return withContext(dispatcher) {
        entered = true
        publish(stopped)
        operation(stopped)
    } }
    finally {
        if (!entered) stopped.complete(Unit)
        val dead = withContext(NonCancellable) { withTimeoutOrNull(timeoutMillis) { stopped.await(); true } == true }
        if (!dead && currentCoroutineContext().isActive) throw LocalWorkerStopTimeout()
    }
}
