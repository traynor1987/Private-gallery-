package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class LocalWorkerLifetimeTest {
    @Test fun cancellationBeforeDispatchDoesNotPublishAnUnstoppableWorker() = runBlocking {
        val queue = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        }
        var published = false
        var entered = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            withLocalWorkerLifetime(dispatcher, { published = true }, 1) {
                entered = true; it.complete(Unit)
            }
        }
        task.cancel()
        while (true) (queue.poll() ?: break).run()
        task.join()
        assertFalse(published)
        assertFalse(entered)
        assertTrue(task.isCancelled)
    }

    @Test fun unconfirmedWorkerStopBlocksSuccessfulReturn() = runBlocking {
        var published: CompletableDeferred<Unit>? = null
        val result = runCatching {
            withLocalWorkerLifetime(Dispatchers.Unconfined, { published = it }, 1) { "output" }
        }
        assertTrue(result.exceptionOrNull() is LocalWorkerStopTimeout)
        assertFalse(published!!.isCompleted)
        published!!.complete(Unit)
        Unit
    }

    @Test fun confirmedDeathAllowsReturn() = runBlocking {
        assertEquals("output", withLocalWorkerLifetime(Dispatchers.Unconfined, {}, 100) {
            it.complete(Unit); "output"
        })
    }
}
