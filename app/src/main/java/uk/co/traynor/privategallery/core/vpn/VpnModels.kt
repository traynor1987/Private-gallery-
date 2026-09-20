package uk.co.traynor.privategallery.core.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.Executors

/** Kept protocol-neutral so a future compatibility engine does not change Browser policy. */
enum class VpnProtocol { WIREGUARD }

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
        if (rawConfiguration.lineSequence().any { it.trim().startsWith("client") || it.trim().startsWith("remote ") }) {
            return VpnProfileImportResult.Rejected("OpenVPN 2 profiles are not supported in this release")
        }
        if (!rawConfiguration.lineSequence().any { it.trim().equals("[Interface]", ignoreCase = true) }) {
            return VpnProfileImportResult.Rejected("Unsupported VPN profile format")
        }
        return runCatching {
            Config.parse(ByteArrayInputStream(rawConfiguration.toByteArray(Charsets.UTF_8)))
            VpnProfileImportResult.Accepted(VpnProfile(id, name, VpnProtocol.WIREGUARD, rawConfiguration))
        }.getOrElse { VpnProfileImportResult.Rejected("Invalid WireGuard configuration") }
    }
}

/** Adapter around the official WireGuard Android tunnel library; no provider APIs are involved. */
interface WireGuardBackend {
    fun connect(profile: VpnProfile, onState: (VpnConnectionState) -> Unit)
    fun disconnect(onState: (VpnConnectionState) -> Unit)
}

class WireGuardVpnEngine(private val backend: WireGuardBackend) : VpnEngine {
    override val protocol = VpnProtocol.WIREGUARD
    override var ownsTunnel: Boolean = false
        private set
    override var state: VpnConnectionState = VpnConnectionState.DISCONNECTED
        private set
    var onStateChanged: ((VpnConnectionState) -> Unit)? = null

    override fun connect(profile: VpnProfile): VpnConnectionState {
        require(profile.protocol == protocol) { "WireGuard engine received another protocol" }
        state = VpnConnectionState.CONNECTING
        backend.connect(profile) { observed ->
            ownsTunnel = observed == VpnConnectionState.CONNECTED || observed == VpnConnectionState.RECONNECTING
            state = observed
            onStateChanged?.invoke(observed)
        }
        return state
    }

    override fun disconnect(): VpnConnectionState {
        state = VpnConnectionState.DISCONNECTING
        backend.disconnect { observed ->
            ownsTunnel = false
            state = observed
            onStateChanged?.invoke(observed)
        }
        return state
    }
}

/**
 * Real Android VpnService-backed backend. A successful `UP` state is emitted only after the
 * official GoBackend has established the tunnel; all backend errors fail closed.
 */
class OfficialWireGuardBackend(context: Context) : WireGuardBackend {
    private val backend = GoBackend(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var activeTunnel: ManagedTunnel? = null
    @Volatile private var disconnectRequested = false

    override fun connect(profile: VpnProfile, onState: (VpnConnectionState) -> Unit) {
        disconnectRequested = false
        executor.execute {
            try {
                val config = Config.parse(ByteArrayInputStream(profile.privateConfiguration.toByteArray(Charsets.UTF_8)))
                val tunnel = ManagedTunnel(tunnelName(profile.id), onState) { disconnectRequested }
                activeTunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
                activeTunnel = tunnel
                val result = backend.setState(tunnel, Tunnel.State.UP, config)
                if (result == Tunnel.State.UP) onState(VpnConnectionState.CONNECTED) else onState(VpnConnectionState.FAILED)
            } catch (_: Throwable) {
                activeTunnel = null
                onState(VpnConnectionState.FAILED)
            }
        }
    }

    override fun disconnect(onState: (VpnConnectionState) -> Unit) {
        disconnectRequested = true
        executor.execute {
            try { activeTunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) } }
            catch (_: Throwable) { /* the tunnel is no longer usable; Browser remains closed */ }
            finally { activeTunnel = null; onState(VpnConnectionState.DISCONNECTED) }
        }
    }

    private fun tunnelName(profileId: String): String = "pg-" + profileId.filter { it.isLetterOrDigit() }.take(12).ifEmpty { "vpn" }

    private class ManagedTunnel(
        private val name: String,
        private val report: (VpnConnectionState) -> Unit,
        private val isDisconnectRequested: () -> Boolean,
    ) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) {
            report(
                when (newState) {
                    Tunnel.State.UP -> VpnConnectionState.CONNECTED
                    Tunnel.State.DOWN -> if (isDisconnectRequested()) VpnConnectionState.DISCONNECTED else VpnConnectionState.FAILED
                    Tunnel.State.TOGGLE -> VpnConnectionState.FAILED
                },
            )
        }
    }
}
