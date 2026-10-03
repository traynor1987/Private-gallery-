package uk.co.traynor.privategallery

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority

class MainActivitySessionTest {
    @Test fun `queued Activity delivery after lease close publishes only original live epoch`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val queued = mutableListOf<() -> Unit>()
        var publications = 0
        var discards = 0
        publishProtected(operation, { queued.add(it) }, { discards++ }) { publications++ }
        operation.close()
        queued.removeAt(0).invoke()
        assertEquals(1, publications)
        publishProtected(operation, { queued.add(it) }, { discards++ }) { publications++ }
        authority.revoke(); authority.open(ByteArray(32))
        queued.removeAt(0).invoke()
        assertEquals(1, publications)
        assertEquals(1, discards)
        authority.revoke()
    }

    @Test fun `Activity jobs register before launch and close never-started key leases`() = runBlocking {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32) { 8 })
        val operation = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val copiedKey = operation.key
        var ran = false
        val job = launchOwned(operation, this, Dispatchers.Unconfined) { ran = true }
        job.join()
        assertTrue(ran)
        assertFalse("successful protected job must complete without self-cancellation", job.isCancelled)
        val queued = mutableListOf<() -> Unit>()
        var delivered = false
        publishProtected(operation, { queued.add(it) }) { delivered = !job.isCancelled }
        queued.removeAt(0).invoke()
        assertTrue(delivered)
        assertArrayEquals(ByteArray(32), copiedKey)
        val stale = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        authority.revoke()
        ran = false
        assertThrows(IllegalStateException::class.java) { launchOwned(stale, this, Dispatchers.Unconfined) { ran = true } }
        assertFalse(ran)
        assertArrayEquals(ByteArray(32), stale.key)
    }
    @Test fun `Activity teardown callback in cancelled scope discards work and closes its key lease`() = runBlocking {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32) { 8 })
        val operation = checkNotNull(authority.operationOrNull())
        val copiedKey = operation.key
        val lifecycle = Job().also { it.cancel() }
        var ran = false
        val job = launchOwned(operation, CoroutineScope(lifecycle), Dispatchers.Unconfined) { ran = true }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(ran)
        assertArrayEquals(ByteArray(32), copiedKey)
        authority.revoke()
        // Job completion is observable before its cancellation invocation and reserved
        // owning accounting have returned. Require the real asynchronous cleanup barrier.
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
        assertTrue(authority.cleanupComplete)
    }

    @Test fun `restore admission survives its own partial install but lock invalidates later promotions`() {
        val attempts = AuthenticationAttemptAuthority()
        val original = attempts.begin()
        var protectedRootExists = false
        var slotsInstalled = false
        attempts.commit(original) { protectedRootExists = true }
        assertTrue(protectedRootExists)
        // Initial fresh admission is captured; later stages must not recheck that predicate.
        attempts.commit(original) { slotsInstalled = true }
        assertTrue(slotsInstalled)
        attempts.revoke()
        assertThrows(IllegalStateException::class.java) { attempts.commit(original) { fail("cancelled restore promotion") } }
        attempts.begin()
        assertThrows(IllegalStateException::class.java) { attempts.commit(original) { fail("ABA restore promotion") } }
    }
}
