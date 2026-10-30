package uk.co.traynor.privategallery.core.browser.hidden

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable
import java.io.DataInputStream

/** Tor-facing SOCKS uses a private Unix endpoint and DOMAIN requests, never Android origin DNS. */
internal object HiddenTorSockets {
    class Duplex(private val socket:LocalSocket):Closeable {
        private val closed=java.util.concurrent.atomic.AtomicBoolean()
        fun checkOpen(){check(!closed.get()) {"Tor socket revoked"}}
        val input get()=socket.inputStream
        val output get()=socket.outputStream
        fun shutdownOutput(){socket.shutdownOutput()}
        override fun close(){
            closed.set(true)
            // close(2) alone leaves a concurrent blocking Unix read holding the socket alive.
            // Shutdown first wakes SOCKS negotiation/pumps and makes revocation visible to its peer.
            runCatching {socket.shutdownInput()}
            runCatching {socket.shutdownOutput()}
            socket.close()
        }
    }
    fun open(path:String,host:String,port:Int,register:(Closeable)->Unit={},handshakeTimeoutMs:Int=30_000):Duplex {
        require(handshakeTimeoutMs==30_000 || (uk.co.traynor.privategallery.BuildConfig.DEBUG && handshakeTimeoutMs==60_000))
        require(port in 1..65535)
        val name=java.net.IDN.toASCII(host).toByteArray(Charsets.US_ASCII);require(name.size in 1..255)
        val socket=LocalSocket();val duplex=Duplex(socket);register(duplex)
        var phase="unix_connect"
        try {
            // LocalSocket creates its native descriptor lazily in connect/bind. Its
            // setSoTimeout delegates directly to LocalSocketImpl.setOption and fails
            // with "socket not created" before that descriptor exists.
            duplex.checkOpen()
            socket.connect(LocalSocketAddress(path,LocalSocketAddress.Namespace.FILESYSTEM))
            duplex.checkOpen()
            socket.soTimeout=handshakeTimeoutMs
            val input=DataInputStream(socket.inputStream);val output=socket.outputStream
            phase="socks_greeting"
            output.write(byteArrayOf(5,1,0));output.flush();require(input.readUnsignedByte()==5 && input.readUnsignedByte()==0)
            phase="socks_connect"
            output.write(byteArrayOf(5,1,0,3,name.size.toByte()));output.write(name);output.write(byteArrayOf((port ushr 8).toByte(),port.toByte()));output.flush()
            require(input.readUnsignedByte()==5 && input.readUnsignedByte()==0 && input.readUnsignedByte()==0)
            phase="socks_reply_address"
            val count=when(input.readUnsignedByte()){1->4;4->16;3->input.readUnsignedByte();else->throw java.io.IOException("Invalid Tor SOCKS response")}
            val ignored=ByteArray(count+2);input.readFully(ignored)
            // The longer debug fixture budget applies only to cold SOCKS setup.
            socket.soTimeout=30_000
            return duplex
        }catch(failure:Throwable){
            if(uk.co.traynor.privategallery.BuildConfig.DEBUG)runCatching {
                android.util.Log.i("HiddenTorSocket","phase=$phase cause=${failure.javaClass.simpleName}")
            }
            duplex.close();throw failure
        }
    }
}
