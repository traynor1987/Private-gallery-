package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.BuildConfig

/** Acceptance-only, memory-only fixed fields. No free-form errors, paths, prompts or image data. */
object LocalAiDiagnostics {
    private val state = MutableStateFlow("No local generation recorded this session.")
    val summary = state.asStateFlow()
    fun record(model: ModelSpec, width: Int, height: Int, durationMs: Long, result: String, thermalStatus: Int) {
        if (!BuildConfig.DEBUG && !BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        require(result in setOf("SUCCESS", "CANCELLED", "FAILED"))
        state.value = "${model.providerId} · ${model.id}\nstable-diffusion.cpp 19bbbca1 · CPU\nInput: ${width} × ${height}\nDuration: ${durationMs} ms · $result\nPeak worker memory: unavailable. Android thermal status: $thermalStatus (0 = none, 3 = severe). Memory pressure is monitored; no image or prompt is recorded."
    }
}
