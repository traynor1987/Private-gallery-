package uk.co.traynor.privategallery.core.security

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
    @Test fun activeBorrowDefersDisposalAndRejectsRetiredReuse() = isolated { _,guard ->
        val main=Thread.currentThread();val queue=LinkedBlockingQueue<Runnable>();var disposals=0
        val source=guard.createOwnedMainValue(post={queue.add(it);true},isMain={Thread.currentThread()===main},dispose={_:Any->disposals++}){Any()}
        assertThrows(IllegalStateException::class.java){source.useValue{source.close();assertNull(queue.poll(100,TimeUnit.MILLISECONDS))}}
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
    private fun await(condition:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition()){check(System.nanoTime()<until);Thread.yield()}}
    private fun isolated(body:(PrimarySessionAuthority,ScopedIoGuard)->Unit){val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=checkNotNull(authority.operationOrNull(PrimaryScope.entries.toSet()));try{body(authority,ScopedIoGuard(op,PrimaryScope.READ))}finally{op.close();authority.revoke()}}
}
