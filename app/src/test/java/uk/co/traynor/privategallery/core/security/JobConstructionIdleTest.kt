package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class JobConstructionIdleTest {
    @Test fun primaryUnavailableJobKeepsItsOriginalCleanupWorkerIdle() {
        val invoked = CountDownLatch(1); val pool = pool(invoked); val authority = testPrimaryAuthority()
        replacePool(authority,pool); authority.open(ByteArray(32)); val op = authority.operationOrNull()!!
        try { exercise(invoked,pool,{ authority.revoke() },{ authority.cleanupComplete },{ factory -> op.createOwnedJob(factory) }) }
        finally { op.close(); authority.revoke() }
    }
    @Test fun hiddenUnavailableJobKeepsItsOriginalCleanupWorkerIdle() {
        val invoked = CountDownLatch(1); val pool = pool(invoked); val authority = testSecondaryAuthority { 0 }
        replacePool(authority,pool); assertTrue(authority.completeAuthentication(authority.beginAuthentication(),ByteArray(32)))
        val op = authority.operationOrNull()!!
        try { exercise(invoked,pool,{ authority.revoke() },{ authority.cleanupComplete },{ factory -> op.createOwnedJob(factory) }) }
        finally { op.close(); authority.revoke() }
    }
    @Test fun malformedReturnedJobIsCancelledUnderItsOriginalPreclaimedSlot() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32)); val op = authority.operationOrNull()!!
        lateinit var actual: Job
        try {
            assertThrows(IllegalStateException::class.java) { op.createOwnedJob { actual = Job(); actual } }
            val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while (!actual.isCancelled && System.nanoTime()<deadline) Thread.yield()
            assertTrue(actual.isCancelled)
        } finally { op.close(); authority.revoke() }
        awaitCleanup { authority.cleanupComplete }
    }
    @Test fun duplicateActualJobIdentityAcrossOriginalChildrenIsDeniedWithoutDoubleCancellation() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32)); val op = authority.operationOrNull()!!
        var cancelCalls = 0; val underlying = Job(); val actual = object : Job by underlying {
            override fun cancel(cause: java.util.concurrent.CancellationException?) { synchronized(this) { cancelCalls++ }; underlying.cancel(cause) }
        }
        try {
            assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("job","alias")) {
                val originalJob = original.createJob(0) { attach -> actual.also(attach) }
                create("alias",{ job: Job -> job.cancel() }) { originalJob.actualJob }
                originalJob
            } }
        } finally { op.close(); authority.revoke() }
        awaitCleanup { authority.cleanupComplete }; assertEquals(1,cancelCalls)
    }
    @Test fun factoryFailureBeforeActualJobDoesNotLeaveACompletionWaitObligation() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32)); val op = authority.operationOrNull()!!
        try { assertThrows(java.io.IOException::class.java) { op.createOwnedJob<Job> { throw java.io.IOException("synthetic before construction") } } }
        finally { op.close(); authority.revoke() }
        awaitCleanup { authority.cleanupComplete }
    }
    private fun awaitCleanup(complete: () -> Boolean) {
        val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while (!complete() && System.nanoTime()<deadline) Thread.yield()
        assertTrue(complete())
    }
    private fun exercise(invoked: CountDownLatch,pool: ReleasePool,revoke: () -> Unit,complete: () -> Boolean,
        create: (((Job) -> Unit) -> Job) -> Job) {
        val entered = CountDownLatch(1); val allow = CountDownLatch(1); val done = CountDownLatch(1); val cancelled = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val constructor = thread(isDaemon=true) {
            try {
                assertThrows(IllegalStateException::class.java) { create { attach ->
                    entered.countDown(); check(allow.await(5,TimeUnit.SECONDS))
                    Job().also { attach(it); it.invokeOnCompletion { cancelled.countDown() } }
                } }
            } catch (problem: Throwable) { failure.set(problem) } finally { done.countDown() }
        }
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS)); revoke()
            assertFalse("no cleanup invocation may wait for an unavailable native Job",invoked.await(200,TimeUnit.MILLISECONDS))
            assertEquals(1,pool.occupied); assertFalse(complete())
        } finally { allow.countDown(); assertTrue(done.await(5,TimeUnit.SECONDS)); constructor.interrupt() }
        failure.get()?.let { throw AssertionError("constructor failed",it) }
        assertTrue(invoked.await(5,TimeUnit.SECONDS)); assertTrue(cancelled.await(5,TimeUnit.SECONDS))
        val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while (!complete() && System.nanoTime()<deadline) Thread.yield()
        assertTrue(complete()); assertEquals(0,pool.occupied)
    }
    private fun pool(invoked: CountDownLatch) = ReleasePool(16,allocateInvocation = { action,returned ->
        ReleaseInvocation({ invoked.countDown(); action() },returned)
    })
    private fun replacePool(authority: Any,pool: ReleasePool) {
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible=true }.set(authority,pool)
    }
}
