package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PrimarySessionAuthorityTest {
    @Test fun `closed lease keeps epoch publication but stops decrypt commit and fork`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32) { 7 })
        val operation = checkNotNull(authority.operationOrNull())
        val copiedKey = operation.key
        operation.close()
        assertTrue(operation.isCurrent)
        assertArrayEquals(ByteArray(32), copiedKey)
        assertEquals("queued result", operation.publish { "queued result" })
        assertThrows(IllegalStateException::class.java) { operation.checkValid() }
        assertThrows(IllegalStateException::class.java) { operation.commit { fail("closed commit ran") } }
        assertThrows(IllegalStateException::class.java) { operation.fork() }
    }

    @Test fun `ABA denies stale publication commit and fork after reentry`() {
        val authority = PrimarySessionAuthority { 0 }
        val originalKey = ByteArray(32) { 9 }
        authority.open(originalKey)
        val stale = checkNotNull(authority.operationOrNull())
        authority.revoke()
        assertArrayEquals(ByteArray(32), originalKey)
        authority.open(ByteArray(32) { 9 })
        val current = checkNotNull(authority.operationOrNull())
        assertNotEquals(stale.epoch, current.epoch)
        assertNotEquals(stale.operationId, current.operationId)
        assertEquals(ContainerId.PRIMARY, current.containerId)
        assertFalse(stale.isCurrent)
        assertThrows(IllegalStateException::class.java) { stale.publish { fail("stale publication ran") } }
        assertThrows(IllegalStateException::class.java) { stale.commit { fail("stale commit ran") } }
        assertThrows(IllegalStateException::class.java) { stale.fork() }
        assertEquals(42, current.commit { 42 })
        authority.revoke()
    }

    @Test fun `deadline rejects protected actions even without timer or foreground`() {
        var now = 100L
        val authority = PrimarySessionAuthority { now }
        authority.open(ByteArray(32) { 8 })
        val operation = checkNotNull(authority.operationOrNull())
        authority.onBackgrounded(30_000)
        now = 30_100
        assertThrows(IllegalStateException::class.java) { operation.commit { fail("expired commit ran") } }
        assertNull(authority.operationOrNull())
        assertFalse(operation.isCurrent)
        assertThrows(IllegalStateException::class.java) { operation.publish { fail("expired publication ran") } }
        authority.onForegrounded()
        assertNull(authority.operationOrNull())
    }

    @Test fun `unexpired foreground clears deadline and fork keeps epoch`() {
        var now = 0L
        val authority = PrimarySessionAuthority { now }
        authority.open(ByteArray(32) { 4 })
        val parent = checkNotNull(authority.operationOrNull())
        val child = parent.fork()
        assertEquals(parent.epoch, child.epoch)
        assertNotEquals(parent.operationId, child.operationId)
        authority.onBackgrounded(100)
        now = 99
        authority.onForegrounded()
        now = 100_000
        child.checkValid()
        authority.onBackgrounded(0)
        assertFalse(parent.isCurrent)
    }

    @Test fun `revoke closes resources cancels jobs and waits for asynchronous cleanup`() = runBlocking {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32) { 6 })
        val operation = checkNotNull(authority.operationOrNull())
        var closes = 0
        operation.own(AutoCloseable { closes++ })
        val entered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try { entered.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { releaseCleanup.await() } }
        }
        operation.own(job)
        entered.await()
        authority.revoke()
        assertEquals(1, closes)
        assertTrue(job.isCancelled)
        assertFalse(authority.cleanupComplete)
        assertArrayEquals(ByteArray(32), operation.key)
        releaseCleanup.complete(Unit)
        job.join()
        assertTrue(authority.cleanupComplete)
        operation.close()
        assertEquals(1, closes)
    }

    @Test fun `register after revoke immediately closes resource or cancels job`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        authority.revoke()
        var closes = 0
        assertThrows(IllegalStateException::class.java) { operation.own(AutoCloseable { closes++ }) }
        assertEquals(1, closes)
        val job = Job()
        assertThrows(IllegalStateException::class.java) { operation.own(job) }
        assertTrue(job.isCancelled)
    }

    @Test fun `revoke waits for already admitted commit and denies later commit`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        val admitted = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val revoked = CountDownLatch(1)
        val writer = thread { operation.commit { admitted.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS)) } }
        assertTrue(admitted.await(5, TimeUnit.SECONDS))
        val revoker = thread { authority.revoke(); revoked.countDown() }
        assertFalse(revoked.await(100, TimeUnit.MILLISECONDS))
        finish.countDown()
        writer.join(5_000); revoker.join(5_000)
        assertEquals(0L, revoked.count)
        assertThrows(IllegalStateException::class.java) { operation.commit { fail("revoked commit ran") } }
    }
    @Test fun `reopen denies incomplete cleanup and wipes rejected transferred key`() = runBlocking {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32) { 1 })
        val operation = checkNotNull(authority.operationOrNull())
        val cleanup = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
        }
        operation.own(job)
        authority.revoke()
        val rejected = ByteArray(32) { 4 }
        assertThrows(IllegalStateException::class.java) { authority.open(rejected) }
        assertArrayEquals(ByteArray(32), rejected)
        assertNull(authority.operationOrNull())
        cleanup.complete(Unit); job.join()
        authority.open(ByteArray(32) { 2 })
        assertNotNull(authority.operationOrNull())
        authority.revoke()
    }

    @Test fun `resource close failure does not skip other cleanup or claim completion`() {
        val authority = PrimarySessionAuthority { 0 }
        val key = ByteArray(32) { 1 }
        authority.open(key)
        val operation = checkNotNull(authority.operationOrNull())
        var closed = false
        operation.own(AutoCloseable { throw java.io.IOException("synthetic close failure") })
        operation.own(AutoCloseable { closed = true })
        authority.revoke()
        assertTrue(closed)
        assertFalse(operation.isCurrent)
        assertFalse(authority.cleanupComplete)
        assertArrayEquals(ByteArray(32), key)
        assertArrayEquals(ByteArray(32), operation.key)
    }
    @Test fun `scoped handles cannot be resolved by a later epoch`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val first = checkNotNull(authority.operationOrNull())
        val handle = first.handle("synthetic-item", "legacy")
        first.validate(handle)
        authority.revoke(); authority.open(ByteArray(32))
        val second = checkNotNull(authority.operationOrNull())
        assertThrows(IllegalStateException::class.java) { second.validate(handle) }
        second.validate(second.handle("synthetic-item"))
        authority.revoke()
    }
    @Test fun `session resource outlives key lease and closes on original epoch revocation`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        var closes = 0
        val handoff = operation.own(AutoCloseable { closes++ })
        operation.ownForSession(handoff)
        operation.close()
        assertEquals(0, closes)
        operation.publish { operation.ownForSession(AutoCloseable { closes++ }) }
        assertEquals(0, closes)
        authority.revoke()
        assertEquals(2, closes)
        authority.open(ByteArray(32))
        assertThrows(IllegalStateException::class.java) { operation.ownForSession(AutoCloseable { closes++ }) }
        assertEquals(3, closes)
        authority.revoke()
    }
}
