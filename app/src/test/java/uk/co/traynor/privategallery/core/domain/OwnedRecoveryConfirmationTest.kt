package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import uk.co.traynor.privategallery.core.security.*
import org.junit.Assert.*
import org.junit.Test

class OwnedRecoveryConfirmationTest {
  @Test fun storeConfirmationRetiresTheExactSuppliedSecretBeforeReturning() {
    val dir = Files.createTempDirectory("owned-recovery-confirmation").toFile()
    val authority = SecondarySessionAuthority { 0L }
    try {
      val store = testStore(dir)
      val initial = store.create("1234567890123456".toCharArray(), {}, { it() })
      val initialSecret = initial.recoverySecret
      try { store.confirm(initial, initialSecret, {}, { it() }).use {
        assertTrue(authority.completeAuthentication(authority.beginAuthentication(), it.takeMaster()))
      } } finally { initialSecret.fill(0) }
      checkNotNull(authority.operationOrNull(setOf(SecondaryScope.RECOVERY))).use { operation ->
        store.replaceRecovery(operation).use { pending ->
          val supplied = pending.recoverySecret
          store.confirmReplacement(operation, pending, supplied)
          assertArrayEquals("exact caller input must retire before confirmation succeeds", ByteArray(32), supplied)
          assertEquals(SecondaryPreflight.READY, store.preflight())
        }
      }
    } finally {
      authority.revoke()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (!authority.cleanupComplete) { check(System.nanoTime() < deadline); Thread.yield() }
      dir.deleteRecursively()
    }
  }

  private fun authority(pool: ReleasePool = ReleasePool(16)): SecondarySessionAuthority = testSecondaryAuthority { 0L }.also {
    it.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(it, pool)
    assertTrue(it.completeAuthentication(it.beginAuthentication(), ByteArray(32) { 6 }))
  }
  private fun operation(authority: SecondarySessionAuthority) = checkNotNull(authority.operationOrNull(setOf(SecondaryScope.RECOVERY)))
  private fun awaitCondition(condition: () -> Boolean) {
    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition()) { check(System.nanoTime() < end); Thread.yield() }
  }
  @Test fun normalUseBorrowsStableMasterAndExactSecretThenWipesBoth() {
    val authority = authority(); val operation = operation(authority)
    val secret = ByteArray(32) { 7 }; lateinit var master: ByteArray
    try {
      val result = OwnedRecoveryConfirmation.consume(operation, secret) { m, s ->
        master = m; assertNotSame(operation.key, m); assertSame(secret, s)
        assertArrayEquals(ByteArray(32) { 6 }, m); assertArrayEquals(ByteArray(32) { 7 }, s)
        operation.checkValid(); "confirmed"
      }
      assertEquals("confirmed", result)
      assertArrayEquals(ByteArray(32), master); assertArrayEquals(ByteArray(32), secret)
      assertArrayEquals(ByteArray(32) { 6 }, operation.key)
      assertTrue(operation.reservations.isEmpty())
    } finally { operation.close(); authority.revoke() }
  }
  @Test fun cancellationPreservesBothBuffersUntilActualConsumerReturnAndDeniesLateSuccess() {
    val pool = ReleasePool(16); val authority = authority(pool); val operation = operation(authority)
    val entered = CountDownLatch(1); val release = CountDownLatch(1)
    val secret = ByteArray(32) { 7 }; val master = AtomicReference<ByteArray>(); val failure = AtomicReference<Throwable>()
    val worker = Thread {
      try { OwnedRecoveryConfirmation.consume(operation, secret) { m, s ->
        master.set(m); entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(32) { 6 }, m); assertArrayEquals(ByteArray(32) { 7 }, s)
      } } catch (problem: Throwable) { failure.set(problem) }
    }
    worker.start()
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(2, pool.occupied)
      authority.revoke()
      assertArrayEquals(ByteArray(32), operation.key)
      assertArrayEquals(ByteArray(32) { 6 }, master.get()); assertArrayEquals(ByteArray(32) { 7 }, secret)
      assertEquals(2, pool.occupied); assertFalse(authority.cleanupComplete)
      val late = ByteArray(32) { 8 }; var consumed = false
      assertThrows(IllegalStateException::class.java) { OwnedRecoveryConfirmation.consume(operation, late) { _, _ -> consumed = true } }
      assertFalse(consumed); assertArrayEquals(ByteArray(32), late)
      val next = authority.beginAuthentication()
      assertThrows(IllegalStateException::class.java) { authority.checkPreparation(next) }
    } finally { release.countDown(); worker.join(5000); operation.close(); authority.revoke() }
    assertFalse(worker.isAlive); assertTrue(failure.get().toString(), failure.get() is IllegalStateException)
    assertArrayEquals(ByteArray(32), master.get()); assertArrayEquals(ByteArray(32), secret)
    awaitCondition { authority.cleanupComplete }; assertEquals(0, pool.occupied)
  }
  @Test fun insufficientTwoChildCapacityDeniesBeforeConsumerAndLeavesNoPartialReservation() {
    for (heldCount in listOf(15, 16)) {
      val pool = ReleasePool(16); val authority = authority(pool); val operation = operation(authority)
      val names = Array(heldCount) { "held-$it" }
      val held = operation.createOwned(OwnedResourceManifest.io(*names)) {
        var first: AutoCloseable? = null
        for (name in names) { val child = attach(name, AutoCloseable {}); if (first == null) first = child }
        checkNotNull(first)
      }
      try {
        val secret = ByteArray(32) { 7 }; var consumed = false
        assertThrows(IllegalStateException::class.java) { OwnedRecoveryConfirmation.consume(operation, secret) { _, _ -> consumed = true } }
        assertFalse(consumed); assertArrayEquals(ByteArray(32), secret)
        assertArrayEquals(ByteArray(32) { 6 }, operation.key)
        assertEquals(heldCount, pool.occupied); assertEquals(setOf(held.original), operation.reservations)
      } finally { held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)); operation.close(); authority.revoke() }
    }
  }
  @Test fun invalidSizesAndInheritedAuthorityGateDenyBeforeConsumerAndWipeInput() {
    val pool = ReleasePool(16); val authority = authority(pool); val operation = operation(authority)
    try {
      for (size in listOf(0, 31, 33)) {
        val secret = ByteArray(size) { 7 }; var consumed = false
        assertThrows(IllegalArgumentException::class.java) { OwnedRecoveryConfirmation.consume(operation, secret) { _, _ -> consumed = true } }
        assertFalse(consumed); assertArrayEquals(ByteArray(size), secret); assertEquals(0, pool.occupied)
      }
      val secret = ByteArray(32) { 7 }; var consumed = false
      operation.commit {
        assertThrows(IllegalStateException::class.java) { OwnedRecoveryConfirmation.consume(operation, secret) { _, _ -> consumed = true } }
      }
      assertFalse(consumed); assertArrayEquals(ByteArray(32), secret); assertEquals(0, pool.occupied)
    } finally { operation.close(); authority.revoke() }
  }
  @Test fun consumerExceptionIsPreservedAndBothExactBuffersRetire() {
    val authority = authority(); val operation = operation(authority)
    val expected = java.io.IOException("consumer failure"); val secret = ByteArray(32) { 7 }; lateinit var master: ByteArray
    try {
      val actual = assertThrows(java.io.IOException::class.java) { OwnedRecoveryConfirmation.consume(operation, secret) { m, _ -> master = m; throw expected } }
      assertSame(expected, actual); assertArrayEquals(ByteArray(32), master); assertArrayEquals(ByteArray(32), secret)
      assertTrue(operation.reservations.isEmpty())
    } finally { operation.close(); authority.revoke() }
  }
  @Test fun ordinaryConcurrentLiveOriginalDoesNotDenyConfirmation() {
    val pool = ReleasePool(16); val authority = authority(pool); val operation = operation(authority)
    var otherClosed = false
    val other = operation.createOwned(OwnedResourceManifest.io("other-live-owner")) {
      attach("other-live-owner", AutoCloseable { otherClosed = true })
    }
    val secret = ByteArray(32) { 7 }
    try {
      OwnedRecoveryConfirmation.consume(operation, secret) { master, exact ->
        assertEquals(3, pool.occupied); assertFalse(otherClosed)
        assertArrayEquals(ByteArray(32) { 6 }, master); assertSame(secret, exact)
      }
      assertFalse(otherClosed); assertEquals(1, pool.occupied)
      assertEquals(setOf(other.original), operation.reservations); operation.checkValid()
    } finally { other.close(); assertTrue(other.retirement.await(5, TimeUnit.SECONDS)); operation.close(); authority.revoke() }
  }
  @Test fun sameHelperCannotRetryAfterItsOriginalMandatoryRetirementFailed() {
    val pool = ReleasePool(16); val authority = authority(pool); val operation = operation(authority)
    try {
      assertThrows(IllegalStateException::class.java) {
        OwnedRecoveryConfirmation.consume(operation, ByteArray(32) { 7 }) { _, _ -> failMasterRetirement(operation) }
      }
      assertTrue(pool.hasFailedRetirement); val retained = pool.occupied
      val retry = ByteArray(32) { 8 }; var consumed = false
      assertThrows(IllegalStateException::class.java) {
        OwnedRecoveryConfirmation.consume(operation, retry) { _, _ -> consumed = true }
      }
      assertFalse("a prior failed original must deny this same family's fresh consumer", consumed)
      assertArrayEquals(ByteArray(32), retry); assertEquals(retained, pool.occupied)
    } finally { operation.close(); authority.revoke() }
  }
  private fun failMasterRetirement(operation: SecondaryOperation) {
    val original = operation.reservations.single()
    val tickets = original.javaClass.getDeclaredField("tickets").apply { isAccessible = true }.get(original) as List<*>
    val cell = tickets.first()!!.javaClass.getDeclaredField("resource").apply { isAccessible = true }.get(tickets.first())
    @Suppress("UNCHECKED_CAST")
    val done = cell.javaClass.getDeclaredField("done").apply { isAccessible = true }.get(cell) as CompletableFuture<Unit>
    done.completeExceptionally(java.io.IOException("synthetic actual buffer retirement failure"))
  }
  @Test fun consumerInterruptionPreservesTheOriginalThrowableAndInterruptStatus() {
    val closed = CountDownLatch(2); val release = CountDownLatch(1); val finished = CountDownLatch(1)
    val pool = ReleasePool(16, allocateInvocation = { action, returned ->
      ReleaseInvocation({ action(); closed.countDown(); check(release.await(5, TimeUnit.SECONDS)) }, returned)
    })
    val authority = authority(pool); val operation = operation(authority)
    val expected = InterruptedException("consumer interrupted"); val secret = ByteArray(32) { 7 }
    val master = AtomicReference<ByteArray>(); val failure = AtomicReference<Throwable>()
    val interrupted = java.util.concurrent.atomic.AtomicBoolean()
    val worker = Thread {
      try { OwnedRecoveryConfirmation.consume(operation, secret) { m, _ -> master.set(m); throw expected } }
      catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) }
      finally { finished.countDown() }
    }
    worker.start()
    try {
      assertTrue(closed.await(5, TimeUnit.SECONDS)); assertEquals(2, pool.occupied)
      assertFalse("consumer interruption cannot skip actual mandatory cleanup", finished.await(100, TimeUnit.MILLISECONDS))
      release.countDown(); worker.join(5000)
      assertFalse(worker.isAlive); assertSame(expected, failure.get())
      assertTrue("consumer interruption must remain observable by its caller", interrupted.get())
      assertTrue(operation.reservations.isEmpty()); assertEquals(0, pool.occupied)
      assertArrayEquals(ByteArray(32), master.get()); assertArrayEquals(ByteArray(32), secret)
    } finally { release.countDown(); worker.join(5000); operation.close(); authority.revoke() }
  }
  @Test fun completedBufferStagesCannotReplaceActualCleanupCallbackReturn() {
    mandatoryRetirement(false)
  }
  @Test fun interruptedMandatoryRetirementPreservesInterruptAndAccountingUntilActualReturn() {
    mandatoryRetirement(true)
  }
  private fun mandatoryRetirement(interrupt: Boolean) {
    val closed = CountDownLatch(2); val release = CountDownLatch(1)
    val pool = ReleasePool(16, allocateInvocation = { action, returned ->
      ReleaseInvocation({ action(); closed.countDown(); check(release.await(5, TimeUnit.SECONDS)) }, returned)
    })
    val authority = authority(pool); val operation = operation(authority)
    val secret = ByteArray(32) { 7 }; val master = AtomicReference<ByteArray>()
    val failure = AtomicReference<Throwable>(); val interrupted = java.util.concurrent.atomic.AtomicBoolean(); val finished = CountDownLatch(1)
    val worker = Thread {
      try { OwnedRecoveryConfirmation.consume(operation, secret) { m, _ -> master.set(m) } }
      catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) }
      finally { finished.countDown() }
    }
    worker.start()
    try {
      assertTrue(closed.await(5, TimeUnit.SECONDS))
      assertArrayEquals(ByteArray(32), secret); assertArrayEquals(ByteArray(32), master.get())
      assertEquals(2, pool.occupied); assertEquals(1, operation.reservations.size)
      assertFalse(finished.await(100, TimeUnit.MILLISECONDS))
      if (interrupt) {
        worker.interrupt(); assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertTrue(failure.get() is InterruptedException); assertTrue(interrupted.get())
        assertEquals(2, pool.occupied); assertFalse(authority.cleanupComplete)
        val retry = ByteArray(32) { 8 }; var consumed = false
        assertThrows(IllegalStateException::class.java) {
          OwnedRecoveryConfirmation.consume(operation, retry) { _, _ -> consumed = true; release.countDown() }
        }
        assertFalse("unfinished prior mandatory retirement denies fresh family consumption", consumed)
        assertArrayEquals(ByteArray(32), retry); assertEquals(2, pool.occupied)
      }
      val next = authority.beginAuthentication()
      assertThrows(IllegalStateException::class.java) { authority.checkPreparation(next) }
    } finally { release.countDown(); worker.join(5000); operation.close(); authority.revoke() }
    assertFalse(worker.isAlive)
    if (!interrupt) assertTrue("revoked exact operation cannot return late success", failure.get() is IllegalStateException)
    awaitCondition { authority.cleanupComplete }; assertEquals(0, pool.occupied)
  }
  @Test fun failedMandatoryRetirementRetainsAccountingAndOriginalConsumerFailure() {
    for (consumerFails in listOf(false, true)) {
      val pool = ReleasePool(16); val authority = authority(pool); val operation = operation(authority)
      val secret = ByteArray(32) { 7 }; lateinit var master: ByteArray
      val consumerFailure = java.io.IOException("consumer"); val cleanupFailure = java.io.IOException("cleanup")
      try {
        val actual = assertThrows(Throwable::class.java) { OwnedRecoveryConfirmation.consume(operation, secret) { m, _ ->
          master = m
          val original = operation.reservations.single()
          val tickets = original.javaClass.getDeclaredField("tickets").apply { isAccessible = true }.get(original) as List<*>
          val cell = tickets.first()!!.javaClass.getDeclaredField("resource").apply { isAccessible = true }.get(tickets.first())
          @Suppress("UNCHECKED_CAST")
          val done = cell.javaClass.getDeclaredField("done").apply { isAccessible = true }.get(cell) as CompletableFuture<Unit>
          done.completeExceptionally(cleanupFailure)
          if (consumerFails) throw consumerFailure
        } }
        if (consumerFails) { assertSame(consumerFailure, actual); assertEquals(1, actual.suppressed.size) }
        else assertTrue(actual is IllegalStateException)
        assertArrayEquals(ByteArray(32), secret); assertArrayEquals(ByteArray(32), master)
        assertTrue(pool.hasFailedRetirement); assertTrue(pool.occupied >= 1); assertFalse(authority.cleanupComplete)
        val next = authority.beginAuthentication()
        assertThrows(IllegalStateException::class.java) { authority.checkPreparation(next) }
      } finally { operation.close(); authority.revoke() }
    }
  }
}
