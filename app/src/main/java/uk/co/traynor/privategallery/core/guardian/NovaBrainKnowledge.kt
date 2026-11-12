package uk.co.traynor.privategallery.core.guardian

import kotlin.math.roundToInt

/** Aggregate results from a frozen holdout disjoint from the training conversations. */
internal data class NovaBrainValidation(
    val conversations: Int,
    val upsetting: Int,
    val ordinary: Int,
    val precision: Double,
    val recall: Double,
    val patternRecall: Double,
    val calibration: Double,
    val confirmedPatternCoverage: Double,
    val validatedImprovement: Double,
    val falsePositives: Int = 0,
    val falseNegatives: Int = 0,
    val accuracy: Double = 0.0,
    val categoryResults: Map<NovaBrainPattern, NovaBrainCategoryResult> = emptyMap(),
    val validatedAtMillis: Long = 0,
) {
    fun knowledgePercent(): Int? {
        if (conversations < 10 || upsetting < 20 || ordinary < 20) return null
        val scores = listOf(precision, recall, patternRecall, calibration, confirmedPatternCoverage, validatedImprovement)
        if (scores.any { !it.isFinite() || it !in 0.0..1.0 }) return null
        return (100 * (0.20 * precision + 0.25 * recall + 0.20 * patternRecall +
            0.15 * calibration + 0.10 * confirmedPatternCoverage + 0.10 * validatedImprovement)).roundToInt()
    }
}
