package uk.co.traynor.privategallery.core.guardian

import java.security.MessageDigest
import java.util.Locale

internal enum class GuardianAssessment(val label: String) {
    LOW_RISK("Low Risk"), UNCERTAIN("Uncertain"), UPSETTING("Potentially Upsetting"),
}

internal data class GuardianExample(val features: Set<String>, val assessment: GuardianAssessment)

internal enum class GuardianAssessmentReason(val label: String) {
    ROUTINE("Ordinary greeting, reply or request"),
    OWNER_EXAMPLES("Matched owner-approved examples"),
    CONCERNING_CUE("Concerning wording detected"),
    INSUFFICIENT_CONTEXT("Insufficient evidence for a confident assessment"),
}

internal data class GuardianContentDecision(val assessment: GuardianAssessment, val reason: GuardianAssessmentReason)

private val words = Regex("[\\p{L}\\p{M}]+")
private val negations = setOf("not", "never", "no", "cannot", "dont", "isnt", "wasnt", "wouldnt", "didnt")
private val spacing = Regex("[\\p{Zs}\\t]+")
private val routineMessages = setOf(
    "hi", "hey", "hello", "hello there", "good morning", "good afternoon", "good evening", "good night",
    "how are you", "how are you doing", "thanks", "thank you", "thanks so much", "you're welcome",
    "nice to hear from you", "hope you're well", "see you soon", "see you later", "see you tomorrow",
    "have a good day", "can you call me", "could you call me", "are you free to talk",
)

private fun normaliseGuardianText(text: String) =
    text.trim().lowercase(Locale.ROOT).replace('’', '\'').replace(spacing, " ")

internal fun guardianFeatures(text: String): Set<String> {
    val normalised = normaliseGuardianText(text.take(2048))
    val tokens = words.findAll(normalised.replace("'", ""))
        .take(96).map { it.value }.toList()
    if (tokens.isEmpty()) return emptySet()
    val digest = MessageDigest.getInstance("SHA-256")
    fun feature(value: String) = java.util.HexFormat.of().formatHex(digest.digest(value.toByteArray(Charsets.UTF_8)))
    // Short owner examples are exact matches only. Preserve the punctuation and non-word content.
    if (tokens.size < 4) return setOf("exact:" + feature(normalised))
    return buildSet {
        for (n in 2..3) tokens.windowed(n).forEach { add(feature(it.joinToString(" "))) }
        add(if (tokens.any(negations::contains)) "negated" else "non-negated")
    }
}

internal fun classifyGuardian(
    text: String, examples: List<GuardianExample>, recent: List<String> = emptyList(),
    sensitivity: GuardianSensitivity = GuardianSensitivity.STANDARD,
): GuardianAssessment = guardianContentDecision(text, examples, recent, sensitivity).assessment

internal fun guardianContentDecision(
    text: String, examples: List<GuardianExample>, recent: List<String> = emptyList(),
    sensitivity: GuardianSensitivity = GuardianSensitivity.STANDARD,
): GuardianContentDecision {
    val cues = recogniseNovaBrainPatterns(text)
    val concerning = GuardianContentDecision(GuardianAssessment.UPSETTING, GuardianAssessmentReason.CONCERNING_CUE)
    if (NovaBrainPattern.THREAT in cues || NovaBrainPattern.INSULT in cues) return concerning
    val features = guardianFeatures(text)
    val exactLabels = if (features.isEmpty()) emptySet() else examples.asSequence()
        .filter { it.features == features }.map { it.assessment }.toSet()
    val learned = if (exactLabels.size > 1) GuardianAssessment.UNCERTAIN
        else exactLabels.singleOrNull() ?: matchGuardianExamples(features, examples, recent, sensitivity)
    if (learned != null) return GuardianContentDecision(learned, GuardianAssessmentReason.OWNER_EXAMPLES)
    if (cues.any { it != NovaBrainPattern.PASSIVE_AGGRESSION }) return concerning
    if (normaliseGuardianText(text).trimEnd('.', '?', '!') in routineMessages) {
        return GuardianContentDecision(GuardianAssessment.LOW_RISK, GuardianAssessmentReason.ROUTINE)
    }
    return GuardianContentDecision(GuardianAssessment.UNCERTAIN, GuardianAssessmentReason.INSUFFICIENT_CONTEXT)
}

private fun matchGuardianExamples(
    features: Set<String>, examples: List<GuardianExample>, recent: List<String>,
    sensitivity: GuardianSensitivity,
): GuardianAssessment? {
    if (features.size < 2) return null
    val negation = if ("negated" in features) "negated" else "non-negated"
    val ranked = examples.asSequence().filter { negation in it.features }.map { example ->
        val overlap = features.count { it in example.features } - 1
        val total = features.size + example.features.size - overlap - 2
        example.assessment to if (overlap < 3 || total <= 0) 0.0 else overlap.toDouble() / total
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }.entries.sortedByDescending { it.value }
    val best = ranked.firstOrNull() ?: return null
    val second = ranked.getOrNull(1)?.value ?: 0.0
    if (best.value - second < .20) return null
    if (best.value >= sensitivity.threshold) return best.key
    val supporting = best.key != GuardianAssessment.UNCERTAIN && best.value >= sensitivity.threshold - .12 &&
        recent.takeLast(3).count { classifyGuardian(it, examples) == best.key } >= 2
    return best.key.takeIf { supporting }
}
