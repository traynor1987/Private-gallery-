package uk.co.traynor.privategallery.core.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
import uk.co.traynor.privategallery.core.security.testPrimaryAuthority
import uk.co.traynor.privategallery.core.security.testSecondaryAuthority
import uk.co.traynor.privategallery.core.security.RetirementAcknowledgement
import java.util.concurrent.atomic.AtomicReference

class SecondaryAttemptOwnershipTest {
    @Test fun `authority gated callback cannot invoke an attempt factory`() {
        val authority = testSecondaryAuthority { 0 }
        val attempt = authority.beginAuthentication()
        var factories = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                authority.commitAuthentication(attempt) {
                    authority.createAttemptOwned(attempt, OwnedResourceManifest.io("resource")) {
                        factories++
                        attach("resource", AutoCloseable {})
                    }
                }
            }
            assertEquals(0, factories)
        } finally { authority.revoke() }
    }

    @Test fun `attempt capacity denies before factory and does not consume Primary capacity`() {
        val authority = testSecondaryAuthority { 0 }
        val attempt = authority.beginAuthentication()
        val calls = AtomicInteger()
        val owned = (0 until 16).map {
            authority.createAttemptOwned(attempt, OwnedResourceManifest.io("resource")) {
                calls.incrementAndGet(); attach("resource", AutoCloseable {})
            }
        }
        try {
            authority.checkPreparation(attempt)
            assertFalse(authority.cleanupComplete)
            assertThrows(IllegalStateException::class.java) {
                authority.createAttemptOwned(attempt, OwnedResourceManifest.io("resource")) {
                    calls.incrementAndGet(); attach("resource", AutoCloseable {})
                }
            }
            assertEquals(16, calls.get())
            val primary = testPrimaryAuthority()
            primary.open(ByteArray(32))
            try { checkNotNull(primary.operationOrNull()).use { it.checkValid() } }
            finally { primary.revoke() }
        } finally {
            owned.forEach { it.close() }
            owned.forEach { assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) }
            authority.revoke()
        }
    }

    @Test fun `cancel dispatches attached partial child before constructor returns`() {
        val authority = testSecondaryAuthority { 0 }
        val attempt = authority.beginAuthentication()
        val attached = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val rejected = CountDownLatch(1)
        val terminal = AtomicReference<RetirementAcknowledgement>()
        val worker = thread {
            try {
                authority.createAttemptOwned(attempt, OwnedResourceManifest.io("resource")) {
                    terminal.set(original.terminalRetirement)
                    attach("resource", AutoCloseable {
                        // Cleanup may acquire authority: it must not inherit the authority gate.
                        authority.bindingOrNull(); closed.countDown()
                    })
                    attached.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                    error("factory finishes after cancellation")
                }
            } catch (_: IllegalStateException) { rejected.countDown() }
        }
        try {
            assertTrue(attached.await(5, TimeUnit.SECONDS))
            authority.cancelAuthentication(attempt)
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertFalse(authority.cleanupComplete)
            assertThrows(IllegalStateException::class.java) { authority.checkPreparation(attempt) }
        } finally { finish.countDown(); worker.join(5_000) }
        assertFalse(worker.isAlive)
        assertTrue(rejected.await(5, TimeUnit.SECONDS))
        assertTrue(terminal.get().await(5, TimeUnit.SECONDS))
    }

    @Test fun `old attempt retirement never cancels replacement and barriers remain exact`() {
        val authority = testSecondaryAuthority { 0 }
        val old = authority.beginAuthentication()
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val owned = authority.createAttemptOwned(old, OwnedResourceManifest.io("resource")) {
            attach("resource", AutoCloseable { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) })
        }
        val current = authority.beginAuthentication()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            authority.cancelAuthentication(old)
            var factories = 0
            assertThrows(IllegalStateException::class.java) {
                authority.createAttemptOwned(current, OwnedResourceManifest.io("resource")) {
                    factories++; attach("resource", AutoCloseable {})
                }
            }
            assertEquals(0, factories)
        } finally { finish.countDown() }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        authority.checkPreparation(current)
        assertTrue(authority.completeAuthentication(current, ByteArray(32)))
        authority.revoke()
    }
    @Test fun `premature key admission consumes attempt and retires every attempt resource`() {
        val authority = testSecondaryAuthority { 0 }
        val attempt = authority.beginAuthentication()
        val closed = CountDownLatch(1)
        val owned = authority.createAttemptOwned(attempt, OwnedResourceManifest.io("resource")) {
            attach("resource", AutoCloseable { closed.countDown() })
        }
        val key = ByteArray(32) { 9 }
        assertThrows(IllegalStateException::class.java) { authority.completeAuthentication(attempt, key) }
        assertArrayEquals(ByteArray(32), key)
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) { authority.checkPreparation(attempt) }
        assertTrue(authority.cleanupComplete)
        assertNull(authority.operationOrNull())
    }

}
