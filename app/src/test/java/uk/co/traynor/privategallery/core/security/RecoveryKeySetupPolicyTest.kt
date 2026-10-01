package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryKeySetupPolicyTest {
    @Test
    fun `pending setup must be offered again after authenticated unlock`() {
        assertTrue(RecoveryKeySetupPolicy.shouldShowAfterUnlock(RecoverySetupState.NOT_CONFIGURED))
        assertTrue(RecoveryKeySetupPolicy.shouldShowAfterUnlock(RecoverySetupState.PENDING_CONFIRMATION))
        assertFalse(RecoveryKeySetupPolicy.shouldShowAfterUnlock(RecoverySetupState.CONFIRMED))
        assertFalse(RecoveryKeySetupPolicy.shouldShowAfterUnlock(RecoverySetupState.CORRUPT))
    }

    @Test
    fun `an existing vault needs recovery-key setup after biometric unlock only when absent`() {
        assertTrue(RecoveryKeySetupPolicy.shouldShowAfterUnlock(recoveryKeyConfigured = false))
        assertFalse(RecoveryKeySetupPolicy.shouldShowAfterUnlock(recoveryKeyConfigured = true))
    }
}
