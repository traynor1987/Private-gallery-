package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianRetention89Test {
    @Test fun sevenAndThirtyDaysUseCaptureTimeAndExactBoundary() {
        val captured = 1_800_000_000_000L
        for (days in listOf(1, 7, 30)) {
            assertEquals(days * GuardianRetention89.DAY, GuardianRetention89.archiveAfter(days, captured, captured))
            assertEquals(1L, GuardianRetention89.archiveAfter(days, captured, captured + days * GuardianRetention89.DAY - 1))
            assertEquals(0L, GuardianRetention89.archiveAfter(days, captured, captured + days * GuardianRetention89.DAY))
            assertEquals(0L, GuardianRetention89.archiveAfter(days, captured, captured + 365 * GuardianRetention89.DAY))
        }
    }
    @Test fun foreverNeverSchedulesExpiryAndClockRollbackDoesNotExpire() {
        assertNull(GuardianRetention89.archiveAfter(0, 1, Long.MAX_VALUE))
        assertEquals(30 * GuardianRetention89.DAY, GuardianRetention89.archiveAfter(30, 10_000, 0))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnrecognisedPolicy() {
        GuardianRetention89.archiveAfter(-1, 0, 0)
    }
}
