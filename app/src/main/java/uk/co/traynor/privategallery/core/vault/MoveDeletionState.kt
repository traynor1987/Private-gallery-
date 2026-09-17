package uk.co.traynor.privategallery.core.vault

/** Maps the Android delete-dialog result without ever removing vault data. */
object MoveDeletionState {
    fun afterSystemResult(previous: VaultItemState, approved: Boolean): VaultItemState {
        require(previous == VaultItemState.DELETE_PENDING)
        return VaultItemState.COMPLETE
    }
}
