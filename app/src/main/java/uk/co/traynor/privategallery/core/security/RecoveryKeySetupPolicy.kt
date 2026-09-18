package uk.co.traynor.privategallery.core.security

/**
 * Recovery setup is a one-time migration. Every successful unlock path must
 * consult this policy; biometric unlock must not bypass it.
 */
object RecoveryKeySetupPolicy {
    fun shouldShowAfterUnlock(recoveryKeyConfigured: Boolean): Boolean = !recoveryKeyConfigured
}
