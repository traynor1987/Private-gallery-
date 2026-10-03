package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

/** Tests actual teardown dispatch; neither pool sizes nor a model establish acknowledgement. */
class ReleaseCapacityTest {
    @Test fun `capacity denies before another factory can run`() {
        val pool = ReleasePool(2)
        val owner = Any()
        val first = pool.reserve(owner)
        val second = pool.reserve(owner)
        assertThrows(IllegalStateException::class.java) { pool.reserve(owner) }
        assertEquals(2, pool.occupied)
        first.release(); second.release()
        waitFor { pool.occupied == 0 }
    }

    @Test fun `blocked callbacks cannot starve admitted independent unblocker`() {
        val pool = ReleasePool(3)
        val owner = Any()
        val blocked = CountDownLatch(2)
        val unblock = CountDownLatch(1)
        val tickets = List(2) {
            pool.reserve(owner).also { ticket ->
                ticket.construct { attach ->
                    AutoCloseable { blocked.countDown(); check(unblock.await(5, TimeUnit.SECONDS)) }.also(attach)
                }
            }
        }
        val last = pool.reserve(owner)
        last.construct { attach -> AutoCloseable { unblock.countDown() }.also(attach) }
        tickets.forEach { it.release() }
        assertTrue(blocked.await(5, TimeUnit.SECONDS))
        last.release()
        assertTrue(unblock.await(1, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
    }

    @Test fun `early native acknowledgement does not recycle still running release`() {
        val pool = ReleasePool(1)
        val entered = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val ticket = pool.reserve(Any())
        ticket.construct { attach -> object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletionStage<Unit> {
                entered.countDown()
                check(returned.await(5, TimeUnit.SECONDS))
                return CompletableFuture.completedFuture(Unit)
            }
        }.also(attach) }
        ticket.release()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertEquals(1, pool.occupied)
        assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
        returned.countDown()
        waitFor { pool.occupied == 0 }
    }

    @Test fun `returned close still waits for positive native acknowledgement`() {
        val pool = ReleasePool(1)
        val requested = CountDownLatch(1)
        val acknowledgement = CompletableFuture<Unit>()
        val ticket = pool.reserve(Any())
        ticket.construct { attach -> object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletionStage<Unit> {
                requested.countDown(); return acknowledgement
            }
        }.also(attach) }
        ticket.release()
        assertTrue(requested.await(5, TimeUnit.SECONDS))
        assertFalse(ticket.finished)
        acknowledgement.complete(Unit)
        waitFor { pool.occupied == 0 }
    }

    @Test fun `revocation releases attached partial child before constructor returns`() {
        val pool = ReleasePool(1)
        val attached = CountDownLatch(1)
        val released = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val ticket = pool.reserve(Any())
        val factory = thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    ticket.construct { attach ->
                        val child = AutoCloseable { released.countDown() }
                        attach(child)
                        attached.countDown()
                        check(released.await(5, TimeUnit.SECONDS))
                        child
                    }
                }
            } finally { finished.countDown() }
        }
        assertTrue(attached.await(5, TimeUnit.SECONDS))
        ticket.release()
        assertTrue(released.await(1, TimeUnit.SECONDS))
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        factory.join(1000)
        waitFor { pool.occupied == 0 }
    }

    @Test fun `failed release retains finite obligation and never becomes success`() {
        val pool = ReleasePool(1)
        val ticket = pool.reserve(Any())
        ticket.construct { attach -> AutoCloseable { throw java.io.IOException("release failed") }.also(attach) }
        ticket.release()
        waitFor { ticket.finished }
        assertTrue(ticket.failed)
        assertEquals(1, pool.occupied)
        assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
    }

    @Test fun `duplicate retirement invokes one actual close`() {
        val pool = ReleasePool(1)
        val count = java.util.concurrent.atomic.AtomicInteger()
        val ticket = pool.reserve(Any())
        ticket.construct { attach -> AutoCloseable { count.incrementAndGet() }.also(attach) }
        repeat(20) { ticket.release() }
        waitFor { pool.occupied == 0 }
        assertEquals(1, count.get())
    }

    @Test fun `known unattached factory result is rejected but still cleaned`() {
        val pool = ReleasePool(1)
        val closed = CountDownLatch(1)
        val ticket = pool.reserve(Any())
        assertThrows(IllegalStateException::class.java) {
            ticket.construct { AutoCloseable { closed.countDown() } }
        }
        assertTrue("a known returned resource cannot disappear on contract rejection", closed.await(1, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
    }

    @Test fun `callback interrupt cannot kill reserved physical release worker`() {
        val pool = ReleasePool(1)
        val first = pool.reserve(Any())
        first.construct { attach -> AutoCloseable { Thread.currentThread().interrupt() }.also(attach) }
        first.release()
        waitFor { pool.occupied == 0 }
        val closed = CountDownLatch(1)
        val next = pool.reserve(Any())
        next.construct { attach -> AutoCloseable { closed.countDown() }.also(attach) }
        next.release()
        assertTrue("the original reserved worker must survive callback interrupts", closed.await(1, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
    }

    @Test fun `manifest admission is atomic when insufficient slots remain`() {
        val pool = ReleasePool(3)
        val original = pool.reserve(Any())
        assertThrows(IllegalStateException::class.java) { pool.reserveAll(Any(), 3) }
        assertEquals("denied manifest must consume no partial capacity", 1, pool.occupied)
        val accepted = pool.reserveAll(Any(), 2)
        assertEquals(3, pool.occupied)
        accepted.forEach { it.release() }
        original.release()
        waitFor { pool.occupied == 0 }
    }

    @Test fun `manifest constructor retains unused slots until actual construction ends`() {
        val pool = ReleasePool(2)
        val entered = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val attachedReleased = CountDownLatch(1)
        val factoryFinished = CountDownLatch(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 2))
        thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    reservation.construct {
                        attach(0, AutoCloseable { attachedReleased.countDown() })
                        entered.countDown()
                        check(returned.await(5, TimeUnit.SECONDS))
                        Unit
                    }
                }
            } finally { factoryFinished.countDown() }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        reservation.release()
        assertTrue(attachedReleased.await(1, TimeUnit.SECONDS))
        assertEquals("unused manifest entries remain original construction obligations", 2, pool.occupied)
        returned.countDown()
        assertTrue(factoryFinished.await(5, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
    }

    @Test fun `normal manifest retirement releases exact children once and removes all obligations`() {
        val pool = ReleasePool(2)
        val closes = java.util.concurrent.atomic.AtomicInteger()
        repeat(20) {
            val reservation = ReleaseReservation(pool.reserveAll(Any(), 2))
            reservation.construct {
                attach(0, AutoCloseable { closes.incrementAndGet() })
                attach(1, AutoCloseable { closes.incrementAndGet() })
            }
            repeat(3) { reservation.release() }
            waitFor { reservation.successful && pool.occupied == 0 }
        }
        assertEquals(40, closes.get())
    }

    @Test fun `rejected repeated construction cannot finish the original blocked constructor`() {
        val pool = ReleasePool(1)
        val original = ReleaseReservation(pool.reserveAll(Any(), 1))
        val attached = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val returnOriginal = CountDownLatch(1)
        val finished = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    original.construct {
                        attach(0, AutoCloseable { closed.countDown() })
                        attached.countDown()
                        check(returnOriginal.await(5, TimeUnit.SECONDS))
                    }
                }
            } finally { finished.countDown() }
        }
        try {
            assertTrue(attached.await(5, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { original.construct { fail("second factory must never run") } }
            original.release()
            assertTrue(closed.await(1, TimeUnit.SECONDS))
            assertEquals("second invocation cannot end first invocation's construction", 1, pool.occupied)
        } finally { returnOriginal.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS)) }
        waitFor { pool.occupied == 0 }
    }

    @Test fun `reentrant rejected construction preserves the enclosing factory ownership`() {
        val pool = ReleasePool(1)
        val original = ReleaseReservation(pool.reserveAll(Any(), 1))
        val closes = java.util.concurrent.atomic.AtomicInteger()
        original.construct {
            attach(0, AutoCloseable { closes.incrementAndGet() })
            assertThrows(IllegalStateException::class.java) { construct { fail("nested factory cannot run") } }
            assertFalse("nested rejection must not retire outer resources", retiring)
        }
        original.release()
        waitFor { pool.occupied == 0 }
        assertEquals(1, closes.get())
    }

    @Test fun `late group accounting is invoked once for whole exact manifest`() {
        val pool = ReleasePool(2)
        val original = ReleaseReservation(pool.reserveAll(Any(), 2))
        original.construct {
            attach(0, AutoCloseable {})
            attach(1, AutoCloseable {})
        }
        original.release()
        waitFor { original.successful && pool.occupied == 0 }
        val notifications = java.util.concurrent.atomic.AtomicInteger()
        original.onSuccessfulRetirement { notifications.incrementAndGet() }
        assertEquals("late hook acknowledges one manifest, not every child again", 1, notifications.get())
    }

    @Test fun `simultaneous final child acknowledgements notify the exact manifest once`() {
        val pool = ReleasePool(2)
        val original = ReleaseReservation(pool.reserveAll(Any(), 2))
        val futures = List(2) { CompletableFuture<Unit>() }
        val requested = CountDownLatch(2)
        original.construct {
            repeat(2) { index -> attach(index, object : AcknowledgedCloseable {
                override fun closeAcknowledged(): CompletionStage<Unit> {
                    requested.countDown(); return futures[index]
                }
            }) }
        }
        val notifications = java.util.concurrent.atomic.AtomicInteger()
        original.onSuccessfulRetirement { notifications.incrementAndGet() }
        original.release()
        assertTrue(requested.await(5, TimeUnit.SECONDS))
        val workers = mutableListOf<Thread>()
        // Hold the actual pool bookkeeping lock: each ticket reaches terminal before either
        // can recycle and invoke accounting. This forces both group callbacks to see success.
        synchronized(poolGate(pool)) {
            repeat(2) { index -> workers += thread(isDaemon = true) { futures[index].complete(Unit) } }
            waitFor { original.successful }
        }
        workers.forEach { it.join(1000) }
        assertEquals(1, notifications.get())
    }

    @Test fun `late hook racing terminal recycle notifies the original ticket once`() {
        val pool = ReleasePool(1)
        val ticket = pool.reserve(Any())
        val acknowledgement = CompletableFuture<Unit>()
        val requested = CountDownLatch(1)
        ticket.construct { attach -> object : AcknowledgedCloseable {
            override fun closeAcknowledged(): CompletionStage<Unit> {
                requested.countDown(); return acknowledgement
            }
        }.also(attach) }
        ticket.release()
        assertTrue(requested.await(5, TimeUnit.SECONDS))
        val notifications = java.util.concurrent.atomic.AtomicInteger()
        lateinit var completion: Thread
        synchronized(poolGate(pool)) {
            completion = thread(isDaemon = true) { acknowledgement.complete(Unit) }
            waitFor { ticket.successful }
            ticket.onSuccessfulRetirement { notifications.incrementAndGet() }
        }
        completion.join(1000)
        waitFor { pool.occupied == 0 }
        assertEquals(1, notifications.get())
    }

    @Test fun `allocation failure publishes no unreachable partial manifest`() {
        val allocations = java.util.concurrent.atomic.AtomicInteger()
        val pool = ReleasePool(2) { owner, slot ->
            if (allocations.incrementAndGet() == 2) throw OutOfMemoryError("synthetic ticket allocation failure")
            ReleaseTicket(owner, slot)
        }
        assertThrows(OutOfMemoryError::class.java) { pool.reserveAll(Any(), 2) }
        assertEquals("no original ticket escaped, so no partial ticket may remain installed", 0, pool.occupied)
        val accepted = pool.reserveAll(Any(), 2)
        accepted.forEach { it.release() }
        waitFor { pool.occupied == 0 }
    }

    @Test fun `release dispatch node funding fails before resource factory admission`() {
        val pool = ReleasePool(1, allocateInvocation = { _, _ -> throw OutOfMemoryError("synthetic release node funding failure") })
        var reservation: ReleaseTicket? = null
        try {
            assertThrows(OutOfMemoryError::class.java) { reservation = pool.reserve(Any()) }
        } finally { reservation?.release() }
        assertEquals("failed pre-funding cannot install an unreachable resource token", 0, pool.occupied)
    }

    /** Test-only scheduling of the real implementation's gate; no production bypass flags. */
    @Test fun `lost original worker denies construction before any resource exists`() {
        val pool = ReleasePool(1)
        val ticket = pool.reserve(Any())
        ticket.workerFailed()
        var created = false
        assertThrows(IllegalStateException::class.java) {
            ticket.construct { attach -> created = true; AutoCloseable {}.also(attach) }
        }
        assertFalse("a failed original worker cannot fund a new factory", created)
        assertTrue(ticket.failed)
        assertEquals(1, pool.occupied)
    }

    @Test fun `lost manifest worker denies the whole factory before any child exists`() {
        val pool = ReleasePool(2)
        val tickets = pool.reserveAll(Any(), 2)
        val reservation = ReleaseReservation(tickets)
        tickets[1].workerFailed()
        var created = false
        assertThrows(IllegalStateException::class.java) {
            reservation.construct { created = true; attach(0, AutoCloseable {}) }
        }
        assertFalse("one unavailable original slot denies the complete constructor", created)
        assertFalse(reservation.successful)
        waitFor { pool.occupied == 1 }
    }

    @Test fun `duplicate actual manifest child retains one original close authority`() {
        val pool = ReleasePool(2)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 2))
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val actual = AutoCloseable { closes.incrementAndGet() }
        val failure = runCatching {
            reservation.construct {
                attach(0, actual)
                attach(1, actual)
            }
        }.exceptionOrNull()
        reservation.release()
        waitFor { pool.occupied == 0 }
        assertTrue("duplicate reference cannot acquire a second release action", failure is IllegalStateException)
        assertEquals("only its original child token may release the actual resource", 1, closes.get())
    }

    @Test fun `duplicate factory result retains one original close authority`() {
        val pool = ReleasePool(2)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 2))
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val actual = AutoCloseable { closes.incrementAndGet() }
        val failure = runCatching {
            reservation.construct {
                attach(0, actual)
                verifyResult(1, actual)
            }
        }.exceptionOrNull()
        reservation.release()
        waitFor { pool.occupied == 0 }
        assertTrue("duplicate result cannot acquire a second release action", failure is IllegalStateException)
        assertEquals("verification retains its original child token", 1, closes.get())
    }

    @Test fun `factory result verification accepts its original attached child`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val actual = AutoCloseable { closes.incrementAndGet() }
        reservation.construct {
            attach(0, actual)
            verifyResult(0, actual)
        }
        assertEquals(0, closes.get())
        reservation.release()
        waitFor { pool.occupied == 0 }
        assertEquals(1, closes.get())
    }

    @Test fun `unattached reservation factory result remains funded for cleanup`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val actual = AutoCloseable { closes.incrementAndGet() }
        assertThrows(IllegalStateException::class.java) {
            reservation.construct { verifyResult(0, actual) }
        }
        waitFor { pool.occupied == 0 }
        assertEquals(1, closes.get())
    }

    private fun poolGate(pool: ReleasePool): Any = ReleasePool::class.java.getDeclaredField("gate").let {
        it.isAccessible = true
        it.get(pool)
    }

    private fun waitFor(check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < end) Thread.yield()
        assertTrue("real release did not acknowledge", check())
    }
}
