package uk.co.traynor.privategallery.core.editor.local

import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** No browser state, cookies, credentials, user URLs, proxy bypass or downloaded executable code. */
object ModelHttps {
    private val hosts = setOf("huggingface.co", "cdn-lfs.huggingface.co", "cdn-lfs-us-1.hf.co",
        "cdn-lfs-eu-1.hf.co", "cas-bridge.xethub.hf.co", "us.aws.cdn.hf.co", "eu.aws.cdn.hf.co")
    fun allowed(uri: URI) = uri.scheme == "https" && uri.host in hosts && uri.userInfo == null &&
        uri.port in setOf(-1, 443) && uri.fragment == null
    fun open(model: ModelSpec, offset: Long): ModelStream {
        var uri = URI(model.url)
        repeat(6) {
            require(allowed(uri)) { "Unapproved model download destination." }
            val connection = uri.toURL().openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 10000
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            try {
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    uri = uri.resolve(connection.getHeaderField("Location") ?: error("Missing download destination."))
                    connection.disconnect()
                } else {
                    require(code == 200 || (code == 206 && offset > 0)) { "Model download failed. Retry later." }
                    val start = if (code == 206) offset else 0L
                    if (code == 206) require(connection.getHeaderField("Content-Range") == "bytes $offset-${model.bytes - 1}/${model.bytes}") { "Invalid model download range." }
                    val length = connection.contentLengthLong
                    require(length < 0 || length == model.bytes - start) { "Unexpected model download size." }
                    return ModelStream(connection.inputStream, start) { connection.disconnect() }
                }
            } catch (failure: Throwable) { connection.disconnect(); throw failure }
        }
        error("Too many model download redirects.")
    }
}
