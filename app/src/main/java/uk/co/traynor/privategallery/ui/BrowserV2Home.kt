package uk.co.traynor.privategallery.ui

import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.CookieManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import uk.co.traynor.privategallery.core.browser.BrowserAddressPolicy
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.BrowserBookmark
import uk.co.traynor.privategallery.core.browser.v2.BrowserMessage
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry
import uk.co.traynor.privategallery.core.browser.v2.BrowserExternalNavigationPolicy
import uk.co.traynor.privategallery.core.browser.v2.BrowserFocusMode
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import uk.co.traynor.privategallery.core.browser.BrowserDownloadPolicy
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy

/**
 * Compose chrome for the Activity-owned V2 session. AndroidView only attaches the selected tab's
 * WebView. It never constructs, reconfigures or destroys it as a recomposition side effect.
 */
@Composable
internal fun BrowserV2ProductionDestination(
    session: BrowserV2Session,
    searchEngine: BrowserSearchEngine,
    onSaveToVault: (VaultImportSource, (String) -> Unit) -> Unit,
    onHistoryVisited: (String, String) -> Unit,
    saveHistory: Boolean,
    onSaveHistoryChanged: (Boolean) -> Unit,
    bookmarks: List<BrowserBookmark>,
    onAddBookmark: (String, String, (String) -> Unit) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onLoadHistory: ((List<BrowserHistoryEntry>) -> Unit) -> Unit,
    onClearHistory: (() -> Unit) -> Unit,
    onOpenBrowserSettings: () -> Unit,
    modifier: Modifier = Modifier,
    acceptanceProbeEnabled: Boolean = false,
    staticContentHost: Boolean = false,
    onOpenGallery: (() -> Unit)? = null,
    onOpenVault: (() -> Unit)? = null,
    onOpenFavourite: (() -> Unit)? = null,
    favouriteLabel: String = "Favourite",
    connectionPresentation: BrowserConnectionPresentation = BrowserConnectionPresentation(false),
    onConnectVpn: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    browserSettings: (@Composable () -> Unit)? = null,
) {
    SideEffect { session.recordAcceptanceUiEvent("BROWSER_ROUTE_ENTERED") }
    Column(
        modifier.fillMaxSize().then(if (acceptanceProbeEnabled) Modifier.background(Color(0xFFCC0000)) else Modifier)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                session.recordAcceptanceUiEvent("BROWSER_ROUTE_MEASURED", mapOf("x" to origin.x.toInt().toString(), "y" to origin.y.toInt().toString(), "width" to coordinates.size.width.toString(), "height" to coordinates.size.height.toString()))
            }
            .semantics { testTag = "browser-production-route" },
    ) {
        if (acceptanceProbeEnabled) AcceptanceProbeLabel("BROWSER_ROUTE", Color(0xFFCC0000))
        BrowserV2Home(
            session = session,
            searchEngine = searchEngine,
            onSaveToVault = onSaveToVault,
            onHistoryVisited = onHistoryVisited,
            saveHistory = saveHistory,
            onSaveHistoryChanged = onSaveHistoryChanged,
            bookmarks = bookmarks,
            onAddBookmark = onAddBookmark,
            onRemoveBookmark = onRemoveBookmark,
            onLoadHistory = onLoadHistory,
            onClearHistory = onClearHistory,
            onOpenBrowserSettings = onOpenBrowserSettings,
            modifier = Modifier.weight(1f),
            acceptanceProbeEnabled = acceptanceProbeEnabled,
            staticContentHost = staticContentHost,
            onOpenGallery = onOpenGallery,
            onOpenVault = onOpenVault,
            onOpenFavourite = onOpenFavourite,
            favouriteLabel = favouriteLabel,
            connectionPresentation = connectionPresentation,
            onConnectVpn = onConnectVpn,
            onFullscreenChanged = onFullscreenChanged,
            browserSettings = browserSettings,
        )
    }
}

@Composable
internal fun BrowserV2Home(
    session: BrowserV2Session,
    searchEngine: BrowserSearchEngine,
    onSaveToVault: (VaultImportSource, (String) -> Unit) -> Unit,
    onHistoryVisited: (String, String) -> Unit,
    saveHistory: Boolean,
    onSaveHistoryChanged: (Boolean) -> Unit,
    bookmarks: List<BrowserBookmark>,
    onAddBookmark: (String, String, (String) -> Unit) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onLoadHistory: ((List<BrowserHistoryEntry>) -> Unit) -> Unit,
    onClearHistory: (() -> Unit) -> Unit,
    onOpenBrowserSettings: () -> Unit,
    modifier: Modifier = Modifier,
    acceptanceProbeEnabled: Boolean = false,
    staticContentHost: Boolean = false,
    onOpenGallery: (() -> Unit)? = null,
    onOpenVault: (() -> Unit)? = null,
    onOpenFavourite: (() -> Unit)? = null,
    favouriteLabel: String = "Favourite",
    connectionPresentation: BrowserConnectionPresentation = BrowserConnectionPresentation(false),
    onConnectVpn: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    browserSettings: (@Composable () -> Unit)? = null,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scrollChrome = remember(density) { uk.co.traynor.privategallery.core.browser.BrowserChromeScroll((48 * density).toInt().coerceAtLeast(1)) }
    var chromeVisible by remember { mutableStateOf(true) }
    var addressFocused by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var webVideoView by remember { mutableStateOf(false) }
    var internalMedia by remember { mutableStateOf<Pair<String, String>?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var address by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }
    var tabSwitcher by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var pendingPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeolocation by remember { mutableStateOf<Pair<String, android.webkit.GeolocationPermissions.Callback>?>(null) }
    var pendingFileResult by remember { mutableStateOf<ValueCallback<Array<android.net.Uri>>?>(null) }
    var pendingImageResource by remember { mutableStateOf<String?>(null) }
    var pendingScreenshotFallback by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<BrowserHistoryEntry>>(emptyList()) }
    var findOpen by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var diagnosticCaptureBusy by remember { mutableStateOf(false) }
    var diagnosticArmFailed by remember { mutableStateOf(false) }
    var pendingExternalNavigation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val controlsPinned by rememberUpdatedState(addressFocused || overflow || tabSwitcher || bookmarksOpen || historyOpen || settingsOpen || findOpen || webVideoView || fullscreen != null || connectionPresentation.blocked)
    fun openSettings() { if (browserSettings != null) settingsOpen = true else onOpenBrowserSettings() }
    LaunchedEffect(controlsPinned) { if (controlsPinned) { scrollChrome.reveal(); chromeVisible = true } }
    val latestSave by rememberUpdatedState(onSaveToVault)
    val latestFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    val latestConnectionBlocked by rememberUpdatedState(connectionPresentation.blocked)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFileResult?.onReceiveValue(uri?.let { arrayOf(it) })
        pendingFileResult = null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val request = pendingPermission
        val requested = request?.resources.orEmpty()
        val androidPermissions = requested.mapNotNull {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> null
            }
        }.distinct()
        if (request != null) {
            if (androidPermissions.isNotEmpty() && androidPermissions.all { grants[it] == true }) request.grant(requested) else request.deny()
        }
        pendingPermission = null
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        pendingGeolocation?.let { (origin, callback) ->
            callback.invoke(origin, grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true, false)
        }
        pendingGeolocation = null
    }
    fun openWebVideoView() {
        session.requestVideoView { available ->
            if (available) {
                internalMedia = null
                webVideoView = true
                latestFullscreenChanged(true)
                session.recordMediaPath("WEBVIEW_FULLSCREEN")
            } else {
                session.recordMediaPath("UNSUPPORTED")
                message = "No active video view is available. Use the website’s own playback controls."
            }
        }
    }
    val videoLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, videoLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                session.pauseForBackground()
                webVideoView = false
                internalMedia = null
                latestFullscreenChanged(false)
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) session.resumeForeground()
        }
        videoLifecycle.addObserver(observer)
        onDispose { videoLifecycle.removeObserver(observer) }
    }
    fun saveViewportScreenshot() {
        runCatching { browserV2ViewportSource(requireNotNull(session.activeWebViewOrNull())) }
            .onSuccess { source -> latestSave(source) { message = it } }
            .onFailure { message = "Unable to capture the visible Browser page. The page is still open." }
    }

    DisposableEffect(session) {
        session.bindListener(object : BrowserV2Session.Listener {
            override fun onSessionChanged() { revision++ }
            override fun onUserScroll(deltaY: Int, atTop: Boolean) {
                val accessibility = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                if (controlsPinned || accessibility?.isTouchExplorationEnabled == true) {
                    scrollChrome.reveal(); chromeVisible = true
                } else chromeVisible = scrollChrome.onScroll(deltaY, atTop)
            }
            override fun onMessage(value: BrowserMessage) {
                message = when (value) {
                    BrowserMessage.VpnRequired -> null
                    BrowserMessage.UnsupportedScheme -> "This link type is not supported in Private Gallery."
                    BrowserMessage.NetworkError -> "Page load failed. Check the connection and try again."
                    BrowserMessage.TlsRejected -> "TLS certificate error. This page was not opened."
                    BrowserMessage.RendererGone -> "The page renderer stopped. Reload this tab to recover."
                    is BrowserMessage.HttpError -> "The website returned HTTP ${value.statusCode}."
                }
            }
            override fun onDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
                // V2's acquisition adapter is intentionally supplied by the Activity; the UI never
                // navigates or replaces the tab in response to a Vault import result.
                latestSave(browserV2DownloadSource(url, userAgent, contentDisposition, mimeType)) { message = it }
            }
            override fun onImageLongPress(resourceUrl: String?) {
                if (resourceUrl != null && BrowserNavigationPolicy.isWebUrl(resourceUrl)) pendingImageResource = resourceUrl
                else pendingScreenshotFallback = true
            }
            override fun onExternalNavigation(value: String) { pendingExternalNavigation = value }
            override fun onHistoryVisit(title: String, url: String) = onHistoryVisited(title, url)
            override fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (latestConnectionBlocked) { callback.onCustomViewHidden(); return }
                webVideoView = false
                fullscreen = view to callback
                latestFullscreenChanged(true)
            }
            override fun onExitFullscreen() { fullscreen = null; latestFullscreenChanged(false) }
            override fun onPermissionRequest(request: PermissionRequest) {
                pendingPermission?.takeUnless { it === request }?.deny()
                pendingPermission = request
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingPermission === request) pendingPermission = null
            }
            override fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
                pendingGeolocation?.let { (previousOrigin, previousCallback) -> previousCallback.invoke(previousOrigin, false, false) }
                pendingGeolocation = origin to callback
            }
            override fun onShowFileChooser(callback: ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
                pendingFileResult?.onReceiveValue(null)
                pendingFileResult = callback
                picker.launch(params.acceptTypes.firstOrNull { !it.isNullOrBlank() } ?: "*/*")
                return true
            }
        })
        onDispose {
            session.exitFullscreen()
            fullscreen = null
            latestFullscreenChanged(false)
            pendingPermission?.deny()
            pendingPermission = null
            pendingFileResult?.onReceiveValue(null)
            pendingFileResult = null
            pendingGeolocation?.let { (origin, callback) -> callback.invoke(origin, false, false) }
            pendingGeolocation = null
            session.pauseForBackground()
            session.bindListener(uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener)
        }
    }

    internalMedia?.let { media ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { internalMedia = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val mediaItem = remember(media) { androidx.media3.common.MediaItem.Builder().setUri(media.first).setMimeType(media.second).build() }
            PrivateVideoPlayer(mediaItem, networkAllowed = session::mediaNetworkingAllowed, onClose = { internalMedia = null },
                onReady = { session.pauseActiveMedia(); session.recordMediaPath("MEDIA3_DIRECT") },
                onWebViewFallback = { internalMedia = null; openWebVideoView() })
        }
    }

    // Deliberately read through revision so WebView callbacks update stable tab chrome.
    @Suppress("UNUSED_VARIABLE") val stateVersion = revision
    val active = session.tabs.activeTab
    LaunchedEffect(active.id, active.url) {
        scrollChrome.reveal(); chromeVisible = true
        if (address != active.url) address = active.url
        webVideoView = false
        internalMedia = null
        if (fullscreen == null) latestFullscreenChanged(false)
    }
    LaunchedEffect(connectionPresentation.blocked) {
        scrollChrome.reveal(); chromeVisible = true
        if (connectionPresentation.blocked) {
            internalMedia = null
            webVideoView = false
            session.enforceNetworkPolicy()
            latestFullscreenChanged(false)
        } else if (videoLifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            session.resumeForeground()
        }
        if (connectionPresentation.blocked && fullscreen != null) {
            fullscreen?.second?.onCustomViewHidden()
            fullscreen = null
            latestFullscreenChanged(false)
        }
    }
    BackHandler(enabled = webVideoView) { webVideoView = false; latestFullscreenChanged(false) }
    BackHandler(enabled = fullscreen != null) {
        fullscreen?.second?.onCustomViewHidden()
        fullscreen = null
        latestFullscreenChanged(false)
    }
    BackHandler(enabled = fullscreen == null && !webVideoView && internalMedia == null && active.canGoBack) { session.goBackActive() }

    // Obtain the Android view after the listener is bound, but never let a provider failure abort
    // the surrounding Compose tree. The V2 chrome is the useful recovery surface.
    val activeWebView = if (staticContentHost || connectionPresentation.blocked) null else session.activeWebViewOrNull()
    LaunchedEffect(session, staticContentHost) {
        if (!staticContentHost) session.recordAcceptanceUiEvent("REAL_MODE_ENTERED")
    }
    SideEffect { session.recordAcceptanceUiEvent("BROWSER_V2_COMPOSED") }
    Box(
        modifier = modifier.fillMaxSize()
            .then(if (acceptanceProbeEnabled) Modifier.background(Color(0xFF00A000)) else Modifier)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                session.recordAcceptanceUiEvent("BROWSER_ROOT_MEASURED", mapOf("x" to origin.x.toInt().toString(), "y" to origin.y.toInt().toString(), "width" to coordinates.size.width.toString(), "height" to coordinates.size.height.toString()))
            }
            .semantics { testTag = "browser-v2-root" },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (acceptanceProbeEnabled) AcceptanceProbeLabel("BROWSER_V2_ROOT", Color(0xFF00A000))
            if (!webVideoView && (chromeVisible || controlsPinned)) Column(
                Modifier.fillMaxWidth()
                    .then(if (acceptanceProbeEnabled) Modifier.background(Color(0xFF0000CC)) else Modifier)
                    .onGloballyPositioned { coordinates ->
                        val origin = coordinates.positionInRoot()
                        session.recordAcceptanceUiEvent("BROWSER_CHROME_MEASURED", mapOf("x" to origin.x.toInt().toString(), "y" to origin.y.toInt().toString(), "width" to coordinates.size.width.toString(), "height" to coordinates.size.height.toString()))
                    }
                    .semantics { testTag = "browser-v2-chrome" },
            ) {
                SideEffect { session.recordAcceptanceUiEvent("BROWSER_CHROME_COMPOSED") }
                if (acceptanceProbeEnabled) AcceptanceProbeLabel("BROWSER_CHROME", Color(0xFF0000CC))
                if (active.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextField(
                        value = address,
                        onValueChange = {
                            address = it
                            session.recordAcceptanceUiEvent("OMNIBOX_TEXT_CHANGED")
                        },
                        modifier = Modifier.weight(1f)
                            .onFocusChanged { focus -> addressFocused = focus.isFocused; session.recordAcceptanceUiEvent(if (focus.isFocused) "OMNIBOX_FOCUS_GAINED" else "OMNIBOX_FOCUS_CHANGED") }
                            .semantics { testTag = "browser-v2-address" },
                        singleLine = true,
                        enabled = !connectionPresentation.blocked,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = "Web address") },
                        placeholder = { Text("Search or enter address") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
                            focusManager.clearFocus()
                            session.recordAcceptanceUiEvent("OMNIBOX_SUBMIT")
                            if (staticContentHost) {
                                session.recordAcceptanceUiEvent("OMNIBOX_SUBMIT_STATIC_HOST_IGNORED")
                                message = "Static content host is active. Switch to Real WebView before navigating."
                            } else {
                                runCatching { BrowserAddressPolicy.destinationFor(address, searchEngine) }
                                    .onSuccess { session.navigateActive(it.url) }
                                    .onFailure { message = "Enter a web address or search." }
                            }
                        }),
                    )
                    IconButton(enabled = !connectionPresentation.blocked, modifier = Modifier.semantics { testTag = "browser-v2-reload" }, onClick = { if (active.loading) session.stopActive() else session.reloadActive() }) {
                        Icon(if (active.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (active.loading) "Stop" else "Reload")
                    }
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .then(if (acceptanceProbeEnabled) Modifier.background(Color(0xFFFFD800)) else Modifier)
                    .onGloballyPositioned { coordinates ->
                        val origin = coordinates.positionInRoot()
                        session.recordAcceptanceUiEvent("CONTENT_HOST_MEASURED", mapOf("x" to origin.x.toInt().toString(), "y" to origin.y.toInt().toString(), "width" to coordinates.size.width.toString(), "height" to coordinates.size.height.toString()))
                    }
                    .semantics { testTag = "browser-v2-page-region" },
            ) {
                SideEffect { session.recordAcceptanceUiEvent("CONTENT_HOST_COMPOSED") }
                // Retain ordinary tabs, but attach a new provider instance after renderer/VPN recovery.
                if (connectionPresentation.blocked) {
                    BrowserConnectionState(connectionPresentation, onConnectVpn, ::openSettings)
                } else if (staticContentHost) {
                    Column(
                        Modifier.fillMaxSize().semantics { testTag = "browser-v2-static-content-host" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("BROWSER CONTENT HOST")
                    }
                } else if (activeWebView != null) {
                    androidx.compose.runtime.key(active.id, activeWebView) {
                        AndroidView(
                            factory = {
                                session.recordAcceptanceUiEvent("WEBVIEW_HOST_REQUESTED")
                                session.onActiveWebViewHostCreated(activeWebView)
                                activeWebView
                            },
                            update = { session.onActiveWebViewUpdated(it) },
                            // Before its first frame WebView fills the canvas with its background.
                            // AndroidView does not clip by default: constrain drawing, not geometry.
                            modifier = Modifier.fillMaxSize().clipToBounds().onGloballyPositioned { coordinates ->
                                val origin = coordinates.positionInRoot()
                                session.recordAcceptanceUiEvent("WEBVIEW_HOST_MEASURED", mapOf("x" to origin.x.toInt().toString(), "y" to origin.y.toInt().toString(), "width" to coordinates.size.width.toString(), "height" to coordinates.size.height.toString()))
                            }.semantics { testTag = "browser-v2-webview-host" },
                            onRelease = session::onActiveWebViewDetached,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("BROWSER UNAVAILABLE", style = MaterialTheme.typography.titleMedium)
                        Text("The Android WebView provider could not start. Your tabs remain available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { session.retryActiveWebView() }) { Text("Retry") }
                    }
                }
                if (acceptanceProbeEnabled) AcceptanceProbeLabel("CONTENT_HOST", Color(0xFFFFD800))
            }
            if (!webVideoView && (chromeVisible || controlsPinned)) Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { testTag = "browser-v2-toolbar" },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !connectionPresentation.blocked && active.canGoBack, onClick = session::goBackActive) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    IconButton(enabled = !connectionPresentation.blocked && active.canGoForward, onClick = session::goForwardActive) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
                    IconButton(onClick = { bookmarksOpen = true }) { Icon(Icons.Default.StarOutline, "Bookmarks") }
                    IconButton(modifier = Modifier.semantics { testTag = "browser-v2-tabs" }, onClick = { tabSwitcher = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CropSquare, contentDescription = "Tabs", modifier = Modifier.size(32.dp))
                            Text(session.tabs.tabs.size.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Default.Menu, "More") }
                }
            }
        }
        if (webVideoView) {
            BrowserVideoWindow()
            IconButton(onClick = { webVideoView = false; latestFullscreenChanged(false) }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .6f))) {
                Icon(Icons.Default.FullscreenExit, "Exit video view", tint = Color.White)
            }
        }
        if (overflow) GalleryMenuSheet("Browser", onDismiss = { overflow = false }) {
            SheetAction("Video view", Icons.Default.Fullscreen) { overflow = false; openWebVideoView() }
            SheetAction("Play in Private Gallery", Icons.Default.PlayCircle) {
                overflow = false
                session.requestPlayableMedia { media ->
                    if (media == null) openWebVideoView()
                    else internalMedia = media
                }
            }
            SheetAction("New tab", Icons.Default.Add) { overflow = false; session.newTab() }
            SheetAction("Bookmark this page", Icons.Default.StarOutline) {
                overflow = false
                if (active.url.isBlank()) message = "Open an HTTP(S) page before bookmarking it."
                else onAddBookmark(active.title, active.url) { message = it }
            }
            SheetAction("Bookmarks", Icons.Default.Bookmarks) { overflow = false; bookmarksOpen = true }
            SheetAction("History", Icons.Default.History) { overflow = false; onLoadHistory { history = it; historyOpen = true } }
            SheetAction("Find in page", Icons.Default.Search) { overflow = false; findOpen = true }
            SheetAction("Screenshot to Vault", Icons.Default.Screenshot) { overflow = false; saveViewportScreenshot() }
            SheetAction(if (active.desktopSite) "Mobile site" else "Desktop site", Icons.Default.Computer) { overflow = false; session.setDesktopSite(active.id, !active.desktopSite) }
            SheetAction("Browser settings", Icons.Default.Settings) { overflow = false; openSettings() }
            if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) SheetAction("Browser diagnostics", Icons.Default.BugReport) { overflow = false; diagnosticsOpen = true }
            if (onOpenGallery != null || onOpenVault != null || onOpenFavourite != null) {
                SheetSection("Private Gallery")
                if (onOpenGallery != null) SheetAction("Gallery", Icons.Default.PhotoLibrary) { overflow = false; onOpenGallery() }
                if (onOpenVault != null) SheetAction("Vault", Icons.Default.Lock) { overflow = false; onOpenVault() }
                if (onOpenFavourite != null) SheetAction(favouriteLabel, Icons.Default.Favorite) { overflow = false; onOpenFavourite() }
                SheetAction("App settings", Icons.Default.Settings) { overflow = false; onOpenBrowserSettings() }
            }
        }
        if (settingsOpen && browserSettings != null) BrowserPanel("Browser settings", "Search, privacy and connection", { settingsOpen = false }) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) { browserSettings() }
        }
        if (tabSwitcher) BrowserTabsPanel(session.tabs.tabs, active.id,
            onSelect = { session.select(it); tabSwitcher = false }, onRemove = session::close,
            onNew = { session.newTab(); tabSwitcher = false }, onClose = { tabSwitcher = false })
        if (bookmarksOpen) BrowserBookmarksPanel(bookmarks,
            onOpen = { session.navigateActive(it); bookmarksOpen = false }, onRemove = onRemoveBookmark,
            onClose = { bookmarksOpen = false })
        if (historyOpen) BrowserHistoryPanel(history, saveHistory,
            onOpen = { session.navigateActive(it); historyOpen = false },
            onClear = { onClearHistory { history = emptyList() } }, onClose = { historyOpen = false })
        if (findOpen) AlertDialog(
            onDismissRequest = { session.clearFindInActivePage(); findOpen = false }, title = { Text("Find in page") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = findText, onValueChange = { findText = it; session.findInActivePage(it) }, singleLine = true, placeholder = { Text("Find text") })
                Row { TextButton(onClick = { session.findNextInActivePage(false) }) { Text("Previous") }; TextButton(onClick = { session.findNextInActivePage(true) }) { Text("Next") } }
            } }, confirmButton = { TextButton(onClick = { session.clearFindInActivePage(); findOpen = false }) { Text("Close") } },
        )
        if (diagnosticsOpen && BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) AlertDialog(
            onDismissRequest = { diagnosticsOpen = false }, title = { Text("Browser diagnostics") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Clear removes current-session events. The previous-process fatal report is retained.", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !diagnosticCaptureBusy && session.verboseDiagnosticsEnabled(), onClick = {
                    diagnosticCaptureBusy = true
                    session.armAcceptanceInteraction { ready -> diagnosticCaptureBusy = false; diagnosticArmFailed = !ready; if (ready) diagnosticsOpen = false; revision++ }
                }) { Text("Arm next interaction") }
                TextButton(enabled = !diagnosticCaptureBusy && session.verboseDiagnosticsEnabled(), onClick = {
                    diagnosticCaptureBusy = true
                    session.captureAcceptanceRuntime { diagnosticCaptureBusy = false; revision++ }
                }) { Text(if (diagnosticCaptureBusy) "Capturing…" else "Capture diagnostic snapshot") }
                if (diagnosticArmFailed) Text("Could not arm this page. Wait for loading to finish and try again.")
                Text("Arm before the failing tap. Capture afterwards, then Copy all. Structure only; no browsing content.", style = MaterialTheme.typography.bodySmall)
                Text("Browser compatibility test", style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = { session.setAcceptanceFocusMode(BrowserFocusMode.CURRENT); revision++ }) { Text("Current") }
                    TextButton(onClick = { session.setAcceptanceFocusMode(BrowserFocusMode.EXPLICIT_WEBVIEW_FOCUS); revision++ }) { Text("Explicit WebView focus") }
                }
                TextButton(onClick = { session.setVerboseDiagnostics(!session.verboseDiagnosticsEnabled()); revision++ }) {
                    Text(if (session.verboseDiagnosticsEnabled()) "Verbose diagnostics: on" else "Verbose diagnostics: off")
                }
                Text(session.acceptanceReport(), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { diagnosticsOpen = false }) { Text("Close") } },
            dismissButton = { Row {
                TextButton(enabled = !diagnosticCaptureBusy, onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Private Gallery Browser acceptance trace", session.acceptanceReport())) }) { Text("Copy all") }
                TextButton(onClick = { session.clearAcceptanceReport(); revision++ }) { Text("Clear current session") }
            } },
        )
        message?.let { value -> AlertDialog(onDismissRequest = { message = null }, text = { Text(value) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
        pendingPermission?.let { request -> AlertDialog(
            onDismissRequest = { request.deny(); pendingPermission = null },
            title = { Text("Website permission") },
            text = { Text("Allow this website to use the requested capability for this session?") },
            confirmButton = { TextButton(onClick = {
                val required = request.resources.mapNotNull {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        else -> null
                    }
                }.distinct()
                if (required.isEmpty()) { request.deny(); pendingPermission = null }
                else if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) { request.grant(request.resources); pendingPermission = null }
                else permissionLauncher.launch(required.toTypedArray())
            }) { Text("Allow this time") } },
            dismissButton = { TextButton(onClick = { request.deny(); pendingPermission = null }) { Text("Deny") } },
        ) }
        pendingGeolocation?.let { (origin, callback) -> AlertDialog(
            onDismissRequest = { callback.invoke(origin, false, false); pendingGeolocation = null },
            title = { Text("Website location") },
            text = { Text("Allow this website to use your location for this session?") },
            confirmButton = { TextButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) { callback.invoke(origin, true, false); pendingGeolocation = null }
                else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Allow this time") } },
            dismissButton = { TextButton(onClick = { callback.invoke(origin, false, false); pendingGeolocation = null }) { Text("Deny") } },
        ) }
        pendingImageResource?.let { resource -> AlertDialog(
            onDismissRequest = { pendingImageResource = null },
            title = { Text("Save image to Vault") },
            text = { Text("Save the image resource currently made available to this Browser session?") },
            confirmButton = { TextButton(onClick = {
                pendingImageResource = null
                session.activeWebViewOrNull()?.let { view ->
                    latestSave(browserV2ImageSource(resource, view.settings.userAgentString, session.tabs.activeTab.url)) { message = it }
                } ?: run { message = "Browser is unavailable. Retry before saving an image." }
            }) { Text("Save to Vault") } },
            dismissButton = { TextButton(onClick = { pendingImageResource = null }) { Text("Cancel") } },
        ) }
        if (pendingScreenshotFallback) AlertDialog(
            onDismissRequest = { pendingScreenshotFallback = false },
            title = { Text("Displayed image") },
            text = { Text("This visual has no safe image resource. Save the visible Browser viewport as a screenshot instead?") },
            confirmButton = { TextButton(onClick = {
                pendingScreenshotFallback = false
                saveViewportScreenshot()
            }) { Text("Screenshot to Vault") } },
            dismissButton = { TextButton(onClick = { pendingScreenshotFallback = false }) { Text("Cancel") } },
        )
        pendingExternalNavigation?.let { value ->
            val plan = remember(value) { BrowserExternalNavigationPolicy.plan(context, value) }
            AlertDialog(
                onDismissRequest = { pendingExternalNavigation = null },
                title = { Text("Open in app?") },
                text = { Text(
                    when {
                        plan.openIntent != null -> "This link can be opened by another app."
                        plan.httpsFallback != null -> "No compatible app is available. Open the safe website fallback here instead?"
                        else -> "This link requires an external app or uses an unsupported link type."
                    },
                ) },
                confirmButton = {
                    when {
                        plan.openIntent != null -> TextButton(onClick = {
                            runCatching { context.startActivity(plan.openIntent) }
                            pendingExternalNavigation = null
                        }) { Text("Open") }
                        plan.httpsFallback != null -> TextButton(onClick = {
                            session.navigateActive(plan.httpsFallback)
                            pendingExternalNavigation = null
                        }) { Text("Open website") }
                        else -> TextButton(onClick = { pendingExternalNavigation = null }) { Text("OK") }
                    }
                },
                dismissButton = { TextButton(onClick = { pendingExternalNavigation = null }) { Text("Cancel") } },
            )
        }
                fullscreen?.takeUnless { connectionPresentation.blocked }?.let { (view, _) -> BrowserVideoFullscreen(view, session::exitFullscreen) }
    }
}

@Composable
private fun AcceptanceProbeLabel(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().height(22.dp).background(color).padding(horizontal = 4.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
    )
}

/** The download is opened only after the WebView download callback and imported through Vault. */
private fun browserV2DownloadSource(
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimeType: String,
): VaultImportSource {
    require(BrowserNavigationPolicy.isWebUrl(url)) { "Unsupported download URL" }
    val name = BrowserDownloadPolicy.safeDisplayName(contentDisposition.substringAfter("filename=", "download").trim().trim('"'))
    return VaultImportSource(
        displayName = name,
        mimeType = mimeType.ifBlank { "application/octet-stream" },
        openStream = {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
                require(BrowserDownloadPolicy.acceptsResponse(url, responseCode)) { "Download response was rejected" }
            }.inputStream
        },
        sourceReference = null,
    )
}

/** Uses only WebView's exposed hit-test resource, never a guessed original or premium variant. */
private fun browserV2ImageSource(resourceUrl: String, userAgent: String, referer: String?): VaultImportSource {
    require(BrowserNavigationPolicy.isWebUrl(resourceUrl)) { "Unsupported image URL" }
    val filename = BrowserDownloadPolicy.safeDisplayName(URL(resourceUrl).path.substringAfterLast('/').ifBlank { "browser-image" })
    return VaultImportSource(
        displayName = filename,
        mimeType = URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream",
        openStream = {
            (URL(resourceUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", userAgent)
                referer?.takeIf(BrowserNavigationPolicy::isWebUrl)?.let { setRequestProperty("Referer", it) }
                CookieManager.getInstance().getCookie(resourceUrl)?.let { setRequestProperty("Cookie", it) }
                require(BrowserDownloadPolicy.acceptsResponse(url.toString(), responseCode)) { "Image response was rejected" }
            }.inputStream
        },
    )
}

/** Explicit fallback when element bounds are unavailable; this makes its viewport scope visible. */
private fun browserV2ViewportSource(view: android.webkit.WebView): VaultImportSource {
    val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    Canvas(bitmap).also(view::draw)
    val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
    bitmap.recycle()
    return VaultImportSource("browser-screenshot-${System.currentTimeMillis()}.png", "image/png", { ByteArrayInputStream(bytes) }, onConsumed = { bytes.fill(0) })
}
