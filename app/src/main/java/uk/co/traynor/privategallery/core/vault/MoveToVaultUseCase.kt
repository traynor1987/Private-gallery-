package uk.co.traynor.privategallery.core.vault

/**
 * Coordinates the irreversible part of a Move operation.
 *
 * The deletion port is deliberately reached only after the import port has
 * verified its encrypted payload and committed its durable vault record.
 * Android UI code still owns the subsequent system delete confirmation.
 */
interface VaultImportPort {
    fun encryptAndVerify(source: String): Boolean
    fun commitVerified(source: String)
}

interface SourceDeletionPort {
    fun requestDelete(source: String)
}

enum class MoveResult {
    FailedBeforeDelete,
    DeleteConfirmationRequired,
}

class MoveToVaultUseCase(
    private val vault: VaultImportPort,
    private val sourceDeletion: SourceDeletionPort,
) {
    fun move(source: String): MoveResult {
        if (!vault.encryptAndVerify(source)) {
            return MoveResult.FailedBeforeDelete
        }

        vault.commitVerified(source)
        sourceDeletion.requestDelete(source)
        return MoveResult.DeleteConfirmationRequired
    }
}
