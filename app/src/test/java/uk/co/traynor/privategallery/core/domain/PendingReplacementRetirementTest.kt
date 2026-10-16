package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class PendingReplacementRetirementTest {
  @Test fun replacementWaitsOutsideRootForActualBorrowReturnAndRejectsRevokedSuccess() {
    for (revoke in listOf(false, true)) {
      val dir = Files.createTempDirectory("pending-replacement-retirement").toFile()
      val authority = SecondarySessionAuthority { 0L }
      var replacement: PendingSetup? = null
      var operation: SecondaryOperation? = null
      try {
        val armed = java.util.concurrent.atomic.AtomicBoolean()
        val selected = CountDownLatch(1)
        val io = object : SecondaryStorageIo by DurableSecondaryIo {
          override fun atomicReplace(source: java.nio.file.Path, target: java.nio.file.Path) {
            DurableSecondaryIo.atomicReplace(source, target)
            if (armed.get() && target.fileName.toString() == "selected") selected.countDown()
          }
        }
        val store = testStore(dir, io)
        val initial = store.create("1234567890123456".toCharArray(), {}, { it() })
        val initialSecret = initial.recoverySecret
        val authenticated = try { store.confirm(initial, initialSecret, {}, { it() }) } finally { initialSecret.fill(0) }
        authenticated.use { assertTrue(authority.completeAuthentication(authority.beginAuthentication(), it.takeMaster())) }
        operation = checkNotNull(authority.operationOrNull(setOf(SecondaryScope.RECOVERY)))
        val original = store.replaceRecovery(operation); replacement = original
        val supplied = original.recoverySecret
        val borrowed = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        val borrowFailure = AtomicReference<Throwable>(); val confirmFailure = AtomicReference<Throwable>()
        val actualBytes = AtomicReference<ByteArray>()
        val borrower = Thread {
          try { original.consumeRecoverySecret { actualBytes.set(it); borrowed.countDown(); check(release.await(10, TimeUnit.SECONDS)); assertTrue(it.any { b -> b != 0.toByte() }) } }
          catch (failure: Throwable) { borrowFailure.set(failure) }
        }
        val confirmOperation = operation
        val confirmer = Thread {
          try { store.confirmReplacement(confirmOperation, original, supplied) }
          catch (failure: Throwable) { confirmFailure.set(failure) }
          finally { finished.countDown() }
        }
        borrower.start()
        try {
          assertTrue(borrowed.await(5, TimeUnit.SECONDS)); armed.set(true); confirmer.start()
          assertTrue("confirmation selection must precede root-observer checks", selected.await(5, TimeUnit.SECONDS))
          // Readback succeeds only after the selected generation became confirmed.
          val rootFree = CountDownLatch(1)
          val observerFailure = AtomicReference<Throwable>()
          val observer = Thread {
            try { assertEquals(SecondaryPreflight.READY, store.preflight()) }
            catch (failure: Throwable) { observerFailure.set(failure) }
            finally { rootFree.countDown() }
          }
          observer.start(); assertTrue(rootFree.await(5, TimeUnit.SECONDS)); observer.join(5000); assertFalse(observer.isAlive); assertNull(observerFailure.get())
          assertFalse("held pending buffer cannot count as retired success", finished.await(100, TimeUnit.MILLISECONDS))
          if (revoke) authority.revoke()
        } finally { release.countDown(); borrower.join(5000); confirmer.join(5000); supplied.fill(0) }
        assertFalse(borrower.isAlive); assertFalse(confirmer.isAlive); assertNull(borrowFailure.get())
        if (revoke) assertTrue(confirmFailure.get() is IllegalStateException) else assertNull(confirmFailure.get())
        original.awaitRetirement(); assertTrue(checkNotNull(actualBytes.get()).all { it == 0.toByte() })
      } finally {
        replacement?.close(); replacement?.awaitRetirement(); operation?.close(); authority.revoke()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!authority.cleanupComplete) { check(System.nanoTime() < deadline); Thread.yield() }
        dir.deleteRecursively()
      }
    }
  }
}
