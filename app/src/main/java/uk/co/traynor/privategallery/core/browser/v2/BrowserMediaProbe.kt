package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Bounded metadata probe of a URL the current WebView already exposed or requested. */
internal object BrowserMediaProbe {
    fun inspect(url: String, userAgent: String, page: String,
        guard: uk.co.traynor.privategallery.core.security.ScopedIoGuard,
        connectionFactory: (URI) -> HttpURLConnection = { URL(it.toString()).openConnection() as HttpURLConnection },
        cancelled: () -> Boolean): MediaSaveCandidate {
        guard.requireScope(uk.co.traynor.privategallery.core.security.PrimaryScope.BROWSER_UPLOAD_EGRESS)
        fun checkAccess() { guard.check(); if (cancelled()) throw java.io.IOException("Media probe cancelled") }
        var target = runCatching { URI(url) }.getOrNull() ?: return unavailable(MediaSaveReason.NO_MEDIA_CANDIDATE)
        val referer = runCatching { URI(page) }.getOrNull()?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }
            ?.let { "https://${it.host}/" }
        repeat(4) { attempt ->
            if (!safeHttps(target)) return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
            var connection: HttpURLConnection? = null
            var transport: uk.co.traynor.privategallery.core.security.ScopedConnection<HttpURLConnection>? = null
            try {
                try {
                    checkAccess()
                    transport = guard.connection { connectionFactory(target) }
                    connection = transport.value.apply {
                        checkAccess()
                        requestMethod = "HEAD"
                        instanceFollowRedirects = false
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        useCaches = false
                        setRequestProperty("User-Agent", userAgent)
                        referer?.let { setRequestProperty("Referer", it) }
                        CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
                    }
                    checkAccess()
                    val status = connection.responseCode.also { checkAccess() }
                    if (status in 300..399) {
                        val next = resolveSafeRedirect(target, connection.getHeaderField("Location"))
                            ?: return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
                        if (attempt == 3) return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
                        target = next
                        return@repeat
                    }
                    if (status == 401) return unavailable(MediaSaveReason.SESSION_AUTH_FAILED)
                    // Some ordinary media servers reject HEAD while the browser's GET works.
                    if (status == 403 || status == 405 || status == 501) {
                        transport.close()
                        checkAccess()
                        transport = guard.connection { connectionFactory(target) }
                        connection = transport.value.apply {
                            checkAccess()
                            instanceFollowRedirects = false
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            useCaches = false
                            setRequestProperty("Range", "bytes=0-0")
                            setRequestProperty("User-Agent", userAgent)
                            referer?.let { setRequestProperty("Referer", it) }
                            CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
                        }
                    }
                    checkAccess()
                    val finalStatus = connection.responseCode.also { checkAccess() }
                    if (finalStatus in 300..399) {
                        target = resolveSafeRedirect(target, connection.getHeaderField("Location"))
                            ?: return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
                        if (attempt == 3) return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
                        return@repeat
                    }
                    if (finalStatus == 401 || finalStatus == 403)
                        return unavailable(MediaSaveReason.SESSION_AUTH_FAILED)
                    if (finalStatus !in 200..299) return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
                    val responseMime = connection.contentType?.substringBefore(';')?.lowercase()
                    if (responseMime == "text/html" || responseMime == "application/json") return unavailable(MediaSaveReason.UNSUPPORTED_CONTAINER)
                    val candidate = BrowserMediaSavePolicy.classify(target.toString(), false, responseMime)
                    if (candidate.kind == MediaSaveKind.STREAM && protectedManifest(target, userAgent, referer, guard))
                        return MediaSaveCandidate("", null, MediaSaveKind.PROTECTED, MediaSaveReason.DRM_DETECTED)
                    return candidate
                } finally { transport?.close(); checkAccess() }
            } catch (_: Exception) {
                return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
            }
        }
        return unavailable(MediaSaveReason.MEDIA_REQUEST_FAILED)
    }

    fun resolveSafeRedirect(current: URI, location: String?): URI? = location?.let {
        runCatching { current.resolve(it) }.getOrNull()?.takeIf(::safeHttps)
    }
    private fun safeHttps(uri: URI): Boolean = uri.scheme == "https" && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.port in setOf(-1, 443) && uri.toString().length <= 8192
    private fun unavailable(reason: MediaSaveReason) = MediaSaveCandidate("", null, MediaSaveKind.UNSUPPORTED, reason)

    /** Conservatively rejects encrypted HLS and DASH protection markers before export or key requests. */
    fun protectedManifest(uri: URI, userAgent: String, referer: String?, guard: uk.co.traynor.privategallery.core.security.ScopedIoGuard,
        connectionFactory: (URI) -> HttpURLConnection = { URL(it.toString()).openConnection() as HttpURLConnection }): Boolean {
        var target = uri
        repeat(4) { attempt ->
            guard.check()
            if (!safeHttps(target)) throw java.io.IOException("Unsupported manifest URL")
            val transport = guard.connection { connectionFactory(target) }
            try {
                val connection = transport.value.apply {
                    guard.check()
                    instanceFollowRedirects = false
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", userAgent)
                    referer?.let { setRequestProperty("Referer", it) }
                    CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
                }
                if (connection.responseCode in 300..399) {
                    if (attempt == 3) throw java.io.IOException("Too many manifest redirects")
                    target = resolveSafeRedirect(target, connection.getHeaderField("Location"))
                        ?: throw java.io.IOException("Unsupported manifest redirect")
                    return@repeat
                }
                if (connection.responseCode !in 200..299) throw java.io.IOException("Manifest request failed")
                guard.check()
                val bytes = transport.input { it.inputStream }.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (output.size() < 64 * 1024) {
                        val count = input.read(buffer, 0, minOf(buffer.size, 64 * 1024 - output.size()))
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                try { guard.check(); return ManifestProtectionPolicy.isProtected(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
            } finally { transport.close() }
        }
        throw java.io.IOException("Manifest request failed")
    }
}

internal object ManifestProtectionPolicy {
    fun isProtected(content: String): Boolean = content.lineSequence().any { line ->
        val trimmed = line.trim()
        (trimmed.startsWith("#EXT-X-KEY:", true) &&
            !Regex("METHOD\\s*=\\s*NONE(?:,|$)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) ||
            trimmed.startsWith("#EXT-X-SESSION-KEY:", true)
    } || content.contains("<ContentProtection", true)
}
