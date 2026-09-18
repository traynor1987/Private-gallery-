package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryKeySetupPolicyTest {
    @Test
    fun `an existing vault needs recovery-key setup after biometric unlock only when absent`() {
        assertTrue(RecoveryKeySetupPolicy.shouldShowAfterUnlock(recoveryKeyConfigured = false))
        assertFalse(RecoveryKeySetupPolicy.shouldShowAfterUnlock(recoveryKeyConfigured = true))
    }
}
