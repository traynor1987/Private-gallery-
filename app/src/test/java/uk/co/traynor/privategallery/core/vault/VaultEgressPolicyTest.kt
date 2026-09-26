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
    @Test fun generatedImageRestrictionPersistsInEncryptedIndex() {
        val root = java.nio.file.Files.createTempDirectory("generated-provenance").toFile()
        try {
            val generated = item(true).copy(origin = MediaOrigin.REMOTE_AI_GENERATED,
                sourceUri = "ai:replicate|model:bytedance/seedream-4.5", displayName = "generated-image.png")
            val key = ByteArray(32)
            EncryptedIndexStore(root).saveSnapshot(VaultIndexSnapshot(listOf(generated)), key)
            val loaded = EncryptedIndexStore(root).loadSnapshot(key).items.single()
            assertEquals(MediaOrigin.REMOTE_AI_GENERATED, loaded.origin)
            assertTrue(loaded.vaultOnly)
            assertFalse(loaded.displayName.contains("prompt"))
            VaultEgress.entries.forEach { assertThrows(SecurityException::class.java) { VaultEgressPolicy.requireAllowed(loaded, it) } }
        } finally { root.deleteRecursively() }
    }
}
