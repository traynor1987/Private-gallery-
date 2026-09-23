package uk.co.traynor.privategallery.core.browser.v2

import java.net.URI

data class SecureWebViewConfiguration(
    val javaScript: Boolean,
    val domStorage: Boolean,
    val firstPartyCookies: Boolean,
    val thirdPartyCookies: Boolean,
    val fileAccess: Boolean,
    val contentAccess: Boolean,
    val mixedContent: Boolean,
    val javascriptBridge: Boolean,
    val multipleWindows: Boolean,
    val responsiveViewport: Boolean,
)

enum class BrowserTlsDecision { CANCEL }
enum class BrowserUserAgentMode { MOBILE, DESKTOP }

/** Pure security decisions shared by every V2 main, popup and restored tab. */
object BrowserSecurityPolicy {
    fun defaultConfiguration() = SecureWebViewConfiguration(
        javaScript = true,
        domStorage = true,
        firstPartyCookies = true,
        thirdPartyCookies = false,
        fileAccess = false,
        contentAccess = false,
        mixedContent = false,
        javascriptBridge = false,
        multipleWindows = true,
        responsiveViewport = true,
    )

    fun allowsNavigation(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    fun tlsDecision() = BrowserTlsDecision.CANCEL

    /** Keep the provider's engine/version identity; never invent an Android or Chrome version. */
    fun userAgent(mode: BrowserUserAgentMode, providerUserAgent: String): String = when (mode) {
        BrowserUserAgentMode.MOBILE -> providerUserAgent
        BrowserUserAgentMode.DESKTOP -> providerUserAgent
            .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile ", " ")
            .replace(Regex(" Version/[0-9.]+"), "")
    }
}
