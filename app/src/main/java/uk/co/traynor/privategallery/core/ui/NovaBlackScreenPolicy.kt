package uk.co.traynor.privategallery.core.ui

/** Ephemeral foreground presentation authority. There is deliberately no persisted activation. */
internal class NovaBlackScreenPolicy {
    enum class State { IDLE, PENDING, ACTIVE, CLOSED }
    var state: State = State.IDLE
        private set
    val active: Boolean get() = state == State.ACTIVE

    fun request(authorized: Boolean, foreground: Boolean, focused: Boolean): Boolean {
        if (state == State.CLOSED) return false
        if (!authorized || !foreground) { dismiss(); return false }
        state = if (focused) State.ACTIVE else State.PENDING
        return true
    }

    fun windowFocusChanged(focused: Boolean, authorized: Boolean, foreground: Boolean) {
        if (state == State.CLOSED || state == State.IDLE) return
        if (!authorized || !foreground || (state == State.ACTIVE && !focused)) {
            dismiss()
        } else if (state == State.PENDING && focused) {
            state = State.ACTIVE
        }
    }

    fun canInteract(authorized: Boolean): Boolean {
        if (!authorized) dismiss()
        return authorized && active
    }

    fun dismiss() { if (state != State.CLOSED) state = State.IDLE }
    fun pause() = dismiss()
    fun close() { state = State.CLOSED }
}
