package uk.co.traynor.privategallery.core.domain

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.OwnedAuthenticationKey

class SecondaryAuthenticationWorkerTest {
  @Test fun actualWorkerHasTerminatedBeforeOwnedKeyCanBeAdmitted() {
    val authority = SecondarySessionAuthority { 1L }
    val attempt = authority.beginAuthentication()
    val producer = AtomicReference<Thread>()
    val pin = charArrayOf('8')
    val key = ByteArray(32) { 7 }
    val handoff = authority.prepareThreadAuthentication(attempt, pin) {
      producer.set(Thread.currentThread()); key
    }
    try {
      assertNotSame(Thread.currentThread(), producer.get())
      assertFalse("actual Thread return, not a body/finally marker", producer.get().isAlive)
      assertEquals('\u0000', pin[0])
      assertTrue(authority.completeAuthentication(attempt, handoff))
      assertTrue(handoff.retirement.await(5, TimeUnit.SECONDS))
    } finally { handoff.close(); authority.revoke() }
    assertArrayEquals(ByteArray(32), key)
  }

  @Test fun cancellationWhileActualWorkerBorrowsInputCannotAdmitOrWipeItEarly() {
    val authority = SecondarySessionAuthority { 1L }
    val attempt = authority.beginAuthentication()
    val entered = CountDownLatch(1); val release = CountDownLatch(1)
    val failed = AtomicReference<Throwable?>()
    val key = ByteArray(32) { 9 }; val pin = charArrayOf('8')
    val coordinator = Thread {
      try { authority.prepareThreadAuthentication(attempt, pin) {
        entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
        assertEquals('8', pin[0]); key
      } } catch (problem: Throwable) { failed.set(problem) }
    }
    coordinator.start()
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      authority.cancelAuthentication(attempt)
      assertEquals('8', pin[0]); assertFalse(authority.cleanupComplete)
      assertNull(authority.operationOrNull())
    } finally { release.countDown(); coordinator.join(10_000) }
    assertFalse(coordinator.isAlive); assertNotNull(failed.get())
    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!authority.cleanupComplete && System.nanoTime() < until) Thread.yield()
    assertTrue(authority.cleanupComplete); assertArrayEquals(ByteArray(32), key)
    assertEquals('\u0000', pin[0]); authority.revoke()
  }

  @Test fun wrongAttemptCannotConsumeOrRetireOwnedWorkerResult() {
    val authority = SecondarySessionAuthority { 1L }
    val old = authority.beginAuthentication(); val current = authority.beginAuthentication()
    val handoff = authority.prepareThreadAuthentication(current, charArrayOf()) { ByteArray(32) { 3 } }
    try {
      assertThrows(IllegalStateException::class.java) { authority.completeAuthentication(old, handoff) }
      assertTrue(authority.completeAuthentication(current, handoff))
      assertTrue(handoff.retirement.await(5, TimeUnit.SECONDS))
    } finally { handoff.close(); authority.revoke() }
  }
}
