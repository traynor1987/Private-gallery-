package uk.co.traynor.privategallery.core.guardian

import java.security.MessageDigest
import java.util.Locale

internal enum class GuardianAssessment(val label: String) {
    LOW_RISK("Low Risk"), UNCERTAIN("Uncertain"), UPSETTING("Potentially Upsetting"),
}

internal data class GuardianExample(val features: Set<String>, val assessment: GuardianAssessment)

private val words = Regex("[\\p{L}\\p{M}]+")
private val negations = setOf("not", "never", "no", "cannot", "dont", "isnt", "wasnt", "wouldnt", "didnt")

internal fun guardianFeatures(text: String): Set<String> {
    val tokens = words.findAll(text.take(2048).lowercase(Locale.ROOT).replace("’", "").replace("'", ""))
        .take(96).map { it.value }.toList()
    if (tokens.size < 4) return emptySet()
    val digest = MessageDigest.getInstance("SHA-256")
    fun feature(value: String) = java.util.HexFormat.of().formatHex(digest.digest(value.toByteArray(Charsets.UTF_8)))
    return buildSet {
        for (n in 2..3) tokens.windowed(n).forEach { add(feature(it.joinToString(" "))) }
        add(if (tokens.any(negations::contains)) "negated" else "non-negated")
    }
}

/** Version 1 is a nearest-example phrase model. Unmatched, short and ambiguous text abstains. */
internal fun classifyGuardian(
    text: String, examples: List<GuardianExample>, recent: List<String> = emptyList(),
    sensitivity: GuardianSensitivity = GuardianSensitivity.STANDARD,
): GuardianAssessment {
    val features = guardianFeatures(text)
    if (features.isEmpty()) return GuardianAssessment.UNCERTAIN
    val negation = if ("negated" in features) "negated" else "non-negated"
    val ranked = examples.asSequence().filter { negation in it.features }.map { example ->
        val overlap = features.count { it in example.features } - 1
        val total = features.size + example.features.size - overlap - 2
        example.assessment to if (overlap < 3 || total <= 0) 0.0 else overlap.toDouble() / total
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }.entries.sortedByDescending { it.value }
    val best = ranked.firstOrNull() ?: return GuardianAssessment.UNCERTAIN
    val second = ranked.getOrNull(1)?.value ?: 0.0
    if (best.value - second < .20) return GuardianAssessment.UNCERTAIN
    if (best.value >= sensitivity.threshold) return best.key
    val supporting = best.key != GuardianAssessment.UNCERTAIN && best.value >= sensitivity.threshold - .12 &&
        recent.takeLast(3).count { classifyGuardian(it, examples) == best.key } >= 2
    return if (supporting) best.key else GuardianAssessment.UNCERTAIN
}
