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
    val hasTunnel: Boolean get() = false
    fun diagnosticSummary(): String = ""
}

class WireGuardVpnEngine(private val backend: WireGuardBackend) : VpnEngine {
    override val protocol = VpnProtocol.WIREGUARD
    @Volatile override var ownsTunnel = false
        private set
    @Volatile override var state = VpnConnectionState.DISCONNECTED
        private set
    var onStateChanged: ((VpnConnectionState) -> Unit)? = null
    private var requestId = 0L

    @Synchronized override fun connect(profile: VpnProfile): VpnConnectionState {
        require(profile.protocol == protocol)
        val request = ++requestId
        state = VpnConnectionState.CONNECTING
        ownsTunnel = true
        onStateChanged?.invoke(state)
        backend.connect(profile) { observed -> synchronized(this) {
            if (request == requestId) {
                ownsTunnel = backend.hasTunnel || observed in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.CONNECTED, VpnConnectionState.RECONNECTING)
                state = observed
                onStateChanged?.invoke(observed)
            }
        } }
        return state
    }

    fun diagnosticSummary(): String = backend.diagnosticSummary()

    @Synchronized override fun disconnect(): VpnConnectionState {
        val request = ++requestId
        state = VpnConnectionState.DISCONNECTING
        onStateChanged?.invoke(state)
        backend.disconnect { observed -> synchronized(this) {
            if (request == requestId) {
                ownsTunnel = backend.hasTunnel
                state = observed
                onStateChanged?.invoke(observed)
            }
        } }
        return state
    }
}

/** Official backend, with generation-fenced peer verification. UP is not connectivity. */
class OfficialWireGuardBackend(context: Context) : WireGuardBackend {
    private val context = context.applicationContext
    private val backend = GoBackend(this.context)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var activeTunnel: ManagedTunnel? = null
    @Volatile private var summary = "VPN not connected."
    override val hasTunnel: Boolean get() = activeTunnel != null
    override fun diagnosticSummary(): String = summary

    override fun connect(profile: VpnProfile, onState: (VpnConnectionState) -> Unit) {
        val attempt = generation.incrementAndGet()
        summary = "Starting WireGuard; peer handshake not yet verified."
        executor.execute {
            if (generation.get() != attempt) return@execute
            try {
                activeTunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
                activeTunnel = null
                val parsed = Config.parse(ByteArrayInputStream(profile.privateConfiguration.toByteArray(Charsets.UTF_8)))
                val config = WireGuardProfilePolicy.prepare(parsed, context.packageName)
                val tunnel = ManagedTunnel("pg-" + profile.id.filter { it.isLetterOrDigit() }.take(12).ifEmpty { "vpn" }) { down ->
                    if (down && generation.get() == attempt) {
                        summary = "WireGuard service stopped or VPN permission was revoked. Browsing is blocked."
                        onState(VpnConnectionState.FAILED)
                    }
                }
                activeTunnel = tunnel
                backend.setState(tunnel, Tunnel.State.UP, config)
                if (generation.get() != attempt) return@execute
                val started = android.os.SystemClock.elapsedRealtime()
                var waitingSince = started
                var lastState = VpnConnectionState.CONNECTING
                fun sample() {
                    if (generation.get() != attempt || activeTunnel !== tunnel) return
                    try {
                        val stats = backend.getStatistics(tunnel)
                        val peer = stats.peer(config.peers.single().publicKey)
                        val handshake = peer?.latestHandshakeEpochMillis() ?: 0L
                        val epoch = System.currentTimeMillis()
                        val ready = backend.getState(tunnel) == Tunnel.State.UP &&
                            android.net.VpnService.prepare(context) == null &&
                            WireGuardReadiness.peerVerified(epoch, handshake)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (ready) waitingSince = now
                        val state = if (ready) VpnConnectionState.CONNECTED
                            else if (now - waitingSince >= 45_000) VpnConnectionState.FAILED
                            else if (lastState == VpnConnectionState.CONNECTING) VpnConnectionState.CONNECTING
                            else VpnConnectionState.RECONNECTING
                        val age = if (handshake <= 0 || handshake > epoch) "unavailable" else "${(epoch-handshake)/1000}s"
                        summary = "Peer handshake age: $age; received ${stats.totalRx()} B; sent ${stats.totalTx()} B. " +
                            if (ready) "Peer verified; website connectivity is checked by the browser."
                            else "Browsing blocked. Check the profile/server and Android VPN ownership; no direct fallback."
                        if (generation.get() != attempt) return
                        if (state != lastState) { lastState = state; onState(state) }
                    } catch (_: Exception) {
                        summary = "WireGuard verification unavailable. Browsing is blocked."
                        if (generation.get() == attempt && lastState != VpnConnectionState.FAILED) {
                            lastState = VpnConnectionState.FAILED; onState(lastState)
                        }
                    }
                    if (generation.get() == attempt) executor.schedule({ sample() }, 2, java.util.concurrent.TimeUnit.SECONDS)
                }
                sample()
            } catch (failure: Exception) {
                if (generation.get() == attempt) {
                    // Only locally authored policy messages are shown, never backend exceptions/configuration.
                    summary = if (failure is WireGuardProfileException) failure.message ?: "Unsupported VPN profile."
                        else "WireGuard could not start. Check VPN permission, internet access and your imported profile."
                    onState(VpnConnectionState.FAILED)
                }
            }
        }
    }

    override fun disconnect(onState: (VpnConnectionState) -> Unit) {
        val attempt = generation.incrementAndGet() // invalidates pending establishment and stats immediately
        executor.execute {
            if (generation.get() != attempt) return@execute
            val tunnel = activeTunnel
            try { tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) } }
            catch (_: Exception) { /* preserve ownership unless backend confirms release */ }
            val released = tunnel == null || runCatching { backend.getState(tunnel) == Tunnel.State.DOWN }.getOrDefault(false)
            if (released && activeTunnel === tunnel) activeTunnel = null
            if (generation.get() == attempt) {
                summary = if (released) "VPN disconnected." else "VPN teardown not confirmed. Browsing remains blocked; retry disconnect."
                onState(if (released) VpnConnectionState.DISCONNECTED else VpnConnectionState.FAILED)
            }
        }
    }

    private class ManagedTunnel(private val name: String, private val reportDown: (Boolean) -> Unit) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) { if (newState != Tunnel.State.UP) reportDown(true) }
    }
}
