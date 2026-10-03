package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Actual three finally sites delegate cancellation to the real owned transport.
 * Initial paid POST/status are public fakes; no remote/provider completion claim. */
class Phase3PredictionCancellationIntegrationTest {
    @Test fun eachActualTimeoutCancelsTheInnerJobAndDispatchesNativeDisconnect()=runBlocking {
        for(kind in 0..2)exercise(kind,blocked=true)
    }
    @Test fun eachCaughtBestEffortNativeFailureStillPinsFreshAuthentication()=runBlocking {
        for(kind in 0..2)exercise(kind,blocked=true,failed=true)
    }
    @Test fun eachClosedOriginalOwnerDeniesCancellationBeforeNativeUnderNonCancellable()=runBlocking {
        for(kind in 0..2)exercise(kind,closed=true)
    }
    @Test fun eachSuccessfulIgnoredResponseRetiresAndWipesExactNativeArrays()=runBlocking {
        for(kind in 0..2)exercise(kind)
    }
    @Test fun cancellationStatusAndVerificationAdmissionsCannotCrossRoutes()=runBlocking {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS))!!
        var factories=0;var consumers=0;val backend=PrivateAiHttpTransport{factories++;Native(false,false)}
        val cancel="https://api.replicate.com/v1/predictions/PublicId42/cancel"
        val status="https://api.replicate.com/v1/predictions/PublicId42"
        try {withContext(PrimaryIoContext(ScopedIoGuard(op,PrimaryScope.REMOTE_AI_EGRESS))){
            val cases:List<CancellationCall> = listOf(
                {backend.consumePredictionStatus("PublicId42",AiHttpRequest("GET",cancel,emptyMap())){consumers++}},
                {backend.consumeVerification(AiHttpRequest("GET",cancel,emptyMap())){consumers++}},
                {backend.consumePredictionCancellation("PublicId42",AiHttpRequest("POST",status,emptyMap())){consumers++}},
                {backend.consumePredictionCancellation("PublicId42",AiHttpRequest("POST","https://api.replicate.com/v1/account",emptyMap())){consumers++}}
            )
            for(call in cases){try{call();fail("cross-route request admitted")}catch(_:IllegalArgumentException){}}
        };assertEquals(0,factories);assertEquals(0,consumers);assertEquals(0,ioPool(authority).occupied)
        }finally{op.close();authority.revoke()}
    }
    private suspend fun exercise(kind:Int,blocked:Boolean=false,failed:Boolean=false,closed:Boolean=false)=supervisorScope {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS))!!
        val native=Native(blocked,failed);val transport=Hybrid(native);val guard=ScopedIoGuard(op,PrimaryScope.REMOTE_AI_EGRESS)
        if(closed)op.close()
        val caller=async(Dispatchers.Default+PrimaryIoContext(guard)){invoke(kind,transport)};transport.outer=caller
        try {
            withTimeout(5000){transport.statusEntered.await()};caller.cancel()
            withTimeout(15000){try{caller.await();fail("cancelled caller returned output")}catch(_:CancellationException){}}
            assertEquals(1,transport.posts);assertEquals(1,transport.cancels);assertEquals(0,transport.downloads)
            assertTrue(transport.activeInner);assertTrue(transport.distinctInner)
            if(closed){assertEquals(0,transport.factories);assertEquals(0,native.disconnects.get());assertEquals(0,ioPool(authority).occupied)}
            else {
                assertEquals(1,transport.factories);assertTrue(native.postObserved);assertEquals(1,native.disconnects.get())
                if(blocked){assertEquals(1,native.headers.get());assertEquals(0,transport.consumers);assertEquals(0,native.inputs.get())
                    assertTrue("actual inner3s timeout must signal before Native cleanup",System.nanoTime()-transport.started.get()>=2_500_000_000L)
                }else{assertEquals(1,transport.consumers);assertEquals(1,native.inputs.get());assertEquals(1,native.closes.get())
                    assertTrue(checkNotNull(transport.result).all{it==0.toByte()});assertTrue(checkNotNull(native.workspace).all{it==0.toByte()})
                }
            }
            op.close();authority.revoke()
            if(failed){assertTrue(ioPool(authority).occupied>0);assertFalse(authority.cleanupComplete)
                val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
            }else{withTimeout(5000){while(!authority.cleanupComplete)delay(1)};assertEquals(0,ioPool(authority).occupied)}
        }finally{native.unblock.release();caller.cancel();caller.join();op.close();authority.revoke()}
    }
    private suspend fun invoke(kind:Int,t:AiHttpTransport):ByteArray=when(kind){
        0->ReplicateSeedreamApi(t,1).edit("synthetic-token".toByteArray(),byteArrayOf(1),"public edit")
        1->ReplicateModelEditApi(t,1).edit("synthetic-token".toByteArray(),ReplicateEditModel.SEEDREAM,byteArrayOf(1),"public edit",null)
        else->ReplicateImageGenerationApi(t,1).generate("synthetic-token".toByteArray(),GenerationRequest(GenerationModel.SEEDREAM,"public create",GenerationAspect.SQUARE))
    }
    private class Hybrid(private val native:Native):AiHttpTransport {
        val statusEntered=CompletableDeferred<Unit>();val started=AtomicLong();var posts=0;var cancels=0;var factories=0;var downloads=0;var consumers=0
        var outer:Job?=null;var activeInner=false;var distinctInner=false;var result:ByteArray?=null
        override suspend fun execute(request:AiHttpRequest):AiHttpResponse {
            if(request.method=="POST"&&!request.url.endsWith("/cancel")){posts++;return AiHttpResponse(200,"application/json","{\"id\":\"PublicId42\",\"status\":\"processing\"}".toByteArray())}
            downloads++;throw AssertionError("Cancellation reached legacy execute")
        }
        override suspend fun consumePredictionStatus(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){statusEntered.complete(Unit);awaitCancellation()}
        override suspend fun consumePredictionCancellation(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){
            cancels++;val inner=currentCoroutineContext().job;activeInner=inner.isActive;distinctInner=inner!==outer;started.set(System.nanoTime())
            assertEquals("PublicId42",expectedId);assertEquals("POST",request.method);assertEquals("https://api.replicate.com/v1/predictions/PublicId42/cancel",request.url);assertNull(request.body)
            PrivateAiHttpTransport{factories++;native}.consumePredictionCancellation(expectedId,request){response->consumers++;result=response.bytes;consume(response)}
        }
    }
    private class Native(private val blocked:Boolean,private val failed:Boolean):HttpsURLConnection(URL("https://api.replicate.com/v1/predictions/PublicId42/cancel")){
        val unblock=Semaphore(0);val disconnects=AtomicInteger();val headers=AtomicInteger();val inputs=AtomicInteger();val closes=AtomicInteger()
        var postObserved=false;var workspace:ByteArray?=null
        override fun getResponseCode():Int{assertEquals("POST",requestMethod);assertFalse(doOutput);postObserved=true;headers.incrementAndGet();if(blocked)unblock.acquire();return 200}
        override fun disconnect(){disconnects.incrementAndGet();unblock.release();if(failed)throw java.io.IOException("public Native disconnect failure")}
        override fun getInputStream():InputStream{inputs.incrementAndGet();return object:ByteArrayInputStream("{}".toByteArray()){
            override fun read(b:ByteArray,off:Int,len:Int):Int{workspace=b;return super.read(b,off,len)}
            override fun close(){closes.incrementAndGet()}
        }}
        override fun getOutputStream():java.io.OutputStream=throw AssertionError("No body/output stream admitted")
        override fun getContentType()="application/json"
        override fun getContentLengthLong()=-1L
        override fun connect(){}
        override fun usingProxy()=false
        override fun getCipherSuite()="public"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates()=emptyArray<java.security.cert.Certificate>()
    }
    private fun ioPool(authority:PrimarySessionAuthority):ReleasePool=authority.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.get(authority) as ReleasePool
}
private typealias CancellationCall = suspend () -> Unit
