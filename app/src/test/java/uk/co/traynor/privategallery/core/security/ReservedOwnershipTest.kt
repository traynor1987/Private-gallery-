package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority
import uk.co.traynor.privategallery.core.domain.SecondaryOperation

class ReservedOwnershipTest {
    @Test fun `closed original lease denies before resource construction`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32) { 7 })
        val operation = authority.operationOrNull()!!
        operation.close()
        val called = AtomicInteger()
        assertThrows(IllegalStateException::class.java) {
            operation.createOwned { attach -> AutoCloseable { }.also { called.incrementAndGet(); attach(it) } }
        }
        assertEquals("stale factory must not run", 0, called.get())
        authority.revoke()
    }

    @Test fun `original constructor remains accounted and partial child releases before return`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32) { 3 })
        val operation = authority.operationOrNull()!!
        val attached = CountDownLatch(1)
        val childClosed = CountDownLatch(1)
        val finishFactory = CountDownLatch(1)
        val finished = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    operation.createOwned { attach ->
                        val child = AutoCloseable { childClosed.countDown() }
                        attach(child)
                        attached.countDown()
                        check(finishFactory.await(5, TimeUnit.SECONDS))
                        child
                    }
                }
            } finally { finished.countDown() }
        }
        assertTrue(attached.await(5, TimeUnit.SECONDS))
        authority.revoke()
        assertTrue("partial child dispatch cannot wait for composite return", childClosed.await(1, TimeUnit.SECONDS))
        assertFalse("actual original constructor remains pending", authority.cleanupComplete)
        val rejectedKey = ByteArray(32) { 9 }
        assertThrows(IllegalStateException::class.java) { authority.open(rejectedKey) }
        assertTrue(rejectedKey.all { it == 0.toByte() })
        finishFactory.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        waitFor { authority.cleanupComplete }
    }

    @Test fun `normal retirement does not accumulate tokens in a live epoch`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32) { 5 })
        val operation = authority.operationOrNull()!!
        val closes = AtomicInteger()
        repeat(40) {
            val owned = operation.createOwned { attach -> AutoCloseable { closes.incrementAndGet() }.also(attach) }
            repeat(3) { owned.close() }
            waitFor { closes.get() == it + 1 }
        }
        operation.close(); authority.revoke()
        waitFor { authority.cleanupComplete }
        assertEquals("normal retirement and later revoke share exact cleanup", 40, closes.get())
    }

    @Test fun `hidden closed lease refuses original factory before constructing`() {
        val authority = testSecondaryAuthority { 0 }
        authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32))
        val operation = authority.operationOrNull()!!
        operation.close()
        val created = AtomicInteger()
        assertThrows(IllegalStateException::class.java) {
            operation.createOwned { attach -> AutoCloseable {}.also { created.incrementAndGet(); attach(it) } }
        }
        assertEquals(0, created.get())
        authority.revoke()
    }

    @Test fun `hidden partial creation has original pending cleanup across attempted authentication`() {
        val authority = testSecondaryAuthority { 0 }
        authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32))
        val operation = authority.operationOrNull()!!
        val attached = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val completed = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    operation.createOwned { attach ->
                        val resource = AutoCloseable { closed.countDown() }
                        attach(resource); attached.countDown()
                        check(finish.await(5, TimeUnit.SECONDS))
                        resource
                    }
                }
            } finally { completed.countDown() }
        }
        assertTrue(attached.await(5, TimeUnit.SECONDS))
        authority.revoke()
        assertTrue(closed.await(1, TimeUnit.SECONDS))
        assertFalse(authority.cleanupComplete)
        val auth = authority.beginAuthentication()
        assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(auth) }
        finish.countDown()
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        waitFor { authority.cleanupComplete }
        authority.checkAuthentication(auth)
    }

    @Test fun `rejected unattached known result keeps original release obligation`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val closed = CountDownLatch(1)
        assertThrows(IllegalStateException::class.java) {
            operation.createOwned { _ -> AutoCloseable { closed.countDown() } }
        }
        assertTrue("known rejected result must actually close", closed.await(1, TimeUnit.SECONDS))
        operation.close(); authority.revoke()
        waitFor { authority.cleanupComplete }
    }

    @Test fun `failed original reservation snapshot cannot authorize fresh Primary key`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32) { 7 })
        val operation = authority.operationOrNull()!!
        val closed = CountDownLatch(1)
        val owned = operation.createOwned { attach -> AutoCloseable { closed.countDown() }.also(attach) }
        val field = PrimaryOperation::class.java.getDeclaredField("reservations").also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val original = field.get(operation) as MutableSet<ReleaseReservation>
        field.set(operation, object : MutableSet<ReleaseReservation> by original {
            override fun iterator(): MutableIterator<ReleaseReservation> = throw OutOfMemoryError("snapshot allocation failure")
        })
        try {
            assertThrows(OutOfMemoryError::class.java) { authority.revoke() }
            assertFalse("undispatched original child remains an authentication barrier", authority.cleanupComplete)
            val rejectedKey = ByteArray(32) { 9 }
            assertThrows(IllegalStateException::class.java) { authority.open(rejectedKey) }
            assertTrue(rejectedKey.all { it == 0.toByte() })
        } finally {
            field.set(operation, original)
            owned.close()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun `failed original reservation snapshot cannot authorize fresh Hidden key`() {
        val authority = testSecondaryAuthority { 0L }
        assertTrue(authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32) { 7 }))
        val operation = authority.operationOrNull()!!
        val closed = CountDownLatch(1)
        val owned = operation.createOwned { attach -> AutoCloseable { closed.countDown() }.also(attach) }
        val field = SecondaryOperation::class.java.getDeclaredField("reservations").also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val original = field.get(operation) as MutableSet<ReleaseReservation>
        field.set(operation, object : MutableSet<ReleaseReservation> by original {
            override fun iterator(): MutableIterator<ReleaseReservation> = throw OutOfMemoryError("snapshot allocation failure")
        })
        try {
            assertThrows(OutOfMemoryError::class.java) { authority.revoke() }
            assertFalse("undispatched original Hidden child remains an authentication barrier", authority.cleanupComplete)
            val attempt = authority.beginAuthentication()
            val rejectedKey = ByteArray(32) { 9 }
            assertThrows(IllegalStateException::class.java) { authority.completeAuthentication(attempt, rejectedKey) }
            assertTrue(rejectedKey.all { it == 0.toByte() })
        } finally {
            field.set(operation, original)
            owned.close()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
        }
    }

    private fun waitFor(check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < end) Thread.yield()
        assertTrue(check())
    }
}
