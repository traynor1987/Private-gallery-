package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianLocalClassifierTest {
    @Test fun everydayGreetingsAndRequestsAreOrdinaryWithoutTrainingOrHistory() {
        val messages = listOf("Hi", " HELLO! ", "Good morning", "How are you?", "Thanks",
            "See you tomorrow", "Can you call me?", "Nice to hear from you")
        for (sensitivity in GuardianSensitivity.entries) for (text in messages) {
            val decision = guardianContentDecision(text, emptyList(), sensitivity = sensitivity)
            assertEquals(text, GuardianContentDecision(GuardianAssessment.LOW_RISK, GuardianAssessmentReason.ROUTINE), decision)
            assertFalse(guardianPolicyHolds(decision.assessment, holdUncertain = true))
        }
    }

    @Test fun shortLengthDoesNotOverrideThreatsOrTheOwnersHoldRule() {
        for (text in listOf("You're an idiot.", "Hi, you're worthless.", "I will hit you.",
            "I won't take no for an answer.", "After everything I've done for you.", "You made me do it.")) {
            assertEquals(text, GuardianAssessment.UPSETTING, classifyGuardian(text, emptyList()))
        }
        for (text in listOf("Come here", "Fine, whatever you say", "Hi\nCome here", "Hi, explain yourself")) {
            val decision = guardianContentDecision(text, emptyList())
            assertEquals(text, GuardianAssessment.UNCERTAIN, decision.assessment)
            assertFalse(guardianPolicyHolds(decision.assessment, holdUncertain = false))
            assertTrue(guardianPolicyHolds(decision.assessment, holdUncertain = true))
        }
        assertFalse(guardianDismissalAllowed(automatic = true, quietCollection = false, held = false))
        assertFalse(guardianDismissalAllowed(automatic = false, quietCollection = false, held = true))
        assertTrue(guardianDismissalAllowed(automatic = true, quietCollection = false, held = true))
        assertTrue(guardianDismissalAllowed(automatic = false, quietCollection = true, held = false))
    }

    @Test fun approvedShortCorrectionIsExactAndDirectThreatRemainsProtected() {
        val approved = GuardianExample(guardianFeatures("Hi"), GuardianAssessment.UPSETTING)
        assertEquals(GuardianAssessment.UPSETTING, classifyGuardian(" HI ", listOf(approved)))
        assertEquals(GuardianAssessment.LOW_RISK, classifyGuardian("Hi!", listOf(approved)))
        assertEquals(GuardianAssessment.LOW_RISK, classifyGuardian("Hello", listOf(approved)))
        val threat = "I will hit you"
        assertEquals(GuardianAssessment.UPSETTING,
            classifyGuardian(threat, listOf(GuardianExample(guardianFeatures(threat), GuardianAssessment.LOW_RISK))))
    }

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
