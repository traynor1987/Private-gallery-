package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreAndRemoveUseCaseTest {
    @Test
    fun `failed restore never removes the vault item`() {
        val events = mutableListOf<String>()

        val result = RestoreAndRemoveUseCase(FakeRestore(events, restore = false)).restoreAndRemove("vault-1")

        assertEquals(RestoreAndRemoveResult.RestoreFailed, result)
        assertEquals(listOf("restore-and-verify"), events)
    }

    @Test
    fun `verified restore is completed before vault removal`() {
        val events = mutableListOf<String>()

        val result = RestoreAndRemoveUseCase(FakeRestore(events, restore = true)).restoreAndRemove("vault-1")

        assertEquals(RestoreAndRemoveResult.RestoredAndRemoved, result)
        assertEquals(listOf("restore-and-verify", "delete-vault"), events)
    }

    private class FakeRestore(
        private val events: MutableList<String>,
        private val restore: Boolean,
    ) : VaultRestorePort {
        override fun restoreAndVerify(vaultId: String): Boolean {
            events += "restore-and-verify"
            return restore
        }

        override fun deleteVaultItem(vaultId: String) {
            events += "delete-vault"
        }
    }
}
