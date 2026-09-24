package uk.co.traynor.privategallery.core.media

import java.net.URI

/** No browser headers/cookies or persisted URLs. DRM and non-network sources stay in WebView. */
object WebMediaPolicy {
    fun mime(url: String, htmlVideo: Boolean = false, drm: Boolean = false): String? {
        if (drm || url.length > 8192) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return when (uri.path.orEmpty().substringAfterLast('.').lowercase()) {
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            "mov" -> "video/quicktime"
            else -> if (htmlVideo) "video/*" else null
        }
    }
    fun requireNetwork(allowed: Boolean) { if (!allowed) throw java.io.IOException("Browser network permission unavailable") }
}
