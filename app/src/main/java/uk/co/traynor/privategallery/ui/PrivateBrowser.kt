package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.DownloadListener
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import uk.co.traynor.privategallery.core.browser.BrowserAddressPolicy
import uk.co.traynor.privategallery.core.browser.BrowserDownloadAction
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserScreenState
import uk.co.traynor.privategallery.core.browser.BrowserWebSecurityPolicy
import uk.co.traynor.privategallery.core.browser.BrowserViewportPolicy

internal class BrowserCallbacks {
    var onPageStarted: (String) -> Unit = {}
    var onPageFinished: (String) -> Unit = {}
    var onProgress: (Int) -> Unit = {}
    var onTitle: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onUnsupportedLink: () -> Unit = {}
    var onDownload: () -> Unit = {}
    var onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> }
    var onHideCustomView: () -> Unit = {}
}

/**
 * Kept injectable so an instrumentation test can exercise the real BrowserHome composition
 * without depending on a device WebView provider. Production always uses [secureBrowserWebView].
 */
internal fun interface BrowserWebViewFactory {
    fun create(context: android.content.Context, callbacks: BrowserCallbacks): WebView
}

/**
 * Browser V1 never adds a JavascriptInterface and deliberately permits only http(s) navigation.
 * Future Vault downloads must enter through a separate authenticated import coordinator.
 */
@Composable
internal fun BrowserHome(
    existingWebView: WebView?,
    searchEngine: BrowserSearchEngine,
    onWebViewReady: (WebView) -> Unit,
    onFullscreenExitChanged: ((() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    webViewFactory: BrowserWebViewFactory = BrowserWebViewFactory(::secureBrowserWebView),
) {
    var address by remember { mutableStateOf(existingWebView?.url.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var initializedWebView by remember(existingWebView) { mutableStateOf(existingWebView) }
    var initializationFailed by remember(existingWebView) { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val latestWebViewReady by rememberUpdatedState(onWebViewReady)
    val callbacks = remember {
        BrowserCallbacks().also { callbacks ->
            callbacks.onPageStarted = { url -> address = url; loading = true; message = null }
            callbacks.onPageFinished = { url -> address = url; loading = false; progress = 100 }
            callbacks.onProgress = { value -> progress = value }
            callbacks.onTitle = { value -> title = value }
            callbacks.onError = { value -> loading = false; message = value }
            callbacks.onUnsupportedLink = { message = "This link type is not supported in Private Gallery." }
            callbacks.onDownload = { message = "Download detected. Private downloads are not supported yet." }
            callbacks.onShowCustomView = { view, callback ->
                customView = view
                customViewCallback = callback
            }
            callbacks.onHideCustomView = {
                customView = null
                customViewCallback = null
            }
        }
    }
    fun leaveFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }
    androidx.compose.runtime.LaunchedEffect(customView) {
        onFullscreenExitChanged(if (customView == null) null else ::leaveFullscreen)
    }
    val context = LocalContext.current
    val webViewRef = remember(existingWebView) { mutableStateOf(existingWebView) }
    // Do not construct Android WebView as this destination enters. The initial screen is
    // deliberately native Compose chrome + a start surface; WebView is attached only after the
    // user explicitly submits an address/search. This prevents WebView from owning the first
    // rendered frame on affected production devices.
    fun ensureWebView(): WebView? {
        initializedWebView?.let { return it }
        if (initializationFailed) return null
        return runCatching {
            Log.d(BROWSER_LOG_TAG, "Creating WebView after explicit navigation")
            webViewFactory.create(context, callbacks)
        }.onSuccess { view ->
            initializedWebView = view
            webViewRef.value = view
            latestWebViewReady(view)
            Log.d(BROWSER_LOG_TAG, "WebView created and configured")
        }.onFailure {
            initializationFailed = true
            message = "Browser could not start. Try reopening Private Gallery."
            Log.w(BROWSER_LOG_TAG, "WebView creation failed", it)
        }.getOrNull()
    }
    fun retryWebView() {
        initializationFailed = false
        message = null
        ensureWebView()
    }
    fun submitAddress() {
        val view = webViewRef.value ?: ensureWebView()
        if (view == null) {
            if (!initializationFailed) message = "Browser is still starting. Try again in a moment."
            return
        }
        runCatching { BrowserAddressPolicy.destinationFor(address, searchEngine) }
            .onSuccess {
                Log.d(BROWSER_LOG_TAG, "Starting HTTP(S) navigation")
                view.loadUrl(it.url)
            }
            .onFailure { message = "Enter a web address or search." }
    }

    Box(modifier = modifier.fillMaxSize().semantics { testTag = "browser-root" }) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Browser chrome follows the app spacing system. The WebView deliberately does not:
            // responsive websites must receive the whole available content width.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GalleryTokens.PageHorizontal, vertical = 8.dp)
                    .semantics { testTag = "browser-chrome" },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    GallerySectionLabel("Private Gallery")
                    Text("Browser", style = MaterialTheme.typography.headlineSmall)
                }
                if (title.isNotBlank()) Text(title, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 130.dp))
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth().semantics { testTag = "browser-address" },
                singleLine = true,
                label = { Text("Address or search") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submitAddress() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth().semantics { testTag = "browser-controls" },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { webViewRef.value?.takeIf { it.canGoBack() }?.goBack() }) { Text("←") }
                TextButton(onClick = { webViewRef.value?.takeIf { it.canGoForward() }?.goForward() }) { Text("→") }
                TextButton(onClick = { if (loading) webViewRef.value?.stopLoading() else webViewRef.value?.reload() }) { Text(if (loading) "Stop" else "Refresh") }
                Button(onClick = ::submitAddress) { Text("Go") }
            }
            if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            }
            // The WebView is constrained to this page-only region. Browser chrome is a sibling
            // above it, never an overlay underneath an unrestricted AndroidView. In particular,
            // this region intentionally has no Gallery horizontal padding.
            Box(modifier = Modifier.fillMaxWidth().weight(1f).semantics { testTag = "browser-page-region" }) {
                val screenState = if (initializationFailed) BrowserScreenState.initializationFailed() else BrowserScreenState.initial()
                when {
                    initializedWebView != null -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { initializedWebView!! },
                        update = { view ->
                            webViewRef.value = view
                            latestWebViewReady(view)
                        },
                    )
                    screenState.showError -> BrowserStartSurface(
                        title = "Browser unavailable",
                        detail = message ?: "Browser could not start. Try reopening Private Gallery.",
                        onRetry = ::retryWebView,
                    )
                    else -> BrowserStartSurface(
                        title = "Search or enter an address",
                        detail = "Web pages open here. Private Gallery does not save browser downloads to your Vault.",
                    )
                }
            }
        }
        customView?.let { view ->
            AndroidView(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
                factory = { view },
            )
        }
    }
}

@Composable
private fun BrowserStartSurface(title: String, detail: String, onRetry: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onRetry?.let { retry -> TextButton(onClick = retry) { Text("Retry") } }
        }
    }
}

private fun secureBrowserWebView(context: android.content.Context, callbacks: BrowserCallbacks): WebView =
    WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = BrowserWebSecurityPolicy.fileAccessEnabled
            allowContentAccess = BrowserWebSecurityPolicy.contentAccessEnabled
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(BrowserWebSecurityPolicy.multipleWindowsEnabled)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Respect mobile viewport meta tags (width=device-width), without the global
            // overview zoom that makes responsive media appear smaller/cropped on narrow views.
            useWideViewPort = BrowserViewportPolicy.useWideViewport
            loadWithOverviewMode = BrowserViewportPolicy.loadWithOverview
            textZoom = BrowserViewportPolicy.textZoomPercent
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        setInitialScale(BrowserViewportPolicy.initialScale)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, BrowserWebSecurityPolicy.thirdPartyCookiesEnabled)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return if (BrowserNavigationPolicy.isWebUrl(request.url.toString())) false else {
                    Log.d(BROWSER_LOG_TAG, "Blocked unsupported navigation scheme")
                    callbacks.onUnsupportedLink()
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = callbacks.onPageStarted(url)
            override fun onPageFinished(view: WebView, url: String) = callbacks.onPageFinished(url)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    Log.w(BROWSER_LOG_TAG, "Main-frame page load failed: ${error.errorCode}")
                    callbacks.onError("Page load failed. Check your connection and try again.")
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) callbacks.onError("Page load failed. The website returned an error.")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                // Never offer an application-level certificate bypass.
                handler.cancel()
                Log.w(BROWSER_LOG_TAG, "TLS error blocked")
                callbacks.onError("TLS certificate error. This page was not opened.")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = callbacks.onProgress(newProgress)
            override fun onReceivedTitle(view: WebView, title: String?) { callbacks.onTitle(title.orEmpty()) }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false
            override fun onShowCustomView(view: View, callback: CustomViewCallback) = callbacks.onShowCustomView(view, callback)
            override fun onHideCustomView() = callbacks.onHideCustomView()
        }
        setDownloadListener(DownloadListener { _, _, _, _, _ ->
            if (BrowserNavigationPolicy.downloadAction() == BrowserDownloadAction.SHOW_NOT_SUPPORTED) callbacks.onDownload()
        })
    }

private const val BROWSER_LOG_TAG = "PrivateGalleryBrowser"
