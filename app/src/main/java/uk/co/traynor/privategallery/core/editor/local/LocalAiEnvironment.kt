package uk.co.traynor.privategallery.core.editor.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.core.editor.*
import java.io.File

class LocalAiEnvironment(context: Context) {
    private val context = context.applicationContext
    val store = ModelStore(File(this.context.noBackupFilesDir, "ai-models"))
    private val preferences = this.context.getSharedPreferences("ai_local_models", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableDownloads = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloads = mutableDownloads.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    val providers: List<AiImageEditProvider> = ModelCatalog.all.map { LocalImageEditProvider(it, this) }
    fun accepted(model: ModelSpec) = preferences.getString(model.id, null) == model.sha256
    fun accept(model: ModelSpec) { preferences.edit().putString(model.id, model.sha256).apply() }
    fun device(): DeviceResources {
        val memory = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getMemoryInfo(memory)
        val supported = Build.SUPPORTED_ABIS.any { it in setOf("arm64-v8a", "x86_64") }
        return DeviceResources(Build.VERSION.SDK_INT, supported, supported && LocalNative.available,
            memory.totalMem, memory.availMem, memory.lowMemory,
            Build.VERSION.SDK_INT >= 29 && (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE,
            memory.threshold, manager.memoryClass, manager.largeMemoryClass, Runtime.getRuntime().maxMemory())
    }
    fun refreshDiagnostics() {
        val resources = device()
        LocalAiDiagnostics.resources(resources, ModelCatalog.all.associateWith { installed(it) })
    }
    fun availability(model: ModelSpec) = LocalCapabilityPolicy.evaluate(model, device(), store.isInstalled(model) && accepted(model))
    fun installed(model: ModelSpec) = store.isInstalled(model) && accepted(model)
    fun availableSpace() = File(context.noBackupFilesDir, "ai-models").usableSpace
    fun canDownload(model: ModelSpec) = availability(model) !in setOf(
        LocalAvailability.UNSUPPORTED_ANDROID, LocalAvailability.UNSUPPORTED_CHIPSET,
        LocalAvailability.RUNTIME_NOT_AVAILABLE, LocalAvailability.INSUFFICIENT_RAM)
    @Synchronized fun download(model: ModelSpec) {
        if (jobs.values.any { it.isActive }) return
        if (!accepted(model) || !canDownload(model)) return
        jobs[model.id] = scope.launch {
            fun publish(state: ModelDownloadState) { mutableDownloads.value = mutableDownloads.value + (model.id to state) }
            try {
                store.download(model) { bytes -> publish(ModelDownloadState(true, bytes, if (bytes == model.bytes) "Verifying integrity…" else "Downloading…")) }
                publish(ModelDownloadState(false, model.bytes, "Installed"))
            } catch (cancelled: CancellationException) {
                publish(ModelDownloadState(false, 0, "Cancelled · Retry resumes the partial download")); throw cancelled
            } catch (_: Exception) { publish(ModelDownloadState(false, 0, "Download failed · Retry resumes safely")) }
        }
    }
    @Synchronized fun cancel(model: ModelSpec) { jobs[model.id]?.cancel() }
    suspend fun remove(model: ModelSpec) {
        val job = synchronized(this) { jobs[model.id] }
        job?.cancelAndJoin()
        store.remove(model)
        mutableDownloads.value = mutableDownloads.value + (model.id to ModelDownloadState(false, 0, "Not installed"))
    }
    suspend fun generate(model: ModelSpec, request: AiEditRequest, progress: (String) -> Unit): ByteArray {
        if (Build.VERSION.SDK_INT < 29) throw AiEditFailure(LocalAvailability.UNSUPPORTED_ANDROID.label)
        fun requireAdmission() {
            val resources = device()
            LocalAiDiagnostics.resources(resources, ModelCatalog.all.associateWith { installed(it) })
            if (!LocalCapabilityPolicy.canStart(model, resources, installed(model), request.ownerMemoryAttempt))
                throw AiEditFailure(LocalCapabilityPolicy.evaluate(model, resources, installed(model)).label)
        }
        requireAdmission()
        progress("Verifying model…")
        if (!store.verify(model)) {
            mutableDownloads.value = mutableDownloads.value + (model.id to ModelDownloadState(false, 0, "Integrity check failed · Remove and download again"))
            throw AiEditFailure("Model integrity check failed. Remove it and download again.")
        }
        requireAdmission() // Hashing a multi-GB model can outlast the original snapshot.
        try {
            return withLocalResourceGuard(::device, { resources ->
                LocalAiDiagnostics.resources(resources, ModelCatalog.all.associateWith { installed(it) })
            }) { LocalInferenceClient(context).generate(store.file(model), model, request, progress, ::requireAdmission) }
        } catch (_: LocalResourceLimit) {
            throw AiEditFailure("Local generation stopped to protect device memory or temperature. Close other apps or let the device cool, then retry.")
        }
    }
}
data class ModelDownloadState(val busy: Boolean = false, val bytes: Long = 0, val message: String = "Not installed")

private class LocalImageEditProvider(private val model: ModelSpec, private val environment: LocalAiEnvironment) : AiImageEditProvider {
    override val id = model.providerId
    override val displayName = if (model == ModelCatalog.lightweight) "Local · Lightweight" else "Local · Advanced"
    override val processing = AiProcessing.ON_DEVICE
    override val modelId = model.id
    override val timeoutMillis = 30 * 60_000L
    override val capabilities = setOf(AiCapability.GENERATIVE_EDIT, AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL, AiCapability.RESTYLE)
    private val mutableProgress = MutableStateFlow<String?>(null)
    override val progress = mutableProgress.asStateFlow()
    override val availabilityLabel get() = environment.availability(model).label
    override val ownerAttemptWarning get() = if (environment.availability(model) == LocalAvailability.LOW_MEMORY)
        "Available memory is below the recommended level. This one-time local attempt may stop if memory runs low. Close other apps first. Your original stays unchanged and there is no cloud fallback." else null
    override val configured get() = environment.installed(model)
    override val ready get() = environment.availability(model) == LocalAvailability.SUPPORTED_SLOWER
    override suspend fun edit(request: AiEditRequest): ByteArray = try {
        environment.generate(model, request) { mutableProgress.value = it }
    } finally { mutableProgress.value = null }
}
