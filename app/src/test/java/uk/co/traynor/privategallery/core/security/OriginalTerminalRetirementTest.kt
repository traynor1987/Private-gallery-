package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

/** Real completion ordering with the existing ticket monitor paused after owner hooks return. */
class OriginalTerminalRetirementTest {
    @Test fun normalCloseCannotReturnBeforeItsActualTicketMarkerAndRecycle() {
        val pool = ReleasePool(1); val ticket = pool.reserve(Any())
        val reservation = ReleaseReservation(listOf(ticket))
        val ticketGate = ReleaseTicket::class.java.getDeclaredField("gate").apply { isAccessible = true }.get(ticket)
        val holdMarker = CountDownLatch(1); val markerHeld = CountDownLatch(1); val allowMarker = CountDownLatch(1)
        val blockerDone = CountDownLatch(1); val closeDone = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val blocker = thread(isDaemon = true) {
            try {
                assertTrue(holdMarker.await(5, TimeUnit.SECONDS))
                synchronized(ticketGate) { markerHeld.countDown(); assertTrue(allowMarker.await(5, TimeUnit.SECONDS)) }
            } catch (problem: Throwable) { failure.set(problem) } finally { blockerDone.countDown() }
        }
        reservation.onRetirementAccounting(onAcknowledged = {
            holdMarker.countDown(); assertTrue(markerHeld.await(5, TimeUnit.SECONDS))
        }, accounting = {})
        val actual = AutoCloseable {}
        reservation.construct { attach(0, actual) }
        val owned = OwnedResource(actual, reservation)
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val guard = ScopedIoGuard(operation, PrimaryScope.READ)
        val closer = thread(isDaemon = true) {
            try { guard.retire(owned) } catch (problem: Throwable) { failure.set(problem) }
            finally { closeDone.countDown() }
        }
        try {
            assertTrue(markerHeld.await(5, TimeUnit.SECONDS))
            // Native invocation and owning hooks really returned; the actual final marker
            // and physical capacity are still held. Do not query ticket-gated getters here.
            assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS))
            assertEquals(1, pool.occupied)
            assertFalse("normal close must await its original terminal state", closeDone.await(250, TimeUnit.MILLISECONDS))
            allowMarker.countDown(); assertTrue(closeDone.await(5, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("original terminal retirement failed", it) }
            assertTrue(reservation.successful); assertEquals(0, pool.occupied)
        } finally {
            allowMarker.countDown(); assertTrue(closeDone.await(5, TimeUnit.SECONDS)); assertTrue(blockerDone.await(5, TimeUnit.SECONDS))
            closer.interrupt(); blocker.interrupt(); operation.close(); authority.revoke()
        }
    }
    @Test fun normalCloseWaitsOnlyForItsOwnOriginalAndLeavesUnrelatedLiveResourceAlone() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val guard = ScopedIoGuard(operation, PrimaryScope.READ); var otherClosed = 0
        val unrelated = operation.createOwned { attach -> AutoCloseable { otherClosed++ }.also(attach) }
        val input = guard.input { java.io.ByteArrayInputStream(byteArrayOf(1)) }
        input.close(); assertEquals(0, otherClosed); assertFalse(unrelated.retirement.isComplete)
        unrelated.close(); operation.close(); authority.revoke()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
        assertTrue(authority.cleanupComplete); assertEquals(1, otherClosed)
    }
}
