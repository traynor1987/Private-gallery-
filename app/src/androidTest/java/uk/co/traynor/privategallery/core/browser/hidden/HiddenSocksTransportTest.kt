package uk.co.traynor.privategallery.core.browser.hidden

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.io.DataInputStream
import java.util.concurrent.Executors

/** Local SOCKS protocol fixture, NOT real Tor or live DNS-leak verification. */
@RunWith(AndroidJUnit4::class)
class HiddenSocksTransportTest {
    @Test fun closeAfterAdmissionBeforeExecuteCannotOpenProxySocket() {
        ServerSocket(0,1,InetAddress.getByAddress(byteArrayOf(127,0,0,1))).use {server ->
            server.soTimeout=500
            val admitted=java.util.concurrent.CountDownLatch(1)
            val proceed=java.util.concurrent.CountDownLatch(1)
            val executor=Executors.newSingleThreadExecutor()
            val transport=HiddenHttpTransport.tor(server.localPort,beforeExecute={admitted.countDown();check(proceed.await(5,java.util.concurrent.TimeUnit.SECONDS))},authorized={true})
            val result=executor.submit<Int> {transport.request("http://unresolvable-hidden.invalid/","GET").statusCode}
            try {
                assertTrue(admitted.await(5,java.util.concurrent.TimeUnit.SECONDS))
                transport.close();proceed.countDown()
                assertEquals(403,result.get().toInt())
                try {server.accept().close();fail("Closed transport opened a SOCKS socket")} catch(_:java.net.SocketTimeoutException) {}
            } finally {proceed.countDown();transport.close();executor.shutdownNow()}
        }
    }

    @Test fun socksSendsDomainWithoutResolvingItLocallyAndNeverFallsBackWhenProxyCloses() {
        ServerSocket(0,1,InetAddress.getByAddress(byteArrayOf(127,0,0,1))).use {server ->
            val executor=Executors.newSingleThreadExecutor()
            val observed=executor.submit<String> {
                server.accept().use {socket ->
                    socket.soTimeout=10000
                    val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
                    check(input.readUnsignedByte()==5);val methods=input.readUnsignedByte();repeat(methods){input.readUnsignedByte()}
                    output.write(byteArrayOf(5,0));output.flush()
                    check(input.readUnsignedByte()==5);check(input.readUnsignedByte()==1);input.readUnsignedByte()
                    check(input.readUnsignedByte()==3) {"Origin must be a domain, never locally resolved IP"}
                    val bytes=ByteArray(input.readUnsignedByte());input.readFully(bytes);input.readUnsignedShort()
                    output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,80));output.flush()
                    var ending="";while(!ending.endsWith("\r\n\r\n")){ending=(ending+input.readUnsignedByte().toChar()).takeLast(4)}
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nsafe".toByteArray());output.flush()
                    String(bytes)
                }
            }
            val transport=HiddenHttpTransport.tor(server.localPort){true}
            try {
                val response=transport.request("http://unresolvable-hidden.invalid/probe","GET")
                assertEquals(200,response.statusCode)
                assertEquals("safe",response.data.bufferedReader().use {it.readText()})
                assertEquals("unresolvable-hidden.invalid",observed.get())
                assertEquals(403,transport.request("http://unresolvable-hidden.invalid/probe","POST").statusCode)
            } finally {transport.close();executor.shutdownNow()}
        }
        val closed=ServerSocket(0);val port=closed.localPort;closed.close()
        HiddenHttpTransport.tor(port){true}.use {transport ->assertEquals(403,transport.request("https://unresolvable-hidden.invalid/","GET").statusCode)}
    }
}
