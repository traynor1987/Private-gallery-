package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveDeletionStateTest {
    @Test
    fun `rejected deletion leaves verified vault item complete`() {
        assertEquals(
            VaultItemState.COMPLETE,
            MoveDeletionState.afterSystemResult(VaultItemState.DELETE_PENDING, approved = false),
        )
    }

    @Test
    fun `approved deletion completes the move without changing vault state`() {
        assertEquals(
            VaultItemState.COMPLETE,
            MoveDeletionState.afterSystemResult(VaultItemState.DELETE_PENDING, approved = true),
        )
    }

    @Test
    fun `callback finalizes the durable pending state rather than stale callback state`() {
        assertEquals(
            VaultItemState.COMPLETE,
            MoveDeletionState.finishRecordedState(VaultItemState.DELETE_PENDING, approved = true),
        )
    }
}
