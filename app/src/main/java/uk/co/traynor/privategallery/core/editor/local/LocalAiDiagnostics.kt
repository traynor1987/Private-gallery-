package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.BuildConfig

/** Fixed reason codes only: never retain runtime messages, driver strings or model paths. */
enum class BackendFallbackReason {
    NONE, NPU_NOT_IMPLEMENTED, VULKAN_UNAVAILABLE, MODEL_INCOMPATIBLE,
    INITIALIZATION_FAILED, MODEL_LOAD_FAILED, GENERATION_FAILED, FORCED_BACKEND_UNAVAILABLE,
}

/** Null means unavailable, not a zero-duration measurement or an assumed backend. */
data class BackendRunMetrics(
    val backend: LocalBackend? = null,
    val probeMs: Long? = null,
    val initMs: Long? = null,
    val loadMs: Long? = null,
    val generationMs: Long? = null,
    val thermalStart: Int? = null,
    val thermalEnd: Int? = null,
    val thermalMax: Int? = null,
    val minAvailableRamBytes: Long? = null,
    val fallbackReason: BackendFallbackReason? = null,
    val attempts: List<BackendAttempt> = emptyList(),
    val stopReason: LocalStopReason? = null,
    val modelLoadStarted: Boolean = false,
    val modelLoadSucceeded: Boolean = false,
    val inferenceStarted: Boolean = false,
    val completedSteps: Int? = null,
    val totalSteps: Int? = null,
    val availableBeforeLoad: Long? = null,
    val availableAtGenerationStart: Long? = null,
    val availableAtAbort: Long? = null,
    val totalRam: Long? = null,
    val lowMemoryAtAbort: Boolean? = null,
    val androidThreshold: Long? = null,
    val heapUsedBytes: Long? = null,
    val heapFreeBytes: Long? = null,
    val heapMaxBytes: Long? = null,
    val resourcesUnloaded: Boolean? = null,
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
)

/** Acceptance-only, memory-only fixed fields. No free-form errors, paths, prompts or image data. */
object LocalAiDiagnostics {
    private val state = MutableStateFlow("No memory snapshot or local generation recorded this session.")
    val summary = state.asStateFlow()
    private var memory = "No memory snapshot yet."
    private val runs = ArrayDeque<String>()
    private val enabled get() = BuildConfig.DEBUG || BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS

    @Synchronized fun resources(device: DeviceResources, installed: Map<ModelSpec, Boolean>) {
        if (!enabled) return
        memory = LocalMemoryReport.format(device, installed)
        publish()
    }
    @Synchronized fun preflight(model: ModelSpec, device: DeviceResources, reason: LocalStopReason) {
        record(model, 0, 0, 0, "FAILED", if (device.tooHot) 3 else 0, null,
            BackendRunMetrics(stopReason = reason, totalRam = device.totalRam, availableAtAbort = device.availableRam,
                lowMemoryAtAbort = device.lowMemory, androidThreshold = device.lowMemoryThreshold,
                resourcesUnloaded = true))
    }

    @Synchronized fun record(
        model: ModelSpec, width: Int, height: Int, durationMs: Long, result: String,
        thermalStatus: Int, peakRssBytes: Long?, metrics: BackendRunMetrics = BackendRunMetrics(),
    ) {
        if (!enabled) return
        require(result in setOf("SUCCESS", "CANCELLED", "FAILED"))
        // ModelSpec is constructible: only exact reviewed catalog entries may supply text.
        val reviewed = ModelCatalog.all.firstOrNull { it == model }
        val modelLabel = reviewed?.let { "${it.providerId} · ${it.id}" } ?: "UNRECOGNIZED"
        runs.addFirst(buildString {
            appendLine(modelLabel)
            appendLine("stable-diffusion.cpp 19bbbca1 · actual backend=${metrics.backend ?: "unavailable"}")
            appendLine("Input: ${metrics.inputWidth ?: "unavailable"} × ${metrics.inputHeight ?: "unavailable"} · Duration: $durationMs ms · $result")
            appendLine("Model format=SafeTensors FP16; model file bytes=${reviewed?.bytes ?: "unavailable"}; model resident bytes=unavailable (not measured separately)")
            appendLine("Inference dimensions=$width × $height; output dimensions=${if (result == "SUCCESS") "$width × $height" else "unavailable"}; completed steps=${metrics.completedSteps ?: "unavailable"}/${metrics.totalSteps ?: "unavailable"}")
            appendLine("Model load started=${metrics.modelLoadStarted}; succeeded=${metrics.modelLoadSucceeded}; inference started=${metrics.inferenceStarted}")
            appendLine("Stage ms: probe=${metrics.probeMs ?: "unavailable"}; init=${metrics.initMs ?: "unavailable"}; load=${metrics.loadMs ?: "unavailable"}; generation=${metrics.generationMs ?: "unavailable"}")
            appendLine("Worker peak RSS bytes (last report): ${peakRssBytes ?: "unavailable"}; minimum available RAM bytes=${metrics.minAvailableRamBytes ?: "unavailable"}")
            appendLine("Total RAM bytes=${metrics.totalRam ?: "unavailable"}; before load available=${metrics.availableBeforeLoad ?: "unavailable"}; generation start available=${metrics.availableAtGenerationStart ?: "unavailable"}; abort available=${metrics.availableAtAbort ?: "unavailable"}; lowMemory at abort=${metrics.lowMemoryAtAbort ?: "unavailable"}; Android threshold=${metrics.androidThreshold ?: "unavailable"}")
            appendLine("Java heap used/free/max bytes=${metrics.heapUsedBytes ?: "unavailable"}/${metrics.heapFreeBytes ?: "unavailable"}/${metrics.heapMaxBytes ?: "unavailable"}")
            appendLine("Android thermal status: start=${metrics.thermalStart ?: "unavailable"}; end=${metrics.thermalEnd ?: thermalStatus}; max=${metrics.thermalMax ?: "unavailable"} (0 = none, 3 = severe)")
            appendLine("Backend attempts=${metrics.attempts.joinToString(" → ") { "${it.backend}:${it.failure ?: "SUCCESS"}" }.ifEmpty { "unavailable" }}; fallback reason=${metrics.fallbackReason ?: "unavailable"}")
            append("Termination=${metrics.stopReason ?: if (result == "SUCCESS") "SUCCESS" else "UNCLASSIFIED"}; worker resources unloaded=${metrics.resourcesUnloaded ?: "unconfirmed"}")
        })
        while (runs.size > 6) runs.removeLast()
        publish()
    }

    private fun publish() {
        val generation = if (runs.isEmpty()) "No local generation recorded this session." else
            "Recent local runs (newest first, at most 6)\n${runs.joinToString("\n\n")}\nPeak RSS excludes some GPU/driver allocations; minimum available RAM is sampled. A killed worker may not report its final peak. No image or prompt is recorded."
        state.value = "$memory\n\nNPU: runtime/model editing integration unavailable in this build.\n$generation"
    }
}
data class BackendAttempt(val backend: LocalBackend, val failure: LocalStopReason? = null)
