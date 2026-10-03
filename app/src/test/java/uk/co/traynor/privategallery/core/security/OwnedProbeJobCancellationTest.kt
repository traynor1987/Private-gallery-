package uk.co.traynor.privategallery.core.security

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.v2.startOwnedBrowserProbe
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OwnedProbeJobCancellationTest {
    @Test fun normalJobCancellationUnblocksExactTransportBeforeActualProducerCompletion() = isolated { authority, owner ->
        val operation=owner.fork(owner.scopes);val key=operation.key
        val entered=CountDownLatch(1);val disconnected=CountDownLatch(1);val closed=CountDownLatch(1)
        val returned=CountDownLatch(1);val allowReturn=CountDownLatch(1);val closeCalls=AtomicInteger()
        val connection=object : HttpURLConnection(URL("https://invalid.example/")) {
            override fun connect()=Unit
            override fun usingProxy()=false
            override fun disconnect() { disconnected.countDown() }
            override fun getInputStream()=object : InputStream() {
                override fun read(): Int { entered.countDown();check(closed.await(10,TimeUnit.SECONDS));return -1 }
                override fun close() { closeCalls.incrementAndGet();closed.countDown() }
            }
        }
        val guard=ScopedIoGuard(operation,PrimaryScope.BROWSER_UPLOAD_EGRESS)
        val root=owner.createOwnedJob<Job> { attach -> Job().also(attach) }
        val job=operation.createOwnedJob<Job> { attach -> CoroutineScope(root+Dispatchers.IO).launch(start=CoroutineStart.LAZY) {
            try { guard.connectedOwnedInput({ connection }).use { it.stream.read() } }
            catch (failure: IllegalStateException) { check(!currentCoroutineContext().isActive) }
            finally { returned.countDown();check(allowReturn.await(10,TimeUnit.SECONDS)) }
        }.also(attach) }
        try {
            startOwnedBrowserProbe(operation,job);assertTrue(entered.await(5,TimeUnit.SECONDS));job.cancel()
            assertTrue("cancellation must dispatch transport before producer completion",disconnected.await(2,TimeUnit.SECONDS))
            assertTrue(closed.await(5,TimeUnit.SECONDS));assertTrue(returned.await(5,TimeUnit.SECONDS))
            assertFalse(job.isCompleted);assertFalse(authority.cleanupComplete);assertArrayEquals(ByteArray(32),key)
            val denied=ByteArray(32) { 7 };assertThrows(IllegalStateException::class.java) { authority.open(denied) };assertArrayEquals(ByteArray(32),denied)
            assertEquals(1,closeCalls.get())
        } finally {
            operation.close();closed.countDown();allowReturn.countDown()
            runBlocking { withTimeout(5_000) { job.join() } };root.cancel()
        }
    }

    @Test fun cancellationBeforeLazyStartNeverRunsProducerAndClosesExactLease() = isolated { _, owner ->
        val operation=owner.fork(owner.scopes);val key=operation.key;val calls=AtomicInteger()
        val job=operation.createOwnedJob<Job> { attach -> unparentedScope.launch(start=CoroutineStart.LAZY) { calls.incrementAndGet() }.also(attach) }
        job.cancel();assertThrows(IllegalStateException::class.java) { startOwnedBrowserProbe(operation,job) }
        assertEquals(0,calls.get());assertTrue(job.isCompleted);assertArrayEquals(ByteArray(32),key)
    }

    @Test fun normalCompletionClosesOnlyExactForkAndKeepsOriginalOwnerLive() = isolated { _, owner ->
        val operation=owner.fork(owner.scopes);val key=operation.key
        val job=operation.createOwnedJob<Job> { attach -> unparentedScope.launch(start=CoroutineStart.LAZY) {}.also(attach) }
        startOwnedBrowserProbe(operation,job);runBlocking { withTimeout(5_000) { job.join() } }
        assertArrayEquals(ByteArray(32),key);assertThrows(IllegalStateException::class.java) { operation.checkValid() };owner.checkValid()
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test fun hookInstallationFailureClosesExactAlreadyOwnedJob() = isolated { _, owner ->
        val operation=owner.fork(owner.scopes);val key=operation.key;val actual=Job();var hooks=0
        val facade=object : Job by actual {
            override fun invokeOnCompletion(handler: CompletionHandler): DisposableHandle {
                if (++hooks>1) throw IllegalStateException("synthetic probe hook failure")
                return actual.invokeOnCompletion(handler)
            }
            override fun invokeOnCompletion(onCancelling: Boolean,invokeImmediately: Boolean,handler: CompletionHandler): DisposableHandle =
                throw IllegalStateException("synthetic probe cancellation hook failure")
        }
        val job=operation.createOwnedJob<Job> { attach -> facade.also(attach) }
        assertThrows(IllegalStateException::class.java) { startOwnedBrowserProbe(operation,job) }
        assertArrayEquals(ByteArray(32),key);runBlocking { withTimeout(5_000) { actual.join() } };assertTrue(actual.isCancelled);owner.checkValid()
    }

    @Test fun startFailureDispatchesExactFundedJobAndWipesForkKey() = isolated { _, owner ->
        val operation=owner.fork(owner.scopes);val key=operation.key;val actual=Job()
        val facade=object : Job by actual { override fun start(): Boolean = throw IllegalStateException("synthetic start failure") }
        val job=operation.createOwnedJob<Job> { attach -> facade.also(attach) }
        assertThrows(IllegalStateException::class.java) { startOwnedBrowserProbe(operation,job) }
        assertArrayEquals(ByteArray(32),key);runBlocking { withTimeout(5_000) { actual.join() } };assertTrue(actual.isCancelled);owner.checkValid()
    }

    // No implicit additional Job is created for this test-only producer scope.
    private val unparentedScope = object : CoroutineScope { override val coroutineContext = Dispatchers.IO }
    private fun isolated(test: (PrimarySessionAuthority,PrimaryOperation) -> Unit) {
        val authority=PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool","presentationReleasePool"))
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible=true }.set(authority,ReleasePool(16))
        authority.open(ByteArray(32) { 0x5a });val owner=authority.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.BROWSER_UPLOAD_EGRESS))!!
        try { test(authority,owner) } finally { owner.close();authority.revoke() }
    }
}
