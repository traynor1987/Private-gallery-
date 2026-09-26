package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class HideContentPolicyTest {
    @Test fun migrationDefaultsVisibleButCorruptExistingStateFailsClosed() {
        assertFalse(HideContentPolicy.decode(false, null))
        assertTrue(HideContentPolicy.decode(true, "true"))
        assertTrue(HideContentPolicy.decode(true, "corrupt"))
    }

    @Test fun discoveryRequiresTenTapsAndConcealmentDoesNotChangeHiddenState() {
        var state = SecretDiscoveryState()
        repeat(9) { state = state.tap() }
        assertFalse(state.discovered)
        assertEquals(1, state.remaining)
        state = state.tap()
        assertTrue(state.discovered)
        assertEquals(0, state.remaining)
        assertFalse(state.conceal().discovered)
        assertTrue(HideContentPolicy.decode(true, "true"))
    }

    @Test fun contentGateNeverPresentsExistingValuesWhileHidden() {
        val actual = listOf("private photo", "collection")
        assertEquals(emptyList<String>(), HideContentPolicy.present(true, actual))
        assertEquals(actual, HideContentPolicy.present(false, actual))
        assertEquals(0, HideContentPolicy.count(true, 63))
        assertEquals(63, HideContentPolicy.count(false, 63))
    }

    @Test fun sensitiveActionsAlwaysNeedFreshAuthentication() {
        assertFalse(HideContentPolicy.canReveal(false))
        assertTrue(HideContentPolicy.canReveal(true))
        assertFalse(HideContentPolicy.canEnableScreenshots(false))
        assertTrue(HideContentPolicy.canEnableScreenshots(true))
    }
}
