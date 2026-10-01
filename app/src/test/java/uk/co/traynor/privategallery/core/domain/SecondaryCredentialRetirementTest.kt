package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Search current on-disk state directly; selected-generation rejection alone is insufficient. */
class SecondaryCredentialRetirementTest {
  private class Fixture : AutoCloseable {
    val files = Files.createTempDirectory("retirement").toFile()
    val store = testStore(files)
    val authority = SecondarySessionAuthority { 1L }
    val pending = store.create("111111111111".toCharArray(), {}, { it() })
    val identity = pending.identity
    val secret = pending.recoverySecret
    init { store.confirm(pending, secret, {}, { it() }).use { authority.completeAuthentication(authority.beginAuthentication(), it.takeMaster()) } }
    fun countPin(pin: String): Int = files.resolve("domain-store/slots").walkTopDown().filter { it.isFile }.count { file ->
      try { F1Slot.unwrapPin(identity, pin.toCharArray(), file.readBytes()).also { it.fill(0) }; true } catch (_: F1Exception) { false }
    }
    fun countRecovery(secret: ByteArray): Int = files.resolve("domain-store/recovery").walkTopDown().filter { it.isFile }.count { file ->
      try { F1Slot.unwrapRecovery(identity, secret, file.readBytes()).also { it.fill(0) }; true } catch (_: F1Exception) { false }
    }
    override fun close() { authority.revoke(); secret.fill(0); pending.close(); files.deleteRecursively() }
  }
  @Test fun obsoletePinWrappersAreAbsentFromCurrentAppFiles() = Fixture().use { f ->
    checkNotNull(f.authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS))).use { f.store.changePin(it, "222222222222".toCharArray()) }
    assertEquals("old PIN cannot unwrap any retained local envelope", 0, f.countPin("111111111111"))
    assertEquals(1, f.countPin("222222222222"))
  }
  @Test fun obsoleteRecoveryWrappersAreAbsentAfterPossessionConfirmedReplacement() = Fixture().use { f ->
    checkNotNull(f.authority.operationOrNull(setOf(SecondaryScope.RECOVERY))).use { operation ->
      f.store.replaceRecovery(operation).use { replacement ->
        val secret = replacement.recoverySecret
        try { f.store.confirmReplacement(operation, replacement, secret) } finally { secret.fill(0) }
      }
    }
    assertEquals("old recovery cannot unwrap retained local copies", 0, f.countRecovery(f.secret))
    assertEquals(SecondaryPreflight.READY, testStore(f.files).preflight())
  }
  @Test fun canceledPinChangeDoesNotLeaveAUsableNewCredentialEnvelope() = Fixture().use { f ->
    val fault = object : SecondaryStorageIo by DurableSecondaryIo {
      override fun writeNew(path: Path, bytes: ByteArray) {
        DurableSecondaryIo.writeNew(path, bytes)
        if (path.parent.parent?.fileName?.toString() == "slots") f.authority.revoke()
      }
    }
    val changed = testStore(f.files, fault, Unit)
    checkNotNull(f.authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS))).use { operation ->
      assertThrows(SecondaryStoreException::class.java) { changed.changePin(operation, "333333333333".toCharArray()) }
    }
    assertEquals("canceled new credential cannot unwrap retained material", 0, f.countPin("333333333333"))
    f.store.authenticatePin("111111111111".toCharArray()).close()
  }
  @Test fun restartResumesRetirementAfterTheOldReservationWasRemoved() = Fixture().use { f ->
    val root = f.files.resolve("domain-store")
    val old = root.resolve("selected").readBytes().copyOfRange(10,26).hex()
    val fault = object : SecondaryStorageIo by DurableSecondaryIo {
      var interrupted = false
      override fun remove(path: Path, directory: Boolean) {
        if (interrupted) throw java.io.IOException("interrupted cleanup")
        DurableSecondaryIo.remove(path, directory)
        if (path == root.resolve("transactions/$old/reservation").toPath()) {
          interrupted = true
          throw java.io.IOException("interrupted after reservation removal")
        }
      }
    }
    checkNotNull(f.authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS))).use { op ->
      assertThrows(SecondaryStoreException::class.java) {
        testStore(f.files, fault).changePin(op, "222222222222".toCharArray())
      }
    }
    assertTrue(root.resolve("retirement").isFile)
    assertFalse(root.resolve("transactions/$old/reservation").exists())
    val restarted = testStore(f.files)
    assertEquals(SecondaryPreflight.READY, restarted.preflight())
    restarted.authenticatePin("222222222222".toCharArray()).close()
    assertFalse(root.resolve("retirement").exists())
    assertEquals(0, f.countPin("111111111111"))
  }
  @Test fun everyRetirementRemovalBoundaryRestartsWithoutPrimaryMutation() = Fixture().use { f ->
    f.files.resolve("vault").mkdir()
    f.files.resolve("vault/canary").writeText("primary")
    val master = checkNotNull(f.authority.operationOrNull()).use { it.key.copyOf() }
    class RemovalFault(val boundary: Int = Int.MAX_VALUE, val after: Boolean = false) : SecondaryStorageIo by DurableSecondaryIo {
      var calls = 0
      var stopped = false
      override fun remove(path: Path, directory: Boolean) {
        if (stopped) throw java.io.IOException("stopped")
        calls++
        if (calls == boundary && !after) { stopped = true; throw java.io.IOException("before removal") }
        DurableSecondaryIo.remove(path, directory)
        if (calls == boundary && after) { stopped = true; throw java.io.IOException("after removal") }
      }
      override fun syncDirectory(path: Path) {
        if (stopped) throw java.io.IOException("stopped")
        DurableSecondaryIo.syncDirectory(path)
      }
    }
    fun change(files: java.io.File, fault: RemovalFault) {
      val authority = SecondarySessionAuthority { 1L }
      authority.completeAuthentication(authority.beginAuthentication(), master.copyOf())
      try {
        checkNotNull(authority.operationOrNull(setOf(SecondaryScope.WRITE))).use { op ->
          testStore(files, fault).updateSettings(op, StrongAuthInterval.THREE_DAYS, SecondaryAutoLock.ONE_MINUTE)
        }
      } finally { authority.revoke() }
    }
    val probe = Files.createTempDirectory("removal-probe").toFile()
    val count = try {
      f.files.copyRecursively(probe, overwrite = true)
      val fault = RemovalFault(); change(probe, fault); fault.calls
    } finally { probe.deleteRecursively() }
    try {
      assertTrue(count > 10)
      for (after in listOf(false, true)) for (boundary in 1..count) {
        val dir = Files.createTempDirectory("removal-case").toFile()
        try {
          f.files.copyRecursively(dir, overwrite = true)
          assertThrows(SecondaryStoreException::class.java) { change(dir, RemovalFault(boundary, after)) }
          val reopened = testStore(dir)
          assertEquals("removal $boundary after=$after", SecondaryPreflight.READY, reopened.preflight())
          reopened.validateAuthenticated(master).close()
          assertFalse(dir.resolve("domain-store/retirement").exists())
          assertEquals("primary", dir.resolve("vault/canary").readText())
        } finally { dir.deleteRecursively() }
      }
    } finally { master.fill(0) }
  }
}
