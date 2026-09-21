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

    /** Attachment is lifecycle-only; it deliberately says nothing about a page or navigation. */
    fun webViewAttachment(retained: Boolean): String =
        "WEBVIEW_ATTACHMENT:${if (retained) "retained" else "new"}"

    /** Error classes are diagnostic mechanism only; URLs and error descriptions stay private. */
    fun resourceError(url: String?, errorCode: Int, isMainFrame: Boolean): String {
        // Intentionally inspect no part of [url]. Keeping it in this boundary makes accidental
        // addition of a URL to diagnostic output visible during review and tests.
        @Suppress("UNUSED_VARIABLE") val ignoredUrl = url
        return "RESOURCE_ERROR:${frameCategory(isMainFrame)}:${errorCategory(errorCode)}"
    }

    fun mainFrameError(errorCode: Int): String = "MAIN_FRAME_ERROR:${errorCategory(errorCode)}"

    fun httpError(statusCode: Int, isMainFrame: Boolean): String = "HTTP_ERROR:${frameCategory(isMainFrame)}:${when (statusCode / 100) {
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

    /**
     * Console text/source are intentionally discarded. For errors, a small mechanism category
     * differentiates common existing-page failures without retaining the original page message.
     */
    fun consoleMessage(level: String?, message: String?): String {
        val severity = level?.lowercase()?.takeIf { it in setOf("tip", "log", "warning", "error", "debug") } ?: "other"
        return if (severity == "error") "JS_CONSOLE:error:${consoleErrorCategory(message)}" else "JS_CONSOLE:$severity"
    }

    /** Records only the VPN state that caused an actual WebView cancellation, never a profile. */
    fun vpnGateStopLoading(connectionState: String?): String =
        "VPN_GATE_STOP_LOADING:${connectionState?.lowercase()?.takeIf { it in setOf("unconfigured", "disconnected", "connecting", "reconnecting", "failed", "disconnecting") } ?: "other"}"

    private fun originOf(url: String?): String? = runCatching {
        val uri = java.net.URI(url ?: return null)
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()
        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) null
        else "$scheme://$host:${if (uri.port == -1) defaultPort(scheme) else uri.port}"
    }.getOrNull()

    private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80

    private fun frameCategory(isMainFrame: Boolean): String = if (isMainFrame) "main_frame" else "subresource"

    private fun consoleErrorCategory(message: String?): String {
        val value = message?.lowercase().orEmpty()
        return when {
            "content security policy" in value || "csp" in value -> "csp"
            "cross-origin" in value || "cors" in value -> "cors"
            "network" in value || "failed to fetch" in value -> "network"
            "syntaxerror" in value || "syntax error" in value -> "syntax"
            "typeerror" in value || "type error" in value -> "type"
            "storage" in value || "indexeddb" in value || "localstorage" in value || "sessionstorage" in value -> "storage"
            "permission" in value || "notallowederror" in value -> "permission"
            "security" in value || "blocked" in value -> "security"
            else -> "other"
        }
    }

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
    private val totalByEvent = linkedMapOf<String, Int>()

    fun record(event: String) {
        totalByEvent[event] = (totalByEvent[event] ?: 0) + 1
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

    /**
     * Outcome counters remain visible when a busy application has more requests than the recent
     * event list can show. A request observation is not represented as a successful load.
     */
    fun outcomeSummary(): List<String> = buildList {
        countMatching("RESOURCE_LOAD:")?.let { add("Resource requests observed: $it") }
        countMatching("RESOURCE_ERROR:")?.let { add("Resource delivery errors: $it") }
        countMatching("HTTP_ERROR:main_frame:")?.let { add("Main-frame HTTP error responses: $it") }
        countMatching("HTTP_ERROR:subresource:")?.let { add("Subresource HTTP error responses: $it") }
        countMatching("JS_CONSOLE:")?.let { add("JavaScript console reports: $it") }
        countMatching("RENDER_PROCESS_GONE:")?.let { add("Renderer process failures: $it") }
        countMatching("VPN_GATE_BLOCKED_REQUEST:")?.let { add("VPN-gate blocks: $it") }
        countMatching("VPN_GATE_STOP_LOADING:")?.let { add("VPN-gate stop-loading calls: $it") }
    }

    private fun countMatching(prefix: String): Int? = totalByEvent
        .filterKeys { it.startsWith(prefix) }
        .values
        .sum()
        .takeIf { it > 0 }
}
