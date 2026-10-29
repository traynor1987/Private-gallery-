package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.*
import org.junit.Test

class RecentlyDeletedPolicyTest {
    @Test fun everyRetentionChoiceHasExactBoundaryAndNeverDoesNotExpire() {
        val deleted=1000L
        for(days in listOf(7,15,30,60)) {
            val boundary=deleted+days*RecentlyDeletedPolicy.DAY_MILLIS
            assertFalse(RecentlyDeletedPolicy.expired(deleted,boundary-1,days))
            assertTrue(RecentlyDeletedPolicy.expired(deleted,boundary,days))
            assertEquals(1L,RecentlyDeletedPolicy.remainingDays(deleted,boundary-1,days))
            assertEquals(0L,RecentlyDeletedPolicy.remainingDays(deleted,boundary,days))
        }
        assertFalse(RecentlyDeletedPolicy.expired(deleted,Long.MAX_VALUE,0))
        assertNull(RecentlyDeletedPolicy.remainingDays(deleted,Long.MAX_VALUE,0))
        assertTrue(runCatching {RecentlyDeletedPolicy.expired(deleted,Long.MAX_VALUE,1)}.isFailure)
    }

    @Test fun retentionBoundaryDoesNotDeleteEarlyOrFromFutureClock() {
        val deleted = 1_000_000L
        assertFalse(RecentlyDeletedPolicy.expired(deleted, deleted + RecentlyDeletedPolicy.RETENTION_MILLIS - 1))
        assertTrue(RecentlyDeletedPolicy.expired(deleted, deleted + RecentlyDeletedPolicy.RETENTION_MILLIS))
        assertFalse(RecentlyDeletedPolicy.expired(deleted, deleted - 1))
        assertFalse(RecentlyDeletedPolicy.expired(null, Long.MAX_VALUE))
    }
}
