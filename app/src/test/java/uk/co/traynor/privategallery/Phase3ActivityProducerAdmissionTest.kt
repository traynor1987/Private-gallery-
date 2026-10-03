package uk.co.traynor.privategallery

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Actual production Activity launch helper; public arrays, isolated physical test pools only. */
class Phase3ActivityProducerAdmissionTest {
    @Test fun staleQueuedActivityProducerIsNeutralBeforeActualFundedCancelReturns() = stale(false)
    @Test fun staleActivityProducerReturnIsNeutralBeforeActualFundedCancelReturns() = stale(true)
    @Test fun actualBodyIllegalStateFailureRemainsOriginalStickyFailure() = genuineFailure(IllegalStateException("public Activity body failure"))
    @Test fun actualBodyErrorRemainsOriginalStickyFailure() = genuineFailure(AssertionError("public Activity body error"))
    @Test fun genuineClockIllegalStateFailureCannotBecomeNeutralProducerCancellation() {
        var clockFails=false;val failure=IllegalStateException("public clock failure")
        val authority=PrimarySessionAuthority{if(clockFails)throw failure else 0L}.also { value ->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        authority.open(ByteArray(32));val operation=checkNotNull(authority.operationOrNull());val queued=ArrayBlockingQueue<Runnable>(1)
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queued.offer(block))}}
        val seen=CountDownLatch(1);val observed=java.util.concurrent.atomic.AtomicReference<Throwable>();val calls=AtomicInteger()
        val scope=CoroutineScope(dispatcher+CoroutineExceptionHandler{_,actual->observed.set(actual);seen.countDown()})
        try {
            val job=launchOwned(operation,scope,kotlin.coroutines.EmptyCoroutineContext){calls.incrementAndGet()}
            authority.onBackgrounded(100);clockFails=true
            checkNotNull(queued.poll(5,TimeUnit.SECONDS)).run()
            runBlocking{withTimeout(5_000){job.join()}};assertTrue(seen.await(5,TimeUnit.SECONDS));assertSame(failure,observed.get());assertEquals(0,calls.get())
            val original=operation.reservations.single();await{original.failed};assertFalse(authority.cleanupComplete)
            clockFails=false;val replacement=ByteArray(32){9};assertThrows(IllegalStateException::class.java){authority.open(replacement)};assertArrayEquals(ByteArray(32),replacement)
        } finally {clockFails=false;operation.close();authority.revoke()}
    }
    @Test fun successfulActivityBodyKeepsNormalCompletionAndEpochPublication() = isolated { authority, operation ->
        var ran=false
        val key=operation.key
        val job=launchOwned(operation,CoroutineScope(Dispatchers.Unconfined),kotlin.coroutines.EmptyCoroutineContext){ran=true}
        runBlocking{withTimeout(5_000){job.join()}}
        assertTrue(ran);assertFalse(job.isCancelled);assertArrayEquals(ByteArray(32),key)
        var published=false;operation.publish{published=true};assertTrue(published)
        await{authority.cleanupComplete}
    }
    @Test fun cancelledLifecycleCannotRunBodyAndStillWipesOriginalLease() = isolated { authority, operation ->
        val parent=Job().also{it.cancel()};val calls=AtomicInteger();val key=operation.key
        val job=launchOwned(operation,CoroutineScope(parent+Dispatchers.Unconfined),kotlin.coroutines.EmptyCoroutineContext){calls.incrementAndGet()}
        runBlocking{withTimeout(5_000){job.join()}}
        assertTrue(job.isCancelled);assertEquals(0,calls.get());assertArrayEquals(ByteArray(32),key)
        await{authority.cleanupComplete}
    }
    @Test fun completeProducerCapacityDeniesBeforeDispatchOrBody() = isolated { authority, operation ->
        val holders=(1..16).map{operation.createOwned(OwnedResourceManifest.io("holder")){attach("holder",AutoCloseable{})}}
        val dispatched=AtomicInteger();val calls=AtomicInteger();val key=operation.key
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){dispatched.incrementAndGet();block.run()}}
        try {
            assertThrows(IllegalStateException::class.java){launchOwned(operation,CoroutineScope(dispatcher),kotlin.coroutines.EmptyCoroutineContext){calls.incrementAndGet()}}
            assertEquals(0,dispatched.get());assertEquals(0,calls.get());assertArrayEquals(ByteArray(32),key)
        } finally {holders.forEach{it.close()}}
        await{authority.cleanupComplete}
    }
    private fun genuineFailure(failure:Throwable) = isolated { authority, operation ->
        val seen=CountDownLatch(1);val observed=java.util.concurrent.atomic.AtomicReference<Throwable>();val key=operation.key
        val scope=CoroutineScope(Dispatchers.IO+CoroutineExceptionHandler{_,actual->observed.set(actual);seen.countDown()})
        val job=launchOwned(operation,scope,kotlin.coroutines.EmptyCoroutineContext){throw failure}
        runBlocking{withTimeout(5_000){job.join()}};assertTrue(seen.await(5,TimeUnit.SECONDS));assertSame(failure,observed.get())
        val original=operation.reservations.single();await{original.failed}
        assertArrayEquals(ByteArray(32),key);assertFalse(authority.cleanupComplete)
        val replacement=ByteArray(32){9};assertThrows(IllegalStateException::class.java){authority.open(replacement)};assertArrayEquals(ByteArray(32),replacement)
    }
    private fun isolated(body:(PrimarySessionAuthority,PrimaryOperation)->Unit) {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val operation=checkNotNull(authority.operationOrNull())
        try{body(authority,operation)}finally{operation.close();authority.revoke()}
    }
    private fun await(condition:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition()){check(System.nanoTime()<until);Thread.yield()}}
    private fun stale(afterBody: Boolean) {
        val cancelEntered=CountDownLatch(1);val allowCancel=CountDownLatch(1)
        val authority=PrimarySessionAuthority().also { value ->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16,allocateInvocation={action,returned ->
                ReleaseInvocation({cancelEntered.countDown();check(allowCancel.await(10,TimeUnit.SECONDS));action()},returned)
            }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        authority.open(ByteArray(32));val operation=checkNotNull(authority.operationOrNull())
        val queued=ArrayBlockingQueue<Runnable>(1)
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queued.offer(block))}}
        val calls=AtomicInteger();val bodyEntered=CountDownLatch(1);val allowReturn=CountDownLatch(1);val candidateReached=java.util.concurrent.atomic.AtomicBoolean()
        val uncaught=ConcurrentLinkedQueue<Throwable>()
        val scope=object:CoroutineScope{override val coroutineContext=(if(afterBody)Dispatchers.IO else dispatcher)+CoroutineExceptionHandler{_,failure->uncaught.add(failure)}}
        val job=launchOwned(operation,scope,kotlin.coroutines.EmptyCoroutineContext){
            calls.incrementAndGet();bodyEntered.countDown()
            if(afterBody)check(allowReturn.await(10,TimeUnit.SECONDS))
            candidateReached.set(true)
        }
        try {
            if(afterBody)assertTrue(bodyEntered.await(5,TimeUnit.SECONDS))
            authority.revoke();assertTrue(cancelEntered.await(5,TimeUnit.SECONDS))
            assertTrue(job.isActive);assertFalse(authority.cleanupComplete)
            if(afterBody)allowReturn.countDown() else checkNotNull(queued.poll(5,TimeUnit.SECONDS)).run()
            runBlocking{withTimeout(5_000){job.join()}}
            assertEquals(if(afterBody)1 else 0,calls.get())
            assertEquals(afterBody,candidateReached.get())
            assertTrue("Fixed helper stale admission must not become an uncaught consumer failure",uncaught.isEmpty())
            assertTrue("Stale Activity producer result must be cancelled",job.isCancelled)
            assertFalse("Actual funded cancel return remains mandatory after Job completion",authority.cleanupComplete)
        }finally{allowReturn.countDown();allowCancel.countDown();operation.close();authority.revoke()}
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(!authority.cleanupComplete&&System.nanoTime()<until)Thread.yield()
        assertTrue(authority.cleanupComplete)
    }
}
