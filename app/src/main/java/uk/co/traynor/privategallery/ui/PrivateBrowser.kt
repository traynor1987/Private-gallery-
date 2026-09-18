package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import uk.co.traynor.privategallery.core.browser.BrowserAddressPolicy
import uk.co.traynor.privategallery.core.browser.BrowserDownloadAction
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserWebSecurityPolicy

private class BrowserCallbacks {
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
 * Browser V1 never adds a JavascriptInterface and deliberately permits only http(s) navigation.
 * Future Vault downloads must enter through a separate authenticated import coordinator.
 */
@Composable
fun BrowserHome(
    existingWebView: WebView?,
    searchEngine: BrowserSearchEngine,
    onWebViewReady: (WebView) -> Unit,
    onFullscreenExitChanged: ((() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var address by remember { mutableStateOf(existingWebView?.url.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
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
    val webViewRef = remember { mutableStateOf<WebView?>(existingWebView) }
    fun submitAddress() {
        val view = webViewRef.value ?: return
        runCatching { BrowserAddressPolicy.destinationFor(address, searchEngine) }
            .onSuccess { view.loadUrl(it.url) }
            .onFailure { message = "Enter a web address or search." }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = GalleryTokens.PageHorizontal, vertical = 8.dp),
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Address or search") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submitAddress() }),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { webViewRef.value?.takeIf { it.canGoBack() }?.goBack() }) { Text("←") }
                TextButton(onClick = { webViewRef.value?.takeIf { it.canGoForward() }?.goForward() }) { Text("→") }
                TextButton(onClick = { if (loading) webViewRef.value?.stopLoading() else webViewRef.value?.reload() }) { Text(if (loading) "Stop" else "Refresh") }
                Button(onClick = ::submitAddress) { Text("Go") }
            }
            if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = {
                    val view = existingWebView ?: secureBrowserWebView(context, callbacks)
                    webViewRef.value = view
                    latestWebViewReady(view)
                    view
                },
                update = { view ->
                    webViewRef.value = view
                    latestWebViewReady(view)
                },
            )
        }
        customView?.let { view ->
            AndroidView(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
                factory = { view },
            )
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, BrowserWebSecurityPolicy.thirdPartyCookiesEnabled)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return if (BrowserNavigationPolicy.isWebUrl(request.url.toString())) false else {
                    callbacks.onUnsupportedLink()
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = callbacks.onPageStarted(url)
            override fun onPageFinished(view: WebView, url: String) = callbacks.onPageFinished(url)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) callbacks.onError("Page load failed. Check your connection and try again.")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) callbacks.onError("Page load failed. The website returned an error.")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                // Never offer an application-level certificate bypass.
                handler.cancel()
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
