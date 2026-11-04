package uk.co.traynor.novaos.preview.browser

import java.net.URI

// Ported unchanged from Primary V1 BrowserPolicy at6661507b; no production dependencies.

enum class BrowserBackAction { EXIT_FULLSCREEN, GO_BACK, FALL_THROUGH }

enum class BrowserDownloadAction { REQUEST_VAULT_SAVE }

enum class BrowserTlsAction { CANCEL }

enum class BrowserPopupAction { LOAD_IN_CURRENT_VIEW, CANCEL }

object BrowserWebSecurityPolicy {
    const val javaScriptBridgeEnabled = false
    const val fileAccessEnabled = false
    const val contentAccessEnabled = false
    // The real-device traces show the affected pages use neither onCreateWindow nor a child
    // WebView. Keep the known-good single-window policy instead of changing existing-page
    // JavaScript semantics merely to support an unobserved mechanism.
    const val multipleWindowsEnabled = false
    const val automaticWindowOpeningEnabled = false
    const val thirdPartyCookiesEnabled = false
}

object BrowserViewportPolicy {
    const val useWideViewport = true
    const val loadWithOverview = false
    const val textZoomPercent = 100
    const val initialScale = 0
    const val pageUsesChromeHorizontalMargins = false
}

object BrowserNavigationPolicy {
    fun isWebUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    fun backAction(fullscreen: Boolean, canGoBack: Boolean): BrowserBackAction = when {
        fullscreen -> BrowserBackAction.EXIT_FULLSCREEN
        canGoBack -> BrowserBackAction.GO_BACK
        else -> BrowserBackAction.FALL_THROUGH
    }

    fun tlsErrorAction(): BrowserTlsAction = BrowserTlsAction.CANCEL
    fun downloadAction(): BrowserDownloadAction = BrowserDownloadAction.REQUEST_VAULT_SAVE
    fun clearDataOnLock(enabled: Boolean): Boolean = enabled
}

object BrowserPopupPolicy {
    fun actionFor(destination: String?): BrowserPopupAction =
        if (destination != null && BrowserNavigationPolicy.isWebUrl(destination)) BrowserPopupAction.LOAD_IN_CURRENT_VIEW else BrowserPopupAction.CANCEL
}
