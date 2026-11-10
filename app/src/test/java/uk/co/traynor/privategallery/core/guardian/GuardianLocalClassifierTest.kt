package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianLocalClassifierTest {
    @Test fun approvedExamplesGeneraliseCautiouslyAndPreserveWordOrder() {
        val alarming = "you have to come here right now"
        val routine = "you do not have to come here right now"
        val examples = listOf(
            GuardianExample(guardianFeatures(alarming), GuardianAssessment.UPSETTING),
            GuardianExample(guardianFeatures(routine), GuardianAssessment.LOW_RISK),
        )
        assertEquals(GuardianAssessment.UPSETTING, classifyGuardian(alarming, examples))
        assertEquals(GuardianAssessment.LOW_RISK, classifyGuardian(routine, examples))
        assertEquals(GuardianAssessment.UNCERTAIN, classifyGuardian("I'll be there after work", examples))
        assertEquals(GuardianAssessment.UNCERTAIN, classifyGuardian("come here", examples))
        assertEquals(GuardianAssessment.UNCERTAIN, classifyGuardian(alarming, emptyList()))
    }

    @Test fun conflictingExactExamplesAbstain() {
        val text = "this is an owner reviewed example"
        val features = guardianFeatures(text)
        assertEquals(GuardianAssessment.UNCERTAIN, classifyGuardian(text, listOf(
            GuardianExample(features, GuardianAssessment.UPSETTING),
            GuardianExample(features, GuardianAssessment.LOW_RISK),
        )))
    }
}
