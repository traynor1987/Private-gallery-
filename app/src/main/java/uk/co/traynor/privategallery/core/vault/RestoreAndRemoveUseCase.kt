package uk.co.traynor.privategallery.core.vault

/**
 * Handles the destructive variant of restore.  [VaultRestorePort] must only
 * return true once the newly-created MediaStore item can be read back and
 * matches the decrypted vault payload.
 */
interface VaultRestorePort {
    fun restoreAndVerify(vaultId: String): Boolean
    fun deleteVaultItem(vaultId: String)
}

enum class RestoreAndRemoveResult {
    RestoreFailed,
    RestoredAndRemoved,
}

class RestoreAndRemoveUseCase(
    private val vault: VaultRestorePort,
) {
    fun restoreAndRemove(vaultId: String): RestoreAndRemoveResult {
        if (!vault.restoreAndVerify(vaultId)) {
            return RestoreAndRemoveResult.RestoreFailed
        }

        vault.deleteVaultItem(vaultId)
        return RestoreAndRemoveResult.RestoredAndRemoved
    }
}
