package uk.co.traynor.privategallery.core.vault

enum class MediaOrigin { IMPORTED, LOCAL_EDIT, REMOTE_AI_EDIT, LOCAL_AI_EDIT, REMOTE_AI_GENERATED }
enum class VaultEgress { RESTORE, EXPORT, SHARE }
object VaultEgressPolicy {
    fun requireAllowed(item: VaultItem, action: VaultEgress) {
        if (item.vaultOnly) throw SecurityException("This derivative is restricted to Vault; ${action.name.lowercase()} is unavailable.")
    }
    fun derivativeRestricted(parent: VaultItem, remoteAi: Boolean, keepAiInVault: Boolean): Boolean =
        parent.vaultOnly || (remoteAi && keepAiInVault)
}
