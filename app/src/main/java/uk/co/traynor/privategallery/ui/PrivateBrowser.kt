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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import uk.co.traynor.privategallery.core.browser.BrowserAddressPolicy
import uk.co.traynor.privategallery.core.browser.BrowserDownloadAction
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy
import uk.co.traynor.privategallery.core.browser.BrowserNetworkGatePolicy
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserScreenState
import uk.co.traynor.privategallery.core.browser.BrowserToolbarAction
import uk.co.traynor.privategallery.core.browser.BrowserToolbarPolicy
import uk.co.traynor.privategallery.core.browser.BrowserWebSecurityPolicy
import uk.co.traynor.privategallery.core.browser.BrowserViewportPolicy
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class BrowserCallbacks {
    var onPageStarted: (String) -> Unit = {}
    var onPageFinished: (String) -> Unit = {}
    var onProgress: (Int) -> Unit = {}
    var onTitle: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onUnsupportedLink: () -> Unit = {}
    var onDownload: (url: String, contentDisposition: String, mimeType: String) -> Unit = { _, _, _ -> }
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
    onClearBrowsingData: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onSaveToVault: (VaultImportSource) -> Unit = {},
    requireVpnForBrowsing: Boolean = false,
    vpnConnected: Boolean = true,
    modifier: Modifier = Modifier,
    webViewFactory: BrowserWebViewFactory = BrowserWebViewFactory(::secureBrowserWebView),
) {
    var address by remember { mutableStateOf(TextFieldValue(existingWebView?.url.orEmpty())) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var initializedWebView by remember(existingWebView) { mutableStateOf(existingWebView) }
    var initializationFailed by remember(existingWebView) { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var addressFocused by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<VaultImportSource?>(null) }
    val latestWebViewReady by rememberUpdatedState(onWebViewReady)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // The address field owns Back while it is focused. This lets Android dismiss the IME before
    // the Activity's browser-history policy is reached.
    BackHandler(enabled = addressFocused) { focusManager.clearFocus(force = true) }
    val networkAllowed = BrowserNetworkGatePolicy.mayStartNetworkRequest(requireVpnForBrowsing, vpnConnected)
    val callbacks = remember(networkAllowed) {
        BrowserCallbacks().also { callbacks ->
            callbacks.onPageStarted = { url -> address = TextFieldValue(url); loading = true; message = null }
            callbacks.onPageFinished = { url -> address = TextFieldValue(url); loading = false; progress = 100 }
            callbacks.onProgress = { value -> progress = value }
            callbacks.onTitle = { value -> title = value }
            callbacks.onError = { value -> loading = false; message = value }
            callbacks.onUnsupportedLink = { message = "This link type is not supported in Private Gallery." }
            callbacks.onDownload = { url, contentDisposition, mimeType ->
                if (!networkAllowed) {
                    message = "VPN is not connected. Download was not started."
                } else {
                    val filename = contentDisposition.substringAfter("filename=", "download").trim().trim('"').ifBlank { "download" }
                    pendingDownload = VaultImportSource(filename, mimeType.ifBlank { "application/octet-stream" }, {
                        (URL(url).openConnection() as HttpURLConnection).apply { instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 30_000 }.inputStream
                    }, sourceReference = null)
                }
            }
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
    LaunchedEffect(customView) {
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
        if (!networkAllowed) {
            message = "VPN is not connected. Browser networking is paused."
            return
        }
        val view = webViewRef.value ?: ensureWebView()
        if (view == null) {
            if (!initializationFailed) message = "Browser is still starting. Try again in a moment."
            return
        }
        runCatching { BrowserAddressPolicy.destinationFor(address.text, searchEngine) }
            .onSuccess {
                Log.d(BROWSER_LOG_TAG, "Starting HTTP(S) navigation")
                view.loadUrl(it.url)
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
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
                    .padding(horizontal = GalleryTokens.PageHorizontal, vertical = 6.dp)
                    .semantics { testTag = "browser-chrome" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    GallerySectionLabel("Private Gallery")
                    Text("Browser", style = MaterialTheme.typography.titleLarge)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().semantics { testTag = "browser-controls" },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = webViewRef.value?.canGoBack() == true,
                    onClick = { webViewRef.value?.takeIf { it.canGoBack() }?.goBack() },
                    modifier = Modifier.semantics { testTag = "browser-back" },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                IconButton(
                    enabled = webViewRef.value?.canGoForward() == true,
                    onClick = { webViewRef.value?.takeIf { it.canGoForward() }?.goForward() },
                    modifier = Modifier.semantics { testTag = "browser-forward" },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward") }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    TextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            // TextField is measured for Material's 56dp minimum. Forcing it to
                            // 48dp clips URL glyphs on real devices (for example bbc.co.uk).
                            .height(BrowserToolbarPolicy.addressFieldHeightDp.dp)
                            .onFocusChanged { state ->
                                if (state.isFocused && !addressFocused && address.text.isNotEmpty()) {
                                    address = address.copy(selection = TextRange(0, address.text.length))
                                }
                                addressFocused = state.isFocused
                            }
                            .semantics { testTag = "browser-address" },
                        singleLine = true,
                        placeholder = { Text("Search or enter address") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (address.text.startsWith("https://")) Icons.Filled.Lock else Icons.Filled.Language,
                                contentDescription = if (address.text.startsWith("https://")) "HTTPS connection" else "Address or search",
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submitAddress() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                IconButton(
                    enabled = webViewRef.value != null && networkAllowed,
                    onClick = {
                        when (BrowserToolbarPolicy.primaryAction(loading)) {
                            BrowserToolbarAction.STOP -> webViewRef.value?.stopLoading()
                            BrowserToolbarAction.RELOAD -> webViewRef.value?.reload()
                        }
                    },
                    modifier = Modifier.semantics { testTag = "browser-reload" },
                ) {
                    Icon(
                        imageVector = if (BrowserToolbarPolicy.primaryAction(loading) == BrowserToolbarAction.STOP) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = if (BrowserToolbarPolicy.primaryAction(loading) == BrowserToolbarAction.STOP) "Stop loading" else "Reload",
                    )
                }
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.semantics { testTag = "browser-overflow" },
                    ) { Icon(Icons.Filled.MoreVert, contentDescription = "More browser options") }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        if (title.isNotBlank()) DropdownMenuItem(text = { Text(title, maxLines = 1) }, onClick = {})
                        DropdownMenuItem(
                            text = { Text("Screenshot to Vault") },
                            enabled = webViewRef.value != null,
                            onClick = {
                                overflowExpanded = false
                                webViewRef.value?.let { page ->
                                    val bitmap = Bitmap.createBitmap(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                    android.graphics.Canvas(bitmap).also(page::draw)
                                    val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
                                    bitmap.recycle()
                                    onSaveToVault(VaultImportSource(
                                        displayName = "browser-screenshot-${System.currentTimeMillis()}.png",
                                        mimeType = "image/png",
                                        openStream = { ByteArrayInputStream(bytes) },
                                        onConsumed = { bytes.fill(0) },
                                    ))
                                    message = "Screenshot is being saved to Vault."
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear browsing data") },
                            onClick = { overflowExpanded = false; onClearBrowsingData() },
                        )
                        DropdownMenuItem(
                            text = { Text("Browser settings") },
                            onClick = { overflowExpanded = false; onOpenBrowserSettings() },
                        )
                    }
                }
            }
            // Reserve two pixels so page content never shifts as the progress bar starts/stops.
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxSize())
            }
            }
            // The WebView is constrained to this page-only region. Browser chrome is a sibling
            // above it, never an overlay underneath an unrestricted AndroidView. In particular,
            // this region intentionally has no Gallery horizontal padding.
            Box(modifier = Modifier.fillMaxWidth().weight(1f).semantics { testTag = "browser-page-region" }) {
                val screenState = if (initializationFailed) BrowserScreenState.initializationFailed() else BrowserScreenState.initial()
                when {
                    screenState.showError -> BrowserStartSurface(
                        title = "Browser unavailable",
                        detail = message ?: "Browser could not start. Try reopening Private Gallery.",
                        onRetry = ::retryWebView,
                    )
                    message != null -> BrowserStartSurface(
                        title = "Couldn't load page",
                        detail = message.orEmpty(),
                        onRetry = if (webViewRef.value != null) ({ webViewRef.value?.reload() }) else null,
                    )
                    initializedWebView != null -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { initializedWebView!! },
                        update = { view ->
                            webViewRef.value = view
                            latestWebViewReady(view)
                        },
                    )
                    else -> BrowserStartSurface(
                        title = "Private browsing session",
                        detail = "Search or enter an address. Browser downloads are not saved to your Vault.",
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
        pendingDownload?.let { source ->
            AlertDialog(
                onDismissRequest = { pendingDownload = null },
                title = { Text("Save to Vault?") },
                text = { Text("This download will be encrypted directly into your Vault. It will not be saved to public Downloads.") },
                confirmButton = { TextButton(onClick = { pendingDownload = null; onSaveToVault(source); message = "Download is being saved to Vault." }) { Text("Save to Vault") } },
                dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text("Cancel") } },
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
        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (BrowserNavigationPolicy.downloadAction() == BrowserDownloadAction.REQUEST_VAULT_SAVE) callbacks.onDownload(url, contentDisposition.orEmpty(), mimeType.orEmpty())
        })
    }

private const val BROWSER_LOG_TAG = "PrivateGalleryBrowser"
