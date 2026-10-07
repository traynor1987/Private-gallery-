package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.CookieManager
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class BrowserVideoUnavailableException(val reason: MediaSaveReason = MediaSaveReason.MEDIA_REQUEST_FAILED) : IOException("Video is not directly retrievable")

/** HTTPS redirects get only the destination's WebView cookie; no source cookie is forwarded. */
internal fun videoVaultSource(candidate: MediaSaveCandidate, userAgent: String, page: String,
    cancelled: () -> Boolean, progress: (Int?) -> Unit, networkComplete: () -> Unit = {}): VaultImportSource {
    require(candidate.kind == MediaSaveKind.DIRECT && candidate.mime != null)
    val source = URI(candidate.url)
    val origin = runCatching { URI(page) }.getOrNull()?.takeIf { it.scheme == "https" }
    val referer = origin?.let { "${it.scheme}://${it.host}/" }
    val extension = when (candidate.mime) { "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/quicktime" -> "mov"; "video/x-matroska" -> "mkv"; else -> "ts" }
    return VaultImportSource("browser-video.$extension", candidate.mime, openStream = { error("Primary network authority required") }, openScopedStream = openStream@{ guard ->
        var target = source
        repeat(4) { redirects ->
            guard.check()
            if (cancelled()) throw IOException("Video save cancelled")
            val connection = guard.connection { URL(target.toString()).openConnection() as HttpURLConnection }
            var inputAttached = false
            try {
                val active = connection.value.apply {
                    guard.check()
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    useCaches = false
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("User-Agent", userAgent)
                    referer?.let { setRequestProperty("Referer", it) }
                    CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
                }
                guard.check()
                if (active.responseCode in 300..399 && redirects < 3) {
                    val next = target.resolve(active.getHeaderField("Location") ?: throw BrowserVideoUnavailableException())
                    if (next.scheme != "https" || next.host.isNullOrBlank() || next.port !in setOf(-1, 443) || next.rawUserInfo != null)
                        throw BrowserVideoUnavailableException()
                    target = next
                    connection.close()
                } else {
                    if (active.responseCode == 206) throw BrowserVideoUnavailableException(MediaSaveReason.DIRECT_MEDIA_PARTIAL)
                    if (active.responseCode !in 200..299) throw BrowserVideoUnavailableException()
                    val responseMime = active.contentType?.substringBefore(';')?.lowercase()
                    if (responseMime in setOf("text/html", "text/plain", "application/json")) {
                        throw BrowserVideoUnavailableException(MediaSaveReason.NON_MEDIA_RESPONSE)
                    }
                    if (responseMime in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")) {
                        throw BrowserVideoUnavailableException(MediaSaveReason.MANIFEST_DETECTED)
                    }
                    val length = active.contentLengthLong.takeIf { it > 0 }
                    if (length != null && length > MAX_VIDEO_BYTES) throw BrowserVideoUnavailableException()
                    val input = BufferedInputStream(connection.input { it.inputStream })
                    inputAttached = true
                    try {
                        input.mark(32)
                        val header = ByteArray(16)
                        val read = input.read(header)
                        input.reset()
                        VideoValidationPolicy.headerReason(candidate.mime, header, read.coerceAtLeast(0))?.let { throw BrowserVideoUnavailableException(it) }
                        progress(0.takeIf { length != null })
                        return@openStream object : FilterInputStream(input) {
                            var count = 0L
                            var lastPercent: Int? = null
                            override fun read(): Int { val value = `in`.read(); if (value >= 0) updated(1) else verifyEnd(); return value }
                            override fun read(bytes: ByteArray, offset: Int, size: Int): Int {
                                val n = `in`.read(bytes, offset, size)
                                if (n > 0) updated(n)
                                if (n < 0) verifyEnd()
                                return n
                            }
                            fun verifyEnd() {
                                if (length != null && count != length) throw BrowserVideoUnavailableException(MediaSaveReason.DIRECT_MEDIA_PARTIAL)
                                networkComplete()
                            }
                            fun updated(n: Int) {
                                if (cancelled()) throw IOException("Video save cancelled")
                                count += n
                                if (count > MAX_VIDEO_BYTES) throw BrowserVideoUnavailableException()
                                val percent = BrowserMediaSavePolicy.percent(count, length)
                                if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                            }
                        }
                    } catch (failure: Throwable) { input.close(); throw failure }
                }
            } catch (failure: Throwable) {
                if (!inputAttached) try { connection.close() } catch (releaseFailure: Throwable) { failure.addSuppressed(releaseFailure) }
                throw failure
            }
        }
        throw BrowserVideoUnavailableException()
    }, isCancelled = cancelled)
}

internal fun validHeader(mime: String, header: ByteArray, count: Int): Boolean =
    VideoHeaderBytePolicy.valid(mime, header, count)

private const val MAX_VIDEO_BYTES = 512L * 1024 * 1024
