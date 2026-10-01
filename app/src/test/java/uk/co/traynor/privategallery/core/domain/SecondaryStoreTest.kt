package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SecondaryStoreTest {
  private val pin get() = "1234567890123456".toCharArray()
  private fun fail(action: () -> Unit) { try { action(); fail("accepted") } catch (_: SecondaryStoreException) {} }
  @Test fun pendingRestartAndPossessionConfirmationPreserveIndependentMaster() {
    val dir = Files.createTempDirectory("store-test").toFile()
    try {
      dir.resolve("vault").mkdir(); val primary = dir.resolve("vault/canary"); primary.writeText("primary")
      val store = SecondaryStore(dir)
      val pending = store.create(pin, {}, { it() })
      assertEquals(SecondaryPreflight.PENDING, store.preflight())
      fail { store.authenticateRecovery(pending.recoverySecret).close() }
      fail { store.confirm(pending, ByteArray(32), {}, { it() }).close() }
      val secret = pending.recoverySecret
      val auth = store.confirm(pending, secret, {}, { it() })
      val master = auth.takeMaster(); auth.close()
      assertEquals(SecondaryPreflight.READY, SecondaryStore(dir).preflight())
      SecondaryStore(dir).authenticatePin(pin).use { assertArrayEquals(master, it.takeMaster()) }
      store.authenticateRecovery(secret).use { assertArrayEquals(master, it.takeMaster()) }
      fail { store.authenticatePin("9999999999999999".toCharArray()).close() }
      assertEquals("primary", primary.readText()); master.fill(0); secret.fill(0)
    } finally { dir.deleteRecursively() }
  }
  @Test fun cancelledFreshCannotResetAndResumeChangesPendingSecret() {
    val dir = Files.createTempDirectory("store-test").toFile()
    try {
      val store = SecondaryStore(dir); val first = store.create(pin, {}, { it() }); val oldSecret = first.recoverySecret; first.close()
      fail { store.create(pin, {}, { it() }).close() }
      store.resumePending(pin, {}, { it() }).use { second ->
        assertFalse(oldSecret.contentEquals(second.recoverySecret))
        fail { store.confirm(second, oldSecret, {}, { it() }).close() }
        store.confirm(second, second.recoverySecret, {}, { it() }).close()
      }
    } finally { dir.deleteRecursively() }
  }
  @Test fun pinMutationPreservesMasterAndOldRecoveryUntilReplacementConfirmed() {
    val dir = Files.createTempDirectory("store-test").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); val oldSecret = pending.recoverySecret
      val auth = store.confirm(pending, oldSecret, {}, { it() }); val master = auth.takeMaster()
      val authority = SecondarySessionAuthority { System.nanoTime() / 1_000_000 }; val attempt = authority.beginAuthentication(); assertTrue(authority.completeAuthentication(attempt, master.copyOf()))
      authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS, SecondaryScope.RECOVERY, SecondaryScope.WRITE))!!.use { op ->
        store.changePin(op, "2345678901234567".toCharArray())
        fail { store.authenticatePin(pin).close() }
        store.authenticatePin("2345678901234567".toCharArray()).use { assertArrayEquals(master, it.takeMaster()) }
        val replacement = store.replaceRecovery(op)
        store.authenticateRecovery(oldSecret).close()
        store.confirmReplacement(op, replacement, replacement.recoverySecret)
        fail { store.authenticateRecovery(oldSecret).close() }
        store.updateSettings(op, StrongAuthInterval.THREE_DAYS, SecondaryAutoLock.ONE_MINUTE)
      }
      store.validateAuthenticated(master).use { assertEquals(StrongAuthInterval.THREE_DAYS, it.strongAuthInterval) }
      authority.revoke(); master.fill(0)
    } finally { dir.deleteRecursively() }
  }
  @Test fun staleRemovedBiometricRecordAndInsufficientScopeNeverAuthorize() {
    val dir = Files.createTempDirectory("device-extension").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); val auth = store.confirm(pending, pending.recoverySecret, {}, { it() })
      val master = auth.takeMaster(); val authority = SecondarySessionAuthority { System.nanoTime() / 1_000_000 }
      authority.completeAuthentication(authority.beginAuthentication(), master.copyOf())
      authority.operationOrNull(setOf(SecondaryScope.READ))!!.use { read -> fail { store.removeBiometric(read) } }
      authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS))!!.use { op ->
        store.installBiometric(op, F1Crypto.random(16), 1, byteArrayOf(9, 8, 7))
        val record = store.biometricRecord()!!
        store.validateBiometric(master, record).close()
        store.removeBiometric(op)
        assertNull(store.biometricRecord())
        fail { store.validateBiometric(master, record).close() }
      }
      authority.revoke(); master.fill(0)
    } finally { dir.deleteRecursively() }
  }
  @Test fun serializedMutationsRetainBothCredentialAndSettingsChanges() {
    val dir = Files.createTempDirectory("serialized-mutations").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); val auth = store.confirm(pending, pending.recoverySecret, {}, { it() })
      val authority = SecondarySessionAuthority { System.nanoTime() / 1_000_000 }
      authority.completeAuthentication(authority.beginAuthentication(), auth.takeMaster())
      val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
      val a = authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS))!!
      val b = authority.operationOrNull(setOf(SecondaryScope.WRITE))!!
      val threads = listOf(Thread { try { SecondaryStore(dir).changePin(a, "2345678901234567".toCharArray()) } catch (t: Throwable) { errors.add(t) } }, Thread { try { SecondaryStore(dir).updateSettings(b, StrongAuthInterval.SEVEN_DAYS, SecondaryAutoLock.FIVE_MINUTES) } catch (t: Throwable) { errors.add(t) } })
      threads.forEach(Thread::start); threads.forEach(Thread::join)
      assertTrue(errors.toString(), errors.isEmpty())
      store.authenticatePin("2345678901234567".toCharArray()).use { assertEquals(StrongAuthInterval.SEVEN_DAYS, it.strongAuthInterval); assertEquals(SecondaryAutoLock.FIVE_MINUTES, it.autoLock) }
      fail { store.authenticatePin(pin).close() }; authority.revoke()
    } finally { dir.deleteRecursively() }
  }
  @Test fun corruptionAndUnknownSelectionFailClosed() {
    val dir = Files.createTempDirectory("store-test").toFile()
    try {
      val store = SecondaryStore(dir); val pending = store.create(pin, {}, { it() }); store.confirm(pending, pending.recoverySecret, {}, { it() }).close()
      val token = dir.resolve("domain-store/selected").readBytes().copyOfRange(10, 26).hex()
      val index = dir.resolve("domain-store/index/$token/index")
      val bytes = index.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); index.writeBytes(bytes)
      fail { store.authenticatePin(pin).close() }
      dir.resolve("domain-store/selected").writeText("bad")
      assertEquals(SecondaryPreflight.UNAVAILABLE, store.preflight())
    } finally { dir.deleteRecursively() }
  }
  @Test fun concurrentFreshAdmissionsOnlyIssueOneMaster() {
    val dir = Files.createTempDirectory("store-test").toFile()
    try {
      val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
      val threads = List(2) { Thread { try { SecondaryStore(dir).create(pin, {}, { it() }).close(); results.add(true) } catch (_: SecondaryStoreException) { results.add(false) } } }
      threads.forEach(Thread::start); threads.forEach(Thread::join)
      assertEquals(1, results.count { it }); assertEquals(SecondaryPreflight.PENDING, SecondaryStore(dir).preflight())
    } finally { dir.deleteRecursively() }
  }
}
