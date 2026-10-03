package uk.co.traynor.privategallery.core.browser.v2

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual probe producer and lazy native Jobs, synthetic transports, no external network/data. */
class Phase3BrowserProbeCancellationTest {
    @Test fun directCancellationDispatchesIndependentManifestUnblockersBeforeJobReturns() = blockedRead(false,false)
    @Test fun rootCancelChildrenDispatchesIndependentManifestUnblockersBeforeJobReturns() = blockedRead(true,false)
    @Test fun failedNativeCloseKeepsOriginalBarrierAfterCancelledJobReturns() = blockedRead(false,true)

    private fun blockedRead(cancelRoot: Boolean,failClose: Boolean) = isolated { authority,owner ->
        val operation=owner.fork(owner.scopes);val key=operation.key
        val entered=CountDownLatch(1);val disconnected=CountDownLatch(1);val closed=CountDownLatch(1);val allowReadReturn=CountDownLatch(1)
        val array=AtomicReference<ByteArray>();val closes=AtomicInteger();val disconnects=AtomicInteger();val factories=AtomicInteger()
        val connection=object : FakeConnection() {
            override fun disconnect() { disconnects.incrementAndGet();disconnected.countDown() }
            override fun getInputStream()=object : InputStream() {
                override fun read(): Int = error("unexpected scalar native read")
                override fun read(bytes: ByteArray,off: Int,len: Int): Int {
                    array.set(bytes);bytes.fill(0x5a);entered.countDown()
                    check(allowReadReturn.await(10,TimeUnit.SECONDS));bytes[off]=0x6b;return 1
                }
                override fun close() {
                    closes.incrementAndGet();closed.countDown()
                    if (failClose) throw java.io.IOException("synthetic input close failure")
                }
            }
        }
        val root=owner.createOwnedJob<Job> { attach -> Job().also(attach) }
        val job=operation.createOwnedJob<Job> { attach -> CoroutineScope(root+Dispatchers.IO).launch(start=CoroutineStart.LAZY) {
            val result=BrowserMediaProbe.inspect("https://invalid.example/list.m3u8","synthetic","",ScopedIoGuard(operation,PrimaryScope.BROWSER_UPLOAD_EGRESS),{ if (factories.incrementAndGet()==1) object : FakeConnection() { override fun disconnect() { disconnects.incrementAndGet() } } else connection }) { false }
            assertEquals(MediaSaveKind.UNSUPPORTED,result.kind)
        }.also(attach) }
        try {
            startOwnedBrowserProbe(operation,job);assertTrue(entered.await(5,TimeUnit.SECONDS))
            // The outer HEAD transport remains owned during the complete manifest phase.
            assertEquals(2,factories.get());assertEquals(0,disconnects.get())
            if (cancelRoot) root.cancelChildren() else job.cancel()
            assertTrue(closed.await(5,TimeUnit.SECONDS))
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while (disconnects.get()!=2 && System.nanoTime()<deadline) Thread.yield()
            assertEquals(2,disconnects.get());assertFalse(job.isCompleted)
            assertArrayEquals(ByteArray(32),key);assertFalse(authority.cleanupComplete)
            val denied=ByteArray(32) { 7 };assertThrows(IllegalStateException::class.java) { authority.open(denied) };assertArrayEquals(ByteArray(32),denied)
            allowReadReturn.countDown();runBlocking { withTimeout(5_000) { job.join() } }
            assertEquals(1,closes.get());assertEquals(2,disconnects.get())
            authority.revoke()
            if (failClose) {
                // The successful buffer child's physical slot must return before inspecting its array.
                val pool=PrimarySessionAuthority::class.java.getDeclaredField("ioReleasePool").apply { isAccessible=true }.get(authority) as ReleasePool
                val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while (pool.occupied!=1 && System.nanoTime()<end) Thread.yield()
                assertEquals(1,pool.occupied);assertFalse(authority.cleanupComplete)
                val deniedAfter=ByteArray(32) { 9 };assertThrows(IllegalStateException::class.java) { authority.open(deniedAfter) };assertArrayEquals(ByteArray(32),deniedAfter)
            } else awaitCleanup(authority)
            assertTrue(array.get().all { it==0.toByte() })
        } finally {
            operation.close();allowReadReturn.countDown();runBlocking { withTimeout(5_000) { job.join() } };root.cancel()
        }
    }

    @Test fun cancellationWhileInputFactoryBlockedRetiresLateActualInputWithoutReading() = isolated { authority,owner ->
        val operation=owner.fork(owner.scopes);val entered=CountDownLatch(1);val allowFactoryReturn=CountDownLatch(1)
        val disconnected=CountDownLatch(1);val closes=AtomicInteger();val reads=AtomicInteger();val disconnects=AtomicInteger();val factories=AtomicInteger()
        val connection=object : FakeConnection() {
            override fun disconnect() { disconnects.incrementAndGet();disconnected.countDown() }
            override fun getInputStream(): InputStream {
                entered.countDown();check(allowFactoryReturn.await(10,TimeUnit.SECONDS))
                return object : InputStream() { override fun read(): Int { reads.incrementAndGet();return -1 };override fun close() { closes.incrementAndGet() } }
            }
        }
        val root=owner.createOwnedJob<Job> { attach -> Job().also(attach) }
        val job=operation.createOwnedJob<Job> { attach -> CoroutineScope(root+Dispatchers.IO).launch(start=CoroutineStart.LAZY) {
            assertEquals(MediaSaveKind.UNSUPPORTED,BrowserMediaProbe.inspect("https://invalid.example/list.m3u8","synthetic","",ScopedIoGuard(operation,PrimaryScope.BROWSER_UPLOAD_EGRESS),{ if (factories.incrementAndGet()==1) object : FakeConnection() { override fun disconnect() { disconnects.incrementAndGet() } } else connection }) { false }.kind)
        }.also(attach) }
        try {
            startOwnedBrowserProbe(operation,job);assertTrue(entered.await(5,TimeUnit.SECONDS));job.cancel()
            val beforeAdmission=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while (disconnects.get()!=2 && System.nanoTime()<beforeAdmission) Thread.yield()
            assertEquals(2,disconnects.get());assertArrayEquals(ByteArray(32),operation.key)
            assertFalse(job.isCompleted);assertEquals(0,closes.get())
            val denied=ByteArray(32) { 3 };assertThrows(IllegalStateException::class.java) { authority.open(denied) };assertArrayEquals(ByteArray(32),denied)
            allowFactoryReturn.countDown();runBlocking { withTimeout(5_000) { job.join() } };authority.revoke();awaitCleanup(authority)
            assertEquals(0,reads.get());assertEquals(1,closes.get());assertEquals(2,disconnects.get())
        } finally { operation.close();allowFactoryReturn.countDown();runBlocking { withTimeout(5_000) { job.join() } };root.cancel() }
    }

    private open class FakeConnection : HttpURLConnection(URL("https://invalid.example/list.m3u8")) {
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun disconnect()=Unit
        override fun getResponseCode()=200
        override fun getContentType()="application/vnd.apple.mpegurl"
    }
    private fun awaitCleanup(authority: PrimarySessionAuthority) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
        assertTrue(authority.cleanupComplete)
    }
    private fun isolated(test: (PrimarySessionAuthority,PrimaryOperation) -> Unit) {
        val authority=PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool","presentationReleasePool"))
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible=true }.set(authority,ReleasePool(16))
        authority.open(ByteArray(32) { 0x5a });val owner=authority.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.BROWSER_UPLOAD_EGRESS))!!
        try { test(authority,owner) } finally { owner.close();authority.revoke() }
    }
}
