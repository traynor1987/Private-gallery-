package uk.co.traynor.privategallery.core.vpn

import java.util.UUID

enum class VpnProtocol { WIREGUARD, OPENVPN2 }

/** Browser policy deliberately consumes only these trusted engine states. */
enum class VpnConnectionState {
    UNCONFIGURED, DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED, DISCONNECTING,
}

data class VpnProfile(
    val id: String,
    val displayName: String,
    val protocol: VpnProtocol,
    /** Never render or log this validated private configuration. */
    val privateConfiguration: String,
)

sealed interface VpnProfileImportResult {
    data class Accepted(val profile: VpnProfile) : VpnProfileImportResult
    data class Rejected(val reason: String) : VpnProfileImportResult
}

interface VpnEngine {
    val protocol: VpnProtocol
    val ownsTunnel: Boolean
    val state: VpnConnectionState
    fun connect(profile: VpnProfile): VpnConnectionState
    fun disconnect(): VpnConnectionState
}

/** Strictly provider-neutral profile validation. It deliberately does not retain provider data. */
object VpnProfileParser {
    fun import(displayName: String, rawConfiguration: String, id: String = UUID.randomUUID().toString()): VpnProfileImportResult {
        val name = displayName.trim()
        if (name.isEmpty()) return VpnProfileImportResult.Rejected("A profile name is required")
        val protocol = when {
            rawConfiguration.lineSequence().any { it.trim().equals("[Interface]", ignoreCase = true) } -> VpnProtocol.WIREGUARD
            rawConfiguration.lineSequence().any { it.trim().startsWith("client") } || rawConfiguration.lineSequence().any { it.trim().startsWith("remote ") } -> VpnProtocol.OPENVPN2
            else -> return VpnProfileImportResult.Rejected("Unsupported VPN profile format")
        }
        val reason = when (protocol) {
            VpnProtocol.WIREGUARD -> validateWireGuard(rawConfiguration)
            VpnProtocol.OPENVPN2 -> validateOpenVpn2(rawConfiguration)
        }
        return if (reason == null) VpnProfileImportResult.Accepted(VpnProfile(id, name, protocol, rawConfiguration))
        else VpnProfileImportResult.Rejected(reason)
    }

    private fun validateWireGuard(config: String): String? {
        val lines = config.lineSequence().map { it.substringBefore('#').substringBefore(';').trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.none { it.equals("[Interface]", true) } || lines.none { it.equals("[Peer]", true) }) return "WireGuard needs Interface and Peer sections"
        if (lines.none { it.startsWith("PrivateKey", true) && it.contains('=') }) return "WireGuard Interface private key is required"
        if (lines.none { it.startsWith("PublicKey", true) && it.contains('=') }) return "WireGuard Peer public key is required"
        if (lines.none { it.startsWith("AllowedIPs", true) && it.contains('=') }) return "WireGuard allowed IPs are required"
        return null
    }

    private fun validateOpenVpn2(config: String): String? {
        val forbidden = setOf("script-security", "up", "down", "route-up", "ipchange", "plugin", "management", "auth-user-pass", "askpass")
        val directives = config.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith(';') }
        directives.forEach { line ->
            val directive = line.takeWhile { !it.isWhitespace() }.lowercase()
            if (directive in forbidden) return "Unsupported OpenVPN directive: $directive"
        }
        if (!config.lineSequence().any { it.trim().startsWith("remote ") }) return "OpenVPN remote is required"
        return null
    }
}

/** In-memory implementation used until the official Android tunnel backend confirms a lifecycle event. */
class WireGuardVpnEngine : VpnEngine {
    override val protocol = VpnProtocol.WIREGUARD
    override var ownsTunnel: Boolean = false
        private set
    override var state: VpnConnectionState = VpnConnectionState.DISCONNECTED
        private set
    override fun connect(profile: VpnProfile): VpnConnectionState {
        require(profile.protocol == protocol) { "WireGuard engine received another protocol" }
        state = VpnConnectionState.CONNECTING
        return state
    }
    fun onTunnelState(connected: Boolean, reconnecting: Boolean = false): VpnConnectionState {
        ownsTunnel = connected || reconnecting
        state = when { connected -> VpnConnectionState.CONNECTED; reconnecting -> VpnConnectionState.RECONNECTING; else -> VpnConnectionState.FAILED }
        return state
    }
    override fun disconnect(): VpnConnectionState { ownsTunnel = false; state = VpnConnectionState.DISCONNECTING; return state }
    fun onDisconnected(): VpnConnectionState { ownsTunnel = false; state = VpnConnectionState.DISCONNECTED; return state }
}

class OpenVpn2Engine : VpnEngine {
    override val protocol = VpnProtocol.OPENVPN2
    override var ownsTunnel: Boolean = false
        private set
    override var state: VpnConnectionState = VpnConnectionState.DISCONNECTED
        private set
    override fun connect(profile: VpnProfile): VpnConnectionState {
        require(profile.protocol == protocol) { "OpenVPN 2 engine received another protocol" }
        state = VpnConnectionState.CONNECTING
        return state
    }
    fun onTunnelState(connected: Boolean, reconnecting: Boolean = false): VpnConnectionState {
        ownsTunnel = connected || reconnecting
        state = when { connected -> VpnConnectionState.CONNECTED; reconnecting -> VpnConnectionState.RECONNECTING; else -> VpnConnectionState.FAILED }
        return state
    }
    override fun disconnect(): VpnConnectionState { ownsTunnel = false; state = VpnConnectionState.DISCONNECTING; return state }
    fun onDisconnected(): VpnConnectionState { ownsTunnel = false; state = VpnConnectionState.DISCONNECTED; return state }
}
