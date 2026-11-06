package uk.co.traynor.privategallery.core.security

/** Screen pinning is optional presentation, never managed lock-task mode or authentication. */
object NovaScreenPinningPolicy {
    enum class State { NONE, PINNED, LOCKED, UNKNOWN }
    enum class Decision { REQUEST, REVOKED, NOT_FOREGROUND, DEVICE_LOCKED, POLICY_UNKNOWN, MANAGED, ALREADY_PINNED, LOCKED, STATE_UNKNOWN }

    data class Observation(
        val state: State,
        val current: Boolean,
        val foreground: Boolean,
        val deviceLocked: Boolean?,
        val managedLockTaskPermitted: Boolean?,
    )

    fun decide(observation: Observation): Decision = with(observation) {
        when {
            !current -> Decision.REVOKED
            !foreground -> Decision.NOT_FOREGROUND
            deviceLocked == true -> Decision.DEVICE_LOCKED
            state == State.LOCKED -> Decision.LOCKED
            state == State.PINNED -> Decision.ALREADY_PINNED
            state == State.UNKNOWN -> Decision.STATE_UNKNOWN
            deviceLocked == null || managedLockTaskPermitted == null -> Decision.POLICY_UNKNOWN
            managedLockTaskPermitted -> Decision.MANAGED
            else -> Decision.REQUEST
        }
    }
}
