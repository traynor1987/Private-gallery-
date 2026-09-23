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
    private val webViewFactory: BrowserV2WebViewFactory = BrowserV2WebViewFactory { context, callbacks, tabId, desktopSite ->
        SecureWebViewFactory(callbacks).create(context, tabId, desktopSite)
    },
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
    private val presentationProbes = mutableMapOf<WebView, BrowserWebViewPresentationProbe>()
    /** Restored tabs are cold metadata until Browser becomes visible after unlock. */
    private val coldRestoreIds = linkedSetOf<String>()
    private val diagnostics = BrowserAcceptanceDebugConsole(BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS)
    private var focusMode = BrowserFocusMode.CURRENT
    private var lastFocusAttachment: Pair<String, BrowserFocusMode>? = null
    private var androidViewUpdateCount = 0

    /** The Activity owns this session; the visible composable only binds the current UI delegate. */
    fun bindListener(value: Listener) { listener = value }

    /**
     * The Browser surface must survive an unavailable or crashing system WebView provider.
     * The Android view is therefore optional to Compose, while tab chrome remains available.
     */
    fun activeWebViewOrNull(): WebView? {
        if (tabs.activeTab.failure == BrowserTabFailure.WEBVIEW_UNAVAILABLE) return null
        return runCatching { activeWebView() }.getOrElse { failure ->
        tabs.webViewUnavailable(tabs.activeTab.id)
        diagnostics.record("WEBVIEW_CREATE_FAILED", mapOf("type" to failure.javaClass.simpleName.take(80)), true)
        recordCrashContext("WEBVIEW_CREATE_FAILED")
        changed()
        null
        }
    }

    fun retryActiveWebView(): WebView? {
        val id = tabs.activeTab.id
        webViews.remove(id)?.let(::destroy)
        tabs.detachWebView(id)
        tabs.retryWebView(id)
        lastFocusAttachment = null
        return activeWebViewOrNull()
    }

    fun activeWebView(): WebView = webView(tabs.activeTab.id).also { loadColdRestoreIfNeeded(tabs.activeTab.id, it) }
    fun activeFocusMode(): BrowserFocusMode = focusMode
    fun verboseDiagnosticsEnabled(): Boolean = diagnostics.isCaptureEnabled()

    /** These controls are compiled into signed acceptance builds only. */
    fun setAcceptanceFocusMode(mode: BrowserFocusMode) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        focusMode = mode
        lastFocusAttachment = null
        activeWebViewOrNull()?.let(::onActiveWebViewUpdated)
    }

    fun setVerboseDiagnostics(enabled: Boolean) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        diagnostics.setCaptureEnabled(enabled)
    }

    fun onActiveWebViewHostCreated(view: WebView) {
        recordAcceptanceUiEvent("WEBVIEW_HOST_CREATED", mapOf("clip_to_bounds" to "true"))
        presentationProbes[view]?.recordState("HOST_CREATED")
    }

    fun onActiveWebViewUpdated(view: WebView) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        androidViewUpdateCount++
        recordWebViewParentEvent("WEBVIEW_HOST_UPDATED", view)
        view.post {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val parentView = view.parent as? View
            val measured = mapOf(
                    "x_window" to location[0].toString(),
                    "y_window" to location[1].toString(),
                    "width" to view.width.toString(),
                    "height" to view.height.toString(),
                    "left_in_parent" to view.left.toString(),
                    "top_in_parent" to view.top.toString(),
                    "parent_width" to (parentView?.width ?: 0).toString(),
                    "parent_height" to (parentView?.height ?: 0).toString(),
                )
            diagnostics.record("WEBVIEW_NATIVE_MEASURED", measured)
            recordCrashContext("WEBVIEW_NATIVE_MEASURED", measured)
        }
        val key = tabs.activeTab.id to focusMode
        if (key == lastFocusAttachment) return
        lastFocusAttachment = key
        view.post {
            if (focusMode == BrowserFocusMode.EXPLICIT_WEBVIEW_FOCUS && view.isAttachedToWindow && view.rootView.hasWindowFocus()) {
                view.requestFocus()
            }
            diagnostics.record(
                "WEBVIEW_FOCUS_STATE",
                mapOf(
                    "mode" to focusMode.name.lowercase(),
                    "native_has_focus" to view.hasFocus().toString(),
                    "native_is_focused" to view.isFocused.toString(),
                    "root_has_focus" to view.rootView.hasFocus().toString(),
                    "window_focus" to view.rootView.hasWindowFocus().toString(),
                ),
            )
            view.evaluateJavascript("(function(){return document.hasFocus()?'true':'false'})()") { value ->
                diagnostics.record("DOCUMENT_FOCUS", mapOf("has_focus" to value.trim('"')))
            }
        }
    }

    fun onActiveWebViewDetached(view: WebView) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        recordWebViewParentEvent("WEBVIEW_HOST_RELEASED", view)
    }

    fun loadAcceptanceLocalTestPage() {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        val view = activeWebViewOrNull() ?: return
        recordAcceptanceUiEvent("NAVIGATION_SUBMITTED", mapOf("local_fixture" to "true"))
        presentationProbes[view]?.recordState("NAVIGATION_SUBMITTED")
        view.loadDataWithBaseURL(
            "about:blank",
            "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{height:100%;margin:0;background:#34205f;color:white;font:700 28px sans-serif}main{height:100%;display:grid;place-content:center;text-align:center}</style><main><div>REAL WEBVIEW TEST</div><div style='font-size:16px;margin-top:12px'>Local acceptance page · no network</div></main>",
            "text/html",
            "UTF-8",
            null,
        )
        diagnostics.record("ACCEPTANCE_LOCAL_PAGE_REQUESTED")
        recordCrashContext("ACCEPTANCE_LOCAL_PAGE_REQUESTED")
    }

    fun webView(tabId: String): WebView = webViews.getOrPut(tabId) {
        webViewFactory.create(appContext, this, tabId, tabs.tabs.first { it.id == tabId }.desktopSite).also {
            tabs.attachWebView(tabId, "v2-$tabId")
            if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) {
                presentationProbes[it] = BrowserWebViewPresentationProbe(it, ::recordAcceptanceUiEvent)
            }
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
        webViewOrNull(tab.id)
        if (url.isNotBlank()) navigate(tab.id, url)
        changed()
        return tab
    }

    fun select(tabId: String) { tabs.select(tabId); activeWebViewOrNull(); changed() }

    fun close(tabId: String) {
        webViews.remove(tabId)?.let(::destroy)
        tabs.close(tabId)
        activeWebViewOrNull()
        changed()
    }

    fun navigateActive(url: String) = navigate(tabs.activeTab.id, url)
    fun reloadActive() {
        requireNetworkOrReport() ?: return
        val active = tabs.activeTab
        if (active.failure == BrowserTabFailure.RENDERER_GONE) recoverRenderer(active.id)
        else activeWebViewOrNull()?.reload()
    }
    fun stopActive() = activeWebViewOrNull()?.stopLoading()
    fun goBackActive() { activeWebViewOrNull()?.takeIf { it.canGoBack() }?.goBack() }
    fun goForwardActive() { activeWebViewOrNull()?.takeIf { it.canGoForward() }?.goForward() }
    fun findInActivePage(text: String) { activeWebViewOrNull()?.findAllAsync(text) }
    fun findNextInActivePage(forward: Boolean) { activeWebViewOrNull()?.findNext(forward) }
    fun clearFindInActivePage() { activeWebViewOrNull()?.clearMatches() }

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
        activeWebViewOrNull()
        if (url.isNotBlank()) navigate(tabId, url)
        changed()
    }

    fun destroyAll() {
        webViews.values.toList().forEach(::destroy)
        webViews.clear()
        tabs.tabs.forEach { tabs.detachWebView(it.id) }
        changed()
    }
    fun acceptanceReport(): String = buildString {
        append(diagnostics.report(mapOf("focus_mode" to focusMode.name.lowercase())))
        BrowserV2FatalCrashCapture.readLastReport(appContext)?.let {
            appendLine()
            appendLine("Persisted fatal report from previous process:")
            append(it)
        }
    }
    /** Structural UI markers contain only labels and numeric bounds, never page/private data. */
    fun recordAcceptanceUiEvent(category: String, details: Map<String, String> = emptyMap()) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        diagnostics.record(category, details)
        recordCrashContext(category, details)
    }
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
        recordAcceptanceUiEvent("NAVIGATION_SUBMITTED")
        webViews[tabId]?.let { presentationProbes[it]?.recordState("NAVIGATION_SUBMITTED") }
        coldRestoreIds.remove(tabId)
        if (!BrowserSecurityPolicy.allowsNavigation(url)) {
            listener.onMessage(BrowserMessage.UnsupportedScheme)
            return
        }
        if (requireNetworkOrReport() == null) return
        diagnostics.startNavigation(mapOf("main_frame" to "true"), preserveEvents = true)
        webViewOrNull(tabId)?.apply {
            loadUrl(url)
            post { if (isAttachedToWindow) requestFocus() }
        }
    }

    private fun requireNetworkOrReport(): Unit? = if (vpnGate.permitsRemoteNetworking()) Unit else {
        listener.onMessage(BrowserMessage.VpnRequired)
        null
    }

    private fun webViewOrNull(tabId: String): WebView? = runCatching { webView(tabId) }.getOrElse { failure ->
        tabs.webViewUnavailable(tabId)
        diagnostics.record("WEBVIEW_CREATE_FAILED", mapOf("type" to failure.javaClass.simpleName.take(80)), true)
        changed()
        null
    }

    private fun loadColdRestoreIfNeeded(tabId: String, view: WebView) {
        if (tabId !in coldRestoreIds) return
        val url = tabs.tabs.firstOrNull { it.id == tabId }?.url.orEmpty()
        if (!BrowserSecurityPolicy.allowsNavigation(url)) return
        if (requireNetworkOrReport() == null) return
        coldRestoreIds.remove(tabId)
        diagnostics.startNavigation(mapOf("main_frame" to "true", "restore" to "true"), preserveEvents = true)
        recordAcceptanceUiEvent("NAVIGATION_SUBMITTED", mapOf("restore" to "true"))
        presentationProbes[view]?.recordState("NAVIGATION_SUBMITTED")
        view.loadUrl(url)
    }

    private fun destroy(view: WebView) {
        presentationProbes.remove(view)?.dispose()
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

    private fun recordWebViewParentEvent(category: String, view: WebView) {
        val parent = view.parent
        diagnostics.record(
            category,
            mapOf(
                "parent" to (parent?.javaClass?.simpleName ?: "none").take(80),
                "attached" to (parent != null).toString(),
                "window_attached" to view.isAttachedToWindow.toString(),
            ),
        )
        recordCrashContext(category)
    }

    private fun recordCrashContext(category: String, details: Map<String, String> = emptyMap()) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        val allTabs = runCatching { tabs.tabs }.getOrDefault(emptyList())
        val active = runCatching { tabs.activeTab }.getOrNull()
        val selectedExists = active != null && allTabs.any { it.id == active.id }
        val view = active?.let { webViews[it.id] }
        val parent = view?.parent
        BrowserV2CrashContextStore.record(
            event = category,
            activeTabCount = allTabs.size,
            selectedTabExists = selectedExists,
            webViewAttached = parent != null,
            webViewAttachedToWindow = view?.isAttachedToWindow == true,
            webViewParentCategory = parent?.javaClass?.simpleName ?: "none",
            androidViewUpdateCount = androidViewUpdateCount,
            numericDetails = details,
        )
    }

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
        recordPresentationTransition(tabId, if (loading) "PAGE_STARTED" else "PAGE_FINISHED")
        if (!loading && BrowserSecurityPolicy.allowsNavigation(url)) listener.onHistoryVisit(title, url)
        changed()
    }
    override fun onPageCommitVisible(tabId: String) {
        diagnostics.record("MAIN_PAGE_COMMIT_VISIBLE")
        recordPresentationTransition(tabId, "PAGE_COMMIT_VISIBLE")
    }

    private fun recordPresentationTransition(tabId: String, event: String) {
        recordAcceptanceUiEvent(event)
        webViews[tabId]?.let { presentationProbes[it]?.recordState(event) }
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
    // Page console text can contain private data even without URLs: retain severity/counts only.
    override fun onConsole(tabId: String, level: String, message: String?, line: Int) = diagnostics.recordConsole(level, null, line)
}

fun interface BrowserV2WebViewFactory {
    fun create(context: Context, callbacks: BrowserWebViewCallbacks, tabId: String, desktopSite: Boolean): WebView
}

enum class BrowserFocusMode { CURRENT, EXPLICIT_WEBVIEW_FOCUS }

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
