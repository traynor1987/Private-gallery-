package uk.co.traynor.privategallery.core.security

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.runScopedImport

class ScopedImportTransactionTest {
    @Test fun `provider creation and actual close run outside storage before final commit`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val guard = ScopedIoGuard(op, PrimaryScope.WRITE); val storage = Any(); val closed = AtomicInteger()
        val result = runScopedImport(guard, storage, {
            assertFalse(Thread.holdsLock(storage))
            object : ByteArrayInputStream(byteArrayOf(42)) {
                override fun close() { synchronized(storage) { closed.incrementAndGet() } }
            }
        }, { input ->
            assertTrue(Thread.holdsLock(storage))
            input.close() // Nested crypto wrappers must not dispose the provider under storage.
            assertEquals(0, closed.get())
            input.read()
        }, { prepared ->
            assertTrue(Thread.holdsLock(storage)); assertEquals(1, closed.get()); prepared
        })
        assertEquals(42, result); authority.revoke(); assertTrue(authority.cleanupComplete)
    }
    @Test fun `failed native close cannot reach selected metadata commit`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val commits = AtomicInteger()
        assertThrows(IOException::class.java) {
            runScopedImport(ScopedIoGuard(op, PrimaryScope.WRITE), Any(), {
                object : ByteArrayInputStream(byteArrayOf(42)) { override fun close(): Unit = throw IOException("final provider close failed") }
            }, { it.read() }, { commits.incrementAndGet() })
        }
        assertEquals(0, commits.get()); authority.revoke(); assertFalse(authority.cleanupComplete)
    }
    @Test fun `revocation during outside-lock close denies final metadata commit`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val storage = Any(); val entered = CountDownLatch(1); val finish = CountDownLatch(1); val returned = CountDownLatch(1)
        val commits = AtomicInteger(); val failure = AtomicReference<Throwable>()
        thread(isDaemon = true) {
            try {
                runScopedImport(ScopedIoGuard(op, PrimaryScope.WRITE), storage, {
                    object : ByteArrayInputStream(byteArrayOf(42)) {
                        override fun close() { synchronized(storage) { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) } }
                    }
                }, { it.read() }, { commits.incrementAndGet() })
            } catch (problem: Throwable) { failure.set(problem) } finally { returned.countDown() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertFalse(authority.cleanupComplete); assertEquals(0, commits.get())
            val key = ByteArray(32) { 9 }
            assertThrows(IllegalStateException::class.java) { authority.open(key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS)) }
        assertTrue(failure.get() is IllegalStateException); assertEquals(0, commits.get()); authority.revoke()
    }
}
