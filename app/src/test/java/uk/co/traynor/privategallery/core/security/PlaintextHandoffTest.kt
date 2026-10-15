package uk.co.traynor.privategallery.core.security

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaintextHandoffTest {
    @Test fun duplicateAndLateCallbacksRetireOnlyTheirOriginal() = runBlocking {
        val a = testPrimaryAuthority(); a.open(ByteArray(32))
        val op = checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val bytes = byteArrayOf(7); val first = op.createOwnedPlaintext { bytes }
        val extraBytes = byteArrayOf(8); val extra = op.createOwnedPlaintext { extraBytes }
        var callback: ((Result<OwnedPlaintext>) -> Unit)? = null
        val actual = awaitOwnedPlaintext { callback = it; it(Result.success(first)); it(Result.success(first)); it(Result.success(extra)) }
        assertSame(first, actual); first.useBytes { assertEquals(7, it[0].toInt()) }
        assertTrue(extra.retirement.await(5, TimeUnit.SECONDS)); assertEquals(0, extraBytes[0].toInt())
        actual.close(); assertTrue(actual.retirement.await(5, TimeUnit.SECONDS))
        callback!!(Result.success(first)); op.close(); a.revoke()
    }
    @Test fun cancelledReceiverClosesLateResult() = runBlocking {
        val a = testPrimaryAuthority(); a.open(ByteArray(32)); val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        var callback: ((Result<OwnedPlaintext>) -> Unit)?=null
        val job=launch(start=CoroutineStart.UNDISPATCHED) { awaitOwnedPlaintext { callback=it } }
        job.cancelAndJoin()
        val bytes=byteArrayOf(9);val owned=op.createOwnedPlaintext { bytes }
        callback!!(Result.success(owned)); assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertEquals(0,bytes[0].toInt())
        op.close();a.revoke()
    }
    @Test fun cancellationAfterCallbackBeforeDispatchClosesTransferredOriginal() = runBlocking {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val queue=java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val dispatcher=object:kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable) { queue.add(block) }
        }
        var callback:((Result<OwnedPlaintext>)->Unit)?=null;var consumers=0
        val source=op.createOwnedPlaintext { byteArrayOf(6) }
        val job=launch(dispatcher,start=CoroutineStart.UNDISPATCHED) {
            val received=awaitOwnedPlaintext { callback=it };try { consumers++ } finally { received.close() }
        }
        try {
            callback!!(Result.success(source));job.cancel()
            while(true)(queue.poll()?:break).run()
            withTimeout(5000) { job.join() };assertEquals(0,consumers)
            assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
        } finally { job.cancel();while(true)(queue.poll()?:break).run();source.close();op.close();a.revoke() }
    }
    @Test fun failedCleanupRemainsChargedAfterPlaintextRetires() {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val failed=op.createOwned(OwnedResourceManifest.io("failing-input")) { attach("failing-input",AutoCloseable { error("Synthetic close failure") }) }
        val bytes=byteArrayOf(9);val source=op.createOwnedPlaintext { bytes }
        try {
            failed.close();source.close();assertTrue(source.retirement.await(5,TimeUnit.SECONDS));assertArrayEquals(ByteArray(1),bytes)
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(!failed.releaseFailed && System.nanoTime()<deadline) Thread.yield()
            assertTrue(failed.releaseFailed);assertFalse(failed.retirement.isComplete)
            a.revoke();val rejected=ByteArray(32){3}
            assertThrows(IllegalStateException::class.java) { a.open(rejected) }
            assertArrayEquals(ByteArray(32),rejected);assertFalse(a.cleanupComplete)
        } finally { source.close();op.close();a.revoke() }
    }
    @Test fun playerReadsBorrowPerCallAndDenyRevokedSource() {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        val owned=op.createOwnedPlaintext { byteArrayOf(1,2,3) };val target=ByteArray(2)
        assertEquals(3,owned.size);assertEquals(2,owned.readAt(1,target,0,2));assertArrayEquals(byteArrayOf(2,3),target)
        a.revoke();assertThrows(IllegalStateException::class.java) { owned.readAt(0,target,0,2) };assertArrayEquals(ByteArray(2),target)
        owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));op.close()
    }
}
