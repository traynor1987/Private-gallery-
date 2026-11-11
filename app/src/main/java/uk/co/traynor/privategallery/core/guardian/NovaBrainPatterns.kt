package uk.co.traynor.privategallery.core.guardian

internal enum class NovaBrainPattern(val label: String, expression: String) {
    GUILT("Guilt-tripping", "after everything i(?:'ve| have) done for you"),
    GASLIGHTING("Gaslighting indicator", "you(?:'re| are) imagining things"),
    EMOTIONAL_PRESSURE("Emotional pressure", "if you really (?:loved|cared about) me|if you leave me,? (?:it will be your fault|you will regret it)"),
    REPEATED_DEMAND("Repeated demand", "how many times do i have to tell you"),
    BLAME_SHIFTING("Blame-shifting", "you made me do it"),
    PASSIVE_AGGRESSION("Passive aggression", "fine,? whatever you say"),
    COERCION("Coercion", "do as i say or else"),
    BOUNDARY_PRESSURE("Boundary pressure", "i don(?:'t| not) care if you said no|i won't take no for an answer"),
    THREAT("Threat", "i(?:'ll| will) (?:hurt|hit|kill) you"),
    INSULT("Insult", "you(?:'re| are) (?:a )?(?:worthless idiot|stupid idiot)");

    private val cue = Regex("(?:^|[.!?]\\s+)(?:$expression)\\b", RegexOption.IGNORE_CASE)

    fun matches(text: String): Boolean = cue.containsMatchIn(text)
}

internal fun recogniseNovaBrainPatterns(text: String): Set<NovaBrainPattern> {
    val normalised = text.trim().replace('’', '\'')
    return NovaBrainPattern.entries.filterTo(linkedSetOf()) { it.matches(normalised) }
}
