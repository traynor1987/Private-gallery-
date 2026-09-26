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

/** Only same-origin HTTPS redirects. Never forward browser credentials to another host. */
internal fun videoVaultSource(candidate: MediaSaveCandidate, userAgent: String, page: String,
    cancelled: () -> Boolean, progress: (Int?) -> Unit): VaultImportSource {
    require(candidate.kind == MediaSaveKind.DIRECT && candidate.mime != null)
    val source = URI(candidate.url)
    val origin = runCatching { URI(page) }.getOrNull()?.takeIf { it.scheme == "https" }
    val referer = origin?.let { "${it.scheme}://${it.host}/" }
    val extension = when (candidate.mime) { "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/quicktime" -> "mov"; "video/x-matroska" -> "mkv"; else -> "ts" }
    return VaultImportSource("browser-video.$extension", candidate.mime, openStream = openStream@{
        var target = source
        var connection: HttpURLConnection? = null
        repeat(4) { redirects ->
            if (cancelled()) throw IOException("Video save cancelled")
            connection = (URL(target.toString()).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", userAgent)
                referer?.let { setRequestProperty("Referer", it) }
                CookieManager.getInstance().getCookie(target.toString())?.let { setRequestProperty("Cookie", it) }
            }
            val active = connection!!
            if (active.responseCode in 300..399 && redirects < 3) {
                val next = target.resolve(active.getHeaderField("Location") ?: throw IOException("Invalid media redirect"))
                active.disconnect()
                if (next.scheme != "https" || next.host != source.host || next.port != source.port || next.rawUserInfo != null)
                    throw IOException("Media redirect is unavailable")
                target = next
            } else {
                if (active.responseCode !in 200..299) { active.disconnect(); throw IOException("Media request unavailable") }
                val length = active.contentLengthLong.takeIf { it > 0 }
                if (length != null && length > MAX_VIDEO_BYTES) { active.disconnect(); throw IOException("Video exceeds Vault save limit") }
                val input = BufferedInputStream(active.inputStream)
                try {
                    input.mark(32)
                    val header = ByteArray(16)
                    val read = input.read(header)
                    input.reset()
                    if (!validHeader(candidate.mime, header, read)) throw IOException("Video format could not be validated")
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
                        fun verifyEnd() { if (length != null && count != length) throw IOException("Incomplete media response") }
                        fun updated(n: Int) {
                            if (cancelled()) throw IOException("Video save cancelled")
                            count += n
                            if (count > MAX_VIDEO_BYTES) throw IOException("Video exceeds Vault save limit")
                            val percent = BrowserMediaSavePolicy.percent(count, length)
                            if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                        }
                        override fun close() { try { super.close() } finally { active.disconnect() } }
                    }
                } catch (failure: Throwable) { input.close(); active.disconnect(); throw failure }
            }
        }
        throw IOException("Too many media redirects")
    }, isCancelled = cancelled)
}

internal fun validHeader(mime: String, header: ByteArray, count: Int): Boolean = when (mime) {
    "video/mp4", "video/quicktime" -> count >= 12 && header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
    "video/webm", "video/x-matroska" -> count >= 4 && header.take(4) == listOf(0x1a, 0x45, 0xdf, 0xa3).map { it.toByte() }
    "video/mp2t" -> count >= 1 && header[0] == 0x47.toByte()
    else -> false
}

private const val MAX_VIDEO_BYTES = 512L * 1024 * 1024
