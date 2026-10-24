package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptPolicyTest {
    @Test fun `cancelled entry survives repeated starts but completed session gets fresh prompt`() {
        var attempted=true // User cancelled, then activity stops/recreates while still locked.
        repeat(3) {
            attempted=BiometricPromptPolicy.attemptedAfterLock(attempted,false)
            assertFalse(BiometricPromptPolicy.shouldAutoPrompt(true,true,attempted))
        }
        attempted=BiometricPromptPolicy.attemptedAfterLock(attempted,true)
        assertTrue(BiometricPromptPolicy.shouldAutoPrompt(true,true,attempted))
        assertFalse(BiometricPromptPolicy.shouldAutoPrompt(true,false,attempted))
    }

    @Test fun `deliberate PIN entry invalidates queued automatic prompt before dispatch`() {
        val attempts=BiometricUnlockAttemptState()
        val queued=attempts.begin()
        attempts.lock()
        assertFalse(attempts.isCurrent(queued))
        val explicitRetry=attempts.begin()
        assertTrue(attempts.isCurrent(explicitRetry))
        assertFalse(attempts.isCurrent(queued))
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
