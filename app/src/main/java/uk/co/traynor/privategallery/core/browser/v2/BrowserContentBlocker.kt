package uk.co.traynor.privategallery.core.browser.v2

import java.net.URI
import java.util.Locale

/** Small bundled host filter. No request URLs, browsing hosts or exceptions are persisted. */
class BrowserContentBlocker(private val onEnabledChanged: (Boolean) -> Unit = {}) {
    @Volatile var enabled: Boolean = true
        set(value) { field = value; onEnabledChanged(value) }
    @Volatile private var bypassedHosts: Set<String> = emptySet()

    fun isSiteBypassed(pageUrl: String): Boolean = host(pageUrl)?.let { site(it) in bypassedHosts } ?: false

    fun setSiteBypassed(pageUrl: String, bypassed: Boolean) {
        val domain = host(pageUrl)?.let(::site) ?: return
        synchronized(this) {
            bypassedHosts = if (bypassed) bypassedHosts + domain else bypassedHosts - domain
        }
    }

    fun shouldBlock(pageUrl: String, resourceUrl: String, mainFrame: Boolean): Boolean {
        if (!enabled || mainFrame) return false
        val pageHost = host(pageUrl) ?: return false
        val resourceHost = host(resourceUrl) ?: return false
        if (isSiteBypassed(pageUrl) || resourceHost == pageHost || resourceHost.endsWith(".$pageHost")) return false
        return AD_HOSTS.any { resourceHost == it || resourceHost.endsWith(".$it") }
    }

    fun shouldBlockPopup(pageUrl: String, userGesture: Boolean): Boolean =
        enabled && !userGesture && !isSiteBypassed(pageUrl)

    private fun host(value: String): String? = runCatching {
        val uri = URI(value)
        if (uri.scheme !in setOf("http", "https")) null else uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun site(host: String): String = host.removePrefix("www.")

    // A conservative bundled set of dedicated advertising/tracking hosts. Domain-boundary
    // matching avoids blocking unrelated sites with similar names. App updates revise this set.
    private companion object {
        val AD_HOSTS = setOf(
            "2mdn.net", "adnxs.com", "adsrvr.org", "amazon-adsystem.com", "casalemedia.com",
            "criteo.com", "criteo.net", "doubleclick.net", "googlesyndication.com",
            "googleadservices.com", "moatads.com", "openx.net", "outbrain.com",
            "pubmatic.com", "rubiconproject.com", "scorecardresearch.com", "taboola.com",
            "yieldmo.com", "zedo.com",
        )
    }
}
