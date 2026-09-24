package uk.co.traynor.privategallery.core.vault
import org.junit.Assert.*
import org.junit.Test
class VaultEgressPolicyTest {
    private fun item(restricted: Boolean) = VaultItem("id", "image/png", "anything.png", 1, 1, ByteArray(32), ByteArray(12), VaultItemState.COMPLETE, origin = MediaOrigin.REMOTE_AI_EDIT, vaultOnly = restricted)
    @Test fun everyPublicRouteRejectsRestrictedItem() {
        VaultEgress.entries.forEach { action ->
            assertThrows(SecurityException::class.java) { VaultEgressPolicy.requireAllowed(item(true), action) }
        }
    }
    @Test fun ordinaryAndUnrestrictedMediaStillAllowed() {
        VaultEgress.entries.forEach { VaultEgressPolicy.requireAllowed(item(false), it) }
    }
    @Test fun localDescendantCannotLaunderRestriction() {
        assertTrue(VaultEgressPolicy.derivativeRestricted(item(true), remoteAi = false, keepAiInVault = false))
        assertTrue(VaultEgressPolicy.derivativeRestricted(item(false), remoteAi = true, keepAiInVault = true))
        assertFalse(VaultEgressPolicy.derivativeRestricted(item(false), remoteAi = false, keepAiInVault = true))
    }
    @Test fun encryptedMetadataRetainsRestrictionAcrossReopen() {
        val root = java.nio.file.Files.createTempDirectory("provenance").toFile()
        try {
            val key = ByteArray(32)
            EncryptedIndexStore(root).saveSnapshot(VaultIndexSnapshot(listOf(item(true))), key)
            val loaded = EncryptedIndexStore(root).loadSnapshot(key).items.single()
            assertTrue(loaded.vaultOnly); assertEquals(MediaOrigin.REMOTE_AI_EDIT, loaded.origin)
        } finally { root.deleteRecursively() }
    }
}
