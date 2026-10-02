package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real reserved-worker/Job boundary, independently of the pending authority migration. */
class ReservedJobReleaseTest {
    @Test fun `partial job cancels before its original constructor returns`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val attached = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val returned = CountDownLatch(1)
        thread(isDaemon = true) {
            try {
                assertThrows(IllegalStateException::class.java) {
                    reservation.construct {
                        attach(0, ReservedJobRelease()).create { own ->
                            Job().also { job ->
                                own(job); job.invokeOnCompletion { cancelled.countDown() }
                                attached.countDown(); check(finish.await(5, TimeUnit.SECONDS))
                            }
                        }
                    }
                }
            } finally { returned.countDown() }
        }
        try {
            assertTrue(attached.await(5, TimeUnit.SECONDS))
            reservation.release()
            assertTrue(cancelled.await(1, TimeUnit.SECONDS))
            assertEquals(1, pool.occupied)
            assertFalse(reservation.successful)
        } finally { finish.countDown() }
        assertTrue(returned.await(5, TimeUnit.SECONDS))
        waitFor { reservation.successful && pool.occupied == 0 }
    }

    @Test fun `blocked cancellation return cannot recycle after job completion`() {
        val pool = ReleasePool(2)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 2))
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val independent = CountDownLatch(1)
        lateinit var actual: Job
        reservation.construct {
            attach(0, ReservedJobRelease()).create { own -> Job().also {
                actual = it; own(it)
                it.invokeOnCompletion { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }
            } }
            attach(1, AutoCloseable { independent.countDown() })
        }
        reservation.release()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(actual.isCompleted)
            assertTrue(independent.await(1, TimeUnit.SECONDS))
            waitFor { pool.occupied == 1 }
            assertFalse(reservation.successful)
            assertEquals(1, pool.occupied)
        } finally { finish.countDown() }
        waitFor { reservation.successful && pool.occupied == 0 }
    }

    @Test fun `factory failure keeps attached job finalization obligation`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        assertThrows(IOException::class.java) {
            reservation.construct {
                attach(0, ReservedJobRelease()).create { own ->
                    val job = Job(); own(job)
                    job.invokeOnCompletion { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }
                    throw IOException("partial factory")
                }
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertFalse(reservation.successful)
            assertEquals(1, pool.occupied)
        } finally { finish.countDown() }
        waitFor { reservation.successful && pool.occupied == 0 }
    }

    @Test fun `failed coroutine finalization permanently retains its original slot`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val started = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> failed.countDown() })
        lateinit var actual: Job
        try {
            reservation.construct {
                attach(0, ReservedJobRelease()).create { own -> scope.launch(start = CoroutineStart.LAZY) {
                    try { started.countDown(); awaitCancellation() }
                    finally { withContext(NonCancellable) { throw IOException("teardown failed") } }
                }.also { actual = it; own(it) } }
            }
            actual.start(); assertTrue(started.await(5, TimeUnit.SECONDS))
            reservation.release(); assertTrue(failed.await(5, TimeUnit.SECONDS))
            waitFor { reservation.failed }
            assertFalse(reservation.successful)
            assertEquals(1, pool.occupied)
            assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
        } finally { scope.cancel() }
    }

    @Test fun `inline completion cannot erase a subsequent hook installation failure`() {
        val pool = ReleasePool(1)
        val reservation = ReleaseReservation(pool.reserveAll(Any(), 1))
        val completed = Job().also { it.complete() }
        val actual = object : Job by completed {
            override fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle {
                handler(null); throw IOException("hook failed")
            }
        }
        assertThrows(IOException::class.java) {
            reservation.construct { attach(0, ReservedJobRelease()).create { own -> actual.also(own) } }
        }
        waitFor { reservation.failed }
        assertFalse(reservation.successful)
        assertEquals(1, pool.occupied)
    }

    private fun waitFor(check: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < end) Thread.yield()
        assertTrue("original reserved release did not settle", check())
    }
}
