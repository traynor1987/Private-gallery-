package uk.co.traynor.privategallery.core.browser.v2

import java.net.URI

enum class MediaSaveKind { DIRECT, STREAM, PROTECTED, UNSUPPORTED, UNKNOWN }
enum class MediaSaveReason {
    NONE, NO_MEDIA_CANDIDATE, BLOB_WITHOUT_OBSERVED_SOURCE, HLS_UNSUPPORTED, DASH_UNSUPPORTED,
    SEPARATE_TRACK_MUX_UNSUPPORTED, MEDIA_REQUEST_FAILED, SESSION_AUTH_FAILED, DRM_DETECTED, UNSUPPORTED_CONTAINER,
    DIRECT_MEDIA_PARTIAL, MANIFEST_DETECTED, HLS_ASSEMBLY_FAILED, DASH_ASSEMBLY_FAILED,
    SEGMENT_DOWNLOAD_FAILED, AUDIO_TRACK_MISSING, VIDEO_TRACK_MISSING, REMUX_FAILED,
    NON_MEDIA_RESPONSE, MEDIA_VALIDATION_FAILED, ZERO_DURATION,
}
data class MediaSaveCandidate(val url: String, val mime: String?, val kind: MediaSaveKind,
    val reason: MediaSaveReason = MediaSaveReason.NONE)

/** URL and response MIME classification; neither a blob nor a filename suffix alone proves downloadability. */
object BrowserMediaSavePolicy {
    fun classify(url: String, protected: Boolean, responseMime: String? = null): MediaSaveCandidate {
        if (protected) return MediaSaveCandidate("", null, MediaSaveKind.PROTECTED, MediaSaveReason.DRM_DETECTED)
        val uri = runCatching { URI(url) }.getOrNull()
        if (url.length > 8192 || uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in setOf(-1, 443))
            return MediaSaveCandidate("", null, MediaSaveKind.UNKNOWN,
                if (url.startsWith("blob:")) MediaSaveReason.BLOB_WITHOUT_OBSERVED_SOURCE else MediaSaveReason.NO_MEDIA_CANDIDATE)
        val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val declaredMime = responseMime?.substringBefore(';')?.trim()?.lowercase()
        val mime = declaredMime?.takeIf { it in SUPPORTED_MIMES } ?: when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mpd" -> "application/dash+xml"
            else -> null
        }
        return when {
            mime in STREAM_MIMES -> MediaSaveCandidate(url, mime, MediaSaveKind.STREAM)
            mime != null -> MediaSaveCandidate(url, mime, MediaSaveKind.DIRECT)
            declaredMime?.startsWith("video/") == true -> MediaSaveCandidate("", null, MediaSaveKind.UNSUPPORTED, MediaSaveReason.UNSUPPORTED_CONTAINER)
            else -> MediaSaveCandidate("", null, MediaSaveKind.UNKNOWN, MediaSaveReason.NO_MEDIA_CANDIDATE)
        }
    }

    private val STREAM_MIMES = setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml")
    private val SUPPORTED_MIMES = STREAM_MIMES + setOf("video/mp4", "video/webm", "video/quicktime", "video/x-matroska", "video/mp2t")

    fun percent(received: Long, total: Long?): Int? = total?.takeIf { it > 0 }?.let { (received.coerceAtLeast(0) * 100 / it).coerceIn(0, 100).toInt() }
}

/** Volatile, bounded URL-only hints from the active WebView document; no headers, cookies or URLs enter diagnostics. */
class ObservedMediaRequests {
    private val hints = ArrayDeque<Pair<String, String?>>()
    @Synchronized fun observe(url: String, headers: Map<String, String>, playing: Boolean = false) {
        val mime = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value
            ?.substringBefore(',')?.substringBefore(';')?.trim()?.lowercase()
        val candidate = BrowserMediaSavePolicy.classify(url, false, mime)
        val likely = candidate.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM) ||
            mime?.startsWith("video/") == true ||
            headers.any { it.key.equals("Sec-Fetch-Dest", true) && it.value.equals("video", true) } ||
            headers.any { it.key.equals("Range", true) && it.value.startsWith("bytes=") }
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        val staticResource = listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".woff", ".woff2", ".ico")
            .any(path::endsWith)
        if ((!likely && (!playing || staticResource)) || !url.startsWith("https://")) return
        hints.removeAll { it.first == url }
        hints.addLast(url to mime)
        while (hints.size > 24) hints.removeFirst()
    }
    @Synchronized fun resolved(url: String, mime: String) {
        hints.removeAll { it.first == url }
        hints.addLast(url to mime)
        while (hints.size > 24) hints.removeFirst()
    }
    @Synchronized fun best(currentUrl: String, protected: Boolean): MediaSaveCandidate {
        if (protected) return BrowserMediaSavePolicy.classify(currentUrl, true)
        val current = BrowserMediaSavePolicy.classify(currentUrl, false)
        if (current.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM)) return current
        return hints.asReversed().asSequence().map { BrowserMediaSavePolicy.classify(it.first, false, it.second) }
            .firstOrNull { it.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM) } ?: current
    }
    @Synchronized fun probeUrls(currentUrl: String): List<String> = (listOf(currentUrl) + hints.asReversed().map { it.first })
        .filter { it.startsWith("https://") }.distinct().take(4)
    @Synchronized fun clear() { hints.clear() }
    override fun toString(): String = "ObservedMediaRequests(count=${synchronized(this) { hints.size }})"
}

enum class BrowserUploadPolicy {
    VAULT_ONLY, VAULT_AND_DEVICE, BLOCKED;

    companion object {
        fun parse(saved: String?): BrowserUploadPolicy = entries.firstOrNull { it.name == saved } ?: VAULT_ONLY

        fun accepts(mime: String, requested: Array<String>): Boolean {
            val types = requested.flatMap { it.split(',') }.map { it.substringBefore(';').trim().lowercase() }.filter { it.isNotEmpty() }
            return types.isEmpty() || types.any { it == "*/*" || it == mime.lowercase() || (it.endsWith("/*") && mime.lowercase().startsWith(it.removeSuffix("*"))) }
        }

        fun safeName(mime: String): String = "upload." + when (mime.lowercase()) {
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"
            "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/quicktime" -> "mov"; "video/x-matroska" -> "mkv"
            else -> "bin"
        }
    }
}

object BrowserUploadPreference {
    private const val FILE = "browser_upload_policy"
    private const val KEY = "file_uploads"
    fun read(context: android.content.Context): BrowserUploadPolicy = BrowserUploadPolicy.parse(context.getSharedPreferences(FILE, 0).getString(KEY, null))
    fun write(context: android.content.Context, value: BrowserUploadPolicy) {
        context.getSharedPreferences(FILE, 0).edit().putString(KEY, value.name).apply()
    }
}
