package uk.co.traynor.privategallery.core.domain

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** ART lifecycle fixtures use synthetic JCA keys/backend only, never Android Keystore,
 * biometric authorization, selected owner storage or owner credentials. */
@RunWith(AndroidJUnit4::class)
class Phase3EnrollmentDeleteTest {
  private val identity = DomainIdentity(ByteArray(16) { 1 }, ByteArray(16) { 2 })
  private val id = ByteArray(16) { 3 }
  private val master = ByteArray(32) { it.toByte() }
  private open class SyntheticBackend : SecondaryBiometricKeyBackend {
    val keys = mutableMapOf<String, SecretKey>()
    var creates = 0
    var lookups = 0
    var deletes = 0
    override fun create(alias: String): Cipher {
      if (keys.containsKey(alias)) throw F1Exception(F1Failure.UNAVAILABLE)
      creates++
      val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
      keys[alias] = key
      return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
    override fun decrypt(alias: String, nonce: ByteArray): Cipher {
      lookups++
      val key = keys[alias] ?: throw F1Exception(F1Failure.UNAVAILABLE)
      return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
    }
    override fun deleteOwned(alias: String) { deletes++; keys.remove(alias) }
  }
  private fun reject(block: () -> Unit): F1Failure {
    try { block(); fail("accepted") } catch (e: F1Exception) { return e.failure }
    throw AssertionError()
  }
  private fun enrollment(slot: SecondaryBiometricSlot): ByteArray {
    val pending = slot.prepareEnrollment(identity, id, 1)
    return try {
      slot.finishEnrollment(pending, pending.cipher, master).also { pending.markInstalled() }
    } finally { pending.close() }
  }
  @Test fun originalEnrollmentDeletionRunsOutsideItsMonitor() {
    lateinit var pending: SecondaryBiometricSlot.PendingEnrollment
    var held = false
    val backend = object : SyntheticBackend() {
      override fun deleteOwned(alias: String) { held = Thread.holdsLock(pending); super.deleteOwned(alias) }
    }
    val slot = SecondaryBiometricSlot(backend)
    pending = slot.prepareEnrollment(identity, id, 1)
    pending.close()
    assertFalse("Native deletion must execute outside the enrollment monitor", held)
    assertEquals(1, backend.deletes)
  }
  @Test fun failedFinishDeletionRunsAfterOriginalMonitorUnwinds() {
    lateinit var pending: SecondaryBiometricSlot.PendingEnrollment
    var held = false
    val backend = object : SyntheticBackend() {
      override fun deleteOwned(alias: String) { held = Thread.holdsLock(pending); super.deleteOwned(alias) }
    }
    val slot = SecondaryBiometricSlot(backend)
    pending = slot.prepareEnrollment(identity, id, 1)
    reject { slot.finishEnrollment(pending, Cipher.getInstance("AES/GCM/NoPadding"), master) }
    assertFalse("Failure cleanup must execute after the finish monitor unwinds", held)
    assertEquals(1, backend.deletes)
  }
  @Test fun failedOriginalDeletionCannotBecomeSuccessfulDuplicateClose() {
    var deletes = 0
    val failure = F1Exception(F1Failure.UNAVAILABLE)
    val backend = object : SyntheticBackend() {
      override fun deleteOwned(alias: String): Unit { deletes++; throw failure }
    }
    val pending = SecondaryBiometricSlot(backend).prepareEnrollment(identity, id, 1)
    assertSame(failure, assertThrows(F1Exception::class.java) { pending.close() })
    assertSame(failure, assertThrows(F1Exception::class.java) { pending.close() })
    assertEquals(1, deletes)
  }
  @Test fun inheritedEnrollmentMonitorDeniesCloseBeforeStateOrProviderChanges() {
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend)
    val pending = slot.prepareEnrollment(identity, id, 1)
    synchronized(pending) { assertThrows(F1Exception::class.java) { pending.close() } }
    assertEquals(0, backend.deletes)
    val envelope = slot.finishEnrollment(pending, pending.cipher, master)
    try { pending.markInstalled(); pending.close() } finally { envelope.fill(0) }
    assertEquals(0, backend.deletes)
    assertTrue(backend.keys.containsKey(SecondaryBiometricEnvelope.alias(identity, id)))
  }
  @Test fun heldDeletionLeavesMonitorAvailableAndDuplicateCloseWaitsForActualReturn() {
    val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
    val firstDone = java.util.concurrent.CountDownLatch(1); val secondDone = java.util.concurrent.CountDownLatch(1)
    val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
    val backend = object : SyntheticBackend() {
      override fun deleteOwned(alias: String) { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); super.deleteOwned(alias) }
    }
    val pending = SecondaryBiometricSlot(backend).prepareEnrollment(identity, id, 1)
    val first = kotlin.concurrent.thread(isDaemon = true) { try { pending.close() } catch (e: Throwable) { failure.set(e) } finally { firstDone.countDown() } }
    var second: Thread? = null
    try {
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
      second = kotlin.concurrent.thread(isDaemon = true) { try { pending.close() } catch (e: Throwable) { failure.set(e) } finally { secondDone.countDown() } }
      val until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
      while (second.state != Thread.State.WAITING && secondDone.count != 0L && System.nanoTime() < until) Thread.yield()
      assertEquals("Duplicate waits outside the enrollment monitor for actual provider return", Thread.State.WAITING, second.state)
      assertThrows(F1Exception::class.java) { pending.markInstalled() }
      assertEquals(1L, firstDone.count); assertEquals(1L, secondDone.count)
      release.countDown()
      assertTrue(firstDone.await(5, java.util.concurrent.TimeUnit.SECONDS)); assertTrue(secondDone.await(5, java.util.concurrent.TimeUnit.SECONDS))
      assertNull(failure.get()); assertEquals(1, backend.deletes)
    } finally { release.countDown(); first.join(5000); second?.join(5000); assertFalse(first.isAlive); assertFalse(second?.isAlive == true) }
  }
  @Test fun providerSelfReentryDeniesPromptlyWithoutRetryOrPrematureCompletion() {
    lateinit var pending: SecondaryBiometricSlot.PendingEnrollment
    var held = false; var reentryDenied = false
    val backend = object : SyntheticBackend() {
      override fun deleteOwned(alias: String) {
        held = Thread.holdsLock(pending)
        assertThrows(F1Exception::class.java) { pending.close() }; reentryDenied = true
        super.deleteOwned(alias)
      }
    }
    pending = SecondaryBiometricSlot(backend).prepareEnrollment(identity, id, 1)
    pending.close(); pending.close()
    assertTrue(reentryDenied); assertFalse(held); assertEquals(1, backend.deletes)
  }
  @Test fun admittedDeletionErrorRetainsItsExactStickyOutcome() {
    var deletes = 0; val failure = AssertionError("synthetic deletion error")
    val backend = object : SyntheticBackend() { override fun deleteOwned(alias: String): Unit { deletes++; throw failure } }
    val pending = SecondaryBiometricSlot(backend).prepareEnrollment(identity, id, 1)
    assertSame(failure, assertThrows(AssertionError::class.java) { pending.close() })
    assertSame(failure, assertThrows(AssertionError::class.java) { pending.close() })
    assertEquals(1, deletes)
  }
  @Test fun repeatedFinishDenialCannotInvalidateCompletedUninstalledEnrollment() {
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend)
    val pending = slot.prepareEnrollment(identity, id, 1)
    val envelope = slot.finishEnrollment(pending, pending.cipher, master)
    try {
      reject { slot.finishEnrollment(pending, pending.cipher, master) }
      pending.markInstalled(); pending.close()
      assertEquals(0, backend.deletes)
      assertTrue(backend.keys.containsKey(SecondaryBiometricEnvelope.alias(identity, id)))
    } finally { envelope.fill(0) }
  }
  @Test fun interruptedDuplicateClosePreservesInterruptAndCannotCompleteOriginalDeletion() {
    val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
    val done = java.util.concurrent.CountDownLatch(1); val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
    val backend = object : SyntheticBackend() {
      override fun deleteOwned(alias: String) { entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)); super.deleteOwned(alias) }
    }
    val pending = SecondaryBiometricSlot(backend).prepareEnrollment(identity, id, 1)
    val first = kotlin.concurrent.thread(isDaemon = true) { try { pending.close() } catch (e: Throwable) { failure.set(e) } finally { done.countDown() } }
    try {
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
      Thread.currentThread().interrupt()
      try {
        assertThrows(F1Exception::class.java) { pending.close() }
        assertTrue(Thread.currentThread().isInterrupted)
        assertEquals(1L, done.count)
      } finally { Thread.interrupted() }
      release.countDown(); assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
      pending.close(); assertNull(failure.get()); assertEquals(1, backend.deletes)
    } finally { Thread.interrupted(); release.countDown(); first.join(5000); assertFalse(first.isAlive) }
  }
}
