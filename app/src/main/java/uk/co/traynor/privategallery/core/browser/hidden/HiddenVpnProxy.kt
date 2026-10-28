package uk.co.traynor.privategallery.core.browser.hidden

import android.net.Network
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Body-transparent HTTP/CONNECT bridge. DNS and every upstream socket use one immutable VPN lease. */
internal class HiddenVpnProxy(
    private val socketFactory:javax.net.SocketFactory,
    private val resolve:(String)->List<InetAddress>,
    private val authorized: () -> Boolean,
) : Closeable {
    constructor(network:Network,authorized:()->Boolean):this(network.socketFactory,{network.getAllByName(it).toList()},authorized)
    val password = java.util.UUID.randomUUID().toString()
    private val credential = java.util.Base64.getEncoder().encodeToString(("hidden:"+password).toByteArray(Charsets.UTF_8))
    private val closed = AtomicBoolean()
    private val admission = Any()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val slots = Semaphore(32)
    private val workers = Executors.newCachedThreadPool()
    private val server = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),0),32) }
    val port get() = server.localPort
    init { workers.execute {
        while(!closed.get()) {
            val socket=try {server.accept()} catch(_:Exception) {break}
            if(!slots.tryAcquire()) {socket.close();continue}
            synchronized(admission) {if(closed.get() || !authorized()) {socket.close();slots.release();return@execute};sockets.add(socket)}
            workers.execute { try {serve(socket)} catch(_:Exception) {} finally {sockets.remove(socket);runCatching {socket.close()};slots.release()} }
        }
    } }
    private fun checkAllowed() {check(!closed.get() && authorized())}
    private fun track(socket:Socket) = synchronized(admission) {checkAllowed();sockets.add(socket)}
    private fun serve(client:Socket) {
        client.soTimeout=30_000
        val input=client.getInputStream()
        val first=line(input)
        val parts=first.split(' ')
        require(parts.size==3 && parts[0].matches(Regex("[A-Z]{1,16}")) && parts[2] in setOf("HTTP/1.0","HTTP/1.1"))
        val headers=mutableListOf<String>()
        var size=first.length
        while(true) {val h=line(input);size+=h.length;require(size<=32_768);if(h.isEmpty())break;require(!h.startsWith(' ') && !h.startsWith('\t') && h.contains(':') && h.none {it=='\r' || it=='\n' || it=='\u0000'});headers+=h}
        val supplied=headers.singleOrNull {it.substringBefore(':').equals("Proxy-Authorization",true)}?.substringAfter(':')?.trim()
        if(supplied==null || !java.security.MessageDigest.isEqual(supplied.toByteArray(),("Basic "+credential).toByteArray())) {
            client.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"Hidden VPN session\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1));return
        }
        val connect=parts[0]=="CONNECT"
        val uri=if(connect)URI("https://${parts[1]}/") else URI(parts[1])
        require(HiddenConnectionPolicy.safeUrl(uri.toString(),HiddenConnectionPolicy.Mode.VPN_ONLY))
        require(connect || uri.scheme=="http")
        val host=checkNotNull(uri.host)
        val port=if(uri.port<0)if(connect)443 else 80 else uri.port
        // Resolve once through the VPN and reject the entire answer if any address is private.
        val addresses=resolve(host)
        require(addresses.isNotEmpty() && addresses.all(::publicAddress))
        checkAllowed()
        val upstream=socketFactory.createSocket()
        try {
            track(upstream);upstream.connect(InetSocketAddress(addresses.first(),port),30_000)
            checkAllowed()
            val output=upstream.getOutputStream()
            if(connect) client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            else {
                val path=uri.rawPath.orEmpty().ifEmpty {"/"}+(uri.rawQuery?.let {"?$it"} ?: "")
                output.write("${parts[0]} $path ${parts[2]}\r\n".toByteArray(Charsets.ISO_8859_1))
                // A new origin connection per request. No proxy credentials enter the website.
                headers.filterNot {it.substringBefore(':').lowercase() in setOf("proxy-authorization","proxy-connection","connection","host")}.forEach {output.write((it+"\r\n").toByteArray(Charsets.ISO_8859_1))}
                output.write("Host: ${uri.rawAuthority}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            }
            output.flush();client.soTimeout=0
            workers.execute {try {pump(input,output);runCatching {upstream.shutdownOutput()}} catch(_:Exception) {runCatching {upstream.close()};runCatching {client.close()}}}
            pump(upstream.getInputStream(),client.getOutputStream())
        } finally {sockets.remove(upstream);runCatching {upstream.close()}}
    }
    private fun pump(input:InputStream,output:java.io.OutputStream) {
        val buffer=ByteArray(32*1024)
        try {while(true) {checkAllowed();val n=input.read(buffer);if(n<0)break;checkAllowed();output.write(buffer,0,n);output.flush()}} finally {buffer.fill(0)}
    }
    override fun close() {
        synchronized(admission) {if(!closed.compareAndSet(false,true))return;runCatching {server.close()};sockets.forEach {runCatching {it.close()}};sockets.clear()}
        workers.shutdownNow()
    }
    companion object {
        internal fun publicAddress(address:InetAddress):Boolean {
            if(address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress)return false
            val b=address.address
            if(b.size==4) {val a=b[0].toInt() and 255;val c=b[1].toInt() and 255;return a !in setOf(0,127) && a<224 && !(a==100 && c in 64..127) && !(a==169 && c==254) && !(a==198 && c in 18..19)}
            return b.size==16 && (b[0].toInt() and 0xfe)!=0xfc
        }
        private fun line(input:InputStream):String {
            val bytes=java.io.ByteArrayOutputStream()
            while(true) {val value=input.read();require(value>=0 && bytes.size()<8192);if(value==10)break;bytes.write(value)}
            val raw=bytes.toByteArray();require(raw.isNotEmpty() && raw.last()==13.toByte());return String(raw,0,raw.size-1,Charsets.ISO_8859_1)
        }
    }
}
