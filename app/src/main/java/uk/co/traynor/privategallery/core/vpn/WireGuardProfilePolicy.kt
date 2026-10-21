package uk.co.traynor.privategallery.core.vpn

import com.wireguard.config.Config
import com.wireguard.config.Peer
import java.net.Inet4Address
import java.net.Inet6Address

/** Browser protection accepts a single full-tunnel peer, never silent split routing. */
internal class WireGuardProfileException(message: String) : IllegalArgumentException(message)

internal object WireGuardProfilePolicy {
    private inline fun require(condition: Boolean, message: () -> String) {
        if (!condition) throw WireGuardProfileException(message())
    }
    fun prepare(config: Config, packageName: String): Config {
        require(config.peers.size == 1) { "Use a single-peer full-tunnel WireGuard profile." }
        val network = config.`interface`
        require(packageName !in network.excludedApplications &&
            (network.includedApplications.isEmpty() || packageName in network.includedApplications)) {
            "This WireGuard profile excludes this app. Use a profile that includes this installation."
        }
        require(network.dnsServers.isNotEmpty()) { "The WireGuard profile needs a VPN DNS server." }
        val peer = config.peers.single()
        require(peer.endpoint.isPresent) { "The WireGuard profile needs a server endpoint." }
        require(network.addresses.any { it.address is Inet4Address } &&
            peer.allowedIps.any { it.address is Inet4Address && it.mask == 0 }) {
            "The WireGuard profile needs an IPv4 address and full IPv4 route (0.0.0.0/0)."
        }
        val usesV6 = network.addresses.any { it.address is Inet6Address } ||
            network.dnsServers.any { it is Inet6Address } || peer.allowedIps.any { it.address is Inet6Address }
        require(!usesV6 || (network.addresses.any { it.address is Inet6Address } &&
            peer.allowedIps.any { it.address is Inet6Address && it.mask == 0 })) {
            "IPv6 must be fully routed through the VPN or omitted from this profile."
        }
        // With one peer + a default route, pinned GoBackend blocks an omitted IP family.
        // Keepalive triggers authenticated peer discovery without a direct-network probe.
        // Only the runtime copy changes; the encrypted imported profile remains untouched.
        val copy = Peer.Builder().setPublicKey(peer.publicKey).addAllowedIps(peer.allowedIps)
            .setEndpoint(peer.endpoint.get()).setPersistentKeepalive(25)
        peer.preSharedKey.ifPresent { copy.setPreSharedKey(it) }
        return Config.Builder().setInterface(network).addPeer(copy.build()).build()
    }
}
