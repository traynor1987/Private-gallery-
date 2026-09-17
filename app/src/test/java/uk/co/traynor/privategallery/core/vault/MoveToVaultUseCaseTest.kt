package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveToVaultUseCaseTest {
  @Test fun `verification failure never requests source deletion`() {
    val events = mutableListOf<String>()
    val result = MoveToVaultUseCase(FakeVault(events, verify = false), FakeDelete(events)).move("content://media/1")
    assertEquals(MoveResult.FailedBeforeDelete, result)
    assertEquals(listOf("encrypt", "verify"), events)
  }

  @Test fun `move requests delete only after verified commit`() {
    val events = mutableListOf<String>()
    val result = MoveToVaultUseCase(FakeVault(events, verify = true), FakeDelete(events)).move("content://media/1")
    assertEquals(MoveResult.DeleteConfirmationRequired, result)
    assertEquals(listOf("encrypt", "verify", "commit", "request-delete"), events)
  }

  private class FakeVault(private val events: MutableList<String>, private val verify: Boolean) : VaultImportPort {
    override fun encryptAndVerify(source: String): Boolean { events += "encrypt"; events += "verify"; return verify }
    override fun commitVerified(source: String) { events += "commit" }
  }
  private class FakeDelete(private val events: MutableList<String>) : SourceDeletionPort {
    override fun requestDelete(source: String) { events += "request-delete" }
  }
}
