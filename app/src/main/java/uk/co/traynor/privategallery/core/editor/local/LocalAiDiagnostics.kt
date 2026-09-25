package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.BuildConfig

/** Acceptance-only, memory-only fixed fields. No free-form errors, paths, prompts or image data. */
object LocalAiDiagnostics {
    private val state = MutableStateFlow("No memory snapshot or local generation recorded this session.")
    val summary = state.asStateFlow()
    private var memory = "No memory snapshot yet."
    private var generation = "No local generation recorded this session."
    private val enabled get() = BuildConfig.DEBUG || BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS
    @Synchronized fun resources(device: DeviceResources, installed: Map<ModelSpec, Boolean>) {
        if (!enabled) return
        memory = LocalMemoryReport.format(device, installed)
        state.value = "$memory\n\n$generation"
    }
    @Synchronized fun record(model: ModelSpec, width: Int, height: Int, durationMs: Long, result: String, thermalStatus: Int, peakRssBytes: Long?) {
        if (!enabled) return
        require(result in setOf("SUCCESS", "CANCELLED", "FAILED"))
        generation = "${model.providerId} · ${model.id}\nstable-diffusion.cpp 19bbbca1 · CPU\nInput: ${width} × ${height}\nDuration: ${durationMs} ms · $result\nWorker peak RSS bytes (last report): ${peakRssBytes ?: "unavailable"}. Android thermal status: $thermalStatus (0 = none, 3 = severe). A killed worker may not report its final peak. No image or prompt is recorded."
        state.value = "$memory\n\n$generation"
    }
}
