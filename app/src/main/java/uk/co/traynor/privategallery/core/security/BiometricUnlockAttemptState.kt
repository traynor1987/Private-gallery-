package uk.co.traynor.privategallery.core.security

/** A callback belongs to exactly one prompt and one locked-session generation. */
class BiometricUnlockAttemptState {
    private var generation = 0L
    private var active: Long? = null
    fun begin(): Long = (++generation).also { active = it }
    fun isCurrent(attempt: Long): Boolean = active == attempt
    fun cancel(attempt: Long) { if (active == attempt) { active = null; generation++ } }
    fun lock() { active = null; generation++ }
}
