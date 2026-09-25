package uk.co.traynor.privategallery.core.editor.local

import android.os.PowerManager
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

internal class LocalResourceLimit(val reason: LocalStopReason, val snapshot: DeviceResources) : Exception()

/** Cancellation reaches the worker's existing finally/cleanup path before returning failure. */
@androidx.annotation.RequiresApi(29)
internal suspend fun <T> withLocalResourceGuard(
    resources: () -> DeviceResources,
    onPressure: (DeviceResources) -> Unit,
    model: ModelSpec = ModelCatalog.lightweight,
    intervalMillis: Long = 1000,
    power: PowerManager? = null,
    callbackExecutor: Executor? = null,
    block: suspend () -> T,
): T = coroutineScope {
    val generation = async { block() }
    val pressure = AtomicReference<Pair<LocalStopReason, DeviceResources>?>(null)
    fun stop(reason: LocalStopReason, snapshot: DeviceResources) {
        if (pressure.compareAndSet(null, reason to snapshot)) {
            runCatching { onPressure(snapshot) }
            generation.cancel(CancellationException(reason.name))
        }
    }
    val thermalListener = if (power != null && callbackExecutor != null) PowerManager.OnThermalStatusChangedListener { status ->
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) stop(LocalStopReason.THERMAL, resources())
    } else null
    val listening = thermalListener != null && runCatching {
        power!!.addThermalStatusListener(callbackExecutor!!, thermalListener)
    }.isSuccess
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
                stop(reason, snapshot)
            }
        }
    }
    try { generation.await() } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        pressure.get()?.let { throw LocalResourceLimit(it.first, it.second) }
        throw cancelled
    } finally {
        monitor.cancel()
        if (listening) runCatching { power!!.removeThermalStatusListener(thermalListener!!) }
    }
}
