package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class OwnedNativeUseTest {
    @Test fun actualNativeCallbackAndDisposerRunOutsideInvocationGate() = isolated {_,guard ->
        lateinit var use:OwnedNativeUse<FakeNative>
        val native=FakeNative {assertFalse(Thread.holdsLock(gate(use)))}
        val owned=funded(guard,native).also{use=it.second}.first
        try {
            assertEquals(7,use.useInt {assertFalse(Thread.holdsLock(gate(use)));7})
            assertEquals(Long.MAX_VALUE,use.useLong {Long.MAX_VALUE})
        }finally{owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
        assertEquals(1,native.closes)
    }
    @Test fun callbackCanEnterTheGateFromAnotherThread() = isolated {_,guard ->
        val (owned,use)=funded(guard,FakeNative())
        try {use.useInt {val done=CountDownLatch(1);val worker=Thread{synchronized(gate(use)){done.countDown()}};worker.start();assertTrue(done.await(5,TimeUnit.SECONDS));worker.join(5000);1}}
        finally{owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun overlapRejectsBeforeSecondNativeCallback() = isolated {_,guard ->
        val (owned,use)=funded(guard,FakeNative());val entered=CountDownLatch(1);val finish=CountDownLatch(1);val secondFailure=AtomicReference<Throwable?>();val firstFailure=AtomicReference<Throwable?>();var secondInvoked=false
        val first=Thread{try{use.useInt{entered.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));1}}catch(t:Throwable){firstFailure.set(t)}}
        val second=Thread{try{use.useInt{secondInvoked=true;1}}catch(t:Throwable){secondFailure.set(t)}}
        try {first.start();assertTrue(entered.await(5,TimeUnit.SECONDS));second.start();second.join(1000);assertFalse(second.isAlive);assertTrue(secondFailure.get() is IllegalStateException);assertFalse(secondInvoked)}
        finally{finish.countDown();first.join(5000);second.join(5000);owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
        assertNull(firstFailure.get())
    }
    @Test fun reentryDeniesBeforeAnotherNativeInvocation() = isolated {_,guard ->
        val (owned,use)=funded(guard,FakeNative());var nested=false
        try {assertEquals(1,use.useInt{assertThrows(IllegalStateException::class.java){use.useLong{nested=true;1L}};1});assertFalse(nested)}finally{owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun independentUnblockerRunsBeforeQuiescentNativeRelease() = isolated {authority,guard ->
        val entered=CountDownLatch(1);val unblock=CountDownLatch(1);val finish=CountDownLatch(1);val returned=CountDownLatch(1);val failure=AtomicReference<Throwable?>();val native=FakeNative()
        lateinit var use:OwnedNativeUse<FakeNative>
        val owned=guard.createOwned(OwnedResourceManifest.io("root","native","unblocker")) {
            val root=attach("root",AutoCloseable {})
            use=guardedNative("native",guard,{it:FakeNative->it.close()}){native}
            create("unblocker",{_:Any->unblock.countDown()}){Any()};root
        }
        val caller=Thread{try{use.useInt{entered.countDown();assertTrue(unblock.await(5,TimeUnit.SECONDS));assertTrue(finish.await(5,TimeUnit.SECONDS));1}}catch(t:Throwable){failure.set(t)}finally{returned.countDown()}}
        try {
            caller.start();assertTrue(entered.await(5,TimeUnit.SECONDS));authority.revoke();assertTrue(unblock.await(5,TimeUnit.SECONDS));assertEquals(0,native.closes);assertFalse(owned.retirement.isComplete)
            finish.countDown();assertTrue(returned.await(5,TimeUnit.SECONDS));caller.join(5000);assertTrue(failure.get() is IllegalStateException);assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,native.closes)
        }finally{finish.countDown();unblock.countDown();caller.join(5000);owned.close()}
    }
    @Test fun normalRetirementInsideCallbackDeniesItsResult() = isolated {_,guard ->
        val native=FakeNative();val (owned,use)=funded(guard,native)
        assertThrows(IllegalStateException::class.java){use.useInt{owned.close();1}}
        assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,native.closes)
    }
    @Test fun normalRetirementBeforeDelayedWorkerEntersNativeDisposalDeniesUse() = isolated {authority,guard ->
        val workersEntered=CountDownLatch(2);val allowDisposal=CountDownLatch(1)
        setIoPool(authority,ReleasePool(16,allocateInvocation={action,returned ->
            ReleaseInvocation({workersEntered.countDown();assertTrue(allowDisposal.await(5,TimeUnit.SECONDS));action()},returned)
        }))
        val native=FakeNative();val (owned,use)=funded(guard,native);var invoked=false
        try {
            owned.close();assertTrue(workersEntered.await(5,TimeUnit.SECONDS));guard.check()
            assertEquals(0,native.closes)
            assertThrows(IllegalStateException::class.java){use.useInt{invoked=true;1}}
            assertFalse(invoked)
        }finally{allowDisposal.countDown();owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
        assertEquals(1,native.closes)
    }
    @Test fun foreignGuardDeniesBeforeNativeFactory() = isolated {_,foreign ->
        isolated {_,guard ->
            var calls=0
            val owned=guard.createOwned(OwnedResourceManifest.io("root","native")) {
                val root=attach("root",AutoCloseable {})
                assertThrows(IllegalStateException::class.java){guardedNative("native",foreign,{it:FakeNative->it.close()}){calls++;FakeNative()}};root
            }
            assertEquals(0,calls);owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))
        }
    }
    @Test fun callbackFailureStillReleasesActualNativeValue() = isolated {_,guard ->
        val native=FakeNative();val (owned,use)=funded(guard,native)
        assertThrows(IOException::class.java){use.useInt{throw IOException("synthetic Native call failed")}}
        owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,native.closes)
    }
    @Test fun failedNativeDisposerRemainsChargedAndDeniesAuthentication() = isolated {authority,guard ->
        val (owned,use)=funded(guard,FakeNative{throw IOException("synthetic Native release failure")})
        assertEquals(1,use.useInt{1});owned.close()
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!owned.releaseFailed && System.nanoTime()<deadline)Thread.yield()
        assertTrue(owned.releaseFailed);assertFalse(owned.retirement.isComplete);authority.revoke();assertFalse(authority.cleanupComplete)
        val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
    }
    private fun setIoPool(authority:PrimarySessionAuthority,pool:ReleasePool) {
        authority.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(authority,pool)
    }
    private fun funded(guard:ScopedIoGuard,native:FakeNative):Pair<OwnedResource<AutoCloseable>,OwnedNativeUse<FakeNative>> {
        lateinit var use:OwnedNativeUse<FakeNative>
        val owned=guard.createOwned(OwnedResourceManifest.io("root","native")) {
            val root:AutoCloseable=attach("root",AutoCloseable {})
            use=guardedNative("native",guard,{it:FakeNative->it.close()}){native};root
        };return owned to use
    }
    private fun gate(use:OwnedNativeUse<*>):Any=use.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(use)
    private class FakeNative(private val onClose:()->Unit={}) {
        @Volatile var closes=0
        fun close(){onClose();closes++}
    }
    private fun isolated(test:(PrimarySessionAuthority,ScopedIoGuard)->Unit) {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val operation=authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        try{test(authority,ScopedIoGuard(operation,PrimaryScope.WRITE))}finally{operation.close();authority.revoke()}
    }
}
