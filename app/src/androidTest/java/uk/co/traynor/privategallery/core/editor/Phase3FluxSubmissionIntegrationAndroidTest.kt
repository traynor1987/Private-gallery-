package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import uk.co.traynor.privategallery.core.security.*

/** Actual caller + actual Private paid transport, public Native fixture.
 * Other requests are explicitly fake; this is not provider/full-pipeline acceptance. */
@RunWith(AndroidJUnit4::class)
class Phase3FluxSubmissionIntegrationAndroidTest {
    private val request=GenerationRequest(GenerationModel.FLUX_PRO,"public blue bird",GenerationAspect.SQUARE)
    @Test fun actualGenerationDownloadsOnceOnlyAfterNativeSubmissionTerminal()=runBlocking {
        isolated{authority,guard->val native=Fake("""{"id":"Public42","status":"succeeded","output":"https://replicate.delivery/public.png"}""")
            val hybrid=Hybrid(native)
            val result=withContext(PrimaryIoContext(guard)){ReplicateImageGenerationApi(hybrid,0).generate("public-token".toByteArray(),request)}
            assertArrayEquals(byteArrayOf(4,5),result);assertEquals(1,hybrid.typed.get());assertEquals(1,hybrid.legacy.get());assertEquals(0,hybrid.statuses.get());assertEquals(0,hybrid.cancels.get())
            assertEquals(1,native.disconnects.get());assertEquals(1,native.outputCloses.get());assertTrue(checkNotNull(native.body).all{it==0.toByte()});assertTrue(checkNotNull(native.workspace).all{it==0.toByte()});assertTrue(authority.cleanupComplete)
        }
    }
    @Test fun actualPaidCallerRetainsFailedReleaseCauseAndNeverRetriesOrFallsBack()=runBlocking {
        isolated{authority,guard->val native=Fake("""{"id":"Public42","status":"failed"}""");native.outputCloser={throw java.io.IOException("public output close failure")};val hybrid=Hybrid(native)
            val failure=withContext(PrimaryIoContext(guard)){try{ReplicateImageGenerationApi(hybrid,0).generate("public-token".toByteArray(),request);null}catch(t:Throwable){t}}
            assertTrue(failure is AiSubmissionUncertain);assertNotNull(failure!!.cause);assertTrue(failure.message!!.contains("Check your Replicate predictions"))
            assertEquals(1,hybrid.typed.get());assertEquals(0,hybrid.legacy.get());assertEquals(0,hybrid.statuses.get());assertEquals(0,hybrid.cancels.get());assertEquals(0,native.headers.get())
            assertEquals(1,native.outputCloses.get());assertEquals(1,native.disconnects.get());assertTrue(checkNotNull(native.body).all{it==0.toByte()});assertFalse(authority.cleanupComplete)
            authority.revoke();val fresh=ByteArray(32){8};assertThrows(IllegalStateException::class.java){authority.open(fresh)};assertArrayEquals(ByteArray(32),fresh)
        }
    }
    @Test fun actualPaidWriteFailureUsesExistingUncertaintyMessageWithoutSecondRequest()=runBlocking {
        isolated{authority,guard->val native=Fake("""{"id":"Public42","status":"failed"}""");native.writer={throw java.io.IOException("public write failure")};val hybrid=Hybrid(native)
            val failure=withContext(PrimaryIoContext(guard)){try{ReplicateImageGenerationApi(hybrid,0).generate("public-token".toByteArray(),request);null}catch(t:Throwable){t}}
            assertTrue(failure is GenerationFailure);assertEquals(GenerationFailureCategory.TIMEOUT,(failure as GenerationFailure).category);assertTrue(failure.message!!.contains("Check your Replicate predictions"))
            assertEquals(1,hybrid.typed.get());assertEquals(0,hybrid.legacy.get());assertEquals(0,hybrid.statuses.get());assertEquals(0,hybrid.cancels.get());assertEquals(0,native.headers.get());assertEquals(1,native.writes.get());assertTrue(authority.cleanupComplete)
        }
    }
    @Test fun actualPaidBillingRejectionPreservesCategoryWithoutErrorInput()=runBlocking {
        isolated{authority,guard->val native=Fake("public provider error echo");native.code=402;val hybrid=Hybrid(native)
            val failure=withContext(PrimaryIoContext(guard)){try{ReplicateImageGenerationApi(hybrid,0).generate("public-token".toByteArray(),request);null}catch(t:Throwable){t}}
            assertTrue(failure is GenerationFailure);assertEquals(GenerationFailureCategory.BILLING,(failure as GenerationFailure).category)
            assertEquals(1,hybrid.typed.get());assertEquals(0,hybrid.legacy.get());assertEquals(0,hybrid.statuses.get());assertEquals(0,hybrid.cancels.get());assertNull(native.workspace);assertTrue(authority.cleanupComplete)
        }
    }
    @Test fun actualPaidSubmissionRetiresBeforeSameIdStatusWithoutResubmission()=runBlocking {
        isolated{authority,guard->val native=Fake("""{"id":"Public42","status":"starting"}""");val hybrid=Hybrid(native)
            val failure=withContext(PrimaryIoContext(guard)){try{ReplicateImageGenerationApi(hybrid,0).generate("public-token".toByteArray(),request);null}catch(t:Throwable){t}}
            assertTrue(failure is GenerationFailure);assertEquals(GenerationFailureCategory.PREDICTION_FAILED,(failure as GenerationFailure).category)
            assertEquals(1,hybrid.typed.get());assertEquals(0,hybrid.legacy.get());assertEquals(1,hybrid.statuses.get());assertEquals(0,hybrid.cancels.get());assertTrue(authority.cleanupComplete)
        }
    }
    private class Hybrid(private val native:Fake):AiHttpTransport {
        val typed=AtomicInteger();val legacy=AtomicInteger();val statuses=AtomicInteger();val cancels=AtomicInteger()
        override suspend fun execute(request:AiHttpRequest):AiHttpResponse {legacy.incrementAndGet();assertEquals("GET",request.method);assertEquals("https://replicate.delivery/public.png",request.url);assertEquals(1,native.disconnects.get());assertTrue(checkNotNull(native.body).all{it==0.toByte()});return AiHttpResponse(200,"image/png",byteArrayOf(4,5))}
        override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){typed.incrementAndGet();PrivateAiHttpTransport{native}.consumeFluxProSubmission(generation,request,consume)}
        override suspend fun consumePredictionStatus(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){statuses.incrementAndGet();assertEquals("Public42",expectedId);assertEquals("GET",request.method);assertEquals(1,native.disconnects.get());assertTrue(checkNotNull(native.body).all{it==0.toByte()});val bytes="""{"id":"Public42","status":"failed"}""".toByteArray();try{consume(AiHttpResponse(200,"application/json",bytes))}finally{bytes.fill(0)}}
        override suspend fun consumePredictionCancellation(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){cancels.incrementAndGet();throw AssertionError("No ordinary-failure cancel")}
    }
    private class Fake(text:String):HttpsURLConnection(URL(GenerationModel.FLUX_PRO.endpoint)) {
        val payload=text.toByteArray();val disconnects=AtomicInteger();val outputCloses=AtomicInteger();val headers=AtomicInteger();val writes=AtomicInteger();var body:ByteArray?=null;var workspace:ByteArray?=null;var outputCloser:()->Unit={};var writer:()->Unit={};var code=201
        override fun disconnect(){disconnects.incrementAndGet()}
        override fun getOutputStream():OutputStream=object:OutputStream(){override fun write(value:Int)=error("bulk only");override fun write(b:ByteArray,off:Int,len:Int){body=b;writes.incrementAndGet();this@Fake.writer()};override fun close(){outputCloses.incrementAndGet();outputCloser()}}
        override fun getInputStream():InputStream=object:ByteArrayInputStream(payload){override fun read(b:ByteArray,off:Int,len:Int):Int{workspace=b;return super.read(b,off,len)}}
        override fun getResponseCode():Int{headers.incrementAndGet();return code}
        override fun getContentType()="application/json"
        override fun getContentLengthLong()=-1L
        override fun connect(){}
        override fun usingProxy()=false
        override fun getCipherSuite()="public"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates()=emptyArray<java.security.cert.Certificate>()
    }
    private suspend fun isolated(body:suspend(PrimarySessionAuthority,ScopedIoGuard)->Unit) {
        val authority=isolatedAuthority();authority.open(ByteArray(32));val op=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS)))
        try{body(authority,ScopedIoGuard(op,PrimaryScope.REMOTE_AI_EGRESS))}finally{op.close();authority.revoke()}
    }
    private fun isolatedAuthority():PrimarySessionAuthority=PrimarySessionAuthority().also{owner->
        for(name in listOf("ioReleasePool","presentationReleasePool"))owner.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(owner,ReleasePool(16))
    }

}
