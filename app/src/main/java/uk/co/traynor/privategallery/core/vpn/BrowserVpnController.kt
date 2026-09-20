package uk.co.traynor.privategallery.core.vpn

/** Fail-closed Browser policy. This is not a system-wide Android kill switch. */
class BrowserVpnController(
    private val engine: VpnEngine,
    private val disconnectGraceMillis: Long = 30_000L,
    private val maxReconnectAttempts: Int = 3,
) {
    private var activeProfile: VpnProfile? = null
    private var reconnectAttempts = 0
    private var disconnectAt: Long? = null
    var state: VpnConnectionState = VpnConnectionState.UNCONFIGURED
        private set

    fun select(profile: VpnProfile?) { activeProfile = profile; reconnectAttempts = 0; state = if (profile == null) VpnConnectionState.UNCONFIGURED else VpnConnectionState.DISCONNECTED }
    fun enterBrowser(autoConnect: Boolean, requireVpn: Boolean, now: Long): VpnConnectionState {
        disconnectAt = null
        if (!requireVpn) return state
        val profile = activeProfile ?: return VpnConnectionState.UNCONFIGURED.also { state = it }
        if (!autoConnect) return state
        state = engine.connect(profile)
        return state
    }
    fun onEngineState(observed: VpnConnectionState): VpnConnectionState { state = observed; if (observed == VpnConnectionState.CONNECTED) reconnectAttempts = 0; return state }
    fun browserNetworkingAllowed(requireVpn: Boolean): Boolean = !requireVpn || state == VpnConnectionState.CONNECTED
    fun leaveBrowser(now: Long) { if (engine.ownsTunnel) disconnectAt = now + disconnectGraceMillis }
    fun tick(now: Long): VpnConnectionState? {
        if (disconnectAt != null && now >= disconnectAt!!) { disconnectAt = null; activeProfile?.let { state = engine.disconnect(); return state } }
        return null
    }
    fun tunnelLost(): VpnConnectionState {
        val profile = activeProfile ?: return VpnConnectionState.UNCONFIGURED.also { state = it }
        state = if (reconnectAttempts++ < maxReconnectAttempts) engine.connect(profile).let { VpnConnectionState.RECONNECTING } else VpnConnectionState.FAILED
        return state
    }
    fun onLock() { disconnectAt = null; activeProfile?.let { state = engine.disconnect() } }
}
