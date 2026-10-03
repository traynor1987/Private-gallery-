package uk.co.traynor.privategallery.core.browser.v2

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Public fake Native transport/input fixtures exercising the actual production segment helper. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class Phase3VideoSegmentOwnershipAndroidTest {
    @Test fun disposalDispatchesIndependentDisconnectAndWaitsActualInputReturn() = isolated { authority,guard,_ ->
        val inputEntered=CountDownLatch(1);val disconnected=CountDownLatch(1);val allowInput=CountDownLatch(1)
        val inputCloses=AtomicInteger();val disconnects=AtomicInteger();val outcome=AtomicReference<Throwable?>()
        val raw=object:ByteArrayInputStream(byteArrayOf(42)) {
            override fun close(){inputCloses.incrementAndGet();inputEntered.countDown();check(disconnected.await(5,TimeUnit.SECONDS));allowInput.await()}
        }
        val source=open(guard,connection=Fake(input={raw},disconnect={disconnects.incrementAndGet();disconnected.countDown()}))
        val worker=Thread{try{source.close()}catch(t:Throwable){outcome.set(t)}};worker.start()
        try{
            assertTrue(inputEntered.await(5,TimeUnit.SECONDS));assertTrue(disconnected.await(5,TimeUnit.SECONDS))
            assertTrue(worker.isAlive);authority.revoke();assertFalse(authority.cleanupComplete)
        }finally{allowInput.countDown();worker.join(5000)}
        assertFalse(worker.isAlive);assertNull(outcome.get());source.close();assertEquals(1,inputCloses.get());assertEquals(1,disconnects.get())
        await{authority.cleanupComplete}
    }
    @Test fun fourFreeSlotsDenyBeforeEveryNativeFactory() = isolated { _,guard,_ ->
        val holds=List(12){guard.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}};var calls=0
        try{assertThrows(IllegalStateException::class.java){openOwnedVideoSegment(guard,uri,0,-1,true,"Public",null,{false},connectionFactory={calls++;Fake()},cookieFor={error("cookie before admission")})};assertEquals(0,calls)}finally{holds.forEach{guard.retire(it)}}
    }
    @Test fun fiveFreeSlotsAdmitOnlyThisSubpath() = isolated { _,guard,_ ->
        val holds=List(11){guard.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}}
        try{val source=open(guard);assertEquals(42,ByteArray(1).let{assertEquals(1,source.read(it,0,1));it[0].toInt()});source.close()}finally{holds.forEach{guard.retire(it)}}
    }
    @Test fun redirectReplacementWaitsForActualPreviousDisconnectReturn() = isolated { _,guard,_ ->
        val entered=CountDownLatch(1);val allow=CountDownLatch(1);val calls=AtomicInteger();val outcome=AtomicReference<Throwable?>();val source=AtomicReference<OwnedVideoSegment?>()
        val worker=Thread{try{source.set(openOwnedVideoSegment(guard,uri,0,-1,true,"Public",null,{false},connectionFactory={if(calls.incrementAndGet()==1)Fake(302,location="/next",disconnect={entered.countDown();allow.await()})else Fake()},cookieFor={null}))}catch(t:Throwable){outcome.set(t)}}
        worker.start();try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(1,calls.get());assertTrue(worker.isAlive)}finally{allow.countDown();worker.join(5000)}
        assertFalse(worker.isAlive);assertNull(outcome.get());assertEquals(2,calls.get());checkNotNull(source.get()).close()
    }
    @Test fun failedRedirectCannotAcquireReplacementAndStaysCharged() = isolated { authority,guard,_ ->
        var calls=0
        assertThrows(IOException::class.java){openOwnedVideoSegment(guard,uri,0,-1,true,"Public",null,{false},connectionFactory={calls++;Fake(302,location="/next",disconnect={throw IOException("public disconnect fault")})},cookieFor={null})}
        assertEquals(1,calls);denyFresh(authority)
    }
    @Test fun fourthRedirectDeniesFifthFactoryAndClosesAllTransportsOnce() = isolated { _,guard,_ ->
        val calls=AtomicInteger();val closes=AtomicInteger()
        assertThrows(IOException::class.java){openOwnedVideoSegment(guard,uri,0,-1,true,"Public",null,{false},connectionFactory={calls.incrementAndGet();Fake(302,location="/next",disconnect={closes.incrementAndGet()})},cookieFor={null})}
        assertEquals(4,calls.get());assertEquals(4,closes.get())
    }
    @Test fun redirectUsesDestinationSpecificCookieAndPreservesRequestHeaders() = isolated { _,guard,_ ->
        val targets=mutableListOf<URI>();val first=Fake(302,location="https://other.test/final");val second=Fake()
        val source=openOwnedVideoSegment(guard,uri,0,-1,true,"Public UA","https://origin.test/",{false},connectionFactory={if(it.host=="example.test")first else second},cookieFor={targets.add(it);"cookie-${it.host}"})
        try{assertEquals(listOf("example.test","other.test"),targets.map{it.host});assertEquals("cookie-example.test",first.getRequestProperty("Cookie"));assertEquals("cookie-other.test",second.getRequestProperty("Cookie"));assertEquals("Public UA",second.getRequestProperty("User-Agent"));assertEquals("https://origin.test/",second.getRequestProperty("Referer"));assertEquals("https://origin.test",second.getRequestProperty("Origin"));assertEquals("GET",second.requestMethod);assertFalse(second.instanceFollowRedirects)}finally{source.close()}
    }
    @Test fun revocationDispatchesKnownTransportBeforeLateInputFactoryReturns() = isolated { authority,guard,_ ->
        val entered=CountDownLatch(1);val allow=CountDownLatch(1);val disconnected=CountDownLatch(1);val closed=AtomicInteger();val outcome=AtomicReference<Throwable?>();val source=AtomicReference<OwnedVideoSegment?>()
        val connection=Fake(input={entered.countDown();allow.await();object:ByteArrayInputStream(byteArrayOf(42)){override fun close(){closed.incrementAndGet()}}},disconnect={disconnected.countDown()})
        val worker=Thread{try{source.set(open(guard,connection=connection))}catch(t:Throwable){outcome.set(t)}}
        worker.start();try{assertTrue(entered.await(5,TimeUnit.SECONDS));authority.revoke();assertTrue(disconnected.await(5,TimeUnit.SECONDS));assertFalse(authority.cleanupComplete);assertTrue(worker.isAlive)}finally{allow.countDown();worker.join(5000)}
        assertFalse(worker.isAlive);assertNull(source.get());assertNotNull(outcome.get());assertEquals(1,closed.get())
    }
    @Test fun wrongScopeMethodUnsafeUrlAndInvalidRangeDenyBeforeAcquisition() = isolated { authority,guard,_ ->
        var calls=0
        fun attempt(g:ScopedIoGuard=guard,u:URI=uri,p:Long=0,l:Long=-1,get:Boolean=true)=openOwnedVideoSegment(g,u,p,l,get,"Public",null,{false},connectionFactory={calls++;Fake()},cookieFor={error("unexpected cookie")})
        val readOnly=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try{assertThrows(IllegalStateException::class.java){attempt(g=ScopedIoGuard(readOnly,PrimaryScope.READ))}}finally{readOnly.close()}
        assertThrows(IllegalArgumentException::class.java){attempt(get=false)}
        for(u in listOf("http://example.test/x","https://user@example.test/x","https://example.test:8443/x","https://example.test/"+"x".repeat(8192)))assertThrows(IOException::class.java){attempt(u=URI(u))}
        assertThrows(IllegalArgumentException::class.java){attempt(p=-1)};assertThrows(IllegalArgumentException::class.java){attempt(l=-2)};assertThrows(IllegalArgumentException::class.java){attempt(p=Long.MAX_VALUE,l=2)};assertEquals(0,calls)
    }
    @Test fun protectedManifestDeniesSegmentTransportBeforeItsFactory() = isolated { _,guard,_ ->
        var calls=0
        assertThrows(BrowserVideoUnavailableException::class.java){openOwnedVideoSegment(guard,URI("https://example.test/index.m3u8"),0,-1,true,"Public",null,{false},connectionFactory={calls++;Fake()},cookieFor={null},protectedManifest={true})};assertEquals(0,calls)
    }
    @Test fun inputFactoryFailureRetiresPartialTransportAndPreservesOriginalFault() = isolated { _,guard,_ ->
        val fault=IOException("public input factory fault");val closed=AtomicInteger()
        assertSame(fault,assertThrows(IOException::class.java){open(guard,connection=Fake(input={throw fault},disconnect={closed.incrementAndGet()}))});assertEquals(1,closed.get())
    }
    @Test fun headerFailureRetiresBothAcquiredNativeChildren() = isolated { _,guard,_ ->
        val fault=IOException("public headers fault");val inputs=AtomicInteger();val transports=AtomicInteger()
        assertSame(fault,assertThrows(IOException::class.java){open(guard,connection=Fake(input={object:ByteArrayInputStream(byteArrayOf(42)){override fun close(){inputs.incrementAndGet()}}},disconnect={transports.incrementAndGet()},headers={throw fault}))});assertEquals(1,inputs.get());assertEquals(1,transports.get())
    }
    @Test fun authenticationAndOtherStatusFailuresNeverAcquireInput() = isolated { _,guard,_ ->
        var inputs=0;var closes=0
        for(status in listOf(401,403,500)) {
            val fault=assertThrows(IOException::class.java){open(guard,connection=Fake(status,input={inputs++;ByteArrayInputStream(byteArrayOf(42))},disconnect={closes++}))}
            if(status!=500)assertEquals(MediaSaveReason.SESSION_AUTH_FAILED,(fault as BrowserVideoUnavailableException).reason)
        };assertEquals(0,inputs);assertEquals(3,closes)
    }
    @Test fun failedFinalInputCloseNeverBecomesSuccessfulDuplicateDisposalOrFreshAuth() = isolated { authority,guard,_ ->
        val closes=AtomicInteger();val source=open(guard,connection=Fake(input={object:ByteArrayInputStream(byteArrayOf(42)){override fun close(){closes.incrementAndGet();throw IOException("public close fault")}}}))
        assertThrows(IOException::class.java){source.close()};assertThrows(IOException::class.java){source.close()};assertEquals(1,closes.get());denyFresh(authority)
    }
    @Test fun ignoredRange200SkipsBoundedlyAnd206DoesNotDoubleSkip() = isolated { _,guard,_ ->
        for(status in listOf(200,206)) {
            var skips=0
            val connection=Fake(status,input={object:ByteArrayInputStream(if(status==200)byteArrayOf(0,1,42,43)else byteArrayOf(42,43)){override fun skip(n:Long):Long{skips++;return super.skip(n)}}})
            val source=open(guard,p=2,l=2,connection=connection)
            try{assertEquals("bytes=2-3",connection.getRequestProperty("Range"));assertEquals(2,source.remaining);val bytes=ByteArray(4);assertEquals(0,source.read(bytes,0,0));assertEquals(2,source.read(bytes,1,3));assertArrayEquals(byteArrayOf(0,42,43,0),bytes);assertEquals(0,source.remaining);assertEquals(-1,source.read(bytes,0,1));assertEquals(if(status==200)1 else 0,skips)}finally{source.close()}
        }
    }
    @Test fun unsupportedRangeSkipDeniesAndRetiresBothChildren() = isolated { _,guard,_ ->
        val inputs=AtomicInteger();val transports=AtomicInteger()
        for(p in listOf(1L,8L*1024*1024+1))assertThrows(IOException::class.java){open(guard,p=p,connection=Fake(input={object:ByteArrayInputStream(byteArrayOf(42)){override fun skip(n:Long)=0L;override fun close(){inputs.incrementAndGet()}}},disconnect={transports.incrementAndGet()}))}
        assertEquals(2,inputs.get());assertEquals(2,transports.get())
    }
    @Test fun cancellationDuringReadDeniesAndWipesRequestedCallerSlice() = isolated { _,guard,_ ->
        val cancelled=AtomicBoolean();val raw=object:InputStream(){override fun read()=42;override fun read(b:ByteArray,off:Int,len:Int):Int{b[off]=42;cancelled.set(true);return 1}}
        val source=open(guard,cancelled={cancelled.get()},connection=Fake(input={raw}))
        try{val bytes=ByteArray(4){9};assertThrows(IOException::class.java){source.read(bytes,1,2)};assertArrayEquals(byteArrayOf(9,0,0,9),bytes)}finally{source.close()}
    }
    @Test fun overreportedNativeReadCountDeniesAndWipesRequestedSlice() = isolated { _,guard,_ ->
        val raw=object:InputStream(){override fun read()=42;override fun read(b:ByteArray,off:Int,len:Int):Int{b[off]=42;return len+1}};val source=open(guard,connection=Fake(input={raw}))
        try{val bytes=ByteArray(4){9};assertThrows(IOException::class.java){source.read(bytes,1,2)};assertArrayEquals(byteArrayOf(9,0,0,9),bytes)}finally{source.close()}
    }
    @Test fun normalOriginalRetirementDeniesMetadataZeroAndExhaustedReads() = isolated { _,guard,_ ->
        val source=open(guard,l=1)
        assertEquals(1,source.read(ByteArray(1),0,1));source.close()
        assertThrows(IllegalStateException::class.java){source.responseHeaders()}
        assertThrows(IllegalStateException::class.java){source.checkAvailable()}
        val bytes=ByteArray(4){9}
        assertThrows(IllegalStateException::class.java){source.read(bytes,1,2)}
        assertArrayEquals(byteArrayOf(9,0,0,9),bytes)
        assertThrows(IllegalStateException::class.java){source.read(bytes,0,0)}
    }
    private val uri=URI("https://example.test/segment")
    private fun open(guard:ScopedIoGuard,p:Long=0,l:Long=-1,cancelled:()->Boolean={false},connection:Fake=Fake())=openOwnedVideoSegment(guard,uri,p,l,true,"Public",null,cancelled,connectionFactory={connection},cookieFor={null})
    private class Fake(private val status:Int=200,private val location:String?=null,private val input:()->InputStream={ByteArrayInputStream(byteArrayOf(42))},private val disconnect:()->Unit={},private val headers:()->Map<String,List<String>> = {mapOf("Public" to listOf("fixture"))}):HttpURLConnection(URL("https://example.test/fixture")) {
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun disconnect()=disconnect.invoke()
        override fun getResponseCode()=status
        override fun getHeaderField(name:String?)=if(name=="Location")location else null
        override fun getHeaderFields()=headers()
        override fun getInputStream()=input()
        override fun getContentLengthLong()=1L
    }
    private fun denyFresh(authority:PrimarySessionAuthority){authority.revoke();assertFalse(authority.cleanupComplete);val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)}
    private fun await(condition:()->Boolean){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition()){check(System.nanoTime()<until);Thread.yield()}}
    private fun isolated(body:(PrimarySessionAuthority,ScopedIoGuard,PrimaryOperation)->Unit){val authority=PrimarySessionAuthority().also { value ->
            value.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
            value.javaClass.getDeclaredField("presentationReleasePool").apply{isAccessible=true}.set(value,ReleasePool(16))
        };authority.open(ByteArray(32));val op=authority.operationOrNull(PrimaryScope.entries.toSet())!!;try{body(authority,ScopedIoGuard(op,PrimaryScope.BROWSER_UPLOAD_EGRESS),op)}finally{op.close();authority.revoke()}}
}
