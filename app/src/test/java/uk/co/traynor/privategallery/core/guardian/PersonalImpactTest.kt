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
        val result = checkNotNull(validatePersonalImpact(PersonalImpactSettings(true, true), rows + rows))
        assertEquals(1, result.correct)
        assertEquals(5, result.tested)
        assertEquals(1.0, result.falsePositiveRate()!!, 0.0)
        assertNull(result.falseNegativeRate())
        assertEquals(0 to 4, result.byCategory[MessageImpact.FINE])
    }
    @Test fun recommendationsRequireOptInAndRepeatedReports() {
        val reports = summarizePersonalImpact(List(3) { example })
        assertTrue(personalImpactSuggestions(PersonalImpactSettings(true, false), reports).isEmpty())
        assertTrue(personalImpactSuggestions(PersonalImpactSettings(true, true),
            summarizePersonalImpact(listOf(example))).isEmpty())
        assertTrue(personalImpactSuggestions(PersonalImpactSettings(true, true), reports).single().contains("3 times"))
    }
    @Test fun conversationValidationUsesOnlyEarlierFeedbackAndOneTestPerThread() {
        val settings = PersonalImpactSettings(true, true)
        val rows = (0 until 10).flatMap { thread ->
            (1L..3L).map { ConversationImpactReport("$thread", it, it + 1, example) } +
                ConversationImpactReport("$thread", 5L, 6L,
                    example.copy(impact = if (thread < 4) MessageImpact.FINE else MessageImpact.UPSETTING))
        }
        val measured = checkNotNull(validateConversationImpact(settings, rows))
        assertEquals(10, measured.tested)
        assertEquals(6, measured.correct)
        assertEquals(1.0, measured.falsePositiveRate()!!, 0.0)
        assertNull(validateConversationImpact(settings, rows.map { it.copy(capturedAt = 1L) }))
        assertNull(validateConversationImpact(settings, rows.map { it.copy(reportedAt = 5L) }))
    }

    @Test fun historyExcludesLaterCapturesAndRespectsTheFeedbackCutoff() {
        val rows = listOf(
            ConversationImpactReport("thread", 1, 2, example),
            ConversationImpactReport("thread", 2, 5, example.copy(impact = MessageImpact.NEUTRAL)),
            ConversationImpactReport("thread", 5, 6, example.copy(impact = MessageImpact.FINE)),
        )
        assertEquals(listOf(example), priorConversationImpact(rows, 5, 5))
        assertEquals(listOf(example, rows[1].feedback), priorConversationImpact(rows, 5, 7))
    }
}
