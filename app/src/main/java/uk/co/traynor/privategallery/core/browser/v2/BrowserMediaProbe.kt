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
                    if (candidate.kind == MediaSaveKind.STREAM && protectedManifest(target, userAgent, referer, guard, connectionFactory))
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
        guard.requireScope(uk.co.traynor.privategallery.core.security.PrimaryScope.BROWSER_UPLOAD_EGRESS)
        var target = uri
        repeat(4) { attempt ->
            guard.check()
            if (!safeHttps(target)) throw java.io.IOException("Unsupported manifest URL")
            var redirect: URI? = null
            var result: Boolean? = null
            var failedOriginal: uk.co.traynor.privategallery.core.security.ReleaseReservation? = null
            val original = try { guard.createOwned(uk.co.traynor.privategallery.core.security.OwnedResourceManifest.io("connection", "stream", "buffer")) {
                failedOriginal = this.original
                val transport = create("connection", { actual: HttpURLConnection -> actual.disconnect() }) { connectionFactory(target) }
                guard.check()
                val connection = transport.value.apply {
                    instanceFollowRedirects = false
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", userAgent)
                    referer?.let { setRequestProperty("Referer", it) }
                    CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
                }
                guard.check()
                val status = connection.responseCode.also { guard.check() }
                if (status in 300..399) {
                    if (attempt == 3) throw java.io.IOException("Too many manifest redirects")
                    redirect = resolveSafeRedirect(target, connection.getHeaderField("Location"))
                        ?: throw java.io.IOException("Unsupported manifest redirect")
                } else {
                    if (status !in 200..299) throw java.io.IOException("Manifest request failed")
                    val buffer = create("buffer", { actual: uk.co.traynor.privategallery.core.security.OwnedByteBuffer -> actual.close() }) {
                        uk.co.traynor.privategallery.core.security.OwnedByteBuffer(guard, 64 * 1024)
                    }
                    guard.check()
                    val input = create("stream", { actual: java.io.InputStream -> actual.close() }) { connection.inputStream }
                    result = buffer.value.useBytes { bytes ->
                        var length = 0
                        while (length < bytes.size) {
                            guard.check()
                            val count = input.value.read(bytes, length, bytes.size - length)
                            guard.check()
                            if (count < 0) break
                            if (count == 0) {
                                val value = input.value.read().also { guard.check() }
                                if (value < 0) break
                                bytes[length++] = value.toByte()
                            } else length += count
                        }
                        // A truncated prefix cannot prove absence of protection markers.
                        if (length == bytes.size && input.value.read().also { guard.check() } >= 0)
                            throw java.io.IOException("Manifest exceeds supported bound")
                        ManifestProtectionPolicy.isProtected(String(bytes, 0, length, Charsets.UTF_8))
                    }
                }
                transport
            } } catch (failure: Throwable) {
                try { failedOriginal?.let { guard.retire(it) } }
                catch (releaseFailure: Throwable) { failure.addSuppressed(releaseFailure) }
                throw failure
            }
            try { guard.check() }
            finally { guard.retire(original); guard.check() }
            if (redirect != null) { target = checkNotNull(redirect); return@repeat }
            return checkNotNull(result)

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
