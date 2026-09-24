package uk.co.traynor.privategallery.core.editor

import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*

fun interface AiRequestBody { fun writeTo(output: OutputStream) }
// Deliberately not data classes: default toString must never print tokens, prompts or image data.
class AiHttpRequest(val method: String, val url: String, val headers: Map<String,String>, val body: AiRequestBody? = null, val maxResponseBytes: Int = 2 * 1024 * 1024)
class AiHttpResponse(val status: Int, val contentType: String?, val bytes: ByteArray)
fun interface AiHttpTransport { suspend fun execute(request: AiHttpRequest): AiHttpResponse }

/** Android default networking; no Browser session, cookies, disk cache, proxy override or VPN bypass.
 * Never follows redirects, including a redirect of an authenticated result download.
 */
class PrivateAiHttpTransport : AiHttpTransport {
    override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
        check(AiRemoteUrls.allowed(request.url))
        var result: ByteArray? = null
        try {
            val response = withContext(Dispatchers.IO) {
                coroutineScope {
                    val connection = URI(request.url).toURL().openConnection() as HttpsURLConnection
                    val closer = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { connection.disconnect() }
                    }
                    try {
                        connection.instanceFollowRedirects = false
                        connection.useCaches = false
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 20_000
                        connection.requestMethod = request.method
                        connection.setRequestProperty("Accept-Encoding", "identity")
                        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                        request.body?.let { body ->
                            connection.doOutput = true
                            connection.setChunkedStreamingMode(16 * 1024)
                            connection.outputStream.use { body.writeTo(it) }
                        }
                        ensureActive()
                        val code = connection.responseCode
                        // Do not read/retain provider error bodies (can echo secrets or private prompts).
                        if (code !in 200..299) AiHttpResponse(code, null, byteArrayOf())
                        else {
                            if (connection.contentLengthLong > request.maxResponseBytes) throw AiEditFailure("The provider response is too large.")
                            result = connection.inputStream.use { readBounded(it, request.maxResponseBytes) { ensureActive() } }
                            AiHttpResponse(code, connection.contentType, result!!)
                        }
                    } finally { withContext(NonCancellable) { closer.cancelAndJoin() }; connection.disconnect() }
                }
            }
            currentCoroutineContext().ensureActive()
            result = null
            return response
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: java.net.SocketTimeoutException) { throw AiEditFailure("Replicate timed out. Try again.")
        } catch (_: Exception) { throw AiEditFailure("Cannot reach Replicate. Check your connection and try again.")
        } finally { result?.fill(0) }
    }
    companion object {
        internal fun readBounded(input: InputStream, limit: Int, checkActive: () -> Unit): ByteArray {
            val chunks = mutableListOf<Pair<ByteArray,Int>>()
            var total = 0
            try {
                while (true) {
                    checkActive()
                    val chunk = ByteArray(minOf(32768, limit - total + 1))
                    val count = try { input.read(chunk) } catch (failure: Throwable) { chunk.fill(0); throw failure }
                    if (count < 0) { chunk.fill(0); break }
                    chunks += chunk to count
                    total += count
                    if (total > limit) throw AiEditFailure("The provider response is too large.")
                }
                checkActive()
                return ByteArray(total).also { output ->
                    var offset = 0
                    for ((bytes,count) in chunks) { bytes.copyInto(output,offset,0,count); offset += count }
                }
            } finally { chunks.forEach { it.first.fill(0) } }
        }
    }
}

internal object AiRemoteUrls {
    fun allowed(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            (uri.host == "api.replicate.com" || output(value))
    }.getOrDefault(false)
    fun output(value: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host ?: return false
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            (host == "replicate.delivery" || host.endsWith(".replicate.delivery"))
    }.getOrDefault(false)
}
