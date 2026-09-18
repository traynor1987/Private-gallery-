package uk.co.traynor.privategallery.core.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Deliberate policy boundary for untrusted WebView content. */
enum class BrowserSearchEngine(val label: String, val searchPrefix: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    ;

    companion object {
        fun decode(value: String?): BrowserSearchEngine = entries.firstOrNull { it.name == value } ?: GOOGLE
    }
}

data class BrowserDestination(val url: String)

enum class BrowserBackAction { EXIT_FULLSCREEN, GO_BACK, FALL_THROUGH }
enum class BrowserDownloadAction { SHOW_NOT_SUPPORTED }
enum class BrowserTlsAction { CANCEL }

/** Presentation contract: browser chrome never depends on an initialized WebView. */
data class BrowserScreenState(
    val showChrome: Boolean,
    val showStartSurface: Boolean,
    val showError: Boolean,
) {
    companion object {
        fun initial() = BrowserScreenState(showChrome = true, showStartSurface = true, showError = false)
        fun initializationFailed() = BrowserScreenState(showChrome = true, showStartSurface = false, showError = true)
    }
}

/** Configuration invariants for untrusted web content. No app API is exposed to JavaScript. */
object BrowserWebSecurityPolicy {
    const val javaScriptBridgeEnabled = false
    const val fileAccessEnabled = false
    const val contentAccessEnabled = false
    const val multipleWindowsEnabled = false
    const val thirdPartyCookiesEnabled = false
}

/**
 * Responsive-Web contract for ordinary mobile sites. This deliberately supports viewport meta
 * tags but does not force overview zoom or a desktop-sized viewport.
 */
object BrowserViewportPolicy {
    const val useWideViewport = true
    const val loadWithOverview = false
    const val textZoomPercent = 100
    const val initialScale = 0
}

object BrowserAddressPolicy {
    fun destinationFor(input: String, engine: BrowserSearchEngine): BrowserDestination {
        val value = input.trim()
        require(value.isNotEmpty()) { "Enter a web address or search" }
        if (BrowserNavigationPolicy.isWebUrl(value)) return BrowserDestination(value)
        if (value.matches(Regex("^[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+(?:[:/].*)?$"))) {
            return BrowserDestination("https://$value")
        }
        return BrowserDestination(engine.searchPrefix + URLEncoder.encode(value, StandardCharsets.UTF_8.name()))
    }
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
    fun downloadAction(): BrowserDownloadAction = BrowserDownloadAction.SHOW_NOT_SUPPORTED
    fun clearDataOnLock(enabled: Boolean): Boolean = enabled
}
