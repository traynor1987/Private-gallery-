package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Inert factory/helper boundaries; no production authority or caller migration implied. */
class OwnedFactoryScopeTest {
    @Test fun `named copied key and control resource share exact original normal retirement`() {
        val pool = ReleasePool(2); val manifest = OwnedResourceManifest.io("reader", "key")
        val reservation = ReleaseReservation(pool.reserveAll(Any(), manifest.children.size))
        reservation.onRetirementAccounting {}
        val source = byteArrayOf(7, 9); var copy = byteArrayOf(); val closes = AtomicInteger()
        val actual = reservation.construct {
            OwnedFactoryScope(this, manifest).run {
                val reader = attach("reader", AutoCloseable { closes.incrementAndGet() })
                copy = copyBytes("key", source)
                reader.also { verifyResult(0, it) }
            }
        }
        val owned = OwnedResource(actual, reservation)
        repeat(3) { owned.close() }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
        assertEquals(1, closes.get()); assertArrayEquals(byteArrayOf(0, 0), copy)
        assertArrayEquals(byteArrayOf(7, 9), source)
    }

    @Test fun `named child lookup refuses unknown copied resource before copying`() {
        val pool = ReleasePool(1); val copies = AtomicInteger()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1), copyArray = { copies.incrementAndGet(); it.copyOf() })
        reservation.onRetirementAccounting {}
        assertThrows(IllegalStateException::class.java) {
            reservation.construct { OwnedFactoryScope(this, OwnedResourceManifest.io("key")).copyBytes("other", byteArrayOf(7)) }
        }
        assertTrue(reservation.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
        assertEquals(0, copies.get())
    }

    @Test fun `duplicate or oversized named manifests fail before child creation`() {
        assertThrows(IllegalArgumentException::class.java) { OwnedResourceManifest.io("key", "key") }
        assertThrows(IllegalArgumentException::class.java) { OwnedResourceManifest.io(*Array(17) { "child-$it" }) }
        assertThrows(IllegalArgumentException::class.java) { OwnedResourceManifest.presentation(" ") }
        assertThrows(IllegalArgumentException::class.java) { OwnedResourceManifest.io() }
    }

    @Test fun `declared independent action dispatches while reader wipe holds its original mutex`() {
        val pool = ReleasePool(3); val manifest = OwnedResourceManifest.io("reader", "key", "transport")
        val reservation = ReleaseReservation(pool.reserveAll(Any(), manifest.children.size)); reservation.onRetirementAccounting {}
        val mutex = Any(); val transport = CountDownLatch(1); var copy = byteArrayOf()
        val actual = reservation.construct {
            OwnedFactoryScope(this, manifest).run {
                val reader = attach("reader", AutoCloseable {})
                copy = copyBytes("key", byteArrayOf(7), mutex)
                attach("transport", AutoCloseable { transport.countDown() })
                reader.also { verifyResult(0, it) }
            }
        }
        val owned = OwnedResource(actual, reservation)
        synchronized(mutex) {
            owned.close(); assertTrue(transport.await(1, TimeUnit.SECONDS))
            assertFalse(owned.retirement.await(20, TimeUnit.MILLISECONDS))
            assertFalse(reservation.successful); assertArrayEquals(byteArrayOf(7), copy)
        }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); waitFor { pool.occupied == 0 }
        assertArrayEquals(byteArrayOf(0), copy)
    }

    @Test fun `insufficient physical capacity denies complete manifest before factory invocation`() {
        val pool = ReleasePool(8); val manifest = OwnedResourceManifest.io(*Array(9) { "child-$it" })
        val factories = AtomicInteger()
        assertThrows(IllegalArgumentException::class.java) {
            val reservation = ReleaseReservation(pool.reserveAll(Any(), manifest.children.size))
            reservation.construct { factories.incrementAndGet() }
        }
        assertEquals(0, factories.get()); assertEquals(0, pool.occupied)
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < deadline) Thread.yield()
        assertTrue("actual retirement did not acknowledge", check())
    }
}
