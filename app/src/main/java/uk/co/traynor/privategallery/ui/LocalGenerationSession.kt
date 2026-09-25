package uk.co.traynor.privategallery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.editor.AiEditProvenance
import uk.co.traynor.privategallery.core.editor.local.*

/** Retained only across Activity recreation. Plaintext lives in memory, never SavedState. */
internal class LocalGenerationSession : ViewModel() {
    data class State(
        val running: Boolean = false,
        val cancelling: Boolean = false,
        val progress: LocalGenerationProgress? = null,
        val result: ByteArray? = null,
        val provenance: AiEditProvenance? = null,
        val error: LocalStopReason? = null,
        val startedAtMs: Long? = null,
    )
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
    private var work: Job? = null
    private var generationEpoch = 0

    fun start(progress: StateFlow<LocalGenerationProgress?>?, execute: suspend () -> Pair<ByteArray, AiEditProvenance>): Boolean {
        if (mutable.value.running) return false
        discardResult()
        val epoch = ++generationEpoch
        mutable.value = State(running = true, progress = LocalGenerationProgress(LocalStage.PREPARING),
            startedAtMs = android.os.SystemClock.elapsedRealtime())
        work = viewModelScope.launch {
            val listener = progress?.let { upstream -> launch {
                upstream.collect { update -> if (update != null && epoch == generationEpoch && mutable.value.running)
                    mutable.value = mutable.value.copy(progress = update) }
            } }
            try {
                val (bytes, provenance) = execute()
                if (!isActive || epoch != generationEpoch) { bytes.fill(0); return@launch }
                mutable.value = State(result = bytes, provenance = provenance)
            } catch (_: CancellationException) {
                if (epoch == generationEpoch) mutable.value = State(error = LocalStopReason.USER_CANCELLED)
            } catch (failure: LocalGenerationFailure) {
                if (epoch == generationEpoch) mutable.value = State(error = failure.reason)
            } catch (_: OutOfMemoryError) {
                if (epoch == generationEpoch) mutable.value = State(error = LocalStopReason.JAVA_HEAP)
            } catch (_: Exception) {
                if (epoch == generationEpoch) mutable.value = State(error = LocalStopReason.INFERENCE)
            } finally { listener?.cancel(); if (epoch == generationEpoch) work = null }
        }
        return true
    }
    fun cancel() {
        if (!mutable.value.running || mutable.value.cancelling) return
        mutable.value = mutable.value.copy(cancelling = true)
        work?.cancel()
    }
    fun dismissError() { if (!mutable.value.running) mutable.value = mutable.value.copy(error = null) }
    fun discardResult() { mutable.value.result?.fill(0); mutable.value = State() }
    fun clear() { generationEpoch++; cancel(); discardResult() }
    override fun onCleared() { clear(); super.onCleared() }
}
