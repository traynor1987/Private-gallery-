package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Public fake Main/Native leaf fixtures; not provider/WebView/whole cleanup acceptance. */
class OwnedMainThreadValueTest {
    @Test fun postingDoesNotAcknowledgeActualNativeDisposal() = isolated { authority,guard ->
        val main=Thread.currentThread();val queue=LinkedBlockingQueue<Runnable>();val disposals=AtomicInteger()
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={Thread.currentThread()===main},dispose={_:Any->disposals.incrementAndGet()}){Any()}
        source.close();val callback=checkNotNull(queue.poll(5,TimeUnit.SECONDS));authority.revoke()
        assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete);assertEquals(0,disposals.get())
        callback.run();assertTrue(source.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,disposals.get());source.close();assertEquals(1,disposals.get())
    }
    @Test fun failedPostingPinsOriginalAndFreshAuthentication() = isolated { authority,guard ->
        var disposals=0
        val source=guard.createOwnedMainValue(post={false},isMain={true},dispose={_:Any->disposals++}){Any()}
        source.close();await{source.releaseFailed};authority.revoke();assertFalse(authority.cleanupComplete);assertEquals(0,disposals)
        val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
    }
    @Test fun activeBorrowDefersDisposalAndRejectsRetiredReuse() = isolated { authority,guard ->
        val workerReturned=CountDownLatch(1)
        setPresentationPool(authority,ReleasePool(16,allocateInvocation={action,returned->ReleaseInvocation({action();workerReturned.countDown()},returned)}))
        val main=Thread.currentThread();val queue=LinkedBlockingQueue<Runnable>();var disposals=0
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={Thread.currentThread()===main},dispose={_:Any->disposals++}){Any()}
        assertThrows(IllegalStateException::class.java){source.useValue{source.close();assertTrue(workerReturned.await(5,TimeUnit.SECONDS));assertNull(queue.poll())}}
        val callback=checkNotNull(queue.poll(5,TimeUnit.SECONDS));assertEquals(0,disposals);callback.run();assertTrue(source.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,disposals)
        assertThrows(IllegalStateException::class.java){source.useValue{error("retired consumer entered")}}
    }
    @Test fun exhaustedPresentationCapacityDeniesBeforeNativeFactory() = isolated { _,guard ->
        val holds=List(16){guard.createOwned(OwnedResourceManifest.presentation("hold")){attach("hold",AutoCloseable{})}};var calls=0
        try{assertThrows(IllegalStateException::class.java){guard.createOwnedMainValue(post={true},isMain={true},dispose={_:Any->}){calls++;Any()}};assertEquals(0,calls)}finally{holds.forEach{guard.retire(it)}}
    }
    @Test fun wrongPartitionCannotCreateMainLeafThroughInternalOriginal() = isolated { _,guard ->
        var calls=0
        assertThrows(IllegalStateException::class.java) {
            guard.createOwned(OwnedResourceManifest.io("wrong-root")) {
                original.createMainValue(guard,post={it.run();true},isMain={true},dispose={_:Any->}) { calls++;Any() }
            }
        }
        assertEquals(0,calls)
    }
    @Test fun revokedBlockedFactoryBindsLateActualAndKeepsWorkerIdle() = isolated { authority,guard ->
        val entered=CountDownLatch(1);val allow=CountDownLatch(1);val workerReturned=CountDownLatch(1)
        setPresentationPool(authority,ReleasePool(16,allocateInvocation={action,returned->ReleaseInvocation({action();workerReturned.countDown()},returned)}))
        val original=AtomicReference<ReleaseReservation>();val result=AtomicReference<Throwable?>();val queue=LinkedBlockingQueue<Runnable>();val disposals=AtomicInteger()
        val constructor=Thread { try {
            guard.createOwned(OwnedResourceManifest.presentation("main-root")) {
                original.set(this.original)
                this.original.createMainValue(guard,post={queue.add(it);true},isMain={true},dispose={_:Any->disposals.incrementAndGet()}) {
                    entered.countDown();allow.await();Any()
                }
            }
        } catch(t:Throwable) { result.set(t) } }
        try {
            constructor.start();assertTrue(entered.await(5,TimeUnit.SECONDS));authority.revoke()
            assertTrue(workerReturned.await(5,TimeUnit.SECONDS));assertNull(queue.poll());assertEquals(0,disposals.get())
            assertFalse(original.get().terminalRetirement.isComplete);assertFalse(authority.cleanupComplete)
            allow.countDown();constructor.join(5000);assertFalse(constructor.isAlive);assertTrue(result.get() is IllegalStateException)
            checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();assertTrue(original.get().terminalRetirement.await(5,TimeUnit.SECONDS));assertEquals(1,disposals.get())
        } finally {allow.countDown();constructor.join(5000)}
    }
    @Test fun fastNativeReturnCannotAcknowledgeBeforePostingActuallyReturns() = isolated { authority,guard ->
        val nativeReturned=CountDownLatch(1);val allowPost=CountDownLatch(1);val disposals=AtomicInteger()
        val source=guard.createOwnedMainValue(post={it.run();nativeReturned.countDown();allowPost.await();true},isMain={true},dispose={_:Any->disposals.incrementAndGet()}){Any()}
        try {source.close();assertTrue(nativeReturned.await(5,TimeUnit.SECONDS));authority.revoke();assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete);assertEquals(1,disposals.get())}
        finally{allowPost.countDown()}
        assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
    @Test fun fastNativeReturnThenRejectedPostingIsStickyFailure() = isolated { authority,guard ->
        val disposals=AtomicInteger();val posts=AtomicInteger()
        val source=guard.createOwnedMainValue(post={posts.incrementAndGet();it.run();false},isMain={true},dispose={_:Any->disposals.incrementAndGet()}){Any()}
        source.close();await{source.releaseFailed};source.close();authority.revoke();assertEquals(1,posts.get());assertEquals(1,disposals.get());assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete)
        assertFreshDenied(authority)
    }
    @Test fun fastNativeReturnThenThrowingPostingIsStickyFailure() = isolated { authority,guard ->
        val disposals=AtomicInteger();val posts=AtomicInteger()
        val source=guard.createOwnedMainValue(post={posts.incrementAndGet();it.run();throw IOException("synthetic posting failure")},isMain={true},dispose={_:Any->disposals.incrementAndGet()}){Any()}
        source.close();await{source.releaseFailed};source.close();authority.revoke();assertEquals(1,posts.get());assertEquals(1,disposals.get());assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete);assertFreshDenied(authority)
    }
    @Test fun postingReturnAndWorkerReturnCannotAcknowledgeHeldNativeCallback() = isolated { authority,guard ->
        val queue=LinkedBlockingQueue<Runnable>();val entered=CountDownLatch(1);val allow=CountDownLatch(1);val workerReturned=CountDownLatch(1)
        setPresentationPool(authority,ReleasePool(16,allocateInvocation={action,returned->ReleaseInvocation({action();workerReturned.countDown()},returned)}))
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={true},dispose={_:Any->entered.countDown();allow.await()}){Any()}
        source.close();val callback=checkNotNull(queue.poll(5,TimeUnit.SECONDS));val runner=Thread{callback.run()}
        try{runner.start();assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(workerReturned.await(5,TimeUnit.SECONDS));authority.revoke();assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete)}
        finally{allow.countDown();runner.join(5000)}
        assertFalse(runner.isAlive);assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
    @Test fun failedNativeCallbackPinsWithoutRetryOrFreshAuthentication() = isolated { authority,guard ->
        val queue=LinkedBlockingQueue<Runnable>();val disposals=AtomicInteger()
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={true},dispose={_:Any->disposals.incrementAndGet();throw AssertionError("synthetic Native failure")}){Any()}
        source.close();val callback=checkNotNull(queue.poll(5,TimeUnit.SECONDS));callback.run();await{source.releaseFailed};callback.run();source.close()
        assertEquals(1,disposals.get());authority.revoke();assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete);assertFreshDenied(authority)
    }
    @Test fun unknownNativeConstructionFailureStaysCharged() = isolated { authority,guard ->
        val thrown=AssertionError("synthetic entered factory failure");val original=AtomicReference<ReleaseReservation>();var posts=0
        assertSame(thrown,assertThrows(AssertionError::class.java){guard.createOwned(OwnedResourceManifest.presentation("root")) {
            original.set(this.original);this.original.createMainValue(guard,post={posts++;true},isMain={true},dispose={_:Any->}){throw thrown}
        }})
        await{original.get().failed};authority.revoke();assertEquals(0,posts);assertFalse(original.get().terminalRetirement.isComplete);assertFalse(authority.cleanupComplete);assertFreshDenied(authority)
    }
    @Test fun knownPreNativeAffinityDenialRetiresEmptyOriginalAfterUnwind() = isolated { authority,guard ->
        val original=AtomicReference<ReleaseReservation>();var calls=0;var posts=0
        assertThrows(IllegalStateException::class.java){guard.createOwned(OwnedResourceManifest.presentation("root")) {
            original.set(this.original);this.original.createMainValue(guard,post={posts++;true},isMain={false},dispose={_:Any->}){calls++;Any()}
        }}
        assertTrue(original.get().terminalRetirement.await(5,TimeUnit.SECONDS));assertFalse(original.get().failed);assertEquals(0,calls);assertEquals(0,posts);authority.revoke();assertTrue(authority.cleanupComplete)
    }
    @Test fun originatingAuthorityGatedFactoryDeniesBeforeFundingOrNative() = isolated { authority,guard ->
        var calls=0;val pool=presentationPool(authority);val before=pool.occupied
        assertThrows(IllegalStateException::class.java){guard.commit{guard.createOwnedMainValue(post={true},isMain={true},dispose={_:Any->}){calls++;Any()}}}
        assertEquals(0,calls);assertEquals(before,pool.occupied)
    }
    @Test fun originatingAuthorityGatedUseDeniesBeforeConsumer() = isolated { _,guard ->
        val queue=LinkedBlockingQueue<Runnable>();var calls=0
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={true},dispose={_:Any->}){Any()}
        try{assertThrows(IllegalStateException::class.java){guard.commit{source.useValue{calls++}}};assertEquals(0,calls)}
        finally{source.close();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun wrongReleaseAffinityNeverInvokesDisposerAndStaysCharged() = isolated { authority,guard ->
        val main=Thread.currentThread();val queue=LinkedBlockingQueue<Runnable>();var calls=0
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={Thread.currentThread()===main},dispose={_:Any->calls++}){Any()}
        source.close();val callback=checkNotNull(queue.poll(5,TimeUnit.SECONDS));val wrong=Thread{callback.run()};wrong.start();wrong.join(5000);assertFalse(wrong.isAlive)
        await{source.releaseFailed};callback.run();source.close();assertEquals(0,calls);authority.revoke();assertFalse(authority.cleanupComplete);assertFreshDenied(authority)
    }
    @Test fun wrongUseAffinityDeniesBeforeConsumer() = isolated { _,guard ->
        val main=Thread.currentThread();val queue=LinkedBlockingQueue<Runnable>();val calls=AtomicInteger();val failure=AtomicReference<Throwable?>()
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={Thread.currentThread()===main},dispose={_:Any->}){Any()}
        val wrong=Thread{try{source.useValue{calls.incrementAndGet()}}catch(t:Throwable){failure.set(t)}}
        try{wrong.start();wrong.join(5000);assertFalse(wrong.isAlive);assertTrue(failure.get() is IllegalStateException);assertEquals(0,calls.get())}
        finally{source.close();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun reentrantUseDeniesBeforeSecondConsumerAndPreservesMatchingThrowable() = isolated { _,guard ->
        val queue=LinkedBlockingQueue<Runnable>();val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={true},dispose={_:Any->}){Any()};val thrown=IOException("synthetic consumer failure")
        try{source.useValue{assertThrows(IllegalStateException::class.java){source.useValue{error("nested consumer entered")}}};assertSame(thrown,assertThrows(IOException::class.java){source.useValue{throw thrown}})}
        finally{source.close();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun repeatedRunnableDisposesExactValueOnceAndFacadeExposesNoFuture() = isolated { _,guard ->
        val queue=LinkedBlockingQueue<Runnable>();val actual=Any();var calls=0
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={true},dispose={value:Any->assertSame(actual,value);calls++}){actual}
        source.useValue{assertSame(actual,it)};source.close();val callback=checkNotNull(queue.poll(5,TimeUnit.SECONDS));callback.run();callback.run();assertTrue(source.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,calls)
        assertFalse(source.javaClass.declaredMethods.any{CompletableFuture::class.java.isAssignableFrom(it.returnType)||java.util.concurrent.CompletionStage::class.java.isAssignableFrom(it.returnType)})
    }
    @Test fun nativeCallbacksAndAcknowledgementCompletionRunOutsidePrivateGates() = isolated { authority,guard ->
        val queue=LinkedBlockingQueue<Runnable>();val actual=Any();val reservation=AtomicReference<ReleaseReservation>();val cell=AtomicReference<OriginalMainValue<Any>>()
        fun outside(){
            assertFalse(Thread.holdsLock(privateGate(authority)))
            reservation.get()?.let{assertFalse(Thread.holdsLock(privateGate(it)))}
            cell.get()?.let{assertFalse(Thread.holdsLock(privateGate(it)))}
        }
        val owned=guard.createOwned(OwnedResourceManifest.presentation("root")) {
            reservation.set(original)
            original.createMainValue(guard,post={outside();queue.add(it);true},isMain={outside();true},dispose={value:Any->outside();assertSame(actual,value)}){outside();actual}.also{cell.set(it)}
        }
        val source=OwnedMainThreadValue(owned)
        source.useValue{outside();assertSame(actual,it)}
        val observed=CountDownLatch(1)
        // Test-only inspection of the preallocated cell; production facade has no stage.
        val future=cell.get().javaClass.getDeclaredField("acknowledged").apply{isAccessible=true}.get(cell.get()) as CompletableFuture<*>
        val failure=AtomicReference<Throwable?>();future.whenComplete{_,_->try{outside()}catch(t:Throwable){failure.set(t)}finally{observed.countDown()}}
        source.close();checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();assertTrue(observed.await(5,TimeUnit.SECONDS));assertNull(failure.get());assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
    @Test fun actualPhysicalWorkerReturnRemainsRequiredAfterNativeAcknowledgement() = isolated { authority,guard ->
        val queue=LinkedBlockingQueue<Runnable>();val returning=CountDownLatch(1);val allow=CountDownLatch(1)
        setPresentationPool(authority,ReleasePool(16,allocateInvocation={action,returned->ReleaseInvocation(action,{returning.countDown();allow.await();returned()})}))
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={true},dispose={_:Any->}){Any()}
        try{source.close();assertTrue(returning.await(5,TimeUnit.SECONDS));checkNotNull(queue.poll(5,TimeUnit.SECONDS)).run();authority.revoke();assertFalse(source.retirement.isComplete);assertFalse(authority.cleanupComplete)}finally{allow.countDown()}
        assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
    }
    private fun privateGate(value:Any):Any=value.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(value)
    private fun assertFreshDenied(authority:PrimarySessionAuthority){val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)}
    private fun setPresentationPool(authority:PrimarySessionAuthority,pool:ReleasePool){authority.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(authority,pool)}
    private fun presentationPool(authority:PrimarySessionAuthority):ReleasePool=authority.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.get(authority) as ReleasePool
    private fun await(condition:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition()){check(System.nanoTime()<until);Thread.yield()}}
    private fun isolated(body:(PrimarySessionAuthority,ScopedIoGuard)->Unit){val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=checkNotNull(authority.operationOrNull(PrimaryScope.entries.toSet()));try{body(authority,ScopedIoGuard(op,PrimaryScope.READ))}finally{op.close();authority.revoke()}}
}
