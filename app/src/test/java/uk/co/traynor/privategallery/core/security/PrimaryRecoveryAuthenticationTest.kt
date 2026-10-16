package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class PrimaryRecoveryAuthenticationTest {
    private fun awaitCleanup(authority: PrimarySessionAuthority) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!authority.cleanupComplete) { check(System.nanoTime() < end); Thread.yield() }
    }
    private fun fixture(action: (PrimarySessionAuthority) -> Unit) {
        val authority = testPrimaryAuthority()
        try { action(authority) } finally { authority.revoke(); awaitCleanup(authority) }
    }
    @Test fun bothInputsAreWipedBeforeExactResultAdmission() = fixture { authority ->
        val secret = "recovery-input".toCharArray(); val pin = "123456".toCharArray()
        val key = ByteArray(32) { 4 }
        assertTrue(authority.authenticateRecovery(secret, pin) {
            assertTrue(secret.any { it != '\u0000' }); assertTrue(pin.any { it != '\u0000' })
            key
        })
        assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
        val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try { assertArrayEquals(key, operation.key) } finally { operation.close() }
        authority.revoke(); awaitCleanup(authority); assertArrayEquals(ByteArray(32), key)
    }
    @Test fun exceptionWipesBothInputsWithoutAuthentication() = fixture { authority ->
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); val expected = IOException("controlled")
        assertSame(expected, assertThrows(IOException::class.java) {
            authority.authenticateRecovery(secret, pin) { throw expected }
        })
        assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
        assertNull(authority.bindingOrNull())
    }
    @Test fun revocationRetainsBorrowedInputsAndDeniesLateResultAndReplacement() = fixture { authority ->
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); val key = ByteArray(32) { 8 }
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val accepted = AtomicReference<Boolean>()
        val worker = Thread {
            try { accepted.set(authority.authenticateRecovery(secret, pin) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); key }) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertFalse(authority.cleanupComplete)
            assertTrue(secret.any { it != '\u0000' }); assertTrue(pin.any { it != '\u0000' })
            val replacement = ByteArray(32) { 2 }
            assertThrows(IllegalStateException::class.java) { authority.open(replacement) }
            assertArrayEquals(ByteArray(32), replacement)
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertNull(accepted.get()); assertTrue(failure.get() is IllegalStateException)
        awaitCleanup(authority)
        assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' }); assertArrayEquals(ByteArray(32), key)
        assertNull(authority.bindingOrNull())
    }
    @Test fun timedOutProducerCannotPromoteCredentials() {
        var now = 0L; val authority = PrimarySessionAuthority { now }
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); val key = ByteArray(32) { 3 }
        try {
            assertThrows(IllegalStateException::class.java) {
                authority.authenticateRecovery(secret, pin) { now = 60000L; key }
            }
            awaitCleanup(authority)
            assertArrayEquals(ByteArray(32), key); assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
            assertNull(authority.bindingOrNull())
        } finally { authority.revoke(); awaitCleanup(authority) }
    }
    @Test fun invalidKeyCannotEscapeAndBothInputsRetire() = fixture { authority ->
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); val key = ByteArray(31) { 7 }
        assertThrows(IllegalStateException::class.java) { authority.authenticateRecovery(secret, pin) { key } }
        assertArrayEquals(ByteArray(31), key); assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
        assertNull(authority.bindingOrNull())
    }
    @Test fun failedAttemptCreationWipesBothInputsBeforeFactory() {
        val authority = testPrimaryAuthority { error("controlled clock failure") }
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); var entered = false
        assertThrows(IllegalStateException::class.java) { authority.authenticateRecovery(secret, pin) { entered = true; ByteArray(32) } }
        assertFalse(entered); assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
    }
    @Test fun insufficientCompleteResultCapacityDeniesBeforeFactoryAndReturnsInputSlot() {
        val authority = testPrimaryAuthority(); val pool = ReleasePool(16)
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        val held = pool.reserveAll(Any(), 15)
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); var entered = false
        try {
            assertThrows(IllegalStateException::class.java) { authority.authenticateRecovery(secret, pin) { entered = true; ByteArray(32) } }
            assertFalse(entered); assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
            assertEquals(15, pool.occupied); assertNull(authority.bindingOrNull())
        } finally { held.forEach { it.release() }; authority.revoke(); awaitCleanup(authority) }
        assertEquals(0, pool.occupied)
    }
    @Test fun cleanupCallbackMustActuallyReturnBeforeFinalAdmission() = fixture { authority ->
        val sequence = java.util.concurrent.atomic.AtomicInteger()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            val input = sequence.getAndIncrement() == 0
            ReleaseInvocation({ action(); if (input) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }, returned)
        })
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); val key = ByteArray(32) { 5 }
        val failure = AtomicReference<Throwable>(); val accepted = AtomicReference<Boolean>()
        val worker = Thread {
            try { accepted.set(authority.authenticateRecovery(secret, pin) { key }) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue("cleanup action already wiped the PIN", pin.all { it == '\u0000' })
            assertNull("a completed close action is not actual callback return", authority.bindingOrNull())
            assertNull(accepted.get()); assertTrue(worker.isAlive); assertTrue(pool.occupied > 0)
            authority.revoke(); assertFalse(authority.cleanupComplete)
        } finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertEquals(false, accepted.get()); assertNull(failure.get())
        assertNull(authority.bindingOrNull())
        awaitCleanup(authority); assertArrayEquals(ByteArray(32), key); assertEquals(0, pool.occupied)
    }
    @Test fun repeatedRecoveryCyclesReturnAllCapacityAndWipeOriginals() = fixture { authority ->
        repeat(20) {
            val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); val key = ByteArray(32) { 9 }
            assertTrue(authority.authenticateRecovery(secret, pin) { key })
            assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
            authority.revoke(); awaitCleanup(authority); assertArrayEquals(ByteArray(32), key)
        }
    }

    @Test fun expiredOriginalDeniesMutationBeforeAnyPersistenceEffect() {
        var now = 0L; val authority = testPrimaryAuthority { now }
        val secret = "secret".toCharArray(); val pin = "123456".toCharArray(); var writes = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                authority.authenticateRecovery(secret, pin) { mutate ->
                    now = 60000L
                    mutate { writes++ }
                    ByteArray(32)
                }
            }
            assertEquals(0, writes); assertNull(authority.bindingOrNull())
            assertTrue(secret.all { it == '\u0000' }); assertTrue(pin.all { it == '\u0000' })
        } finally { authority.revoke(); awaitCleanup(authority) }
    }
    @Test fun revokedOriginalDeniesMutationWhileUiTokenCouldStillBeCurrent() = fixture { authority ->
        var writes = 0
        assertThrows(IllegalStateException::class.java) {
            authority.authenticateRecovery("secret".toCharArray(), "123456".toCharArray()) { mutate ->
                authority.revoke(); mutate { writes++ }; ByteArray(32)
            }
        }
        assertEquals(0, writes); assertNull(authority.bindingOrNull())
    }
    @Test fun mutationCallbackCannotBeReusedAfterFactoryReturnOrFromAnotherThread() = fixture { authority ->
        var saved: ((() -> Unit) -> Unit)? = null; var writes = 0
        assertTrue(authority.authenticateRecovery("secret".toCharArray(), "123456".toCharArray()) { mutate ->
            saved = mutate
            val failure = AtomicReference<Throwable>()
            val foreign = Thread { try { mutate { writes++ } } catch (problem: Throwable) { failure.set(problem) } }
            foreign.start(); foreign.join(5000); assertFalse(foreign.isAlive)
            assertTrue(failure.get() is IllegalStateException)
            mutate { writes++ }
            assertThrows(IllegalStateException::class.java) { mutate { writes++ } }
            ByteArray(32)
        })
        assertThrows(IllegalStateException::class.java) { checkNotNull(saved).invoke { writes++ } }
        assertEquals(1, writes)
    }
    @Test fun revocationAfterGuardDoesNotAuthorizeSessionEvenIfAdmittedMutationFinishes() = fixture { authority ->
        var writes = 0; val key = ByteArray(32) { 1 }
        assertThrows(IllegalStateException::class.java) {
            authority.authenticateRecovery("secret".toCharArray(), "123456".toCharArray()) { mutate ->
                mutate { authority.revoke(); writes++ }
                key
            }
        }
        // Current legacy command can finish after the check; this is NOT proof
        // that its KDF/editor were prepared before that check. Full D remains open.
        assertEquals(1, writes); assertNull(authority.bindingOrNull()); awaitCleanup(authority)
        assertArrayEquals(ByteArray(32), key)
    }

}
