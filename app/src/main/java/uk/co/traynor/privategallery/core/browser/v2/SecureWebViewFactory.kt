package uk.co.traynor.privategallery.core.browser.v2

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebView.HitTestResult
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions

/**
 * The only constructor for V2 tab WebViews. It deliberately has no request interception and no
 * Javascript bridge: normal page resources stay inside WebView's network stack and every tab has
 * the same explicit policy.
 */
class SecureWebViewFactory(
    private val callbacks: BrowserWebViewCallbacks,
    private val configuration: SecureWebViewConfiguration = BrowserSecurityPolicy.defaultConfiguration(),
) {
    fun create(context: android.content.Context, tabId: String, desktopSite: Boolean): WebView {
        var live = true
        return object : WebView(context) {
            override fun destroy() { live = false; super.destroy() }
        }.apply webView@{
        isFocusable = true
        isFocusableInTouchMode = true
        settings.apply {
            javaScriptEnabled = configuration.javaScript
            domStorageEnabled = configuration.domStorage
            // IndexedDB and service workers use the WebView profile; there is no app-created
            // database or bridge involved here.
            databaseEnabled = false
            allowFileAccess = configuration.fileAccess
            allowContentAccess = configuration.contentAccess
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = if (configuration.mixedContent) WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE else WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = configuration.responsiveViewport
            loadWithOverviewMode = false
            javaScriptCanOpenWindowsAutomatically = configuration.multipleWindows
            setSupportMultipleWindows(configuration.multipleWindows)
            mediaPlaybackRequiresUserGesture = true
            userAgentString = BrowserSecurityPolicy.userAgent(if (desktopSite) BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE, WebSettings.getDefaultUserAgent(context))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(configuration.firstPartyCookies)
            setAcceptThirdPartyCookies(this@webView, configuration.thirdPartyCookies)
        }
        val documentStartAssistant = androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStartAssistant) androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, BrowserVideoAssistant.script(context), setOf("*"))
        fun event(name: String, details: Map<String, String> = emptyMap()) = callbacks.onStructuralEvent(tabId, name, details)
        val permissions = java.util.IdentityHashMap<android.webkit.PermissionRequest, BrowserPermissionRequest>()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val allowed = BrowserSecurityPolicy.allowsNavigation(url)
                return callbacks.onNavigationRequest(tabId, url, request.isForMainFrame, allowed)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!live) return
                callbacks.onPageState(tabId, url, view.title.orEmpty(), true, view.canGoBack(), view.canGoForward())
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!live) return
                callbacks.onPageState(tabId, url, view.title.orEmpty(), false, view.canGoBack(), view.canGoForward())
                if (live && !documentStartAssistant) view.evaluateJavascript(BrowserVideoAssistant.script(context), null)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                callbacks.onPageCommitVisible(tabId)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                callbacks.onPageError(tabId, request.isForMainFrame, error.errorCode)
            }

            override fun onLoadResource(view: WebView, url: String) {
                callbacks.onResourceObserved(tabId)
                super.onLoadResource(view, url)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                callbacks.onHttpError(tabId, request.isForMainFrame, errorResponse.statusCode)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                callbacks.onTlsRejected(tabId)
            }

            override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, response: android.webkit.SafeBrowsingResponse) {
                event("SAFE_BROWSING_EVENT", mapOf("threat_type" to threatType.toString(), "main_frame" to request.isForMainFrame.toString()))
                super.onSafeBrowsingHit(view, request, threatType, response)
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                if (!live) return true
                event("RENDER_PROCESS_GONE", mapOf("crashed" to detail.didCrash().toString()))
                callbacks.onRendererGone(tabId)
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!live) return
                callbacks.onTitle(tabId, title.orEmpty(), view.canGoBack(), view.canGoForward())
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) = callbacks.onProgress(tabId, newProgress)

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!live) return false
                // onCreateWindow is the native observable request; do not monkey-patch window.open.
                event("WINDOW_OPEN_REQUEST", mapOf("source" to "native_create_window"))
                event("WINDOW_CREATE_REQUEST", mapOf("dialog" to isDialog.toString(), "user_gesture" to isUserGesture.toString()))
                val transport = resultMsg.obj as? WebView.WebViewTransport
                if (transport == null || resultMsg.target == null) {
                    event("WINDOW_CREATE_RESULT", mapOf("accepted" to "false", "reason" to "invalid_transport"))
                    return false
                }
                val child = callbacks.onCreateWindow(tabId, isDialog, isUserGesture)
                if (child == null) {
                    event("WINDOW_CREATE_RESULT", mapOf("accepted" to "false", "reason" to "unavailable"))
                    return false
                }
                transport.webView = child
                resultMsg.sendToTarget()
                event("WINDOW_CREATE_RESULT", mapOf("accepted" to "true"))
                return true
            }

            override fun onRequestFocus(view: WebView) { event("WINDOW_FOCUS_REQUEST"); callbacks.onWindowFocus(tabId) }
            override fun onCloseWindow(window: WebView) { event("WINDOW_CLOSE_REQUEST"); callbacks.onCloseWindow(tabId) }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) { if (!live) { callback.onCustomViewHidden(); return }; event("FULLSCREEN_REQUEST"); callbacks.onShowCustomView(tabId, view, callback) }
            override fun onHideCustomView() { if (!live) return; event("FULLSCREEN_EXIT"); callbacks.onHideCustomView(tabId) }
            override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                event("JS_DIALOG_ALERT"); return super.onJsAlert(view, url, message, result)
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                event("JS_DIALOG_CONFIRM"); return super.onJsConfirm(view, url, message, result)
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: android.webkit.JsPromptResult): Boolean {
                event("JS_DIALOG_PROMPT"); return super.onJsPrompt(view, url, message, defaultValue, result)
            }
            override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                event("JS_BEFORE_UNLOAD"); return super.onJsBeforeUnload(view, url, message, result)
            }
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                callbacks.onConsole(tabId, consoleMessage.messageLevel().name, consoleMessage.message(), consoleMessage.lineNumber())
                return super.onConsoleMessage(consoleMessage)
            }
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                event("PERMISSION_REQUEST", mapOf("resource_count" to request.resources.size.toString()))
                val wrapped = BrowserPermissionRequest(request) { decision, count ->
                    permissions.remove(request)
                    event("PERMISSION_RESULT", mapOf("decision" to decision, "granted_count" to count.toString()))
                }
                permissions[request] = wrapped
                callbacks.onPermissionRequest(tabId, wrapped)
            }
            override fun onPermissionRequestCanceled(request: android.webkit.PermissionRequest) {
                permissions.remove(request)?.let { it.canceled(); callbacks.onPermissionRequestCanceled(tabId, it) }
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                event("PERMISSION_REQUEST", mapOf("kind" to "geolocation"))
                callbacks.onGeolocationRequest(tabId, origin) { value, allow, retain ->
                    event("PERMISSION_RESULT", mapOf("kind" to "geolocation", "granted" to allow.toString()))
                    callback.invoke(value, allow, retain)
                }
            }
            override fun onShowFileChooser(webView: WebView, filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean {
                event("FILE_CHOOSER_REQUEST", mapOf("mode" to fileChooserParams.mode.toString()))
                var completed = false
                val accepted = callbacks.onShowFileChooser(tabId, { values ->
                    if (!completed) {
                        completed = true
                        event("FILE_CHOOSER_RESULT", mapOf("selected_count" to (values?.size ?: 0).toString()))
                        filePathCallback.onReceiveValue(values)
                    }
                }, fileChooserParams)
                if (!accepted && !completed) event("FILE_CHOOSER_RESULT", mapOf("decision" to "unhandled"))
                return accepted
            }
        }
        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            event("DOWNLOAD_REQUEST")
            callbacks.onDownload(tabId, url, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType.orEmpty())
        })
        setOnLongClickListener {
            when (hitTestResult.type) {
                HitTestResult.IMAGE_TYPE, HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    callbacks.onImageLongPress(tabId, hitTestResult.extra)
                    true
                }
                else -> false
            }
        }
    }
    }
}

/** Android callbacks are intentionally narrow; persistence, VPN and acquisition stay outside WebView. */
interface BrowserWebViewCallbacks {
    /** @return true when the navigation must be cancelled. */
    fun onNavigationRequest(tabId: String, url: String, mainFrame: Boolean, allowed: Boolean): Boolean
    fun onPageState(tabId: String, url: String, title: String, loading: Boolean, canGoBack: Boolean, canGoForward: Boolean)
    fun onPageCommitVisible(tabId: String) = Unit
    fun onStructuralEvent(tabId: String, event: String, details: Map<String, String>) = Unit
    fun onWindowFocus(tabId: String) = Unit
    fun onPermissionRequestCanceled(tabId: String, request: android.webkit.PermissionRequest) = Unit
    fun onTitle(tabId: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onPageError(tabId: String, mainFrame: Boolean, errorCode: Int)
    fun onHttpError(tabId: String, mainFrame: Boolean, statusCode: Int)
    fun onTlsRejected(tabId: String)
    fun onRendererGone(tabId: String)
    fun onProgress(tabId: String, progress: Int)
    fun onCreateWindow(parentTabId: String, isDialog: Boolean, isUserGesture: Boolean): WebView?
    fun onCloseWindow(tabId: String)
    fun onShowCustomView(tabId: String, view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideCustomView(tabId: String)
    fun onPermissionRequest(tabId: String, request: android.webkit.PermissionRequest)
    fun onShowFileChooser(tabId: String, callback: android.webkit.ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun onDownload(tabId: String, url: String, userAgent: String, contentDisposition: String, mimeType: String)
    fun onImageLongPress(tabId: String, resourceUrl: String?)
    fun onResourceObserved(tabId: String)
    fun onConsole(tabId: String, level: String, message: String?, line: Int)
    fun onGeolocationRequest(tabId: String, origin: String, callback: android.webkit.GeolocationPermissions.Callback)
}
