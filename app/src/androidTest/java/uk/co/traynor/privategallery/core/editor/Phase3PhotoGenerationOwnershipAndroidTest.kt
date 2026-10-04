package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import uk.co.traynor.privategallery.core.security.*

/** Public byte fixtures; this scope covers copied input/outer Job, not renderer/provider children. */
@RunWith(AndroidJUnit4::class)
class Phase3PhotoGenerationOwnershipAndroidTest {
    private val scope=CoroutineScope(Dispatchers.IO)
    @Test fun oneFreeSlotDeniesWholeInputAndProducerBeforeAnyBodyOrChildJob()=isolated {a,owner->
        val holds=List(15){owner.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}}
        val parent=Job();val calls=AtomicInteger();val source=ByteArray(1024){7}
        try{assertThrows(IllegalStateException::class.java){launchPhotoGenerationWork(owner,CoroutineScope(parent+Dispatchers.IO),source){calls.incrementAndGet()}}
            assertEquals(0,calls.get());assertFalse(parent.children.any());assertTrue(source.all{it==7.toByte()});owner.checkValid()
        }finally{parent.cancel();holds.forEach{it.close()}}
        await{a.cleanupComplete}
    }
    @Test fun successBorrowsExactCopyWipesItAndPreservesSourceAndEditorLease()=isolated {a,owner->
        val source=ByteArray(1024){7};var copy:ByteArray?=null
        val work=launchPhotoGenerationWork(owner,scope,source){bytes->copy=bytes;assertNotSame(source,bytes);assertArrayEquals(source,bytes)}
        runBlocking{withTimeout(5000){work.job.join()}};await{a.cleanupComplete}
        assertTrue(work.completed);assertTrue(work.retirement.isComplete);assertArrayEquals(ByteArray(1024),copy)
        assertTrue(source.all{it==7.toByte()});owner.checkValid()
    }
    @Test fun queuedPrestartCancellationCompletesWithoutRunningBody()=isolated {a,owner->
        val queue=java.util.concurrent.ArrayBlockingQueue<Runnable>(4);val calls=AtomicInteger()
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queue.offer(block))}}
        val work=launchPhotoGenerationWork(owner,CoroutineScope(dispatcher),byteArrayOf(7,8)){calls.incrementAndGet()}
        val original=owner.reservations.single()
        val root=privateRoot(original);val copy=privateCopy(root);assertArrayEquals(byteArrayOf(7,8),copy)
        work.job.cancel();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run()
        runBlocking{withTimeout(5000){work.job.join()}};await{a.cleanupComplete}
        assertEquals(0,calls.get());assertTrue(work.completed);assertTrue(work.retirement.isComplete);assertArrayEquals(ByteArray(2),copy);owner.checkValid()
    }
    @Test fun alreadyCancelledParentNeverRunsBodyAndLeavesNoPendingOriginal()=isolated {a,owner->
        val parent=Job();parent.cancel();val calls=AtomicInteger();val source=byteArrayOf(7,8)
        assertTrue(runCatching{launchPhotoGenerationWork(owner,CoroutineScope(parent+Dispatchers.IO),source){calls.incrementAndGet()}}.exceptionOrNull() is CancellationException)
        await{a.cleanupComplete};assertEquals(0,calls.get());assertArrayEquals(byteArrayOf(7,8),source);owner.checkValid()
    }
    @Test fun revokeCannotWipeBorrowUntilActualCallbackReturnsButCancelsJobIndependently()=isolated {a,owner->
        val entered=CountDownLatch(1);val escape=CountDownLatch(1);var copy:ByteArray?=null;val source=ByteArray(1024){7}
        val work=launchPhotoGenerationWork(owner,scope,source){bytes->copy=bytes;entered.countDown();check(escape.await(10,TimeUnit.SECONDS));assertTrue(bytes.all{it==7.toByte()})}
        try{assertTrue(entered.await(5,TimeUnit.SECONDS));a.revoke();await{work.job.isCancelled}
            assertFalse(work.job.isCompleted);assertFalse(work.completed);assertFalse(work.retirement.isComplete);assertFalse(a.cleanupComplete)
            assertArrayEquals(source,copy);val key=ByteArray(32){9};assertThrows(IllegalStateException::class.java){a.open(key)};assertArrayEquals(ByteArray(32),key)
        }finally{escape.countDown();runBlocking{withTimeout(5000){work.job.join()}}}
        await{a.cleanupComplete};assertTrue(work.completed);assertTrue(work.retirement.isComplete);assertArrayEquals(ByteArray(1024),copy)
    }
    @Test fun genuineConsumerErrorKeepsIdentityWipesCopyAndFailedJobCharge() {
        genuineFailure(object:AssertionError("public render fault"){})
    }
    @Test fun genuineConsumerIllegalStateKeepsIdentityAndFailedCharge() {
        genuineFailure(object:IllegalStateException("public consumer fault"){})
    }
    @Test fun childOnProvidedReceiverPinsBorrowUntilStructuredChildActuallyReturns()=isolated {a,owner->
        val entered=CountDownLatch(1);val directReturned=CountDownLatch(1);val escape=CountDownLatch(1);var copy:ByteArray?=null
        val work=launchPhotoGenerationWork(owner,scope,byteArrayOf(7,8)){bytes->
            copy=bytes
            launch(Dispatchers.IO){entered.countDown();check(escape.await(10,TimeUnit.SECONDS));assertArrayEquals(byteArrayOf(7,8),bytes)}
            directReturned.countDown()
        }
        try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(directReturned.await(5,TimeUnit.SECONDS))
            assertFalse(work.job.isCompleted);assertFalse(work.completed);assertFalse(work.retirement.isComplete);assertArrayEquals(byteArrayOf(7,8),copy)
            a.revoke();await{work.job.isCancelled};assertFalse(a.cleanupComplete);assertArrayEquals(byteArrayOf(7,8),copy)
        }finally{escape.countDown();runBlocking{withTimeout(5000){work.job.join()}}}
        await{a.cleanupComplete};assertArrayEquals(ByteArray(2),copy);assertTrue(work.retirement.isComplete)
    }
    @Test fun suspendedBorrowCanResumeOnAnotherDispatcherWithoutEarlyWipe()=isolated {a,owner->
        val entered=CompletableDeferred<Unit>();val escape=CompletableDeferred<Unit>();var copy:ByteArray?=null
        val work=launchPhotoGenerationWork(owner,scope,byteArrayOf(7,8)){bytes->
            copy=bytes;entered.complete(Unit)
            withContext(Dispatchers.Default+NonCancellable){escape.await();assertArrayEquals(byteArrayOf(7,8),bytes)}
        }
        try{runBlocking{withTimeout(5000){entered.await()}};a.revoke();await{work.job.isCancelled}
            assertFalse(work.retirement.isComplete);assertArrayEquals(byteArrayOf(7,8),copy)
        }finally{escape.complete(Unit);runBlocking{withTimeout(5000){work.job.join()}}}
        await{a.cleanupComplete};assertArrayEquals(ByteArray(2),copy)
    }
    @Test fun exactRootIdentityAndWrongOriginalRemainGenuineEvenAfterRetirement()=isolated {a,owner->
        val queue=java.util.concurrent.ArrayBlockingQueue<Runnable>(4)
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queue.offer(block))}}
        val work=launchPhotoGenerationWork(owner,CoroutineScope(dispatcher),byteArrayOf(7)){}
        val original=owner.reservations.single();val root=privateRoot(original)
        val foreign=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.LOCAL_EDIT)))
        try{assertThrows(IllegalStateException::class.java){original.requirePhotoInputUse(foreign,root)}
            assertThrows(IllegalStateException::class.java){original.requirePhotoInputUse(owner,Any())}
            work.job.cancel();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();runBlocking{withTimeout(5000){work.job.join()}};await{a.cleanupComplete}
            assertThrows(IllegalStateException::class.java){original.requirePhotoInputUse(owner,Any())}
            val denied=assertThrows(CancellationException::class.java){original.requirePhotoInputUse(owner,root)};assertNull(denied.cause)
        }finally{foreign.close()}
    }
    @Test fun genuineActiveDeadlineClockFailurePropagatesAtEntryAndAfterConsumer() {
        for(post in listOf(false,true)) {
            val expected=object:IllegalStateException("public clock fault"){};var failClock=false
            val a=PrimarySessionAuthority{if(failClock)throw expected else 0L}
            for(name in listOf("ioReleasePool","presentationReleasePool"))a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,ReleasePool(16))
            a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.LOCAL_EDIT)));a.onBackgrounded(100000)
            try{if(!post){failClock=true;val caught=runCatching{launchPhotoGenerationWork(owner,scope,byteArrayOf(7)) {error("No callback")}}.exceptionOrNull();assertSame(expected,caught);failClock=false;assertTrue(a.cleanupComplete)}
                else{var copy:ByteArray?=null;var failure:Throwable?=null;val seen=CountDownLatch(1)
                    val queue=java.util.concurrent.ArrayBlockingQueue<Runnable>(4)
                    val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queue.offer(block))}}
                    val target=CoroutineScope(dispatcher+CoroutineExceptionHandler{_,actual->failure=actual;seen.countDown()})
                    val work=launchPhotoGenerationWork(owner,target,byteArrayOf(7)){bytes->copy=bytes;failClock=true}
                    checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run()
                    runBlocking{withTimeout(5000){work.job.join()}};assertTrue(seen.await(5,TimeUnit.SECONDS));assertSame(expected,failure);assertArrayEquals(ByteArray(1),copy)
                    failClock=false;val original=owner.reservations.single();await{original.failed};assertFalse(work.retirement.isComplete);assertFalse(a.cleanupComplete)
                }
            }finally{failClock=false;owner.close();a.revoke()}
        }
    }
    @Test fun queuedStaleAdmissionStaysNeutralWhileActualCancelWorkerIsHeld() { staleAdmission(false) }
    @Test fun postConsumerStaleAdmissionStaysNeutralWhileActualCancelWorkerIsHeld() { staleAdmission(true) }
    private fun staleAdmission(afterConsumer:Boolean) {
        val cancelEntered=CountDownLatch(1);val allowCancel=CountDownLatch(1);val releaseIndex=AtomicInteger()
        val a=PrimarySessionAuthority().also{value->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16,allocateInvocation={action,returned->
                val index=releaseIndex.incrementAndGet()
                ReleaseInvocation({if(index==2){cancelEntered.countDown();check(allowCancel.await(10,TimeUnit.SECONDS))};action()},returned)
            }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.LOCAL_EDIT)))
        val queue=java.util.concurrent.ArrayBlockingQueue<Runnable>(4)
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queue.offer(block))}}
        val calls=AtomicInteger();val entered=CountDownLatch(1);val escape=CountDownLatch(1)
        val uncaught=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val target=CoroutineScope((if(afterConsumer)Dispatchers.IO else dispatcher)+CoroutineExceptionHandler{_,failure->uncaught.add(failure)})
        var copy:ByteArray?=null
        val work=launchPhotoGenerationWork(owner,target,byteArrayOf(7,8)){bytes->copy=bytes;calls.incrementAndGet();entered.countDown();if(afterConsumer)check(escape.await(10,TimeUnit.SECONDS))}
        try{
            if(afterConsumer)assertTrue(entered.await(5,TimeUnit.SECONDS))
            a.revoke();assertTrue(cancelEntered.await(5,TimeUnit.SECONDS));assertTrue(work.job.isActive);assertFalse(a.cleanupComplete)
            if(afterConsumer)escape.countDown() else checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run()
            runBlocking{withTimeout(5000){work.job.join()}}
            assertEquals(if(afterConsumer)1 else 0,calls.get());assertTrue(uncaught.isEmpty());assertTrue(work.job.isCancelled)
            assertTrue(work.completed);assertFalse(work.retirement.isComplete);assertFalse(a.cleanupComplete)
            if(afterConsumer)assertArrayEquals(ByteArray(2),copy)
            val key=ByteArray(32){9};assertThrows(IllegalStateException::class.java){a.open(key)};assertArrayEquals(ByteArray(32),key)
        }finally{escape.countDown();allowCancel.countDown();owner.close();a.revoke()}
        await{a.cleanupComplete};assertTrue(work.retirement.isComplete)
    }
    @Test fun admittedConsumerExecutesOutsideAuthorityAndPrivateRootGates()=isolated {a,owner->
        val authorityGate=a.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(a)
        val work=launchPhotoGenerationWork(owner,scope,byteArrayOf(7)){
            val root=privateRoot(owner.reservations.single())
            val rootGate=root.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(root)
            assertFalse(Thread.holdsLock(authorityGate));assertFalse(Thread.holdsLock(rootGate));owner.checkValid()
        }
        runBlocking{withTimeout(5000){work.job.join()}};await{a.cleanupComplete};assertTrue(work.retirement.isComplete)
    }
    @Test fun missingCapabilityRemainsGenuineWhenLeaseIsAlsoClosed() {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ)))
        owner.close()
        try{val failure=runCatching{launchPhotoGenerationWork(owner,scope,byteArrayOf(7)){error("No callback")}}.exceptionOrNull()
            assertTrue("Missing immutable capability must remain genuine",failure is IllegalStateException)
            assertFalse(failure is CancellationException);assertTrue(a.cleanupComplete)
        }finally{a.revoke()}
    }
    private fun privateRoot(original:ReleaseReservation):Any =
        (original.javaClass.getDeclaredField("actualIdentities").apply{isAccessible=true}.get(original) as Array<*>)[0]!!
    private fun privateCopy(root:Any):ByteArray =root.javaClass.getDeclaredField("actual").apply{isAccessible=true}.get(root) as ByteArray
    private fun genuineFailure(expected:Throwable)=isolated {a,owner->
        var copy:ByteArray?=null;var failure:Throwable?=null;val seen=CountDownLatch(1)
        val target=CoroutineScope(Dispatchers.IO+CoroutineExceptionHandler{_,actual->failure=actual;seen.countDown()})
        val work=launchPhotoGenerationWork(owner,target,byteArrayOf(7,8)){bytes->copy=bytes;throw expected}
        runBlocking{withTimeout(5000){work.job.join()}};assertTrue(seen.await(5,TimeUnit.SECONDS))
        assertSame(expected,failure);assertArrayEquals(ByteArray(2),copy);assertTrue(work.completed)
        val original=owner.reservations.single();await{original.failed}
        assertFalse(work.retirement.isComplete);assertFalse(a.cleanupComplete)
        owner.checkValid();a.revoke()
        val key=ByteArray(32){9};assertThrows(IllegalStateException::class.java){a.open(key)};assertArrayEquals(ByteArray(32),key)
    }
    @Test fun emptyOversizedSourceAndMissingReadOrEditCapabilityDenyBeforeBody() {
        for(scopes in listOf(setOf(PrimaryScope.READ),setOf(PrimaryScope.LOCAL_EDIT),setOf(PrimaryScope.REMOTE_AI_EGRESS))) {
            val a=testPrimaryAuthority();a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(scopes));var calls=0
            try{assertThrows(IllegalStateException::class.java){launchPhotoGenerationWork(owner,scope,byteArrayOf(7)){calls++}};assertEquals(0,calls);assertTrue(a.cleanupComplete)}
            finally{owner.close();a.revoke()}
        }
        isolated{a,owner->var calls=0
            for(source in listOf(ByteArray(0),ByteArray(PhotoRenderer.MAX_SOURCE_BYTES+1))) {
                assertTrue(runCatching{launchPhotoGenerationWork(owner,scope,source){calls++}}.exceptionOrNull() is IllegalArgumentException)
                await{a.cleanupComplete};owner.checkValid()
            }
            assertEquals(0,calls)
        }
    }
    private fun testPrimaryAuthority():PrimarySessionAuthority = PrimarySessionAuthority().also {a->
        for(name in listOf("ioReleasePool","presentationReleasePool"))a.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(a,ReleasePool(16))
    }
    private fun isolated(body:(PrimarySessionAuthority,PrimaryOperation)->Unit) {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.LOCAL_EDIT)))
        try{body(a,owner)}finally{owner.close();a.revoke()}
    }
    private fun await(predicate:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!predicate()){check(System.nanoTime()<end){"Actual original acknowledgement missing"};Thread.yield()}
    }
}
