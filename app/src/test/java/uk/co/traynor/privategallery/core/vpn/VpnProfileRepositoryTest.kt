package uk.co.traynor.privategallery.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `cannot remove active profile until another profile replaces it`() {
        val root = createTempDir(prefix = "vpn-profile-removal")
        val key = ByteArray(32) { 8 }
        val repository = VpnProfileRepository(root, key)
        val first = (repository.import("First", validConfig) as VpnProfileImportResult.Accepted).profile
        val second = (repository.import("Second", validConfig) as VpnProfileImportResult.Accepted).profile
        repository.select(first.id)
        assertTrue(runCatching { repository.remove(first.id) }.isFailure)
        repository.replaceActiveWith(second.id)
        repository.remove(first.id)
        assertEquals(second.id, repository.snapshot().activeProfileId)
        root.deleteRecursively()
    }

    @Test fun `lists safe profile summaries without private configuration`() {
        val root = createTempDir(prefix = "vpn-profile-summaries")
        val key = ByteArray(32) { 9 }
        val repository = VpnProfileRepository(root, key)
        val first = (repository.import("Home", validConfig) as VpnProfileImportResult.Accepted).profile
        val second = (repository.import("Travel", validConfig) as VpnProfileImportResult.Accepted).profile
        repository.select(second.id)

        val summaries = repository.summaries()

        assertEquals(listOf("Home", "Travel"), summaries.map { it.displayName })
        assertEquals(listOf(false, true), summaries.map { it.active })
        assertFalse(summaries.toString().contains("PrivateKey"))
        assertFalse(summaries.toString().contains(validConfig))
        root.deleteRecursively()
    }

    private companion object {
        const val validConfig = "[Interface]\nPrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\n[Peer]\nPublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\nAllowedIPs = 0.0.0.0/0"
    }
}
