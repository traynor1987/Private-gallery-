package uk.co.traynor.privategallery.core.browser

import java.util.ArrayDeque

/**
 * Short-lived structural diagnostics for real-device WebView investigation. These deliberately
 * preserve mechanism only; no page title, host, path, query, cookie or console message leaves
 * the WebView boundary.
 */
enum class BrowserDiagnosticEvent {
    WEBVIEW_CREATED,
    WEBVIEW_REBOUND,
    MAIN_NAVIGATION,
    MAIN_PAGE_STARTED,
    MAIN_PAGE_COMMIT_VISIBLE,
    MAIN_PAGE_FINISHED,
    CHILD_WINDOW_REQUEST,
    CHILD_WEBVIEW_CREATED,
    CHILD_NAVIGATION,
    CHILD_WINDOW_CLOSED,
    JS_CONSOLE_ERROR,
    PERMISSION_REQUEST,
    FILE_CHOOSER_REQUEST,
    MAIN_FRAME_ERROR,
    CHILD_FRAME_ERROR,
    RESOURCE_ERROR,
    RESOURCE_LOAD,
    HTTP_ERROR,
    TLS_ERROR,
    RENDER_PROCESS_GONE,
    VPN_GATE_BLOCKED_REQUEST,
}

object BrowserDiagnosticsPolicy {
    fun event(event: BrowserDiagnosticEvent, url: String? = null): String =
        "${event.name}:${if (url != null && BrowserNavigationPolicy.isWebUrl(url)) "web" else "none"}"

    /** Error classes are diagnostic mechanism only; URLs and error descriptions stay private. */
    fun resourceError(url: String?, errorCode: Int): String {
        // Intentionally inspect no part of [url]. Keeping it in this boundary makes accidental
        // addition of a URL to diagnostic output visible during review and tests.
        @Suppress("UNUSED_VARIABLE") val ignoredUrl = url
        return "RESOURCE_ERROR:${errorCategory(errorCode)}"
    }

    fun mainFrameError(errorCode: Int): String = "MAIN_FRAME_ERROR:${errorCategory(errorCode)}"

    fun httpError(statusCode: Int): String = "HTTP_ERROR:${when (statusCode / 100) {
        1 -> "1xx"
        2 -> "2xx"
        3 -> "3xx"
        4 -> "4xx"
        5 -> "5xx"
        else -> "other"
    }}"

    /**
     * Records only the relationship of a requested resource to the visible document. Neither
     * origin is retained: this distinguishes an app whose embedded resources are not loading
     * without retaining browsing history or session identifiers.
     */
    fun resourceLoad(resourceUrl: String?, mainDocumentUrl: String?): String {
        val resourceOrigin = originOf(resourceUrl)
        val mainOrigin = originOf(mainDocumentUrl)
        return "RESOURCE_LOAD:${when {
            resourceOrigin == null -> "invalid"
            mainOrigin == null -> "unknown_document"
            resourceOrigin == mainOrigin -> "same_origin"
            else -> "other_origin"
        }}"
    }

    /** Console text/source are intentionally discarded; only the platform severity is useful. */
    fun consoleMessage(level: String?, @Suppress("UNUSED_PARAMETER") message: String?): String =
        "JS_CONSOLE:${level?.lowercase()?.takeIf { it in setOf("tip", "log", "warning", "error", "debug") } ?: "other"}"

    private fun originOf(url: String?): String? = runCatching {
        val uri = java.net.URI(url ?: return null)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) null
        else "$scheme://$host:${if (uri.port == -1) defaultPort(scheme) else uri.port}"
    }.getOrNull()

    private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80

    private fun errorCategory(errorCode: Int): String = when (errorCode) {
        -2 -> "host_lookup"
        -6 -> "connect"
        -7 -> "io"
        -8 -> "timeout"
        -9 -> "redirect_loop"
        -11 -> "ssl_handshake"
        -15 -> "too_many_requests"
        else -> "other"
    }
}

/**
 * Keeps diagnostics readable when a normal web application loads many resources. The recorder
 * only receives already-sanitised event codes and coalesces adjacent duplicates, retaining the
 * structural ordering needed for physical-device investigation.
 */
class BrowserDiagnosticRecorder(private val maximumEntries: Int = 18) {
    private data class Entry(val event: String, var count: Int)

    private val entries = ArrayDeque<Entry>()

    fun record(event: String) {
        val last = entries.peekLast()
        if (last?.event == event) last.count += 1
        else {
            entries.addLast(Entry(event, 1))
            while (entries.size > maximumEntries) entries.removeFirst()
        }
    }

    fun snapshot(): List<String> = entries.map { entry ->
        if (entry.count == 1) entry.event else "${entry.event} ×${entry.count}"
    }
}
