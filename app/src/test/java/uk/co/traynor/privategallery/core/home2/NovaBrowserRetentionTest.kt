package uk.co.traynor.privategallery.core.home2

import org.junit.Assert.*
import org.junit.Test

class NovaBrowserRetentionTest {
    private data class State(
        val resumed: Boolean = true,
        val started: Boolean = true,
        val interactive: Boolean = true,
        val concealed: Boolean = false,
        val hidden: Boolean = false,
        val authenticated: Boolean = true,
        val profileClearing: Boolean = false,
    ) {
        fun retain() = NovaBrowserRetention.retainPrimary(resumed, started, interactive, concealed, hidden, authenticated, profileClearing)
    }

    @Test fun home_and_native_switches_keep_media_but_actual_stop_cannot_retain_it() {
        val foreground = State()
        // Home and native destinations intentionally share the same Activity authority.
        assertTrue("Home overlay", foreground.retain())
        assertTrue("Native app detaches browser UI", foreground.retain())
        val stopped = foreground.copy(resumed = false, started = false)
        assertFalse("Detached browser still pauses when the Activity stops", stopped.retain())
        assertFalse("Starting without resuming cannot retain", stopped.copy(started = true).retain())
        assertTrue("Authenticated foreground can be admitted again", foreground.retain())
    }

    @Test fun screen_off_lock_panic_hidden_and_profile_clear_override_foreground_retention() {
        val foreground = State()
        val revoked = listOf(
            "screen off" to foreground.copy(interactive = false),
            "locked or timed out" to foreground.copy(authenticated = false),
            "panic concealment" to foreground.copy(concealed = true),
            "Hidden selected" to foreground.copy(hidden = true),
            "shared profile clearing" to foreground.copy(profileClearing = true),
            "Activity paused" to foreground.copy(resumed = false),
            "Activity stopped" to foreground.copy(started = false),
        )
        revoked.forEach { (reason, state) -> assertFalse(reason, state.retain()) }
    }
}
