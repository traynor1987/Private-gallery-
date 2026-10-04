package uk.co.traynor.privategallery.core.editor

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Public byte fixtures; this scope covers copied input/outer Job, not renderer/provider children. */
class Phase3PhotoGenerationOwnershipTest {
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
        work.job.cancel();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run()
        runBlocking{withTimeout(5000){work.job.join()}};await{a.cleanupComplete}
        assertEquals(0,calls.get());assertTrue(work.completed);assertTrue(work.retirement.isComplete);owner.checkValid()
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
    private fun genuineFailure(expected:Throwable)=isolated {a,owner->
        var copy:ByteArray?=null;var failure:Throwable?=null;val seen=CountDownLatch(1)
        val target=CoroutineScope(Dispatchers.IO+CoroutineExceptionHandler{_,actual->failure=actual;seen.countDown()})
        val work=launchPhotoGenerationWork(owner,target,byteArrayOf(7,8)){bytes->copy=bytes;throw expected}
        runBlocking{withTimeout(5000){work.job.join()}};assertTrue(seen.await(5,TimeUnit.SECONDS))
        assertSame(expected,failure);assertArrayEquals(ByteArray(2),copy);assertTrue(work.completed);assertFalse(work.retirement.isComplete);assertFalse(a.cleanupComplete)
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
    private fun isolated(body:(PrimarySessionAuthority,PrimaryOperation)->Unit) {
        val a=testPrimaryAuthority();a.open(ByteArray(32));val owner=checkNotNull(a.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.LOCAL_EDIT)))
        try{body(a,owner)}finally{owner.close();a.revoke()}
    }
    private fun await(predicate:()->Boolean) {
        val end=System.nanoTime()+5_000_000_000
        while(!predicate()){check(System.nanoTime()<end){"Actual original acknowledgement missing"};Thread.yield()}
    }
}
