package uk.co.traynor.privategallery.core.vpn

/**
 * Process-local ownership seam for the small task-removal service. It holds only the engine,
 * never a profile/configuration, and refuses to touch tunnels this app does not own.
 */
object OwnedVpnTunnelRegistry {
    private var engine: VpnEngine? = null

    @Synchronized fun attach(value: VpnEngine) { engine = value }
    @Synchronized fun detach(value: VpnEngine) { if (engine === value) engine = null }
    @Synchronized fun disconnectOwnedTunnel(): VpnConnectionState? =
        engine?.takeIf { it.ownsTunnel }?.disconnect()
}
