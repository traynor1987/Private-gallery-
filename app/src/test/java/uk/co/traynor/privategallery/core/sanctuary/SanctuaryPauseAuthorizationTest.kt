package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.*
import org.junit.Test

class SanctuaryPauseAuthorizationTest {
    @Test fun deniesUnconfirmedExpiredCrossSessionAndReplayedRequests() {
        var now = 1_000L
        val access = SanctuaryPauseAuthorization { now }
        val denied = access.request("first")
        assertFalse(access.complete(denied, "first", confirmed = false))
        assertFalse(access.complete(denied, "first", confirmed = true))
        val switched = access.request("first")
        assertFalse(access.complete(switched, "second", confirmed = true))
        val expired = access.request("first")
        now += 60_000
        assertFalse(access.complete(expired, "first", confirmed = true))
        assertFalse(access.isPaused("first"))
    }

    @Test fun newerPromptInvalidatesEarlierCredentialCallback() {
        val authorization = SanctuaryPauseAuthorization { 1_000L }
        val older = authorization.request("first")
        val newer = authorization.request("first")
        assertFalse(authorization.complete(older, "first", confirmed = true))
        assertFalse(authorization.isPaused("first"))
        assertTrue(authorization.complete(newer, "first", confirmed = true))
    }

    @Test fun pauseExpiresAndNeverCrossesSessions() {
        var now = 1_000L
        val authorization = SanctuaryPauseAuthorization { now }
        val request = authorization.request("first")
        assertTrue(authorization.complete(request, "first", confirmed = true))
        assertFalse(authorization.complete(request, "first", confirmed = true))
        assertTrue(authorization.isPaused("first"))
        assertFalse(authorization.isPaused("second"))
        now += 299_999L
        assertTrue(authorization.isPaused("first"))
        now += 1L
        assertFalse(authorization.isPaused("first"))
    }
}
