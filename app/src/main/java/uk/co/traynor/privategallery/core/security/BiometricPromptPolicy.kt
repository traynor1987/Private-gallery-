package uk.co.traynor.privategallery.core.security

/** Keeps automatic biometric prompting a one-shot action for each locked entry. */
object BiometricPromptPolicy {
    /** Re-entering an already locked screen preserves cancellation; a newly ended session starts a fresh entry. */
    fun attemptedAfterLock(alreadyAttempted:Boolean, endedAuthenticatedSession:Boolean):Boolean =
        alreadyAttempted && !endedAuthenticatedSession

    fun shouldAutoPrompt(
        isLocked: Boolean,
        biometricEnabled: Boolean,
        alreadyPromptedForLockEntry: Boolean,
    ): Boolean = isLocked && biometricEnabled && !alreadyPromptedForLockEntry
}
