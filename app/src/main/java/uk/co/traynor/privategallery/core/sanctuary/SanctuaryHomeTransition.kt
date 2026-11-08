package uk.co.traynor.privategallery.core.sanctuary

/** One Home request per confirmed blocked foreground transition. State is scoped to one session. */
internal class SanctuaryHomeTransition {
    enum class Display { NONE, BLOCKED, REDIRECTING, HELD_ON_HOME, DEGRADED }

    private var blockedPackage: String? = null
    private var attempted = false
    private var accepted = false
    private var confirmationDeadline = 0L
    private var held = false

    fun observe(foreground: String?, blocked: Boolean, home: String?, now: Long): Display {
        if (foreground == null) return if (pending(now)) Display.REDIRECTING else Display.NONE
        if (blocked) {
            if (foreground != blockedPackage || held) {
                reset()
                blockedPackage = foreground
            }
            return when {
                !attempted -> Display.BLOCKED
                pending(now) -> Display.REDIRECTING
                else -> Display.DEGRADED
            }
        }
        if (foreground == home && (held || pending(now))) {
            held = true
            return Display.HELD_ON_HOME
        }
        reset()
        return Display.NONE
    }

    fun beginHome(foreground: String, now: Long): Boolean {
        if (foreground != blockedPackage || attempted) return false
        attempted = true
        confirmationDeadline = now + HOME_CONFIRMATION_MILLIS
        return true
    }

    fun finishHome(actionAccepted: Boolean) {
        accepted = actionAccepted
    }

    fun dismissHomeHold() {
        if (held) reset()
        // An in-flight request must not newly pin the overlay on Home after an explicit launch.
        else accepted = false
    }

    fun reset() {
        blockedPackage = null
        attempted = false
        accepted = false
        confirmationDeadline = 0L
        held = false
    }

    private fun pending(now: Long): Boolean = accepted && now < confirmationDeadline

    private companion object {
        const val HOME_CONFIRMATION_MILLIS = 2_500L
    }
}
