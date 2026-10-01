package uk.co.traynor.privategallery.core.security

import java.io.File

/** Missing/partial credentials never authorize replacing an existing encrypted Vault. */
object PrimaryVaultSetupGuard {
    fun canCreate(filesDir: File, hasEnvelopeMaterial: Boolean, hasRecoveryMaterial: Boolean = false,
                  hasBiometricMaterial: Boolean = false): Boolean =
        !hasEnvelopeMaterial && !hasRecoveryMaterial && !hasBiometricMaterial && !File(filesDir, "vault-restore-staging").exists() && listOf("vault").none { name ->
            val root = File(filesDir, name)
            root.exists() && (!root.isDirectory || root.walkTopDown().any { it.isFile } || root.listFiles() == null)
        }
}
