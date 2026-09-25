package uk.co.traynor.privategallery.core.editor.local

import android.content.*
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
    suspend fun generate(model: File, spec: ModelSpec, request: AiEditRequest, progress: (String) -> Unit): ByteArray = gate.withLock {
        withContext(Dispatchers.Default) {
            val preview = PhotoRenderer.render(request.image, PhotoEdit(), true)
            val scale = spec.maxDimension.toFloat() / max(preview.width, preview.height)
            val width = ((preview.width * scale / 64).roundToInt() * 64).coerceIn(64, spec.maxDimension)
            val height = ((preview.height * scale / 64).roundToInt() * 64).coerceIn(64, spec.maxDimension)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            var outcome = "FAILED"
            val memory = SharedMemory.create("private-ai", width * height * 7)
            val map = memory.mapReadWrite()
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
                    progress("Preparing model…")
                    ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        val stopped = CompletableDeferred<Unit>()
                        try { withContext(Dispatchers.Main.immediate) {
                            runWorker(memory, descriptor, width, height, request, progress, stopped)
                        } } finally {
                            withContext(NonCancellable) { withTimeoutOrNull(5000) { stopped.await() } }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    map.position(width * height * 4)
                    result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    for (y in 0 until height) {
                        for (x in 0 until width) row[x] = Color.rgb(map.get().toInt() and 255, map.get().toInt() and 255, map.get().toInt() and 255)
                        result.setPixels(row, 0, width, 0, y, width, 1)
                    }
                    PhotoRenderer.encode(result).also { outcome = "SUCCESS" }
                } finally { row.fill(0) }
            } catch (cancelled: CancellationException) { outcome = "CANCELLED"; throw cancelled
            } finally {
                LocalAiDiagnostics.record(spec, width, height, android.os.SystemClock.elapsedRealtime() - startedAt, outcome)
                image?.takeUnless { it.isRecycled }?.recycle(); mask?.recycle(); result?.recycle()
                if (!preview.isRecycled) preview.recycle()
                map.clear(); while (map.hasRemaining()) map.put(0.toByte())
                SharedMemory.unmap(map); memory.close()
            }
        }
    }
    private suspend fun runWorker(memory: SharedMemory, model: ParcelFileDescriptor, width: Int, height: Int,
        request: AiEditRequest, progress: (String) -> Unit, stopped: CompletableDeferred<Unit>) = suspendCancellableCoroutine<Unit> { continuation ->
        var remote: Messenger? = null
        var bound = false
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var connection: ServiceConnection
        fun close() {
            if (finished) return
            finished = true
            if (remote == null) stopped.complete(Unit)
            runCatching { remote?.send(Message.obtain(null, LocalInferenceService.CANCEL)) }
            if (bound) { runCatching { context.unbindService(connection) }; bound = false }
        }
        fun fail() { close(); if (continuation.isActive) continuation.resumeWithException(AiEditFailure("Local generation stopped. Free memory, let the device cool, then retry.")) }
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (!finished) when (message.what) {
                LocalInferenceService.PROGRESS -> if (message.arg2 > 0) progress("Generating… step ${message.arg1} of ${message.arg2}")
                LocalInferenceService.COMPLETE -> { close(); if (continuation.isActive) continuation.resume(Unit) }
                LocalInferenceService.FAILED -> fail()
            }
            true
        })
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (finished || !continuation.isActive) { close(); return }
                remote = Messenger(binder)
                try {
                    binder.linkToDeath({ stopped.complete(Unit); handler.post { if (!finished) fail() } }, 0)
                    remote!!.send(Message.obtain(null, LocalInferenceService.GENERATE).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putParcelable("pixels", memory); putParcelable("model", model)
                            putInt("width", width); putInt("height", height)
                            putString("prompt", request.parameters.prompt)
                            putBoolean("masked", request.parameters.strokes.isNotEmpty())
                        }
                    })
                } catch (_: Exception) { fail() }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (!finished) fail() }
            override fun onNullBinding(name: ComponentName) { fail() }
            override fun onBindingDied(name: ComponentName) { fail() }
        }
        continuation.invokeOnCancellation { handler.post { close() } }
        try {
            bound = context.bindIsolatedService(Intent(context, LocalInferenceService::class.java), Context.BIND_AUTO_CREATE, "generation-" + java.util.UUID.randomUUID().toString(), context.mainExecutor, connection)
            if (!bound) fail()
        } catch (_: Exception) { fail() }
    }
    companion object { private val gate = Mutex() }
}
