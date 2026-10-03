package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Phase3AiSetupOwnershipTest {
    @Test fun wholeCandidateAndJobManifestMustFitBeforeProducerStarts()=isolated {authority,owner->
        val holds=(1..15).map{owner.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}}
        val calls=AtomicInteger()
        try {
            try{launchAiSetupWork(owner,unparented,"public-fixture"){calls.incrementAndGet()};fail("Incomplete candidate/Job manifest admitted")}
            catch(_:IllegalStateException){}
            assertEquals(0,calls.get());owner.checkValid()
        }finally{holds.forEach{it.close()}}
    }
    @Test fun successfulProducerWipesExactCandidateAndKeepsDialogOwnerLive()=isolated {authority,owner->
        var candidate:ByteArray?=null
        val job=launchAiSetupWork(owner,unparented," public-fixture "){bytes->candidate=bytes;assertArrayEquals("public-fixture".toByteArray(),bytes)}
        runBlocking{withTimeout(5_000){job.join()}}
        assertArrayEquals(ByteArray(14),candidate);owner.checkValid()
        await{authority.cleanupComplete}
    }
    @Test fun stoppedScopeNeverStartsProducer()=isolated {authority,owner->
        val parent=Job();parent.cancel();val calls=AtomicInteger()
        try{launchAiSetupWork(owner,CoroutineScope(parent+Dispatchers.IO),"public-fixture"){calls.incrementAndGet()}}
        catch(_:CancellationException){}
        assertEquals(0,calls.get());await{authority.cleanupComplete};owner.checkValid()
    }
    @Test fun revokeKeepsBorrowedCandidateChargedUntilActualProducerReturn()=isolated {authority,owner->
        val entered=CountDownLatch(1);val finish=CountDownLatch(1);var candidate:ByteArray?=null
        val job=launchAiSetupWork(owner,unparented,"public-fixture"){bytes->
            withContext(Dispatchers.IO){candidate=bytes;entered.countDown();check(finish.await(10,TimeUnit.SECONDS))}
        }
        try{
            assertTrue(entered.await(5,TimeUnit.SECONDS));authority.revoke()
            assertFalse(job.isCompleted);assertFalse(authority.cleanupComplete)
            assertArrayEquals("public-fixture".toByteArray(),candidate)
            val replacement=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(replacement)};assertArrayEquals(ByteArray(32),replacement)
        }finally{finish.countDown();runBlocking{withTimeout(5_000){job.join()}}}
        await{authority.cleanupComplete};assertArrayEquals(ByteArray(14),candidate)
    }
    @Test fun malformedOrOversizedCredentialDeniesBeforeProducerAndLeavesOwnerLive()=isolated {authority,owner->
        val calls=AtomicInteger()
        for(text in listOf("x".repeat(8193),"public fixture","publicéfixture","public\u0000fixture")) {
            assertThrows(AiEditFailure::class.java){launchAiSetupWork(owner,unparented,text){calls.incrementAndGet()}}
            await{authority.cleanupComplete};owner.checkValid()
        }
        assertEquals(0,calls.get())
    }
    @Test fun exactAsciiBoundAndBlankSavedTokenRouteRemainSupported()=isolated {authority,owner->
        var actual:ByteArray?=null
        val full=launchAiSetupWork(owner,unparented,"x".repeat(8192)){bytes->actual=bytes;assertEquals(8192,bytes!!.size)}
        runBlocking{withTimeout(5_000){full.join()}};await{authority.cleanupComplete};assertArrayEquals(ByteArray(8192),actual)
        for(text in listOf<String?>(null," \t\n ")) {
            val saved=launchAiSetupWork(owner,unparented,text){bytes->assertNull(bytes)}
            runBlocking{withTimeout(5_000){saved.join()}};await{authority.cleanupComplete};owner.checkValid()
        }
    }
    @Test fun insufficientCredentialScopeDeniesBeforeProducer() {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val owner=authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS))!!
        val calls=AtomicInteger()
        try{assertThrows(IllegalStateException::class.java){launchAiSetupWork(owner,unparented,"public-fixture"){calls.incrementAndGet()}};assertEquals(0,calls.get());assertTrue(authority.cleanupComplete)}
        finally{owner.close();authority.revoke()}
    }
    @Test fun consumerFailureClearsOriginalButFailedJobRemainsCharged()=genuineFailure(
        object:AssertionError("public-fixture failure"){val marker=Any()})
    @Test fun consumerIllegalStateFailureKeepsExactThrowableAndFailedJobCharged()=genuineFailure(
        object:IllegalStateException("public-fixture consumer failure"){val marker=Any()})
    private fun genuineFailure(failure:Throwable)=isolated {authority,owner->
        var candidate:ByteArray?=null
        val seen=CountDownLatch(1);val scope=object:CoroutineScope{override val coroutineContext=Dispatchers.IO+CoroutineExceptionHandler{_,caught->assertSame(failure,caught);seen.countDown()}}
        val job=launchAiSetupWork(owner,scope,"public-fixture"){bytes->candidate=bytes;throw failure}
        runBlocking{withTimeout(5_000){job.join()}};assertTrue(seen.await(5,TimeUnit.SECONDS));assertArrayEquals(ByteArray(14),candidate)
        assertFalse(authority.cleanupComplete);owner.checkValid()
        val replacement=ByteArray(32){9};assertThrows(IllegalStateException::class.java){authority.open(replacement)};assertArrayEquals(ByteArray(32),replacement)
    }
    @Test fun staleQueuedProducerAdmissionIsNeutralWhileOriginalCancelWorkerIsHeld() {
        staleAdmission(false)
    }
    @Test fun staleProviderReturnIsNeutralWhileOriginalCancelWorkerIsHeld() {
        staleAdmission(true)
    }
    private fun staleAdmission(afterProvider:Boolean) {
        val cancelEntered=CountDownLatch(1);val allowCancel=CountDownLatch(1);val releaseIndex=AtomicInteger()
        val authority=PrimarySessionAuthority().also{value->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16,allocateInvocation={action,returned->
                val index=releaseIndex.incrementAndGet()
                ReleaseInvocation({if(index==2){cancelEntered.countDown();check(allowCancel.await(10,TimeUnit.SECONDS))};action()},returned)
            }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        }
        authority.open(ByteArray(32));val owner=authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS,PrimaryScope.REMOTE_AI_EGRESS))!!
        val queued=java.util.concurrent.ArrayBlockingQueue<Runnable>(1)
        val dispatcher=object:CoroutineDispatcher(){override fun dispatch(context:kotlin.coroutines.CoroutineContext,block:Runnable){check(queued.offer(block))}}
        val calls=AtomicInteger();val providerEntered=CountDownLatch(1);val allowReturn=CountDownLatch(1)
        val uncaught=java.util.concurrent.ConcurrentLinkedQueue<Throwable>();var candidate:ByteArray?=null
        val scope=object:CoroutineScope{override val coroutineContext=(if(afterProvider)Dispatchers.IO else dispatcher)+CoroutineExceptionHandler{_,failure->uncaught.add(failure)}}
        val job=launchAiSetupWork(owner,scope,"public-fixture"){bytes->candidate=bytes;calls.incrementAndGet();providerEntered.countDown();if(afterProvider)check(allowReturn.await(10,TimeUnit.SECONDS))}
        try {
            if(afterProvider)assertTrue(providerEntered.await(5,TimeUnit.SECONDS))
            authority.revoke();assertTrue(cancelEntered.await(5,TimeUnit.SECONDS));assertTrue(job.isActive);assertFalse(authority.cleanupComplete)
            if(afterProvider)allowReturn.countDown() else checkNotNull(queued.poll(5,TimeUnit.SECONDS)).run()
            runBlocking{withTimeout(5_000){job.join()}}
            assertEquals(if(afterProvider)1 else 0,calls.get());assertTrue("Stale admission escaped as an uncaught producer failure",uncaught.isEmpty())
            assertTrue(job.isCancelled);assertFalse("Actual cancel return still required after Job completion",authority.cleanupComplete);if(afterProvider)assertArrayEquals(ByteArray(14),candidate)
        }finally{allowReturn.countDown();allowCancel.countDown();owner.close();authority.revoke()}
        await{authority.cleanupComplete}
    }
    @Test fun combinedSetupAndVerificationSevenSlotFixtureFitsBeforeNativeAcquisition()=isolated {authority,owner->
        combinedVerification(authority,owner,9,true)
    }
    @Test fun combinedSetupAndVerificationSixSlotRemainderDeniesBeforeNativeAcquisition()=isolated {authority,owner->
        combinedVerification(authority,owner,10,false)
    }
    private fun combinedVerification(authority:PrimarySessionAuthority,owner:PrimaryOperation,held:Int,expected:Boolean) {
        val holders=(1..held).map{owner.createOwned(OwnedResourceManifest.io("holder")){attach("holder",AutoCloseable{})}}
        val calls=AtomicInteger();val consumed=AtomicInteger();val rejected=AtomicInteger();var candidate:ByteArray?=null;var response:ByteArray?=null
        val guard=ScopedIoGuard(owner,PrimaryScope.REMOTE_AI_EGRESS)
        try {
            val job=launchAiSetupWork(owner,unparented,"public-fixture"){bytes->
                candidate=bytes
                try{withContext(PrimaryIoContext(guard)){
                    PrivateAiHttpTransport{uri->calls.incrementAndGet();object:javax.net.ssl.HttpsURLConnection(uri.toURL()){
                        override fun getInputStream()=java.io.ByteArrayInputStream(byteArrayOf(1))
                        override fun getResponseCode()=200
                        override fun getContentLengthLong()=-1L
                        override fun getContentType()="application/json"
                        override fun disconnect()=Unit
                        override fun connect()=Unit
                        override fun usingProxy()=false
                        override fun getCipherSuite()="public-fixture"
                        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
                        override fun getServerCertificates():Array<java.security.cert.Certificate> =emptyArray()
                    }}.consumeVerification(AiHttpRequest("GET","https://api.openai.com/v1/models/gpt-image-2.5-flare",emptyMap(),maxResponseBytes=8)){
                        response=it.bytes;assertArrayEquals(byteArrayOf(1),it.bytes);consumed.incrementAndGet()
                    }
                }}catch(_:IllegalStateException){rejected.incrementAndGet()}
            }
            runBlocking{withTimeout(5_000){job.join()}}
            assertEquals(if(expected)1 else 0,calls.get());assertEquals(if(expected)1 else 0,consumed.get());assertEquals(if(expected)0 else 1,rejected.get())
            assertArrayEquals(ByteArray(14),candidate);if(expected)assertArrayEquals(ByteArray(1),response);owner.checkValid()
        }finally{holders.forEach{guard.retire(it)}}
        await{authority.cleanupComplete}
    }
    private val unparented=object:CoroutineScope{override val coroutineContext=Dispatchers.IO}
    private fun await(condition:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition()){check(System.nanoTime()<until);Thread.yield()}}
    private fun isolated(body:(PrimarySessionAuthority,PrimaryOperation)->Unit){
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val owner=authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS,PrimaryScope.REMOTE_AI_EGRESS))!!
        try{body(authority,owner)}finally{owner.close();authority.revoke()}
    }
}
