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
    private val mode:HiddenConnectionPolicy.Mode=HiddenConnectionPolicy.Mode.VPN_ONLY,
    private val torUnixPath:String?=null,
) : Closeable {
    constructor(context:android.content.Context,network:Network,authorized:()->Boolean):this(network.socketFactory,{uk.co.traynor.privategallery.core.vpn.OwnedVpnDns.lookup(context,network,it,authorized)},authorized)
    val password = java.util.UUID.randomUUID().toString()
    private val credential = java.util.Base64.getEncoder().encodeToString(("hidden:"+password).toByteArray(Charsets.UTF_8))
    private val closed = AtomicBoolean()
    private val admission = Any()
    private val sockets = ConcurrentHashMap.newKeySet<java.io.Closeable>()
    private val slots = Semaphore(32)
    private val workers = Executors.newCachedThreadPool()
    private val authorityWatch=Executors.newSingleThreadScheduledExecutor()
    private val server = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),0),32) }
    val port get() = server.localPort
    init {authorityWatch.scheduleWithFixedDelay({if(!closed.get() && !authorized())close()},100,100,java.util.concurrent.TimeUnit.MILLISECONDS);workers.execute {
        while(!closed.get()) {
            val socket=try {server.accept()} catch(_:Exception) {break}
            if(!slots.tryAcquire()) {socket.close();continue}
            synchronized(admission) {if(closed.get() || !authorized()) {socket.close();slots.release();return@execute};sockets.add(socket)}
            workers.execute { try {serve(socket)} catch(_:Exception) {} finally {sockets.remove(socket);runCatching {socket.close()};slots.release()} }
        }
    } }
    private fun checkAllowed() {check(!closed.get() && authorized())}
    private fun track(socket:java.io.Closeable) = synchronized(admission) {checkAllowed();sockets.add(socket)}
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
        require(HiddenConnectionPolicy.safeUrl(uri.toString(),mode))
        require(connect || uri.scheme=="http")
        val host=checkNotNull(uri.host)
        val port=if(uri.port<0)if(connect)443 else 80 else uri.port
        checkAllowed()
        val upstream:java.io.Closeable
        val upstreamInput:java.io.InputStream
        val upstreamOutput:java.io.OutputStream
        val shutdown:()->Unit
        if(torUnixPath!=null) {
            var registered:java.io.Closeable?=null
            val stream=try {HiddenTorSockets.open(torUnixPath,host,port,{registered=it;track(it)})}
                catch(failure:Throwable){registered?.let {sockets.remove(it)};throw failure}
            upstream=stream;upstreamInput=stream.input;upstreamOutput=stream.output;shutdown={stream.shutdownOutput()}
        } else {
            val addresses=resolve(host)
            require(addresses.isNotEmpty() && addresses.all(::publicAddress))
            val socket=socketFactory.createSocket();track(socket)
            try {socket.connect(InetSocketAddress(addresses.first(),port),30_000)}catch(failure:Throwable){sockets.remove(socket);socket.close();throw failure}
            upstream=socket;upstreamInput=socket.getInputStream();upstreamOutput=socket.getOutputStream();shutdown={socket.shutdownOutput()}
        }
        try {
            checkAllowed()
            val output=upstreamOutput
            if(connect) client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            else {
                val path=uri.rawPath.orEmpty().ifEmpty {"/"}+(uri.rawQuery?.let {"?$it"} ?: "")
                output.write("${parts[0]} $path ${parts[2]}\r\n".toByteArray(Charsets.ISO_8859_1))
                // A new origin connection per request. No proxy credentials enter the website.
                headers.filterNot {it.substringBefore(':').lowercase() in setOf("proxy-authorization","proxy-connection","connection","host")}.forEach {output.write((it+"\r\n").toByteArray(Charsets.ISO_8859_1))}
                output.write("Host: ${uri.rawAuthority}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            }
            output.flush();client.soTimeout=0
            workers.execute {try {pump(input,output);runCatching {shutdown()}} catch(_:Exception) {runCatching {upstream.close()};runCatching {client.close()}}}
            try {pump(upstreamInput,client.getOutputStream())}catch(failure:Exception) {
                if(uk.co.traynor.privategallery.BuildConfig.DEBUG && torUnixPath!=null)runCatching {
                    android.util.Log.i("HiddenTorSocket","phase=upstream_response cause=${failure.javaClass.simpleName}")
                }
                throw failure
            }
        } finally {sockets.remove(upstream);runCatching {upstream.close()}}
    }
    private fun pump(input:InputStream,output:java.io.OutputStream) {
        val buffer=ByteArray(32*1024)
        try {while(true) {checkAllowed();val n=input.read(buffer);if(n<0)break;checkAllowed();output.write(buffer,0,n);output.flush()}} finally {buffer.fill(0)}
    }
    override fun close() {
        synchronized(admission) {if(!closed.compareAndSet(false,true))return;runCatching {server.close()};sockets.forEach {runCatching {it.close()}};sockets.clear()}
        authorityWatch.shutdownNow();workers.shutdownNow()
    }
    companion object {
        fun tor(unixPath:String,authorized:()->Boolean)=HiddenVpnProxy(javax.net.SocketFactory.getDefault(),{throw java.net.UnknownHostException("Tor origin DNS prohibited")},authorized,HiddenConnectionPolicy.Mode.TOR_ONLY,unixPath)
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
