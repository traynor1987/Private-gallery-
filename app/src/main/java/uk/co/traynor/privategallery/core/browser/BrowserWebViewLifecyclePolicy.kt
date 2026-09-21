package uk.co.traynor.privategallery.core.browser

/**
 * A Browser exit begins the owned-VPN grace period; it is not itself a network failure. Keeping
 * the existing WebView alive lets a modern page finish its current work and lets a quick return
 * reuse the same rendered session. Network loading is stopped only when the security boundary
 * actually changes.
 */
enum class BrowserWebViewLifecycleEvent {
    LEAVE_BROWSER,
    APP_BACKGROUNDED,
    LOCKED,
    VPN_NOT_CONNECTED,
}

object BrowserWebViewLifecyclePolicy {
    fun shouldStopLoading(event: BrowserWebViewLifecycleEvent): Boolean = when (event) {
        BrowserWebViewLifecycleEvent.LEAVE_BROWSER,
        BrowserWebViewLifecycleEvent.APP_BACKGROUNDED -> false
        BrowserWebViewLifecycleEvent.LOCKED,
        BrowserWebViewLifecycleEvent.VPN_NOT_CONNECTED -> true
    }
}
