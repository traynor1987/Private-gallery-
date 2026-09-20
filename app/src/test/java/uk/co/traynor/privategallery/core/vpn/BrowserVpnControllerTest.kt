package uk.co.traynor.privategallery.core.vpn

import org.junit.Assert.*
import org.junit.Test

class BrowserVpnControllerTest {
    private val wireGuard = WireGuardVpnEngine()
    private val openVpn = OpenVpn2Engine()
    private val controller = BrowserVpnController(mapOf(VpnProtocol.WIREGUARD to wireGuard, VpnProtocol.OPENVPN2 to openVpn))
    private val profile = VpnProfile("wg", "Personal", VpnProtocol.WIREGUARD, "[Interface]\nPrivateKey = x\n[Peer]\nPublicKey = y\nAllowedIPs = 0.0.0.0/0")

    @Test fun `browser remains blocked until engine confirms connected`() {
        controller.select(profile)
        assertEquals(VpnConnectionState.CONNECTING, controller.enterBrowser(true, true, 0))
        assertFalse(controller.browserNetworkingAllowed(true))
        controller.onEngineState(wireGuard.onTunnelState(connected = true))
        assertTrue(controller.browserNetworkingAllowed(true))
    }
    @Test fun `drop fails closed and reconnect is bounded`() {
        controller.select(profile); controller.enterBrowser(true, true, 0)
        assertEquals(VpnConnectionState.RECONNECTING, controller.tunnelLost())
        assertFalse(controller.browserNetworkingAllowed(true))
        controller.tunnelLost(); controller.tunnelLost(); controller.tunnelLost()
        assertEquals(VpnConnectionState.FAILED, controller.tunnelLost())
    }
    @Test fun `owned tunnel disconnect waits for grace and reentry cancels it`() {
        controller.select(profile); controller.enterBrowser(true, true, 0); wireGuard.onTunnelState(true); controller.onEngineState(VpnConnectionState.CONNECTED)
        controller.leaveBrowser(100); assertNull(controller.tick(30_099)); controller.enterBrowser(false, true, 30_100); assertNull(controller.tick(31_000))
    }
    @Test fun `profile parser rejects unsafe OpenVPN directives without echoing configuration`() {
        val result = VpnProfileParser.import("x", "client\nremote x 1194\nplugin evil") as VpnProfileImportResult.Rejected
        assertEquals("Unsupported OpenVPN directive: plugin", result.reason)
    }
    @Test fun `profile parser accepts standard WireGuard`() {
        assertTrue(VpnProfileParser.import("x", profile.privateConfiguration) is VpnProfileImportResult.Accepted)
    }
}
