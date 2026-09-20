package uk.co.traynor.privategallery.core.vpn

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EncryptedVpnProfileStoreTest {
    @Test fun `profile configuration is encrypted at rest and active profile survives reload`() {
        val root = createTempDir(prefix = "vpn-profile-store")
        val key = ByteArray(32) { 7 }
        val profile = VpnProfile("profile", "WireGuard", VpnProtocol.WIREGUARD, "[Interface]\nPrivateKey = secret")
        EncryptedVpnProfileStore(root).save(VpnProfileSnapshot(listOf(profile), profile.id), key)
        val encrypted = File(root, "vpn-profiles.enc").readText(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("PrivateKey = secret"))
        val reloaded = EncryptedVpnProfileStore(root).load(key)
        assertEquals(profile.id, reloaded.activeProfileId)
        assertEquals(profile, reloaded.profiles.single())
        root.deleteRecursively()
    }
}
