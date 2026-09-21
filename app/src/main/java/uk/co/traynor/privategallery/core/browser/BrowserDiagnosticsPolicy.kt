package uk.co.traynor.privategallery.core.browser

/**
 * Short-lived structural diagnostics for real-device WebView investigation. These deliberately
 * preserve mechanism only; no page title, host, path, query, cookie or console message leaves
 * the WebView boundary.
 */
enum class BrowserDiagnosticEvent {
    MAIN_NAVIGATION,
    MAIN_PAGE_STARTED,
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
