package uk.co.traynor.privategallery.core.vault

/** Maps the Android delete-dialog result without ever removing vault data. */
object MoveDeletionState {
    fun afterSystemResult(previous: VaultItemState, approved: Boolean): VaultItemState {
        require(previous == VaultItemState.DELETE_PENDING)
        return VaultItemState.COMPLETE
    }

    /**
     * The Android deletion activity returns after the caller's in-memory item
     * has become stale. Finish the transition from the durable index state.
     */
    fun finishRecordedState(recordedState: VaultItemState, approved: Boolean): VaultItemState =
        afterSystemResult(recordedState, approved)
}
