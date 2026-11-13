package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardianNoticePolicyTest {
    @Test fun explicitInsultTakesPriorityOverLowConcern() {
        val patterns = recogniseNovaBrainPatterns("You're a worthless idiot. I will never help you again.")
        assertEquals(GuardianNoticeKind.HIGH, guardianNoticeKind(GuardianAssessment.LOW_RISK, patterns))
    }

    @Test fun supportiveWordingRequiresARealLowRiskAssessment() {
        val patterns = recogniseNovaBrainPatterns("I hope you are okay. Take whatever time you need.")
        assertEquals(GuardianNoticeKind.LOW, guardianNoticeKind(GuardianAssessment.LOW_RISK, patterns))
        assertEquals(GuardianNoticeKind.UNCERTAIN, guardianNoticeKind(null, patterns))
    }

    @Test fun ambiguousLanguageDoesNotBecomeAnUnsupportedWarning() {
        assertEquals(GuardianNoticeKind.UNCERTAIN, guardianNoticeKind(GuardianAssessment.UNCERTAIN,
            recogniseNovaBrainPatterns("Okay, I guess.")))
        assertEquals(GuardianNoticeKind.UPSETTING, guardianNoticeKind(null,
            recogniseNovaBrainPatterns("After everything I've done for you.")))
    }
}
