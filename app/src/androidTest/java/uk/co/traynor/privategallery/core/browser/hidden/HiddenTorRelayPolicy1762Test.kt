package uk.co.traynor.privategallery.core.browser.hidden

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.*
import java.util.concurrent.atomic.AtomicInteger

/** Real loopback SOCKS parser. A rejecting protect adapter guarantees no underlay connect. */
class HiddenTorRelayPolicy1762Test {
    @Test fun relayRequiresSecretNumericPublicTargetAndSuccessfulProtectBeforeAnyConnect() {
        val protections=AtomicInteger()
        HiddenTorRelayBridge({protections.incrementAndGet();false},{true}).use {bridge ->
            fun request(type:Int,address:ByteArray,password:String=bridge.password) {
                Socket(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),bridge.port).use {socket ->
                    socket.soTimeout=2000
                    val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
                    output.write(byteArrayOf(5,1,2));output.flush();assertEquals(5,input.readUnsignedByte());assertEquals(2,input.readUnsignedByte())
                    val user=bridge.username.toByteArray();val pass=password.toByteArray()
                    output.write(byteArrayOf(1,user.size.toByte()));output.write(user);output.write(pass.size);output.write(pass);output.flush()
                    if(password!=bridge.password){try {assertEquals(-1,input.read())}catch(expected:java.io.IOException){};return}
                    assertEquals(1,input.readUnsignedByte());assertEquals(0,input.readUnsignedByte())
                    output.write(byteArrayOf(5,1,0,type.toByte()));output.write(address);output.write(byteArrayOf(1,187.toByte()));output.flush()
                    try {assertEquals(-1,input.read())}catch(expected:java.io.IOException){}
                }
            }
            request(1,byteArrayOf(203.toByte(),0,113,10),"wrong-secret");assertEquals(0,protections.get())
            request(3,byteArrayOf(11)+"example.com".toByteArray());assertEquals("Domains never enter protected egress",0,protections.get())
            request(1,byteArrayOf(127,0,0,1));assertEquals(0,protections.get())
            request(1,byteArrayOf(10,0,0,1));assertEquals(0,protections.get())
            request(4,InetAddress.getByName("fc00::1").address);assertEquals(0,protections.get())
            request(1,byteArrayOf(203.toByte(),0,113,10));assertEquals("Valid numeric request reaches protect; false prevents connect",1,protections.get())
        }
    }
    @Test fun unauthenticatedMethodCannotReachProtect() {
        val protections=AtomicInteger()
        HiddenTorRelayBridge({protections.incrementAndGet();false},{true}).use {bridge ->
            Socket(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),bridge.port).use {socket ->
                socket.soTimeout=2000;socket.getOutputStream().write(byteArrayOf(5,1,0));socket.getOutputStream().flush()
                try {assertEquals(-1,socket.getInputStream().read())}catch(expected:java.io.IOException){}
            }
            assertEquals(0,protections.get())
        }
    }
}
