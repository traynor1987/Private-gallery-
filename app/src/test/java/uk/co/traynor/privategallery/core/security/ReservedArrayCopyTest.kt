package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ReservedArrayCopyTest {
    @Test fun `result verification cannot replace an in progress actual array claim`() {
        val pool = ReleasePool(1)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val actual = AtomicReference<ByteArray>()
        val otherClosed = AtomicInteger()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1), copyArray = { source ->
            entered.countDown(); check(finish.await(5, TimeUnit.SECONDS))
            source.copyOf().also(actual::set)
        })
        thread(isDaemon = true) {
            try { reservation.construct { copyBytes(0, byteArrayOf(7, 9)) } }
            catch (_: IllegalStateException) { /* Baseline loses the actual array here. */ }
            finally { reservation.release(); returned.countDown() }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            assertThrows(IllegalStateException::class.java) {
                reservation.verifyResult(0, AutoCloseable { otherClosed.incrementAndGet() })
            }
            assertEquals(1, pool.occupied)
        } finally { finish.countDown() }
        assertTrue(returned.await(5, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
        assertArrayEquals("the legitimate copy must retain its original wipe obligation", byteArrayOf(0, 0), actual.get())
        assertEquals("verification must not acquire an unrelated result in a claimed slot", 0, otherClosed.get())
    }

    @Test fun `same ticket attached result remains valid after claiming its child`() {
        val pool = ReleasePool(1)
        val closed = AtomicInteger()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val actual = AutoCloseable { closed.incrementAndGet() }
        reservation.construct { attach(0, actual); verifyResult(0, actual) }
        reservation.release()
        waitFor { pool.occupied == 0 }
        assertEquals(1, closed.get())
    }

    @Test fun `unclaimed malformed result retains its funded cleanup obligation`() {
        val pool = ReleasePool(1)
        val closed = AtomicInteger()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        assertThrows(IllegalStateException::class.java) {
            reservation.construct { verifyResult(0, AutoCloseable { closed.incrementAndGet() }) }
        }
        waitFor { pool.occupied == 0 }
        assertEquals(1, closed.get())
    }

    @Test fun `release obligation allocation fails before any actual array copy`() {
        val pool = ReleasePool(1)
        val copies = AtomicInteger()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1),
            allocateArrayRelease = { throw OutOfMemoryError("release obligation allocation") },
            copyArray = { source -> copies.incrementAndGet(); source.copyOf() })
        assertThrows(OutOfMemoryError::class.java) { reservation.construct { copyBytes(0, byteArrayOf(7, 9)) } }
        waitFor { pool.occupied == 0 }
        assertEquals("no actual buffer exists without a preallocated release obligation", 0, copies.get())
    }

    @Test fun `normal array retirement wipes exact copy and leaves original source unchanged`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val source = byteArrayOf(7, 9)
        val copy = reservation.construct { copyBytes(0, source) }
        assertNotSame(source, copy)
        assertArrayEquals(byteArrayOf(7, 9), copy)
        reservation.release(); reservation.release()
        waitFor { pool.occupied == 0 }
        assertArrayEquals(byteArrayOf(0, 0), copy)
        assertArrayEquals(byteArrayOf(7, 9), source)
    }

    @Test fun `late array copy remains original obligation after revoke during allocation`() {
        val pool = ReleasePool(1)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val actual = AtomicReference<ByteArray>()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1), copyArray = { source ->
            entered.countDown(); check(finish.await(5, TimeUnit.SECONDS))
            source.copyOf().also(actual::set)
        })
        thread(isDaemon = true) {
            try { assertThrows(IllegalStateException::class.java) { reservation.construct { copyBytes(0, byteArrayOf(7, 9)) } } }
            finally { returned.countDown() }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        reservation.release()
        try {
            assertEquals(1, pool.occupied)
            assertFalse(reservation.successful)
            assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
        } finally { finish.countDown() }
        assertTrue(returned.await(5, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
        assertArrayEquals(byteArrayOf(0, 0), actual.get())
    }

    @Test fun `blocked reader mutex wipe does not delay independent native unblocker`() {
        val pool = ReleasePool(2)
        val mutex = Any()
        val nativeReleased = CountDownLatch(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 2))
        val copy = reservation.construct {
            val result = copyBytes(0, byteArrayOf(7, 9), mutex)
            attach(1, AutoCloseable { nativeReleased.countDown() })
            result
        }
        synchronized(mutex) {
            reservation.release()
            assertTrue(nativeReleased.await(1, TimeUnit.SECONDS))
            waitFor { pool.occupied == 1 }
            assertArrayEquals(byteArrayOf(7, 9), copy)
            assertFalse(reservation.successful)
        }
        waitFor { pool.occupied == 0 }
        assertArrayEquals(byteArrayOf(0, 0), copy)
    }

    @Test fun `duplicate declared child refuses before another copied array exists`() {
        val pool = ReleasePool(1)
        val copies = AtomicInteger()
        val closed = CountDownLatch(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1), copyArray = { source ->
            copies.incrementAndGet(); source.copyOf()
        })
        assertThrows(IllegalStateException::class.java) {
            reservation.construct {
                attach(0, AutoCloseable { closed.countDown() })
                copyBytes(0, byteArrayOf(7, 9))
            }
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        waitFor { pool.occupied == 0 }
        assertEquals(0, copies.get())
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < deadline) Thread.yield()
        assertTrue("actual retirement did not acknowledge", check())
    }
}
