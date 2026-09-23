package uk.co.traynor.privategallery.core.browser.v2

/** Android WebViewClient error categories, without provider descriptions or resource addresses. */
internal object BrowserResourceError {
    fun category(code: Int): String = when (code) {
        -1 -> "generic_unknown"
        -2 -> "host_lookup"
        -6 -> "connect"
        -7 -> "io"
        -8 -> "timeout"
        -9 -> "redirect_loop"
        -10 -> "unsupported_scheme"
        -11 -> "ssl_handshake"
        -12 -> "bad_url"
        -13, -14 -> "file"
        -15 -> "too_many_requests"
        -16 -> "unsafe_resource"
        else -> "other"
    }
}
