package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.*

internal class LocalResourceLimit : Exception()

/** Cancellation reaches the worker's existing finally/cleanup path before returning failure. */
internal suspend fun <T> withLocalResourceGuard(
    resources: () -> DeviceResources,
    onPressure: (DeviceResources) -> Unit,
    intervalMillis: Long = 1000,
    block: suspend () -> T,
): T = coroutineScope {
    val generation = async { block() }
    var resourceStopped = false
    val monitor = launch {
        while (generation.isActive) {
            delay(intervalMillis)
            if (!generation.isActive) break
            val snapshot = resources()
            if (LocalCapabilityPolicy.shouldStop(snapshot)) {
                resourceStopped = true
                onPressure(snapshot)
                generation.cancel(CancellationException("Device resource limit"))
            }
        }
    }
    try { generation.await() } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        if (resourceStopped) throw LocalResourceLimit()
        throw cancelled
    } finally { monitor.cancel() }
}
