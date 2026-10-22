package uk.co.traynor.privategallery.core.browser.hidden

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Controlled SOCKS/HTTP wire fixture. This is not a real Tor or WireGuard route. */
class HiddenNavigationTransportTest {
    @Test fun ordinaryGetFollowsRelativeAndCrossHostRedirectsThroughSameSocksClient() {
        fixture(listOf(
            "302 Found\r\nLocation: /next\r\nSet-Cookie: isolated=1; Path=/",
            "301 Moved\r\nLocation: http://destination.invalid/final",
            "200 OK\r\nContent-Type: text/html"
        )) { transport, observed ->
            val response=transport.request("http://source.invalid/start","GET")
            assertEquals(200,response.statusCode)
            assertEquals("fixture",response.data.bufferedReader().use {it.readText()})
            val requests=observed()
            assertEquals(listOf("source.invalid","source.invalid","destination.invalid"),requests.map {it.first})
            assertTrue(requests[1].second.contains("isolated=1"))
            assertFalse(requests[2].second.contains("isolated=1"))
        }
    }
    @Test fun transportFailureIsNotReportedAsWebsiteOrPolicy403() {
        val server=ServerSocket(0);val port=server.localPort;server.close()
        HiddenHttpTransport.tor(port){true}.use {transport ->
            assertEquals(503,transport.request("https://bbc.co.uk/","GET").statusCode)
        }
    }
    @Test fun realWebsite403RetainsItsBodyAndIsNotLocalPolicyBlock() {
        fixture(listOf("403 Forbidden\r\nContent-Type: text/plain")) { transport,_ ->
            val response=transport.request("http://source.invalid/","GET")
            assertEquals(403,response.statusCode)
            assertEquals("fixture",response.data.bufferedReader().use {it.readText()})
            assertFalse(response.responseHeaders.orEmpty().containsKey("X-Private-Gallery-Result"))
        }
    }
    private fun fixture(replies:List<String>, test:(HiddenHttpTransport,()->List<Pair<String,String>>)->Unit) {
        ServerSocket(0,10,InetAddress.getByAddress(byteArrayOf(127,0,0,1))).use {server ->
            server.soTimeout=10000
            val executor=Executors.newSingleThreadExecutor()
            val observed=executor.submit<List<Pair<String,String>>> {
                replies.map {reply -> server.accept().use {socket ->
                    socket.soTimeout=10000
                    val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
                    check(input.readUnsignedByte()==5);repeat(input.readUnsignedByte()){input.readUnsignedByte()}
                    output.write(byteArrayOf(5,0));output.flush()
                    check(input.readUnsignedByte()==5);check(input.readUnsignedByte()==1);input.readUnsignedByte()
                    check(input.readUnsignedByte()==3) {"Must send unresolved hostname to SOCKS"}
                    val host=ByteArray(input.readUnsignedByte());input.readFully(host);input.readUnsignedShort()
                    output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,80));output.flush()
                    val header=StringBuilder();while(!header.endsWith("\r\n\r\n"))header.append(input.readUnsignedByte().toChar())
                    output.write("HTTP/1.1 $reply\r\nContent-Length: 7\r\nConnection: close\r\n\r\nfixture".toByteArray());output.flush()
                    String(host) to header.toString()
                }}
            }
            HiddenHttpTransport.tor(server.localPort){true}.use {transport ->
                try {test(transport) {observed.get(15,TimeUnit.SECONDS)}} finally {server.close();executor.shutdownNow()}
            }
        }
    }
}
