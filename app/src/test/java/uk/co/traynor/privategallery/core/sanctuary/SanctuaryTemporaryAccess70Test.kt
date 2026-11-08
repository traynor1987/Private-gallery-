package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.*
import org.junit.Test

class SanctuaryTemporaryAccess70Test {
    @Test fun deniesUnconfirmedExpiredCrossSessionAndReplayedRequests() {
        var now = 1_000L
        val access = SanctuaryTemporaryAccess70 { now }
        val denied = access.request("first", "com.example.blocked")
        assertFalse(access.complete(denied, "first", confirmed = false))
        assertFalse(access.complete(denied, "first", confirmed = true))
        val switched = access.request("first", "com.example.blocked")
        assertFalse(access.complete(switched, "second", confirmed = true))
        val expired = access.request("first", "com.example.blocked")
        now += 60_001
        assertFalse(access.complete(expired, "first", confirmed = true))
        assertTrue(access.packages("first").isEmpty())
    }

    @Test fun explicitPackageGrantAndPauseExpireAndNeverCrossSessions() {
        var now = 1_000L
        val access = SanctuaryTemporaryAccess70 { now }
        val app = access.request("first", "com.example.blocked")
        assertTrue(access.complete(app, "first", confirmed = true))
        assertFalse(access.complete(app, "first", confirmed = true))
        assertEquals(setOf("com.example.blocked"), access.packages("first"))
        assertFalse(access.paused("first"))
        val pause = access.request("first", null)
        assertTrue(access.complete(pause, "first", confirmed = true))
        assertTrue(access.paused("first"))
        assertFalse(access.paused("second"))
        assertTrue(access.packages("second").isEmpty())
        now += SanctuaryTemporaryAccess70.ACCESS_DURATION
        assertFalse(access.paused("first"))
        assertTrue(access.packages("first").isEmpty())
    }
}
