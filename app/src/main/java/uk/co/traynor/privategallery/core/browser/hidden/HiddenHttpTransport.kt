package uk.co.traynor.privategallery.core.browser.hidden

import android.net.Network
import android.webkit.WebResourceResponse
import okhttp3.*
import java.io.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The only Hidden HTTP egress. WebView's own network loader stays disabled. */
internal class HiddenHttpTransport private constructor(
    builder: OkHttpClient.Builder,
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
                .dns(object:Dns { override fun lookup(hostname:String):List<InetAddress> = throw UnknownHostException("Local DNS prohibited") }), authorized, beforeExecute)
        }
        fun vpn(network: Network, authorized: () -> Boolean): HiddenHttpTransport = HiddenHttpTransport(
            OkHttpClient.Builder().proxy(Proxy.NO_PROXY).socketFactory(network.socketFactory).dns(object:Dns {
                override fun lookup(hostname:String):List<InetAddress> =
                network.getAllByName(hostname).filter { !it.isAnyLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isSiteLocalAddress && !it.isMulticastAddress }
                    .also { if(it.isEmpty()) throw UnknownHostException("Private destinations prohibited") }
            }), authorized)
        fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream("Request blocked by Hidden privacy policy.".toByteArray()))
    }
    private fun checkAllowed() { if(closed.get() || !authorized()) throw IOException("Hidden connection unavailable") }
    private fun open(url: String, download: Boolean = false): Response {
        checkAllowed(); require(HiddenConnectionPolicy.safeUrl(url))
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "PrivateGalleryHidden/1.6")
            .header("Accept", "text/html,application/xhtml+xml,image/*,video/*,*/*;q=0.5").build())
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
    fun request(url: String, method: String): WebResourceResponse {
        if(method!="GET") return blocked()
        var owned: Response? = null
        return try {
            val response=open(url)
            owned=response
            // Interception cannot represent redirects safely. Never serve a different origin under the old URL.
            if(response.code in 300..399) {release(response);return blocked()}
            val type=response.body?.contentType()
            val headers=response.headers.toMultimap().mapValues {it.value.joinToString(", ")}.toMutableMap()
            listOf("Set-Cookie","Content-Length","Content-Encoding","Connection","Transfer-Encoding","Refresh").forEach { key -> headers.keys.filter {it.equals(key,true)}.forEach(headers::remove) }
            headers["Cache-Control"]="no-store"
            headers["X-DNS-Prefetch-Control"]="off"
            headers["Content-Security-Policy"]="script-src 'none'; worker-src 'none'; connect-src 'none'; object-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none'"
            val result=WebResourceResponse(type?.let {"${it.type}/${it.subtype}"} ?: "application/octet-stream",type?.charset()?.name() ?: "UTF-8",
                response.code,response.message.ifBlank {"Response"},headers,stream(response,32L*1024*1024))
            owned=null
            result
        } catch(_:Exception) {blocked()} finally {owned?.let(::release)}
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
            stream(response,2L*1024*1024*1024, requireComplete = true).use {save(type,it)}
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
