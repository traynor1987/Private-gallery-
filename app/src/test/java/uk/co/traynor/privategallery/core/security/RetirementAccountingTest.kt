package uk.co.traynor.privategallery.core.security

import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class RetirementAccountingTest {
    @Test fun `nonfinal prepublication failure cannot race a successful manifest acknowledgement`() {
        val pool = ReleasePool(2); val tickets = pool.reserveAll(Any(), 2)
        val reservation = ReleaseReservation(tickets)
        val publications = AtomicInteger()
        reservation.onRetirementAccounting(onAcknowledged = { publications.incrementAndGet() }) {}
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val field = ReleaseTicket::class.java.getDeclaredField("owningPublication").apply { isAccessible = true }
        field.set(tickets[0], {
            entered.countDown(); check(finish.await(5, TimeUnit.SECONDS))
            throw OutOfMemoryError("nonfinal generated publication scan allocation failed")
        })
        val requested = CountDownLatch(2); val futures = List(2) { CompletableFuture<Unit>() }
        reservation.construct { repeat(2) { index -> attach(index, object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletableFuture<Unit> { requested.countDown(); return futures[index] }
        }) } }
        reservation.release(); assertTrue(requested.await(5, TimeUnit.SECONDS))
        val first = thread(isDaemon = true) { futures[0].complete(Unit) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); futures[1].complete(Unit)
            assertFalse("no publication while any original prepublication action can still fail", reservation.retirement.isComplete)
            assertFalse(reservation.successful); assertEquals(0, publications.get())
        } finally { finish.countDown(); first.join(5000) }
        waitFor { reservation.failed }
        assertFalse(reservation.retirement.isComplete); assertEquals(0, publications.get())
        assertEquals(1, pool.occupied)
    }

    @Test fun `nonfinal owning callback failure cannot follow successful manifest acknowledgement`() {
        val pool = ReleasePool(2); val tickets = pool.reserveAll(Any(), 2)
        val reservation = ReleaseReservation(tickets)
        val publications = AtomicInteger()
        reservation.onRetirementAccounting(onAcknowledged = { publications.incrementAndGet() }) {}
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val field = ReleaseTicket::class.java.getDeclaredField("owningRetirement").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(tickets[0]) as () -> Unit
        field.set(tickets[0], {
            original(); entered.countDown(); check(finish.await(5, TimeUnit.SECONDS))
            throw OutOfMemoryError("nonfinal owning bookkeeping failed after mutation")
        })
        val requested = CountDownLatch(2); val futures = List(2) { CompletableFuture<Unit>() }
        reservation.construct { repeat(2) { index -> attach(index, object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletableFuture<Unit> { requested.countDown(); return futures[index] }
        }) } }
        reservation.release(); assertTrue(requested.await(5, TimeUnit.SECONDS))
        val first = thread(isDaemon = true) { futures[0].complete(Unit) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); futures[1].complete(Unit)
            assertFalse("other children cannot publish while original bookkeeping is still running", reservation.retirement.isComplete)
            assertFalse(reservation.successful)
            assertEquals(0, publications.get())
        } finally { finish.countDown(); first.join(5000) }
        waitFor { reservation.failed }
        assertFalse("a later owning failure must never follow an accepted retirement", reservation.retirement.isComplete)
        assertFalse(reservation.successful); assertEquals(0, publications.get())
        assertEquals(1, pool.occupied)
        assertThrows(IllegalStateException::class.java) { pool.reserveAll(Any(), 2) }
    }

    @Test fun `late observer during blocked owning bookkeeping waits and delivers exactly once`() {
        val pool = ReleasePool(1); val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val entered = CountDownLatch(1); val finish = CountDownLatch(1); val observed = CountDownLatch(1)
        val observations = AtomicInteger()
        reservation.onRetirementAccounting { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }
        reservation.construct { attach(0, AutoCloseable {}) }; reservation.release()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            reservation.onSuccessfulRetirement { observations.incrementAndGet(); observed.countDown() }
            assertEquals(0, observations.get()); assertFalse(reservation.retirement.isComplete)
        } finally { finish.countDown() }
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
        assertTrue("pending observation must not be consumed before owning success", observed.await(1, TimeUnit.SECONDS))
        assertEquals(1, observations.get())
    }

    @Test fun `late observation of strict owner is delivered without altering owning acknowledgement`() {
        val pool = ReleasePool(1); val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        reservation.onRetirementAccounting {}
        reservation.construct { attach(0, AutoCloseable {}) }; reservation.release()
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
        var observations = 0
        reservation.onSuccessfulRetirement { observations++ }
        assertEquals(1, observations)
        assertTrue(reservation.retirement.isComplete); assertTrue(reservation.successful)
    }

    @Test fun `late observational hook cannot publish an owning retirement acknowledgement`() {
        val pool = ReleasePool(1); val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        reservation.construct { attach(0, AutoCloseable {}) }; reservation.release()
        waitFor { reservation.successful && pool.occupied == 0 }
        var observed = false
        reservation.onSuccessfulRetirement { observed = true }
        assertTrue(observed); assertFalse(reservation.retirement.isComplete)
    }

    @Test fun `last owning group callback must return before aggregate publication`() {
        val pool = ReleasePool(1); val tickets = pool.reserveAll(Any(), 1)
        val reservation = ReleaseReservation(tickets); reservation.onRetirementAccounting {}
        val blocked = CountDownLatch(1); val finish = CountDownLatch(1)
        val field = ReleaseTicket::class.java.getDeclaredField("owningRetirement").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(tickets.single()) as () -> Unit
        field.set(tickets.single(), { original(); blocked.countDown(); check(finish.await(5, TimeUnit.SECONDS)) })
        reservation.construct { attach(0, AutoCloseable {}) }; reservation.release()
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS))
            assertFalse(reservation.retirement.isComplete); assertFalse(reservation.successful)
            assertEquals(1, pool.occupied)
            assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
        } finally { finish.countDown() }
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
    }

    @Test fun `last strict callback keeps its worker and whole manifest pending until return`() {
        val pool = ReleasePool(2); val tickets = pool.reserveAll(Any(), 2)
        val reservation = ReleaseReservation(tickets); reservation.onRetirementAccounting {}
        val blocked = CountDownLatch(1); val finish = CountDownLatch(1)
        blockStrictReturn(tickets[1], blocked, finish)
        val requested = CountDownLatch(2); val futures = List(2) { CompletableFuture<Unit>() }
        reservation.construct { repeat(2) { index -> attach(index, object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletableFuture<Unit> { requested.countDown(); return futures[index] }
        }) } }
        reservation.release(); assertTrue(requested.await(5, TimeUnit.SECONDS)); futures[0].complete(Unit)
        val last = thread(isDaemon = true) { futures[1].complete(Unit) }
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS))
            assertFalse(reservation.retirement.isComplete); assertFalse(reservation.successful)
            assertEquals(1, pool.occupied)
            assertThrows(IllegalStateException::class.java) { pool.reserveAll(Any(), 2) }
        } finally { finish.countDown(); last.join(5000) }
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
    }


    @Test fun `last child cannot acknowledge while first strict callback has not returned`() {
        val pool = ReleasePool(2); val tickets = pool.reserveAll(Any(), 2)
        val reservation = ReleaseReservation(tickets); reservation.onRetirementAccounting {}
        val blocked = CountDownLatch(1); val finish = CountDownLatch(1)
        blockStrictReturn(tickets[0], blocked, finish)
        val requested = CountDownLatch(2); val futures = List(2) { CompletableFuture<Unit>() }
        reservation.construct { repeat(2) { index -> attach(index, object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletableFuture<Unit> { requested.countDown(); return futures[index] }
        }) } }
        reservation.release(); assertTrue(requested.await(5, TimeUnit.SECONDS))
        val first = thread(isDaemon = true) { futures[0].complete(Unit) }
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS)); futures[1].complete(Unit)
            assertFalse("all actual native acknowledgements do not bypass a pending strict return", reservation.retirement.isComplete)
            assertFalse(reservation.successful)
            assertEquals(1, pool.occupied)
            assertThrows(IllegalStateException::class.java) { pool.reserveAll(Any(), 2) }
        } finally { finish.countDown(); first.join(5000) }
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
    }

    @Test fun `owning registration after construction or release is rejected`() {
        val pool = ReleasePool(1); val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        reservation.construct { attach(0, AutoCloseable {}) }
        try { assertThrows(IllegalStateException::class.java) { reservation.onRetirementAccounting {} } }
        finally { reservation.release() }
        waitFor { pool.occupied == 0 }
        assertThrows(IllegalStateException::class.java) { reservation.onRetirementAccounting {} }
    }

    private fun blockStrictReturn(ticket: ReleaseTicket, entered: CountDownLatch, finish: CountDownLatch) {
        val field = ReleaseTicket::class.java.getDeclaredField("retirementAccounting").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(ticket) as () -> Unit
        field.set(ticket, { original(); entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) })
    }

    @Test fun `failed original registry accounting retains quota and never acknowledges retirement`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val attempted = CountDownLatch(1)
        reservation.onRetirementAccounting {
            try { throw OutOfMemoryError("original registry retirement") }
            finally { attempted.countDown() }
        }
        reservation.construct { attach(0, AutoCloseable {}) }
        reservation.release()
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!reservation.failed && pool.occupied != 0 && System.nanoTime() < deadline) Thread.yield()
        assertFalse("failed bookkeeping is never a successful original obligation", reservation.successful)
        assertTrue(reservation.failed)
        assertFalse(reservation.retirement.isComplete)
        assertEquals("failed original accounting retains its funded release slot", 1, pool.occupied)
        assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < deadline) Thread.yield()
        assertTrue("actual retirement did not acknowledge", check())
    }
}
