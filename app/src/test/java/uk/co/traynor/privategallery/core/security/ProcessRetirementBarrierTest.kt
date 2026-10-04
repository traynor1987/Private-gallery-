package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority

/** Use the real process pools; never clear or replace their outstanding tickets. */
class ProcessRetirementBarrierTest {
    @Test fun `new Primary authority cannot bypass a process-wide retired release`() {
        val first = PrimarySessionAuthority(); first.open(ByteArray(32))
        val second = PrimarySessionAuthority()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val owned = first.operationOrNull()!!.use { operation ->
            operation.createSessionOwned { attach -> AutoCloseable { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }.also(attach) }
        }
        first.revoke()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val key = ByteArray(32) { 9 }
            assertThrows("a new registry cannot reset original pending cleanup", IllegalStateException::class.java) { second.open(key) }
            assertArrayEquals(ByteArray(32), key)
            assertFalse(second.cleanupComplete)
        } finally { finish.countDown(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); second.revoke() }
        second.open(ByteArray(32)); second.revoke()
    }

    @Test fun `new Hidden authority cannot bypass a process-wide retired release`() {
        val first = SecondarySessionAuthority { 0L }
        assertTrue(first.completeAuthentication(first.beginAuthentication(), ByteArray(32)))
        val second = SecondarySessionAuthority { 0L }
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val owned = first.operationOrNull()!!.use { operation ->
            operation.createSessionOwned { attach -> AutoCloseable { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }.also(attach) }
        }
        first.revoke()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val attempt = second.beginAuthentication()
            assertThrows(IllegalStateException::class.java) { second.checkAuthentication(attempt) }
            val key = ByteArray(32) { 9 }
            assertThrows(IllegalStateException::class.java) { second.completeAuthentication(attempt, key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); second.revoke() }
        assertTrue(second.completeAuthentication(second.beginAuthentication(), ByteArray(32))); second.revoke()
    }

    @Test fun `retired Primary release cannot consume independent Hidden authentication`() {
        val first = PrimarySessionAuthority(); first.open(ByteArray(32))
        val hidden = SecondarySessionAuthority { 0L }
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val owned = first.operationOrNull()!!.use { operation ->
            operation.createSessionOwned { attach -> AutoCloseable { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }.also(attach) }
        }
        first.revoke()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(hidden.completeAuthentication(hidden.beginAuthentication(), ByteArray(32)))
            assertNotNull(hidden.operationOrNull()?.also { it.close() })
        } finally { finish.countDown(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); hidden.revoke() }
    }
}
