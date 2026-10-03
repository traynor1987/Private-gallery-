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

/** Public fake Native; legacy baseline needs the explicitly recorded factory seam. */
@RunWith(AndroidJUnit4::class)
class Phase3FluxSubmissionNativeBaselineAndroidTest {
    private val generation=GenerationRequest(GenerationModel.FLUX_PRO,"public blue bird",GenerationAspect.PORTRAIT,seed=Int.MIN_VALUE)
    private val request=AiHttpRequest("POST",GenerationModel.FLUX_PRO.endpoint,mapOf("Content-Type" to "application/json"),maxResponseBytes=32)
    @Test fun sixFreeSlotsDenyCompleteSevenBeforeNativeConnection()=runBlocking {
        isolated{_,guard->
            val holds=List(10){guard.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}};var factories=0
            try {
                val failure=withContext(PrimaryIoContext(guard)){try{PrivateAiHttpTransport{factories++;Fake()}.consumeFluxProSubmission(generation,request){};null}catch(t:Throwable){t}}
                assertTrue(failure is IllegalStateException);assertEquals(0,factories)
            }finally{holds.forEach{guard.retire(it)}}
        }
    }
    @Test fun successDisconnectsExactlyOnceBeforeLocalConsumer()=runBlocking {
        isolated{_,guard->val native=Fake()
            withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport{native}.consumeFluxProSubmission(generation,request){assertEquals(1,native.disconnects.get());assertArrayEquals(byteArrayOf(7),it.bytes)}}
            assertEquals(1,native.disconnects.get());assertEquals(1,native.outputCloses.get());assertEquals(1,native.inputCloses.get())
        }
    }
    @Test fun nativeWriteUsesCompleteDeclaredOriginalBodyArrayAndClearsItAfterReturn()=runBlocking {
        isolated{authority,guard->val native=Fake()
            withContext(PrimaryIoContext(guard)){PrivateAiHttpTransport{native}.consumeFluxProSubmission(generation,request){
                assertEquals(64*1024,checkNotNull(native.body).size)
                assertTrue(checkNotNull(native.body).all{it==0.toByte()})
            }}
            assertTrue(authority.cleanupComplete)
        }
    }
    private open class Fake:HttpsURLConnection(URL(GenerationModel.FLUX_PRO.endpoint)) {
        val disconnects=AtomicInteger();val outputCloses=AtomicInteger();val inputCloses=AtomicInteger();var body:ByteArray?=null
        override fun disconnect(){disconnects.incrementAndGet()}
        override fun getOutputStream():OutputStream=object:OutputStream(){
            override fun write(b:ByteArray,off:Int,len:Int){body=b;assertTrue(len>0)}
            override fun write(value:Int)=error("bulk only")
            override fun close(){outputCloses.incrementAndGet()}
        }
        override fun getInputStream():InputStream=object:ByteArrayInputStream(byteArrayOf(7)){override fun close(){inputCloses.incrementAndGet();super.close()}}
        override fun getResponseCode()=200
        override fun getContentType()="application/json"
        override fun getContentLengthLong()=-1L
        override fun connect(){}
        override fun usingProxy()=false
        override fun getCipherSuite()="public"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates()=emptyArray<java.security.cert.Certificate>()
    }
    private suspend fun isolated(body:suspend(PrimarySessionAuthority,ScopedIoGuard)->Unit) {
        val authority=isolatedAuthority();authority.open(ByteArray(32));val operation=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS)))
        try{body(authority,ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))}finally{operation.close();authority.revoke()}
    }
    private fun isolatedAuthority():PrimarySessionAuthority=PrimarySessionAuthority().also{owner->
        for(name in listOf("ioReleasePool","presentationReleasePool"))owner.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(owner,ReleasePool(16))
    }

}
