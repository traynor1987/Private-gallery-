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
    fun create(context: android.content.Context, tabId: String, desktopSite: Boolean): WebView = WebView(context).apply webView@{
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
            userAgentString = BrowserSecurityPolicy.userAgent(if (desktopSite) BrowserUserAgentMode.DESKTOP else BrowserUserAgentMode.MOBILE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(configuration.firstPartyCookies)
            setAcceptThirdPartyCookies(this@webView, configuration.thirdPartyCookies)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val allowed = BrowserSecurityPolicy.allowsNavigation(url)
                return callbacks.onNavigationRequest(tabId, url, request.isForMainFrame, allowed)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                callbacks.onPageState(tabId, url, view.title.orEmpty(), true, view.canGoBack(), view.canGoForward())
            }

            override fun onPageFinished(view: WebView, url: String) {
                callbacks.onPageState(tabId, url, view.title.orEmpty(), false, view.canGoBack(), view.canGoForward())
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

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                callbacks.onRendererGone(tabId)
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                callbacks.onTitle(tabId, title.orEmpty(), view.canGoBack(), view.canGoForward())
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) = callbacks.onProgress(tabId, newProgress)

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val child = callbacks.onCreateWindow(tabId, isDialog, isUserGesture) ?: return false
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) = callbacks.onCloseWindow(tabId)
            override fun onShowCustomView(view: View, callback: CustomViewCallback) = callbacks.onShowCustomView(tabId, view, callback)
            override fun onHideCustomView() = callbacks.onHideCustomView(tabId)
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                callbacks.onConsole(tabId, consoleMessage.messageLevel().name, consoleMessage.message(), consoleMessage.lineNumber())
                return super.onConsoleMessage(consoleMessage)
            }
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) = callbacks.onPermissionRequest(tabId, request)
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) = callbacks.onGeolocationRequest(tabId, origin, callback)
            override fun onShowFileChooser(webView: WebView, filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean =
                callbacks.onShowFileChooser(tabId, filePathCallback, fileChooserParams)
        }
        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
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

/** Android callbacks are intentionally narrow; persistence, VPN and acquisition stay outside WebView. */
interface BrowserWebViewCallbacks {
    /** @return true when the navigation must be cancelled. */
    fun onNavigationRequest(tabId: String, url: String, mainFrame: Boolean, allowed: Boolean): Boolean
    fun onPageState(tabId: String, url: String, title: String, loading: Boolean, canGoBack: Boolean, canGoForward: Boolean)
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
