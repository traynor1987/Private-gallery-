package uk.co.traynor.privategallery.core.browser.hidden

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable
import java.io.DataInputStream

/** Tor-facing SOCKS uses a private Unix endpoint and DOMAIN requests, never Android origin DNS. */
internal object HiddenTorSockets {
    class Duplex(private val socket:LocalSocket):Closeable {
        val input get()=socket.inputStream
        val output get()=socket.outputStream
        fun shutdownOutput(){socket.shutdownOutput()}
        override fun close(){socket.close()}
    }
    fun open(path:String,host:String,port:Int,register:(Closeable)->Unit={}):Duplex {
        require(port in 1..65535)
        val name=java.net.IDN.toASCII(host).toByteArray(Charsets.US_ASCII);require(name.size in 1..255)
        val socket=LocalSocket();val duplex=Duplex(socket);register(duplex)
        try {
            // LocalSocket creates its native descriptor lazily in connect/bind. Its
            // setSoTimeout delegates directly to LocalSocketImpl.setOption and fails
            // with "socket not created" before that descriptor exists.
            socket.connect(LocalSocketAddress(path,LocalSocketAddress.Namespace.FILESYSTEM))
            socket.soTimeout=30_000
            val input=DataInputStream(socket.inputStream);val output=socket.outputStream
            output.write(byteArrayOf(5,1,0));output.flush();require(input.readUnsignedByte()==5 && input.readUnsignedByte()==0)
            output.write(byteArrayOf(5,1,0,3,name.size.toByte()));output.write(name);output.write(byteArrayOf((port ushr 8).toByte(),port.toByte()));output.flush()
            require(input.readUnsignedByte()==5 && input.readUnsignedByte()==0 && input.readUnsignedByte()==0)
            val count=when(input.readUnsignedByte()){1->4;4->16;3->input.readUnsignedByte();else->throw java.io.IOException("Invalid Tor SOCKS response")}
            val ignored=ByteArray(count+2);input.readFully(ignored)
            return duplex
        }catch(failure:Throwable){socket.close();throw failure}
    }
}
