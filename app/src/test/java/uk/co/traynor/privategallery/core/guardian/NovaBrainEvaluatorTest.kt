package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class NovaBrainEvaluatorTest {
    private val contexts = listOf(
        "kitchen table", "garden path", "station ticket", "summer holiday", "school playground",
        "office desk", "weekend market", "hospital entrance", "meeting room", "library shelf",
    )
    private val holdout = (0..9).flatMap { group ->
        listOf(
            NovaBrainValidationMessage("$group", "I will hurt you. Context ${contexts[group]}", setOf(NovaBrainPattern.THREAT)),
            NovaBrainValidationMessage("$group", "Do as I say or else. Context ${contexts[group]}", setOf(NovaBrainPattern.COERCION)),
            NovaBrainValidationMessage("$group", "Can we meet for lunch? Context ${contexts[group]}", emptySet()),
            NovaBrainValidationMessage("$group", "Fine, I guess. Context ${contexts[group]}", emptySet()),
        )
    }

    @Test fun measuresMatchesAndFalsePositives() {
        val result = checkNotNull(evaluateNovaBrain(holdout, emptySet(), emptySet()))
        assertEquals(1.0, result.precision, 0.0)
        assertEquals(1.0, result.recall, 0.0)
        assertEquals(1.0, result.accuracy, 0.0)
        assertEquals(0.0, result.validatedImprovement, 0.0)
        assertEquals(10, result.categoryResults.getValue(NovaBrainPattern.THREAT).truePositive)
        val changed = holdout.toMutableList()
        changed[2] = changed[2].copy(text = "You're imagining things. Quoted in rehearsal.")
        assertEquals(20.0 / 21, evaluateNovaBrain(changed, emptySet(), emptySet())!!.precision, 0.0)
    }

    @Test fun measuresLearnedFlagsOnDistinctExamples() {
        val ordinary = holdout[2].text
        val learned = GuardianExample(guardianFeatures(ordinary + " later today"), GuardianAssessment.UPSETTING)
        val result = checkNotNull(evaluateNovaBrain(holdout, emptySet(), emptySet(), listOf(learned)))
        assertEquals(1, result.falsePositives)
        assertEquals(39.0 / 40, result.accuracy, 0.0)
    }

    @Test fun rejectsTrainingOverlapAndTooLittleEvidence() {
        assertNull(evaluateNovaBrain(holdout, setOf("0"), emptySet()))
        assertNull(evaluateNovaBrain(holdout, emptySet(), setOf(guardianFeatures(holdout[0].text))))
        assertNull(evaluateNovaBrain(holdout.dropLast(1), emptySet(), emptySet()))
        val paraphrased = guardianFeatures(holdout[0].text + " today")
        assertNull(evaluateNovaBrain(holdout, emptySet(), setOf(paraphrased)))
        val repeated = holdout.toMutableList()
        repeated[4] = repeated[4].copy(text = holdout[0].text + " today")
        assertNull(evaluateNovaBrain(repeated, emptySet(), emptySet()))
    }
}
