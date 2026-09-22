package uk.co.traynor.privategallery.core.browser

/**
 * Coarse resource classification for the acceptance trace. It deliberately receives only a
 * URL path, never records it, and leaves opaque XHR/fetch requests as `other` rather than
 * presenting an observation as a successful response.
 */
internal object BrowserAcceptanceResourceClassifier {
    fun classify(fetchDestination: String?, path: String?, isMainFrame: Boolean): String = when (fetchDestination?.lowercase()) {
        "script" -> "script"
        "style" -> "stylesheet"
        "image" -> "image"
        "font" -> "font"
        "iframe", "frame" -> "iframe"
        "audio", "video", "track" -> "media"
        "document" -> "document"
        "empty" -> "xhr_fetch"
        else -> classifyPath(path) ?: if (isMainFrame) "document" else "other"
    }

    private fun classifyPath(path: String?): String? = when (path?.substringAfterLast('.', "")?.lowercase()) {
        "js", "mjs", "cjs" -> "script"
        "css" -> "stylesheet"
        "png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp" -> "image"
        "woff", "woff2", "ttf", "otf", "eot" -> "font"
        "mp4", "webm", "mp3", "m4a", "ogg", "wav", "m3u8" -> "media"
        else -> null
    }
}
