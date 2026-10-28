package uk.co.traynor.privategallery.core.browser.hidden

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.LocalServerSocket
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Native replacement for retired GET Runtime injection. Actual Tor resource lifetime is separately
 * mandatory in NativeTorIsolation1762Test; these deterministic controls stall the real Unix proxy. */
class HiddenRuntimeFailureTest {
    @Test(timeout=15_000) fun freshUnixSocketNegotiatesDomainAndExchangesTunnelBytes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(File(context.applicationInfo.dataDir).canonicalFile,"tf").apply {mkdirs()}
        val path=File(folder,"s"+java.util.UUID.randomUUID().toString().take(8)).absolutePath
        val bound=LocalSocket();bound.bind(LocalSocketAddress(path,LocalSocketAddress.Namespace.FILESYSTEM))
        val server=LocalServerSocket(bound.fileDescriptor)
        val worker=Executors.newSingleThreadExecutor()
        try {
            val peer=worker.submit<String> {server.accept().use {socket ->
                val input=DataInputStream(socket.inputStream);val out=java.io.DataOutputStream(socket.outputStream)
                assertEquals(5,input.readUnsignedByte());assertEquals(1,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte())
                out.write(byteArrayOf(5,0));out.flush()
                assertEquals(5,input.readUnsignedByte());assertEquals(1,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte());assertEquals(3,input.readUnsignedByte())
                val name=ByteArray(input.readUnsignedByte());input.readFully(name)
                assertEquals("example.com",String(name,Charsets.US_ASCII));assertEquals(443,input.readUnsignedShort())
                out.write(byteArrayOf(5,0,0,1,0,0,0,0,0,0));out.flush()
                val received=input.readUTF();out.writeUTF("Synthetic origin reply");out.flush();received
            }}
            HiddenTorSockets.open(path,"example.com",443).use {tunnel ->
                java.io.DataOutputStream(tunnel.output).apply {writeUTF("Synthetic client body");flush()}
                assertEquals("Synthetic origin reply",DataInputStream(tunnel.input).readUTF())
            }
            assertEquals("Synthetic client body",peer.get(3,TimeUnit.SECONDS))
        } finally {server.close();bound.close();worker.shutdownNow();File(path).delete()}
    }
    @Test(timeout=60_000) fun nativeUnixConnectTimeoutClosesPeerWithoutFallback()=stalled(false)
    @Test(timeout=15_000) fun revokedNativeAuthorityClosesStalledUnixPeerAndDeniesReuse()=stalled(true)
    private fun stalled(revoke:Boolean) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(File(context.applicationInfo.dataDir).canonicalFile,"tf").apply {mkdirs()}
        val path=File(folder,"s"+java.util.UUID.randomUUID().toString().take(8)).absolutePath
        val bound=LocalSocket();bound.bind(LocalSocketAddress(path,LocalSocketAddress.Namespace.FILESYSTEM))
        val server=LocalServerSocket(bound.fileDescriptor)
        val allowed=AtomicBoolean(true);val connected=CountDownLatch(1)
        val workers=Executors.newFixedThreadPool(2)
        val proxy=HiddenVpnProxy.tor(path){allowed.get()}
        try {
            val peer=workers.submit<Int>{server.accept().use {socket ->
                val input=DataInputStream(socket.inputStream);val out=socket.outputStream
                check(input.readUnsignedByte()==5);repeat(input.readUnsignedByte()){input.readUnsignedByte()};out.write(byteArrayOf(5,0));out.flush()
                check(input.readUnsignedByte()==5 && input.readUnsignedByte()==1 && input.readUnsignedByte()==0 && input.readUnsignedByte()==3)
                val domain=ByteArray(input.readUnsignedByte());input.readFully(domain);check(String(domain)=="example.com");check(input.readUnsignedShort()==443)
                connected.countDown();input.read() // Deliberately no CONNECT success response.
            }}
            val response=workers.submit<ByteArray>{Socket(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),proxy.port).use {socket ->
                socket.soTimeout=40_000
                val credential=java.util.Base64.getEncoder().encodeToString(("hidden:"+proxy.password).toByteArray())
                socket.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nProxy-Authorization: Basic $credential\r\n\r\n".toByteArray());socket.getOutputStream().flush()
                socket.getInputStream().readBytes()
            }}
            assertTrue("Native proxy sends an unresolved DOMAIN SOCKS request",connected.await(5,TimeUnit.SECONDS))
            val at=SystemClock.elapsedRealtime()
            if(revoke)allowed.set(false)
            assertEquals("Stalled Unix peer observes closure",-1,peer.get(if(revoke)3 else 36,TimeUnit.SECONDS).toInt())
            val elapsed=SystemClock.elapsedRealtime()-at
            if(!revoke)assertTrue("Natural production30s SOCKS timeout, not watchdog success",elapsed in 25_000..36_000)
            assertEquals("No successful CONNECT response and no alternate transport",0,response.get(3,TimeUnit.SECONDS).size)
            val retiredPort=proxy.port;proxy.close()
            try {Socket().use {it.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),retiredPort),500)};fail("Closed proxy admitted reuse")}catch(expected:java.io.IOException){}
        } finally {allowed.set(false);proxy.close();server.close();bound.close();workers.shutdownNow();File(path).delete()}
    }
}
