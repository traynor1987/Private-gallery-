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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState
import uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy
import uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPreference
import uk.co.traynor.privategallery.core.browser.v2.BrowserMediaSavePolicy
import uk.co.traynor.privategallery.core.browser.v2.MediaSaveCandidate
import uk.co.traynor.privategallery.core.browser.v2.MediaSaveKind
import java.util.concurrent.atomic.AtomicBoolean
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
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
    onLoadVaultItems: ((List<VaultItem>) -> Unit) -> Unit = { it(emptyList()) },
    onPrepareVaultUpload: (List<VaultItem>, (Result<List<android.net.Uri>>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException("Vault upload unavailable"))) },
    onClearVaultUpload: () -> Unit = {},
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
            onLoadVaultItems = onLoadVaultItems,
            onPrepareVaultUpload = onPrepareVaultUpload,
            onClearVaultUpload = onClearVaultUpload,
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

/** Ephemeral UI state only. Kept together to bound Compose/D8 method register pressure. */
@androidx.compose.runtime.Stable
private class BrowserUiState {
    var chromeVisible by mutableStateOf(true)
    var addressFocused by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var webVideoView by mutableStateOf(false)
    var internalMedia by mutableStateOf<Pair<String, String>?>(null)
    var revision by mutableIntStateOf(0)
    var address by mutableStateOf("")
    var overflow by mutableStateOf(false)
    var tabSwitcher by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var fullscreen by mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null)
    var pendingPermission by mutableStateOf<PermissionRequest?>(null)
    var pendingGeolocation by mutableStateOf<Pair<String, android.webkit.GeolocationPermissions.Callback>?>(null)
    var pendingFileResult by mutableStateOf<ValueCallback<Array<android.net.Uri>>?>(null)
    var fileAcceptTypes by mutableStateOf<Array<String>>(emptyArray())
    var fileMultiple by mutableStateOf(false)
    var fileOrigin by mutableStateOf("website")
    var vaultPickerOpen by mutableStateOf(false)
    var vaultUploadChoice by mutableStateOf(false)
    var vaultItems by mutableStateOf<List<VaultItem>>(emptyList())
    var selectedUploads by mutableStateOf<List<VaultItem>>(emptyList())
    var uploadBusy by mutableStateOf(false)
    var mediaCandidate by mutableStateOf<MediaSaveCandidate?>(null)
    var saveVideoDialog by mutableStateOf(false)
    var saveProgress by mutableStateOf<Int?>(null)
    var saveStage by mutableStateOf("Downloading…")
    var saveBusy by mutableStateOf(false)
    var saveCancelled = AtomicBoolean(false)
    var pendingImageResource by mutableStateOf<String?>(null)
    var pendingScreenshotFallback by mutableStateOf(false)
    var bookmarksOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var history by mutableStateOf<List<BrowserHistoryEntry>>(emptyList())
    var findOpen by mutableStateOf(false)
    var findText by mutableStateOf("")
    var diagnosticsOpen by mutableStateOf(false)
    var diagnosticCaptureBusy by mutableStateOf(false)
    var diagnosticArmFailed by mutableStateOf(false)
    var pendingExternalNavigation by mutableStateOf<String?>(null)
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
    onLoadVaultItems: ((List<VaultItem>) -> Unit) -> Unit = { it(emptyList()) },
    onPrepareVaultUpload: (List<VaultItem>, (Result<List<android.net.Uri>>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException("Vault upload unavailable"))) },
    onClearVaultUpload: () -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scrollChrome = remember(density) { uk.co.traynor.privategallery.core.browser.BrowserChromeScroll((48 * density).toInt().coerceAtLeast(1)) }
    val latestScrollChrome by rememberUpdatedState(scrollChrome)
    val ui = remember { BrowserUiState() }
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val controlsPinned by rememberUpdatedState(ui.addressFocused || ui.overflow || ui.tabSwitcher || ui.bookmarksOpen || ui.historyOpen || ui.settingsOpen || ui.findOpen || ui.webVideoView || ui.fullscreen != null || connectionPresentation.blocked)
    fun openSettings() { if (browserSettings != null) ui.settingsOpen = true else onOpenBrowserSettings() }
    val chromeTarget = !ui.webVideoView && (ui.chromeVisible || controlsPinned)
    val chromeFraction by animateFloatAsState(if (chromeTarget) 1f else 0f, tween(220), label = "Browser controls")
    val chromeAnimating by rememberUpdatedState(chromeFraction != if (chromeTarget) 1f else 0f)
    LaunchedEffect(controlsPinned) { if (controlsPinned) { scrollChrome.reveal(); ui.chromeVisible = true } }
    val latestSave by rememberUpdatedState(onSaveToVault)
    val latestFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    val latestConnectionBlocked by rememberUpdatedState(connectionPresentation.blocked)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        ui.pendingFileResult?.onReceiveValue(uri?.let { arrayOf(it) })
        ui.pendingFileResult = null
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        ui.pendingFileResult?.onReceiveValue(uris.take(4).takeIf { it.isNotEmpty() }?.toTypedArray())
        ui.pendingFileResult = null
    }
    fun cancelUpload() {
        ui.pendingFileResult?.onReceiveValue(null)
        ui.pendingFileResult = null
        ui.vaultPickerOpen = false
        ui.vaultUploadChoice = false
        ui.selectedUploads = emptyList()
        ui.uploadBusy = false
        onClearVaultUpload()
        session.recordAcceptanceUiEvent("UPLOAD_CANCELLED")
        session.recordAcceptanceUiEvent("UPLOAD_TEMP_CLEANED")
    }
    fun launchDevicePicker() {
        ui.vaultUploadChoice = false
        val mime = ui.fileAcceptTypes.firstOrNull { it.isNotBlank() } ?: "*/*"
        if (ui.fileMultiple) multiPicker.launch(mime) else picker.launch(mime)
    }
    fun openVaultPicker() {
        ui.vaultUploadChoice = false
        ui.vaultItems = emptyList()
        ui.vaultPickerOpen = true
        onLoadVaultItems { items ->
            if (ui.vaultPickerOpen) ui.vaultItems = items.filter {
                it.state == VaultItemState.COMPLETE && !it.vaultOnly && BrowserUploadPolicy.accepts(it.mimeType, ui.fileAcceptTypes)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val request = ui.pendingPermission
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
        ui.pendingPermission = null
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        ui.pendingGeolocation?.let { (origin, callback) ->
            callback.invoke(origin, grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true, false)
        }
        ui.pendingGeolocation = null
    }
    fun openWebVideoView() {
        session.requestVideoView { available ->
            if (available) {
                ui.internalMedia = null
                ui.webVideoView = true
                latestFullscreenChanged(true)
                session.recordMediaPath("WEBVIEW_FULLSCREEN")
            } else {
                session.recordMediaPath("UNSUPPORTED")
                ui.message = "No active video view is available. Use the website’s own playback controls."
            }
        }
    }
    val videoLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, videoLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                session.pauseForBackground()
                ui.webVideoView = false
                ui.internalMedia = null
                latestFullscreenChanged(false)
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) session.resumeForeground()
        }
        videoLifecycle.addObserver(observer)
        onDispose { videoLifecycle.removeObserver(observer) }
    }
    fun saveViewportScreenshot() {
        runCatching { browserV2ViewportSource(requireNotNull(session.activeWebViewOrNull())) }
            .onSuccess { source -> latestSave(source) { ui.message = it } }
            .onFailure { ui.message = "Unable to capture the visible Browser page. The page is still open." }
    }

    DisposableEffect(session) {
        session.bindListener(object : BrowserV2Session.Listener {
            override fun onSessionChanged() { ui.revision++ }
            override fun onUserScroll(deltaY: Int, atTop: Boolean) {
                val accessibility = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                if (controlsPinned || accessibility?.isTouchExplorationEnabled == true) {
                    latestScrollChrome.reveal(); ui.chromeVisible = true
                } else if (!chromeAnimating) ui.chromeVisible = latestScrollChrome.onScroll(deltaY, atTop)
            }
            override fun onMessage(value: BrowserMessage) {
                ui.message = when (value) {
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
                latestSave(browserV2DownloadSource(url, userAgent, contentDisposition, mimeType)) { ui.message = it }
            }
            override fun onImageLongPress(resourceUrl: String?) {
                if (resourceUrl != null && BrowserNavigationPolicy.isWebUrl(resourceUrl)) ui.pendingImageResource = resourceUrl
                else ui.pendingScreenshotFallback = true
            }
            override fun onExternalNavigation(value: String) { ui.pendingExternalNavigation = value }
            override fun onHistoryVisit(title: String, url: String) = onHistoryVisited(title, url)
            override fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (latestConnectionBlocked) { callback.onCustomViewHidden(); return }
                ui.webVideoView = false
                ui.fullscreen = view to callback
                latestFullscreenChanged(true)
            }
            override fun onExitFullscreen() { ui.fullscreen = null; latestFullscreenChanged(false) }
            override fun onPermissionRequest(request: PermissionRequest) {
                ui.pendingPermission?.takeUnless { it === request }?.deny()
                ui.pendingPermission = request
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (ui.pendingPermission === request) ui.pendingPermission = null
            }
            override fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
                ui.pendingGeolocation?.let { (previousOrigin, previousCallback) -> previousCallback.invoke(previousOrigin, false, false) }
                ui.pendingGeolocation = origin to callback
            }
            override fun onShowFileChooser(callback: ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
                ui.pendingFileResult?.onReceiveValue(null)
                ui.pendingFileResult = callback
                ui.fileAcceptTypes = params.acceptTypes
                ui.fileMultiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                ui.fileOrigin = runCatching { java.net.URI(session.tabs.activeTab.url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "this website"
                session.recordAcceptanceUiEvent("FILE_CHOOSER_REQUEST")
                when (BrowserUploadPreference.read(context)) {
                    BrowserUploadPolicy.BLOCKED -> {
                        session.recordAcceptanceUiEvent("FILE_CHOOSER_POLICY", mapOf("policy" to "blocked"))
                        ui.message = "File uploads are blocked in Private Gallery Browser."
                        callback.onReceiveValue(null); ui.pendingFileResult = null
                    }
                    BrowserUploadPolicy.VAULT_ONLY -> {
                        session.recordAcceptanceUiEvent("FILE_CHOOSER_POLICY", mapOf("policy" to "vault_only"))
                        openVaultPicker()
                    }
                    BrowserUploadPolicy.VAULT_AND_DEVICE -> {
                        session.recordAcceptanceUiEvent("FILE_CHOOSER_POLICY", mapOf("policy" to "device_allowed"))
                        ui.vaultUploadChoice = true
                    }
                }
                return true
            }
        })
        onDispose {
            session.exitFullscreen()
            ui.fullscreen = null
            latestFullscreenChanged(false)
            ui.pendingPermission?.deny()
            ui.pendingPermission = null
            ui.pendingFileResult?.onReceiveValue(null)
            ui.pendingFileResult = null
            onClearVaultUpload()
            ui.pendingGeolocation?.let { (origin, callback) -> callback.invoke(origin, false, false) }
            ui.pendingGeolocation = null
            session.pauseForBackground()
            session.bindListener(uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener)
        }
    }

    ui.internalMedia?.let { media ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { ui.internalMedia = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val mediaItem = remember(media) { androidx.media3.common.MediaItem.Builder().setUri(media.first).setMimeType(media.second).build() }
            PrivateVideoPlayer(mediaItem, networkAllowed = session::mediaNetworkingAllowed, onClose = { ui.internalMedia = null },
                onReady = { session.pauseActiveMedia(); session.recordMediaPath("MEDIA3_DIRECT") },
                onWebViewFallback = { ui.internalMedia = null; openWebVideoView() })
        }
    }

    // Deliberately read through revision so WebView callbacks update stable tab chrome.
    @Suppress("UNUSED_VARIABLE") val stateVersion = ui.revision
    val active = session.tabs.activeTab
    LaunchedEffect(active.id, active.url) {
        scrollChrome.reveal(); ui.chromeVisible = true
        if (ui.address != active.url) ui.address = active.url
        ui.webVideoView = false
        ui.internalMedia = null
        if (ui.fullscreen == null) latestFullscreenChanged(false)
    }
    LaunchedEffect(active.id, active.url, active.loading, connectionPresentation.blocked) {
        ui.mediaCandidate = null
        if (!active.loading && !connectionPresentation.blocked) while (true) {
            session.requestSaveCandidate { ui.mediaCandidate = it }
            delay(2500)
        }
    }
    LaunchedEffect(connectionPresentation.blocked) {
        scrollChrome.reveal(); ui.chromeVisible = true
        if (connectionPresentation.blocked) {
            ui.internalMedia = null
            ui.webVideoView = false
            session.enforceNetworkPolicy()
            latestFullscreenChanged(false)
        } else if (videoLifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            session.resumeForeground()
        }
        if (connectionPresentation.blocked && ui.fullscreen != null) {
            ui.fullscreen?.second?.onCustomViewHidden()
            ui.fullscreen = null
            latestFullscreenChanged(false)
        }
    }
    BackHandler(enabled = onOpenGallery != null && !ui.addressFocused && !active.canGoBack && ui.fullscreen == null && !ui.webVideoView && ui.internalMedia == null) { onOpenGallery?.invoke() }
    BackHandler(enabled = ui.addressFocused) { focusManager.clearFocus(force = true) }
    BackHandler(enabled = ui.webVideoView) { ui.webVideoView = false; latestFullscreenChanged(false) }
    BackHandler(enabled = ui.fullscreen != null) {
        ui.fullscreen?.second?.onCustomViewHidden()
        ui.fullscreen = null
        latestFullscreenChanged(false)
    }
    BackHandler(enabled = ui.fullscreen == null && !ui.webVideoView && ui.internalMedia == null && !ui.addressFocused && active.canGoBack) { session.goBackActive() }

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
            BrowserChromeBar(chromeFraction, top = true) { Column(
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
                        value = ui.address,
                        onValueChange = {
                            ui.address = it
                            session.recordAcceptanceUiEvent("OMNIBOX_TEXT_CHANGED")
                        },
                        modifier = Modifier.weight(1f)
                            .onFocusChanged { focus -> ui.addressFocused = focus.isFocused; session.recordAcceptanceUiEvent(if (focus.isFocused) "OMNIBOX_FOCUS_GAINED" else "OMNIBOX_FOCUS_CHANGED") }
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
                                ui.message = "Static content host is active. Switch to Real WebView before navigating."
                            } else {
                                runCatching { BrowserAddressPolicy.destinationFor(ui.address, searchEngine) }
                                    .onSuccess { session.navigateActive(it.url) }
                                    .onFailure { ui.message = "Enter a web address or search." }
                            }
                        }),
                    )
                    IconButton(enabled = !connectionPresentation.blocked, modifier = Modifier.semantics { testTag = "browser-v2-reload" }, onClick = { if (active.loading) session.stopActive() else session.reloadActive() }) {
                        Icon(if (active.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (active.loading) "Stop" else "Reload")
                    }
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
                if (ui.mediaCandidate?.kind == MediaSaveKind.DIRECT && !ui.webVideoView && ui.fullscreen == null) {
                    Surface(modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp), shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shadowElevation = 4.dp) {
                        IconButton(onClick = { ui.saveVideoDialog = true }, modifier = Modifier.semantics { testTag = "browser-save-video" }) {
                            Icon(Icons.Default.FileDownload, "Save video to Vault")
                        }
                    }
                }
            }
            BrowserChromeBar(chromeFraction, top = false) { Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { testTag = "browser-v2-toolbar" },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !connectionPresentation.blocked && active.canGoBack, onClick = session::goBackActive) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    IconButton(enabled = !connectionPresentation.blocked && active.canGoForward, onClick = session::goForwardActive) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
                    IconButton(onClick = { ui.bookmarksOpen = true }) { Icon(Icons.Default.StarOutline, "Bookmarks") }
                    IconButton(modifier = Modifier.semantics { testTag = "browser-v2-tabs" }, onClick = { ui.tabSwitcher = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CropSquare, contentDescription = "Tabs", modifier = Modifier.size(32.dp))
                            Text(session.tabs.tabs.size.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = { ui.overflow = true }) { Icon(Icons.Default.Menu, "More") }
                }
            }
        }
        }
        if (ui.webVideoView) {
            BrowserVideoWindow()
            IconButton(onClick = { ui.webVideoView = false; latestFullscreenChanged(false) }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .6f))) {
                Icon(Icons.Default.FullscreenExit, "Exit video view", tint = Color.White)
            }
            if (ui.mediaCandidate?.kind == MediaSaveKind.DIRECT) IconButton(onClick = { ui.saveVideoDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(24.dp))) {
                Icon(Icons.Default.FileDownload, "Save video to Vault", tint = Color.White)
            }
        }
        if (ui.saveVideoDialog) AlertDialog(onDismissRequest = { if (!ui.saveBusy) ui.saveVideoDialog = false },
            title = { Text("Save video to Vault") },
            text = { Text(if (ui.saveBusy) ui.saveProgress?.let { "${ui.saveStage} $it%" } ?: ui.saveStage else
                "Save the detected ${ui.mediaCandidate?.mime ?: "video"} from this page as an encrypted Vault item?") },
            confirmButton = { if (!ui.saveBusy) TextButton(onClick = {
                val candidate = ui.mediaCandidate ?: return@TextButton
                val userAgent = session.activeWebViewOrNull()?.settings?.userAgentString ?: return@TextButton
                ui.saveCancelled = AtomicBoolean(false)
                ui.saveProgress = null
                ui.saveStage = "Downloading…"
                ui.saveBusy = true
                session.recordAcceptanceUiEvent("SAVE_TO_VAULT_STARTED")
                val source = uk.co.traynor.privategallery.core.browser.v2.videoVaultSource(candidate, userAgent, active.url,
                    { ui.saveCancelled.get() || !session.mediaNetworkingAllowed() }, { percent ->
                        ui.saveProgress = percent
                        if (percent == 100) { ui.saveProgress = null; ui.saveStage = "Checking encrypted copy…" }
                    }, { ui.saveProgress = null; ui.saveStage = "Checking encrypted copy…" })
                latestSave(source) { message ->
                    ui.saveBusy = false
                    ui.saveVideoDialog = false
                    ui.message = message
                    session.recordAcceptanceUiEvent(if (message == "Saved to Vault." || message == "Already in Vault.") "SAVE_TO_VAULT_COMPLETED" else "SAVE_TO_VAULT_FAILED", if (message == "Saved to Vault.") emptyMap() else mapOf("category" to "acquisition"))
                }
            }) { Text("Save to Vault") } },
            dismissButton = { TextButton(onClick = { ui.saveCancelled.set(true); ui.saveVideoDialog = false }) { Text("Cancel") } },
        )
        if (ui.overflow) GalleryMenuSheet("Browser", onDismiss = { ui.overflow = false }) {
            SheetAction("Video view", Icons.Default.Fullscreen) { ui.overflow = false; openWebVideoView() }
            SheetAction("Save video to Vault", Icons.Default.FileDownload) {
                ui.overflow = false
                session.requestSaveCandidate { candidate ->
                    ui.mediaCandidate = candidate
                    if (candidate?.kind == MediaSaveKind.DIRECT) ui.saveVideoDialog = true
                    else ui.message = "This video can be played here but can't be saved directly."
                }
            }
            SheetAction("Play in Private Gallery", Icons.Default.PlayCircle) {
                ui.overflow = false
                session.requestPlayableMedia { media ->
                    if (media == null) openWebVideoView()
                    else ui.internalMedia = media
                }
            }
            SheetAction("New tab", Icons.Default.Add) { ui.overflow = false; session.newTab() }
            SheetAction("Bookmark this page", Icons.Default.StarOutline) {
                ui.overflow = false
                if (active.url.isBlank()) ui.message = "Open an HTTP(S) page before bookmarking it."
                else onAddBookmark(active.title, active.url) { ui.message = it }
            }
            SheetAction("Bookmarks", Icons.Default.Bookmarks) { ui.overflow = false; ui.bookmarksOpen = true }
            SheetAction("History", Icons.Default.History) { ui.overflow = false; onLoadHistory { ui.history = it; ui.historyOpen = true } }
            SheetAction("Find in page", Icons.Default.Search) { ui.overflow = false; ui.findOpen = true }
            SheetAction("Screenshot to Vault", Icons.Default.Screenshot) { ui.overflow = false; saveViewportScreenshot() }
            SheetAction(if (active.desktopSite) "Mobile site" else "Desktop site", Icons.Default.Computer) { ui.overflow = false; session.setDesktopSite(active.id, !active.desktopSite) }
            if (session.contentBlocker.enabled && uk.co.traynor.privategallery.core.browser.v2.BrowserSecurityPolicy.allowsNavigation(active.url)) {
                val bypassed = session.contentBlocker.isSiteBypassed(active.url)
                SheetAction(if (bypassed) "Turn blocking on for this site" else "Turn blocking off for this site", Icons.Default.Shield) {
                    ui.overflow = false
                    session.contentBlocker.setSiteBypassed(active.url, !bypassed)
                    session.reloadActive()
                    ui.message = if (bypassed) "Blocking on for this site." else "Blocking off for this site until the app closes."
                }
            }
            SheetAction("Browser settings", Icons.Default.Settings) { ui.overflow = false; openSettings() }
            if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) SheetAction("Browser diagnostics", Icons.Default.BugReport) { ui.overflow = false; ui.diagnosticsOpen = true }
            if (onOpenGallery != null || onOpenVault != null || onOpenFavourite != null) {
                SheetSection("Private Gallery")
                if (onOpenGallery != null) SheetAction("Gallery", Icons.Default.PhotoLibrary) { ui.overflow = false; onOpenGallery() }
                if (onOpenVault != null) SheetAction("Vault", Icons.Default.Lock) { ui.overflow = false; onOpenVault() }
                if (onOpenFavourite != null) SheetAction(favouriteLabel, Icons.Default.Favorite) { ui.overflow = false; onOpenFavourite() }
                SheetAction("App settings", Icons.Default.Settings) { ui.overflow = false; onOpenBrowserSettings() }
            }
        }
        if (ui.settingsOpen && browserSettings != null) BrowserPanel("Browser settings", "Search, privacy and connection", { ui.settingsOpen = false }) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) { browserSettings() }
        }
        if (ui.vaultUploadChoice) AlertDialog(onDismissRequest = ::cancelUpload,
            title = { Text("Choose file for website") },
            text = { Text("Choose explicitly from your encrypted Vault or from your device.") },
            confirmButton = { TextButton(onClick = ::openVaultPicker) { Text("Choose from Vault") } },
            dismissButton = { Row {
                TextButton(onClick = ::launchDevicePicker) { Text("Choose from device") }
                TextButton(onClick = ::cancelUpload) { Text("Cancel") }
            } },
        )
        if (ui.vaultPickerOpen) androidx.compose.ui.window.Dialog(onDismissRequest = ::cancelUpload,
            properties = androidx.compose.ui.window.DialogProperties(securePolicy = androidx.compose.ui.window.SecureFlagPolicy.Inherit)) {
            Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose from Vault", style = MaterialTheme.typography.titleLarge)
                    Text(if (ui.fileMultiple) "Select up to four compatible items." else "Select one compatible item.", style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (ui.vaultItems.isEmpty()) Text("No compatible Vault items.")
                        ui.vaultItems.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(ui.selectedUploads.any { it.id == item.id }, onCheckedChange = { checked ->
                                    ui.selectedUploads = if (checked) {
                                        if (ui.fileMultiple) (ui.selectedUploads + item).distinctBy { it.id }.take(4) else listOf(item)
                                    } else ui.selectedUploads.filterNot { it.id == item.id }
                                    session.recordAcceptanceUiEvent("VAULT_ITEM_SELECTED")
                                })
                                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = ::cancelUpload) { Text("Cancel") }
                        TextButton(enabled = ui.selectedUploads.isNotEmpty(), onClick = { ui.vaultPickerOpen = false }) { Text("Continue") }
                    }
                }
            }
        }
        if (ui.selectedUploads.isNotEmpty() && !ui.vaultPickerOpen) AlertDialog(onDismissRequest = ::cancelUpload,
            title = { Text(if (ui.selectedUploads.size == 1) "Upload this file?" else "Upload these files?") },
            text = { Text("This sends a decrypted copy through the page at ${ui.fileOrigin}. An embedded upload service may receive it. Private Gallery cannot control how the destination stores or uses it.") },
            confirmButton = { TextButton(enabled = !ui.uploadBusy, onClick = {
                ui.uploadBusy = true
                session.recordAcceptanceUiEvent("UPLOAD_CONFIRMED")
                onPrepareVaultUpload(ui.selectedUploads) { result ->
                    ui.uploadBusy = false
                    if (ui.pendingFileResult == null) { onClearVaultUpload(); return@onPrepareVaultUpload }
                    ui.pendingFileResult?.onReceiveValue(result.getOrNull()?.toTypedArray())
                    ui.pendingFileResult = null
                    ui.selectedUploads = emptyList()
                    if (result.isFailure) { ui.message = "Could not prepare this Vault upload."; onClearVaultUpload(); session.recordAcceptanceUiEvent("UPLOAD_TEMP_CLEANED") }
                }
            }) { Text(if (ui.uploadBusy) "Preparing…" else "Upload") } },
            dismissButton = { TextButton(onClick = ::cancelUpload) { Text("Cancel") } },
        )
        if (ui.tabSwitcher) BrowserTabsPanel(session.tabs.tabs, active.id,
            onSelect = { session.select(it); ui.tabSwitcher = false }, onRemove = session::close,
            onNew = { session.newTab(); ui.tabSwitcher = false }, onClose = { ui.tabSwitcher = false })
        if (ui.bookmarksOpen) BrowserBookmarksPanel(bookmarks,
            onOpen = { session.navigateActive(it); ui.bookmarksOpen = false }, onRemove = onRemoveBookmark,
            onClose = { ui.bookmarksOpen = false })
        if (ui.historyOpen) BrowserHistoryPanel(ui.history, saveHistory,
            onOpen = { session.navigateActive(it); ui.historyOpen = false },
            onClear = { onClearHistory { ui.history = emptyList() } }, onClose = { ui.historyOpen = false })
        if (ui.findOpen) AlertDialog(
            onDismissRequest = { session.clearFindInActivePage(); ui.findOpen = false }, title = { Text("Find in page") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = ui.findText, onValueChange = { ui.findText = it; session.findInActivePage(it) }, singleLine = true, placeholder = { Text("Find text") })
                Row { TextButton(onClick = { session.findNextInActivePage(false) }) { Text("Previous") }; TextButton(onClick = { session.findNextInActivePage(true) }) { Text("Next") } }
            } }, confirmButton = { TextButton(onClick = { session.clearFindInActivePage(); ui.findOpen = false }) { Text("Close") } },
        )
        if (ui.diagnosticsOpen && BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) AlertDialog(
            onDismissRequest = { ui.diagnosticsOpen = false }, title = { Text("Browser diagnostics") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Clear removes current-session events. The previous-process fatal report is retained.", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !ui.diagnosticCaptureBusy && session.verboseDiagnosticsEnabled(), onClick = {
                    ui.diagnosticCaptureBusy = true
                    session.armAcceptanceInteraction { ready -> ui.diagnosticCaptureBusy = false; ui.diagnosticArmFailed = !ready; if (ready) ui.diagnosticsOpen = false; ui.revision++ }
                }) { Text("Arm next interaction") }
                TextButton(enabled = !ui.diagnosticCaptureBusy && session.verboseDiagnosticsEnabled(), onClick = {
                    ui.diagnosticCaptureBusy = true
                    session.captureAcceptanceRuntime { ui.diagnosticCaptureBusy = false; ui.revision++ }
                }) { Text(if (ui.diagnosticCaptureBusy) "Capturing…" else "Capture diagnostic snapshot") }
                if (ui.diagnosticArmFailed) Text("Could not arm this page. Wait for loading to finish and try again.")
                Text("Arm before the failing tap. Capture afterwards, then Copy all. Structure only; no browsing content.", style = MaterialTheme.typography.bodySmall)
                Text("Browser compatibility test", style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = { session.setAcceptanceFocusMode(BrowserFocusMode.CURRENT); ui.revision++ }) { Text("Current") }
                    TextButton(onClick = { session.setAcceptanceFocusMode(BrowserFocusMode.EXPLICIT_WEBVIEW_FOCUS); ui.revision++ }) { Text("Explicit WebView focus") }
                }
                TextButton(onClick = { session.setVerboseDiagnostics(!session.verboseDiagnosticsEnabled()); ui.revision++ }) {
                    Text(if (session.verboseDiagnosticsEnabled()) "Verbose diagnostics: on" else "Verbose diagnostics: off")
                }
                Text(session.acceptanceReport(), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { ui.diagnosticsOpen = false }) { Text("Close") } },
            dismissButton = { Row {
                TextButton(enabled = !ui.diagnosticCaptureBusy, onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Private Gallery Browser acceptance trace", session.acceptanceReport())) }) { Text("Copy all") }
                TextButton(onClick = { session.clearAcceptanceReport(); ui.revision++ }) { Text("Clear current session") }
            } },
        )
        ui.message?.let { value -> AlertDialog(onDismissRequest = { ui.message = null }, text = { Text(value) }, confirmButton = { TextButton(onClick = { ui.message = null }) { Text("OK") } }) }
        ui.pendingPermission?.let { request -> AlertDialog(
            onDismissRequest = { request.deny(); ui.pendingPermission = null },
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
                if (required.isEmpty()) { request.deny(); ui.pendingPermission = null }
                else if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) { request.grant(request.resources); ui.pendingPermission = null }
                else permissionLauncher.launch(required.toTypedArray())
            }) { Text("Allow this time") } },
            dismissButton = { TextButton(onClick = { request.deny(); ui.pendingPermission = null }) { Text("Deny") } },
        ) }
        ui.pendingGeolocation?.let { (origin, callback) -> AlertDialog(
            onDismissRequest = { callback.invoke(origin, false, false); ui.pendingGeolocation = null },
            title = { Text("Website location") },
            text = { Text("Allow this website to use your location for this session?") },
            confirmButton = { TextButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) { callback.invoke(origin, true, false); ui.pendingGeolocation = null }
                else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Allow this time") } },
            dismissButton = { TextButton(onClick = { callback.invoke(origin, false, false); ui.pendingGeolocation = null }) { Text("Deny") } },
        ) }
        ui.pendingImageResource?.let { resource -> AlertDialog(
            onDismissRequest = { ui.pendingImageResource = null },
            title = { Text("Save image to Vault") },
            text = { Text("Save the image resource currently made available to this Browser session?") },
            confirmButton = { TextButton(onClick = {
                ui.pendingImageResource = null
                session.activeWebViewOrNull()?.let { view ->
                    latestSave(browserV2ImageSource(resource, view.settings.userAgentString, session.tabs.activeTab.url)) { ui.message = it }
                } ?: run { ui.message = "Browser is unavailable. Retry before saving an image." }
            }) { Text("Save to Vault") } },
            dismissButton = { TextButton(onClick = { ui.pendingImageResource = null }) { Text("Cancel") } },
        ) }
        if (ui.pendingScreenshotFallback) AlertDialog(
            onDismissRequest = { ui.pendingScreenshotFallback = false },
            title = { Text("Displayed image") },
            text = { Text("This visual has no safe image resource. Save the visible Browser viewport as a screenshot instead?") },
            confirmButton = { TextButton(onClick = {
                ui.pendingScreenshotFallback = false
                saveViewportScreenshot()
            }) { Text("Screenshot to Vault") } },
            dismissButton = { TextButton(onClick = { ui.pendingScreenshotFallback = false }) { Text("Cancel") } },
        )
        ui.pendingExternalNavigation?.let { value ->
            val plan = remember(value) { BrowserExternalNavigationPolicy.plan(context, value) }
            AlertDialog(
                onDismissRequest = { ui.pendingExternalNavigation = null },
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
                            ui.pendingExternalNavigation = null
                        }) { Text("Open") }
                        plan.httpsFallback != null -> TextButton(onClick = {
                            session.navigateActive(plan.httpsFallback)
                            ui.pendingExternalNavigation = null
                        }) { Text("Open website") }
                        else -> TextButton(onClick = { ui.pendingExternalNavigation = null }) { Text("OK") }
                    }
                },
                dismissButton = { TextButton(onClick = { ui.pendingExternalNavigation = null }) { Text("Cancel") } },
            )
        }
                ui.fullscreen?.takeUnless { connectionPresentation.blocked }?.let { (view, _) -> BrowserVideoFullscreen(view, session::exitFullscreen) }
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
