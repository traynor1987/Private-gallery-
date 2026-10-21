package uk.co.traynor.privategallery.core.hidden

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vpn.WireGuardProfilePolicy
import uk.co.traynor.privategallery.core.vpn.WireGuardReadiness

class WireGuardProfilePolicyTest {
    private val app = "uk.co.traynor.privategallery.hiddenprototype"
    private fun config(extra: String = "", routes: String = "0.0.0.0/0", dns: String = "DNS = 10.0.0.1") = Config.parse(ByteArrayInputStream("""
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = 10.0.0.2/32
        $dns
        $extra
        [Peer]
        PublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Endpoint = 192.0.2.1:51820
        AllowedIPs = $routes
    """.trimIndent().toByteArray()))
    @Test fun runtimeKeepalivePreservesStoredProfileAndKeys() {
        val original = config(); val before = original.toWgQuickString()
        val prepared = WireGuardProfilePolicy.prepare(original, app)
        assertEquals(before, original.toWgQuickString())
        assertEquals(original.`interface`, prepared.`interface`)
        assertEquals(original.peers.single().publicKey, prepared.peers.single().publicKey)
        assertEquals(25, prepared.peers.single().persistentKeepalive.get())
    }
    @Test fun splitRoutesMissingDnsAndWrongApplicationAreRejected() {
        listOf(config(routes="10.0.0.0/8"), config(dns=""),
            config(extra="ExcludedApplications = $app"),
            config(extra="IncludedApplications = uk.co.traynor.privategallery"),
            config(routes="0.0.0.0/0, 2001:db8::/32")).forEach { value ->
            assertTrue(runCatching { WireGuardProfilePolicy.prepare(value, app) }.isFailure)
        }
    }
    @Test fun interfaceUpWithoutFreshHandshakeNeverPassesReadiness() {
        assertFalse(WireGuardReadiness.peerVerified(1_000_000,0))
        assertFalse(WireGuardReadiness.peerVerified(1_000_000,800_000))
        assertFalse(WireGuardReadiness.peerVerified(1_000_000,1_000_001))
        assertTrue(WireGuardReadiness.peerVerified(1_000_000,999_000))
    }
}
