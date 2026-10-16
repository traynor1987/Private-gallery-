package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class PrimaryBiometricPreferenceWriteTest {
    private class Editor(private val write: () -> Boolean) : SharedPreferences.Editor {
        override fun commit() = write()
        override fun apply() { error("Synchronous writes only") }
        override fun putString(key: String?, value: String?) = this
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = this
        override fun putLong(key: String?, value: Long) = this
        override fun putFloat(key: String?, value: Float) = this
        override fun putBoolean(key: String?, value: Boolean) = this
        override fun remove(key: String?) = this
        override fun clear() = this
    }
    private fun <T> fixture(scope: PrimaryScope = PrimaryScope.CREDENTIALS, expectFailedCleanup: Boolean = false, action: (PrimarySessionAuthority, PrimaryOperation) -> T): T {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32) { 3 })
        val operation = checkNotNull(authority.operationOrNull(setOf(scope)))
        var failure: Throwable? = null
        try { return action(authority, operation) }
        catch (problem: Throwable) { failure = problem; throw problem }
        finally {
            try {
            operation.close(); authority.revoke()
            if (expectFailedCleanup) assertFalse("failed fixture stays charged; never reset its ownership", authority.cleanupComplete)
            else {
                val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!authority.cleanupComplete) { check(System.nanoTime() < limit); Thread.yield() }
            }
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        }
    }
    @Test fun preparationAndWriteRunOutsideAuthorityWithOriginalRetainedThroughReturn() = fixture { _, operation ->
        var original: ReleaseReservation? = null; var writes = 0
        persistPrimaryBiometricPreferences(operation) {
            operation.requireNativeOutsideAuthorityGate()
            original = operation.commit { operation.reservations.single() }
            Editor {
                operation.requireNativeOutsideAuthorityGate()
                assertFalse(checkNotNull(original).terminalRetirement.isComplete); writes++; true
            }
        }
        assertEquals(1, writes); assertTrue(checkNotNull(original).terminalRetirement.isComplete)
    }
    @Test fun inheritedAuthorityGateIsDeniedBeforeEditorPreparation() = fixture { _, operation ->
        var prepared = false
        assertThrows(IllegalStateException::class.java) {
            operation.commit { persistPrimaryBiometricPreferences(operation) { prepared = true; Editor { true } } }
        }
        assertFalse(prepared)
    }
    @Test fun capacityExhaustionDeniesBeforeEditorPreparation() = fixture { _, operation ->
        val held = operation.createOwned(OwnedResourceManifest.io(*Array(16) { "held-$it" })) {
            var root: AutoCloseable? = null
            for (index in 0 until 16) { val child = attach("held-$index", AutoCloseable { }); if (root == null) root = child }
            checkNotNull(root)
        }
        try {
            var prepared = false
            assertThrows(IllegalStateException::class.java) { persistPrimaryBiometricPreferences(operation) { prepared = true; Editor { true } } }
            assertFalse(prepared)
        } finally { held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)) }
    }
    @Test fun admittedWriteCanFinishAfterRevokeWithoutBlockingRevocationOrPublishingSuccess() = fixture { authority, operation ->
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val revoked = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val succeeded = AtomicBoolean()
        val writer = Thread {
            try { persistPrimaryBiometricPreferences(operation) { Editor { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); true } }; succeeded.set(true) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        val revoker = Thread { authority.revoke(); revoked.countDown() }
        writer.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); revoker.start()
            assertTrue("preference IO cannot hold authority revocation", revoked.await(1, TimeUnit.SECONDS))
            assertFalse(authority.cleanupComplete)
            assertThrows(IllegalStateException::class.java) { authority.open(ByteArray(32) { 8 }) }
        } finally { release.countDown(); writer.join(5000); if (revoker.state != Thread.State.NEW) revoker.join(5000) }
        assertFalse(writer.isAlive); assertFalse(revoker.isAlive); assertFalse(succeeded.get())
        assertTrue(failure.get() is IllegalStateException)
    }
    @Test fun revokedDuringPreparationNeverInvokesThePreparedEditor() = fixture { authority, operation ->
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val writes = java.util.concurrent.atomic.AtomicInteger()
        val worker = Thread {
            try { persistPrimaryBiometricPreferences(operation) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); Editor { writes.incrementAndGet(); true } } }
            catch (problem: Throwable) { failure.set(problem) }
        }
        worker.start()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke(); assertFalse(authority.cleanupComplete) }
        finally { release.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive); assertEquals(0, writes.get()); assertTrue(failure.get() is IllegalStateException)
    }
    @Test fun conflictingMutationIsDeniedBeforePreparationAndOldCleanupCannotFreeReplacement() = fixture { _, operation ->
        val first = PrimaryBiometricPreferenceSlot.claim()
        try {
            var prepared = false
            assertThrows(IllegalStateException::class.java) { persistPrimaryBiometricPreferences(operation) { prepared = true; Editor { true } } }
            assertFalse(prepared)
        } finally { first.close() }
        val replacement = PrimaryBiometricPreferenceSlot.claim()
        try {
            first.close()
            assertThrows(IllegalStateException::class.java) { PrimaryBiometricPreferenceSlot.claim() }
            replacement.requireHeld()
        } finally { replacement.close() }
        persistPrimaryBiometricPreferences(operation) { Editor { true } }
    }
    @Test fun providerPreparationFailureRetiresActualOriginalBeforeRethrowingAndAllowsAnIndependentRetry() = fixture { _, operation ->
        var original: ReleaseReservation? = null
        val expected = java.io.IOException("synthetic preference failure")
        val actual = assertThrows(java.io.IOException::class.java) {
            persistPrimaryBiometricPreferences(operation) {
                original = operation.commit { operation.reservations.single() }
                throw expected
            }
        }
        assertSame(expected, actual); assertTrue(checkNotNull(original).terminalRetirement.isComplete)
        persistPrimaryBiometricPreferences(operation) { Editor { true } }
    }
    private fun awaitFailed(original: ReleaseReservation) {
        val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!original.failed && !original.terminalRetirement.isComplete) { check(System.nanoTime() < limit); Thread.yield() }
        assertTrue("ambiguous commit must retain failed original", original.failed)
        assertFalse(original.terminalRetirement.isComplete)
    }
    @Test fun enteredProviderThrowKeepsFailedOriginalAndExistingSlotUnavailable() = fixture(expectFailedCleanup = true) { _, operation ->
        val registry = PrimaryBiometricPreferenceRegistry(); var original: ReleaseReservation? = null
        val expected = java.io.IOException("controlled provider failure")
        val actual = assertThrows(java.io.IOException::class.java) {
            syntheticPersistBiometricPreferences(registry, operation) {
                original = operation.commit { operation.reservations.single() }
                Editor { throw expected }
            }
        }
        assertSame(expected, actual); awaitFailed(checkNotNull(original))
        var acquired = false
        assertThrows(IllegalStateException::class.java) {
            syntheticPersistBiometricPreferences(registry, operation) { acquired = true; Editor { true } }
        }
        assertFalse(acquired); assertThrows(IllegalStateException::class.java) { registry.claim() }
        Unit
    }
    @Test fun interruptedRetirementRestoresStatusAndNeverReportsWriteSuccess() = fixture { _, operation ->
        val failure = AtomicReference<Throwable>(); val interrupted = AtomicBoolean(); val succeeded = AtomicBoolean()
        val worker = Thread {
            try { persistPrimaryBiometricPreferences(operation) { Editor { Thread.currentThread().interrupt(); true } }; succeeded.set(true) }
            catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) }
        }
        worker.start(); worker.join(5000)
        assertFalse(worker.isAlive); assertFalse(succeeded.get()); assertTrue(failure.get() is InterruptedException); assertTrue(interrupted.get())
    }
    @Test fun recoveryCallbackMustBeSameThreadOnceOnlyAndComplete() {
        var pinWrites = 0; var disables = 0
        withPrimaryBiometricRecoveryDisable({ disables++; true }) { enter, disable ->
            val failure = AtomicReference<Throwable>()
            val foreign = Thread { try { enter(); pinWrites++; disable() } catch (problem: Throwable) { failure.set(problem) } }
            foreign.start(); foreign.join(5000); assertFalse(foreign.isAlive); assertTrue(failure.get() is IllegalStateException)
            enter(); pinWrites++; disable()
            assertThrows(IllegalStateException::class.java) { enter(); pinWrites++; disable() }
        }
        assertEquals(1, pinWrites); assertEquals(1, disables)
    }
    @Test fun escapedRecoveryCallbackIsDeniedBeforePinAndCannotAlterReplacementSlot() {
        var captured: (() -> Unit)? = null; var pinWrites = 0; var disables = 0
        assertThrows(IllegalStateException::class.java) {
            withPrimaryBiometricRecoveryDisable({ disables++; true }) { enter, disable ->
                captured = { enter(); pinWrites++; disable() }
            }
        }
        val replacement = PrimaryBiometricPreferenceSlot.claim()
        try {
            assertThrows(IllegalStateException::class.java) { checkNotNull(captured).invoke() }
            replacement.requireHeld()
        } finally { replacement.close() }
        assertEquals(0, pinWrites); assertEquals(0, disables)
    }
    @Test fun swallowedDisableFailureCannotTurnIntoRecoverySuccess() {
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            withPrimaryBiometricRecoveryDisable({ calls++; false }) { enter, disable ->
                enter(); assertThrows(IllegalStateException::class.java) { disable() }
            }
        }
        assertEquals(1, calls)
    }
    @Test fun failedPreferenceCommitNeverReturnsSuccessAndCannotAcknowledgeHeldDiskWork() = fixture(expectFailedCleanup = true) { authority, operation ->
        val registry = PrimaryBiometricPreferenceRegistry(); var original: ReleaseReservation? = null; var calls = 0
        val submitted = CountDownLatch(1); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val disk = Thread { check(submitted.await(10, TimeUnit.SECONDS)); entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        disk.start()
        try {
            assertThrows(IllegalStateException::class.java) {
                syntheticPersistBiometricPreferences(registry, operation) {
                    original = operation.commit { operation.reservations.single() }
                    Editor { calls++; submitted.countDown(); check(entered.await(5, TimeUnit.SECONDS)); false }
                }
            }
            assertEquals(1, calls); awaitFailed(checkNotNull(original)); assertFalse(authority.cleanupComplete)
            assertThrows(IllegalStateException::class.java) { registry.claim() }
            assertThrows(IllegalStateException::class.java) { authority.open(ByteArray(32)) }
        } finally { release.countDown(); disk.join(5000) }
        assertFalse(disk.isAlive); assertTrue(checkNotNull(original).failed)
        assertFalse(checkNotNull(original).terminalRetirement.isComplete)
        assertThrows(IllegalStateException::class.java) { registry.claim() }
        Unit
    }
    @Test fun insufficientScopeCannotPrepareAnEditor() = fixture(PrimaryScope.READ) { _, operation ->
        var prepared = false
        assertThrows(IllegalStateException::class.java) { persistPrimaryBiometricPreferences(operation) { prepared = true; Editor { true } } }
        assertFalse(prepared)
    }
}
