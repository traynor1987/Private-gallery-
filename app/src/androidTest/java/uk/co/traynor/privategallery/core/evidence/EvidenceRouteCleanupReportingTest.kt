package uk.co.traynor.privategallery.core.evidence

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EvidenceRouteCleanupReportingTest {
    @Test fun reportsRouteFailureWithCleanupFailureAttached() = runBlocking {
        val routeFailure = IllegalStateException("route")
        val cleanupFailure = IllegalStateException("cleanup")
        val observed = runCatching {
            try {
                throw routeFailure
            } finally {
                retireRouteClock179(null, routeFailure) { throw cleanupFailure }
            }
        }.exceptionOrNull()
        assertSame(routeFailure, observed)
        assertEquals(listOf(cleanupFailure), routeFailure.suppressed.toList())
    }

    @Test fun cleanupFailureAloneStillFails() = runBlocking {
        val cleanupFailure = IllegalStateException("cleanup")
        val observed = runCatching {
            retireRouteClock179(null) { throw cleanupFailure }
        }.exceptionOrNull()
        assertSame(cleanupFailure, observed)
    }
}
