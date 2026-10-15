package uk.co.traynor.privategallery.core.browser.v2

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

class BrowserUploadLeaseTest {
    @Test fun clearingUploadCancelsItsUnstartedOriginalTimerAndRetiresCopies() = runBlocking {
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull())
        val closed = CountDownLatch(1)
        val copies = operation.createSessionOwned(OwnedResourceManifest.io("copies")) {
            attach("copies", AutoCloseable { closed.countDown() })
        }
        val timer = operation.createSessionOwnedJob { attach ->
            launch(start = CoroutineStart.LAZY) { delay(300_000) }.also(attach)
        }
        try {
            val lease = BrowserUploadLease(copies, timer)
            lease.close()
            assertTrue("Original timer must retire when its files are cleared", timer.isCancelled)
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            assertTrue(copies.retirement.await(5, TimeUnit.SECONDS))
            timer.join()
            assertFalse("A cleared lease must not start another timer", lease.startCleanupTimer())
        } finally {
            timer.cancelAndJoin()
            copies.close()
            operation.close()
            authority.revoke()
        }
    }
}
