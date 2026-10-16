package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptPolicyTest {
    @Test fun terminalCallbacksCanClaimOnlyTheirExactCurrentAttemptOnce() {
        val state = BiometricUnlockAttemptState()
        val old = state.begin(); val current = state.begin()
        assertFalse(state.claim(old)); assertTrue(state.isCurrent(current))
        assertTrue(state.claim(current)); assertFalse(state.claim(current))
        val replacement = state.begin()
        assertFalse(state.claim(current)); assertTrue(state.isCurrent(replacement))
        state.lock(); assertFalse(state.claim(replacement))
    }
    @Test fun simultaneousTerminalCallbacksHaveOnlyOneClaimant() {
        val state = BiometricUnlockAttemptState(); val attempt = state.begin()
        val ready = java.util.concurrent.CountDownLatch(2)
        val start = java.util.concurrent.CountDownLatch(1)
        val claims = java.util.concurrent.atomic.AtomicInteger()
        val workers = List(2) { Thread {
            ready.countDown()
            check(start.await(5, java.util.concurrent.TimeUnit.SECONDS))
            if (state.claim(attempt)) claims.incrementAndGet()
        } }
        workers.forEach { it.start() }
        try { assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
        finally { start.countDown(); workers.forEach { it.join(5000) } }
        workers.forEach { assertFalse(it.isAlive) }
        org.junit.Assert.assertEquals(1, claims.get())
    }
    @Test fun oldCryptoResultReroutedIntoReplacementCallbackCannotClaimReplacement() {
        val state = BiometricUnlockAttemptState(); val oldCipher = Any(); val replacementCipher = Any()
        state.begin(oldCipher)
        val replacement = state.begin(replacementCipher)
        assertFalse(state.claim(replacement, oldCipher))
        assertFalse(state.claim(replacement, null))
        assertTrue(state.isCurrent(replacement))
        assertTrue(state.claim(replacement, replacementCipher))
        assertFalse(state.claim(replacement, replacementCipher))
    }
    @Test fun foreignPromptCannotMatchOrCancelAnEqualNumberedLocalAttempt() {
        val local = BiometricUnlockAttemptState(); val other = BiometricUnlockAttemptState()
        val mine = local.begin(); val foreign = other.begin()
        assertFalse(local.isCurrent(foreign))
        local.cancel(foreign)
        assertTrue(local.isCurrent(mine))
    }
    @Test fun `explicit lock invalidates old callback and fresh biometric attempt can unlock`() {
        val attempts = BiometricUnlockAttemptState()
        val cold = attempts.begin()
        assertTrue(attempts.isCurrent(cold))
        attempts.lock()
        assertFalse(attempts.isCurrent(cold))
        val afterLock = attempts.begin()
        assertTrue(attempts.isCurrent(afterLock))
        attempts.cancel(afterLock)
        assertFalse(attempts.isCurrent(afterLock))
        val retry = attempts.begin()
        assertTrue(attempts.isCurrent(retry))
        attempts.lock()
        assertTrue(attempts.isCurrent(attempts.begin()))
    }
    @Test fun `fresh locked entry offers enrolled biometric envelope despite stale capability snapshot`() {
        assertTrue(BiometricPromptPolicy.shouldAutoPrompt(
            isLocked = true,
            biometricEnabled = true,
            alreadyPromptedForLockEntry = false,
        ))
    }

    @Test fun `cancelled or recomposed lock screen does not auto-prompt again`() {
        assertFalse(BiometricPromptPolicy.shouldAutoPrompt(
            isLocked = true,
            biometricEnabled = true,
            alreadyPromptedForLockEntry = true,
        ))
    }

    @Test fun `disabled biometrics keep PIN as the normal path`() {
        assertFalse(BiometricPromptPolicy.shouldAutoPrompt(true, false, false))
    }
}
