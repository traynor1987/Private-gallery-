package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaBrainPatternTest {
    @Test fun recognisesLocalCuesWithoutExamples() {
        val cases = mapOf(
            "After everything I’ve done for you" to NovaBrainPattern.GUILT,
            "You're imagining things" to NovaBrainPattern.GASLIGHTING,
            "If you really loved me" to NovaBrainPattern.EMOTIONAL_PRESSURE,
            "How many times do I have to tell you?" to NovaBrainPattern.REPEATED_DEMAND,
            "You made me do it" to NovaBrainPattern.BLAME_SHIFTING,
            "Fine, whatever you say" to NovaBrainPattern.PASSIVE_AGGRESSION,
            "Do as I say or else" to NovaBrainPattern.COERCION,
            "I don't care if you said no" to NovaBrainPattern.BOUNDARY_PRESSURE,
            "I will hurt you" to NovaBrainPattern.THREAT,
            "You're a worthless idiot" to NovaBrainPattern.INSULT,
        )
        cases.forEach { (text, cue) -> assertEquals(text, setOf(cue), recogniseNovaBrainPatterns(text)) }
    }

    @Test fun ambiguousOrNegatedTextCanRemainUnclassified() {
        listOf("I'll be there tomorrow", "I won't hurt you", "They said I will hurt you").forEach {
            assertEquals(emptySet<NovaBrainPattern>(), recogniseNovaBrainPatterns(it))
        }
    }
    @Test fun checksCapturedTextBeyondTheFirstTwoThousandCharacters() {
        val text = "Neutral context. ".repeat(160) + "If you leave me, you will regret it."
        assertEquals(setOf(NovaBrainPattern.EMOTIONAL_PRESSURE), recogniseNovaBrainPatterns(text))
    }
}
