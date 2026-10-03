package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.CookieManager
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import uk.co.traynor.privategallery.core.security.OwnedInput
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.ScopedIoGuard

/** Scoped segment transport/input only. Transformer, staging, provider copies and fan-out remain separate. */
internal class OwnedVideoSegment internal constructor(
    private val guard: ScopedIoGuard,
    private val cancelled: () -> Boolean,
    private val owned: OwnedInput,
    val uri: URI,
    private val headers: Map<String, List<String>>,
    remaining: Long,
) : AutoCloseable {
    private val input = owned.adopt(guard)
    var remaining: Long = remaining
        private set
    private fun checkActive() { guard.check(); if (cancelled()) throw IOException("Video save cancelled") }
    fun responseHeaders(): Map<String, List<String>> { checkActive(); return headers }
    fun checkAvailable() = checkActive()
    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        try {
            checkActive()
            if (length == 0) return 0
            if (remaining == 0L) return -1
            val requested = if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()
            val count = input.read(buffer, offset, requested)
            checkActive()
            if (count < -1 || count > requested) throw IOException("Invalid media read count")
            if (count > 0 && remaining > 0) remaining -= count
            return count
        } catch (failure: Throwable) { buffer.fill(0, offset, offset + length); throw failure }
    }
    override fun close() = owned.close()
}

/** Five exact children are admitted before the first transport; redirects never reuse a child. */
internal fun openOwnedVideoSegment(
    guard: ScopedIoGuard, uri: URI, position: Long, length: Long, get: Boolean,
    userAgent: String, origin: String?, cancelled: () -> Boolean,
    connectionFactory: (URI) -> HttpURLConnection = { URL(it.toString()).openConnection() as HttpURLConnection },
    cookieFor: (URI) -> String? = { CookieManager.getInstance().getCookie(it.toString()) },
    protectedManifest: (URI) -> Boolean = { BrowserMediaProbe.protectedManifest(it, userAgent, origin, guard) },
): OwnedVideoSegment {
    guard.requireScope(PrimaryScope.BROWSER_UPLOAD_EGRESS)
    fun checkActive() { guard.check(); if (cancelled()) throw IOException("Video save cancelled") }
    checkActive()
    require(get) { "Unsupported media request" }
    require(position >= 0 && length >= -1) { "Invalid media range" }
    require(length < 0 || length == 0L || position <= Long.MAX_VALUE - (length - 1)) { "Invalid media range" }
    fun safe(target: URI) = target.scheme == "https" && !target.host.isNullOrBlank() &&
        target.rawUserInfo == null && target.port in setOf(-1, 443) && target.toString().length <= 8192
    if (!safe(uri)) throw IOException("Unsupported media transport")
    var finalUri = uri
    var finalHeaders: Map<String, List<String>> = emptyMap()
    var finalRemaining = -1L
    val owned = guard.createOwnedInput(OwnedResourceManifest.io("input", "transport0", "transport1", "transport2", "transport3")) {
        var target = uri
        for (attempt in 0..3) {
            checkActive()
            if (!safe(target)) throw IOException("Unsupported media transport")
            if (target.path.orEmpty().endsWith(".m3u8", true) || target.path.orEmpty().endsWith(".mpd", true)) {
                if (protectedManifest(target)) throw BrowserVideoUnavailableException(MediaSaveReason.DRM_DETECTED)
                checkActive()
            }
            val name = "transport$attempt"
            val transport = create(name, { actual: HttpURLConnection -> actual.disconnect() }) { connectionFactory(target) }
            checkActive()
            val connection = transport.value
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            origin?.let { connection.setRequestProperty("Referer", it); connection.setRequestProperty("Origin", it.removeSuffix("/")) }
            cookieFor(target)?.let { connection.setRequestProperty("Cookie", it) }
            if (position > 0 || length >= 0) {
                val end = if (length >= 0) (position + length - 1).toString() else ""
                connection.setRequestProperty("Range", "bytes=$position-$end")
            }
            checkActive()
            val status = connection.responseCode
            checkActive()
            if (status in 300..399) {
                val next = BrowserMediaProbe.resolveSafeRedirect(target, connection.getHeaderField("Location"))
                checkActive()
                if (attempt == 3 || next == null) throw IOException("Unsupported media redirect")
                retireChildren(name)
                checkActive()
                target = next
                continue
            }
            if (status == 401 || status == 403) throw BrowserVideoUnavailableException(MediaSaveReason.SESSION_AUTH_FAILED)
            if (status !in 200..299) throw IOException("Media request failed")
            val root = create("input", { actual: InputStream -> actual.close() }) { connection.inputStream }
            checkActive()
            if (status == 200 && position > 0) {
                if (position > 8L * 1024 * 1024) throw IOException("Range unsupported")
                var skipped = 0L
                while (skipped < position) {
                    checkActive()
                    val count = root.value.skip(position - skipped)
                    checkActive()
                    if (count <= 0 || count > position - skipped) throw IOException("Media range unavailable")
                    skipped += count
                }
            }
            val contentLength = if (length < 0) connection.contentLengthLong else -1L
            checkActive()
            finalRemaining = if (length >= 0) length else contentLength.takeIf { it >= 0 }
                ?.minus(if (status == 200) position else 0) ?: -1L
            finalHeaders = connection.headerFields?.filterKeys { it != null }?.mapKeys { it.key!! } ?: emptyMap()
            checkActive()
            finalUri = target
            if (attempt < 3) discardChildren(*(attempt + 1..3).map { "transport$it" }.toTypedArray())
            checkActive()
            return@createOwnedInput root
        }
        throw IOException("Media redirect limit exceeded")
    }
    try {
        checkActive()
        return OwnedVideoSegment(guard, cancelled, owned, finalUri, finalHeaders, finalRemaining)
    } catch (failure: Throwable) {
        try { owned.close() } catch (releaseFailure: Throwable) { failure.addSuppressed(releaseFailure) }
        throw failure
    }
}
