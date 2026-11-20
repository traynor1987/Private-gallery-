package uk.co.traynor.privategallery.core.guardian

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class GuardianPipelineObservation(
    val sequence: Long,
    val slot: Long,
    val account: ProbeAccount,
    val grouped: Boolean,
    val summary: Boolean,
    val clearable: Boolean,
    val locked: Boolean,
    val dnd: Boolean,
    val mode: GuardianPresentationMode,
    val entries: Int? = null,
    val capture: String = "Received",
    val assessed: Int? = null,
    val held: Int? = null,
    val latest: GuardianAssessment? = null,
    val action: GuardianPresentationAction? = null,
    val delivery: String? = null,
    val removal: String? = null,
)

/** Bounded, process-local diagnostics. Entries have no message, sender, Android key or contact identifiers. */
internal object GuardianPipelineDiagnostics {
    private val counter = AtomicLong()
    private val observations = MutableStateFlow<List<GuardianPipelineObservation>>(emptyList())
    val recent = observations.asStateFlow()

    fun record(
        slot: Long, account: ProbeAccount, grouped: Boolean, summary: Boolean, clearable: Boolean,
        locked: Boolean, dnd: Boolean, mode: GuardianPresentationMode,
    ): Long {
        val sequence = counter.incrementAndGet()
        val observation = GuardianPipelineObservation(sequence, slot, account, grouped, summary, clearable, locked, dnd, mode)
        observations.update { (it + observation).takeLast(24) }
        return sequence
    }

    fun update(sequence: Long?, change: (GuardianPipelineObservation) -> GuardianPipelineObservation) {
        if (sequence == null) return
        observations.update { entries -> entries.map { if (it.sequence == sequence) change(it) else it } }
    }

    fun clear() { observations.value = emptyList() }
}
