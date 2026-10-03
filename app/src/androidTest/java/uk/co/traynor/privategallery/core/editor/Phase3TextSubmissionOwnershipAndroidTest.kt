package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import uk.co.traynor.privategallery.core.security.*

/** Real Private transport, public fake Native only; isolated pools are not process fit. */
@RunWith(AndroidJUnit4::class)
class Phase3TextSubmissionOwnershipAndroidTest {
    private val models=listOf(GenerationModel.SEEDREAM,GenerationModel.WHISKII)
    private fun generation(model:GenerationModel,references:List<String> = emptyList())=
        GenerationRequest(model," public bird ",GenerationAspect.SQUARE,references=references)
    private fun request(model:GenerationModel)=AiHttpRequest("POST",model.endpoint,mapOf("Content-Type" to "application/json"),maxResponseBytes=32)
    private suspend fun submit(t:AiHttpTransport,kind:GenerationModel,g:GenerationRequest,r:AiHttpRequest,consume:(AiHttpResponse)->Unit={}) {
        when(kind) {
            GenerationModel.FLUX_PRO->t.consumeFluxProSubmission(g,r,consume)
            GenerationModel.SEEDREAM->t.consumeSeedreamTextSubmission(g,r,consume)
            GenerationModel.WHISKII->t.consumeWhiskiiTextSubmission(g,r,consume)
            else->error("Fixture admission")
        }
    }
    @Test fun exactlySevenFreeSlotsUseFundedBodyAndRetireNativeBeforeConsumer()=runBlocking {
        for(model in models)isolated{authority,guard->
            val holds=List(9){guard.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}}
            val native=Fake(model);var emitted:String?=null;native.writer={b,o,n->emitted=String(b,o,n,Charsets.UTF_8)}
            try{withContext(PrimaryIoContext(guard)){submit(PrivateAiHttpTransport{uri->assertEquals(model.endpoint,uri.toString());native},model,generation(model),request(model)) {
                assertArrayEquals(byteArrayOf(7),it.bytes);assertEquals(1,native.disconnects.get());assertEquals(1,native.closes.get())
                assertEquals(65536,checkNotNull(native.body).size);assertTrue(checkNotNull(native.body).all{it==0.toByte()})
            }}
                val root=JSONObject(checkNotNull(emitted));assertEquals(if(model==GenerationModel.SEEDREAM)1 else 2,root.length())
                assertEquals("public bird",root.getJSONObject("input").getString("prompt"))
                if(model==GenerationModel.SEEDREAM){assertEquals("2K",root.getJSONObject("input").getString("size"));assertFalse(root.has("version"))}
                else assertEquals("alicewuv/whiskii-gen:e90d5fa37f8c42812753afd6bc05409d67a970bc87ef57454892c0fab98a7b03",root.getString("version"))
            }finally{holds.forEach{guard.retire(it)}}
            assertTrue(authority.cleanupComplete)
        }
    }
    @Test fun crossModelKindAndUrlMatrixDeniedBeforeNative()=runBlocking {
        isolated{_,guard->var factories=0;val t=PrivateAiHttpTransport{factories++;Fake(GenerationModel.SEEDREAM)}
            val kinds=models+GenerationModel.FLUX_PRO
            for(kind in kinds)for(model in GenerationModel.entries)for(urlModel in kinds) {
                if(kind==model && model==urlModel)continue
                val g=generation(model);val r=request(urlModel)
                val failure=withContext(PrimaryIoContext(guard)){try{submit(t,kind,g,r);null}catch(e:Throwable){e}}
                assertTrue("$kind/$model/$urlModel",failure is IllegalArgumentException)
            }
            assertEquals(0,factories)
        }
    }
    @Test fun opaqueBodyQueriesHeadersBoundsAndMutableReferencesDenyBeforeNative()=runBlocking {
        for(model in models)isolated{_,guard->var factories=0;var bodyCalls=0;val t=PrivateAiHttpTransport{factories++;Fake(model)};val good=request(model)
            for(bad in listOf(AiHttpRequest("GET",good.url,good.headers),AiHttpRequest("POST",good.url+"?version=foreign",good.headers),
                AiHttpRequest("POST",good.url,good.headers,AiRequestBody{bodyCalls++;it.write("foreign version".toByteArray())}),
                AiHttpRequest("POST",good.url,emptyMap()),AiHttpRequest("POST",good.url,good.headers,maxResponseBytes=0),
                AiHttpRequest("POST",good.url,good.headers,maxResponseBytes=2*1024*1024+1))) {
                val failure=withContext(PrimaryIoContext(guard)){try{submit(t,model,generation(model),bad);null}catch(e:Throwable){e}}
                assertTrue(failure is IllegalArgumentException)
            }
            val refs=mutableListOf<String>();val g=generation(model,refs);refs.add("data:image/png;base64,AQ==")
            assertTrue(withContext(PrimaryIoContext(guard)){try{submit(t,model,g,good);null}catch(e:Throwable){e}} is IllegalArgumentException)
            assertEquals(0,factories);assertEquals(0,bodyCalls)
        }
    }
    @Test fun readScopeAndClosedOriginalDenyBeforeNativeEvenNonCancellable()=runBlocking {
        for(model in models)for(closed in listOf(false,true)) {
            val a=isolatedAuthority();a.open(ByteArray(32));val scope=if(closed)PrimaryScope.REMOTE_AI_EGRESS else PrimaryScope.READ
            val op=checkNotNull(a.operationOrNull(setOf(scope)));val guard=ScopedIoGuard(op,scope);var factories=0
            if(closed)op.close()
            try{val failure=withContext(NonCancellable+PrimaryIoContext(guard)){try{submit(PrivateAiHttpTransport{factories++;Fake(model)},model,generation(model),request(model));null}catch(e:Throwable){e}}
                assertTrue(failure is IllegalStateException);assertEquals(0,factories);assertTrue(a.cleanupComplete)
            }finally{op.close();a.revoke()}
        }
    }
    @Test fun actualBlockedWriteCancellationDispatchesDisconnectBeforeBorrowReturnAndWipe()=runBlocking {
        for(model in models)isolated{authority,guard->val native=Fake(model);val entered=CountDownLatch(1);val disconnected=CountDownLatch(1);val escape=Semaphore(0);var consumed=false
            native.disconnector={disconnected.countDown()}
            native.writer={b,o,n->assertTrue(n>0);assertEquals('{'.code.toByte(),b[o]);entered.countDown();escape.acquire();assertEquals('{'.code.toByte(),b[o])}
            val worker=launch(Dispatchers.IO+PrimaryIoContext(guard)){submit(PrivateAiHttpTransport{native},model,generation(model),request(model)){consumed=true}}
            try{assertTrue(entered.await(5,TimeUnit.SECONDS));worker.cancel();assertTrue(disconnected.await(2,TimeUnit.SECONDS))
                assertEquals('{'.code.toByte(),checkNotNull(native.body)[0]);assertEquals(0,native.closes.get());assertFalse(authority.cleanupComplete);assertFalse(consumed)
            }finally{escape.release();withTimeout(5000){worker.join()}}
            assertTrue(checkNotNull(native.body).all{it==0.toByte()});assertEquals(1,native.disconnects.get());assertEquals(1,native.closes.get());assertEquals(0,native.headers.get());assertTrue(authority.cleanupComplete)
        }
    }
    @Test fun actualPaidHeaderTimeoutAndFailedDisconnectNeverBecomeRetryOutcome()=runBlocking {
        for(model in models)isolated{authority,guard->val native=Fake(model);var submissions=0
            native.header={throw java.net.SocketTimeoutException("public timeout")};native.disconnector={throw java.io.IOException("public disconnect failure")}
            val t=PrivateAiHttpTransport{uri->assertEquals(model.endpoint,uri.toString());submissions++;native}
            val failure=withContext(PrimaryIoContext(guard)){try{ReplicateImageGenerationApi(t,0).generate("public-token".toByteArray(),generation(model));null}catch(e:Throwable){e}}
            assertTrue(failure is AiSubmissionUncertain);assertTrue(failure!!.cause!!.suppressed.any{it is AiNetworkFailure&&it.timedOut})
            assertEquals(1,submissions);assertEquals(1,native.disconnects.get());assertEquals(1,native.headers.get());assertTrue(checkNotNull(native.body).all{it==0.toByte()});assertFalse(authority.cleanupComplete)
            authority.revoke();val key=ByteArray(32){9};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
        }
    }
    private class Fake(model:GenerationModel):HttpsURLConnection(URL(model.endpoint)) {
        val disconnects=AtomicInteger();val closes=AtomicInteger();val headers=AtomicInteger();var body:ByteArray?=null
        var writer:(ByteArray,Int,Int)->Unit={_,_,_->};var disconnector:()->Unit={};var header:()->Unit={}
        override fun disconnect(){disconnects.incrementAndGet();disconnector()}
        override fun getOutputStream():OutputStream=object:OutputStream(){override fun write(value:Int)=error("bulk only");override fun write(b:ByteArray,off:Int,len:Int){body=b;this@Fake.writer(b,off,len)};override fun close(){closes.incrementAndGet()}}
        override fun getInputStream()=ByteArrayInputStream(byteArrayOf(7))
        override fun getResponseCode():Int{headers.incrementAndGet();header();return 200}
        override fun getContentLengthLong()=-1L
        override fun getContentType()="application/json"
        override fun connect(){}
        override fun usingProxy()=false
        override fun getCipherSuite()="public"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates()=emptyArray<java.security.cert.Certificate>()
    }
    private suspend fun isolated(body:suspend(PrimarySessionAuthority,ScopedIoGuard)->Unit) {
        val a=isolatedAuthority();a.open(ByteArray(32));val op=checkNotNull(a.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS)))
        try{body(a,ScopedIoGuard(op,PrimaryScope.REMOTE_AI_EGRESS))}finally{op.close();a.revoke()}
    }
    private fun isolatedAuthority():PrimarySessionAuthority=PrimarySessionAuthority().also{owner->
        for(name in listOf("ioReleasePool","presentationReleasePool"))owner.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(owner,ReleasePool(16))
    }

}
