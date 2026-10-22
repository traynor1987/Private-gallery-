package uk.co.traynor.privategallery.core.browser.hidden

import android.net.Network
import android.webkit.WebResourceResponse
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The only Hidden HTTP egress. WebView's own network loader stays disabled. */
internal class HiddenHttpTransport private constructor(
    builder: OkHttpClient.Builder,
    private val mode: HiddenConnectionPolicy.Mode,
    private val authorized: () -> Boolean,
    private val beforeExecute: () -> Unit = {},
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val admission = Any()
    private val calls = mutableSetOf<Call>()
    private val responses = ConcurrentHashMap.newKeySet<Response>()
    private val cookies = MemoryCookies { closed.get() }
    private val client = builder.followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .cache(null).cookieJar(cookies).connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS)).build()
    companion object {
        fun tor(port: Int, beforeExecute: () -> Unit = {}, authorized: () -> Boolean): HiddenHttpTransport {
            require(port in 1..65535)
            return HiddenHttpTransport(OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)), port)))
                .dns(object:Dns { override fun lookup(hostname:String):List<InetAddress> = throw UnknownHostException("Local DNS prohibited") }), HiddenConnectionPolicy.Mode.TOR_ONLY, authorized, beforeExecute)
        }
        fun vpn(network: Network, authorized: () -> Boolean): HiddenHttpTransport = HiddenHttpTransport(
            OkHttpClient.Builder().proxy(Proxy.NO_PROXY).socketFactory(network.socketFactory).dns(object:Dns {
                override fun lookup(hostname:String):List<InetAddress> =
                if(hostname.endsWith(".onion",true)) throw UnknownHostException("Onion requires Tor") else network.getAllByName(hostname).filter { !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isSiteLocalAddress && !it.isMulticastAddress }
                    .also { if(it.isEmpty()) throw UnknownHostException("Private destinations prohibited") }
            }), HiddenConnectionPolicy.Mode.VPN_ONLY, authorized)
        const val RESULT_HEADER = "X-Private-Gallery-Result"
        fun localError(code:Int, kind:String, message:String):WebResourceResponse = WebResourceResponse(
            "text/plain","UTF-8",code,"Private browser",mapOf("Cache-Control" to "no-store",RESULT_HEADER to kind),
            ByteArrayInputStream(message.toByteArray()))
        fun blocked():WebResourceResponse=localError(451,"policy","Local privacy policy blocked this address or revoked session. No alternate route was used.")
    }
    private class PolicyRejected:IOException()
    private class UnsupportedRedirect:IOException()
    private fun checkAllowed() { if(closed.get() || !authorized()) throw IOException("Hidden connection unavailable") }
    private fun open(url: String, download: Boolean = false, headers:Map<String,String> = emptyMap()): Response {
        checkAllowed()
        if(!HiddenConnectionPolicy.safeUrl(url,mode))throw PolicyRejected()
        val request=Request.Builder().url(url).header("User-Agent", "PrivateGalleryHidden/1.6")
            .header("Accept", "text/html,application/xhtml+xml,image/*,video/*,*/*;q=0.5")
        // Never import renderer cookies, credentials, Host, Origin or Referer. Native cookies own the session.
        headers.forEach { (key,value) ->
            if(key.lowercase() in setOf("user-agent","accept","accept-language","range","if-range") &&
                value.length<=4096 && !value.contains('\r') && !value.contains('\n'))request.header(key,value)
        }
        val call = client.newCall(request.build())
        if (download) call.timeout().timeout(60, TimeUnit.MINUTES)
        synchronized(admission) { checkAllowed(); calls.add(call) }
        try {
            beforeExecute()
            val response = call.execute()
            synchronized(admission) {
                try { checkAllowed(); responses.add(response); return response }
                catch(e:Exception) {response.close();throw e}
            }
        } finally { synchronized(admission) {calls.remove(call)} }
    }
    private fun following(url:String,headers:Map<String,String>):Pair<Response,Int> {
        var next=url.toHttpUrlOrNull() ?: throw PolicyRejected()
        val visited=mutableSetOf<String>()
        var hops=0
        while(true) {
            if(!visited.add(next.toString()) || hops>10)throw UnsupportedRedirect()
            val response=open(next.toString(),headers=headers)
            if(response.code !in setOf(301,302,303,307,308))return response to hops
            val target=try {response.header("Location")?.let {next.resolve(it)}} finally {release(response)}
                ?: throw UnsupportedRedirect()
            if(!HiddenConnectionPolicy.safeUrl(target.toString(),mode) ||
                (next.isHttps && !target.isHttps))throw PolicyRejected()
            next=target;hops++
        }
    }

    private fun release(response: Response) { responses.remove(response); response.close() }
    private fun stream(response: Response, limit: Long, requireComplete: Boolean = false): InputStream {
        val body = checkNotNull(response.body)
        val expected = body.contentLength().takeIf { it >= 0 }
        val input = if (requireComplete) uk.co.traynor.privategallery.core.browser.CompleteResponseInputStream(body.byteStream(), expected) else body.byteStream()
        return object : FilterInputStream(input) {
            private var count = 0L
            private var eof = false
            override fun read(): Int { val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                checkAllowed()
                if(length==0)return 0
                if(eof)return -1
                val read = input.read(bytes,offset,length)
                if(read>0) { count+=read; if(count>limit) {close();throw IOException("Resource too large")} }
                try {checkAllowed()} catch(e:Exception) {bytes.fill(0,offset,offset+maxOf(0,read));close();throw e}
                if(read<0) {
                    eof=true;close()
                    if(requireComplete && expected != null && count != expected) throw IOException("Incomplete download")
                }
                return read
            }
            override fun close() { release(response) }
        }
    }
    fun request(url: String, method: String, requestHeaders:Map<String,String> = emptyMap(),
                onMainRedirect:((String,Int)->Boolean)? = null, resource:Boolean=false): WebResourceResponse {
        if(method!="GET") return localError(405,"unsupported","This private renderer supports GET navigation only. Forms and uploads are unavailable.")
        var owned: Response? = null
        return try {
            val (response,hops)=following(url,requestHeaders)
            owned=response
            // Android cannot return 3xx from interception. Navigate the main frame to its canonical URL;
            // never render another origin/path's HTML under the old URL. Final GET is reissued there.
            if(hops>0 && onMainRedirect!=null) {
                if(!onMainRedirect(response.request.url.toString(),hops))return blocked()
                return WebResourceResponse("text/plain","UTF-8",204,"Navigation",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(byteArrayOf()))
            }
            if(response.code in 300..399)throw UnsupportedRedirect()
            val type=response.body?.contentType()
            if(hops>0 && resource && response.isSuccessful) {
                val mime=type?.let {"${it.type}/${it.subtype}"}.orEmpty()
                if(mime=="text/css") {
                    // A stylesheet redirect must establish its final base URL for relative imports/images.
                    // Import the canonical stylesheet instead of serving its bytes under the source URL.
                    val target=response.request.url.toString().replace("\\","\\\\").replace("\"","\\\"")
                    return WebResourceResponse("text/css","UTF-8",200,"Stylesheet navigation",mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream("@import url(\"$target\");".toByteArray()))
                }
                if(mime in setOf("image/svg+xml","text/html","application/xhtml+xml","application/xml","text/xml"))
                    throw UnsupportedRedirect()
            }
            val headers=response.headers.toMultimap().mapValues {it.value.joinToString(", ")}.toMutableMap()
            listOf("Set-Cookie","Content-Length","Content-Encoding","Connection","Transfer-Encoding","Refresh",RESULT_HEADER).forEach { key -> headers.keys.filter {it.equals(key,true)}.forEach(headers::remove) }
            headers["Cache-Control"]="no-store"
            headers["X-DNS-Prefetch-Control"]="off"
            headers["Content-Security-Policy"]="script-src 'none'; worker-src 'none'; connect-src 'none'; object-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none'"
            val result=WebResourceResponse(type?.let {"${it.type}/${it.subtype}"} ?: "application/octet-stream",type?.charset()?.name() ?: "UTF-8",
                response.code,response.message.ifBlank {"Response"},headers,stream(response,32L*1024*1024))
            owned=null
            result
        } catch(_:PolicyRejected) {blocked()}
        catch(_:UnsupportedRedirect) {localError(501,"unsupported","Redirect is missing, loops, exceeds the limit, or cannot be represented safely.")}
        catch(_:Exception) {localError(503,"transport","Private transport failed (network, DNS, TLS or session unavailable). No direct-network fallback was attempted.")}
        finally {owned?.let(::release)}
    }
    /** A real request through this exact Tor/VPN client; no redirect or fallback route. */
    fun verifyReady() {
        val response = open("https://example.com/")
        try {
            if (response.code != 200) throw IOException("Hidden readiness HTTP response rejected")
            val body = response.body ?: throw IOException("Hidden readiness response missing")
            val bytes = ByteArray(256)
            if (body.byteStream().read(bytes) <= 0) throw IOException("Hidden readiness response empty")
            checkAllowed()
        } finally { release(response) }
    }
    fun download(url: String, progress: (Long, Long) -> Unit = { _, _ -> }, save: (String, InputStream) -> Unit) {
        val response=open(url, download = true)
        try {
            check(response.code == 200 && response.header("Content-Range") == null) { "Partial download is unsupported" }
            val type=response.body?.contentType()?.let {"${it.type}/${it.subtype}"} ?: ""
            require(type.startsWith("image/") || type in setOf("video/mp4", "video/webm", "video/quicktime", "video/x-matroska")) {
                "Segmented or unsupported media cannot be saved directly"
            }
            require(!URI(url).path.orEmpty().substringAfterLast('.').lowercase().let { it in setOf("ts", "m4s", "cmfv", "cmfa") }) {
                "A media segment cannot be saved as a complete video"
            }
            val total=response.body?.contentLength() ?: -1
            var completed=0L
            progress(0,total)
            val observed=object:FilterInputStream(stream(response,2L*1024*1024*1024,requireComplete=true)) {
                override fun read():Int {val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255}
                override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                    val count=`in`.read(bytes,offset,length)
                    if(count>0){completed+=count;progress(completed,total)}
                    return count
                }
            }
            observed.use {save(type,it)}
        } finally {release(response)}
    }
    override fun close() {
        synchronized(admission) {
            if(!closed.compareAndSet(false,true))return
            calls.forEach {it.cancel()}
            client.dispatcher.cancelAll();responses.toList().forEach(::release)
        }
        client.connectionPool.evictAll();client.dispatcher.executorService.shutdown();cookies.clear()
    }
    private class MemoryCookies(private val closed: () -> Boolean) : CookieJar {
        private val values=mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if(closed())return
            cookies.forEach { cookie ->values.removeAll {it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path};if(values.size<256)values.add(cookie)}
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {values.removeAll {it.expiresAt<System.currentTimeMillis()};return values.filter {it.matches(url)}}
        @Synchronized fun clear() {values.clear()}
    }
}
