package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

class PrimaryRecoveryReadOwnershipTest {
    private fun record() = RecoveryWrappedKey(ByteArray(16), ByteArray(12), ByteArray(48))
    private fun <T> fixture(scope: PrimaryScope = PrimaryScope.BACKUP, action: (PrimarySessionAuthority, PrimaryOperation) -> T): T {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32) { 3 })
        val operation = checkNotNull(authority.operationOrNull(setOf(scope)))
        try { return action(authority, operation) }
        finally {
            operation.close(); authority.revoke()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!authority.cleanupComplete) { check(System.nanoTime() < deadline); Thread.yield() }
        }
    }
    @Test fun readRunsOutsideAuthorityAndRetiresBeforeReturningTheOriginalRecord() = fixture { authority, operation ->
        val expected = record()
        var original: ReleaseReservation? = null
        val actual = readPrimaryRecoveryEnvelope(operation) {
            operation.requireNativeOutsideAuthorityGate()
            original = operation.commit { operation.reservations.single() }
            assertFalse(checkNotNull(original).terminalRetirement.isComplete)
            expected
        }
        assertSame(expected, actual)
        assertTrue(checkNotNull(original).terminalRetirement.isComplete)
        assertTrue(authority.cleanupComplete)
    }
    @Test fun revocationCanFinishWhileProviderIsHeldAndLateResultCannotEscape() = fixture { authority, operation ->
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val revoked = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val result = AtomicReference<RecoveryWrappedKey>()
        val reader = Thread {
            try { result.set(readPrimaryRecoveryEnvelope(operation) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); record() }) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        val revoker = Thread { authority.revoke(); revoked.countDown() }
        reader.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); revoker.start()
            assertTrue("metadata provider must not hold the authority gate", revoked.await(1, TimeUnit.SECONDS))
            assertFalse("held actual read cannot be acknowledged", authority.cleanupComplete)
        } finally { release.countDown(); reader.join(5000); if (revoker.state != Thread.State.NEW) revoker.join(5000) }
        assertFalse(reader.isAlive); assertFalse(revoker.isAlive)
        assertNull(result.get()); assertTrue(failure.get() is IllegalStateException)
    }
    @Test fun inheritedAuthorityGateIsRejectedBeforeProviderEntry() = fixture { _, operation ->
        var entered = false
        assertThrows(IllegalStateException::class.java) { operation.commit { readPrimaryRecoveryEnvelope(operation) { entered = true; record() } } }
        assertFalse(entered)
    }
    @Test fun missingScopeCannotInvokeProvider() = fixture(PrimaryScope.READ) { _, operation ->
        var entered = false
        assertThrows(IllegalStateException::class.java) { readPrimaryRecoveryEnvelope(operation) { entered = true; record() } }
        assertFalse(entered)
    }
    @Test fun interruptionCannotReturnARecordAndRestoresTheReaderInterrupt() = fixture { _, operation ->
        val failure = AtomicReference<Throwable>(); val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        val result = AtomicReference<RecoveryWrappedKey>()
        val reader = Thread {
            try {
                result.set(readPrimaryRecoveryEnvelope(operation) { Thread.currentThread().interrupt(); record() })
            } catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) }
        }
        reader.start(); reader.join(5000)
        assertFalse(reader.isAlive); assertNull(result.get())
        assertTrue(failure.get() is InterruptedException); assertTrue(interrupted.get())
    }
    @Test fun providerExceptionCannotEscapeBeforeOriginalRetirement() = fixture { _, operation ->
        val expected = IllegalStateException("synthetic provider failure")
        var original: ReleaseReservation? = null
        val actual = assertThrows(IllegalStateException::class.java) {
            readPrimaryRecoveryEnvelope(operation) {
                original = operation.commit { operation.reservations.single() }
                throw expected
            }
        }
        assertSame(expected, actual); assertTrue(checkNotNull(original).terminalRetirement.isComplete)
    }
    @Test fun exhaustedPhysicalCapacityDeniesProviderBeforeAcquisition() = fixture { _, operation ->
        val names = Array(16) { "held-$it" }
        val held = operation.createOwned(OwnedResourceManifest.io(*names)) {
            var first: AutoCloseable? = null
            for (name in names) { val child = attach(name, AutoCloseable { }); if (first == null) first = child }
            checkNotNull(first)
        }
        try {
            var entered = false
            assertThrows(IllegalStateException::class.java) { readPrimaryRecoveryEnvelope(operation) { entered = true; record() } }
            assertFalse(entered)
        } finally { held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)) }
    }
}
