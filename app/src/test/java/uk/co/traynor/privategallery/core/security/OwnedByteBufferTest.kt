package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class OwnedByteBufferTest {
    @Test fun normalRetirementWipesTheExactActualArrayBeforeAcknowledgement() = isolated { _, guard ->
        val root = buffer(guard)
        lateinit var actual: ByteArray
        assertTrue(root.value.value.useBytes { actual = it; it.fill(7); true })
        guard.retire(root)
        assertTrue(root.retirement.isComplete)
        assertTrue(actual.all { it == 0.toByte() })
    }
    @Test fun retiredBufferCannotInvokeAnotherConsumerWithStillLiveLease() = isolated { _, guard ->
        val root = buffer(guard); guard.retire(root); guard.check()
        var calls = 0
        assertThrows(IllegalStateException::class.java) { root.value.value.useBytes { calls++;true } }
        assertEquals(0,calls)
    }
    @Test fun originalRevocationInsideConsumerDeniesResultAndWipesAfterReaderReturns() = isolated { authority, guard ->
        val root = buffer(guard); lateinit var actual: ByteArray
        assertThrows(IllegalStateException::class.java) {
            root.value.value.useBytes { actual = it; it.fill(9); authority.revoke(); true }
        }
        guard.retire(root)
        assertTrue(actual.all { it == 0.toByte() }); assertTrue(authority.cleanupComplete)
    }
    @Test fun invalidBufferBoundRetiresOriginalWithoutCreatingAnActualArray() = isolated { authority, guard ->
        for (size in listOf(-1,0,64 * 1024 + 1)) {
            assertThrows(IllegalArgumentException::class.java) { buffer(guard,size) }
        }
        authority.revoke()
        awaitCleanup(authority)
    }
    @Test fun closedOriginalDeniesBufferFactoryBeforeConstruction() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull(setOf(PrimaryScope.READ))!!;val guard = ScopedIoGuard(operation,PrimaryScope.READ)
        operation.close(); var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                guard.createOwned(OwnedResourceManifest.io("buffer")) {
                    create("buffer", { actual: OwnedByteBuffer -> actual.close() }) { calls++;OwnedByteBuffer(guard,this.original,0,16) }
                }
            }
            assertEquals(0,calls)
        } finally { authority.revoke() }
    }
    @Test fun consumerFailureStillWipesActualArrayUnderOriginalNormalRetirement() = isolated { _, guard ->
        val root = buffer(guard); lateinit var actual: ByteArray
        try {
            assertThrows(java.io.IOException::class.java) {
                root.value.value.useBytes { actual = it; it.fill(5); throw java.io.IOException("synthetic reader failure") }
            }
        } finally { guard.retire(root) }
        assertTrue(actual.all { it == 0.toByte() }); assertTrue(root.retirement.isComplete)
    }
    @Test fun retiredOriginalDeniesBorrowBeforeDelayedFundedWipeStarts() {
        val entered=java.util.concurrent.CountDownLatch(1);val wipe=java.util.concurrent.CountDownLatch(1)
        val authority=PrimarySessionAuthority().also{value->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16,allocateInvocation={action,returned->
                ReleaseInvocation({entered.countDown();check(wipe.await(10,java.util.concurrent.TimeUnit.SECONDS));action()},returned)
            }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        authority.open(ByteArray(32));val operation=authority.operationOrNull(setOf(PrimaryScope.READ))!!;val guard=ScopedIoGuard(operation,PrimaryScope.READ)
        val root=buffer(guard);var calls=0
        try {
            root.close();assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));guard.check()
            assertFalse(root.retirement.isComplete)
            assertThrows(IllegalStateException::class.java){root.value.value.useBytes{calls++;true}}
            assertEquals(0,calls)
        }finally{wipe.countDown();guard.retire(root);operation.close();authority.revoke()}
    }
    @Test fun admittedFailureClearsWholeOriginalBeforeNormalRetirement()=isolated {_,guard->
        val root=buffer(guard);lateinit var actual:ByteArray;val failure=java.io.IOException("public injected failure")
        try {
            val caught=assertThrows(java.io.IOException::class.java){root.value.value.useBytes{actual=it;it.fill(7);throw failure}}
            assertSame(failure,caught);assertArrayEquals(ByteArray(32),actual);assertFalse(root.retirement.isComplete)
        }finally{guard.retire(root)}
    }
    @Test fun reentrantBorrowRejectsWithoutWipingActiveOriginal()=isolated {_,guard->
        val root=buffer(guard);var inner=0
        try {
            assertTrue(root.value.value.useBytes{bytes->
                bytes.fill(7)
                assertThrows(IllegalStateException::class.java){root.value.value.useBytes{inner++;true}}
                assertEquals(0,inner);assertArrayEquals(ByteArray(32){7},bytes);true
            })
        }finally{guard.retire(root)}
    }
    @Test fun foreignGuardAndWrongChildDenyBeforeBoundedArrayConstruction() {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val first=authority.operationOrNull(setOf(PrimaryScope.READ))!!;val second=first.fork()
        val guard=ScopedIoGuard(first,PrimaryScope.READ);val foreign=ScopedIoGuard(second,PrimaryScope.READ)
        try {
            for(mode in 0..1){var failed:ReleaseReservation?=null
                assertThrows(IllegalStateException::class.java){guard.createOwned(OwnedResourceManifest.io("buffer")){
                    failed=this.original
                    create("buffer",{value:OwnedByteBuffer->value.close()}){OwnedByteBuffer(if(mode==0)foreign else guard,this.original,if(mode==0)0 else 1,32)}
                }}
                guard.retire(checkNotNull(failed));assertTrue(authority.cleanupComplete);guard.check();foreign.check()
            }
        }finally{first.close();second.close();authority.revoke()}
    }
    @Test fun activeNormalRetirementDeniesResultAndClearsBeforeDelayedFundedWipe() {
        val wipeEntered=java.util.concurrent.CountDownLatch(1);val allowWipe=java.util.concurrent.CountDownLatch(1)
        val authority=PrimarySessionAuthority().also{value->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16,allocateInvocation={action,returned->
                ReleaseInvocation({wipeEntered.countDown();check(allowWipe.await(10,java.util.concurrent.TimeUnit.SECONDS));action()},returned)
            }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.READ))!!;val guard=ScopedIoGuard(op,PrimaryScope.READ);val root=buffer(guard)
        val entered=java.util.concurrent.CountDownLatch(1);val finish=java.util.concurrent.CountDownLatch(1);val returned=java.util.concurrent.CountDownLatch(1)
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable>();val reachedCandidate=java.util.concurrent.atomic.AtomicBoolean();lateinit var bytes:ByteArray
        val reader=Thread{try{root.value.value.useBytes{bytes=it;it.fill(7);entered.countDown();check(finish.await(10,java.util.concurrent.TimeUnit.SECONDS));reachedCandidate.set(true);true}}
            catch(caught:Throwable){failure.set(caught)}finally{returned.countDown()}}
        try {
            reader.start();assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));root.close();assertTrue(wipeEntered.await(5,java.util.concurrent.TimeUnit.SECONDS));guard.check()
            assertArrayEquals(ByteArray(32){7},bytes);finish.countDown();assertTrue(returned.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("Callback reached its candidate return before postflight denial",reachedCandidate.get());assertTrue(failure.get() is IllegalStateException);assertArrayEquals(ByteArray(32),bytes);assertFalse(root.retirement.isComplete);assertFalse(authority.cleanupComplete)
        }finally{finish.countDown();allowWipe.countDown();reader.join(5_000);guard.retire(root);op.close();authority.revoke()}
    }
    @Test fun callbackGateIsFreeAndConcurrentRejectedBorrowCannotWipeActiveArray()=isolated {_,guard->
        val root=buffer(guard);val entered=java.util.concurrent.CountDownLatch(1);val finish=java.util.concurrent.CountDownLatch(1);lateinit var bytes:ByteArray
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable>();val reader=Thread{try{root.value.value.useBytes{bytes=it;it.fill(7);entered.countDown();check(finish.await(10,java.util.concurrent.TimeUnit.SECONDS));true}}catch(caught:Throwable){failure.set(caught)}}
        val rejected=java.util.concurrent.CountDownLatch(1);val borrowed=java.util.concurrent.atomic.AtomicInteger()
        var second:Thread?=null;var gateThread:Thread?=null
        try {
            reader.start();assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val gate=root.value.value.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(root.value.value)
            val free=java.util.concurrent.CountDownLatch(1);gateThread=Thread{synchronized(gate){free.countDown()}}.also{it.start()};assertTrue(free.await(2,java.util.concurrent.TimeUnit.SECONDS))
            second=Thread{try{root.value.value.useBytes{borrowed.incrementAndGet();true}}catch(_:IllegalStateException){rejected.countDown()}}.also{it.start()}
            assertTrue("Rejected borrower must not wait for active Native return",rejected.await(2,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,borrowed.get());assertArrayEquals(ByteArray(32){7},bytes)
        }finally{finish.countDown();reader.join(5_000);second?.join(5_000);gateThread?.join(5_000);guard.retire(root)}
        assertNull(failure.get());assertArrayEquals(ByteArray(32),bytes)
    }
    @Test fun selfCloseRejectsBeforeChangingActiveBytesOrRetirement()=isolated {_,guard->
        val root=buffer(guard)
        try{assertTrue(root.value.value.useBytes{bytes->bytes.fill(7);assertThrows(IllegalStateException::class.java){root.value.value.close()};assertArrayEquals(ByteArray(32){7},bytes);true});assertFalse(root.retirement.isComplete)}
        finally{guard.retire(root)}
    }
    @Test fun admittedErrorRetainsIdentityAndClearsWholeOriginal()=isolated {_,guard->
        val root=buffer(guard);val failure=object:AssertionError("public injected Error"){val marker=Any()};lateinit var bytes:ByteArray
        try{val caught=assertThrows(failure.javaClass){root.value.value.useBytes{bytes=it;it.fill(9);throw failure}};assertSame(failure,caught);assertArrayEquals(ByteArray(32),bytes);assertFalse(root.retirement.isComplete)}
        finally{guard.retire(root)}
    }
    @Test fun interruptedFundedWipeRemainsFailedAfterActualReaderClearsOriginal() {
        val worker=java.util.concurrent.atomic.AtomicReference<Thread>();val returned=java.util.concurrent.CountDownLatch(1)
        val authority=PrimarySessionAuthority().also{value->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16,allocateInvocation={action,after->
                ReleaseInvocation({worker.set(Thread.currentThread());action()},{try{after()}finally{returned.countDown()}})
            }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.READ))!!;val guard=ScopedIoGuard(op,PrimaryScope.READ);val root=buffer(guard)
        val entered=java.util.concurrent.CountDownLatch(1);val finish=java.util.concurrent.CountDownLatch(1);val failure=java.util.concurrent.atomic.AtomicReference<Throwable>();lateinit var bytes:ByteArray
        val reader=Thread{try{root.value.value.useBytes{bytes=it;it.fill(7);entered.countDown();check(finish.await(10,java.util.concurrent.TimeUnit.SECONDS));true}}catch(caught:Throwable){failure.set(caught)}}
        try{
            reader.start();assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));root.close()
            val until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while(worker.get()?.state!=Thread.State.WAITING){check(System.nanoTime()<until);Thread.yield()}
            worker.get().interrupt();assertTrue(returned.await(5,java.util.concurrent.TimeUnit.SECONDS));finish.countDown();reader.join(5_000)
            assertFalse(reader.isAlive);assertTrue(failure.get() is IllegalStateException);assertArrayEquals(ByteArray(32),bytes)
            assertTrue(root.releaseFailed);assertFalse(root.retirement.isComplete);assertFalse(authority.cleanupComplete)
            val replacement=ByteArray(32){8};assertThrows(IllegalStateException::class.java){authority.open(replacement)};assertArrayEquals(ByteArray(32),replacement)
        }finally{finish.countDown();reader.join(5_000);op.close();authority.revoke()}
    }
    private fun buffer(guard: ScopedIoGuard,size: Int = 32) = guard.createOwned(OwnedResourceManifest.io("buffer")) {
        create("buffer", { actual: OwnedByteBuffer -> actual.close() }) { OwnedByteBuffer(guard,this.original,0,size) }
    }
    private fun isolated(test: (PrimarySessionAuthority,ScopedIoGuard) -> Unit) {
        val authority = testPrimaryAuthority();authority.open(ByteArray(32) { 5 })
        val operation = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        try { test(authority,ScopedIoGuard(operation,PrimaryScope.READ)) }
        finally { operation.close();authority.revoke() }
    }
    private fun awaitCleanup(authority: PrimarySessionAuthority) {
        val deadline = System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
        assertTrue(authority.cleanupComplete)
    }
}
