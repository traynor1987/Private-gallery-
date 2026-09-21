package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.DownloadListener
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebView.HitTestResult
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
import androidx.compose.ui.Alignment
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
import uk.co.traynor.privategallery.core.browser.BrowserDownloadPolicy
import uk.co.traynor.privategallery.core.browser.BrowserImageAcquisitionAction
import uk.co.traynor.privategallery.core.browser.BrowserImageHitType
import uk.co.traynor.privategallery.core.browser.BrowserImagePolicy
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy
import uk.co.traynor.privategallery.core.browser.BrowserNetworkGatePolicy
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserScreenState
import uk.co.traynor.privategallery.core.browser.BrowserToolbarAction
import uk.co.traynor.privategallery.core.browser.BrowserToolbarPolicy
import uk.co.traynor.privategallery.core.browser.BrowserWebSecurityPolicy
import uk.co.traynor.privategallery.core.browser.BrowserPopupPolicy
import uk.co.traynor.privategallery.core.browser.BrowserPopupAction
import uk.co.traynor.privategallery.core.browser.BrowserDiagnosticEvent
import uk.co.traynor.privategallery.core.browser.BrowserDiagnosticRecorder
import uk.co.traynor.privategallery.core.browser.BrowserDiagnosticsPolicy
import uk.co.traynor.privategallery.core.browser.BrowserBookmark
import uk.co.traynor.privategallery.core.browser.BrowserViewportPolicy
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.WeakHashMap

internal class BrowserCallbacks {
    var onPageStarted: (String) -> Unit = {}
    var onPageFinished: (String) -> Unit = {}
    var onProgress: (Int) -> Unit = {}
    var onTitle: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onUnsupportedLink: () -> Unit = {}
    var onDownload: (url: String, userAgent: String, contentDisposition: String, mimeType: String) -> Unit = { _, _, _, _ -> }
    var onImageLongPress: (BrowserImageHitType, String?) -> Unit = { _, _ -> }
    var onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> }
    var onHideCustomView: () -> Unit = {}
    /** A short-lived, policy-restricted child used only for an ordinary WebView window request. */
    var onPopupOpened: (WebView) -> Unit = {}
    var onPopupClosed: () -> Unit = {}
    var onDiagnostic: (String) -> Unit = {}
}

/**
 * The Activity deliberately retains the WebView across destinations. Its WebViewClient therefore
 * also retains the callbacks created with that WebView. Rebind the same callback holder whenever
 * BrowserHome is composed again so diagnostics and page state always reach the visible session.
 */
internal object BrowserCallbackBindings {
    private val bindings = WeakHashMap<Any, BrowserCallbacks>()

    @Synchronized
    fun bind(owner: Any, candidate: BrowserCallbacks): BrowserCallbacks = bindings.getOrPut(owner) { candidate }

    /** Allows Activity-owned lifecycle policy to record only an already-sanitised event. */
    @Synchronized
    fun recordDiagnostic(owner: Any, event: String) {
        bindings[owner]?.onDiagnostic?.invoke(event)
    }
}

private data class BrowserImageRequest(
    val action: BrowserImageAcquisitionAction,
    val resourceUrl: String?,
)

/**
 * Kept injectable so an instrumentation test can exercise the real BrowserHome composition
 * without depending on a device WebView provider. Production always uses [secureBrowserWebView].
 */
internal fun interface BrowserWebViewFactory {
    fun create(context: android.content.Context, callbacks: BrowserCallbacks): WebView
}

/**
 * Browser V1 never adds a JavascriptInterface and deliberately permits only http(s) navigation.
 * Downloads and viewport screenshots enter only through the authenticated Vault coordinator.
 */
@Composable
internal fun BrowserHome(
    existingWebView: WebView?,
    searchEngine: BrowserSearchEngine,
    onWebViewReady: (WebView) -> Unit,
    onFullscreenExitChanged: ((() -> Unit)?) -> Unit,
    onClearBrowsingData: () -> Unit,
    onOpenBrowserSettings: () -> Unit,
    onSaveToVault: (VaultImportSource, (String) -> Unit) -> Unit = { _, _ -> },
    requireVpnForBrowsing: Boolean = false,
    vpnConnected: Boolean = true,
    bookmarks: List<BrowserBookmark> = emptyList(),
    onAddBookmark: (String, String, (String) -> Unit) -> Unit = { _, _, _ -> },
    onRemoveBookmark: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    webViewFactory: BrowserWebViewFactory = BrowserWebViewFactory(::secureBrowserWebView),
) {
    var address by remember { mutableStateOf(TextFieldValue(existingWebView?.url.orEmpty())) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    // Acquisition feedback is deliberately not a Browser navigation error. Keeping these state
    // channels separate prevents a successful screenshot/download from replacing the WebView.
    var pageError by remember { mutableStateOf<String?>(null) }
    var acquisitionFeedback by remember { mutableStateOf<String?>(null) }
    var initializedWebView by remember(existingWebView) { mutableStateOf(existingWebView) }
    var initializationFailed by remember(existingWebView) { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var addressFocused by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<VaultImportSource?>(null) }
    var pendingImage by remember { mutableStateOf<BrowserImageRequest?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val diagnosticRecorder = remember { BrowserDiagnosticRecorder() }
    var diagnostics by remember { mutableStateOf<List<String>>(emptyList()) }
    var diagnosticOutcomes by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingBookmarkUrl by remember { mutableStateOf<String?>(null) }
    val latestWebViewReady by rememberUpdatedState(onWebViewReady)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // The address field owns Back while it is focused. This lets Android dismiss the IME before
    // the Activity's browser-history policy is reached.
    BackHandler(enabled = addressFocused) { focusManager.clearFocus(force = true) }
    val networkAllowed = BrowserNetworkGatePolicy.mayStartNetworkRequest(requireVpnForBrowsing, vpnConnected)
    val callbacks = remember(existingWebView) {
        existingWebView?.let { BrowserCallbackBindings.bind(it, BrowserCallbacks()) } ?: BrowserCallbacks()
    }
    callbacks.onPageStarted = { url -> address = TextFieldValue(url); loading = true; pageError = null; acquisitionFeedback = null }
    callbacks.onPageFinished = { url -> address = TextFieldValue(url); loading = false; progress = 100 }
    callbacks.onProgress = { value -> progress = value }
    callbacks.onTitle = { value -> title = value }
    callbacks.onError = { value -> loading = false; pageError = value }
    callbacks.onUnsupportedLink = { acquisitionFeedback = "This link type is not supported in Private Gallery." }
    callbacks.onDownload = { url, userAgent, contentDisposition, mimeType ->
                if (!networkAllowed) {
                    acquisitionFeedback = "VPN is not connected. Download was not started."
                } else {
                    val filename = BrowserDownloadPolicy.safeDisplayName(contentDisposition.substringAfter("filename=", "download").trim().trim('"'))
                    pendingDownload = VaultImportSource(filename, mimeType.ifBlank { "application/octet-stream" }, {
                        require(BrowserNavigationPolicy.isWebUrl(url)) { "Unsupported download URL" }
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            setRequestProperty("User-Agent", userAgent)
                            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
                            require(BrowserDownloadPolicy.acceptsResponse(url, responseCode)) { "Download response was rejected" }
                        }.inputStream
                    }, sourceReference = null)
                }
    }
    callbacks.onImageLongPress = { hit, value ->
        val action = BrowserImagePolicy.actionFor(hit, value)
        if (action == BrowserImageAcquisitionAction.SAVE_RESOURCE && !networkAllowed) {
            acquisitionFeedback = "VPN is not connected. Image save was not started."
        } else if (action != null) pendingImage = BrowserImageRequest(action, BrowserImagePolicy.authorisedResource(value))
    }
    callbacks.onShowCustomView = { view, callback ->
        customView = view
        customViewCallback = callback
    }
    callbacks.onHideCustomView = {
        customView = null
        customViewCallback = null
    }
    callbacks.onPopupOpened = { popup -> popupWebView?.takeIf { it !== popup }?.destroy(); popupWebView = popup }
    callbacks.onPopupClosed = { popupWebView?.destroy(); popupWebView = null }
    callbacks.onDiagnostic = { event ->
        diagnosticRecorder.record(event)
        diagnostics = diagnosticRecorder.snapshot()
        diagnosticOutcomes = diagnosticRecorder.outcomeSummary()
    }
    LaunchedEffect(existingWebView) {
        if (existingWebView != null) {
            callbacks.onDiagnostic(BrowserDiagnosticsPolicy.webViewAttachment(retained = true))
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
            callbacks.onDiagnostic(BrowserDiagnosticsPolicy.webViewAttachment(retained = false))
            latestWebViewReady(view)
            Log.d(BROWSER_LOG_TAG, "WebView created and configured")
        }.onFailure {
            initializationFailed = true
            pageError = "Browser could not start. Try reopening Private Gallery."
            Log.w(BROWSER_LOG_TAG, "WebView creation failed", it)
        }.getOrNull()
    }
    fun retryWebView() {
        initializationFailed = false
        pageError = null
        ensureWebView()
    }
    fun submitAddress() {
        if (!networkAllowed) {
            acquisitionFeedback = "VPN is not connected. Browser networking is paused."
            return
        }
        val view = webViewRef.value ?: ensureWebView()
        if (view == null) {
            if (!initializationFailed) pageError = "Browser is still starting. Try again in a moment."
            return
        }
        runCatching { BrowserAddressPolicy.destinationFor(address.text, searchEngine) }
            .onSuccess {
                Log.d(BROWSER_LOG_TAG, "Starting HTTP(S) navigation")
                view.loadUrl(it.url)
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            .onFailure { acquisitionFeedback = "Enter a web address or search." }
    }
    LaunchedEffect(networkAllowed, pendingBookmarkUrl) {
        val destination = pendingBookmarkUrl ?: return@LaunchedEffect
        if (networkAllowed) {
            (webViewRef.value ?: ensureWebView())?.loadUrl(destination)
            pendingBookmarkUrl = null
        }
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
                    if (!networkAllowed) Text("VPN is connecting. Browser networking is blocked until it is confirmed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            text = { Text("Bookmark this page") },
                            enabled = webViewRef.value?.url?.let(BrowserNavigationPolicy::isWebUrl) == true,
                            onClick = {
                                overflowExpanded = false
                                webViewRef.value?.url?.let { url -> onAddBookmark(title, url) { result -> acquisitionFeedback = result } }
                            },
                        )
                        DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { overflowExpanded = false; showBookmarks = true })
                        DropdownMenuItem(text = { Text("Browser diagnostics") }, onClick = { overflowExpanded = false; showDiagnostics = true })
                        DropdownMenuItem(
                            text = { Text("Screenshot to Vault") },
                            enabled = webViewRef.value != null,
                            onClick = {
                                overflowExpanded = false
                                webViewRef.value?.let { page ->
                                    acquisitionFeedback = "Saving screenshot to Vault…"
                                    onSaveToVault(captureViewportSource(page)) { result -> acquisitionFeedback = result }
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
                        detail = pageError ?: "Browser could not start. Try reopening Private Gallery.",
                        onRetry = ::retryWebView,
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
                        detail = "Search or enter an address. Downloads can be saved directly to your Vault.",
                    )
                }
                acquisitionFeedback?.let { feedback ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(feedback, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
                }
            }
        }
        customView?.let { view ->
            AndroidView(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
                factory = { view },
            )
        }
        popupWebView?.let { popup ->
            // This is not a tab: it is the one temporary child WebView supplied by a normal
            // HTTPS window.open/target=_blank request. Closing always destroys it and returns
            // the originating page to an interactive state.
            Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.54f))) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(modifier = Modifier.fillMaxSize().padding(18.dp), factory = { popup })
                    IconButton(
                        modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
                        onClick = { callbacks.onPopupClosed() },
                    ) { Icon(Icons.Filled.Close, contentDescription = "Close website window") }
                }
            }
        }
        pendingDownload?.let { source ->
            AlertDialog(
                onDismissRequest = { pendingDownload = null },
                title = { Text("Save to Vault?") },
                text = { Text("This download will be encrypted directly into your Vault. It will not be saved to public Downloads.") },
                confirmButton = { TextButton(onClick = { pendingDownload = null; acquisitionFeedback = "Saving download to Vault…"; onSaveToVault(source) { result -> acquisitionFeedback = result } }) { Text("Save to Vault") } },
                dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text("Cancel") } },
            )
        }
        pendingImage?.let { request ->
            val isResource = request.action == BrowserImageAcquisitionAction.SAVE_RESOURCE
            AlertDialog(
                onDismissRequest = { pendingImage = null },
                title = { Text(if (isResource) "Save image to Vault?" else "Capture displayed image to Vault?") },
                text = { Text(if (isResource) "The image currently available to this Browser session will be encrypted directly into your Vault." else "The visible Browser viewport will be captured directly into your Vault. You can crop it there if needed.") },
                confirmButton = { TextButton(onClick = {
                    pendingImage = null
                    val page = webViewRef.value ?: return@TextButton
                    acquisitionFeedback = if (isResource) "Saving image to Vault…" else "Capturing displayed image to Vault…"
                    val source = request.resourceUrl?.let { browserImageSource(it, page.settings.userAgentString, page.url) } ?: captureViewportSource(page, "browser-displayed-image")
                    onSaveToVault(source) { result -> acquisitionFeedback = result }
                }) { Text(if (isResource) "Save to Vault" else "Capture to Vault") } },
                dismissButton = { TextButton(onClick = { pendingImage = null }) { Text("Cancel") } },
            )
        }
        if (showBookmarks) AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("Bookmarks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (bookmarks.isEmpty()) Text("No bookmarks yet.")
                    bookmarks.forEach { bookmark ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(modifier = Modifier.weight(1f), onClick = {
                                showBookmarks = false
                                pendingBookmarkUrl = bookmark.url
                                if (!networkAllowed) acquisitionFeedback = "VPN is connecting. Bookmark will open when connected."
                            }) {
                                Column { Text(bookmark.title, maxLines = 1); Text(java.net.URI(bookmark.url).host.orEmpty(), style = MaterialTheme.typography.labelSmall) }
                            }
                            TextButton(onClick = { onRemoveBookmark(bookmark.id) }) { Text("Remove") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBookmarks = false }) { Text("Close") } },
        )
        if (showDiagnostics) AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Browser diagnostics") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Structural events only. Page and session data are not recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (diagnostics.isEmpty()) Text("No events recorded in this Browser session.")
                diagnosticOutcomes.forEach { Text(it, style = MaterialTheme.typography.labelMedium) }
                diagnostics.forEach { Text(it, style = MaterialTheme.typography.labelMedium) }
            } },
            confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } },
        )
    }
}

/** Captures only already-rendered pixels; it never changes WebView URL, history or loading state. */
private fun captureViewportSource(page: WebView, prefix: String = "browser-screenshot"): VaultImportSource {
    val bitmap = Bitmap.createBitmap(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bitmap).also(page::draw)
    val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
    bitmap.recycle()
    return VaultImportSource(
        displayName = "$prefix-${System.currentTimeMillis()}.png",
        mimeType = "image/png",
        openStream = { ByteArrayInputStream(bytes) },
        onConsumed = { bytes.fill(0) },
    )
}

/** Uses precisely the image URL exposed by WebView's native hit test—never an inferred original. */
private fun browserImageSource(resourceUrl: String, userAgent: String, referer: String?): VaultImportSource {
    require(BrowserNavigationPolicy.isWebUrl(resourceUrl)) { "Unsupported image URL" }
    val filename = BrowserDownloadPolicy.safeDisplayName(URL(resourceUrl).path.substringAfterLast('/').ifBlank { "browser-image" })
    val mime = URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream"
    return VaultImportSource(
        displayName = filename,
        mimeType = mime,
        openStream = {
            (URL(resourceUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", userAgent)
                referer?.takeIf(BrowserNavigationPolicy::isWebUrl)?.let { setRequestProperty("Referer", it) }
                // Ordinary same-session cookies are sent only to the image origin. They are never
                // logged, retained in Vault metadata or exposed to page JavaScript.
                CookieManager.getInstance().getCookie(resourceUrl)?.let { setRequestProperty("Cookie", it) }
                connect()
                require(BrowserDownloadPolicy.acceptsResponse(url.toString(), responseCode)) { "Image response was rejected" }
            }.inputStream
        },
    )
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
        BrowserCallbackBindings.bind(this, callbacks)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = BrowserWebSecurityPolicy.fileAccessEnabled
            allowContentAccess = BrowserWebSecurityPolicy.contentAccessEnabled
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            // Preserve the known-good existing-page execution policy. Should a physical trace
            // establish a real child window later, the constrained WebChromeClient path below
            // remains the only place it can be enabled deliberately.
            javaScriptCanOpenWindowsAutomatically = BrowserWebSecurityPolicy.automaticWindowOpeningEnabled
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
            private var mainDocumentUrl: String? = null
            private fun structural(event: BrowserDiagnosticEvent, url: String? = null) {
                val value = BrowserDiagnosticsPolicy.event(event, url)
                Log.d(BROWSER_LOG_TAG, value)
                callbacks.onDiagnostic(value)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) structural(BrowserDiagnosticEvent.MAIN_NAVIGATION, request.url?.toString())
                return if (BrowserNavigationPolicy.isWebUrl(request.url.toString())) false else {
                    Log.d(BROWSER_LOG_TAG, "Blocked unsupported navigation scheme")
                    callbacks.onUnsupportedLink()
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                mainDocumentUrl = url
                structural(BrowserDiagnosticEvent.MAIN_PAGE_STARTED, url)
                callbacks.onPageStarted(url)
            }
            override fun onPageCommitVisible(view: WebView, url: String) {
                structural(BrowserDiagnosticEvent.MAIN_PAGE_COMMIT_VISIBLE, url)
                super.onPageCommitVisible(view, url)
            }
            override fun onPageFinished(view: WebView, url: String) {
                structural(BrowserDiagnosticEvent.MAIN_PAGE_FINISHED, url)
                callbacks.onPageFinished(url)
            }

            override fun onLoadResource(view: WebView, url: String) {
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.resourceLoad(url, mainDocumentUrl))
                super.onLoadResource(view, url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    callbacks.onDiagnostic(BrowserDiagnosticsPolicy.mainFrameError(error.errorCode))
                    Log.w(BROWSER_LOG_TAG, "Main-frame page load failed: ${error.errorCode}")
                    callbacks.onError("Page load failed. Check your connection and try again.")
                } else {
                    callbacks.onDiagnostic(
                        BrowserDiagnosticsPolicy.resourceError(
                            url = request.url?.toString(),
                            errorCode = error.errorCode,
                            isMainFrame = false,
                        ),
                    )
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                callbacks.onDiagnostic(
                    BrowserDiagnosticsPolicy.httpError(
                        statusCode = errorResponse.statusCode,
                        isMainFrame = request.isForMainFrame,
                    ),
                )
                if (request.isForMainFrame && errorResponse.statusCode >= 400) callbacks.onError("Page load failed. The website returned an error.")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                // Never offer an application-level certificate bypass.
                handler.cancel()
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.TLS_ERROR))
                Log.w(BROWSER_LOG_TAG, "TLS error blocked")
                callbacks.onError("TLS certificate error. This page was not opened.")
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.RENDER_PROCESS_GONE))
                callbacks.onError("Browser renderer stopped. Reopen Browser and try again.")
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = callbacks.onProgress(newProgress)
            override fun onReceivedTitle(view: WebView, title: String?) { callbacks.onTitle(title.orEmpty()) }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_WINDOW_REQUEST))
                // A normal modal can create its child after the input callback returns, where
                // WebView reports isUserGesture=false. It remains constrained to one temporary
                // HTTP(S) child below; no external scheme or unrestricted tab is opened.
                Log.d(BROWSER_LOG_TAG, "Web window requested (dialog=$isDialog, userGesture=$isUserGesture)")
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                            callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_NAVIGATION, request.url?.toString()))
                            val safe = BrowserPopupPolicy.actionFor(request.url?.toString()) == BrowserPopupAction.LOAD_IN_CURRENT_VIEW
                            Log.d(BROWSER_LOG_TAG, "Child window navigation ${if (safe) "accepted" else "blocked"}")
                            if (!safe) callbacks.onUnsupportedLink()
                            return !safe
                        }
                        override fun onPageStarted(popupView: WebView, url: String, favicon: Bitmap?) {
                            callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_NAVIGATION, url))
                            if (!BrowserNavigationPolicy.isWebUrl(url)) {
                                Log.d(BROWSER_LOG_TAG, "Child window unsafe main-frame navigation blocked")
                                popupView.stopLoading()
                                callbacks.onUnsupportedLink()
                            }
                        }
                        override fun onReceivedError(popupView: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_FRAME_ERROR, request.url?.toString()))
                                Log.w(BROWSER_LOG_TAG, "Child window main-frame load failed: ${error.errorCode}")
                            }
                        }
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_WEBVIEW_CREATED))
                callbacks.onPopupOpened(popup)
                return true
            }
            override fun onCloseWindow(window: WebView) {
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.CHILD_WINDOW_CLOSED))
                Log.d(BROWSER_LOG_TAG, "Child window closed")
                callbacks.onPopupClosed()
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) = callbacks.onShowCustomView(view, callback)
            override fun onHideCustomView() = callbacks.onHideCustomView()
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                callbacks.onDiagnostic(
                    BrowserDiagnosticsPolicy.consoleMessage(
                        consoleMessage.messageLevel().name,
                        consoleMessage.message(),
                    ),
                )
                return super.onConsoleMessage(consoleMessage)
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.PERMISSION_REQUEST))
                // This diagnostic pass must not change the pre-existing capability policy.
                // WebChromeClient's default remains in control until device evidence shows that
                // a request is relevant to the failed interaction.
                super.onPermissionRequest(request)
            }
            override fun onShowFileChooser(webView: WebView, filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>, fileChooserParams: FileChooserParams): Boolean {
                callbacks.onDiagnostic(BrowserDiagnosticsPolicy.event(BrowserDiagnosticEvent.FILE_CHOOSER_REQUEST))
                // Preserve the existing default behaviour while recording only the mechanism.
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            }
        }
        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (BrowserNavigationPolicy.downloadAction() == BrowserDownloadAction.REQUEST_VAULT_SAVE) callbacks.onDownload(url, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType.orEmpty())
        })
        setOnLongClickListener {
            val result = hitTestResult
            val hit = when (result.type) {
                HitTestResult.IMAGE_TYPE -> BrowserImageHitType.IMAGE
                HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> BrowserImageHitType.IMAGE_LINK
                else -> BrowserImageHitType.TEXT
            }
            if (hit == BrowserImageHitType.TEXT) false else {
                callbacks.onImageLongPress(hit, result.extra)
                true
            }
        }
    }

private const val BROWSER_LOG_TAG = "PrivateGalleryBrowser"
