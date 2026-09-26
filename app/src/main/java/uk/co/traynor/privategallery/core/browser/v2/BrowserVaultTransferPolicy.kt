package uk.co.traynor.privategallery.core.browser.v2

import java.net.URI

enum class MediaSaveKind { DIRECT, STREAM, PROTECTED, UNSUPPORTED, UNKNOWN }
data class MediaSaveCandidate(val url: String, val mime: String?, val kind: MediaSaveKind)

/** Classifies only the current video element. A playable blob or adaptive manifest is not a file. */
object BrowserMediaSavePolicy {
    fun classify(url: String, protected: Boolean): MediaSaveCandidate {
        if (protected) return MediaSaveCandidate("", null, MediaSaveKind.PROTECTED)
        val uri = runCatching { URI(url) }.getOrNull()
        if (url.length > 8192 || uri?.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port != -1)
            return MediaSaveCandidate("", null, MediaSaveKind.UNKNOWN)
        val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val mime = when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            else -> null
        }
        return when {
            mime != null -> MediaSaveCandidate(url, mime, MediaSaveKind.DIRECT)
            extension == "m3u8" || extension == "mpd" -> MediaSaveCandidate("", null, MediaSaveKind.UNSUPPORTED)
            else -> MediaSaveCandidate("", null, MediaSaveKind.UNKNOWN)
        }
    }

    fun percent(received: Long, total: Long?): Int? = total?.takeIf { it > 0 }?.let { (received.coerceAtLeast(0) * 100 / it).coerceIn(0, 100).toInt() }
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
