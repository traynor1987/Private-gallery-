package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.browser.BrowserAcceptanceDebugConsole

/**
 * Android owner for V2 tabs. WebViews live here rather than in Compose; a composable may only
 * attach the active view. A failed/closed tab is the sole normal destruction path.
 */
class BrowserV2Session(
    private val appContext: Context,
    private val vpnGate: BrowserVpnGate,
    listener: Listener,
    maximumTabs: Int = 8,
    private val onMetadataChanged: (BrowserSessionSnapshot) -> Unit = {},
) : BrowserWebViewCallbacks {
    interface Listener {
        fun onSessionChanged()
        fun onMessage(message: BrowserMessage)
        fun onDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String)
        fun onImageLongPress(resourceUrl: String?)
        fun onExternalNavigation(value: String)
        fun onHistoryVisit(title: String, url: String)
        fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
        fun onExitFullscreen()
        fun onPermissionRequest(request: android.webkit.PermissionRequest)
        fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback)
        fun onShowFileChooser(callback: android.webkit.ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    }

    private var listener: Listener = listener

    val tabs = BrowserSessionManager(maximumTabs)
    private val webViews = linkedMapOf<String, WebView>()
    /** Restored tabs are cold metadata until Browser becomes visible after unlock. */
    private val coldRestoreIds = linkedSetOf<String>()
    private val factory = SecureWebViewFactory(this)
    private val diagnostics = BrowserAcceptanceDebugConsole(BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS)

    /** The Activity owns this session; the visible composable only binds the current UI delegate. */
    fun bindListener(value: Listener) { listener = value }

    fun activeWebView(): WebView = webView(tabs.activeTab.id).also { loadColdRestoreIfNeeded(tabs.activeTab.id, it) }
    fun webView(tabId: String): WebView = webViews.getOrPut(tabId) {
        factory.create(appContext, tabId, tabs.tabs.first { it.id == tabId }.desktopSite).also {
            tabs.attachWebView(tabId, "v2-$tabId")
            val provider = WebView.getCurrentWebViewPackage()
            diagnostics.record("WEBVIEW_CREATED", mapOf("tab" to "created"))
            diagnostics.record("WEBVIEW_PROVIDER", mapOf("package" to (provider?.packageName ?: "unknown"), "version" to (provider?.versionName ?: "unknown")))
            diagnostics.record("WEBVIEW_CONFIGURATION", mapOf(
                "javascript" to it.settings.javaScriptEnabled.toString(),
                "dom_storage" to it.settings.domStorageEnabled.toString(),
                "third_party_cookies" to android.webkit.CookieManager.getInstance().acceptThirdPartyCookies(it).toString(),
                "multiple_windows" to it.settings.supportMultipleWindows().toString(),
                "mixed_content" to it.settings.mixedContentMode.toString(),
            ))
            changed()
        }
    }

    /**
     * At most eight live tabs are retained. When that limit is reached, the oldest non-selected
     * tab is discarded completely; only its already-persisted encrypted metadata can be restored
     * later. This prevents invisible WebViews from accumulating indefinitely.
     */
    fun newTab(url: String = ""): BrowserTab {
        val idsBefore = tabs.tabs.mapTo(linkedSetOf()) { it.id }
        val tab = tabs.newTab()
        val retainedIds = tabs.tabs.mapTo(hashSetOf()) { it.id }
        (idsBefore - retainedIds).forEach { evictedId ->
            webViews.remove(evictedId)?.let(::destroy)
            diagnostics.record("WEBVIEW_EVICTED", mapOf("reason" to "tab_limit"))
        }
        webView(tab.id)
        if (url.isNotBlank()) navigate(tab.id, url)
        changed()
        return tab
    }

    fun select(tabId: String) { tabs.select(tabId); activeWebView(); changed() }

    fun close(tabId: String) {
        webViews.remove(tabId)?.let(::destroy)
        tabs.close(tabId)
        webView(tabs.activeTab.id)
        changed()
    }

    fun navigateActive(url: String) = navigate(tabs.activeTab.id, url)
    fun reloadActive() { requireNetworkOrReport() ?: return; activeWebView().reload() }
    fun stopActive() = activeWebView().stopLoading()
    fun goBackActive() { activeWebView().takeIf { it.canGoBack() }?.goBack() }
    fun goForwardActive() { activeWebView().takeIf { it.canGoForward() }?.goForward() }
    fun findInActivePage(text: String) { activeWebView().findAllAsync(text) }
    fun findNextInActivePage(forward: Boolean) { activeWebView().findNext(forward) }
    fun clearFindInActivePage() { activeWebView().clearMatches() }

    fun setDesktopSite(tabId: String, enabled: Boolean) {
        val tab = tabs.tabs.first { it.id == tabId }
        if (tab.desktopSite == enabled) return
        tabs.setDesktopSite(tabId, enabled)
        webViews[tabId]?.let { view ->
            view.settings.userAgentString = BrowserSecurityPolicy.userAgent(
                if (enabled) BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE,
            )
            if (tab.url.isNotBlank()) {
                if (requireNetworkOrReport() != null) view.reload()
            }
        }
        changed()
    }

    fun recoverRenderer(tabId: String) {
        val url = tabs.tabs.first { it.id == tabId }.url
        webViews.remove(tabId)?.let(::destroy)
        tabs.detachWebView(tabId)
        webView(tabId)
        if (url.isNotBlank()) navigate(tabId, url)
        changed()
    }

    fun destroyAll() {
        webViews.values.toList().forEach(::destroy)
        webViews.clear()
        tabs.tabs.forEach { tabs.detachWebView(it.id) }
        changed()
    }
    fun acceptanceReport(): String = diagnostics.report()
    fun clearAcceptanceReport() = diagnostics.clear()
    fun metadataSnapshot() = BrowserSessionSnapshot(tabs.tabs, tabs.activeTab.id)
    fun restoreMetadata(snapshot: BrowserSessionSnapshot) {
        tabs.restore(snapshot.tabs, snapshot.selectedTabId)
        coldRestoreIds.clear()
        coldRestoreIds += tabs.tabs.filter { it.url.isNotBlank() }.map { it.id }
        changed()
    }

    fun clearWebData() {
        webViews.values.forEach { view ->
            view.stopLoading()
            view.clearHistory()
            view.clearCache(true)
            view.clearFormData()
        }
    }

    private fun navigate(tabId: String, url: String) {
        coldRestoreIds.remove(tabId)
        if (!BrowserSecurityPolicy.allowsNavigation(url)) {
            listener.onMessage(BrowserMessage.UnsupportedScheme)
            return
        }
        if (requireNetworkOrReport() == null) return
        diagnostics.startNavigation(mapOf("scheme" to (runCatching { java.net.URI(url).scheme }.getOrNull() ?: "unknown"), "main_frame" to "true"))
        webView(tabId).apply {
            loadUrl(url)
            post { if (isAttachedToWindow) requestFocus() }
        }
    }

    private fun requireNetworkOrReport(): Unit? = if (vpnGate.permitsRemoteNetworking()) Unit else {
        listener.onMessage(BrowserMessage.VpnRequired)
        null
    }

    private fun loadColdRestoreIfNeeded(tabId: String, view: WebView) {
        if (tabId !in coldRestoreIds) return
        val url = tabs.tabs.firstOrNull { it.id == tabId }?.url.orEmpty()
        if (!BrowserSecurityPolicy.allowsNavigation(url)) return
        if (requireNetworkOrReport() == null) return
        coldRestoreIds.remove(tabId)
        diagnostics.startNavigation(mapOf("scheme" to (runCatching { java.net.URI(url).scheme }.getOrNull() ?: "unknown"), "main_frame" to "true", "restore" to "true"))
        view.loadUrl(url)
    }

    private fun destroy(view: WebView) {
        view.stopLoading()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.setDownloadListener(null)
        view.setOnLongClickListener(null)
        view.webChromeClient = WebChromeClient()
        view.webViewClient = WebViewClient()
        view.clearMatches()
        view.destroy()
    }
    private fun changed() { onMetadataChanged(metadataSnapshot()); listener.onSessionChanged() }

    override fun onNavigationRequest(tabId: String, url: String, mainFrame: Boolean, allowed: Boolean): Boolean {
        if (!allowed) {
            if (mainFrame && BrowserExternalNavigationPolicy.isCandidate(url)) listener.onExternalNavigation(url)
            else listener.onMessage(BrowserMessage.UnsupportedScheme)
            return true
        }
        if (mainFrame && !vpnGate.permitsRemoteNetworking()) {
            diagnostics.record("VPN_GATE_BLOCKED_REQUEST", mapOf("main_frame" to "true"), true)
            listener.onMessage(BrowserMessage.VpnRequired)
            return true
        }
        return false
    }
    override fun onPageState(tabId: String, url: String, title: String, loading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        tabs.updateNavigation(tabId, url, title, loading, canGoBack, canGoForward)
        diagnostics.record(if (loading) "MAIN_PAGE_STARTED" else "MAIN_PAGE_FINISHED")
        if (!loading && BrowserSecurityPolicy.allowsNavigation(url)) listener.onHistoryVisit(title, url)
        changed()
    }
    override fun onTitle(tabId: String, title: String, canGoBack: Boolean, canGoForward: Boolean) {
        val tab = tabs.tabs.first { it.id == tabId }
        tabs.updateNavigation(tabId, tab.url, title, tab.loading, canGoBack, canGoForward)
        changed()
    }
    override fun onPageError(tabId: String, mainFrame: Boolean, errorCode: Int) { diagnostics.record(if (mainFrame) "MAIN_PAGE_ERROR" else "RESOURCE_ERROR", mapOf("code" to errorCode.toString()), true); if (mainFrame) listener.onMessage(BrowserMessage.NetworkError) }
    override fun onHttpError(tabId: String, mainFrame: Boolean, statusCode: Int) { diagnostics.record(if (mainFrame) "MAIN_HTTP_ERROR" else "RESOURCE_HTTP_ERROR", mapOf("status" to statusCode.toString()), true); if (mainFrame) listener.onMessage(BrowserMessage.HttpError(statusCode)) }
    override fun onTlsRejected(tabId: String) { diagnostics.record("SSL_ERROR", mapOf("decision" to "cancel"), true); listener.onMessage(BrowserMessage.TlsRejected) }
    override fun onRendererGone(tabId: String) {
        webViews.remove(tabId)?.let(::destroy)
        tabs.rendererGone(tabId)
        listener.onMessage(BrowserMessage.RendererGone)
        changed()
    }
    override fun onProgress(tabId: String, progress: Int) = Unit
    override fun onCreateWindow(parentTabId: String, isDialog: Boolean, isUserGesture: Boolean): WebView? {
        // WebViewTransport receives a genuine tab-owned child, preserving the normal opener path.
        return newTab().let { webView(it.id) }
    }
    override fun onCloseWindow(tabId: String) { if (tabs.tabs.any { it.id == tabId }) close(tabId) }
    override fun onShowCustomView(tabId: String, view: View, callback: WebChromeClient.CustomViewCallback) = listener.onFullscreen(view, callback)
    override fun onHideCustomView(tabId: String) = listener.onExitFullscreen()
    override fun onPermissionRequest(tabId: String, request: android.webkit.PermissionRequest) = listener.onPermissionRequest(request)
    override fun onGeolocationRequest(tabId: String, origin: String, callback: android.webkit.GeolocationPermissions.Callback) = listener.onGeolocationRequest(origin, callback)
    override fun onShowFileChooser(tabId: String, callback: android.webkit.ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean = listener.onShowFileChooser(callback, params)
    override fun onDownload(tabId: String, url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if (vpnGate.permitsRemoteNetworking()) listener.onDownload(url, userAgent, contentDisposition, mimeType) else listener.onMessage(BrowserMessage.VpnRequired)
    }
    override fun onImageLongPress(tabId: String, resourceUrl: String?) = listener.onImageLongPress(resourceUrl)
    override fun onResourceObserved(tabId: String) = diagnostics.record("RESOURCE_REQUEST")
    override fun onConsole(tabId: String, level: String, message: String?, line: Int) = diagnostics.recordConsole(level, message, line)
}

sealed interface BrowserMessage {
    data object VpnRequired : BrowserMessage
    data object UnsupportedScheme : BrowserMessage
    data object NetworkError : BrowserMessage
    data object TlsRejected : BrowserMessage
    data object RendererGone : BrowserMessage
    data class HttpError(val statusCode: Int) : BrowserMessage
}

object NoopBrowserV2Listener : BrowserV2Session.Listener {
    override fun onSessionChanged() = Unit
    override fun onMessage(message: BrowserMessage) = Unit
    override fun onDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) = Unit
    override fun onImageLongPress(resourceUrl: String?) = Unit
    override fun onExternalNavigation(value: String) = Unit
    override fun onHistoryVisit(title: String, url: String) = Unit
    override fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) = Unit
    override fun onExitFullscreen() = Unit
    override fun onPermissionRequest(request: android.webkit.PermissionRequest) = request.deny()
    override fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
    override fun onShowFileChooser(callback: android.webkit.ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean { callback.onReceiveValue(null); return true }
}
