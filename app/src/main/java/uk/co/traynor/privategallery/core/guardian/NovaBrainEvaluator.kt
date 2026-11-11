package uk.co.traynor.privategallery.core.guardian

import java.util.Locale

internal data class NovaBrainValidationMessage(
    val conversationId: String,
    val text: String,
    val expectedPatterns: Set<NovaBrainPattern>,
)

/** Score the fixed local cue engine against independently labelled conversations. */
internal fun evaluateNovaBrain(
    holdout: List<NovaBrainValidationMessage>,
    trainingConversationIds: Set<String>,
    trainingFeatures: Set<Set<String>>,
): NovaBrainValidation? {
    val conversations = holdout.map { it.conversationId }.toSet()
    val positives = holdout.count { it.expectedPatterns.isNotEmpty() }
    val negatives = holdout.size - positives
    if (conversations.size < 10 || positives < 20 || negatives < 20) return null
    if (conversations.any { it.isBlank() || it in trainingConversationIds }) return null
    val texts = holdout.map { it.text.trim().lowercase(Locale.ROOT) }
    if (texts.any(String::isBlank) || texts.toSet().size != holdout.size) return null
    if (holdout.any { guardianFeatures(it.text) in trainingFeatures }) return null

    val predictions = holdout.map { it.expectedPatterns to recogniseNovaBrainPatterns(it.text) }
    val truePositives = predictions.count { (expected, actual) -> expected.isNotEmpty() && actual.isNotEmpty() }
    val falsePositives = predictions.count { (expected, actual) -> expected.isEmpty() && actual.isNotEmpty() }
    val correctPatterns = predictions.sumOf { (expected, actual) -> expected.intersect(actual).size }
    val expectedPatterns = predictions.sumOf { (expected, _) -> expected.size }
    val categories = predictions.flatMap { (expected, actual) -> expected.intersect(actual) }.toSet()
    val precision = if (truePositives + falsePositives == 0) 0.0 else truePositives.toDouble() / (truePositives + falsePositives)
    val recall = truePositives.toDouble() / positives
    val specificity = 1.0 - falsePositives.toDouble() / negatives
    return NovaBrainValidation(
        conversations.size, positives, negatives, precision, recall,
        correctPatterns.toDouble() / expectedPatterns,
        (recall + specificity) / 2,
        categories.size.toDouble() / NovaBrainPattern.entries.size,
        2.0 * truePositives / (truePositives + falsePositives + positives),
    )
}
