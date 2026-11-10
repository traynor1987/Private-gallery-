package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianInboxPolicyTest {
    @Test fun missingClassificationStaysPendingAndExpiredHoldNeedsReview() {
        val start = GuardianInboxClock(100_000_000L, 50_000L, 3)
        val before = start.copy(wall = start.wall + 1_000L, elapsed = start.elapsed + 1_000L)
        val after = start.copy(wall = start.wall + GUARDIAN_HOLD_MILLIS, elapsed = start.elapsed + GUARDIAN_HOLD_MILLIS)
        val hold = GuardianInboxHold(start.wall + GUARDIAN_HOLD_MILLIS, start.elapsed + GUARDIAN_HOLD_MILLIS, start.boot)
        assertEquals(GuardianInboxSection.PENDING, guardianInboxSection(null, false, start))
        assertEquals(GuardianInboxSection.READY, guardianInboxSection(null, true, start))
        assertEquals(GuardianInboxSection.COOLING, guardianInboxSection(hold, true, before))
        assertEquals(GuardianInboxSection.PROTECTED, guardianInboxSection(hold, true, after))
        assertEquals(GuardianInboxSection.COOLING,
            guardianInboxSection(hold, false, before.copy(wall = after.wall)))
        assertEquals(GuardianInboxSection.COOLING,
            guardianInboxSection(hold, false, before.copy(boot = 4, elapsed = 50L)))
    }
}
