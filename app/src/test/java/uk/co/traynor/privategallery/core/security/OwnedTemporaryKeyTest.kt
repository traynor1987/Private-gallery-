package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class OwnedTemporaryKeyTest {
    private fun fixture(): Pair<PrimarySessionAuthority, PrimaryOperation> {
        val a = testPrimaryAuthority(); a.open(ByteArray(32) { 3 })
        return a to checkNotNull(a.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
    }
    @Test fun successfulComparisonRetiresAndActuallyWipesItsOriginal() {
        val (a, op) = fixture(); val secret = ByteArray(32) { 7 }
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        key.withProduced({ secret }) { assertArrayEquals(ByteArray(32) { 7 }, it) }
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(32), secret)
        op.close(); a.revoke()
    }
    @Test fun revokeDuringConsumerDoesNotDestroyALegitimateBorrow() {
        val (a, op) = fixture(); val secret = ByteArray(32) { 7 }
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        val entered = CountDownLatch(1); val resume = CountDownLatch(1); val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { key.withProduced({ secret }) { entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)); assertEquals(7, it[0].toInt()) } }
            catch (e: Throwable) { failure.set(e) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); a.revoke()
            assertFalse(key.retirement.isComplete); assertFalse(a.cleanupComplete)
            assertThrows(IllegalStateException::class.java) { a.open(ByteArray(32)) }
        } finally { resume.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive); assertNotNull(failure.get())
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(32), secret)
    }
    @Test fun lateProviderResultIsWipedWithoutEnteringTheConsumer() {
        val (a, op) = fixture(); val secret = ByteArray(32) { 9 }
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        val entered = CountDownLatch(1); val resume = CountDownLatch(1); var consumed = false
        val worker = Thread {
            try { key.withProduced({ entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)); secret }) { consumed = true } }
            catch (_: IllegalStateException) { }
        }
        worker.start()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); a.revoke(); assertFalse(key.retirement.isComplete) }
        finally { resume.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive); assertFalse(consumed)
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(32), secret)
    }
    @Test fun consumerExceptionPreservesTheFailureAndRetiresTheKey() {
        val (a, op) = fixture(); val secret = ByteArray(32) { 6 }; val expected = IllegalStateException("synthetic consumer")
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        assertSame(expected, assertThrows(IllegalStateException::class.java) { key.withProduced({ secret }) { throw expected } })
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(32), secret)
        op.close(); a.revoke()
    }
    @Test fun retiredOrReusedOriginalCannotInvokeAnotherProvider() {
        val (a, op) = fixture(); var calls = 0
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        key.close(); assertTrue(key.retirement.await(5, TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) { key.withProduced({ calls++; ByteArray(32) }) { fail("retired consumer") } }
        assertEquals(0, calls); op.close(); a.revoke()
    }
    @Test fun invalidLengthIsWipedAndCannotReachConsumer() {
        val (a, op) = fixture(); val secret = ByteArray(33) { 4 }
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        assertThrows(IllegalStateException::class.java) { key.withProduced({ secret }) { fail("invalid key accepted") } }
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(33), secret)
        op.close(); a.revoke()
    }
    @Test fun rejectedUseInsideAuthorityGateStillRetiresItsPreparedOriginal() {
        val (a, op) = fixture(); var entered = false
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        assertThrows(IllegalStateException::class.java) {
            op.commit { key.withProduced({ entered = true; ByteArray(32) }) { fail("prohibited consumer") } }
        }
        assertFalse(entered)
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS))
        op.close(); a.revoke()
    }
    @Test fun capacityExhaustionDeniesBeforeAnotherProviderCanRun() {
        val (a, op) = fixture()
        val keys = mutableListOf<OwnedTemporaryKey>()
        try {
            repeat(16) { keys += ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey() }
            assertThrows(IllegalStateException::class.java) {
                ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
            }
        } finally {
            keys.forEach { it.close() }
            keys.forEach { assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) }
            op.close(); a.revoke()
        }
    }
    @Test fun repeatedComparisonCyclesReturnTheirFixedCapacity() {
        val (a, op) = fixture()
        repeat(40) {
            val bytes = ByteArray(32) { 5 }
            val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
            key.withProduced({ bytes }) { assertEquals(5, it[0].toInt()) }
            assertTrue(key.retirement.await(5, TimeUnit.SECONDS))
            assertArrayEquals(ByteArray(32), bytes)
        }
        op.close(); a.revoke()
    }
    @Test fun providerFailureRetiresWithoutEnteringTheConsumer() {
        val (a, op) = fixture(); val expected = IllegalStateException("synthetic provider")
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        assertSame(expected, assertThrows(IllegalStateException::class.java) {
            key.withProduced({ throw expected }) { fail("failed provider consumed") }
        })
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); op.close(); a.revoke()
    }
    @Test fun oldPreparedKeyCannotReadOrMutateAReplacementSession() {
        val (a, op) = fixture(); var entered = false
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        a.revoke(); assertTrue(key.retirement.await(5, TimeUnit.SECONDS))
        a.open(ByteArray(32) { 8 })
        assertThrows(IllegalStateException::class.java) {
            key.withProduced({ entered = true; ByteArray(32) }) { fail("stale consumer") }
        }
        assertFalse(entered)
        checkNotNull(a.operationOrNull()).use { assertEquals(8, it.key[0].toInt()) }
        op.close(); a.revoke()
    }
    @Test fun reentrantRejectedUseCannotWipeTheAdmittedBorrower() {
        val (a, op) = fixture(); val bytes = ByteArray(32) { 7 }; var secondProvider = false
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        assertThrows(IllegalStateException::class.java) {
            key.withProduced({ bytes }) { borrowed ->
                assertThrows(IllegalStateException::class.java) {
                    key.withProduced({ secondProvider = true; ByteArray(32) }) { fail("second consumer") }
                }
                assertFalse(secondProvider); assertFalse(key.retirement.isComplete)
                assertArrayEquals(ByteArray(32) { 7 }, borrowed)
            }
        }
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(32), bytes)
        op.close(); a.revoke()
    }
    @Test fun actualWipeAndCompletedStageDoNotDischargeAnUnreturnedReleaseCallback() {
        val (a, op) = fixture(); val entered = CountDownLatch(1); val resume = CountDownLatch(1)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ action(); entered.countDown(); check(resume.await(5, TimeUnit.SECONDS)) }, returned)
        })
        a.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(a, pool)
        val bytes = ByteArray(32) { 7 }
        val key = ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey()
        try {
            key.withProduced({ bytes }) { assertEquals(7, it[0].toInt()) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertArrayEquals(ByteArray(32), bytes)
            assertFalse(key.retirement.isComplete); a.revoke(); assertFalse(a.cleanupComplete)
            assertThrows(IllegalStateException::class.java) { a.open(ByteArray(32)) }
        } finally { resume.countDown(); key.close(); op.close(); a.revoke() }
        assertTrue(key.retirement.await(5, TimeUnit.SECONDS)); assertEquals(0, pool.occupied)
    }
    @Test fun noTemporaryKeyFactoryMayBePreparedInsideAnAuthorityGate() {
        val (a, op) = fixture()
        assertThrows(IllegalStateException::class.java) { op.commit { ScopedIoGuard(op, PrimaryScope.CREDENTIALS).createOwnedTemporaryKey() } }
        op.close(); a.revoke()
    }
}
