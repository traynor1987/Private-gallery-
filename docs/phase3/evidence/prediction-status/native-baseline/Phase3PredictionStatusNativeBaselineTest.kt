package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Legacy body baseline requires the documented factory-only seam. No live provider. */
class Phase3PredictionStatusNativeBaselineTest {
    private val id="PublicId42"
    private val request=AiHttpRequest("GET","https://api.replicate.com/v1/predictions/$id",emptyMap(),maxResponseBytes=32)
    @Test fun fourFreeSlotsDenyBeforeNativeFactory()=runBlocking {
        isolated {_,guard->
            val holds=List(12){guard.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}};var factories=0
            try{
                val error=try{withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport{factories++;Fake()}.consumePredictionStatus(id,request){}};null}catch(t:Throwable){t}
                assertTrue(error is IllegalStateException);assertEquals(0,factories)
            }finally{holds.forEach{guard.retire(it)}}
        }
    }
    @Test fun disconnectDispatchDoesNotWaitForInputClose()=runBlocking {
        isolated {_,guard->
            val closeEntered=CountDownLatch(1);val disconnected=CountDownLatch(1);val unblock=Semaphore(0);val outcome=AtomicReference<Throwable?>()
            val native=object:Fake(){
                override fun disconnect(){super.disconnect();disconnected.countDown();unblock.release()}
                override fun getInputStream():InputStream=object:ByteArrayInputStream(byteArrayOf(7)){
                    override fun close(){closeEntered.countDown();unblock.acquire();super.close()}
                }
            }
            val caller=Thread{try{runBlocking(PrimaryIoContext(guard)){PrivateAiHttpTransport{native}.consumePredictionStatus(id,request){assertArrayEquals(byteArrayOf(7),it.bytes)}}}catch(t:Throwable){outcome.set(t)}}
            try{caller.start();assertTrue(closeEntered.await(5,TimeUnit.SECONDS));assertTrue("disconnect must dispatch before input close returns",disconnected.await(1,TimeUnit.SECONDS))}
            finally{unblock.release();caller.join(5000)}
            assertFalse(caller.isAlive);assertNull(outcome.get())
        }
    }
    @Test fun networkTimeoutWithFailedDisposalMustNotRemainRetryable()=runBlocking {
        isolated {authority,guard->
            val native=object:Fake(){
                override fun disconnect(){super.disconnect();throw java.io.IOException("public Native failure")}
                override fun getInputStream():InputStream=object:InputStream(){override fun read():Int=throw SocketTimeoutException("public read timeout");override fun read(b:ByteArray,off:Int,len:Int):Int=throw SocketTimeoutException("public read timeout")}
            }
            val error=try{withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport{native}.consumePredictionStatus(id,request){error("consumer must not run")}};null}catch(t:Throwable){t}
            assertNotNull(error);assertFalse("failed Native retirement must stop status retries",error is AiNetworkFailure)
            authority.revoke();assertFalse(authority.cleanupComplete)
            val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
        }
    }
    @Test fun successfulStatusDisposesConnectionExactlyOnceBeforeConsumer()=runBlocking {
        isolated {_,guard->val native=Fake()
            withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport{native}.consumePredictionStatus(id,request){assertEquals(1,native.disconnects.get());assertArrayEquals(byteArrayOf(7),it.bytes)}}
            assertEquals(1,native.disconnects.get())
        }
    }
    private open class Fake:HttpsURLConnection(URL("https://api.replicate.com/v1/predictions/PublicId42")) {
        val disconnects=AtomicInteger()
        override fun disconnect(){disconnects.incrementAndGet()}
        override fun getInputStream():InputStream=ByteArrayInputStream(byteArrayOf(7))
        override fun getResponseCode()=200
        override fun getContentType()="application/json"
        override fun getContentLengthLong()=-1L
        override fun connect(){}
        override fun usingProxy()=false
        override fun getCipherSuite()="public"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates()=emptyArray<java.security.cert.Certificate>()
    }
    private suspend fun isolated(body:suspend(PrimarySessionAuthority,ScopedIoGuard)->Unit){
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS)))
        try{body(authority,ScopedIoGuard(op,PrimaryScope.REMOTE_AI_EGRESS))}finally{op.close();authority.revoke()}
    }
}
