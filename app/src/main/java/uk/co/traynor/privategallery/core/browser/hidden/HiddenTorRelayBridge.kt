package uk.co.traynor.privategallery.core.browser.hidden

import java.io.Closeable
import java.io.DataInputStream
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Sole Tor underlay egress. Never resolves a name; only authenticated numeric relay TCP. */
internal class HiddenTorRelayBridge(private val protect:(Socket)->Boolean,private val authorized:()->Boolean):Closeable {
    val username="tor"
    val password=java.util.UUID.randomUUID().toString()
    private val closed=AtomicBoolean()
    private val gate=Any()
    private val sockets=ConcurrentHashMap.newKeySet<Socket>()
    private val capacity=Semaphore(32)
    private val executor=Executors.newCachedThreadPool()
    private val authorityWatch=Executors.newSingleThreadScheduledExecutor()
    private val server=ServerSocket(0,32,InetAddress.getByAddress(byteArrayOf(127,0,0,1)))
    val port get()=server.localPort
    init {authorityWatch.scheduleWithFixedDelay({if(!closed.get() && !authorized())close()},100,100,TimeUnit.MILLISECONDS);executor.execute {
        while(!closed.get()) {
            val client=try {server.accept()} catch(_:Exception){break}
            if(!capacity.tryAcquire()){client.close();continue}
            try {track(client)}catch(_:Exception){client.close();capacity.release();continue}
            executor.execute {try {serve(client)}catch(_:Exception){}finally{sockets.remove(client);runCatching{client.close()};capacity.release()}}
        }
    }}
    private fun allowed(){check(!closed.get() && authorized())}
    private fun track(socket:Socket)=synchronized(gate){allowed();sockets.add(socket)}
    private fun serve(client:Socket) {
        client.soTimeout=30_000
        val input=DataInputStream(client.getInputStream());val output=client.getOutputStream()
        require(input.readUnsignedByte()==5)
        val methods=ByteArray(input.readUnsignedByte());input.readFully(methods)
        require(methods.contains(2.toByte()))
        output.write(byteArrayOf(5,2));output.flush()
        require(input.readUnsignedByte()==1)
        val user=ByteArray(input.readUnsignedByte());input.readFully(user)
        val pass=ByteArray(input.readUnsignedByte());input.readFully(pass)
        val accepted=java.security.MessageDigest.isEqual(user,username.toByteArray()) && java.security.MessageDigest.isEqual(pass,password.toByteArray())
        user.fill(0);pass.fill(0);require(accepted)
        output.write(byteArrayOf(1,0));output.flush()
        require(input.readUnsignedByte()==5 && input.readUnsignedByte()==1 && input.readUnsignedByte()==0)
        val bytes=when(input.readUnsignedByte()){1->ByteArray(4);4->ByteArray(16);else->throw java.io.IOException("Numeric relay required")}
        input.readFully(bytes);val address=InetAddress.getByAddress(bytes);val port=input.readUnsignedShort()
        require(port in 1..65535 && HiddenVpnProxy.publicAddress(address))
        val upstream=Socket()
        try {
            track(upstream)
            synchronized(gate){allowed();check(protect(upstream));allowed()}
            upstream.connect(InetSocketAddress(address,port),30_000);allowed()
            output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,0));output.flush();client.soTimeout=0
            executor.execute {try{copy(input,upstream.getOutputStream());runCatching{upstream.shutdownOutput()}}catch(_:Exception){runCatching{upstream.close()}}}
            copy(upstream.getInputStream(),output)
        }finally{sockets.remove(upstream);runCatching{upstream.close()}}
    }
    private fun copy(input:java.io.InputStream,output:java.io.OutputStream) {
        val bytes=ByteArray(32*1024)
        try{while(true){allowed();val n=input.read(bytes);if(n<0)return;allowed();output.write(bytes,0,n);output.flush()}}finally{bytes.fill(0)}
    }
    override fun close(){synchronized(gate){if(!closed.compareAndSet(false,true))return;runCatching{server.close()};sockets.forEach{runCatching{it.close()}};sockets.clear()};authorityWatch.shutdownNow();executor.shutdownNow()}
}
