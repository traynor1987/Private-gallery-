package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class HideContentPolicyTest {
    @Test fun migrationDefaultsVisibleButCorruptExistingStateFailsClosed() {
        assertFalse(HideContentPolicy.decode(false, null))
        assertTrue(HideContentPolicy.decode(true, "true"))
        assertTrue(HideContentPolicy.decode(true, "corrupt"))
    }

    @Test fun discoveryRequiresExactInstalledVersionInstalledSequence() {
        var state = SecretDiscoveryState()
        repeat(5) { state = state.tapInstalled() }
        assertFalse(state.discovered)
        state = state.tapVersion()
        repeat(3) { state = state.tapInstalled() }
        assertFalse(state.discovered)
        state = state.tapInstalled()
        assertTrue(state.discovered)
        assertFalse(state.conceal().discovered)
        assertTrue(HideContentPolicy.decode(true, "true"))
    }

    @Test fun wrongTapOrNavigationResetsPartialDiscovery() {
        var state = SecretDiscoveryState()
        repeat(10) { state = state.tapInstalled() }
        assertFalse(state.discovered)
        repeat(10) { state = state.tapVersion() }
        assertFalse(state.discovered)
        repeat(5) { state = state.tapInstalled() }
        state = state.reset()
        state = state.tapVersion()
        repeat(4) { state = state.tapInstalled() }
        assertFalse(state.discovered)
        state = SecretDiscoveryState()
        repeat(5) { state = state.tapInstalled() }
        state = state.tapVersion().tapVersion()
        repeat(4) { state = state.tapInstalled() }
        assertFalse(state.discovered)
        state = SecretDiscoveryState()
        repeat(4) { state = state.tapInstalled() }
        state = state.tapVersion()
        repeat(4) { state = state.tapInstalled() }
        assertFalse(state.discovered)
    }

    @Test fun contentGateNeverPresentsExistingValuesWhileHidden() {
        val actual = listOf("private photo", "collection")
        assertEquals(emptyList<String>(), HideContentPolicy.present(true, actual))
        assertEquals(actual, HideContentPolicy.present(false, actual))
        assertEquals(0, HideContentPolicy.count(true, 63))
        assertEquals(63, HideContentPolicy.count(false, 63))
    }

}
