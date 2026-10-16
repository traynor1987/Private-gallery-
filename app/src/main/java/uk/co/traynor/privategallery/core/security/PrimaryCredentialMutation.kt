package uk.co.traynor.privategallery.core.security

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Fixed Primary credential purposes; never a container or preference-file selector. */
internal enum class PrimaryCredentialPurpose { SETUP, CHANGE_PIN, RECOVERY, RESTORE, BIOMETRIC, RECOVERY_SLOT }

/** Prerequisite for the complete writer migration. Existing writers do not yet use this slot. */
internal class PrimaryCredentialMutationRegistry {
    private val current = AtomicReference<Mutation?>()

    internal inner class Mutation internal constructor(
        private val terminal: RetirementAcknowledgement,
        val purpose: PrimaryCredentialPurpose,
    ) {
        private val decided = AtomicBoolean()
        private val ambiguous = AtomicBoolean()
        fun requireHeld() { check(current.get() === this && !decided.get()) { "Primary credential mutation unavailable" } }
        fun decideFinished() { requireHeld(); check(decided.compareAndSet(false, true)) }
        fun markAmbiguous() { requireHeld(); ambiguous.set(true) }
        /** The disk record may be rolled back, but that does not acknowledge an
         * earlier provider invocation whose completion was ambiguous. */
        fun decideRolledBack() {
            requireHeld(); check(purpose == PrimaryCredentialPurpose.RESTORE)
            check(decided.compareAndSet(false, true))
        }
        // Only the exact original's success-only terminal marker observes all
        // release callbacks returning. Retain no operation/key/resource graph.
        internal fun replaceable(): Boolean = decided.get() && !ambiguous.get() && terminal.isComplete
    }

    fun claim(original: ReleaseReservation, child: AutoCloseable, purpose: PrimaryCredentialPurpose): Mutation {
        original.requireNativeUse(0, child)
        val prior = current.get()
        check(prior == null || prior.replaceable()) { "Primary credential mutation in progress or unavailable" }
        val next = Mutation(original.terminalRetirement, purpose)
        check(current.compareAndSet(prior, next)) { "Primary credential mutation in progress" }
        return next
    }
}

/** Fixed production process. Callers cannot choose a registry when creating a command. */
internal object PrimaryCredentialMutationSlot {
    internal val process = PrimaryCredentialMutationRegistry()
}
