package uk.co.traynor.privategallery.core.domain

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Actual durable files and process-style restart; no owner data or device keys. */
class SecondaryRetirementRecoveryTest {
  private class Base : AutoCloseable {
    val files = Files.createTempDirectory("retirement-restart").toFile()
    val store = testStore(files)
    val pending = store.create("111111111111".toCharArray(), {}, { it() })
    val identity = pending.identity
    val secret = pending.recoverySecret
    val master = store.confirm(pending,secret,{}, { it() }).takeMaster()
    init { files.resolve("vault").mkdir(); files.resolve("vault/canary").writeText("primary-unchanged") }
    override fun close() { master.fill(0); secret.fill(0); pending.close(); files.deleteRecursively() }
  }
  private class CrashIo(val stopAt: Int = Int.MAX_VALUE, val after: Boolean = false) : SecondaryStorageIo by DurableSecondaryIo {
    var cleanup = false
    var calls = 0
    var crashed = false
    private fun boundary(action: () -> Unit) {
      if (crashed) throw IOException("process interrupted")
      calls++
      if (calls == stopAt && !after) { crashed = true; throw IOException("process interrupted") }
      action()
      if (calls == stopAt && after) { crashed = true; throw IOException("process interrupted") }
    }
    override fun remove(path: Path, directory: Boolean) { cleanup = true; boundary { DurableSecondaryIo.remove(path,directory) } }
    override fun syncDirectory(path: Path) {
      if (cleanup) boundary { DurableSecondaryIo.syncDirectory(path) }
      else { if (crashed) throw IOException("process interrupted"); DurableSecondaryIo.syncDirectory(path) }
    }
  }
  private fun change(files: java.io.File, master: ByteArray, io: SecondaryStorageIo) {
    val authority = SecondarySessionAuthority { 1L }
    try {
      authority.completeAuthentication(authority.beginAuthentication(),master.copyOf())
      checkNotNull(authority.operationOrNull(setOf(SecondaryScope.WRITE))).use {
        testStore(files,io).updateSettings(it,StrongAuthInterval.THREE_DAYS,SecondaryAutoLock.ONE_MINUTE)
      }
    } finally { authority.revoke() }
  }
  @Test fun everyRetirementUnlinkAndFsyncBoundaryResumesWithoutLosingSelectedGeneration() = Base().use { base ->
    val probe = Files.createTempDirectory("cleanup-probe").toFile()
    val count = try { base.files.copyRecursively(probe,true); val io = CrashIo(); change(probe,base.master,io); io.calls }
      finally { probe.deleteRecursively() }
    assertTrue(count > 20)
    for (after in listOf(false,true)) for (boundary in 1..count) {
      val files = Files.createTempDirectory("cleanup-boundary").toFile()
      try {
        base.files.copyRecursively(files,true)
        assertThrows(SecondaryStoreException::class.java) { change(files,base.master,CrashIo(boundary,after)) }
        val restarted = testStore(files)
        assertEquals("boundary=$boundary after=$after",SecondaryPreflight.READY,restarted.preflight())
        restarted.authenticateRecovery(base.secret).use { assertEquals(StrongAuthInterval.THREE_DAYS,it.strongAuthInterval) }
        assertFalse(files.resolve("domain-store/retirement").exists())
        assertEquals(1,files.resolve("domain-store/slots").listFiles()!!.size)
        assertEquals("primary-unchanged",files.resolve("vault/canary").readText())
      } finally { files.deleteRecursively() }
    }
  }
  @Test fun failedSelectionDirectorySyncDoesNotRetirePredecessorBeforeDurability() = Base().use { base ->
    val root = base.files.resolve("domain-store").toPath()
    var promoted = false
    val io = object : SecondaryStorageIo by DurableSecondaryIo {
      override fun atomicReplace(source: Path,target: Path) { DurableSecondaryIo.atomicReplace(source,target); if (target == root.resolve("selected")) promoted = true }
      override fun syncDirectory(path: Path) { if (promoted && path == root) throw IOException("selection sync unavailable"); DurableSecondaryIo.syncDirectory(path) }
      override fun remove(path: Path,directory: Boolean) { fail("must establish durable selection before any retirement") }
    }
    assertThrows(SecondaryStoreException::class.java) { change(base.files,base.master,io) }
    assertTrue(promoted)
    assertTrue(base.files.resolve("domain-store/retirement").exists())
    assertEquals(2,base.files.resolve("domain-store/slots").listFiles()!!.size)
    assertEquals(SecondaryPreflight.READY,testStore(base.files).preflight())
    assertEquals(1,base.files.resolve("domain-store/slots").listFiles()!!.size)
  }
  private fun abandonedCleanup(base: Base) {
    val io = object : SecondaryStorageIo by DurableSecondaryIo {
      override fun remove(path: Path,directory: Boolean) { throw IOException("process ended before cleanup") }
    }
    assertThrows(SecondaryStoreException::class.java) { change(base.files,base.master,io) }
    assertTrue(base.files.resolve("domain-store/retirement").exists())
  }
  @Test fun missingDeviceMaintenanceKeyNeedsCorrectStrongCredentialBeforeAnyRepair() = Base().use { base ->
    abandonedCleanup(base); SyntheticRetirementKeys.forget(base.identity)
    val restarted = testStore(base.files)
    assertEquals(SecondaryPreflight.READY,restarted.preflight())
    assertNull(restarted.biometricRecord())
    assertThrows(SecondaryStoreException::class.java) { restarted.authenticatePin("999999999999".toCharArray()).close() }
    assertFalse(SyntheticRetirementKeys.hasKey(base.identity))
    assertTrue(base.files.resolve("domain-store/retirement").exists())
    assertEquals(2,base.files.resolve("domain-store/slots").listFiles()!!.size)
    assertThrows(SecondaryStoreException::class.java) { restarted.authenticatePin("111111111111".toCharArray(),{ throw SecurityException("cancelled") }).close() }
    assertFalse(SyntheticRetirementKeys.hasKey(base.identity))
    restarted.authenticatePin("111111111111".toCharArray()).close()
    assertTrue(SyntheticRetirementKeys.hasKey(base.identity))
    assertFalse(base.files.resolve("domain-store/retirement").exists())
    assertEquals(1,base.files.resolve("domain-store/slots").listFiles()!!.size)
    assertEquals("primary-unchanged",base.files.resolve("vault/canary").readText())
  }
  @Test fun independentRecoveryRepairsMissingMaintenanceKeyWithoutPrimaryAuthority() = Base().use { base ->
    abandonedCleanup(base); SyntheticRetirementKeys.forget(base.identity)
    val restarted = testStore(base.files)
    assertThrows(SecondaryStoreException::class.java) { restarted.authenticateRecovery(ByteArray(32){9}).close() }
    assertFalse(SyntheticRetirementKeys.hasKey(base.identity))
    restarted.authenticateRecovery(base.secret).use { recovered -> val key = recovered.takeMaster(); try { assertArrayEquals(base.master,key) } finally { key.fill(0) } }
    assertFalse(base.files.resolve("domain-store/retirement").exists())
    assertEquals("primary-unchanged",base.files.resolve("vault/canary").readText())
  }
  @Test fun missingMaintenanceKeyCannotTurnMalformedJournalIntoRepairableMaterial() = Base().use { base ->
    abandonedCleanup(base)
    val journal = base.files.resolve("domain-store/retirement")
    val original = journal.readBytes()
    SyntheticRetirementKeys.forget(base.identity)
    val mutations = listOf<(ByteArray) -> ByteArray>(
      { it.apply { this[145] = 1 } },
      { it.apply { this[140] = 127; this[141] = 127 } },
      { it.apply { copyInto(this,162,146,162) } },
      { it.copyOf(it.size + 1) },
    )
    for ((i, mutation) in mutations.withIndex()) {
      journal.writeBytes(mutation(original.copyOf()))
      val restarted = testStore(base.files)
      run {
        assertEquals("malformed journal $i",SecondaryPreflight.UNAVAILABLE,restarted.preflight())
        assertThrows(SecondaryStoreException::class.java) { restarted.authenticateRecovery(base.secret).close() }
        assertFalse(SyntheticRetirementKeys.hasKey(base.identity))
        assertEquals(2,base.files.resolve("domain-store/slots").listFiles()!!.size)
      }
    }
    journal.writeBytes(original)
    assertEquals("primary-unchanged",base.files.resolve("vault/canary").readText())
  }
  @Test fun badMacUnknownJournalOrCorruptedSelectedMaterialNeverAuthorizeRepair() = Base().use { base ->
    abandonedCleanup(base)
    val journal = base.files.resolve("domain-store/retirement")
    val original = journal.readBytes()
    journal.writeBytes(original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
    assertEquals(SecondaryPreflight.UNAVAILABLE,testStore(base.files).preflight())
    assertThrows(SecondaryStoreException::class.java) { testStore(base.files).authenticateRecovery(base.secret).close() }
    journal.writeBytes(original.copyOf().apply { this[9] = 2 })
    SyntheticRetirementKeys.forget(base.identity)
    assertEquals(SecondaryPreflight.UNAVAILABLE,testStore(base.files).preflight())
    journal.writeBytes(original)
    val selected = base.files.resolve("domain-store/selected").readBytes().copyOfRange(10,26).hex()
    val index = base.files.resolve("domain-store/index/$selected/index")
    index.writeBytes(index.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
    assertThrows(SecondaryStoreException::class.java) { testStore(base.files).authenticateRecovery(base.secret).close() }
    assertFalse(SyntheticRetirementKeys.hasKey(base.identity))
    assertTrue(journal.exists())
    assertEquals(2,base.files.resolve("domain-store/slots").listFiles()!!.size)
  }
}
