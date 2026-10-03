package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReservedJobOwnershipTest {
    @Test fun `closed original operation denies before lazy job creation`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        operation.close()
        val created = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            assertThrows(IllegalStateException::class.java) {
                operation.createOwnedJob { attach ->
                    created.incrementAndGet()
                    scope.launch(start = CoroutineStart.LAZY) { awaitCancellation() }.also(attach)
                }
            }
            assertEquals(0, created.get())
        } finally { scope.cancel(); authority.revoke() }
    }

    @Test fun `job completion cannot acknowledge a blocked cancel invocation`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val entered = CountDownLatch(1)
        val returnHandler = CountDownLatch(1)
        val handlerReturned = CountDownLatch(1)
        val independent = CountDownLatch(1)
        val job = operation.createOwnedJob { attach ->
            Job().also { attach(it); it.invokeOnCompletion {
                entered.countDown()
                check(returnHandler.await(5, TimeUnit.SECONDS))
                handlerReturned.countDown()
            } }
        }
        operation.createOwned { attach -> AutoCloseable { independent.countDown() }.also(attach) }
        authority.revoke()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(job.isCompleted)
            assertTrue("a blocked cancellation callback cannot serialize the independent transport", independent.await(1, TimeUnit.SECONDS))
            assertFalse(authority.cleanupComplete)
            val key = ByteArray(32) { 7 }
            assertThrows(IllegalStateException::class.java) { authority.open(key) }
            assertTrue(key.all { it == 0.toByte() })
        } finally { returnHandler.countDown() }
        assertTrue(handlerReturned.await(5, TimeUnit.SECONDS))
        waitFor { authority.cleanupComplete }
    }

    @Test fun `completed-before-hook lazy job is accounted before any start or exposure`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val ran = AtomicInteger()
        val parent = Job().also { it.cancel() }
        val scope = CoroutineScope(parent + Dispatchers.Default)
        val job = operation.createOwnedJob { attach -> scope.launch(start = CoroutineStart.LAZY) { ran.incrementAndGet() }.also(attach) }
        assertTrue(job.isCompleted)
        assertEquals(0, ran.get())
        operation.close(); authority.revoke()
        waitFor { authority.cleanupComplete }
    }

    @Test fun `attached partial job cancels while its original factory is blocked`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val attached = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val finishFactory = CountDownLatch(1)
        val returned = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    operation.createOwnedJob { attach ->
                        val actual = Job()
                        attach(actual)
                        actual.invokeOnCompletion { cancelled.countDown() }
                        attached.countDown()
                        check(finishFactory.await(5, TimeUnit.SECONDS))
                        actual
                    }
                }
            } finally { returned.countDown() }
        }
        try {
            assertTrue(attached.await(5, TimeUnit.SECONDS))
            authority.revoke()
            assertTrue("partial actual Job cancellation cannot wait for factory return", cancelled.await(1, TimeUnit.SECONDS))
            assertFalse("original Job constructor remains unfinished", authority.cleanupComplete)
        } finally { finishFactory.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS)) }
        waitFor { authority.cleanupComplete }
    }

    @Test fun `throw after actual job attachment retains cancellation and finalization`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val finalizing = CountDownLatch(1)
        val finishFinalization = CountDownLatch(1)
        lateinit var actual: Job
        assertThrows(java.io.IOException::class.java) {
            operation.createOwnedJob<Job> { attach ->
                actual = Job()
                attach(actual)
                actual.invokeOnCompletion { finalizing.countDown(); check(finishFinalization.await(5, TimeUnit.SECONDS)) }
                throw java.io.IOException("factory failed after actual child")
            }
        }
        try {
            authority.revoke()
            assertTrue("known partial Job cannot be acknowledged as empty", finalizing.await(1, TimeUnit.SECONDS))
            assertFalse(authority.cleanupComplete)
        } finally { finishFinalization.countDown(); actual.cancel() }
        waitFor { authority.cleanupComplete }
    }

    @Test fun `actual lazy coroutine finalization failure never acknowledges native teardown`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val started = CountDownLatch(1)
        val finalizing = CountDownLatch(1)
        val failureObserved = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> failureObserved.countDown() })
        val actual = operation.createOwnedJob { attach -> scope.launch(start = CoroutineStart.LAZY) {
            try { started.countDown(); awaitCancellation() }
            finally { withContext(NonCancellable) { finalizing.countDown(); throw java.io.IOException("native finalization failed") } }
        }.also(attach) }
        val original = operation.reservations.single()
        actual.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        authority.revoke()
        assertTrue(finalizing.await(5, TimeUnit.SECONDS))
        assertTrue(failureObserved.await(5, TimeUnit.SECONDS))
        waitFor { actual.isCompleted }
        // Allow the actual cancellation invocation to return, so this is failed finalization,
        // not an accidental still-running close. Wait on the token's own real failure state.
        waitFor { original.failed || original.successful }
        assertFalse("failed NonCancellable native teardown is never successful cleanup", authority.cleanupComplete)
        val rejected = ByteArray(32) { 8 }
        assertThrows(IllegalStateException::class.java) { authority.open(rejected) }
        assertTrue(rejected.all { it == 0.toByte() })
        scope.cancel()
    }

    @Test fun `inline completion followed by hook installation failure is not positive acknowledgement`() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val completed = Job().also { it.complete() }
        lateinit var original: ReleaseReservation
        val actual = object : Job by completed {
            override fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle {
                handler(null)
                throw java.io.IOException("hook installation failed after inline completion")
            }
        }
        assertThrows(java.io.IOException::class.java) {
            operation.createOwnedJob { attach ->
                original = operation.reservations.single()
                actual.also(attach)
            }
        }
        authority.revoke()
        waitFor { original.failed || original.successful }
        assertFalse("an inline completion cannot erase failed hook installation", authority.cleanupComplete)
        val rejected = ByteArray(32) { 6 }
        assertThrows(IllegalStateException::class.java) { authority.open(rejected) }
        assertTrue(rejected.all { it == 0.toByte() })
    }

    private fun waitFor(check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < end) Thread.yield()
        assertTrue(check())
    }
}
