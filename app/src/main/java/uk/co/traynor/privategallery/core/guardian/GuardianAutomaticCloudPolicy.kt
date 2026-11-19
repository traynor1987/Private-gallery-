package uk.co.traynor.privategallery.core.guardian

import java.util.Locale

/** Automatic requests carry one short excerpt, with no conversation metadata. */
internal object GuardianAutomaticCloudPolicy {
    const val STABILITY_MS = 10_000L
    const val WINDOW_MS = 300_000L
    const val MAX_PENDING = 32
    const val MAX_ATTEMPTS = 5
    const val MAX_EXCERPT = 320
    private val words = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
    private val sentence = Regex("[A-Za-z .,!?;:'’\u002d]+")
    private val sensitive = Regex(
        "\\b(password|passcode|pin|otp|verification code|account number|bank|postcode|address|passport|secret|https?|www)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val domainLike = Regex("[A-Za-z]\\.[A-Za-z]")

    fun excerpt(text: String, identifiers: List<String>): String? {
        // Multiline, structured, numeric and ambiguous text stays local. Names supplied by
        // Android are removed before transmission; this is not a guarantee of anonymity.
        if (text.length !in 1..MAX_EXCERPT || !sentence.matches(text) ||
            sensitive.containsMatchIn(text) || domainLike.containsMatchIn(text)) return null
        val known = identifiers.flatMap { value -> words.findAll(value).map { it.value.lowercase(Locale.ROOT) }.toList() }.toSet()
        val result = words.replace(text) { match ->
            val word = match.value.lowercase(Locale.ROOT).replace('’', '\'')
            if (word in known || word.removeSuffix("'s") in known) "[redacted]" else match.value
        }.trim()
        return result.takeIf { it.length in 4..MAX_EXCERPT && words.findAll(it.replace("[redacted]", "")).count() >= 3 }
    }

    fun validWindow(createdAt: Long, now: Long) = now >= createdAt && now - createdAt < WINDOW_MS
    fun backoff(attempt: Int) = STABILITY_MS * (1L shl (attempt - 1).coerceIn(0, 3))
    fun ready(first: Long, second: Long, sameNetwork: Boolean) =
        sameNetwork && second >= first && second - first >= STABILITY_MS
}

internal enum class GuardianCloudState(val label: String) {
    QUEUED("Awaiting cloud assistance"),
    SENDING("Cloud request submitted"),
    COMPLETE("Cloud advisory available"),
    LOCAL_ONLY("Kept local"),
    UNAVAILABLE("Cloud assistance unavailable"),
    UNKNOWN("Cloud outcome unconfirmed"),
}
