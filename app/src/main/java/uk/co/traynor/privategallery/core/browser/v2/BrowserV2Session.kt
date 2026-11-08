package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.browser.BrowserAcceptanceDebugConsole
import kotlinx.coroutines.launch

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
    private val rendererAllowed: () -> Boolean = { true },
    val contentBlocker: BrowserContentBlocker = BrowserContentBlocker(),
    private val webViewFactory: BrowserV2WebViewFactory = BrowserV2WebViewFactory { context, callbacks, tabId, desktopSite ->
        SecureWebViewFactory(callbacks, contentBlocker = contentBlocker).create(context, tabId, desktopSite)
    },
    private val destinationRestricted: (String) -> Boolean = { false },
    private val privateAppRestricted: (uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App) -> Boolean = { false },
) : BrowserWebViewCallbacks {
    interface Listener {
        /** Native window callbacks require a live interactive browser presentation. */
        fun allowWindowInteraction(): Boolean = false
        fun onUserScroll(deltaY: Int, atTop: Boolean) = Unit
        fun onSessionChanged()
        fun onMessage(message: BrowserMessage)
        fun onDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String)
        fun onImageLongPress(resourceUrl: String?)
        fun onExternalNavigation(value: String)
        fun onHistoryVisit(title: String, url: String)
        fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
        fun onExitFullscreen()
        fun onPermissionRequest(request: android.webkit.PermissionRequest)
        fun onPermissionRequestCanceled(request: android.webkit.PermissionRequest) = Unit
        fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback)
        fun onShowFileChooser(callback: android.webkit.ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean
        fun onVideoSaveRequested() = Unit
    }

    private val pageFailures = mutableMapOf<String, String>()
    private val navigationTimeouts = mutableMapOf<String, Runnable>()
    private val pageHandler = android.os.Handler(android.os.Looper.getMainLooper())
    fun pageFailure(tabId: String): String? = pageFailures[tabId]
    private fun cancelNavigationTimeout(tabId: String) { navigationTimeouts.remove(tabId)?.let(pageHandler::removeCallbacks) }
    private fun pageFailed(tabId: String, detail: String) {
        if (tabs.tabs.none { it.id == tabId }) return
        cancelNavigationTimeout(tabId)
        pageFailures[tabId] = detail
        val tab = tabs.tabs.first { it.id == tabId }
        tabs.updateNavigation(tabId, tab.url, tab.title, false, tab.canGoBack, tab.canGoForward)
        webViews[tabId]?.stopLoading()
        changed()
    }
    private fun watchNavigation(tabId: String) {
        cancelNavigationTimeout(tabId)
        pageFailures.remove(tabId)
        val timeout = Runnable {
            if (vpnGate.permitsRemoteNetworking() && webViews.containsKey(tabId)) {
                diagnostics.record("MAIN_PAGE_TIMEOUT", mapOf("code" to "-8"), true)
                pageFailed(tabId, "The page did not respond within 45 seconds (timeout -8). Check VPN handshake details and retry.")
            }
        }
        navigationTimeouts[tabId] = timeout
        pageHandler.postDelayed(timeout, 45_000)
    }

    private var listener: Listener = listener

    val tabs = BrowserSessionManager(maximumTabs)
    private val webViews = linkedMapOf<String, WebView>()
    // A blocked main-frame callback is not published to browser history or persisted tab metadata.
    private val restrictedDocuments = mutableMapOf<String, String>()
    private val restrictedViews = mutableSetOf<WebView>()

    private fun appRestricted(app: uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App): Boolean =
        privateAppRestricted(app) || destinationRestricted(app.startUrl())

    private fun tabRestricted(tabId: String): Boolean =
        privateAppTabs[tabId]?.let(::appRestricted) == true ||
            restrictedDocuments[tabId]?.let(destinationRestricted) == true ||
            tabs.tabs.firstOrNull { it.id == tabId }?.url?.let(destinationRestricted) == true ||
            webViews[tabId]?.url?.let(destinationRestricted) == true

    fun activeDestinationRestricted(): Boolean = tabRestricted(tabs.activeTab.id)

    /** Update presentation only; retained renderers, cookies and saved app identity stay intact. */
    fun enforceDestinationPolicy() {
        webViews.forEach { (id, view) -> setViewRestricted(id, view, tabRestricted(id)) }
        listener.onSessionChanged()
    }

    private fun setViewRestricted(tabId: String, view: WebView, blocked: Boolean) {
        if (blocked && restrictedViews.add(view)) {
            view.visibility = View.INVISIBLE
            view.stopLoading()
            view.evaluateJavascript(BrowserVideoAssistant.PAUSE, null)
            view.onPause()
            if (scriptDialog?.tabId == tabId) cancelScriptDialog()
            if (fullscreenOwner == tabId) exitFullscreen()
        } else if (!blocked && restrictedViews.remove(view)) {
            view.visibility = View.VISIBLE
            if (foreground && vpnGate.permitsRemoteNetworking()) view.onResume()
        }
    }

    private fun reportRestrictedDestination() = listener.onMessage(BrowserMessage.SanctuaryRestricted)
    // Presentation belongs to an existing tab, never to the selected URL or a second WebView.
    private val privateAppTabs = mutableMapOf<String, uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App>()
    fun privateApp(tabId: String) = privateAppTabs[tabId]
    /** Restore admission must include cold app bindings, not just already-created renderers. */
    fun hasPrivateAppTabs(): Boolean = privateAppTabs.isNotEmpty()
    /** Bounded snapshot of existing app renderers only; never activates or creates cold tabs. */
    fun createdPrivateAppWebViews(): List<Pair<String, WebView>> = tabs.tabs.mapNotNull {tab ->
        if(privateAppTabs.containsKey(tab.id))webViews[tab.id]?.let {tab.id to it} else null
    }
    fun openPrivateApp(app: uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App) {
        if (appRestricted(app)) { reportRestrictedDestination(); return }
        privateAppTabs.entries.firstOrNull {it.value.id()==app.id() && tabs.tabs.any {tab->tab.id==it.key}}?.let {
            privateAppTabs[it.key]=app;select(it.key);return
        }
        pauseOutgoingTab()
        exitFullscreen()
        createTab(app.startUrl(), app)
    }
    /** Explicit authenticated Nova app launch; uses the same tab registry, renderer and network gate.
     * Unlike ordinary browser tab selection, an outgoing registered Private App keeps its media.
     * This never starts playback or grants foreground, renderer, transport or vault authority.
     */
    fun openRetainedPrivateApp(app: uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App) {
        requireNovaForeground()
        check(!appRestricted(app)) {"This app is restricted by Sanctuary."}
        privateAppTabs.entries.firstOrNull {it.value.id()==app.id() && tabs.tabs.any {tab->tab.id==it.key}}?.let {
            privateAppTabs[it.key]=app;selectFromNovaHome(it.key);return
        }
        prepareNovaSwitch()
        createTab(app.startUrl(), app)
    }

    /** Copies only the current destination into a fresh ordinary tab with its own back stack. */
    fun openActivePrivateAppInBrowser(): BrowserTab {
        requireNovaForeground()
        val source=tabs.activeTab
        val app=checkNotNull(privateAppTabs[source.id]) {"A saved app must be selected"}
        val destination=source.url.ifBlank {app.startUrl()}
        check(BrowserSecurityPolicy.allowsNavigation(destination)) {"A web address is required"}
        check(!tabRestricted(source.id) && !destinationRestricted(destination)) {"This destination is restricted by Sanctuary."}
        prepareNovaSwitch()
        return createTab(destination)
    }

    /** Existing Primary authority must be checked by the calling Activity/controller. */
    private fun requireNovaForeground() {
        check(android.os.Looper.myLooper()===android.os.Looper.getMainLooper())
        check(rendererAllowed() && mediaNetworkingAllowed()) {"Foreground browser admission is required"}
    }
    private fun prepareNovaSwitch() {
        if(privateAppTabs.containsKey(tabs.activeTab.id))cancelScriptDialog() else pauseOutgoingTab()
        exitFullscreen()
    }
    fun removePrivateApp(appId:String) {
        privateAppTabs.filterValues {it.id()==appId}.keys.toList().forEach(::close)
    }
    fun updatePrivateApp(app: uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App) {
        privateAppTabs.entries.filter { it.value.id() == app.id() }.forEach { it.setValue(app) }
        changed()
    }

    private val observedMedia = java.util.concurrent.ConcurrentHashMap<String, ObservedMediaRequests>()
    private val playingVideoTabs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val mediaProbeResults = mutableMapOf<String, MediaSaveCandidate>() // UI thread only, never persisted
    private val mediaProbing = mutableSetOf<String>()
    private val mediaProbeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val runtimeProbes = mutableMapOf<WebView, BrowserRuntimeProbe>()
    private val pendingWindowFocus = mutableMapOf<String, WebView>()
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
        if(!rendererAllowed() || activeDestinationRestricted())return null
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
        if (activeDestinationRestricted()) return null
        val id = tabs.activeTab.id
        webViews.remove(id)?.let(::destroy)
        tabs.detachWebView(id)
        tabs.retryWebView(id)
        lastFocusAttachment = null
        return activeWebViewOrNull()
    }

    fun activeWebView(): WebView = webView(tabs.activeTab.id).also { loadColdRestoreIfNeeded(tabs.activeTab.id, it) }
    var scriptDialog: BrowserScriptDialog? = null
        private set
    /** Clear before replying: a resumed page can synchronously request another dialog. */
    fun resolveScriptDialog(request: BrowserScriptDialog, answer: String?) {
        if (scriptDialog !== request) return
        scriptDialog = null
        val allowed = foreground && vpnGate.permitsRemoteNetworking() && tabs.activeTab.id == request.tabId && webViews.containsKey(request.tabId)
        request.reply(if (allowed) answer else null)
        changed()
    }
    private fun cancelScriptDialog() { scriptDialog?.let { resolveScriptDialog(it, null) } }
    override fun onScriptDialog(tabId: String, url: String, message: String, initial: String, kind: BrowserScriptDialog.Kind, reply: (String?) -> Unit) {
        if (!foreground || !vpnGate.permitsRemoteNetworking() || tabs.activeTab.id != tabId || !webViews.containsKey(tabId) || scriptDialog != null) {
            reply(null); return
        }
        val origin = runCatching { java.net.URI(url).host }.getOrNull()?.take(253) ?: "Local page"
        scriptDialog = BrowserScriptDialog(tabId, origin, message.take(10000), kind, initial.take(4000), reply)
        changed()
    }

    private var foreground = true
    fun mediaNetworkingAllowed(): Boolean = foreground && vpnGate.permitsRemoteNetworking()

    private var fullscreenOwner: String? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    @Volatile private var documentRevision = 0L
    /** Read-only document fence for website permission ownership; never grants authority itself. */
    internal fun permissionDocumentRevision():Long = documentRevision
    internal fun permissionViewIsCurrent(tabId:String,view:WebView):Boolean = webViews[tabId]===view

    fun exitFullscreen() {
        val callback = fullscreenCallback ?: return
        fullscreenCallback = null
        fullscreenOwner = null
        listener.onExitFullscreen()
        callback.onCustomViewHidden()
    }

    fun recordMediaPath(path: String) {
        require(path in setOf("WEBVIEW_FULLSCREEN", "MEDIA3_DIRECT", "UNSUPPORTED"))
        diagnostics.record("VIDEO_PATH", mapOf("path" to path))
    }

    fun pauseActiveMedia() {
        webViews[tabs.activeTab.id]?.evaluateJavascript(BrowserVideoAssistant.PAUSE, null)
    }

    fun setVideoSaveAvailability(available: Boolean) {
        webViews[tabs.activeTab.id]?.evaluateJavascript(
            "window.postMessage({type:'private-gallery-video-save-available',available:${if (available) "true" else "false"}},'*')", null)
    }

    fun pauseForBackground() {
        foreground = false
        pendingWindowFocus.clear()
        cancelScriptDialog()
        exitFullscreen()
        webViews.values.forEach { it.evaluateJavascript(BrowserVideoAssistant.PAUSE, null); it.onPause() }
    }

    fun enforceNetworkPolicy() {
        if (vpnGate.permitsRemoteNetworking()) return
        cancelScriptDialog()
        // Detaching/onPause alone does not stop WebView networking or media requests.
        // Destroy the live renderers, preserving only existing tab metadata for gated reload.
        documentRevision++
        navigationTimeouts.keys.toList().forEach(::cancelNavigationTimeout)
        exitFullscreen()
        webViews.values.toList().forEach { it.settings.blockNetworkLoads = true; destroy(it) }
        webViews.clear()
        tabs.tabs.forEach { tabs.detachWebView(it.id); if (it.url.isNotBlank()) coldRestoreIds += it.id }
        changed()
    }

    fun resumeForeground() {
        foreground = true
        if (!vpnGate.permitsRemoteNetworking()) enforceNetworkPolicy()
        else webViews.forEach { (id, view) ->
            val blocked = tabRestricted(id)
            setViewRestricted(id, view, blocked)
            if (!blocked) view.onResume()
        }
    }

    /** Keep the existing WebView and its session; never try to resolve iframe/blob resources. */
    fun requestVideoView(completed: (Boolean) -> Unit) {
        if (!mediaNetworkingAllowed()) { completed(false); return }
        val view = activeWebViewOrNull() ?: run { completed(false); return }
        val revision = documentRevision
        view.evaluateJavascript("Boolean(document.querySelector('video,iframe'))") { result ->
            val valid = revision == documentRevision && webViews[tabs.activeTab.id] === view && mediaNetworkingAllowed()
            completed(valid && result == "true")
        }
    }

    /** Called only by an explicit owner action. Reads current top-document HTML5 video, no crawl. */
    fun requestPlayableMedia(completed: (Pair<String, String>?) -> Unit) {
        if (!vpnGate.permitsRemoteNetworking()) { completed(null); return }
        val tabId = tabs.activeTab.id
        val revision = documentRevision
        val view = activeWebViewOrNull() ?: run { completed(null); return }
        view.evaluateJavascript(BrowserVideoAssistant.SOURCE) { result ->
            if (tabs.activeTab.id != tabId || revision != documentRevision || webViews[tabId] !== view || !mediaNetworkingAllowed()) { completed(null); return@evaluateJavascript }
            val media = runCatching {
                val json = org.json.JSONObject(org.json.JSONTokener(result).nextValue() as String)
                val url = json.getString("url")
                // Any cookie-bound source stays with WebView. No cookies/headers are copied,
                // including across adaptive-stream redirects or segment hosts.
                val sessionBound = !android.webkit.CookieManager.getInstance().getCookie(url).isNullOrBlank()
                val mime = if (sessionBound) null else uk.co.traynor.privategallery.core.media.WebMediaPolicy.mime(url, json.optBoolean("video"), json.optBoolean("drm"))
                mime?.let { url to it }
            }.getOrNull()
            completed(media) // Pause WebView only after Media3 reaches READY, never on discovery.
        }
    }

    /** Current playing video plus bounded requests already observed for this document. */
    fun requestSaveCandidate(completed: (MediaSaveCandidate?) -> Unit) {
        if (!mediaNetworkingAllowed()) { completed(null); return }
        var expectedDurationMs: Long? = null
        val finish: (MediaSaveCandidate?) -> Unit = { completed(it?.copy(expectedDurationMs = expectedDurationMs)) }
        val tabId = tabs.activeTab.id
        val revision = documentRevision
        val view = activeWebViewOrNull() ?: run { completed(null); return }
        view.evaluateJavascript(BrowserVideoAssistant.SOURCE) { result ->
            if (tabs.activeTab.id != tabId || revision != documentRevision || webViews[tabId] !== view || !mediaNetworkingAllowed()) {
                completed(null); return@evaluateJavascript
            }
            val inspected = runCatching {
                val json = org.json.JSONObject(org.json.JSONTokener(result).nextValue() as String)
                expectedDurationMs = json.optLong("durationMs", 0).takeIf { it > 0 }
                val playing = json.optBoolean("playing") || tabId in playingVideoTabs
                if (!playing) null else {
                    val currentUrl = json.optString("url")
                    val candidate = observedMedia[tabId]?.best(currentUrl, json.optBoolean("drm"))
                        ?: BrowserMediaSavePolicy.classify(currentUrl, json.optBoolean("drm"))
                    val probeUrls = if (candidate.kind == MediaSaveKind.PROTECTED) emptyList() else
                        (listOf(candidate.url) + (observedMedia[tabId]?.probeUrls(currentUrl)
                            ?: listOf(currentUrl))).filter { it.startsWith("https://") }.distinct().take(4)
                    candidate to probeUrls
                }
            }.getOrNull()
            val candidate = inspected?.first
            val probeUrls = inspected?.second.orEmpty()
            if (probeUrls.isNotEmpty()) {
                val cached = probeUrls.mapNotNull { mediaProbeResults[it] }
                    .firstOrNull { it.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM, MediaSaveKind.PROTECTED) }
                if (cached != null) { finish(cached); return@evaluateJavascript }
                val fresh = probeUrls.filterNot { mediaProbeResults.containsKey(it) }
                if (fresh.isEmpty()) { finish(probeUrls.mapNotNull { mediaProbeResults[it] }.firstOrNull() ?: candidate); return@evaluateJavascript }
                if (mediaProbing.add(fresh.first())) {
                    val userAgent = view.settings.userAgentString
                    val page = tabs.activeTab.url
                    mediaProbeScope.launch {
                        var resultCandidate: MediaSaveCandidate? = null
                        val inspectedUrls = mutableMapOf<String, MediaSaveCandidate>()
                        for (probeUrl in fresh) {
                            if (revision != documentRevision || !mediaNetworkingAllowed()) break
                            val probe = BrowserMediaProbe.inspect(probeUrl, userAgent, page) {
                                revision != documentRevision || !mediaNetworkingAllowed()
                            }
                            inspectedUrls[probeUrl] = probe
                            if (probe.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM, MediaSaveKind.PROTECTED)) {
                                resultCandidate = probe; break
                            }
                            if (resultCandidate == null) resultCandidate = probe
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            mediaProbing.remove(fresh.first())
                            if (tabs.activeTab.id == tabId && revision == documentRevision && webViews[tabId] === view && mediaNetworkingAllowed()) {
                                mediaProbeResults.putAll(inspectedUrls)
                                recordMediaClassification(resultCandidate)
                                finish(resultCandidate)
                            }
                        }
                    }
                } else completed(null)
                return@evaluateJavascript
            }
            recordMediaClassification(candidate)
            finish(candidate)
        }
    }

    private fun recordMediaClassification(candidate: MediaSaveCandidate?) {
        if (candidate == null) return
        recordAcceptanceUiEvent("MEDIA_DETECTED")
        recordAcceptanceUiEvent(when (candidate.kind) {
                MediaSaveKind.DIRECT, MediaSaveKind.STREAM -> "MEDIA_DOWNLOADABLE"
                MediaSaveKind.PROTECTED -> "MEDIA_PROTECTED_OR_UNAVAILABLE"
                MediaSaveKind.UNSUPPORTED -> "MEDIA_UNSUPPORTED"
                else -> "MEDIA_DETECTED"
            }, if (candidate.reason == MediaSaveReason.NONE) emptyMap() else mapOf("category" to candidate.reason.name))
    }

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
        if (!enabled) runtimeProbes.values.forEach { it.pause() }
        diagnostics.setCaptureEnabled(enabled)
    }

    fun onActiveWebViewHostCreated(view: WebView) {
        recordAcceptanceUiEvent("WEBVIEW_HOST_CREATED", mapOf("clip_to_bounds" to "true"))
        presentationProbes[view]?.recordState("HOST_CREATED")
    }

    fun onActiveWebViewUpdated(view: WebView) {
        val activeId = tabs.activeTab.id
        if (pendingWindowFocus[activeId]===view) {
            val request=Runnable {
                if(pendingWindowFocus[activeId]!==view)return@Runnable
                if(!windowInteractionAllowed(activeId) || webViews[activeId]!==view)pendingWindowFocus.remove(activeId)
                else if(view.isAttachedToWindow && view.requestFocus())pendingWindowFocus.remove(activeId)
            }
            if(view.isAttachedToWindow)view.post(request)
            else view.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached:View) {
                    attached.removeOnAttachStateChangeListener(this)
                    attached.post(request)
                }
                override fun onViewDetachedFromWindow(detached:View) {}
            })
        }
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
        check(rendererAllowed()) {"Browser profile clearing is still in progress"}
        webViewFactory.create(appContext, this, tabId, tabs.tabs.first { it.id == tabId }.desktopSite).also {
            tabs.attachWebView(tabId, "v2-$tabId")
            if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) {
                presentationProbes[it] = BrowserWebViewPresentationProbe(it, ::recordAcceptanceUiEvent)
                val probe = BrowserRuntimeProbe(it, diagnostics::isCaptureEnabled, ::recordAcceptanceUiEvent)
                runtimeProbes[it] = probe
            }
            var lastScrollY = 0
            var downX = 0f
            var downY = 0f
            @Suppress("ClickableViewAccessibility")
            it.setOnTouchListener { view, event ->
                val probe = runtimeProbes[it]
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        lastScrollY = view.scrollY
                        downX = event.x; downY = event.y
                        probe?.inputStarted()
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val delta = view.scrollY - lastScrollY
                        lastScrollY = view.scrollY
                        // Observe actual vertical page movement, without consuming gestures or
                        // reacting to JS scroll/viewport changes. Ignore pinch/horizontal swipes.
                        if (event.pointerCount == 1 && kotlin.math.abs(event.y - downY) > kotlin.math.abs(event.x - downX) &&
                            tabs.activeTab.id == tabId && webViews[tabId] === view) {
                            listener.onUserScroll(delta, view.scrollY <= 0)
                        }
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> probe?.inputCanceled()
                    android.view.MotionEvent.ACTION_UP -> {
                        probe?.inputFinished()
                        if (probe != null) view.postOnAnimation { probe.capture("PAGE_INTERACTION") }
                    }
                }
                false // WebView keeps all touch, focus, selection, playback and scroll behavior.
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
                "automatic_windows" to it.settings.javaScriptCanOpenWindowsAutomatically.toString(),
                "provider_user_agent" to (it.settings.userAgentString == android.webkit.WebSettings.getDefaultUserAgent(appContext)).toString(),
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
        if (destinationRestricted(url)) { reportRestrictedDestination(); return tabs.activeTab }
        pauseOutgoingTab()
        exitFullscreen()
        return createTab(url)
    }

    /** Blank standalone Browser entry only; ordinary newTab retains its existing pause behavior. */
    fun newTabFromNovaHome(): BrowserTab {
        requireNovaForeground()
        prepareNovaSwitch()
        return createTab("")
    }

    private fun createTab(url: String, app: uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App? = null): BrowserTab {
        val idsBefore = tabs.tabs.mapTo(linkedSetOf()) { it.id }
        val tab = tabs.newTab()
        // Classify before any renderer callback or metadata publication can escape this method.
        if(app!=null)privateAppTabs[tab.id]=app
        val retainedIds = tabs.tabs.mapTo(hashSetOf()) { it.id }
        (idsBefore - retainedIds).forEach { evictedId ->
            privateAppTabs.remove(evictedId)
            restrictedDocuments.remove(evictedId)
            webViews.remove(evictedId)?.let(::destroy)
            diagnostics.record("WEBVIEW_EVICTED", mapOf("reason" to "tab_limit"))
        }
        webViewOrNull(tab.id)
        if (url.isNotBlank()) navigate(tab.id, url)
        changed()
        return tab
    }

    private fun pauseOutgoingTab() {
        cancelScriptDialog()
        webViews[tabs.activeTab.id]?.let { view ->
            view.evaluateJavascript(BrowserVideoAssistant.PAUSE, null)
            view.onPause()
        }
    }

    fun select(tabId: String) {
        if (tabs.tabs.none { it.id == tabId }) return
        if (tabRestricted(tabId)) { reportRestrictedDestination(); return }
        if (tabId != tabs.activeTab.id) { pauseOutgoingTab(); exitFullscreen() }
        tabs.select(tabId)
        activeWebViewOrNull()?.let { if (foreground) it.onResume() }
        changed()
    }

    /** Home/Private Apps switcher only. The ordinary tab switcher continues to call select. */
    fun selectFromNovaHome(tabId: String) {
        requireNovaForeground()
        if(tabs.tabs.none {it.id==tabId})return
        if (tabRestricted(tabId)) { reportRestrictedDestination(); return }
        if(tabId!=tabs.activeTab.id)prepareNovaSwitch()
        tabs.select(tabId)
        activeWebViewOrNull()?.let {if(foreground)it.onResume()}
        changed()
    }

    fun close(tabId: String) {
        if (scriptDialog?.tabId == tabId) cancelScriptDialog()
        cancelNavigationTimeout(tabId); pageFailures.remove(tabId)
        restrictedDocuments.remove(tabId)
        if (fullscreenOwner == tabId) exitFullscreen()
        webViews.remove(tabId)?.let(::destroy)
        tabs.close(tabId)
        privateAppTabs.remove(tabId)
        activeWebViewOrNull()?.let { if (foreground) it.onResume() }
        changed()
    }

    fun navigateActive(url: String) = navigate(tabs.activeTab.id, url)
    fun reloadActive() {
        if (activeDestinationRestricted()) { reportRestrictedDestination(); return }
        requireNetworkOrReport() ?: return
        val active = tabs.activeTab
        pageFailures.remove(active.id); changed()
        if (active.failure == BrowserTabFailure.RENDERER_GONE) recoverRenderer(active.id)
        else activeWebViewOrNull()?.reload()
    }
    fun stopActive() = activeWebViewOrNull()?.stopLoading()
    fun goBackActive() = travelHistory(-1)
    fun goForwardActive() = travelHistory(1)

    private fun travelHistory(offset: Int) {
        val view = activeWebViewOrNull() ?: return
        if (!view.canGoBackOrForward(offset)) return
        val history = view.copyBackForwardList()
        val item = history.getItemAtIndex(history.currentIndex + offset) ?: return
        if (destinationRestricted(item.url.orEmpty()) || destinationRestricted(item.originalUrl.orEmpty())) {
            reportRestrictedDestination()
            return
        }
        view.goBackOrForward(offset)
    }
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
                android.webkit.WebSettings.getDefaultUserAgent(appContext),
            )
            if (tab.url.isNotBlank()) {
                if (!tabRestricted(tabId) && requireNetworkOrReport() != null) view.reload()
            }
        }
        changed()
    }

    fun recoverRenderer(tabId: String) {
        if (tabRestricted(tabId)) { reportRestrictedDestination(); return }
        val url = tabs.tabs.first { it.id == tabId }.url
        webViews.remove(tabId)?.let(::destroy)
        tabs.detachWebView(tabId)
        activeWebViewOrNull()
        if (url.isNotBlank()) navigate(tabId, url)
        changed()
    }

    fun destroyAll() {
        cancelScriptDialog()
        navigationTimeouts.keys.toList().forEach(::cancelNavigationTimeout); pageFailures.clear()
        restrictedDocuments.clear()
        documentRevision++
        observedMedia.clear()
        playingVideoTabs.clear()
        mediaProbeResults.clear()
        mediaProbing.clear()
        exitFullscreen()
        webViews.values.toList().forEach(::destroy)
        webViews.clear()
        // A destroyed app must never reappear as an unclassified ordinary browser tab on lock.
        privateAppTabs.keys.toList().forEach {id ->if(tabs.tabs.any {it.id==id})tabs.close(id)}
        privateAppTabs.clear()
        tabs.tabs.forEach { tabs.detachWebView(it.id) }
        changed()
    }
    fun acceptanceReport(): String = buildString {
        appendLine("CURRENT SESSION")
        appendLine("Live diagnostics from this process. Counters describe the latest submitted navigation; events remain chronological until cleared.")
        appendLine(webViews[tabs.activeTab.id]?.let { runtimeProbes[it]?.interactionReport() }.orEmpty())
        append(diagnostics.report(mapOf("focus_mode" to focusMode.name.lowercase())))
        BrowserV2FatalCrashCapture.readLastReport(appContext)?.let {
            appendLine()
            appendLine("PREVIOUS PROCESS FATAL REPORT")
            appendLine("HISTORICAL EVIDENCE — not a crash in the current session. Retained separately when current-session events are cleared.")
            append(it)
        }
    }
    /** Structural UI markers contain only labels and numeric bounds, never page/private data. */
    fun recordAcceptanceUiEvent(category: String, details: Map<String, String> = emptyMap()) {
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) return
        diagnostics.record(category, details)
        recordCrashContext(category, details)
    }
    fun clearAcceptanceReport() { runtimeProbes.values.forEach { it.pause() }; diagnostics.clear(); diagnostics.record("CURRENT_SESSION_TRACE_CLEARED") }
    fun armAcceptanceInteraction(onReady: (Boolean) -> Unit) {
        val probe = webViews[tabs.activeTab.id]?.let { runtimeProbes[it] }
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS || probe == null) { onReady(false); return }
        probe.armNextInteraction(onReady)
    }
    fun captureAcceptanceRuntime(onComplete: () -> Unit = {}) {
        val probe = webViews[tabs.activeTab.id]?.let { runtimeProbes[it] }
        if (!BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS || probe == null) { onComplete(); return }
        probe.capture("OWNER_SNAPSHOT", onComplete)
    }
    fun metadataSnapshot(): BrowserSessionSnapshot {
        val ordinary=tabs.tabs.filterNot {privateAppTabs.containsKey(it.id)}
        val selected=tabs.activeTab.id.takeIf {id ->ordinary.any {it.id==id}} ?: ordinary.lastOrNull()?.id
        return BrowserSessionSnapshot(ordinary,selected)
    }
    fun restoreMetadata(snapshot: BrowserSessionSnapshot) {
        privateAppTabs.clear()
        tabs.restore(snapshot.tabs, snapshot.selectedTabId)
        coldRestoreIds.clear()
        coldRestoreIds += tabs.tabs.filter { it.url.isNotBlank() }.map { it.id }
        changed()
    }

    fun clearWebData() {
        cancelScriptDialog()
        webViews.values.forEach { view ->
            view.stopLoading()
            view.clearHistory()
            view.clearCache(true)
            view.clearFormData()
        }
    }

    private fun navigate(tabId: String, url: String) {
        if (destinationRestricted(url) || privateAppTabs[tabId]?.let(::appRestricted) == true) {
            reportRestrictedDestination()
            return
        }
        recordAcceptanceUiEvent("NAVIGATION_SUBMITTED")
        webViews[tabId]?.let { presentationProbes[it]?.recordState("NAVIGATION_SUBMITTED") }
        coldRestoreIds.remove(tabId)
        if (!BrowserSecurityPolicy.allowsNavigation(url)) {
            listener.onMessage(BrowserMessage.UnsupportedScheme)
            return
        }
        if (requireNetworkOrReport() == null) return
        pageFailures.remove(tabId)
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
        if (tabRestricted(tabId) || !BrowserSecurityPolicy.allowsNavigation(url)) return
        if (requireNetworkOrReport() == null) return
        coldRestoreIds.remove(tabId)
        diagnostics.startNavigation(mapOf("main_frame" to "true", "restore" to "true"), preserveEvents = true)
        recordAcceptanceUiEvent("NAVIGATION_SUBMITTED", mapOf("restore" to "true"))
        presentationProbes[view]?.recordState("NAVIGATION_SUBMITTED")
        view.loadUrl(url)
    }

    private fun destroy(view: WebView) {
        restrictedViews.remove(view)
        pendingWindowFocus.entries.removeAll {it.value===view}
        webViews.entries.firstOrNull { it.value === view }?.key?.let(::cancelNavigationTimeout)
        runtimeProbes.remove(view)?.dispose()
        view.setOnTouchListener(null)
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

    override fun interceptRestrictedDestination(url: String): Boolean = destinationRestricted(url)

    override fun onNavigationRequest(tabId: String, url: String, mainFrame: Boolean, allowed: Boolean): Boolean {
        if (destinationRestricted(url) || privateAppTabs[tabId]?.let(::appRestricted) == true) {
            if (mainFrame) reportRestrictedDestination()
            return true
        }
        if (!allowed) {
            diagnostics.record("EXTERNAL_NAVIGATION_REQUEST", mapOf("main_frame" to mainFrame.toString(), "candidate" to BrowserExternalNavigationPolicy.isCandidate(url).toString()))
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
        if (loading && scriptDialog?.tabId == tabId) cancelScriptDialog()
        if (tabs.tabs.none { it.id == tabId }) return
        if (destinationRestricted(url) || privateAppTabs[tabId]?.let(::appRestricted) == true) {
            val alreadyReported = restrictedDocuments.put(tabId, url) == url
            cancelNavigationTimeout(tabId)
            webViews[tabId]?.let { setViewRestricted(tabId, it, true) }
            if (!alreadyReported) reportRestrictedDestination()
            listener.onSessionChanged()
            return
        }
        if (!loading) restrictedDocuments.remove(tabId)
        if (loading) watchNavigation(tabId) else cancelNavigationTimeout(tabId)
        if (loading) { documentRevision++; observedMedia.remove(tabId); playingVideoTabs.remove(tabId); mediaProbeResults.clear(); mediaProbing.clear(); if (fullscreenOwner == tabId) exitFullscreen() }
        if (loading) webViews[tabId]?.let { runtimeProbes[it]?.newDocument() }
        tabs.updateNavigation(tabId, url, title, loading, canGoBack, canGoForward)
        if (!loading) webViews[tabId]?.let { setViewRestricted(tabId, it, tabRestricted(tabId)) }
        diagnostics.record(if (loading) "MAIN_PAGE_STARTED" else "MAIN_PAGE_FINISHED")
        recordPresentationTransition(tabId, if (loading) "PAGE_STARTED" else "PAGE_FINISHED")
        if (!loading && !privateAppTabs.containsKey(tabId) && BrowserSecurityPolicy.allowsNavigation(url)) listener.onHistoryVisit(title, url)
        changed()
    }
    override fun onPageCommitVisible(tabId: String) {
        cancelNavigationTimeout(tabId)
        diagnostics.record("MAIN_PAGE_COMMIT_VISIBLE")
        recordPresentationTransition(tabId, "PAGE_COMMIT_VISIBLE")
    }

    private fun recordPresentationTransition(tabId: String, event: String) {
        recordAcceptanceUiEvent(event)
        webViews[tabId]?.let {
            presentationProbes[it]?.recordState(event)
            if (event != "PAGE_STARTED") runtimeProbes[it]?.capture(event)
        }
    }
    override fun onTitle(tabId: String, title: String, canGoBack: Boolean, canGoForward: Boolean) {
        val tab = tabs.tabs.firstOrNull { it.id == tabId } ?: return
        tabs.updateNavigation(tabId, tab.url, title, tab.loading, canGoBack, canGoForward)
        changed()
    }
    override fun onPageError(tabId: String, mainFrame: Boolean, errorCode: Int) { webViews[tabId]?.let { runtimeProbes[it]?.failure("RESOURCE", errorCode, mainFrame) }; diagnostics.record(if (mainFrame) "MAIN_PAGE_ERROR" else "RESOURCE_ERROR", mapOf("code" to errorCode.toString(), "category" to BrowserResourceError.category(errorCode), "main_frame" to mainFrame.toString()), true); if (mainFrame) pageFailed(tabId, "Page load failed: ${BrowserResourceError.category(errorCode)} ($errorCode). Check VPN details and retry.") }
    override fun onHttpError(tabId: String, mainFrame: Boolean, statusCode: Int) { webViews[tabId]?.let { runtimeProbes[it]?.failure("HTTP", statusCode, mainFrame) }; diagnostics.record(if (mainFrame) "MAIN_HTTP_ERROR" else "RESOURCE_HTTP_ERROR", mapOf("status" to statusCode.toString()), true); if (mainFrame) pageFailed(tabId, "The website returned HTTP $statusCode. Retry or open another website.") }
    override fun onTlsRejected(tabId: String) { webViews[tabId]?.let { runtimeProbes[it]?.failure("TLS", 0, true) }; diagnostics.record("SSL_ERROR", mapOf("decision" to "cancel"), true); pageFailed(tabId, "TLS certificate verification failed. The connection was blocked; no certificate exception was accepted.") }
    override fun onRendererGone(tabId: String) {
        if (scriptDialog?.tabId == tabId) cancelScriptDialog()
        if (tabs.tabs.none { it.id == tabId }) return
        if (fullscreenOwner == tabId) exitFullscreen()
        webViews.remove(tabId)?.let(::destroy)
        tabs.rendererGone(tabId)
        listener.onMessage(BrowserMessage.RendererGone)
        changed()
    }
    override fun onProgress(tabId: String, progress: Int) = Unit
    override fun onCreateWindow(parentTabId: String, isDialog: Boolean, isUserGesture: Boolean): WebView? {
        // A retained, paused, covered or destroyed page cannot allocate a renderer or take focus.
        if (!windowInteractionAllowed(parentTabId) || tabRestricted(parentTabId)) return null
        val previousId = tabs.activeTab.id
        val parentApp=privateAppTabs[parentTabId]
        val child = if(parentApp==null)newTab() else {
            // Native popup policy/lifetime is unchanged; presentation and history keep the owner.
            pauseOutgoingTab();exitFullscreen();createTab("",parentApp)
        }
        val view = webViewOrNull(child.id)
        if (view == null) {
            close(child.id)
            if (tabs.tabs.any { it.id == previousId }) select(previousId)
        }
        return view
    }
    override fun onStructuralEvent(tabId: String, event: String, details: Map<String, String>) {
        diagnostics.record(event, details + ("active_tab" to (tabId == tabs.activeTab.id).toString()))
    }
    override fun onWindowFocus(tabId: String) {
        if (!windowInteractionAllowed(tabId)) return
        val view=webViews[tabId]?:return
        pendingWindowFocus[tabId]=view
        select(tabId)
        if(view.isAttachedToWindow && view.requestFocus())pendingWindowFocus.remove(tabId)
    }
    override fun onPermissionRequestCanceled(tabId: String, request: android.webkit.PermissionRequest) = listener.onPermissionRequestCanceled(request)
    private fun windowInteractionAllowed(tabId:String):Boolean =
        mediaNetworkingAllowed() && rendererAllowed() && tabId==tabs.activeTab.id && webViews[tabId]!=null &&
            runCatching {listener.allowWindowInteraction()}.getOrDefault(false)
    override fun onCloseWindow(tabId: String) { if (windowInteractionAllowed(tabId)) close(tabId) }
    override fun onShowCustomView(tabId: String, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (tabId != tabs.activeTab.id || !mediaNetworkingAllowed() || fullscreenCallback != null) {
            callback.onCustomViewHidden(); return
        }
        fullscreenOwner = tabId
        fullscreenCallback = callback
        recordMediaPath("WEBVIEW_FULLSCREEN")
        listener.onFullscreen(view, WebChromeClient.CustomViewCallback { exitFullscreen() })
    }
    override fun onHideCustomView(tabId: String) { if (fullscreenOwner == tabId) exitFullscreen() }
    override fun onPermissionRequest(tabId:String,request:android.webkit.PermissionRequest) {
        if(tabId!=tabs.activeTab.id || webViews[tabId]==null || !mediaNetworkingAllowed()){request.deny();return}
        listener.onPermissionRequest(request)
    }
    override fun onGeolocationRequest(tabId:String,origin:String,callback:android.webkit.GeolocationPermissions.Callback) {
        if(tabId!=tabs.activeTab.id || webViews[tabId]==null || !mediaNetworkingAllowed()){callback.invoke(origin,false,false);return}
        listener.onGeolocationRequest(origin,callback)
    }
    override fun onShowFileChooser(tabId:String,callback:android.webkit.ValueCallback<Array<android.net.Uri>>,params:WebChromeClient.FileChooserParams):Boolean {
        if(tabId!=tabs.activeTab.id || webViews[tabId]==null || !mediaNetworkingAllowed()){callback.onReceiveValue(null);return true}
        return listener.onShowFileChooser(callback,params)
    }
    override fun onDownload(tabId: String, url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        if(tabId!=tabs.activeTab.id || !webViews.containsKey(tabId) || !foreground)return
        if (mediaNetworkingAllowed()) listener.onDownload(url, userAgent, contentDisposition, mimeType) else listener.onMessage(BrowserMessage.VpnRequired)
    }
    override fun onImageLongPress(tabId: String, resourceUrl: String?) = listener.onImageLongPress(resourceUrl)
    override fun onResourceObserved(tabId: String) = diagnostics.record("RESOURCE_REQUEST")
    override fun onMediaRequestObserved(tabId: String, url: String, headers: Map<String, String>) {
        observedMedia.computeIfAbsent(tabId) { ObservedMediaRequests() }.observe(url, headers, tabId in playingVideoTabs)
    }
    override fun onVideoSaveRequested(tabId: String) {
        if (tabId == tabs.activeTab.id && mediaNetworkingAllowed()) listener.onVideoSaveRequested()
    }
    // Page console text can contain private data even without URLs: retain severity/counts only.
    override fun onConsole(tabId: String, level: String, message: String?, line: Int) {
        if (message == "PG_VIDEO_PLAYING") { playingVideoTabs.add(tabId); return }
        if (message == "PG_VIDEO_STOPPED") { playingVideoTabs.remove(tabId); return }
        diagnostics.recordConsole(level, null, line)
    }
}

fun interface BrowserV2WebViewFactory {
    fun create(context: Context, callbacks: BrowserWebViewCallbacks, tabId: String, desktopSite: Boolean): WebView
}

enum class BrowserFocusMode { CURRENT, EXPLICIT_WEBVIEW_FOCUS }

sealed interface BrowserMessage {
    data object VpnRequired : BrowserMessage
    data object SanctuaryRestricted : BrowserMessage
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
    override fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) = callback.onCustomViewHidden()
    override fun onExitFullscreen() = Unit
    override fun onPermissionRequest(request: android.webkit.PermissionRequest) = request.deny()
    override fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback) = callback.invoke(origin, false, false)
    override fun onShowFileChooser(callback: android.webkit.ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean { callback.onReceiveValue(null); return true }
}
