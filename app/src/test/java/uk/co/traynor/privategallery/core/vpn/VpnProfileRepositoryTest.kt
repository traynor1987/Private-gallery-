package uk.co.traynor.privategallery.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnProfileRepositoryTest {
    @Test fun `imports selects and reloads only validated WireGuard profiles`() {
        val root = createTempDir(prefix = "vpn-profile-repository")
        val key = ByteArray(32) { 4 }
        val repository = VpnProfileRepository(root, key)
        val imported = repository.import("Home", validConfig) as VpnProfileImportResult.Accepted
        repository.select(imported.profile.id)
        assertEquals(imported.profile.id, VpnProfileRepository(root, key).snapshot().activeProfileId)
        assertTrue(VpnProfileRepository(root, key).snapshot().profiles.single().privateConfiguration.contains("PrivateKey"))
        root.deleteRecursively()
    }

    private companion object {
        const val validConfig = "[Interface]\nPrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\n[Peer]\nPublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\nAllowedIPs = 0.0.0.0/0"
    }
}
