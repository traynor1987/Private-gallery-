package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class NovaBrainEvaluatorTest {
    private val holdout = (0..9).flatMap { group ->
        listOf(
            NovaBrainValidationMessage("$group", "I will hurt you. Sample $group", setOf(NovaBrainPattern.THREAT)),
            NovaBrainValidationMessage("$group", "Do as I say or else. Sample $group", setOf(NovaBrainPattern.COERCION)),
            NovaBrainValidationMessage("$group", "Can we meet for lunch? Sample $group", emptySet()),
            NovaBrainValidationMessage("$group", "Fine, I guess. Sample $group", emptySet()),
        )
    }

    @Test fun measuresMatchesAndFalsePositives() {
        val result = checkNotNull(evaluateNovaBrain(holdout, emptySet(), emptySet()))
        assertEquals(1.0, result.precision, 0.0)
        assertEquals(1.0, result.recall, 0.0)
        val changed = holdout.toMutableList()
        changed[2] = changed[2].copy(text = "You're imagining things. Quoted in rehearsal.")
        assertEquals(20.0 / 21, evaluateNovaBrain(changed, emptySet(), emptySet())!!.precision, 0.0)
    }

    @Test fun rejectsTrainingOverlapAndTooLittleEvidence() {
        assertNull(evaluateNovaBrain(holdout, setOf("0"), emptySet()))
        assertNull(evaluateNovaBrain(holdout, emptySet(), setOf(guardianFeatures(holdout[0].text))))
        assertNull(evaluateNovaBrain(holdout.dropLast(1), emptySet(), emptySet()))
    }
}
