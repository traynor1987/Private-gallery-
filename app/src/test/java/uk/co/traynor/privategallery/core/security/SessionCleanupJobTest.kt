package uk.co.traynor.privategallery.core.security

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SessionCleanupJobTest {
    @Test fun cancellationBeforeFirstDispatchStillRunsAndAcknowledgesOriginalCleanup() = runBlocking {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        val dispatcher = QueuedDispatcher()
        val cleanups = AtomicInteger()
        val job = operation.createSessionCleanupJob(dispatcher) { cleanups.incrementAndGet() }
        try {
            job.cancel() // Exact pre-dispatch race: no coroutine body has run yet.
            authority.revoke()
            assertFalse("Cancellation alone cannot acknowledge Native cleanup", job.isCompleted)
            assertFalse(authority.cleanupComplete)
            assertEquals(0, cleanups.get())
            dispatcher.drain()
            job.join()
            assertEquals(1, cleanups.get())
            await { authority.cleanupComplete }
        } finally {
            job.cancel()
            dispatcher.drain()
            operation.close()
            authority.revoke()
        }
    }

    @Test fun cleanupFailureKeepsFreshAuthenticationDenied() = runBlocking {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        val dispatcher = QueuedDispatcher()
        val job = operation.createSessionCleanupJob(dispatcher) { error("Synthetic cleanup failure") }
        job.cancel()
        authority.revoke()
        dispatcher.drain()
        job.join()
        assertFalse(authority.cleanupComplete)
        val rejected = ByteArray(32) { 7 }
        assertThrows(IllegalStateException::class.java) { authority.open(rejected) }
        assertArrayEquals(ByteArray(32), rejected)
        operation.close()
    }

    @Test fun alreadyCancelledLifecycleParentStillRunsOriginalCleanup() = runBlocking {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        val dispatcher = QueuedDispatcher()
        val parent = Job().also { it.cancel() }
        val cleanups = AtomicInteger()
        val job = operation.createSessionCleanupJob(dispatcher + parent) { cleanups.incrementAndGet() }
        try {
            assertFalse(job.isCompleted)
            dispatcher.drain()
            job.join()
            assertEquals(1, cleanups.get())
        } finally {
            operation.close()
            authority.revoke()
            dispatcher.drain()
            await { authority.cleanupComplete }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    @Suppress("DEPRECATION_ERROR") // Deliberately exercise the pinned runtime's original parent attachment.
    @Test fun rejectedFactoryStillStartsItsAlreadyAttachedCleanup() {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        val dispatcher = QueuedDispatcher()
        val parent = Job()
        val cleanups = AtomicInteger()
        val revokingParent = object : Job by parent {
            @Suppress("UNCHECKED_CAST")
            override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? =
                if (key == Job) this as E else null
            override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R =
                operation(initial, this)
            override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext =
                if (key == Job) EmptyCoroutineContext else this
            override fun attachChild(child: ChildJob): ChildHandle {
                // Constructor parent attachment occurs after reservation but
                // before the actual coroutine is handed to the original hook.
                operation.close()
                return parent.attachChild(child)
            }
        }
        try {
            assertThrows(IllegalStateException::class.java) {
                operation.createSessionCleanupJob(dispatcher + revokingParent) { cleanups.incrementAndGet() }
            }
            authority.revoke()
            dispatcher.drain()
            await { cleanups.get() == 1 && authority.cleanupComplete }
        } finally {
            parent.cancel()
            dispatcher.drain()
            authority.revoke()
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun drain() { while (true) (queue.poll() ?: return).run() }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) Thread.yield()
        assertTrue(condition())
    }
}
