package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Proposed production API, deliberately absent until root captures RED.
 *
 * OriginalBiometricCoordinator(post: (Runnable)->Boolean, isMain: ()->Boolean)
 *   .begin(guard, expectedCrypto: Any, backend: OriginalBiometricBackend)
 *       : OriginalBiometricLifecycle
 * Lifecycle: close(), retirement: RetirementAcknowledgement, releaseFailed: Boolean,
 *   takeSuccessReceipt(): Boolean (once, only AFTER full retirement).
 * Backend.acquire(events, attach): Unit immediately attaches each actual Host BEFORE
 *   any later throwing step. Host has cancel() and destroy(); destroy requests teardown,
 *   NOT acknowledgement. Events.success(Any), error(), destroyed() are called directly
 *   on Main. destroyed() is invoked from final onDestroy; its posted barrier observes
 *   actual lifecycle callback return. No future/stage is an accepted public acknowledgement.
 *
 * Fixed original manifest: four IO children, all reserved atomically before acquire:
 * request/callback bridge, cancel invocation, Main callback-return barrier, host lifecycle.
 * Provider, worker, inputs and result keys are ADDITIONAL caller-owned obligations.
 * Every actual AndroidX request uses a FRESH Activity ViewModel. A child Fragment in
 * MainActivity is insufficient on biometric1.1.0. Tests model distinct host identities.
 */
class OriginalBiometricLifecycleTest {
    @Test fun unverifiedBackendCannotAcquireOrConsumeCapacityAndPinAuthorityRemainsCurrent() = isolated { authority, guard, pool ->
        val main = FakeMain()
        val backend = Backend(supportsAcknowledgedRetirement = false)
        var admitted: OriginalBiometricLifecycle? = null
        var denied: IllegalStateException? = null
        try { admitted = OriginalBiometricCoordinator(main::post) { true }.begin(guard, Any(), backend) }
        catch (failure: IllegalStateException) { denied = failure }
        finally {
            // The pre-fix path really acquires a controlled host; retire it normally so
            // the intentional RED cannot strand shared test cleanup workers.
            admitted?.let { request ->
                request.close()
                main.drainUntil { backend.host.destroyCalls > 0 }
                backend.events.destroyed()
                main.drainUntil { request.retirement.isComplete }
            }
        }
        assertNotNull("unsupported app graph retirement must deny before acquisition", denied)
        assertEquals(0, backend.acquisitions)
        assertEquals(0, pool.occupied)
        guard.check()
        assertNotNull(authority.bindingOrNull()?.also { it.close() })
    }

    @Test fun terminalAndDestroyReturnBarriersGateReplacementAndOldEventsStayOriginal() = isolated { _, guard, pool ->
        val main = FakeMain()
        val coordinator = OriginalBiometricCoordinator(main::post) { true }
        val firstBackend = Backend()
        val firstCrypto = Any()
        val first = coordinator.begin(guard, firstCrypto, firstBackend)
        assertEquals(4, pool.occupied)
        val denied = Backend()
        assertThrows(IllegalStateException::class.java) { coordinator.begin(guard, Any(), denied) }
        assertEquals(0, denied.acquisitions)

        // The direct callback has returned but its event-loop return barrier has not run.
        firstBackend.events.success(firstCrypto)
        assertFalse(first.retirement.isComplete)
        assertFalse(first.takeSuccessReceipt())
        assertThrows(IllegalStateException::class.java) { coordinator.begin(guard, Any(), denied) }
        assertFalse(first.runAuthenticated { fail("inside callback return window") })
        main.drainUntil { first.readyForAuthenticatedUse }
        assertEquals(0, firstBackend.host.destroyCalls)
        assertTrue(first.runAuthenticated { assertSame(firstCrypto, it) })
        assertFalse(first.runAuthenticated { fail("duplicate Cipher use") })
        main.drainUntil { firstBackend.host.destroyCalls == 1 }
        assertFalse(first.retirement.isComplete) // finish/remove request is not destruction.
        firstBackend.events.destroyed()
        assertFalse(first.retirement.isComplete) // onDestroy has not crossed its return barrier.
        main.drainUntil { first.retirement.isComplete }
        assertTrue(first.takeSuccessReceipt())
        assertFalse(first.takeSuccessReceipt())
        assertEquals(0, pool.occupied)

        val nextBackend = Backend()
        val nextCrypto = Any()
        val next = coordinator.begin(guard, nextCrypto, nextBackend)
        assertNotSame(firstBackend.host, nextBackend.host)
        assertEquals(4, pool.occupied)
        // These are saved OLD callbacks, deliberately delivered after replacement starts.
        firstBackend.events.error()
        firstBackend.events.success(firstCrypto)
        first.close()
        main.drainAvailable()
        assertEquals(0, nextBackend.host.cancelCalls)
        assertEquals(0, nextBackend.host.destroyCalls)
        assertFalse(next.takeSuccessReceipt())
        nextBackend.events.success(firstCrypto) // even current host must match exact Cipher.
        assertFalse(next.takeSuccessReceipt())
        next.close()
        main.drainUntil { nextBackend.host.destroyCalls == 1 }
        nextBackend.events.destroyed()
        main.drainUntil { next.retirement.isComplete }
        assertFalse(next.takeSuccessReceipt())
        assertEquals(0, pool.occupied)
    }

    @Test fun heldCancelDoesNotSerializeTeardownAndActualCancelReturnRemainsCharged() = isolated { authority, guard, pool ->
        val main = FakeMain()
        val backend = Backend()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        backend.host.onCancel = { entered.countDown(); release.await() }
        val lifecycle = OriginalBiometricCoordinator(main::post) { true }.begin(guard, Any(), backend)
        lifecycle.close()
        // Backend contract queues cancel before teardown, each on its own prefunded child.
        val cancel = main.take()
        val runner = Thread { cancel.run() }
        try {
            runner.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            main.drainUntil { backend.host.destroyCalls == 1 }
            backend.events.destroyed()
            main.drainAvailable()
            authority.revoke()
            assertFalse(lifecycle.retirement.isComplete)
            assertFalse(authority.cleanupComplete)
            assertTrue(pool.occupied > 0)
            val candidate = ByteArray(32) { 7 }
            assertThrows(IllegalStateException::class.java) { authority.open(candidate) }
            assertArrayEquals(ByteArray(32), candidate)
        } finally { release.countDown(); runner.join(5000) }
        assertFalse(runner.isAlive)
        main.drainUntil { lifecycle.retirement.isComplete }
        assertTrue(authority.cleanupComplete)
        assertEquals(0, pool.occupied)
        assertEquals(1, backend.host.cancelCalls)
        assertEquals(1, backend.host.destroyCalls)
    }

    @Test fun acquiredHostOnThrowingConstructionRetainsOriginalUntilDestroyReturns() = isolated { authority, guard, pool ->
        val main = FakeMain()
        val backend = Backend().apply { failAfterAttach = IOException("after exact host acquisition") }
        val failure = assertThrows(IOException::class.java) {
            OriginalBiometricCoordinator(main::post) { true }.begin(guard, Any(), backend)
        }
        assertSame(backend.failAfterAttach, failure)
        main.drainUntil { backend.host.destroyCalls == 1 }
        authority.revoke()
        assertFalse(authority.cleanupComplete)
        assertTrue(pool.occupied > 0)
        backend.events.destroyed()
        assertFalse(authority.cleanupComplete)
        main.drainUntil { authority.cleanupComplete }
        assertEquals(0, pool.occupied)
        assertEquals(1, backend.host.cancelCalls)
        assertEquals(1, backend.host.destroyCalls)
    }

    @Test fun constructionFailureCannotBypassOriginalHostRetirement() = isolated { _, guard, pool ->
        val main = FakeMain()
        val coordinator = OriginalBiometricCoordinator(main::post) { true }
        val first = Backend().apply { failAfterAttach = IOException("construction") }
        assertThrows(IOException::class.java) { coordinator.begin(guard, Any(), first) }
        val replacement = Backend()
        assertThrows(IllegalStateException::class.java) { coordinator.begin(guard, Any(), replacement) }
        assertEquals(0, replacement.acquisitions)
        main.drainUntil { first.host.destroyCalls == 1 }
        first.events.destroyed()
        main.drainUntil { pool.occupied == 0 }
    }

    @Test fun spontaneousHostDestructionRetiresOriginalWithoutAuthenticating() = isolated { _, guard, pool ->
        val main = FakeMain()
        val backend = Backend()
        val lifecycle = OriginalBiometricCoordinator(main::post) { true }.begin(guard, Any(), backend)
        backend.events.destroyed()
        main.drainUntil { lifecycle.retirement.isComplete }
        assertFalse(lifecycle.takeSuccessReceipt())
        assertEquals(0, pool.occupied)
    }

    @Test fun retiredSuccessCannotAuthorizeAfterOriginalEpochRevocation() = isolated { authority, guard, _ ->
        val main = FakeMain()
        val backend = Backend()
        val crypto = Any()
        val lifecycle = OriginalBiometricCoordinator(main::post) { true }.begin(guard, crypto, backend)
        backend.events.success(crypto)
        main.drainUntil { lifecycle.readyForAuthenticatedUse }
        assertTrue(lifecycle.runAuthenticated { assertSame(crypto, it) })
        main.drainUntil { backend.host.destroyCalls == 1 }
        backend.events.destroyed()
        main.drainUntil { lifecycle.retirement.isComplete }
        authority.revoke()
        assertFalse(lifecycle.takeSuccessReceipt())
    }

    @Test fun cancellationDuringAuthenticatedUseRetainsBorrowUntilNativeReturns() = isolated { authority, guard, pool ->
        val main = FakeMain()
        val backend = Backend()
        val crypto = Any()
        val lifecycle = OriginalBiometricCoordinator(main::post) { true }.begin(guard, crypto, backend)
        backend.events.success(crypto)
        main.drainUntil { lifecycle.readyForAuthenticatedUse }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var accepted = true
        val worker = Thread {
            accepted = lifecycle.runAuthenticated { value ->
                assertSame(crypto, value)
                entered.countDown()
                release.await()
            }
        }
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            lifecycle.close()
            main.drainUntil { backend.host.cancelCalls == 1 }
            assertEquals(0, backend.host.destroyCalls)
            authority.revoke()
            assertFalse(authority.cleanupComplete)
            assertTrue(pool.occupied > 0)
        } finally { release.countDown(); worker.join(5000) }
        assertFalse(worker.isAlive)
        assertFalse(accepted)
        main.drainUntil { backend.host.destroyCalls == 1 }
        backend.events.destroyed()
        main.drainUntil { lifecycle.retirement.isComplete }
        assertFalse(lifecycle.takeSuccessReceipt())
        assertFalse(lifecycle.runAuthenticated { fail("stale Cipher escaped") })
        assertEquals(0, pool.occupied)
    }

    @Test fun inlinePostingCannotStandInForCallbackReturn() = isolated { _, guard, _ ->
        val backend = Backend()
        val crypto = Any()
        val lifecycle = OriginalBiometricCoordinator({ it.run(); true }) { true }.begin(guard, crypto, backend)
        backend.events.success(crypto)
        assertFalse(lifecycle.readyForAuthenticatedUse)
        assertFalse(lifecycle.runAuthenticated { fail("SDK callback has not returned") })
        lifecycle.close()
    }

    @Test fun pendingLaunchCancelledBeforeActivityCreationRetainsCapacityUntilActualDestroyReturn() = isolated { _, guard, pool ->
        val main = FakeMain()
        var cancelled = false
        var destroyRequested = false
        var attached = false
        lateinit var events: OriginalBiometricBackend.Events
        val backend = object : OriginalBiometricBackend {
            override val supportsAcknowledgedRetirement = true
            override fun acquire(value: OriginalBiometricBackend.Events, attach: (OriginalBiometricBackend.Host) -> Unit) {
                events = value
                attach(object : OriginalBiometricBackend.Host {
                    override fun cancel() { cancelled = true }
                    override fun destroy() { destroyRequested = true }
                })
                // startActivity returns while the actual Activity has not yet run onCreate.
            }
        }
        val coordinator = OriginalBiometricCoordinator(main::post) { true }
        val lifecycle = coordinator.begin(guard, Any(), backend)
        lifecycle.close()
        main.drainUntil { cancelled && destroyRequested }
        assertFalse(attached)
        assertFalse(lifecycle.retirement.isComplete)
        assertTrue(pool.occupied > 0)
        assertThrows(IllegalStateException::class.java) { coordinator.begin(guard, Any(), Backend()) }
        attached = true // A late onCreate claims only the still-owned original and finishes it.
        assertTrue(attached)
        events.destroyed()
        assertFalse(lifecycle.retirement.isComplete)
        main.drainUntil { lifecycle.retirement.isComplete }
        assertFalse(lifecycle.takeSuccessReceipt())
        assertEquals(0, pool.occupied)
    }

    @Test fun lockedPrimaryOwnedKeyAdmitsOnlyAfterOriginalPromptAndWorkerRetire() = isolated { authority, _, _ ->
        val attempt = authority.beginAuthentication()
        val main = FakeMain()
        val backend = Backend()
        val crypto = Any()
        val lifecycle = OriginalBiometricCoordinator(main::post) { true }.begin(
            BiometricOriginalOwner.PrimaryPreparation(authority, attempt), crypto, backend)
        backend.events.success(crypto)
        main.drainUntil { lifecycle.readyForAuthenticatedUse }
        val key = ByteArray(32) { 19 }
        val handoff = authority.prepareThreadAuthentication(attempt) {
            check(lifecycle.runAuthenticated { assertSame(crypto, it) })
            assertThrows(IllegalStateException::class.java) {
                authority.createAttemptOwned(attempt, OwnedResourceManifest.io("new-native")) {
                    fail("Fresh factory entered while prompt retirement was pending")
                    AutoCloseable {}
                }
            }
            key
        }
        assertNull(authority.bindingOrNull())
        main.drainUntil { backend.host.destroyCalls == 1 }
        assertFalse(lifecycle.retirement.isComplete)
        backend.events.destroyed()
        main.drainUntil { lifecycle.retirement.isComplete }
        assertTrue(lifecycle.takeSuccessReceipt())
        assertTrue(authority.completeAuthentication(attempt, handoff))
        assertNotNull(authority.bindingOrNull())
    }

    @Test fun mainMayRunQueuedCleanupBeforeBackgroundPostReturns() = isolated { _, guard, pool ->
        val mainThread = Thread.currentThread()
        val queue = LinkedBlockingQueue<Pair<Runnable, CountDownLatch?>>()
        val coordinator = OriginalBiometricCoordinator({ action ->
            val returned = if (Thread.currentThread() === mainThread) null else CountDownLatch(1)
            queue.add(action to returned)
            // Force the real Main action to execute before the posting IO worker returns.
            returned?.let { check(it.await(5, TimeUnit.SECONDS)) }
            true
        }) { Thread.currentThread() === mainThread }
        fun drainUntil(done: () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!done()) {
                check(System.nanoTime() < deadline)
                queue.poll(10, TimeUnit.MILLISECONDS)?.let { (action, returned) ->
                    try { action.run() } finally { returned?.countDown() }
                }
            }
        }
        val backend = Backend()
        val lifecycle = coordinator.begin(guard, Any(), backend)
        lifecycle.close()
        drainUntil { backend.host.cancelCalls == 1 && backend.host.destroyCalls == 1 }
        backend.events.destroyed()
        drainUntil { lifecycle.retirement.isComplete }
        assertFalse(lifecycle.releaseFailed)
        assertEquals(0, pool.occupied)
    }

    @Test fun revokedOriginalCannotAcquireLatePlatformPromptBeforeCancelDispatchRuns() = isolated { authority, guard, _ ->
        val main = FakeMain()
        val backend = Backend()
        val lifecycle = OriginalBiometricCoordinator(main::post) { true }.begin(guard, Any(), backend)
        assertTrue(backend.events.canAcquire())
        authority.revoke()
        // Deliberately do not drain Main cancellation. Late onCreate consults the
        // original bridge's authority, not just the eventual cancel flag.
        assertFalse(backend.events.canAcquire())
        assertEquals(0, backend.host.cancelCalls)
        main.drainUntil { backend.host.destroyCalls == 1 }
        backend.events.destroyed()
        main.drainUntil { lifecycle.retirement.isComplete }
        assertFalse(backend.events.canAcquire())
    }

    private class Backend(override val supportsAcknowledgedRetirement: Boolean = true) : OriginalBiometricBackend {
        val host = Host()
        lateinit var events: OriginalBiometricBackend.Events
        var acquisitions = 0
        var failAfterAttach: Throwable? = null
        override fun acquire(events: OriginalBiometricBackend.Events, attach: (OriginalBiometricBackend.Host) -> Unit) {
            acquisitions++
            this.events = events
            attach(host)
            failAfterAttach?.let { throw it }
        }
    }
    private class Host : OriginalBiometricBackend.Host {
        @Volatile var cancelCalls = 0
        @Volatile var destroyCalls = 0
        var onCancel: () -> Unit = {}
        override fun cancel() { cancelCalls++; onCancel() }
        override fun destroy() { destroyCalls++ }
    }
    private class FakeMain {
        private val queue = LinkedBlockingQueue<Runnable>()
        fun post(action: Runnable): Boolean = queue.add(action)
        fun take(): Runnable = checkNotNull(queue.poll(5, TimeUnit.SECONDS)) { "missing independently dispatched Main invocation" }
        fun drainAvailable() { while (true) (queue.poll() ?: return).run() }
        fun drainUntil(done: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!done()) {
                check(System.nanoTime() < end) { "original lifecycle did not reach expected state" }
                queue.poll(10, TimeUnit.MILLISECONDS)?.run()
            }
        }
    }
    private fun await(done: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done()) { check(System.nanoTime() < end); Thread.yield() }
    }
    private fun isolated(body: (PrimarySessionAuthority, ScopedIoGuard, ReleasePool) -> Unit) {
        val authority = testPrimaryAuthority()
        val pool = ReleasePool(16)
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(PrimaryScope.entries.toSet()))
        try { body(authority, ScopedIoGuard(operation, PrimaryScope.CREDENTIALS), pool) }
        finally { operation.close(); authority.revoke() }
    }
}
