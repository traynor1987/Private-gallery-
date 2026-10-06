package uk.co.traynor.privategallery.core.domain

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import uk.co.traynor.privategallery.core.security.ContainerId
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority

class SecondarySessionAuthorityTest {
    @Test fun `authentication tokens have no data class copy authority`() {
        assertFalse(SecondaryAuthAttempt::class.java.declaredMethods.any { it.name == "copy" || it.name == "copy\$default" })
        assertFalse(DiscoveryChallenge::class.java.declaredMethods.any { it.name == "copy" || it.name == "copy\$default" })
        val authority = SecondarySessionAuthority { 0 }
        authority.beginAuthentication()
        assertFalse(authority.completeAuthentication(SecondaryAuthAttempt(), ByteArray(32)))
        assertNull(authority.bindingOrNull())
    }
    private fun unlock(authority: SecondarySessionAuthority, key: ByteArray = ByteArray(32) { 7 }) {
        assertTrue(authority.completeAuthentication(authority.beginAuthentication(), key))
    }
    @Test fun `authentication commit admits only current attempt without issuing keys or authority`() {
        val authority = SecondarySessionAuthority { 0 }
        val stale = authority.beginAuthentication()
        val current = authority.beginAuthentication()
        authority.cancelAuthentication(stale)
        var actions = 0
        for (denied in listOf(stale, SecondaryAuthAttempt(), SecondarySessionAuthority { 0 }.beginAuthentication())) {
            assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(denied) }
            assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(denied) { actions++ } }
        }
        authority.checkAuthentication(current)
        assertEquals(42, authority.commitAuthentication(current) { actions++; 42 })
        assertEquals(1, actions)
        assertNull(authority.operationOrNull()); assertNull(authority.bindingOrNull())
        authority.cancelAuthentication(current)
        assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(current) }
        assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(current) { actions++ } }
        assertEquals(1, actions)
        val completed = authority.beginAuthentication()
        assertTrue(authority.completeAuthentication(completed, ByteArray(32)))
        assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(completed) { actions++ } }
    }
    @Test fun `authentication commit serializes final promotion against cancel and revoke`() {
        for (cancelOnly in listOf(true, false)) {
            val authority = SecondarySessionAuthority { 0 }
            val attempt = authority.beginAuthentication()
            val admitted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val cancellationStarted = CountDownLatch(1)
            val cancellationDone = CountDownLatch(1)
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            val events = java.util.Collections.synchronizedList(mutableListOf<String>())
            val promoter = thread {
                try {
                    authority.commitAuthentication(attempt) {
                        admitted.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                        events.add("promoted")
                    }
                } catch (error: Throwable) { failure.compareAndSet(null, error) }
            }
            assertTrue(admitted.await(5, TimeUnit.SECONDS))
            val canceller = thread {
                try {
                    cancellationStarted.countDown()
                    if (cancelOnly) authority.cancelAuthentication(attempt) else authority.revoke()
                    events.add("cancelled")
                } catch (error: Throwable) { failure.compareAndSet(null, error) }
                finally { cancellationDone.countDown() }
            }
            assertTrue(cancellationStarted.await(5, TimeUnit.SECONDS))
            try { assertFalse(cancellationDone.await(100, TimeUnit.MILLISECONDS)) }
            finally { release.countDown() }
            promoter.join(5_000); canceller.join(5_000)
            assertFalse(promoter.isAlive); assertFalse(canceller.isAlive)
            assertNull(failure.get())
            assertEquals(listOf("promoted", "cancelled"), events)
            assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(attempt) { fail() } }
            assertNull(authority.operationOrNull())
        }
    }
    @Test fun `Primary and Secondary sessions remain independent`() {
        val primary = PrimarySessionAuthority { 0 }
        val secondary = SecondarySessionAuthority { 0 }
        primary.open(ByteArray(32) { 1 }); unlock(secondary)
        val p = checkNotNull(primary.operationOrNull())
        val s = checkNotNull(secondary.operationOrNull())
        assertSame(ContainerId.SECONDARY, s.containerId)
        secondary.revoke(); p.checkValid()
        unlock(secondary); primary.revoke()
        checkNotNull(secondary.operationOrNull()).checkValid()
        secondary.revoke()
    }
    @Test fun `actual container IDs isolate colliding records epochs and preview identities`() {
        val primary = PrimarySessionAuthority { 0 }
        val secondary = SecondarySessionAuthority { 0 }
        try {
            primary.open(ByteArray(32) { 1 }); unlock(secondary)
            checkNotNull(primary.operationOrNull()).use { p ->
                checkNotNull(secondary.operationOrNull()).use { s ->
                    val id = "colliding-item-uuid"
                    val ph = p.handle(id,"same-revision-hash")
                    val sh = s.recordHandle(id,1)
                    var lookups = 0
                    assertThrows(IllegalStateException::class.java) {
                        p.resolve(ph.copy(containerId = ContainerId.SECONDARY), { lookups++; "same-revision-hash" }) { fail() }
                    }
                    assertEquals(0,lookups)
                    assertThrows(IllegalStateException::class.java) { s.validate(sh.copy(containerId = ContainerId.PRIMARY)) }
                    // Even deliberately equal numeric/value epoch data cannot erase ownership.
                    assertThrows(IllegalStateException::class.java) { s.validate(sh.copy(containerId = ContainerId.PRIMARY,epoch = s.epoch)) }
                    val pc = p.cacheIdentity(ph)
                    val foreign = pc.copy(containerId = ContainerId.SECONDARY)
                    assertNotEquals(pc,foreign)
                    assertThrows(IllegalStateException::class.java) { foreign.primaryName }
                    secondary.revoke(); p.checkValid()
                }
            }
        } finally { primary.revoke(); secondary.revoke() }
    }
    @Test fun `closed lease wipes key but permits only original epoch publication`() {
        val authority = SecondarySessionAuthority { 0 }; unlock(authority)
        val lease = checkNotNull(authority.operationOrNull())
        lease.close()
        assertArrayEquals(ByteArray(32), lease.key)
        assertEquals(5, lease.publish { 5 })
        assertThrows(IllegalStateException::class.java) { lease.checkValid() }
        assertThrows(IllegalStateException::class.java) { lease.commit { fail() } }
        assertThrows(IllegalStateException::class.java) { lease.fork() }
        authority.revoke(); unlock(authority)
        assertThrows(IllegalStateException::class.java) { lease.publish { fail() } }
    }
    @Test fun `record handles and bindings reject ABA and foreign container before action`() {
        val authority = SecondarySessionAuthority { 0 }; unlock(authority)
        val old = checkNotNull(authority.operationOrNull())
        val binding = checkNotNull(authority.bindingOrNull())
        val handle = old.recordHandle("index", 1)
        old.validate(handle)
        assertThrows(IllegalStateException::class.java) {
            old.validate(handle.copy(containerId = ContainerId.PRIMARY))
        }
        authority.revoke(); unlock(authority)
        val current = checkNotNull(authority.operationOrNull())
        assertNotEquals(old.epoch, current.epoch)
        assertNotEquals(old.operationId, current.operationId)
        assertFalse(binding.isCurrent)
        assertThrows(IllegalStateException::class.java) { current.validate(handle) }
        assertThrows(IllegalStateException::class.java) { binding.commit { fail() } }
        assertThrows(IllegalStateException::class.java) { old.commit { fail() } }
    }
    @Test fun `scopes cannot escalate or be mutated by issuer`() {
        val authority = SecondarySessionAuthority { 0 }; unlock(authority)
        val scopes = mutableSetOf(SecondaryScope.READ)
        val lease = checkNotNull(authority.operationOrNull(scopes))
        scopes.add(SecondaryScope.CREDENTIALS)
        assertThrows(IllegalStateException::class.java) { lease.requireScope(SecondaryScope.CREDENTIALS) }
        assertThrows(IllegalStateException::class.java) { lease.fork(setOf(SecondaryScope.WRITE)) }
        lease.fork(setOf(SecondaryScope.READ)).checkValid()
    }
    @Test fun `deadline checked on all admissions repeated background cannot extend`() {
        var now = 0L
        val authority = SecondarySessionAuthority { now }; unlock(authority)
        val lease = checkNotNull(authority.operationOrNull())
        val binding = checkNotNull(authority.bindingOrNull())
        authority.onBackgrounded(30_000); now = 29_000; authority.onBackgrounded(30_000)
        now = 30_000
        assertThrows(IllegalStateException::class.java) { lease.publish { fail() } }
        assertFalse(binding.isCurrent)
        assertNull(authority.operationOrNull())
        authority.onForegrounded(); assertNull(authority.bindingOrNull())
    }
    @Test fun `foreground before deadline preserves session screen off revokes immediately`() {
        var now = 0L
        val authority = SecondarySessionAuthority { now }; unlock(authority)
        val lease = checkNotNull(authority.operationOrNull())
        authority.onBackgrounded(100); now = 99; authority.onForegrounded(); now = 1000
        lease.checkValid(); authority.onScreenOff(); assertFalse(lease.isCurrent)
    }
    @Test fun `registry owns resources and unfinished jobs before reopening`() = runBlocking {
        val authority = SecondarySessionAuthority { 0 }; unlock(authority)
        val lease = checkNotNull(authority.operationOrNull())
        var closed = 0
        val resource = lease.own(AutoCloseable { closed++ })
        lease.ownForSession(resource)
        val release = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { withContext(NonCancellable) { release.await() } }
        }
        lease.own(job)
        authority.revoke()
        assertEquals(1, closed); assertTrue(job.isCancelled); assertFalse(authority.cleanupComplete)
        val rejected = ByteArray(32) { 8 }
        val rejectedAttempt = authority.beginAuthentication()
        assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(rejectedAttempt) }
        assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(rejectedAttempt) { fail() } }
        assertThrows(IllegalStateException::class.java) { authority.completeAuthentication(rejectedAttempt, rejected) }
        assertArrayEquals(ByteArray(32), rejected)
        release.complete(Unit); job.join(); assertTrue(authority.cleanupComplete)
        unlock(authority)
        assertThrows(IllegalStateException::class.java) { lease.own(AutoCloseable { closed++ }) }
        assertEquals(2, closed)
    }
    @Test fun `closed lease handoff survives until revoke and cleanup failures fail closed`() {
        val authority = SecondarySessionAuthority { 0 }; unlock(authority)
        val lease = checkNotNull(authority.operationOrNull())
        lease.close()
        var closes = 0
        val secondClose = CountDownLatch(1)
        lease.ownForSession(AutoCloseable { throw IllegalStateException("test") })
        lease.ownForSession(AutoCloseable { closes++; secondClose.countDown() })
        authority.revoke()
        assertTrue(secondClose.await(5, TimeUnit.SECONDS))
        assertEquals(1, closes); assertFalse(authority.cleanupComplete)
        val rejected = authority.beginAuthentication()
        assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(rejected) }
        assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(rejected) { fail() } }
    }
    @Test fun `attempts are one shot scoped and cancelled by lifecycle or newer attempt`() {
        val authority = SecondarySessionAuthority { 0 }
        val other = SecondarySessionAuthority { 0 }
        val old = authority.beginAuthentication()
        val current = authority.beginAuthentication()
        authority.cancelAuthentication(old)
        val staleKey = ByteArray(32) { 4 }
        assertFalse(authority.completeAuthentication(old, staleKey)); assertArrayEquals(ByteArray(32), staleKey)
        assertFalse(other.completeAuthentication(current, ByteArray(32)))
        assertTrue(authority.completeAuthentication(current, ByteArray(32)))
        assertFalse(authority.completeAuthentication(current, ByteArray(32)))
        authority.revoke()
        for (invalidate in listOf<(SecondaryAuthAttempt) -> Unit>(
            { authority.revoke() }, { authority.onBackgrounded(30_000) },
            { authority.onScreenOff() }, { authority.cancelAuthentication(it) }
        )) {
            val attempt = authority.beginAuthentication()
            invalidate(attempt)
            assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(attempt) }
            assertThrows(IllegalStateException::class.java) { authority.commitAuthentication(attempt) { fail() } }
            assertFalse(authority.completeAuthentication(attempt, ByteArray(32)))
            assertNull(authority.bindingOrNull())
        }
    }
    @Test fun `revoke waits for admitted short commit then denies later read or publication`() {
        val authority = SecondarySessionAuthority { 0 }; unlock(authority)
        val lease = checkNotNull(authority.operationOrNull())
        val admitted = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val revoked = CountDownLatch(1)
        val writer = thread {
            lease.commit { admitted.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS)) }
        }
        assertTrue(admitted.await(5, TimeUnit.SECONDS))
        val revoker = thread { authority.revoke(); revoked.countDown() }
        assertFalse(revoked.await(100, TimeUnit.MILLISECONDS))
        finish.countDown(); writer.join(5_000); revoker.join(5_000)
        assertEquals(0L, revoked.count)
        assertThrows(IllegalStateException::class.java) { lease.commit { lease.key.copyOf() } }
        assertThrows(IllegalStateException::class.java) { lease.publish { fail() } }
    }
    @Test fun `transferred master and original lease wipe without aliasing later session keys`() {
        val authority = SecondarySessionAuthority { 0 }
        val master = ByteArray(32) { 9 }; unlock(authority, master)
        val lease = checkNotNull(authority.operationOrNull())
        assertNotSame(master, lease.key)
        assertArrayEquals(master, lease.key)
        authority.revoke(); assertArrayEquals(ByteArray(32), master)
        val next = ByteArray(32) { 5 }; unlock(authority, next)
        assertArrayEquals(ByteArray(32), lease.key)
        assertArrayEquals(next, checkNotNull(authority.operationOrNull()).key)
        val lateJob = Job()
        assertThrows(IllegalStateException::class.java) { lease.own(lateJob) }
        assertTrue(lateJob.isCancelled)
        val invalid = ByteArray(31) { 7 }
        val invalidAttempt = authority.beginAuthentication()
        assertThrows(IllegalArgumentException::class.java) {
            authority.completeAuthentication(invalidAttempt, invalid)
        }
        assertArrayEquals(ByteArray(31), invalid)
        assertFalse(authority.completeAuthentication(invalidAttempt, ByteArray(32)))
    }

    @Test fun `session owned job outlives its producer lease and is cancelled by original epoch revocation`() = runBlocking {
        val authority = SecondarySessionAuthority { 0 }
        unlock(authority)
        val operation = checkNotNull(authority.operationOrNull())
        val entered = CompletableDeferred<Unit>()
        val job = operation.createSessionOwnedJob { attach ->
            launch(start = CoroutineStart.LAZY) {
                entered.complete(Unit)
                awaitCancellation()
            }.also(attach)
        }

        job.start()
        entered.await()
        operation.close()
        assertFalse(job.isCancelled)

        authority.revoke()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(authority.cleanupComplete)
    }
}
