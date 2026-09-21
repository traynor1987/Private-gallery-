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
enum class BrowserDownloadAction { REQUEST_VAULT_SAVE }
enum class BrowserTlsAction { CANCEL }
enum class BrowserToolbarAction { RELOAD, STOP }
enum class BrowserPopupAction { LOAD_IN_CURRENT_VIEW, CANCEL }
enum class BrowserImageAcquisitionAction { SAVE_RESOURCE, CAPTURE_DISPLAYED }
enum class BrowserImageHitType { IMAGE, IMAGE_LINK, TEXT }

/** Page navigation failures and Vault-acquisition feedback are intentionally independent. */
data class BrowserPresentationState(
    val pageError: String? = null,
    val feedback: String? = null,
) {
    val replacesWebPage: Boolean get() = pageError != null
    fun withAcquisitionFeedback(value: String): BrowserPresentationState = copy(feedback = value)
}

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
    // The real-device traces show the affected pages use neither onCreateWindow nor a child
    // WebView. Keep the known-good single-window policy instead of changing existing-page
    // JavaScript semantics merely to support an unobserved mechanism.
    const val multipleWindowsEnabled = false
    const val automaticWindowOpeningEnabled = false
    const val thirdPartyCookiesEnabled = false
}

/** Browser never creates tabs: safe user-initiated popups stay in the current WebView. */
object BrowserPopupPolicy {
    fun actionFor(destination: String?): BrowserPopupAction =
        if (destination != null && BrowserNavigationPolicy.isWebUrl(destination)) BrowserPopupAction.LOAD_IN_CURRENT_VIEW else BrowserPopupAction.CANCEL
}

/**
 * Native WebView hit-test policy. A normal displayed image resource is saved exactly as exposed
 * to the current Browser session. Blob/non-resource images may only capture visible pixels; no
 * hidden/original URL discovery is attempted.
 */
object BrowserImagePolicy {
    fun actionFor(hit: BrowserImageHitType, value: String?): BrowserImageAcquisitionAction? = when {
        hit == BrowserImageHitType.TEXT || value.isNullOrBlank() -> null
        BrowserNavigationPolicy.isWebUrl(value) -> BrowserImageAcquisitionAction.SAVE_RESOURCE
        hit == BrowserImageHitType.IMAGE || hit == BrowserImageHitType.IMAGE_LINK -> BrowserImageAcquisitionAction.CAPTURE_DISPLAYED
        else -> null
    }

    fun authorisedResource(value: String?): String? = value?.takeIf(BrowserNavigationPolicy::isWebUrl)
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
    const val pageUsesChromeHorizontalMargins = false
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
    fun downloadAction(): BrowserDownloadAction = BrowserDownloadAction.REQUEST_VAULT_SAVE
    fun clearDataOnLock(enabled: Boolean): Boolean = enabled
}

/** UI-level companion to BrowserVpnController: never begin a new request while gated. */
object BrowserNetworkGatePolicy {
    fun mayStartNetworkRequest(requireVpn: Boolean, vpnConnected: Boolean): Boolean = !requireVpn || vpnConnected
}

/** Narrow validation boundary for Browser downloads before encrypted Vault ingestion. */
object BrowserDownloadPolicy {
    fun acceptsResponse(url: String, statusCode: Int): Boolean =
        BrowserNavigationPolicy.isWebUrl(url) && statusCode in 200..299

    fun safeDisplayName(candidate: String): String {
        val leaf = candidate.substringAfterLast('/').substringAfterLast('\\').trim()
        return leaf.filter { it.code >= 0x20 && it != '/' && it != '\\' }.take(180).ifBlank { "download" }
    }
}

/** Compact toolbar has one deterministic loading affordance rather than parallel text buttons. */
object BrowserToolbarPolicy {
    /** Material's standard field height; never compress text below its measured content area. */
    const val addressFieldHeightDp = 56

    fun primaryAction(isLoading: Boolean): BrowserToolbarAction =
        if (isLoading) BrowserToolbarAction.STOP else BrowserToolbarAction.RELOAD
}
