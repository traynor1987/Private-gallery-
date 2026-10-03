package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.CookieManager
import uk.co.traynor.privategallery.core.browser.BrowserDownloadPolicy
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/** The download is opened only after the WebView download callback and imported through Vault. */
internal fun browserV2DownloadSource(
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimeType: String,
    connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
): VaultImportSource {
    require(BrowserNavigationPolicy.isWebUrl(url)) { "Unsupported download URL" }
    val name = BrowserDownloadPolicy.safeDisplayName(contentDisposition.substringAfter("filename=", "download").trim().trim('"'))
    return VaultImportSource(
        displayName = name,
        mimeType = mimeType.ifBlank { "application/octet-stream" },
        openStream = { error("Primary network authority required") },
        openScopedStream = { guard ->
            guard.requireScope(uk.co.traynor.privategallery.core.security.PrimaryScope.BROWSER_UPLOAD_EGRESS)
            guard.check()
            val transport = guard.connection { connectionFactory(URL(url)) }
            try {
                transport.value.apply {
                    guard.check()
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
                    require(BrowserDownloadPolicy.acceptsResponse(url, responseCode)) { "Download response was rejected" }
                }
                guard.check()
                transport.input { it.inputStream }
            } catch (failure: Throwable) {
                transport.close()
                throw failure
            }
        },
        sourceReference = null,
    )
}

/** Uses only WebView's exposed hit-test resource, never a guessed original or premium variant. */
internal fun browserV2ImageSource(resourceUrl: String, userAgent: String, referer: String?,
    connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): VaultImportSource {
    require(BrowserNavigationPolicy.isWebUrl(resourceUrl)) { "Unsupported image URL" }
    val filename = BrowserDownloadPolicy.safeDisplayName(URL(resourceUrl).path.substringAfterLast('/').ifBlank { "browser-image" })
    return VaultImportSource(
        displayName = filename,
        mimeType = URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream",
        openStream = { error("Primary network authority required") },
        openScopedStream = { guard ->
            guard.requireScope(uk.co.traynor.privategallery.core.security.PrimaryScope.BROWSER_UPLOAD_EGRESS)
            guard.check()
            val transport = guard.connection { connectionFactory(URL(resourceUrl)) }
            try {
                transport.value.apply {
                    guard.check()
                    instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", userAgent)
                    referer?.takeIf(BrowserNavigationPolicy::isWebUrl)?.let { setRequestProperty("Referer", it) }
                    CookieManager.getInstance().getCookie(resourceUrl)?.let { setRequestProperty("Cookie", it) }
                    require(BrowserDownloadPolicy.acceptsResponse(url.toString(), responseCode)) { "Image response was rejected" }
                }
                guard.check()
                transport.input { it.inputStream }
            } catch (failure: Throwable) {
                transport.close()
                throw failure
            }
        },
    )
}

/** Browser network acquisition carries egress authority; local captures need only import rights. */
internal fun browserImportOperation(authority: uk.co.traynor.privategallery.core.security.PrimarySessionAuthority,
    networkSource: Boolean): uk.co.traynor.privategallery.core.security.PrimaryOperation? {
    val scopes = setOf(uk.co.traynor.privategallery.core.security.PrimaryScope.READ,
        uk.co.traynor.privategallery.core.security.PrimaryScope.WRITE)
    return authority.operationOrNull(if (networkSource) scopes +
        uk.co.traynor.privategallery.core.security.PrimaryScope.BROWSER_UPLOAD_EGRESS else scopes)
}
