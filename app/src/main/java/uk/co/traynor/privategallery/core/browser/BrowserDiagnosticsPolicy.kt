package uk.co.traynor.privategallery.core.browser

/**
 * Short-lived structural diagnostics for real-device WebView investigation. These deliberately
 * preserve mechanism only; no page title, host, path, query, cookie or console message leaves
 * the WebView boundary.
 */
enum class BrowserDiagnosticEvent {
    MAIN_NAVIGATION,
    CHILD_WINDOW_REQUEST,
    CHILD_WEBVIEW_CREATED,
    CHILD_NAVIGATION,
    CHILD_WINDOW_CLOSED,
    JS_CONSOLE_ERROR,
    PERMISSION_REQUEST,
    FILE_CHOOSER_REQUEST,
    MAIN_FRAME_ERROR,
    CHILD_FRAME_ERROR,
}

object BrowserDiagnosticsPolicy {
    fun event(event: BrowserDiagnosticEvent, url: String? = null): String =
        "${event.name}:${if (url != null && BrowserNavigationPolicy.isWebUrl(url)) "web" else "none"}"
}
