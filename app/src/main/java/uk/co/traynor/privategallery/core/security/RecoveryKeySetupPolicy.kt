package uk.co.traynor.privategallery.core.security

/** Pending setup can restart only after legitimate authenticated Primary access. */
object RecoveryKeySetupPolicy {
    fun shouldShowAfterUnlock(state: RecoverySetupState): Boolean =
        state == RecoverySetupState.NOT_CONFIGURED || state == RecoverySetupState.PENDING_CONFIRMATION

    /** Compatibility for older callers; state-aware callers must handle corrupt state separately. */
    fun shouldShowAfterUnlock(recoveryKeyConfigured: Boolean): Boolean = !recoveryKeyConfigured
}
