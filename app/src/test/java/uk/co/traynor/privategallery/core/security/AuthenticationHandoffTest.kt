package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class AuthenticationHandoffTest {
    @Test fun cancellationWipesPreparedKeyBeforeReleaseWorkerRuns() {
        val block=java.util.concurrent.atomic.AtomicBoolean(false)
        val entered=CountDownLatch(1);val resume=CountDownLatch(1)
        val pool=ReleasePool(16,allocateInvocation={action,returned -> ReleaseInvocation({
            if(block.get()) { entered.countDown();check(resume.await(5,TimeUnit.SECONDS)) };action()
        },returned) })
        val a=testPrimaryAuthority()
        a.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible=true }.set(a,pool)
        val attempt=a.beginAuthentication();val bytes=ByteArray(32){9}
        val result=a.prepareAuthentication(attempt,charArrayOf()) { bytes };block.set(true)
        try {
            a.cancelAuthentication(attempt);assertTrue(entered.await(5,TimeUnit.SECONDS))
            assertArrayEquals(ByteArray(32),bytes);assertFalse(result.retirement.isComplete)
        } finally { resume.countDown();result.close();assertTrue(result.retirement.await(5,TimeUnit.SECONDS));a.revoke() }
    }
    @Test fun failedAttemptCreationWipesInputWithoutInvokingFactory() {
        val a=testPrimaryAuthority { error("clock failure") };val pin=charArrayOf('8');var calls=0
        assertThrows(IllegalStateException::class.java) { a.authenticatePin(pin) { calls++;ByteArray(32) } }
        assertEquals(0,calls);assertEquals('\u0000',pin[0])
    }
    @Test fun primaryResultAdmitsOnlyAfterInputCleanupAndConsumesOriginal() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val secret=ByteArray(32){7};val pin=charArrayOf('1')
        val handoff=a.prepareAuthentication(attempt,pin) { secret }
        assertArrayEquals(charArrayOf('\u0000'),pin);assertNull(a.operationOrNull())
        assertTrue(a.completeAuthentication(attempt,handoff));assertTrue(handoff.retirement.await(5,TimeUnit.SECONDS))
        checkNotNull(a.operationOrNull()).use { assertArrayEquals(secret,it.key) }
        a.revoke();assertArrayEquals(ByteArray(32),secret)
    }
    @Test fun revokedProducerCannotWipeBorrowedInputOrPublishLateKey() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val entered=CountDownLatch(1);val finish=CountDownLatch(1)
        val pin=charArrayOf('8');val secret=ByteArray(32){9};val failed=AtomicReference<Throwable?>()
        val producer=Thread { try { a.prepareAuthentication(attempt,pin) {
            entered.countDown();check(finish.await(5,TimeUnit.SECONDS));assertEquals('8',pin[0]);secret
        } } catch(e:Throwable) { failed.set(e) } }
        producer.start()
        try { assertTrue(entered.await(5,TimeUnit.SECONDS));a.revoke();assertFalse(a.cleanupComplete);assertEquals('8',pin[0]) }
        finally { finish.countDown();producer.join(5000) }
        assertFalse(producer.isAlive);assertNotNull(failed.get())
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!a.cleanupComplete&&System.nanoTime()<until)Thread.yield()
        assertTrue(a.cleanupComplete);assertArrayEquals(ByteArray(32),secret);assertEquals('\u0000',pin[0]);assertNull(a.operationOrNull())
    }
    @Test fun obsoletePrimaryResultCannotPromoteIntoReplacementAttempt() {
        val a=testPrimaryAuthority();val old=a.beginAuthentication();val secret=ByteArray(32){2}
        val handoff=a.prepareAuthentication(old,charArrayOf()) { secret };val current=a.beginAuthentication()
        assertFalse(a.completeAuthentication(old,handoff));assertTrue(handoff.retirement.await(5,TimeUnit.SECONDS));assertArrayEquals(ByteArray(32),secret)
        val replacement=a.prepareAuthentication(current,charArrayOf()) { ByteArray(32){3} }
        assertTrue(a.completeAuthentication(current,replacement));assertTrue(replacement.retirement.await(5,TimeUnit.SECONDS));a.revoke()
    }
    @Test fun backgroundInvalidatesPreAuthenticationEvenWithGraceTimeout() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val bytes=ByteArray(32){5}
        val result=a.prepareAuthentication(attempt,charArrayOf()) { bytes }
        a.onBackgrounded(30_000)
        assertFalse(a.completeAuthentication(attempt,result));assertTrue(result.retirement.await(5,TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(32),bytes);assertNull(a.operationOrNull())
    }
    @Test fun partialMetadataAttachmentRetiresWithoutInventingALiveProducer() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val pin=charArrayOf('7')
        val original=AtomicReference<ReleaseReservation>()
        assertThrows(IllegalStateException::class.java) {
            prepareAuthenticationKey(a,attempt,pin,{ _, factory ->
                a.createAttemptOwned(attempt,OwnedResourceManifest.io("result","wrong","input")) {
                    original.set(this.original);factory()
                }
            },{a.checkPreparation(attempt)}) { fail("No Native entry after failed metadata attach");ByteArray(32) }
        }
        assertTrue(original.get().terminalRetirement.await(5,TimeUnit.SECONDS));assertEquals('\u0000',pin[0]);a.revoke()
    }

    @Test fun wrongAttemptCannotRetireReplacementHandoff() {
        val a=testPrimaryAuthority();val old=a.beginAuthentication();val current=a.beginAuthentication()
        val result=a.prepareAuthentication(current,charArrayOf()) { ByteArray(32){6} }
        assertThrows(IllegalStateException::class.java) { a.completeAuthentication(old,result) }
        assertTrue(a.completeAuthentication(current,result));assertTrue(result.retirement.await(5,TimeUnit.SECONDS));a.revoke()
    }
    @Test fun wrongAuthorityCannotRetireAnotherAuthorityHandoff() {
        val a=testPrimaryAuthority();val b=testPrimaryAuthority();val aa=a.beginAuthentication();val bb=b.beginAuthentication()
        val result=b.prepareAuthentication(bb,charArrayOf()) { ByteArray(32){6} }
        assertThrows(IllegalStateException::class.java) { a.completeAuthentication(aa,result) }
        assertTrue(b.completeAuthentication(bb,result));assertTrue(result.retirement.await(5,TimeUnit.SECONDS));a.revoke();b.revoke()
    }

    @Test fun timeoutAfterActualProviderReturnDeniesAndWipesResult() {
        var now=0L;val a=testPrimaryAuthority { now };val attempt=a.beginAuthentication();val bytes=ByteArray(32){4}
        assertThrows(IllegalStateException::class.java) { a.prepareAuthentication(attempt,charArrayOf()) { now=60_000;bytes } }
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!a.cleanupComplete&&System.nanoTime()<until)Thread.yield()
        assertTrue(a.cleanupComplete);assertArrayEquals(ByteArray(32),bytes);assertNull(a.operationOrNull());a.revoke()
    }
    @Test fun failedFinalAdmissionCannotReuseTheOriginalAttempt() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication()
        val held=a.createAttemptOwned(attempt,OwnedResourceManifest.io("held")) { attach("held",AutoCloseable {}) }
        val result=a.prepareAuthentication(attempt,charArrayOf()) { ByteArray(32){4} }
        assertThrows(IllegalStateException::class.java) { a.completeAuthentication(attempt,result) }
        held.close();assertTrue(held.retirement.await(5,TimeUnit.SECONDS));assertTrue(result.retirement.await(5,TimeUnit.SECONDS))
        assertThrows(IllegalStateException::class.java) { a.checkPreparation(attempt) };a.revoke()
    }
    @Test fun cancelledBeforeStartupNeverInvokesFactoryAndWipesInput() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();a.cancelAuthentication(attempt);val pin=charArrayOf('9');var bodies=0
        assertThrows(IllegalStateException::class.java) { a.prepareAuthentication(attempt,pin) { bodies++;ByteArray(32) } }
        assertEquals(0,bodies);assertEquals('\u0000',pin[0]);a.revoke()
    }
    @Test fun capacityExhaustionDeniesBeforeKeyFactory() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();var factories=0
        val held=List(16) { a.createAttemptOwned(attempt,OwnedResourceManifest.io("hold")) { attach("hold",AutoCloseable {}) } }
        try {
            val pin=charArrayOf('2')
            assertThrows(IllegalStateException::class.java) { a.prepareAuthentication(attempt,pin) { factories++;ByteArray(32) } }
            assertEquals(0,factories);assertEquals('\u0000',pin[0])
        } finally { held.forEach { it.close() };held.forEach { assertTrue(it.retirement.await(5,TimeUnit.SECONDS)) };a.revoke() }
    }
    @Test fun providerExceptionRetiresInputAndResultWithoutAdmission() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val pin=charArrayOf('3')
        val failure=java.io.IOException("synthetic provider failure")
        assertSame(failure,assertThrows(java.io.IOException::class.java) { a.prepareAuthentication(attempt,pin) { throw failure } })
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!a.cleanupComplete&&System.nanoTime()<until)Thread.yield()
        assertTrue(a.cleanupComplete);assertEquals('\u0000',pin[0]);assertNull(a.operationOrNull());a.revoke()
    }
    @Test fun failedNativeRetirementCannotBeBypassedWithANewAttempt() {
        val a=testPrimaryAuthority();val old=a.beginAuthentication()
        val held=a.createAttemptOwned(old,OwnedResourceManifest.io("native")) { attach("native",AutoCloseable { error("Synthetic Native cleanup failure") }) }
        held.close();val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(!held.releaseFailed&&System.nanoTime()<until)Thread.yield()
        assertTrue(held.releaseFailed);assertFalse(held.retirement.isComplete)
        val current=a.beginAuthentication();var calls=0
        assertThrows(IllegalStateException::class.java) { a.prepareAuthentication(current,charArrayOf()) { calls++;ByteArray(32) } }
        assertEquals(0,calls);val rejected=ByteArray(32){3}
        assertThrows(IllegalStateException::class.java) { a.open(rejected) };assertArrayEquals(ByteArray(32),rejected);assertFalse(a.cleanupComplete)
    }

    @Test fun arbitraryThreeChildOriginalCannotMasqueradeAsAuthenticationHandoff() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val bytes=ByteArray(32){4}
        val arbitrary=a.createAttemptOwned(attempt,OwnedResourceManifest.io("other","a","b")) {
            val key=attach("other",OriginalAuthenticationKey());key.bind(bytes);key.finishProducer()
            attach("a",AutoCloseable {});attach("b",AutoCloseable {});retireChildren("a","b");key
        }
        val forged=OwnedAuthenticationKey(arbitrary,a,attempt)
        assertThrows(IllegalStateException::class.java) { a.completeAuthentication(attempt,forged) }
        assertTrue(forged.retirement.await(5,TimeUnit.SECONDS));assertArrayEquals(ByteArray(32),bytes);assertNull(a.operationOrNull());a.revoke()
    }
    @Test fun completedFutureCannotDischargeNativeCallbackBeforeItReturns() {
        val a=testPrimaryAuthority();val old=a.beginAuthentication();val entered=CountDownLatch(1);val finish=CountDownLatch(1)
        val future=java.util.concurrent.CompletableFuture<Unit>();val original=a.createAttemptOwned(old,OwnedResourceManifest.io("native")) {
            attach("native",object:AcknowledgedCloseable {
                override fun closeAcknowledged():java.util.concurrent.CompletionStage<Unit> {
                    a.bindingOrNull();future.complete(Unit);entered.countDown();check(finish.await(5,TimeUnit.SECONDS));return future
                }
            })
        }
        val current=a.beginAuthentication()
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertTrue(future.isDone);assertFalse(original.retirement.isComplete);assertFalse(a.cleanupComplete)
            assertThrows(IllegalStateException::class.java) { a.prepareAuthentication(current,charArrayOf()) { fail("Native callback still running");ByteArray(32) } }
            a.cancelAuthentication(old)
        } finally { finish.countDown() }
        assertTrue(original.retirement.await(5,TimeUnit.SECONDS))
        val result=a.prepareAuthentication(current,charArrayOf()) { ByteArray(32){7} };assertTrue(a.completeAuthentication(current,result))
        assertTrue(result.retirement.await(5,TimeUnit.SECONDS));a.revoke()
    }
    @Test fun repeatedAuthenticationCyclesReturnPhysicalCapacity() {
        val a=testPrimaryAuthority()
        repeat(40) {
            val attempt=a.beginAuthentication();val bytes=ByteArray(32){5}
            val result=a.prepareAuthentication(attempt,charArrayOf('4')) { bytes }
            assertTrue(a.completeAuthentication(attempt,result));assertTrue(result.retirement.await(5,TimeUnit.SECONDS))
            a.revoke();assertArrayEquals(ByteArray(32),bytes);assertTrue(a.cleanupComplete)
        }
    }

    @Test fun explicitFacadeCloseCannotInterleaveOriginalValidationAndKeyConsumption() {
        val a=testPrimaryAuthority();val attempt=a.beginAuthentication();val bytes=ByteArray(32){8}
        val result=a.prepareAuthentication(attempt,charArrayOf()) { bytes }
        val owned=result.javaClass.getDeclaredField("owned").apply { isAccessible=true }.get(result) as OwnedResource<*>
        val cell=owned.value
        val cellGate=cell.javaClass.getDeclaredField("gate").apply { isAccessible=true }.get(cell)
        val admitted=AtomicReference<Boolean?>();val failure=AtomicReference<Throwable?>()
        val worker=Thread { try { admitted.set(a.completeAuthentication(attempt,result)) } catch(e:Throwable) { failure.set(e) } }
        val closer=Thread { result.close() }
        try {
            synchronized(cellGate) {
                worker.start();val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(worker.state!=Thread.State.BLOCKED&&System.nanoTime()<until)Thread.yield()
                assertEquals(Thread.State.BLOCKED,worker.state)
                closer.start();val closeUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while(closer.state !in setOf(Thread.State.BLOCKED,Thread.State.TERMINATED)&&System.nanoTime()<closeUntil)Thread.yield()
                assertEquals("Original retirement must serialize with its atomic ownership transfer",Thread.State.BLOCKED,closer.state)
            }
        } finally { worker.join(5000);closer.join(5000);result.close();assertTrue(result.retirement.await(5,TimeUnit.SECONDS));a.revoke() }
        assertFalse(worker.isAlive);assertFalse(closer.isAlive);assertNull(failure.get());assertEquals(true,admitted.get());assertArrayEquals(ByteArray(32),bytes)
    }

}
