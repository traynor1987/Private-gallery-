package uk.co.traynor.privategallery.core.security

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PrimaryVaultSetupGuardTest {
    @Test fun missingOrEmptyRootAllowsFreshSetupOnlyWithoutEnvelope() {
        val root = Files.createTempDirectory("primary-setup").toFile()
        try {
            assertTrue(PrimaryVaultSetupGuard.canCreate(root, false))
            assertFalse(PrimaryVaultSetupGuard.canCreate(root, true))
            root.resolve("vault").mkdir()
            assertTrue(PrimaryVaultSetupGuard.canCreate(root, false))
        } finally { root.deleteRecursively() }
    }
    @Test fun ciphertextOrInterruptedRestorePreventsFreshKeyCreation() {
        for (path in listOf("vault/vault-index.enc", "vault/payloads/sole.vault", "vault/payloads/sole.deleting", "vault-restore-staging/manifest")) {
            val root = Files.createTempDirectory("primary-preserve").toFile()
            try {
                root.resolve(path).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
                assertFalse(path, PrimaryVaultSetupGuard.canCreate(root, false))
            } finally { root.deleteRecursively() }
        }
    }
}
