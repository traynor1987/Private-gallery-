package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryHomeTransition.Display

class SanctuaryHomeTransitionTest {
    private val blocked = "com.whatsapp"
    private val home = "com.sec.android.app.launcher"
    private val state = SanctuaryHomeTransition()

    @Test fun oneHomeAttemptUntilANewForegroundTransition() {
        assertEquals(Display.BLOCKED, state.observe(blocked, true, home, 0))
        assertTrue(state.beginHome(blocked, 0))
        state.finishHome(true)
        assertFalse(state.beginHome(blocked, 1))
        assertEquals(Display.REDIRECTING, state.observe(blocked, true, home, 1))
        assertEquals(Display.HELD_ON_HOME, state.observe(home, false, home, 300))
        assertEquals(Display.HELD_ON_HOME, state.observe(home, false, home, 299_999))
        // Expiry clears the overlay state. No Home action is issued on expiry.
        state.reset()
        assertEquals(Display.NONE, state.observe(home, false, home, 300_000))
        assertFalse(state.beginHome(blocked, 300_000))
    }

    @Test fun failedAndUnconfirmedHomeKeepTheOriginalBlockerWithoutLooping() {
        state.observe(blocked, true, home, 0)
        assertTrue(state.beginHome(blocked, 0))
        state.finishHome(false)
        assertEquals(Display.DEGRADED, state.observe(blocked, true, home, 100))
        assertFalse(state.beginHome(blocked, 100))
        assertEquals(Display.NONE, state.observe(home, false, home, 200))
        state.observe(blocked, true, home, 300)
        assertTrue(state.beginHome(blocked, 300))
        state.finishHome(true)
        assertEquals(Display.DEGRADED, state.observe(blocked, true, home, 4_000))
        assertFalse(state.beginHome(blocked, 4_001))
        assertEquals(Display.NONE, state.observe(home, false, home, 4_002))
    }

    @Test fun approvedAppsAndProtectedSettingsEndTheHomeHold() {
        state.observe(blocked, true, home, 0)
        assertTrue(state.beginHome(blocked, 0))
        state.finishHome(true)
        assertEquals(Display.HELD_ON_HOME, state.observe(home, false, home, 50))
        assertEquals(Display.NONE, state.observe("com.google.android.apps.maps", false, home, 51))
        assertEquals(Display.NONE, state.observe("com.android.settings", false, home, 52))
        assertFalse(state.beginHome(blocked, 53))
        assertEquals(Display.BLOCKED, state.observe(blocked, true, home, 54))
        assertTrue(state.beginHome(blocked, 54))
    }

    @Test fun anUnknownForegroundOnlyKeepsAnInFlightHomeRequestBriefly() {
        assertEquals(Display.NONE, state.observe(null, false, home, 0))
        state.observe(blocked, true, home, 1)
        assertTrue(state.beginHome(blocked, 1))
        state.finishHome(true)
        assertEquals(Display.REDIRECTING, state.observe(null, false, home, 10))
        assertEquals(Display.NONE, state.observe(null, false, home, 4_000))
        assertFalse(state.beginHome(blocked, 4_001))
        assertEquals(Display.DEGRADED, state.observe(blocked, true, home, 4_002))
    }

    @Test fun returningToTheSameBlockedAppAfterHomeIsANewTransition() {
        state.observe(blocked, true, home, 0)
        assertTrue(state.beginHome(blocked, 0))
        state.finishHome(true)
        assertEquals(Display.HELD_ON_HOME, state.observe(home, false, home, 100))
        assertEquals(Display.BLOCKED, state.observe(blocked, true, home, 110))
        assertTrue(state.beginHome(blocked, 110))
    }
}
