package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.*

internal class LocalResourceLimit(val reason: LocalStopReason, val snapshot: DeviceResources) : Exception()

/** Cancellation reaches the worker's existing finally/cleanup path before returning failure. */
internal suspend fun <T> withLocalResourceGuard(
    resources: () -> DeviceResources,
    onPressure: (DeviceResources) -> Unit,
    model: ModelSpec = ModelCatalog.lightweight,
    intervalMillis: Long = 1000,
    block: suspend () -> T,
): T = coroutineScope {
    val generation = async { block() }
    var pressure: Pair<LocalStopReason, DeviceResources>? = null
    val monitor = launch {
        while (generation.isActive) {
            delay(intervalMillis)
            if (!generation.isActive) break
            val snapshot = resources()
            if (LocalCapabilityPolicy.shouldStop(model, snapshot)) {
                val reason = when {
                    snapshot.tooHot -> LocalStopReason.THERMAL
                    snapshot.lowMemory -> LocalStopReason.ANDROID_LOW_MEMORY
                    else -> LocalStopReason.OWN_PRESSURE_RESERVE
                }
                pressure = reason to snapshot
                onPressure(snapshot)
                generation.cancel(CancellationException(reason.name))
            }
        }
    }
    try { generation.await() } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        pressure?.let { throw LocalResourceLimit(it.first, it.second) }
        throw cancelled
    } finally { monitor.cancel() }
}
