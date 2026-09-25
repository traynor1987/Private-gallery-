package uk.co.traynor.privategallery.core.editor.local

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

internal object LocalNative {
    val available: Boolean by lazy { try { System.loadLibrary("private_gallery_ai"); true } catch (_: LinkageError) { false } }
    external fun peakRssBytes(): Long
    external fun promptFits(prompt: String): Boolean
    external fun canReadModel(modelFd: Int): Boolean
    external fun restrictNetworking(): Boolean
    external fun probeVulkan(): Boolean
    external fun generate(modelFd: Int, pixels: ByteBuffer, width: Int, height: Int,
        prompt: String, masked: Boolean, threads: Int, useGpu: Boolean, callback: NativeProgress): Boolean
}
internal class NativeProgress(private val report: (Int, Int) -> Unit, private val stage: (Int, Long) -> Unit) {
    @androidx.annotation.Keep fun onStep(step: Int, steps: Int) { if (steps > 0 && step in 0..steps) report(step, steps) }
    @androidx.annotation.Keep fun onStage(code: Int, millis: Long) { if (code in 1..3 && millis >= 0) stage(code, millis) }
}

/** Isolated UID has no app permissions: no Internet, Vault, credential store or app-private path access. */
@RequiresApi(29)
open class LocalInferenceService : Service() {
    protected open val gpuWorker = false
    private var networkRestricted = false
    @Volatile private var gpuReady = false
    private val started = AtomicBoolean(false)
    override fun onCreate() {
        super.onCreate()
        if (gpuWorker) networkRestricted = LocalNative.available && LocalNative.restrictNetworking()
    }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.sendingUid != applicationInfo.uid) return@Handler true
        when (message.what) {
            CANCEL -> { android.os.Process.killProcess(android.os.Process.myPid()); true }
            PROBE -> {
                if (!gpuWorker || started.get()) return@Handler true
                val reply = message.replyTo
                Thread({
                    val begin = SystemClock.elapsedRealtime()
                    gpuReady = networkRestricted && runCatching { LocalNative.probeVulkan() }.getOrDefault(false)
                    runCatching { reply?.send(Message.obtain(null, PROBED, if (gpuReady) 1 else 0, if (networkRestricted) 1 else 0).apply {
                        data = Bundle().apply { putLong("probeMs", SystemClock.elapsedRealtime() - begin); putInt("workerUid", android.os.Process.myUid()) }
                    }) }
                }, "local-ai-probe").start()
                true
            }
            GENERATE -> {
                if (!started.compareAndSet(false, true)) return@Handler true
                val data = message.data.apply { classLoader = SharedMemory::class.java.classLoader }
                @Suppress("DEPRECATION") val memory = data.getParcelable<SharedMemory>("pixels")
                @Suppress("DEPRECATION") val model = data.getParcelable<ParcelFileDescriptor>("model")
                val reply = message.replyTo
                val width = data.getInt("width"); val height = data.getInt("height")
                val prompt = data.getString("prompt") ?: ""
                val masked = data.getBoolean("masked")
                Thread({
                    var mapped: ByteBuffer? = null
                    var ok = false
                    var failureCode = 0
                    try {
                        require(memory != null && model != null && reply != null)
                        require(width in 64..768 && height in 64..768 && width % 64 == 0 && height % 64 == 0)
                        require(memory.size == width * height * 7 && prompt.length <= 4000)
                        require(!gpuWorker || networkRestricted && gpuReady)
                        mapped = memory.mapReadWrite()
                        if (LocalNative.available && !LocalNative.promptFits(prompt)) {
                            failureCode = PROMPT_TOO_LONG
                        } else if (LocalNative.available && LocalNative.canReadModel(model.fd)) {
                            reply.send(Message.obtain(null, MODEL_OPENED, android.os.Process.myUid(), checkSelfPermission(android.Manifest.permission.INTERNET)))
                            ok = LocalNative.generate(model.fd, mapped!!, width, height, prompt, masked,
                            Runtime.getRuntime().availableProcessors().coerceIn(1, 4), gpuWorker, NativeProgress({ step, steps ->
                                runCatching { reply.send(withPeak(Message.obtain(null, PROGRESS, step, steps))) }
                            }, { code, millis ->
                                runCatching { reply.send(withPeak(Message.obtain(null, STAGE, code, 0)).apply { data.putLong("durationMs", millis) }) }
                            }))
                        }
                    } catch (_: Throwable) { ok = false }
                    finally {
                        mapped?.let { SharedMemory.unmap(it) }
                        memory?.close(); model?.close()
                        runCatching { reply?.send(withPeak(Message.obtain(null, if (ok) COMPLETE else FAILED, failureCode, 0))) }
                    }
                    // Client owns shared result memory and unbinds after reading. No retained warm session.
                }, "local-ai").start()
                true
            }
            else -> false
        }
    })
    private fun withPeak(message: Message): Message = message.apply {
        if (LocalNative.available) data = Bundle().apply { putLong("peakRssBytes", LocalNative.peakRssBytes()) }
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() { super.onDestroy(); android.os.Process.killProcess(android.os.Process.myPid()) }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) android.os.Process.killProcess(android.os.Process.myPid())
    }
    companion object { const val GENERATE = 1; const val CANCEL = 2; const val PROGRESS = 3; const val COMPLETE = 4; const val FAILED = 5; const val MODEL_OPENED = 6; const val PROBE = 7; const val PROBED = 8; const val STAGE = 9; const val PROMPT_TOO_LONG = 1 }
}

/** GPU driver access needs the app UID. This worker receives no keys or source paths.
 * Network syscall restriction is mandatory before probing or receiving image buffers;
 * it does not remove the UID's filesystem/Binder privileges. CPU keeps isolated UID. */
@RequiresApi(29)
class LocalGpuInferenceService : LocalInferenceService() {
    override val gpuWorker = true
}
