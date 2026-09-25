package uk.co.traynor.privategallery.core.editor.local

import android.content.*
import android.app.ActivityManager
import android.graphics.*
import android.os.*
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.traynor.privategallery.core.editor.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

@RequiresApi(29)
class LocalInferenceClient(private val context: Context) {
    suspend fun generate(model: File, spec: ModelSpec, request: AiEditRequest, progress: (LocalGenerationProgress) -> Unit, admit: () -> Unit): ByteArray = gate.withLock {
        lastWorkerStopped?.let { previous ->
            if (withTimeoutOrNull(5000) { previous.await(); true } != true)
                throw AiEditFailure("The previous local worker is still stopping. Retry after it has stopped.")
        }
        admit()
        withContext(Dispatchers.Default) {
            val preview = PhotoRenderer.render(request.image, PhotoEdit(), true, spec.maxDimension.toLong() * spec.maxDimension)
            val scale = spec.maxDimension.toFloat() / max(preview.width, preview.height)
            val width = ((preview.width * scale / 64).roundToInt() * 64).coerceIn(64, spec.maxDimension)
            val height = ((preview.height * scale / 64).roundToInt() * 64).coerceIn(64, spec.maxDimension)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            var outcome = "FAILED"
            var peakRssBytes: Long? = null
            var metrics = BackendRunMetrics(inputWidth = preview.width, inputHeight = preview.height)
            fun update(change: (BackendRunMetrics) -> BackendRunMetrics) { synchronized(this@LocalInferenceClient) { metrics = change(metrics) } }
            fun snapshot(): ActivityManager.MemoryInfo = ActivityManager.MemoryInfo().also {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
            }
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val thermalStart = power.currentThermalStatus
            val thermalMax = java.util.concurrent.atomic.AtomicInteger(thermalStart)
            val minimumAvailable = java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE)
            val memory = try { SharedMemory.create("private-ai", width * height * 7) }
                catch (failure: Throwable) { preview.recycle(); throw failure }
            val map = try { memory.mapReadWrite() }
                catch (failure: Throwable) { memory.close(); preview.recycle(); throw failure }
            val monitor = launch {
                while (isActive) {
                    val snapshot = ActivityManager.MemoryInfo()
                    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(snapshot)
                    minimumAvailable.getAndUpdate { minOf(it, snapshot.availMem) }
                    thermalMax.getAndUpdate { maxOf(it, power.currentThermalStatus) }
                    delay(500)
                }
            }
            var image: Bitmap? = null
            var mask: Bitmap? = null
            var result: Bitmap? = null
            try {
                image = Bitmap.createScaledBitmap(preview, width, height, true)
                val row = IntArray(width)
                try {
                    for (y in 0 until height) {
                        image.getPixels(row, 0, width, 0, y, width, 1)
                        for (pixel in row) { map.put(Color.red(pixel).toByte()); map.put(Color.green(pixel).toByte()); map.put(Color.blue(pixel).toByte()) }
                    }
                    mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(mask)
                    canvas.drawColor(Color.BLACK)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                    // Shared editor normalized coordinates refer to this already transformed image.
                    request.parameters.strokes.forEach { stroke ->
                        paint.strokeWidth = stroke.width * width
                        val path = Path()
                        stroke.points.forEachIndexed { index, p -> if (index == 0) path.moveTo(p.x * width, p.y * height) else path.lineTo(p.x * width, p.y * height) }
                        if (stroke.points.size == 1) { val p = stroke.points.single(); canvas.drawPoint(p.x * width, p.y * height, paint) } else canvas.drawPath(path, paint)
                    }
                    for (y in 0 until height) {
                        mask.getPixels(row, 0, width, 0, y, width, 1)
                        for (pixel in row) map.put(if (Color.red(pixel) >= 128) 255.toByte() else 0.toByte())
                    }
                    mask.recycle(); mask = null
                    if (image !== preview) image.recycle(); image = null; preview.recycle()
                    progress(LocalGenerationProgress(LocalStage.LOADING))
                    ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        suspend fun attempt(gpu: Boolean) {
                            admit()
                            val backend = if (gpu) LocalBackend.VULKAN else LocalBackend.CPU
                            update { it.copy(backend = backend, attempts = it.attempts + BackendAttempt(backend)) }
                            try { withLocalWorkerLifetime(Dispatchers.Main.immediate, { lastWorkerStopped = it }) { stopped ->
                                runWorker(memory, descriptor, width, height, request, progress, stopped, gpu, admit,
                                    { peakRssBytes = maxOf(peakRssBytes ?: 0, it) },
                                    { code, duration -> update { existing -> when (code) {
                                        0 -> existing.copy(probeMs = duration)
                                        1, 3 -> existing.copy(loadMs = duration, modelLoadSucceeded = code == 1)
                                        2 -> existing.copy(generationMs = duration)
                                        4 -> existing.copy(modelLoadStarted = true, availableBeforeLoad = snapshot().availMem)
                                        5 -> existing.copy(inferenceStarted = true, availableAtGenerationStart = snapshot().availMem)
                                        else -> existing
                                    } } },
                                    { step, total -> update { it.copy(completedSteps = step, totalSteps = total) } })
                            } } catch (_: LocalWorkerStopTimeout) {
                                throw LocalGenerationFailure(LocalStopReason.WORKER_DIED)
                            } catch (failure: LocalGenerationFailure) {
                                update { it.copy(attempts = it.attempts.dropLast(1) + BackendAttempt(backend, failure.reason)) }
                                throw failure
                            }
                        }
                        val override = if (LocalBackendSettings.enabled) LocalBackendSettings.override.value else LocalBackendOverride.AUTO
                        if (override == LocalBackendOverride.CPU) attempt(false) else {
                            try { attempt(true) } catch (failure: LocalGenerationFailure) {
                                if (failure.reason !in setOf(LocalStopReason.GPU_ALLOCATION, LocalStopReason.GPU_EXECUTION,
                                        LocalStopReason.MODEL_LOAD, LocalStopReason.WORKER_DIED) ||
                                    lastWorkerStopped?.isCompleted != true) throw failure
                                if (LocalBackendPolicy.select(setOf(LocalBackend.CPU), override, LocalBackendSettings.enabled) == null) {
                                    update { it.copy(fallbackReason = BackendFallbackReason.FORCED_BACKEND_UNAVAILABLE) }
                                    throw failure
                                }
                                update { it.copy(fallbackReason = when (failure.reason) {
                                    LocalStopReason.MODEL_LOAD -> BackendFallbackReason.MODEL_LOAD_FAILED
                                    LocalStopReason.GPU_EXECUTION, LocalStopReason.GPU_ALLOCATION -> BackendFallbackReason.GENERATION_FAILED
                                    else -> BackendFallbackReason.VULKAN_UNAVAILABLE
                                }) }
                                progress(LocalGenerationProgress(LocalStage.LOADING))
                                attempt(false)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    progress(LocalGenerationProgress(LocalStage.FINALISING))
                    map.position(width * height * 4)
                    result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    for (y in 0 until height) {
                        for (x in 0 until width) row[x] = Color.rgb(map.get().toInt() and 255, map.get().toInt() and 255, map.get().toInt() and 255)
                        result.setPixels(row, 0, width, 0, y, width, 1)
                    }
                    PhotoRenderer.encode(result).also { outcome = "SUCCESS" }
                } finally { row.fill(0) }
            } catch (cancelled: CancellationException) {
                outcome = "CANCELLED"
                val reason = LocalStopReason.entries.firstOrNull { it.name == cancelled.message } ?: LocalStopReason.USER_CANCELLED
                update { it.copy(stopReason = reason) }
                throw cancelled
            } catch (failure: LocalGenerationFailure) {
                update { it.copy(stopReason = failure.reason) }
                throw failure
            } catch (failure: OutOfMemoryError) {
                update { it.copy(stopReason = LocalStopReason.JAVA_HEAP) }
                throw failure
            } finally {
                monitor.cancel()
                val thermalEnd = power.currentThermalStatus
                val atEnd = snapshot()
                val heap = Runtime.getRuntime()
                LocalAiDiagnostics.record(spec, width, height, android.os.SystemClock.elapsedRealtime() - startedAt, outcome, thermalEnd, peakRssBytes,
                    metrics.copy(thermalStart = thermalStart, thermalEnd = thermalEnd, thermalMax = maxOf(thermalMax.get(), thermalEnd),
                        minAvailableRamBytes = minimumAvailable.get().takeUnless { it == Long.MAX_VALUE },
                        availableAtAbort = if (outcome == "SUCCESS") null else atEnd.availMem, totalRam = atEnd.totalMem,
                        androidThreshold = atEnd.threshold, lowMemoryAtAbort = if (outcome == "SUCCESS") null else atEnd.lowMemory,
                        heapUsedBytes = heap.totalMemory() - heap.freeMemory(), heapFreeBytes = heap.freeMemory(), heapMaxBytes = heap.maxMemory(),
                        resourcesUnloaded = lastWorkerStopped?.isCompleted == true))
                image?.takeUnless { it.isRecycled }?.recycle(); mask?.recycle(); result?.recycle()
                if (!preview.isRecycled) preview.recycle()
                map.clear(); while (map.hasRemaining()) map.put(0.toByte())
                SharedMemory.unmap(map); memory.close()
            }
        }
    }
    private suspend fun runWorker(memory: SharedMemory, model: ParcelFileDescriptor, width: Int, height: Int,
        request: AiEditRequest, progress: (LocalGenerationProgress) -> Unit, stopped: CompletableDeferred<Unit>, gpu: Boolean, admit: () -> Unit,
        peak: (Long) -> Unit, stage: (Int, Long) -> Unit, step: (Int, Int) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        var remote: Messenger? = null
        var bound = false
        var finished = false
        var submitted = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var connection: ServiceConnection
        fun close() {
            if (finished) return
            finished = true
            if (remote == null) stopped.complete(Unit)
            runCatching { remote?.send(Message.obtain(null, LocalInferenceService.CANCEL)) }
            if (bound) { runCatching { context.unbindService(connection) }; bound = false }
        }
        fun fail(reason: LocalStopReason) { close(); if (continuation.isActive) continuation.resumeWithException(LocalGenerationFailure(reason)) }
        fun unexplainedDeath(): LocalStopReason {
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            val thermal = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            return when {
                memory.lowMemory -> LocalStopReason.ANDROID_LOW_MEMORY
                thermal >= PowerManager.THERMAL_STATUS_SEVERE -> LocalStopReason.THERMAL
                else -> LocalStopReason.WORKER_DIED
            }
        }
        fun unavailable() = fail(LocalStopReason.GPU_EXECUTION)
        lateinit var reply: Messenger
        fun submit() {
            if (finished || submitted || !continuation.isActive) return
            try {
                admit() // Re-check after driver initialization and before handing over plaintext.
                submitted = true
                progress(LocalGenerationProgress(LocalStage.LOADING))
                remote!!.send(Message.obtain(null, LocalInferenceService.GENERATE).apply {
                    replyTo = reply
                    data = Bundle().apply {
                        putParcelable("pixels", memory); putParcelable("model", model)
                        putInt("width", width); putInt("height", height)
                        putString("prompt", request.parameters.prompt)
                        putBoolean("masked", request.parameters.strokes.isNotEmpty())
                    }
                })
            } catch (failure: Exception) {
                close(); if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
        reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (!finished && message.what in setOf(LocalInferenceService.PROGRESS, LocalInferenceService.COMPLETE, LocalInferenceService.FAILED, LocalInferenceService.STAGE)) {
                message.data.getLong("peakRssBytes").takeIf { it > 0 }?.let(peak)
            }
            if (!finished) when (message.what) {
                LocalInferenceService.PROBED -> {
                    stage(0, message.data.getLong("probeMs"))
                    if (message.arg1 == 1 && message.arg2 == 1) submit() else unavailable()
                }
                LocalInferenceService.STAGE -> {
                    stage(message.arg1, message.data.getLong("durationMs"))
                    if (message.arg1 == 4) progress(LocalGenerationProgress(LocalStage.LOADING))
                    if (message.arg1 == 5) progress(LocalGenerationProgress(LocalStage.GENERATING))
                }
                LocalInferenceService.PROGRESS -> if (message.arg2 > 0 && message.arg1 in 1..message.arg2) {
                    step(message.arg1, message.arg2)
                    progress(LocalGenerationProgress(LocalStage.GENERATING, message.arg1, message.arg2))
                }
                LocalInferenceService.COMPLETE -> { close(); if (continuation.isActive) continuation.resume(Unit) }
                LocalInferenceService.FAILED -> fail(when (message.arg1) {
                    LocalInferenceService.PROMPT_TOO_LONG -> LocalStopReason.PROMPT_TOO_LONG
                    LocalInferenceService.MODEL_LOAD_FAILED -> LocalStopReason.MODEL_LOAD
                    LocalInferenceService.NATIVE_ALLOCATION_FAILED -> if (gpu) LocalStopReason.GPU_ALLOCATION else LocalStopReason.CPU_ALLOCATION
                    LocalInferenceService.JAVA_HEAP_FAILED -> LocalStopReason.JAVA_HEAP
                    LocalInferenceService.ANDROID_LOW_MEMORY -> LocalStopReason.ANDROID_LOW_MEMORY
                    else -> if (gpu) LocalStopReason.GPU_EXECUTION else LocalStopReason.CPU_EXECUTION
                })
            }
            true
        })
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (finished || !continuation.isActive) { close(); return }
                remote = Messenger(binder)
                try {
                    observeLocalWorkerDeath(binder, stopped) { handler.post { if (!finished) { if (gpu && !submitted) unavailable() else fail(unexplainedDeath()) } } }
                    if (gpu) {
                        progress(LocalGenerationProgress(LocalStage.PROBING))
                        remote!!.send(Message.obtain(null, LocalInferenceService.PROBE).apply { replyTo = reply })
                        handler.postDelayed({ if (!finished && !submitted) unavailable() }, 45_000)
                    } else submit()
                } catch (_: Exception) { if (gpu && !submitted) unavailable() else fail(unexplainedDeath()) }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (!finished) { if (gpu && !submitted) unavailable() else fail(unexplainedDeath()) } }
            override fun onNullBinding(name: ComponentName) { if (gpu) unavailable() else fail(LocalStopReason.WORKER_DIED) }
            override fun onBindingDied(name: ComponentName) { if (gpu && !submitted) unavailable() else fail(LocalStopReason.WORKER_DIED) }
        }
        continuation.invokeOnCancellation { handler.post { close() } }
        try {
            bound = if (gpu) context.bindService(Intent(context, LocalGpuInferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
                else context.bindIsolatedService(Intent(context, LocalInferenceService::class.java), Context.BIND_AUTO_CREATE, "generation" + java.util.UUID.randomUUID().toString().replace("-", ""), context.mainExecutor, connection)
            if (!bound) { if (gpu) unavailable() else fail(LocalStopReason.WORKER_DIED) }
        } catch (_: Exception) { if (gpu) unavailable() else fail(LocalStopReason.WORKER_DIED) }
    }
    companion object {
        private val gate = Mutex()
        private var lastWorkerStopped: CompletableDeferred<Unit>? = null
    }
}

/** A binder may already be dead before a recipient can be registered. */
internal fun observeLocalWorkerDeath(binder: IBinder, stopped: CompletableDeferred<Unit>, onDeath: () -> Unit) {
    try { binder.linkToDeath({ stopped.complete(Unit); onDeath() }, 0) }
    catch (failure: RemoteException) {
        if (!binder.isBinderAlive) stopped.complete(Unit)
        throw failure
    }
}
