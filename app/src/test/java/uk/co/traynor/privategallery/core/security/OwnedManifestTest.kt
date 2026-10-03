package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority

class OwnedManifestTest {
    @Test fun `complete original manifest dispatches independent children and acknowledges normal retirement`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val entered = CountDownLatch(1); val finish = CountDownLatch(1); val independent = CountDownLatch(1)
        val owned = operation.createOwned(OwnedResourceManifest.io("reader", "transport")) {
            val reader = attach("reader", AutoCloseable { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) })
            attach("transport", AutoCloseable { independent.countDown() })
            reader
        }
        owned.close()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertTrue(independent.await(1, TimeUnit.SECONDS))
            assertFalse(owned.retirement.isComplete)
        } finally { finish.countDown() }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        operation.close(); authority.revoke(); waitFor { authority.cleanupComplete }
    }

    @Test fun `complete manifest refuses capacity before its factory can run`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val blocker = operation.createOwned(OwnedResourceManifest.io(*Array(16) { "child-$it" })) {
            attach("child-0", AutoCloseable {})
        }
        val called = AtomicInteger()
        try {
            assertThrows(IllegalStateException::class.java) {
                operation.createOwned(OwnedResourceManifest.io("reader", "transport")) {
                    called.incrementAndGet(); attach("reader", AutoCloseable {})
                }
            }
            assertEquals(0, called.get())
        } finally { blocker.close() }
        assertTrue(blocker.retirement.await(5, TimeUnit.SECONDS))
        operation.close(); authority.revoke(); waitFor { authority.cleanupComplete }
    }

    @Test fun `typed copied key shares named original manifest retirement`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val source = byteArrayOf(7, 9)
        var copied = byteArrayOf()
        val owned = operation.createOwned(OwnedResourceManifest.io("reader", "copied-key")) {
            val reader = attach("reader", AutoCloseable {})
            copied = copyBytes("copied-key", source)
            reader
        }
        owned.close(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(0, 0), copied); assertArrayEquals(byteArrayOf(7, 9), source)
        operation.close(); authority.revoke(); waitFor { authority.cleanupComplete }
    }

    @Test fun `session transfer requires exact live original operation and preserves original cleanup`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val original = authority.operationOrNull()!!; val foreign = authority.operationOrNull()!!
        val closed = AtomicInteger()
        val owned = original.createOwned(OwnedResourceManifest.io("resource")) { attach("resource", AutoCloseable { closed.incrementAndGet() }) }
        assertThrows(IllegalStateException::class.java) { foreign.transferToSession(owned) }
        original.transferToSession(owned); original.close(); foreign.close()
        assertEquals(0, closed.get())
        assertFalse(owned.retirement.isComplete)
        assertThrows(IllegalStateException::class.java) { original.transferToSession(owned) }
        authority.revoke(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        waitFor { authority.cleanupComplete }; assertEquals(1, closed.get())
    }

    @Test fun `Hidden presentation manifest uses original Hidden lifecycle`() {
        val authority = testSecondaryAuthority { 0 }
        authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32))
        val operation = authority.operationOrNull()!!; val closed = AtomicInteger()
        val owned = operation.createOwned(OwnedResourceManifest.presentation("buffer")) {
            attach("buffer", AutoCloseable { closed.incrementAndGet() })
        }
        operation.transferToSession(owned); operation.close(); authority.revoke()
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        waitFor { authority.cleanupComplete }; assertEquals(1, closed.get())
    }

    @Test fun `manifest rejects duplicate names and unknown copied child without new copy`() {
        assertThrows(IllegalArgumentException::class.java) { OwnedResourceManifest.io("same", "same") }
        val pool = ReleasePool(1); val copies = AtomicInteger()
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1), copyArray = { copies.incrementAndGet(); it.copyOf() })
        assertThrows(IllegalStateException::class.java) {
            reservation.construct { OwnedFactoryScope(this, OwnedResourceManifest.io("declared")).copyBytes("other", byteArrayOf(7)) }
        }
        waitFor { pool.occupied == 0 }; assertEquals(0, copies.get())
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < deadline) Thread.yield()
        assertTrue("actual retirement did not acknowledge", check())
    }
}
