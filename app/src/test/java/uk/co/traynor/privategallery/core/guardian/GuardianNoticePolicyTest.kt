package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianNoticePolicyTest {
    @Test fun minuteScheduleSupportsOvernightAndRejectsInvalidInput() {
        val start = checkNotNull(guardianNoticeTime("22:35"))
        val end = checkNotNull(guardianNoticeTime("06:15"))
        assertFalse(guardianNoticeQuietMinute(start - 1, start, end))
        assertTrue(guardianNoticeQuietMinute(start, start, end))
        assertTrue(guardianNoticeQuietMinute(0, start, end))
        assertFalse(guardianNoticeQuietMinute(end, start, end))
        assertEquals(null, guardianNoticeTime("24:00"))
        assertEquals(null, guardianNoticeTime("06:60"))
    }

    @Test fun routingRequiresAnExactProfileAndRejectsConflictingEnrollment() {
        assertEquals(ProbeAccount.MAIN, guardianNoticeProfile(0, 0, 150))
        assertEquals(ProbeAccount.FRIENDS, guardianNoticeProfile(150, 0, 150))
        assertEquals(ProbeAccount.UNRESOLVED, guardianNoticeProfile(151, 0, 150))
        assertEquals(ProbeAccount.UNRESOLVED, guardianNoticeProfile(150, 0, null))
        assertEquals(ProbeAccount.UNRESOLVED, guardianNoticeProfile(0, 0, 0))
        assertEquals(ProbeAccount.UNRESOLVED, guardianNoticeProfile(150, null, 150))
    }

    @Test fun quietHoursHandleMidnightAndUseAnExclusiveEnd() {
        assertTrue(guardianNoticeQuietHour(22, 22, 7))
        assertTrue(guardianNoticeQuietHour(0, 22, 7))
        assertFalse(guardianNoticeQuietHour(7, 22, 7))
        assertFalse(guardianNoticeQuietHour(21, 22, 7))
        assertTrue(guardianNoticeQuietHour(12, 9, 17))
        assertFalse(guardianNoticeQuietHour(17, 9, 17))
        assertFalse(guardianNoticeQuietHour(12, 12, 12))
    }

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
