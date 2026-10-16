package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.testSecondaryAuthority

class PendingConfirmationBorrowTest {
  @Test fun confirmationRetainsTheExactPendingMasterUntilSelectedNativeCallReturns() {
    val dir = Files.createTempDirectory("pending-confirmation-borrow").toFile()
    val authority = testSecondaryAuthority { 0L }
    val attempt = authority.beginAuthentication()
    val resources = PendingSetupResources.forAttempt(authority, attempt)
    val armed = AtomicBoolean(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
    val failure = AtomicReference<Throwable>(); var worker: Thread? = null; var expected: ByteArray? = null
    try {
      val io = object : SecondaryStorageIo by DurableSecondaryIo {
        override fun atomicReplace(source: java.nio.file.Path, target: java.nio.file.Path) {
          DurableSecondaryIo.atomicReplace(source, target)
          if (armed.get() && target.fileName.toString() == "selected") {
            entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
          }
        }
      }
      val store = testStore(dir, io)
      val pending = store.create("1234567890123456".toCharArray(), { authority.checkPreparation(attempt) },
        { action -> authority.commitPreparation(attempt, action) }, resources)
      val secret = pending.recoverySecret
      // Observe only our synthetic original under its real cell monitor.
      val cell = resources.javaClass.getDeclaredField("master").apply { isAccessible = true }.get(resources)
      val gate = cell.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(cell)
      val value = cell.javaClass.getDeclaredField("value").apply { isAccessible = true }
      val retired = cell.javaClass.getDeclaredField("retired").apply { isAccessible = true }
      val original = synchronized(gate) { value.get(cell) as ByteArray }; expected = original.copyOf()
      worker = Thread {
        try {
          val result = authority.prepareThreadAuthentication(attempt, charArrayOf(), secret) {
            store.confirm(pending, secret, { authority.checkPreparation(attempt) },
              { action -> authority.commitPreparation(attempt, action) }).use { it.takeMaster() }
          }
          try { assertTrue(authority.completeAuthentication(attempt, result)) } finally { result.close() }
        } catch (problem: Throwable) { failure.set(problem) }
      }
      armed.set(true); worker.start()
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS)); pending.close()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!synchronized(gate) { retired.getBoolean(cell) }) { check(System.nanoTime() < deadline); Thread.yield() }
        synchronized(gate) {
          assertTrue("actual pending cleanup must retain the exact bytes borrowed by confirmation", original.any { it != 0.toByte() })
        }
      } finally { release.countDown(); worker.join(10000) }
      assertFalse(worker.isAlive); assertNull(failure.get()); pending.awaitRetirement()
      assertTrue(original.all { it == 0.toByte() }); assertTrue(secret.all { it == 0.toByte() })
      checkNotNull(authority.operationOrNull(setOf(SecondaryScope.READ))).use { assertArrayEquals(expected, it.key) }
      store.authenticatePin("1234567890123456".toCharArray()).use { assertTrue(it.confirmed); assertTrue(it.identity.matches(pending.identity.container, pending.identity.master)) }
    } finally {
      release.countDown(); worker?.join(10000); resources.close(); authority.revoke()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (!authority.cleanupComplete) { check(System.nanoTime() < deadline); Thread.yield() }
      expected?.fill(0); dir.deleteRecursively()
    }
  }
}
