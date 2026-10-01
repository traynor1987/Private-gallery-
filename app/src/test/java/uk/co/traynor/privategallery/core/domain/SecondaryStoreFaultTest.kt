package uk.co.traynor.privategallery.core.domain

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class SecondaryStoreFaultTest {
  private class FaultIo(private val failAt: Int = Int.MAX_VALUE, private val after: Boolean = false) : SecondaryStorageIo {
    var calls = 0
    private fun boundary(action: () -> Unit) {
      calls++
      if (calls == failAt && !after) throw IOException("injected")
      action()
      if (calls == failAt && after) throw IOException("injected")
    }
    override fun mkdir(path: Path) = boundary { DurableSecondaryIo.mkdir(path) }
    override fun writeNew(path: Path, bytes: ByteArray) = boundary { DurableSecondaryIo.writeNew(path, bytes) }
    override fun syncDirectory(path: Path) = boundary { DurableSecondaryIo.syncDirectory(path) }
    override fun atomicReplace(source: Path, target: Path) = boundary { DurableSecondaryIo.atomicReplace(source, target) }
  }
  private val pin get() = "3210987654321098".toCharArray()
  private fun rejected(action: () -> Unit) { try { action(); fail("accepted") } catch (_: SecondaryStoreException) {} }
  private fun token(dir: File): String = dir.resolve("domain-store/selected").readBytes().copyOfRange(10, 26).hex()
  @Test fun everyConfirmationWriteSyncAndSelectionBoundaryRetainsPendingOrReady() {
    val base = Files.createTempDirectory("fault-base").toFile()
    try {
      base.resolve("vault").mkdir(); base.resolve("vault/canary").writeText("unchanged")
      val store = SecondaryStore(base); val pending = store.create(pin, {}, { it() }); val secret = pending.recoverySecret
      val master = store.authenticatePin(pin).takeMaster(); val identity = pending.identity; pending.close()
      val probeDir = Files.createTempDirectory("fault-probe").toFile()
      val count = try {
        base.copyRecursively(probeDir, overwrite = true)
        val io = FaultIo(); val probe = SecondaryStore(probeDir, io, Unit)
        probe.confirm(PendingSetup(probe, identity, token(probeDir), false, master.copyOf(), secret.copyOf()), secret, {}, { it() }).close(); io.calls
      } finally { probeDir.deleteRecursively() }
      assertTrue(count > 30)
      for (after in listOf(false, true)) for (boundary in 1..count) {
        val dir = Files.createTempDirectory("fault-case").toFile()
        try {
          base.copyRecursively(dir, overwrite = true)
          val faultStore = SecondaryStore(dir, FaultIo(boundary, after), Unit)
          val attempt = PendingSetup(faultStore, identity, token(dir), false, master.copyOf(), secret.copyOf())
          rejected { faultStore.confirm(attempt, secret, {}, { it() }).close() }; attempt.close()
          val restart = SecondaryStore(dir)
          assertTrue("boundary $boundary after=$after", restart.preflight() in setOf(SecondaryPreflight.PENDING, SecondaryPreflight.READY))
          restart.authenticatePin(pin).use { assertArrayEquals(master, it.takeMaster()) }
          rejected { restart.create(pin, {}, { it() }).close() }
          assertEquals("unchanged", dir.resolve("vault/canary").readText())
        } finally { dir.deleteRecursively() }
      }
      secret.fill(0); master.fill(0)
    } finally { base.deleteRecursively() }
  }
  @Test fun everyFreshDurableBoundaryPreservesCanaryAndCannotResetMaterial() {
    val probeDir = Files.createTempDirectory("fresh-probe").toFile()
    val count = try { val io = FaultIo(); SecondaryStore(probeDir, io, Unit).create(pin, {}, { it() }).close(); io.calls } finally { probeDir.deleteRecursively() }
    for (after in listOf(false, true)) for (boundary in 1..count) {
      val dir = Files.createTempDirectory("fresh-boundary").toFile()
      try {
        dir.resolve("vault").mkdir(); dir.resolve("vault/canary").writeText("unchanged")
        rejected { SecondaryStore(dir, FaultIo(boundary, after), Unit).create(pin, {}, { it() }).close() }
        val restart = SecondaryStore(dir); val state = restart.preflight()
        assertTrue(state in setOf(SecondaryPreflight.FRESH, SecondaryPreflight.UNAVAILABLE, SecondaryPreflight.PENDING))
        // FRESH is permitted only when no write happened, or a directly empty root was created.
        if (state == SecondaryPreflight.FRESH) {
          val root = dir.resolve("domain-store"); assertTrue(!root.exists() || root.listFiles()!!.isEmpty())
        } else rejected { restart.create(pin, {}, { it() }).close() }
        assertEquals("unchanged", dir.resolve("vault/canary").readText())
      } finally { dir.deleteRecursively() }
    }
  }
  @Test fun freshInterruptedBeforeSelectionNeverBecomesFresh() {
    // Failure after the first namespace mkdir: remaining material blocks destructive retries.
    val dir = Files.createTempDirectory("fresh-fault").toFile()
    try {
      rejected { SecondaryStore(dir, FaultIo(3, true), Unit).create(pin, {}, { it() }).close() }
      val restart = SecondaryStore(dir); assertEquals(SecondaryPreflight.UNAVAILABLE, restart.preflight())
      rejected { restart.create(pin, {}, { it() }).close() }
    } finally { dir.deleteRecursively() }
  }
  @Test fun durableQueryCapAndMissingLedgerCloseServiceAcrossRestart() {
    val dir = Files.createTempDirectory("usage-test").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); store.confirm(pending, pending.recoverySecret, {}, { it() }).close()
      val activePin = dir.resolve("domain-store/slots/${token(dir)}").listFiles()!!.single().readBytes()
      val key = digest(activePin.copyOfRange(12, 70) + activePin.copyOfRange(72, 74) + activePin.copyOfRange(92, 124)).hex()
      val ledger = dir.resolve("domain-store/transactions/usage/$key")
      val bytes = ledger.readBytes(); ByteBuffer.wrap(bytes).putLong(18, SecondaryStore.MAX_QUERIES); ledger.writeBytes(bytes)
      rejected { SecondaryStore(dir).authenticatePin(pin).close() }
      ledger.delete(); rejected { SecondaryStore(dir).authenticatePin(pin).close() }
    } finally { dir.deleteRecursively() }
  }
  @Test fun cancellationAtFinalSetupPromotionIsSerializedWithOriginalAttempt() {
    val dir = Files.createTempDirectory("setup-admission").toFile()
    try {
      val authority = SecondarySessionAuthority { System.nanoTime() / 1_000_000 }
      val attempt = authority.beginAuthentication()
      val io = object : SecondaryStorageIo by DurableSecondaryIo {
        override fun writeNew(path: Path, bytes: ByteArray) {
          DurableSecondaryIo.writeNew(path, bytes)
          if (path.parent.fileName.toString() == "temporary") authority.cancelAuthentication(attempt)
        }
      }
      val store = SecondaryStore(dir, io, Unit)
      rejected { store.create(pin, { authority.checkAuthentication(attempt) }, { action -> authority.commitAuthentication(attempt, action) }).close() }
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
      assertFalse(dir.resolve("domain-store/selected").exists())
    } finally { dir.deleteRecursively() }
  }
  @Test fun observedRootReplacementLinkCannotWriteIntoPrimary() {
    val dir = Files.createTempDirectory("root-change").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); val auth = store.confirm(pending, pending.recoverySecret, {}, { it() })
      val authority = SecondarySessionAuthority { System.nanoTime() / 1_000_000 }
      authority.completeAuthentication(authority.beginAuthentication(), auth.takeMaster())
      val primary = dir.resolve("vault"); primary.mkdir(); primary.resolve("temporary").mkdir(); primary.resolve("canary").writeText("primary")
      val io = object : SecondaryStorageIo by DurableSecondaryIo {
        override fun writeNew(path: Path, bytes: ByteArray) {
          if (path.parent.fileName.toString() == "temporary") {
            Files.move(dir.resolve("domain-store").toPath(), dir.resolve("old-domain-store").toPath())
            Files.createSymbolicLink(dir.resolve("domain-store").toPath(), primary.toPath())
          }
          DurableSecondaryIo.writeNew(path, bytes)
        }
      }
      val op = authority.operationOrNull(setOf(SecondaryScope.WRITE))!!
      rejected { SecondaryStore(dir, io, Unit).updateSettings(op, StrongAuthInterval.SEVEN_DAYS, SecondaryAutoLock.ONE_MINUTE) }
      assertEquals("primary", primary.resolve("canary").readText()); assertTrue(primary.resolve("temporary").listFiles()!!.isEmpty())
      Files.delete(dir.resolve("domain-store").toPath()); Files.move(dir.resolve("old-domain-store").toPath(), dir.resolve("domain-store").toPath())
      authority.revoke()
    } finally { dir.deleteRecursively() }
  }
  @Test fun originalEpochRevocationDuringPreparationCannotSelectMutation() {
    val dir = Files.createTempDirectory("epoch-fault").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); val auth = store.confirm(pending, pending.recoverySecret, {}, { it() })
      val authority = SecondarySessionAuthority { System.nanoTime() / 1_000_000 }; authority.completeAuthentication(authority.beginAuthentication(), auth.takeMaster())
      val old = token(dir)
      val io = object : SecondaryStorageIo by DurableSecondaryIo {
        override fun writeNew(path: Path, bytes: ByteArray) {
          DurableSecondaryIo.writeNew(path, bytes)
          if (path.parent.fileName.toString() == "temporary") authority.revoke()
        }
      }
      val changing = SecondaryStore(dir, io, Unit)
      val operation = authority.operationOrNull(setOf(SecondaryScope.WRITE))!!
      rejected { changing.updateSettings(operation, StrongAuthInterval.SEVEN_DAYS, SecondaryAutoLock.FIVE_MINUTES) }
      assertEquals(old, token(dir)); store.authenticatePin(pin).use { assertEquals(StrongAuthInterval.DAY, it.strongAuthInterval) }
    } finally { dir.deleteRecursively() }
  }
}
