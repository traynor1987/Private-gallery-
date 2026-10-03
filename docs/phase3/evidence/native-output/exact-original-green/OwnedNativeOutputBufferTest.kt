package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class OwnedNativeOutputBufferTest {
    @Test fun callbackAndDependentReadAtRunWithoutTheSampleReaderGate() = isolated { _, guard ->
        val buffer=fundedBuffer(guard,16)
        val gate=buffer.javaClass.getDeclaredField("gate").apply {isAccessible=true}.get(buffer)
        try {
            assertEquals(1,buffer.useBytes { bytes ->
                assertFalse("JNI must run outside sample reader gate",Thread.holdsLock(gate))
                val completed=CountDownLatch(1)
                val callback=Thread {synchronized(gate) {completed.countDown()} }
                callback.start();assertTrue(completed.await(5,TimeUnit.SECONDS));callback.join(5000);bytes[0]=42;1
            })
        } finally {buffer.close()}
    }
    @Test fun concurrentNativeUseRejectsBeforeItsCallbackInsteadOfWaitingBehindFirst() = isolated { _,guard ->
        val buffer=fundedBuffer(guard,16);val entered=CountDownLatch(1);val finish=CountDownLatch(1);val firstFailure=AtomicReference<Throwable?>();val secondFailure=AtomicReference<Throwable?>();var secondEntered=false
        val first=Thread {try {buffer.useBytes {entered.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));1}}catch(t:Throwable){firstFailure.set(t)}}
        val second=Thread {try {buffer.useBytes {secondEntered=true;1}}catch(t:Throwable){secondFailure.set(t)}}
        try {
            first.start();assertTrue(entered.await(5,TimeUnit.SECONDS));second.start();second.join(1000)
            assertFalse("Second use must reject immediately",second.isAlive);assertTrue(secondFailure.get() is IllegalStateException);assertFalse(secondEntered)
        } finally {finish.countDown();first.join(5000);second.join(5000);buffer.close()}
        assertNull(firstFailure.get())
    }
    @Test fun fundedWipeWaitsForActualInvocationReturnWhileIndependentUnblockerRuns() = isolated {authority,guard ->
        val entered=CountDownLatch(1);val returned=CountDownLatch(1);val wipeStarted=CountDownLatch(1);val unblocker=CountDownLatch(1);val finish=CountDownLatch(1);val bytes=AtomicReference<ByteArray?>();val failure=AtomicReference<Throwable?>()
        val owned=guard.createOwned(OwnedResourceManifest.io("root","sample","unblocker")) {
            val root=attach("root",AutoCloseable {})
            create("sample",{it:OwnedNativeOutputBuffer -> wipeStarted.countDown();it.close()}) {OwnedNativeOutputBuffer(guard,original,1,16)}.also {sample ->
                create("unblocker",{_:Any -> unblocker.countDown()}) {Any()}
                val worker=Thread {try {sample.value.useBytes {data -> data.fill(9);bytes.set(data);entered.countDown();assertTrue(unblocker.await(5,TimeUnit.SECONDS));assertTrue(finish.await(5,TimeUnit.SECONDS));data[0]=42;1}}catch(t:Throwable){failure.set(t)}finally{returned.countDown()}}
                worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS))
            }
            root
        }
        try {
            authority.revoke();assertTrue(wipeStarted.await(5,TimeUnit.SECONDS));assertTrue(unblocker.await(5,TimeUnit.SECONDS));assertFalse(owned.retirement.isComplete)
            assertTrue(checkNotNull(bytes.get()).all {it==9.toByte()});finish.countDown();assertTrue(returned.await(5,TimeUnit.SECONDS));assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertArrayEquals(ByteArray(16),bytes.get());assertNotNull(failure.get())
        } finally {finish.countDown();owned.close()}
    }
    @Test fun callbackFailureReleasesTheActiveUseAndLaterWipeSucceeds() = isolated {_,guard ->
        val buffer=fundedBuffer(guard,16);var actual:ByteArray?=null
        assertThrows(IOException::class.java) {buffer.useBytes {actual=it;it.fill(9);throw IOException("synthetic Native read failure")}}
        buffer.close();assertArrayEquals(ByteArray(16),actual)
    }
    @Test fun revokedInvocationCannotReturnSuccessAndWipesOnlyAfterItReturns() = isolated {authority,guard ->
        val buffer=fundedBuffer(guard,16);var actual:ByteArray?=null
        assertThrows(IllegalStateException::class.java) {buffer.useBytes {actual=it;it.fill(9);authority.revoke();1}}
        buffer.close();assertArrayEquals(ByteArray(16),actual)
    }
    @Test fun closedBufferNeverInvokesAnotherNativeCallback() = isolated {_,guard ->
        val buffer=fundedBuffer(guard,16);buffer.close();var invoked=false
        assertThrows(IllegalStateException::class.java) {buffer.useBytes {invoked=true;1}};assertFalse(invoked)
    }
    @Test fun reentrantUseDeniesBeforeSecondCallback() = isolated {_,guard ->
        val buffer=fundedBuffer(guard,16);var second=false
        try {assertEquals(1,buffer.useBytes {assertThrows(IllegalStateException::class.java){buffer.useBytes {second=true;1}};1});assertFalse(second)}finally{buffer.close()}
    }
    @Test fun selfCloseFromTheActualInvocationDeniesWithoutDeadlock() = isolated {_,guard ->
        val buffer=fundedBuffer(guard,16)
        try {assertEquals(1,buffer.useBytes {assertThrows(IllegalStateException::class.java){buffer.close()};1})}finally{buffer.close()}
    }
    @Test fun invalidArraySizeDeniesBeforeAllocation() = isolated {_,guard ->
        for(size in listOf(-1,0,256*1024+1)) assertThrows(IllegalArgumentException::class.java){fundedBuffer(guard,size)}
    }
    @Test fun interruptedFundedWipeRemainsFailedAndCannotAdmitFreshAuthentication() = isolated {authority,guard ->
        val entered=CountDownLatch(1);val finish=CountDownLatch(1);val wipeReturned=CountDownLatch(1);val completed=CountDownLatch(1);val actual=AtomicReference<ByteArray?>();val failure=AtomicReference<Throwable?>()
        val owned=guard.createOwned(OwnedResourceManifest.io("root","sample")) {
            val root=attach("root",AutoCloseable {})
            val sample=create("sample",{it:OwnedNativeOutputBuffer ->
                Thread.currentThread().interrupt()
                try {it.close()}finally{wipeReturned.countDown()}
            }) {OwnedNativeOutputBuffer(guard,original,1,16)}
            Thread {try {sample.value.useBytes {bytes -> bytes.fill(9);actual.set(bytes);entered.countDown();assertTrue(finish.await(5,TimeUnit.SECONDS));1}}catch(t:Throwable){failure.set(t)}finally{completed.countDown()}}.start()
            assertTrue(entered.await(5,TimeUnit.SECONDS));root
        }
        try {
            authority.revoke();assertTrue(wipeReturned.await(5,TimeUnit.SECONDS));assertTrue(checkNotNull(actual.get()).all{it==9.toByte()})
            finish.countDown();assertTrue(completed.await(5,TimeUnit.SECONDS))
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while(!owned.releaseFailed && System.nanoTime()<deadline) Thread.yield()
            assertTrue(owned.releaseFailed);assertFalse(owned.retirement.isComplete);assertFalse(authority.cleanupComplete);assertNotNull(failure.get())
            val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
        }finally{finish.countDown();completed.await(5,TimeUnit.SECONDS);actual.get()?.fill(0)}
    }
    @Test fun normalOwnerRetirementDeniesUseBeforeDelayedWipeWorkerEntersClose() = isolated {_,guard ->
        val workerEntered=CountDownLatch(1);val allowWipe=CountDownLatch(1)
        lateinit var buffer:OwnedNativeOutputBuffer
        val owned=guard.createOwned(OwnedResourceManifest.io("root","sample")) {
            val root=attach("root",AutoCloseable {})
            buffer=create("sample",{it:OwnedNativeOutputBuffer ->
                workerEntered.countDown();assertTrue(allowWipe.await(5,TimeUnit.SECONDS));it.close()
            }) {OwnedNativeOutputBuffer(guard,original,1,16)}.value
            root
        }
        try {
            owned.close();assertTrue(workerEntered.await(5,TimeUnit.SECONDS));guard.check()
            var invoked=false
            assertThrows(IllegalStateException::class.java){buffer.useBytes {invoked=true;1}}
            assertFalse(invoked);assertFalse(owned.retirement.isComplete)
        }finally{allowWipe.countDown();owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
    }
    private fun fundedBuffer(guard:ScopedIoGuard,size:Int):OwnedNativeOutputBuffer {
        lateinit var buffer:OwnedNativeOutputBuffer
        guard.createOwned(OwnedResourceManifest.io("root","sample")) {
            val root=attach("root",AutoCloseable {})
            buffer=nativeOutputBuffer("sample",guard,size).value
            root
        }
        return buffer
    }
    private fun isolated(test:(PrimarySessionAuthority,ScopedIoGuard)->Unit) {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try {test(authority,ScopedIoGuard(op,PrimaryScope.WRITE))}finally{op.close();authority.revoke()}
    }
}
