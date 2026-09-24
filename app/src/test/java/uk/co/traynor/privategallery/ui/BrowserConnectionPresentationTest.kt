package uk.co.traynor.privategallery.ui

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vpn.VpnConnectionState

class BrowserConnectionPresentationTest {
    @Test fun onlyConnectedOrOptionalVpnShowsContent() {
        VpnConnectionState.entries.forEach { state ->
            assertEquals(state != VpnConnectionState.CONNECTED, BrowserConnectionPresentation.from(true, state).blocked)
            assertFalse(BrowserConnectionPresentation.from(false, state).blocked)
        }
    }
    @Test fun progressOnlyRepresentsActualWork() {
        assertFalse(BrowserConnectionPresentation.from(true, VpnConnectionState.DISCONNECTED).busy)
        assertTrue(BrowserConnectionPresentation.from(true, VpnConnectionState.CONNECTING).busy)
        assertTrue(BrowserConnectionPresentation.from(true, VpnConnectionState.RECONNECTING).busy)
        assertEquals("Try again", BrowserConnectionPresentation.from(true, VpnConnectionState.FAILED).action)
    }
    @Test fun permissionAndConfigurationHaveSpecificActions() {
        assertEquals("Allow VPN connection", BrowserConnectionPresentation.from(true, VpnConnectionState.FAILED, permissionRequired = true).action)
        assertEquals("Set up VPN", BrowserConnectionPresentation.from(true, VpnConnectionState.UNCONFIGURED).action)
        val preparing = BrowserConnectionPresentation.from(true, VpnConnectionState.UNCONFIGURED, preparing = true)
        assertTrue(preparing.busy)
        assertNull(preparing.action)
    }
}
