package uk.co.traynor.privategallery.core.domain

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class PendingSetupOwnershipTest {
  private fun awaitCleanup(authority: SecondarySessionAuthority) {
    val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (!authority.cleanupComplete) { check(System.nanoTime() < limit); Thread.yield() }
  }
  @Test fun normalProductionAndRetirementWipeBothExactBuffers() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt)
    val master = ByteArray(32) { 3 }; val secret = ByteArray(32) { 7 }
    try {
      resources.produceMaster({ master }) { m -> resources.produceSecret({ secret }) { s -> assertSame(master, m); assertSame(secret, s) } }
      resources.withMaster { assertSame(master, it) }
      resources.withSecret { assertSame(secret, it) }
      resources.close(); resources.awaitRetirement()
      assertTrue(master.all { it == 0.toByte() }); assertTrue(secret.all { it == 0.toByte() })
      assertThrows(IllegalStateException::class.java) { resources.withMaster { } }
    } finally { resources.close(); authority.cancelAuthentication(attempt); awaitCleanup(authority) }
  }
  @Test fun cancellationRetainsChargeAndBytesUntilActualProducerReturn() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt)
    val entered = CountDownLatch(1); val release = CountDownLatch(1)
    val bytes = ByteArray(32) { 9 }; val failure = AtomicReference<Throwable>()
    val worker = Thread {
      try { resources.produceMaster({ bytes }) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); assertEquals(9, it[0].toInt()) } }
      catch (problem: Throwable) { failure.set(problem) }
    }
    worker.start()
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.cancelAuthentication(attempt)
      assertFalse(resources.retirementComplete); assertEquals(9, bytes[0].toInt())
      assertThrows(IllegalStateException::class.java) { resources.withMaster { } }
    } finally { release.countDown(); worker.join(5000); resources.close(); resources.awaitRetirement(); awaitCleanup(authority) }
    assertFalse(worker.isAlive); assertNull(failure.get()); assertTrue(bytes.all { it == 0.toByte() })
  }
  @Test fun explicitCloseSealsFactoryAndBorrowBeforeQueuedCleanup() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt)
    try {
      resources.close()
      var entries = 0
      assertThrows(IllegalStateException::class.java) { resources.produceMaster({ entries++; ByteArray(32) }) { entries++ } }
      assertThrows(IllegalStateException::class.java) { resources.withSecret { entries++ } }
      assertEquals(0, entries)
    } finally { resources.close(); resources.awaitRetirement(); authority.cancelAuthentication(attempt); awaitCleanup(authority) }
  }
  @Test fun lateProducedBufferAfterCancellationIsWipedWithoutConsumption() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt)
    val entered = CountDownLatch(1); val release = CountDownLatch(1)
    val bytes = ByteArray(32) { 5 }; val failure = AtomicReference<Throwable>()
    val consumed = java.util.concurrent.atomic.AtomicBoolean()
    val worker = Thread {
      try { resources.produceMaster({ entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); bytes }) { consumed.set(true) } }
      catch (problem: Throwable) { failure.set(problem) }
    }
    worker.start()
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.cancelAuthentication(attempt)
      assertFalse(resources.retirementComplete)
    } finally { release.countDown(); worker.join(5000); resources.close(); resources.awaitRetirement(); awaitCleanup(authority) }
    assertFalse(worker.isAlive); assertFalse(consumed.get()); assertTrue(failure.get() is IllegalStateException)
    assertTrue(bytes.all { it == 0.toByte() })
  }
  @Test fun replacementRetainsOriginalEpochAfterShortLeaseCloses() {
    val authority = SecondarySessionAuthority { 0 }
    assertTrue(authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32)))
    val operation = checkNotNull(authority.operationOrNull(setOf(SecondaryScope.RECOVERY)))
    val resources = PendingSetupResources.forReplacement(operation)
    val secret = ByteArray(32) { 8 }
    try {
      resources.produceSecret({ secret }) { }
      operation.close()
      resources.withSecret { assertSame(secret, it) }
      assertThrows(IllegalStateException::class.java) { resources.withMaster { } }
      authority.revoke()
      assertThrows(IllegalStateException::class.java) { resources.withSecret { } }
    } finally { operation.close(); resources.close(); resources.awaitRetirement(); authority.revoke(); awaitCleanup(authority) }
    assertTrue(secret.all { it == 0.toByte() })
  }
  @Test fun capacityExhaustionDeniesBeforePendingConstruction() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val names = Array(16) { "held-$it" }
    val held = authority.createAttemptOwned(attempt, uk.co.traynor.privategallery.core.security.OwnedResourceManifest.io(*names)) {
      var first: AutoCloseable? = null
      for (name in names) { val child = attach(name, AutoCloseable { }); if (first == null) first = child }
      checkNotNull(first)
    }
    try { assertThrows(IllegalStateException::class.java) { PendingSetupResources.forAttempt(authority, attempt) } }
    finally { held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)); authority.cancelAuthentication(attempt); awaitCleanup(authority) }
  }
  @Test fun failedSecretConstructionStillWipesProducedMaster() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt); val master = ByteArray(32) { 4 }
    try {
      assertThrows(IllegalArgumentException::class.java) {
        resources.produceMaster({ master }) { resources.produceSecret({ throw IllegalArgumentException("synthetic") }) { } }
      }
      resources.close(); resources.awaitRetirement(); assertTrue(master.all { it == 0.toByte() })
    } finally { resources.close(); authority.cancelAuthentication(attempt); awaitCleanup(authority) }
  }
  @Test fun revokedBeforeProductionDeniesFactoryEntry() {
    val authority = SecondarySessionAuthority { 0 }; val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt); authority.cancelAuthentication(attempt)
    try {
      var entered = false
      assertThrows(IllegalStateException::class.java) { resources.produceMaster({ entered = true; ByteArray(32) }) { } }
      assertFalse(entered)
    } finally { resources.close(); resources.awaitRetirement(); awaitCleanup(authority) }
  }
}
