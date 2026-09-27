package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.*
import org.junit.Test

class RecentlyDeletedPolicyTest {
    @Test fun retentionBoundaryDoesNotDeleteEarlyOrFromFutureClock() {
        val deleted = 1_000_000L
        assertFalse(RecentlyDeletedPolicy.expired(deleted, deleted + RecentlyDeletedPolicy.RETENTION_MILLIS - 1))
        assertTrue(RecentlyDeletedPolicy.expired(deleted, deleted + RecentlyDeletedPolicy.RETENTION_MILLIS))
        assertFalse(RecentlyDeletedPolicy.expired(deleted, deleted - 1))
        assertFalse(RecentlyDeletedPolicy.expired(null, Long.MAX_VALUE))
    }
}
