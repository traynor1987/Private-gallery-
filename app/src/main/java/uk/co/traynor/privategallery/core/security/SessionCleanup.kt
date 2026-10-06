package uk.co.traynor.privategallery.core.security

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-owned legacy cleanup dispatcher.
 *
 * This only moves transitional resource cancellation/close callbacks outside
 * authority gates. It deliberately does not claim the fixed per-slot capacity
 * required by the complete Task 3 migration.
 */
internal object SessionCleanup {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "private-gallery-session-cleanup").apply { isDaemon = true }
    }

    fun dispatch(action: () -> Unit) {
        executor.execute(action)
    }
}
