package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

class Phase3PredictionStatusOwnershipTest {
    private val request=AiHttpRequest("GET","https://api.replicate.com/v1/predictions/PublicId42",emptyMap(),maxResponseBytes=32)
    private fun fixture():Pair<PrimarySessionAuthority,PrimaryOperation> {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32))
        return authority to authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS))!!
    }
    @Test fun successfulConsumerSeesOnlyExactBytesAfterNativeRetirementThenBothArraysClear()=runBlocking {
        val (authority,operation)=fixture();val native=FakeConnection(byteArrayOf(1,2,3))
        var result:ByteArray?=null
        try {withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))) {
            PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request) {response->
                assertEquals(200,response.status);assertArrayEquals(byteArrayOf(1,2,3),response.bytes)
                assertEquals(1,native.disconnected.get());assertEquals(1,native.closed.get())
                assertTrue(checkNotNull(native.actualWorkspace).all{it==0.toByte()});result=response.bytes
            }
        };assertTrue(checkNotNull(result).all{it==0.toByte()});assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun insufficientCompleteManifestNeverCreatesConnectionOrInvokesConsumer()=runBlocking {
        val (authority,operation)=fixture();val guard=ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS)
        val blockers=(1..12).map{guard.createOwned(OwnedResourceManifest.io("blocker")){attach("blocker",AutoCloseable{})}}
        var factories=0;var consumed=false
        try {
            try {withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport {factories++;FakeConnection(byteArrayOf(1))}.consumePredictionStatus("PublicId42",request){consumed=true}};fail()}
            catch(_:IllegalStateException){}
            assertEquals(0,factories);assertFalse(consumed)
        }finally{blockers.forEach{guard.retire(it)};operation.close();authority.revoke()}
    }
    @Test fun oversizedUnknownLengthResponseNeverReachesConsumerAndClearsWorkspace()=runBlocking {
        val (authority,operation)=fixture();val native=FakeConnection(ByteArray(33){9});var consumed=false
        try {
            try {withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){consumed=true}};fail()}
            catch(_:AiEditFailure){}
            assertFalse(consumed);assertTrue(checkNotNull(native.actualWorkspace).all{it==0.toByte()})
            assertEquals(1,native.disconnected.get());assertEquals(1,native.closed.get());assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun zeroCountReadUsesBoundedScalarProgressAndReturnsExactBytes()=runBlocking {
        val (authority,operation)=fixture();val native=object:FakeConnection(byteArrayOf(7)) {
            override fun getInputStream():InputStream=object:InputStream(){
                var emitted=false
                override fun read(b:ByteArray,off:Int,len:Int):Int {actualWorkspace=b;return if(emitted)-1 else 0}
                override fun read():Int=if(emitted)-1 else 7.also{emitted=true}
                override fun close(){closed.incrementAndGet()}
            }
        }
        try {withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){assertArrayEquals(byteArrayOf(7),it.bytes)}}
            assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun consumerFailureKeepsIdentityAndRetiresExactResult()=runBlocking {
        val (authority,operation)=fixture();val native=FakeConnection(byteArrayOf(3));val failure=object:AssertionError("public injected parser failure"){val identityMarker=Any()}
        var result:ByteArray?=null
        try {
            try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){result=it.bytes;throw failure}};fail()}
            catch(actual:AssertionError){assertSame(failure,actual)}
            assertTrue(checkNotNull(result).all{it==0.toByte()});assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun errorStatusDoesNotOpenOrRetainProviderErrorBody()=runBlocking {
        val (authority,operation)=fixture();val native=FakeConnection(byteArrayOf(9)).apply{code=401}
        try {withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){assertEquals(401,it.status);assertNull(it.contentType);assertEquals(0,it.bytes.size)}}
            assertEquals(0,native.opened.get());assertEquals(1,native.disconnected.get());assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun coroutineCancellationDispatchesIndependentDisconnectToUnblockActualRead()=runBlocking {
        val (authority,operation)=fixture();val entered=CountDownLatch(1);val disconnected=CountDownLatch(1);var consumed=false
        val readUnblockedByDisconnect=java.util.concurrent.atomic.AtomicBoolean(false)
        val native=object:FakeConnection(byteArrayOf(1)) {
            override fun disconnect(){super.disconnect();disconnected.countDown()}
            override fun getInputStream():InputStream=object:InputStream(){
                override fun read()=error("bulk read required")
                override fun read(b:ByteArray,off:Int,len:Int):Int {actualWorkspace=b;b[off]=9;entered.countDown();check(disconnected.await(5,TimeUnit.SECONDS));readUnblockedByDisconnect.set(true);return 1}
                override fun close(){closed.incrementAndGet()}
            }
        }
        try {
            val worker=launch(Dispatchers.IO+PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){consumed=true}}
            assertTrue(entered.await(5,TimeUnit.SECONDS));worker.cancel();withTimeout(5000){worker.join()}
            assertFalse(consumed);assertTrue(readUnblockedByDisconnect.get());assertEquals(1,native.disconnected.get());assertEquals(1,native.closed.get())
            assertTrue(checkNotNull(native.actualWorkspace).all{it==0.toByte()});assertTrue(authority.cleanupComplete)
        }finally{disconnected.countDown();operation.close();authority.revoke()}
    }
    @Test fun failedDisconnectStaysChargedAndDeniesFreshAuthentication()=runBlocking {
        val (authority,operation)=fixture();val native=object:FakeConnection(byteArrayOf(1)){override fun disconnect(){super.disconnect();throw java.io.IOException("public disposal failure")}}
        var consumed=false
        try {
            try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){consumed=true}};fail()}
            catch(_:Exception){}
            assertFalse(consumed);assertFalse(authority.cleanupComplete);authority.revoke()
            val key=ByteArray(32){6};assertThrows(IllegalStateException::class.java){authority.open(key)};assertTrue(key.all{it==0.toByte()})
        }finally{operation.close();authority.revoke()}
    }
    @Test fun exactLimitResponseIsAcceptedAndFullyRetired()=runBlocking {
        val (authority,operation)=fixture();val native=FakeConnection(ByteArray(32){7})
        try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){assertArrayEquals(ByteArray(32){7},it.bytes)}}
            assertTrue(checkNotNull(native.actualWorkspace).all{it==0.toByte()});assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun declaredOversizeDeniesBeforeInputAcquisition()=runBlocking {
        val (authority,operation)=fixture();val native=object:FakeConnection(byteArrayOf(9)){override fun getContentLengthLong()=33L};var consumed=false
        try{
            try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){consumed=true}};fail()}
            catch(_:AiEditFailure){}
            assertFalse(consumed);assertEquals(0,native.opened.get());assertNull(native.actualWorkspace);assertEquals(1,native.disconnected.get());assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun disallowedScopeCannotInvokeConnectionFactory()=runBlocking {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val operation=authority.operationOrNull(setOf(PrimaryScope.READ))!!
        var factories=0;var consumed=false
        try{
            try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.READ))){PrivateAiHttpTransport {factories++;FakeConnection(byteArrayOf(1))}.consumePredictionStatus("PublicId42",request){consumed=true}};fail()}
            catch(_:IllegalStateException){}
            assertEquals(0,factories);assertFalse(consumed);assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun unsupportedStatusRequestsDenyBeforeNativeAcquisition()=runBlocking {
        val (authority,operation)=fixture();val guard=ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS);var factories=0;var consumed=false
        val uri=request.url
        val invalid=mutableListOf<Pair<String,AiHttpRequest>>()
        for(url in listOf("https://api.replicate.com/v1/account","https://api.replicate.com/v1/predictions","$uri/cancel","$uri/../OtherId","$uri?x=1","$uri#x",uri.replace("PublicId42","OtherId"),uri.replace("PublicId42","%50ublicId42"),uri.replace("https:","http:"),uri.replace("api.replicate.com","API.REPLICATE.COM"),uri.replace("api.replicate.com","user@api.replicate.com"),uri.replace("api.replicate.com","api.replicate.com:443"),"https://replicate.delivery/public.png"))invalid+="PublicId42" to AiHttpRequest("GET",url,emptyMap(),maxResponseBytes=32)
        for(id in listOf("","a".repeat(129),"bad-id","../id","bad_id"," id","id ","é"))invalid+=id to AiHttpRequest("GET","https://api.replicate.com/v1/predictions/$id",emptyMap(),maxResponseBytes=32)
        invalid+="PublicId42" to AiHttpRequest("POST",uri,emptyMap(),maxResponseBytes=32)
        invalid+="PublicId42" to AiHttpRequest("GET",uri,emptyMap(),AiRequestBody{error("body must not run")},32)
        for(limit in listOf(0,-1,2*1024*1024+1,Int.MAX_VALUE))invalid+="PublicId42" to AiHttpRequest("GET",uri,emptyMap(),maxResponseBytes=limit)
        try{
            for((id,bad)in invalid){try{withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport{factories++;FakeConnection(byteArrayOf(1))}.consumePredictionStatus(id,bad){consumed=true}};fail("bad status admitted")}catch(_:IllegalArgumentException){}}
            assertEquals(0,factories);assertFalse(consumed);assertEquals(0,ioPool(authority).occupied);assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun fullTwoMiBBoundAnd128AsciiIdAdmitOnlyTheExactOriginal()=runBlocking {
        val (authority,operation)=fixture();val native=FakeConnection(ByteArray(2*1024*1024){7});val id="A".repeat(128)
        try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){
            PrivateAiHttpTransport{native}.consumePredictionStatus(id,AiHttpRequest("GET","https://api.replicate.com/v1/predictions/$id",emptyMap())){
                assertEquals(2*1024*1024,it.bytes.size);assertTrue(it.bytes.all{it==7.toByte()});assertEquals(1,ioPool(authority).occupied);assertEquals(1,native.disconnected.get());assertEquals(1,native.closed.get())
            }
        };assertEquals(0,ioPool(authority).occupied);assertTrue(authority.cleanupComplete)}finally{operation.close();authority.revoke()}
    }
    @Test fun verificationEntryStillRejectsCanonicalPredictionStatus()=runBlocking {
        val (authority,operation)=fixture();var factories=0
        try{try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport{factories++;FakeConnection(byteArrayOf(1))}.consumeVerification(request){error("verification admitted status")}};fail()}catch(_:IllegalArgumentException){}
            assertEquals(0,factories);assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun malformedBulkAndScalarCountsNeverReachConsumerAndWipeWorkspace()=runBlocking {
        for(kind in 0..3){
            val (authority,operation)=fixture();var consumed=false
            val native=object:FakeConnection(byteArrayOf(7)) {
                override fun getInputStream():InputStream=object:InputStream(){
                    override fun read(b:ByteArray,off:Int,len:Int):Int {actualWorkspace=b;b[off]=9;return when(kind){0->-2;1->len+1;else->0}}
                    override fun read():Int=if(kind==2)-2 else 256
                    override fun close(){closed.incrementAndGet()}
                }
            }
            try{try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport{native}.consumePredictionStatus("PublicId42",request){consumed=true}};fail()}catch(_:IllegalStateException){}
                assertFalse(consumed);assertTrue(checkNotNull(native.actualWorkspace).all{it==0.toByte()});assertEquals(1,native.disconnected.get());assertEquals(1,native.closed.get());assertTrue(authority.cleanupComplete)
            }finally{operation.close();authority.revoke()}
        }
    }
    private fun ioPool(authority:PrimarySessionAuthority):ReleasePool=authority.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.get(authority) as ReleasePool
    @Test fun independentDisconnectUnblocksInputCloseBeforeConsumer()=runBlocking {
        val (authority,operation)=fixture();val disconnected=CountDownLatch(1);val closed=CountDownLatch(1)
        val native=object:FakeConnection(byteArrayOf(1)) {
            override fun disconnect(){super.disconnect();disconnected.countDown()}
            override fun getInputStream():InputStream=object:ByteArrayInputStream(byteArrayOf(1)) {
                override fun read(b:ByteArray,off:Int,len:Int):Int {actualWorkspace=b;return super.read(b,off,len)}
                override fun close(){check(disconnected.await(5,TimeUnit.SECONDS));super.close();closed.countDown()}
            }
        }
        try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){PrivateAiHttpTransport {native}.consumePredictionStatus("PublicId42",request){assertEquals(0L,disconnected.count);assertEquals(0L,closed.count);assertArrayEquals(byteArrayOf(1),it.bytes)}}
            assertTrue(authority.cleanupComplete)
        }finally{disconnected.countDown();operation.close();authority.revoke()}
    }
    private open class FakeConnection(private val fixture:ByteArray):HttpsURLConnection(URL("https://api.replicate.com/v1/predictions/PublicId42")) {
        val disconnected=AtomicInteger();val opened=AtomicInteger();val closed=AtomicInteger()
        var actualWorkspace:ByteArray?=null;var code=200
        override fun getInputStream():InputStream {opened.incrementAndGet();return object:ByteArrayInputStream(fixture){
            override fun read(b:ByteArray,off:Int,len:Int):Int {actualWorkspace=b;return super.read(b,off,len)}
            override fun close(){closed.incrementAndGet()}
        }}
        override fun getResponseCode()=code
        override fun getContentLengthLong()=-1L
        override fun getContentType()="application/json"
        override fun disconnect(){disconnected.incrementAndGet()}
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun getCipherSuite()="public-fixture"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates():Array<java.security.cert.Certificate> =emptyArray()
    }
}
