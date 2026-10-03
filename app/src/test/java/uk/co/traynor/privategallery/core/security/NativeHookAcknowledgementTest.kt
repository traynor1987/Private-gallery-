package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import org.junit.Assert.*
import org.junit.Test

/** Separate actual inline acknowledgement from the return of hook installation. */
class NativeHookAcknowledgementTest {
    @Test fun `inline native acknowledgement during blocked hook installation cannot recycle`() {
        val pool = ReleasePool(1)
        val inlineDelivered = CountDownLatch(1); val returnHook = CountDownLatch(1)
        val stage = object : CompletableFuture<Unit>() {
            override fun whenComplete(action: BiConsumer<in Unit, in Throwable>): CompletableFuture<Unit> {
                val result = super.whenComplete(action)
                inlineDelivered.countDown()
                check(returnHook.await(5, TimeUnit.SECONDS))
                return result
            }
        }
        stage.complete(Unit)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        reservation.onRetirementAccounting {}
        reservation.construct { attach(0, object : AcknowledgedCloseable {
            override fun closeAcknowledged() = stage
        }) }
        reservation.release()
        try {
            assertTrue(inlineDelivered.await(5, TimeUnit.SECONDS))
            assertFalse(reservation.successful); assertFalse(reservation.retirement.isComplete)
            assertEquals(1, pool.occupied)
            assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
        } finally { returnHook.countDown() }
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS))
        waitFor { reservation.successful && pool.occupied == 0 }
    }

    @Test fun `inline native acknowledgement followed by hook failure remains failed`() {
        val pool = ReleasePool(1); val inlineDelivered = CountDownLatch(1)
        val stage = object : CompletableFuture<Unit>() {
            override fun whenComplete(action: BiConsumer<in Unit, in Throwable>): CompletableFuture<Unit> {
                super.whenComplete(action)
                inlineDelivered.countDown()
                throw IOException("native completion-hook installation failed")
            }
        }
        stage.complete(Unit)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        reservation.onRetirementAccounting {}
        reservation.construct { attach(0, object : AcknowledgedCloseable {
            override fun closeAcknowledged() = stage
        }) }
        reservation.release()
        assertTrue(inlineDelivered.await(5, TimeUnit.SECONDS))
        waitFor { reservation.failed }
        assertFalse(reservation.successful); assertFalse(reservation.retirement.isComplete)
        assertEquals(1, pool.occupied)
        assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < deadline) Thread.yield()
        assertTrue("actual retirement did not acknowledge", check())
    }
}
