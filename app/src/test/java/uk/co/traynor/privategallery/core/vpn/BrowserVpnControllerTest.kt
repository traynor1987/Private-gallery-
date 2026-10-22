package uk.co.traynor.privategallery.core.vpn

import org.junit.Assert.*
import org.junit.Test

class BrowserVpnControllerTest {
    private val backend = FakeWireGuardBackend()
    private val wireGuard = WireGuardVpnEngine(backend)
    private val controller = BrowserVpnController(wireGuard)
    private val profile = VpnProfile("wg", "Personal", VpnProtocol.WIREGUARD, "[Interface]\nPrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\n[Peer]\nPublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\nAllowedIPs = 0.0.0.0/0")

    @Test fun `failed live tunnel remains owned for lifecycle cleanup`() {
        var disconnects=0
        val retained = object : WireGuardBackend {
            override val hasTunnel = true
            override fun connect(profile: VpnProfile,onState:(VpnConnectionState)->Unit) { onState(VpnConnectionState.FAILED) }
            override fun disconnect(onState:(VpnConnectionState)->Unit) { disconnects++;onState(VpnConnectionState.FAILED) }
        }
        val engine=WireGuardVpnEngine(retained)
        val policy=BrowserVpnController(engine)
        policy.select(profile);policy.enterBrowser(true,true,0)
        assertTrue(engine.ownsTunnel)
        assertFalse(policy.browserNetworkingAllowed(true))
        policy.onTaskRemoved()
        assertEquals(1,disconnects)
        assertTrue(engine.ownsTunnel)
    }
    @Test fun `cancel pending connection rejects late success`() {
        var pending: ((VpnConnectionState) -> Unit)? = null
        val delayed = object : WireGuardBackend {
            override fun connect(profile: VpnProfile, onState: (VpnConnectionState) -> Unit) { pending = onState }
            override fun disconnect(onState: (VpnConnectionState) -> Unit) { onState(VpnConnectionState.DISCONNECTED) }
        }
        val engine = WireGuardVpnEngine(delayed)
        val policy = BrowserVpnController(engine)
        policy.select(profile); policy.enterBrowser(true, true, 0)
        assertTrue(engine.ownsTunnel)
        policy.onTaskRemoved()
        pending!!(VpnConnectionState.CONNECTED)
        assertEquals(VpnConnectionState.DISCONNECTED, engine.state)
        assertFalse(policy.browserNetworkingAllowed(true))
    }
    @Test fun `engine revocation closes gate before presentation callback`() {
        controller.select(profile); controller.enterBrowser(true, true, 0)
        backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)
        backend.emit(VpnConnectionState.FAILED)
        assertFalse(controller.browserNetworkingAllowed(true))
    }
    @Test fun `browser remains blocked until engine confirms connected`() {
        controller.select(profile)
        assertEquals(VpnConnectionState.CONNECTING, controller.enterBrowser(true, true, 0))
        assertFalse(controller.browserNetworkingAllowed(true))
        backend.emit(VpnConnectionState.CONNECTED)
        controller.onEngineState(wireGuard.state)
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
        controller.select(profile); controller.enterBrowser(true, true, 0); backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)
        controller.leaveBrowser(100); assertNull(controller.tick(30_099)); controller.enterBrowser(false, true, 30_100); assertNull(controller.tick(31_000))
    }
    @Test fun `background schedules the same owned tunnel grace disconnect`() {
        controller.select(profile); controller.enterBrowser(true, true, 0); backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)

        controller.onAppBackgrounded(100)

        assertNull(controller.tick(30_099))
        // The fake backend confirms teardown synchronously; a real GoBackend may expose the
        // intermediate DISCONNECTING state first. In either case Browser is already fail-closed.
        assertEquals(VpnConnectionState.DISCONNECTED, controller.tick(30_100))
        assertFalse(controller.browserNetworkingAllowed(true))
    }
    @Test fun `browser reentry after background cancels the owned tunnel grace disconnect`() {
        controller.select(profile); controller.enterBrowser(true, true, 0); backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)

        controller.onAppBackgrounded(100)
        assertEquals(VpnConnectionState.CONNECTED, controller.enterBrowser(true, true, 20_000))

        assertNull(controller.tick(30_100))
        assertTrue(controller.browserNetworkingAllowed(true))
    }
    @Test fun `known task removal immediately disconnects only the owned tunnel`() {
        controller.select(profile); controller.enterBrowser(true, true, 0); backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)

        assertEquals(VpnConnectionState.DISCONNECTED, controller.onTaskRemoved())
        assertFalse(controller.browserNetworkingAllowed(true))
    }
    @Test fun `task removal registry never disconnects a tunnel it does not own`() {
        val external = object : VpnEngine {
            override val protocol = VpnProtocol.WIREGUARD
            override val ownsTunnel = false
            override val state = VpnConnectionState.CONNECTED
            var disconnects = 0
            override fun connect(profile: VpnProfile) = state
            override fun disconnect(): VpnConnectionState { disconnects++; return VpnConnectionState.DISCONNECTED }
        }
        OwnedVpnTunnelRegistry.attach(external)

        assertNull(OwnedVpnTunnelRegistry.disconnectOwnedTunnel())
        assertEquals(0, external.disconnects)
        OwnedVpnTunnelRegistry.detach(external)
    }
    @Test fun `reselecting the active profile preserves a connected owned tunnel`() {
        controller.select(profile); controller.enterBrowser(true, true, 0); backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)

        controller.select(profile)

        assertEquals(VpnConnectionState.CONNECTED, controller.state)
        assertTrue(controller.browserNetworkingAllowed(true))
    }
    @Test fun `lock disconnects while retaining the selected profile for a later reconnect`() {
        assertFalse(controller.hasSelectedProfile)
        controller.select(profile);
        assertTrue(controller.hasSelectedProfile)
         controller.enterBrowser(true, true, 0); backend.emit(VpnConnectionState.CONNECTED); controller.onEngineState(VpnConnectionState.CONNECTED)

        controller.onLock()
        assertTrue(controller.hasSelectedProfile)
        assertEquals(VpnConnectionState.DISCONNECTED, controller.state)
        assertFalse(controller.browserNetworkingAllowed(true))

        assertEquals(VpnConnectionState.CONNECTING, controller.enterBrowser(true, true, 100))
    }
    @Test fun `profile parser rejects OpenVPN profiles for this WireGuard-only release`() {
        val result = VpnProfileParser.import("x", "client\nremote x 1194") as VpnProfileImportResult.Rejected
        assertEquals("OpenVPN 2 profiles are not supported in this release", result.reason)
    }
    @Test fun `profile parser accepts standard WireGuard`() {
        assertTrue(VpnProfileParser.import("x", profile.privateConfiguration) is VpnProfileImportResult.Accepted)
    }
    @Test fun `profile parser rejects malformed WireGuard key material`() {
        assertTrue(VpnProfileParser.import("x", "[Interface]\nPrivateKey = nope\n[Peer]\nPublicKey = nope\nAllowedIPs = 0.0.0.0/0") is VpnProfileImportResult.Rejected)
    }

    private class FakeWireGuardBackend : WireGuardBackend {
        private var stateSink: ((VpnConnectionState) -> Unit)? = null
        override fun connect(profile: VpnProfile, onState: (VpnConnectionState) -> Unit) { stateSink = onState }
        override fun disconnect(onState: (VpnConnectionState) -> Unit) = onState(VpnConnectionState.DISCONNECTED)
        fun emit(state: VpnConnectionState) { stateSink?.invoke(state) }
    }
}
