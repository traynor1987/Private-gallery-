package uk.co.traynor.privategallery.core.security

import java.io.File

/** Missing/partial credentials never authorize replacing an existing encrypted Vault. */
object PrimaryVaultSetupGuard {
    /** Same lock as Primary repository writes/restore; acquire before preference locks. */
    internal val storageLock = Any()

    fun canCreate(filesDir: File, hasEnvelopeMaterial: Boolean, hasRecoveryMaterial: Boolean = false,
                  hasBiometricMaterial: Boolean = false): Boolean =
        !hasEnvelopeMaterial && !hasRecoveryMaterial && !hasBiometricMaterial &&
            PrimaryStorageInventory.canCreate(filesDir.toPath())
}
