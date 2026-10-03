package uk.co.traynor.privategallery.core.browser.v2

import androidx.media3.datasource.DataSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Actual published close/read paths only; reflective injected fields do not exercise open/HTTP/Media3. */
class VideoSegmentPublishedRegressionTest {
    @Test fun normalDisposalDispatchesDisconnectBeforeBlockedInputCloseReturns() = isolated { guard ->
        val entered=CountDownLatch(1);val disconnected=CountDownLatch(1);val escape=AtomicBoolean();val outcome=AtomicReference<Throwable?>()
        val connection=connection { disconnected.countDown() }
        val raw=object:ByteArrayInputStream(byteArrayOf(42)) {
            override fun close() {
                entered.countDown()
                while(disconnected.count>0&&!escape.get()) Thread.sleep(5)
            }
        }
        val source=published(guard,{false},connection,raw)
        val worker=Thread { try {source.close()}catch(t:Throwable){outcome.set(t)} }
        worker.start()
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            assertTrue("Actual disconnect must arrive before test cleanup permits input.close return",disconnected.await(1,TimeUnit.SECONDS))
        } finally { escape.set(true);worker.join(5000) }
        assertFalse(worker.isAlive);assertNull(outcome.get())
    }
    @Test fun cancellationDuringReadDeniesAndWipesOnlyRequestedSlice() = isolated { guard ->
        val cancelled=AtomicBoolean()
        val raw=object:InputStream() {
            override fun read():Int=42
            override fun read(b:ByteArray,off:Int,len:Int):Int {b[off]=42;cancelled.set(true);return 1}
        }
        val source=published(guard,{cancelled.get()},connection {},raw)
        try {
            val bytes=ByteArray(4){9}
            assertThrows(IOException::class.java){source.read(bytes,1,2)}
            assertArrayEquals(byteArrayOf(9,0,0,9),bytes)
        }finally{source.close()}
    }
    @Test fun liveReadAndNormalDisposalRemainUsable() = isolated { guard ->
        val disconnected=CountDownLatch(1);val source=published(guard,{false},connection {disconnected.countDown()},ByteArrayInputStream(byteArrayOf(42)))
        val bytes=ByteArray(2)
        assertEquals(1,source.read(bytes,0,2));assertEquals(42,bytes[0].toInt());assertEquals(-1,source.read(bytes,0,2));source.close()
        assertTrue(disconnected.await(5,TimeUnit.SECONDS))
    }
    private fun published(guard:ScopedIoGuard,cancelled:()->Boolean,connection:HttpURLConnection,input:InputStream):DataSource {
        val type=Class.forName("uk.co.traynor.privategallery.core.browser.v2.SessionVideoDataSource")
        val constructor=type.declaredConstructors.single().also{it.isAccessible=true}
        val source=constructor.newInstance("Public fixture",null,cancelled,guard) as DataSource
        guard.own(AutoCloseable{connection.disconnect()})
        type.getDeclaredField("connection").also{it.isAccessible=true}.set(source,connection)
        type.getDeclaredField("input").also{it.isAccessible=true}.set(source,guard.input{input})
        return source
    }
    private fun connection(disconnect:()->Unit)=object:HttpURLConnection(URL("https://example.test/segment")) {
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun disconnect()=disconnect.invoke()
    }
    private fun isolated(body:(ScopedIoGuard)->Unit) {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try{body(ScopedIoGuard(op,PrimaryScope.BROWSER_UPLOAD_EGRESS))}finally{op.close();authority.revoke()}
    }
}
