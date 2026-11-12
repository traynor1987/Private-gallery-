package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class PersonalImpactTest {
    private val example = PersonalImpact(MessageImpact.UPSETTING, WarningHelp.YES, WaitingOutcome.BETTER)

    @Test fun onlyExplicitAnswersRoundTrip() {
        assertEquals(example, PersonalImpact.decode(example.encode()))
        assertEquals("UPSETTING", example.encode().getString("impact"))
        assertEquals("YES", example.encode().getString("warning"))
        assertEquals("BETTER", example.encode().getString("waiting"))
        assertFalse(PersonalImpact().hasFeedback())
        assertFalse(PersonalImpact.decode(PersonalImpact().encode()).hasFeedback())
    }

    @Test fun disabledLearningDoesNotUseReportsAndCorrectionsRemainSeparate() {
        val rows = List(3) { example }
        assertNull(predictPersonalImpact(PersonalImpactSettings(), rows))
        assertNull(predictPersonalImpact(PersonalImpactSettings(collect = true), rows))
        assertNull(predictPersonalImpact(PersonalImpactSettings(true, true),
            listOf(example, example, example.copy(excluded = true))))
        assertEquals(MessageImpact.UPSETTING, predictPersonalImpact(PersonalImpactSettings(true, true), rows))
        val reports = summarizePersonalImpact(listOf(example.copy(correction = WarningCorrection.FALSE_POSITIVE),
            PersonalImpact(correction = WarningCorrection.FALSE_NEGATIVE)))
        assertEquals(1, reports.useful)
        assertEquals(1, reports.falsePositives)
        assertEquals(1, reports.falseNegatives)
        assertEquals(mapOf(WaitingOutcome.BETTER to 1), reports.waiting)
    }

    @Test fun heldOutGroupsAreCountedOnceAndNeverFitTheirOwnPrediction() {
        val entries = (0..200).map { "group-$it" }
        val train = entries.filter { Math.floorMod(it.hashCode(), 3) != 0 }.take(10)
        val test = entries.filter { Math.floorMod(it.hashCode(), 3) == 0 }.take(5)
        val rows = train.map { PersonalImpactExample(it, example) } +
            test.mapIndexed { i, group ->
                PersonalImpactExample(group, if (i == 0) example else example.copy(impact = MessageImpact.FINE))
            }
        assertNull(validatePersonalImpact(PersonalImpactSettings(true, false), rows))
        assertNull(validatePersonalImpact(PersonalImpactSettings(true, true), rows.take(14)))
        assertEquals(PersonalImpactValidation(1, 5), validatePersonalImpact(PersonalImpactSettings(true, true), rows + rows))
    }
}
