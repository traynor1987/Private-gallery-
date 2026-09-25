package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalResourceGuardTest {
    private val safe = DeviceResources(36, true, true, 12 * ModelCatalog.GIB, 5 * ModelCatalog.GIB, false, false)
    @Test fun pressureCancelsWorkAndCompletesCleanupBeforeGracefulFailure() = runBlocking {
        val buffer = byteArrayOf(1, 2, 3)
        var entered = false
        var reported = false
        val failure = runCatching {
            withTimeout(3000) {
                withLocalResourceGuard({ safe.copy(lowMemory = entered) }, { reported = true }, intervalMillis = 1) {
                    try { entered = true; awaitCancellation() } finally { buffer.fill(0) }
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is LocalResourceLimit)
        assertEquals(LocalStopReason.ANDROID_LOW_MEMORY, (failure as LocalResourceLimit).reason)
        assertTrue(reported)
        assertTrue(buffer.all { it == 0.toByte() })
    }
    @Test fun ownerCancellationRemainsCancellationAndCleansUp() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var wiped = false
        var reported = false
        val job = launch {
            withLocalResourceGuard({ safe }, { reported = true }, intervalMillis = 1) {
                try { entered.complete(Unit); awaitCancellation() } finally { wiped = true }
            }
        }
        withTimeout(3000) { entered.await(); job.cancelAndJoin() }
        assertTrue(job.isCancelled); assertTrue(wiped); assertFalse(reported)
    }
    @Test fun thermalProtectionIsReportedSeparatelyFromMemory() = runBlocking {
        var started = false
        val failure = runCatching { withTimeout(3000) {
            withLocalResourceGuard({ safe.copy(tooHot = started) }, {}, intervalMillis = 1) {
                started = true; awaitCancellation()
            }
        } }.exceptionOrNull()
        assertEquals(LocalStopReason.THERMAL, (failure as LocalResourceLimit).reason)
    }
}
