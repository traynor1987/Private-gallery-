package uk.co.traynor.privategallery.core.guardian

import java.util.Locale

internal data class NovaBrainValidationMessage(
    val conversationId: String,
    val text: String,
    val expectedPatterns: Set<NovaBrainPattern>,
)

internal const val NOVA_BRAIN_VALIDATION_VERSION = 2

internal data class NovaBrainCategoryResult(val truePositive: Int, val falsePositive: Int, val falseNegative: Int)

private fun overlaps(left: Set<String>, right: Set<String>): Boolean {
    val shared = left.count { it in right && it.length == 64 }
    val union = left.count { it.length == 64 } + right.count { it.length == 64 } - shared
    return shared >= 3 && shared * 100 >= union * 82
}

/** Evaluate the default local assessment and cue rules on independent owner-labelled evidence. */
internal fun evaluateNovaBrain(
    holdout: List<NovaBrainValidationMessage>,
    trainingConversationIds: Set<String>,
    trainingFeatures: Set<Set<String>>,
    examples: List<GuardianExample> = emptyList(),
): NovaBrainValidation? {
    val conversations = holdout.map { it.conversationId }.toSet()
    val positives = holdout.count { it.expectedPatterns.isNotEmpty() }
    val negatives = holdout.size - positives
    if (conversations.size < 10 || positives < 20 || negatives < 20) return null
    if (conversations.any { it.isBlank() || it in trainingConversationIds }) return null
    val texts = holdout.map { it.text.trim().lowercase(Locale.ROOT) }
    if (texts.any(String::isBlank) || texts.toSet().size != holdout.size) return null
    val features = holdout.map { guardianFeatures(it.text) }
    val trained = trainingFeatures + examples.map { it.features }
    if (features.any { feature -> feature.isNotEmpty() &&
            trained.any { feature == it || overlaps(feature, it) } }) return null
    for (index in features.indices) {
        if ((0 until index).any { prior ->
                features[index].isNotEmpty() &&
                    (features[index] == features[prior] || overlaps(features[index], features[prior]))
            }) return null
    }

    val predictions = holdout.map { it.expectedPatterns to recogniseNovaBrainPatterns(it.text) }
    val flagged = holdout.mapIndexed { index, row ->
        predictions[index].second.isNotEmpty() ||
            classifyGuardian(row.text, examples) == GuardianAssessment.UPSETTING
    }
    val truePositives = holdout.indices.count { holdout[it].expectedPatterns.isNotEmpty() && flagged[it] }
    val falsePositives = holdout.indices.count { holdout[it].expectedPatterns.isEmpty() && flagged[it] }
    val falseNegatives = positives - truePositives
    val baselineTrue = predictions.count { (expected, actual) -> expected.isNotEmpty() && actual.isNotEmpty() }
    val baselineFalse = predictions.count { (expected, actual) -> expected.isEmpty() && actual.isNotEmpty() }
    fun f1(tp: Int, fp: Int) = 2.0 * tp / (tp + fp + positives)
    val categoryResults = NovaBrainPattern.entries.associateWith { category ->
        NovaBrainCategoryResult(
            predictions.count { (expected, actual) -> category in expected && category in actual },
            predictions.count { (expected, actual) -> category !in expected && category in actual },
            predictions.count { (expected, actual) -> category in expected && category !in actual },
        )
    }
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
        (f1(truePositives, falsePositives) - f1(baselineTrue, baselineFalse)).coerceAtLeast(0.0),
        falsePositives = falsePositives, falseNegatives = falseNegatives,
        accuracy = (truePositives + negatives - falsePositives).toDouble() / holdout.size,
        categoryResults = categoryResults,
    )
}
