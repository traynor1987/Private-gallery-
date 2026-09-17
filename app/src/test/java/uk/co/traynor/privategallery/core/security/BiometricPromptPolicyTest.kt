package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptPolicyTest {
    @Test fun `fresh locked entry auto-prompts once when biometrics are enabled and available`() {
        assertTrue(BiometricPromptPolicy.shouldAutoPrompt(
            isLocked = true,
            biometricEnabled = true,
            biometricAvailable = true,
            alreadyPromptedForLockEntry = false,
        ))
    }

    @Test fun `cancelled or recomposed lock screen does not auto-prompt again`() {
        assertFalse(BiometricPromptPolicy.shouldAutoPrompt(
            isLocked = true,
            biometricEnabled = true,
            biometricAvailable = true,
            alreadyPromptedForLockEntry = true,
        ))
    }

    @Test fun `unavailable or disabled biometrics keep PIN as the normal path`() {
        assertFalse(BiometricPromptPolicy.shouldAutoPrompt(true, false, true, false))
        assertFalse(BiometricPromptPolicy.shouldAutoPrompt(true, true, false, false))
    }
}
