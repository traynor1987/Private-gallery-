package uk.co.traynor.privategallery.core.security

/** Keeps automatic biometric prompting a one-shot action for each locked entry. */
object BiometricPromptPolicy {
    fun shouldAutoPrompt(
        isLocked: Boolean,
        biometricEnabled: Boolean,
        alreadyPromptedForLockEntry: Boolean,
    ): Boolean = isLocked && biometricEnabled && !alreadyPromptedForLockEntry
}
