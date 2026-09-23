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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    acceptanceProbeEnabled: Boolean = BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS,
    staticContentHost: Boolean = BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS,
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
    acceptanceProbeEnabled: Boolean = BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS,
    staticContentHost: Boolean = BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS,
) {
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
    var staticHostSelected by remember { mutableStateOf(staticContentHost) }
    var pendingExternalNavigation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val latestSave by rememberUpdatedState(onSaveToVault)
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
    fun saveViewportScreenshot() {
        runCatching { browserV2ViewportSource(requireNotNull(session.activeWebViewOrNull())) }
            .onSuccess { source -> latestSave(source) { message = it } }
            .onFailure { message = "Unable to capture the visible Browser page. The page is still open." }
    }

    DisposableEffect(session) {
        session.bindListener(object : BrowserV2Session.Listener {
            override fun onSessionChanged() { revision++ }
            override fun onMessage(value: BrowserMessage) {
                message = when (value) {
                    BrowserMessage.VpnRequired -> "VPN is required before Browser networking can begin."
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
            override fun onFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) { fullscreen = view to callback }
            override fun onExitFullscreen() { fullscreen = null }
            override fun onPermissionRequest(request: PermissionRequest) { pendingPermission = request }
            override fun onGeolocationRequest(origin: String, callback: android.webkit.GeolocationPermissions.Callback) { pendingGeolocation = origin to callback }
            override fun onShowFileChooser(callback: ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
                pendingFileResult?.onReceiveValue(null)
                pendingFileResult = callback
                picker.launch(params.acceptTypes.firstOrNull { !it.isNullOrBlank() } ?: "*/*")
                return true
            }
        })
        onDispose { session.bindListener(uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener) }
    }

    // Deliberately read through revision so WebView callbacks update stable tab chrome.
    @Suppress("UNUSED_VARIABLE") val stateVersion = revision
    val active = session.tabs.activeTab
    LaunchedEffect(active.id, active.url) { if (address != active.url) address = active.url }
    BackHandler(enabled = fullscreen != null) { fullscreen?.second?.onCustomViewHidden() }
    BackHandler(enabled = fullscreen == null && active.canGoBack) { session.goBackActive() }

    // Obtain the Android view after the listener is bound, but never let a provider failure abort
    // the surrounding Compose tree. The V2 chrome is the useful recovery surface.
    val activeWebView = if (staticHostSelected) null else session.activeWebViewOrNull()
    LaunchedEffect(session, staticHostSelected) {
        if (!staticHostSelected) session.recordAcceptanceUiEvent("REAL_MODE_ENTERED")
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
            Column(
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
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("PRIVATE GALLERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("BROWSER", style = MaterialTheme.typography.titleLarge)
                }
                if (active.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(enabled = active.canGoBack, onClick = session::goBackActive) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    IconButton(enabled = active.canGoForward, onClick = session::goForwardActive) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
                    TextField(
                        value = address,
                        onValueChange = {
                            address = it
                            session.recordAcceptanceUiEvent("OMNIBOX_TEXT_CHANGED")
                        },
                        modifier = Modifier.weight(1f)
                            .onFocusChanged { focus -> session.recordAcceptanceUiEvent(if (focus.isFocused) "OMNIBOX_FOCUS_GAINED" else "OMNIBOX_FOCUS_CHANGED") }
                            .semantics { testTag = "browser-v2-address" },
                        singleLine = true,
                        placeholder = { Text("Search or enter address") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = {
                            session.recordAcceptanceUiEvent("OMNIBOX_SUBMIT")
                            if (staticHostSelected) {
                                session.recordAcceptanceUiEvent("OMNIBOX_SUBMIT_STATIC_HOST_IGNORED")
                                message = "Static content host is active. Switch to Real WebView before navigating."
                            } else {
                                runCatching { BrowserAddressPolicy.destinationFor(address, searchEngine) }
                                    .onSuccess { session.navigateActive(it.url) }
                                    .onFailure { message = "Enter a web address or search." }
                            }
                        }),
                    )
                    IconButton(modifier = Modifier.semantics { testTag = "browser-v2-reload" }, onClick = { if (active.loading) session.stopActive() else session.reloadActive() }) {
                        Icon(if (active.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (active.loading) "Stop" else "Reload")
                    }
                    IconButton(modifier = Modifier.semantics { testTag = "browser-v2-tabs" }, onClick = { tabSwitcher = true }) { Text(session.tabs.tabs.size.toString()) }
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, "More") }
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
                // A key changes attachment only when selected-tab identity changes.
                if (staticHostSelected) {
                    Column(
                        Modifier.fillMaxSize().semantics { testTag = "browser-v2-static-content-host" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("BROWSER CONTENT HOST")
                    }
                } else if (activeWebView != null) {
                    androidx.compose.runtime.key(active.id) {
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
        }
        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
            if (acceptanceProbeEnabled) {
                DropdownMenuItem(
                    text = { Text(if (staticHostSelected) "Switch to REAL WebView" else "Switch to STATIC host") },
                    onClick = { overflow = false; staticHostSelected = !staticHostSelected },
                )
                if (!staticHostSelected) DropdownMenuItem(
                    text = { Text("Load local WebView test page") },
                    onClick = { overflow = false; session.loadAcceptanceLocalTestPage() },
                )
            }
            DropdownMenuItem(text = { Text("New tab") }, onClick = { overflow = false; session.newTab() })
            DropdownMenuItem(text = { Text("Bookmark this page") }, onClick = {
                overflow = false
                if (active.url.isBlank()) message = "Open an HTTP(S) page before bookmarking it."
                else onAddBookmark(active.title, active.url) { message = it }
            })
            DropdownMenuItem(text = { Text("Bookmarks") }, onClick = { overflow = false; bookmarksOpen = true })
            DropdownMenuItem(text = { Text("History") }, onClick = { overflow = false; onLoadHistory { history = it; historyOpen = true } })
            DropdownMenuItem(text = { Text("Find in page") }, onClick = { overflow = false; findOpen = true })
            DropdownMenuItem(text = { Text("Screenshot to Vault") }, onClick = {
                overflow = false
                saveViewportScreenshot()
            })
            if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) DropdownMenuItem(text = { Text("Browser diagnostics") }, onClick = { overflow = false; diagnosticsOpen = true })
            DropdownMenuItem(text = { Text(if (saveHistory) "Save browsing history: on" else "Save browsing history: off") }, onClick = { onSaveHistoryChanged(!saveHistory) })
            DropdownMenuItem(text = { Text(if (active.desktopSite) "Mobile site" else "Desktop site") }, leadingIcon = { Icon(Icons.Filled.Computer, null) }, onClick = { overflow = false; session.setDesktopSite(active.id, !active.desktopSite) })
            DropdownMenuItem(text = { Text("Browser settings") }, onClick = { overflow = false; onOpenBrowserSettings() })
        }
        if (tabSwitcher) AlertDialog(
            onDismissRequest = { tabSwitcher = false },
            title = { Text("Tabs") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                session.tabs.tabs.forEach { tab -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { session.select(tab.id); tabSwitcher = false }, modifier = Modifier.weight(1f)) { Text(tab.title.ifBlank { "New tab" }, maxLines = 1) }
                    IconButton(onClick = { session.close(tab.id) }) { Icon(Icons.Filled.Close, "Close tab") }
                } }
                TextButton(onClick = { session.newTab(); tabSwitcher = false }) { Icon(Icons.Filled.Add, null); Text("New tab") }
            } }, confirmButton = { TextButton(onClick = { tabSwitcher = false }) { Text("Close") } },
        )
        if (bookmarksOpen) AlertDialog(
            onDismissRequest = { bookmarksOpen = false }, title = { Text("Bookmarks") },
            text = { Column(Modifier.widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bookmarks.isEmpty()) Text("No bookmarks yet.")
                bookmarks.forEach { bookmark -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { session.navigateActive(bookmark.url); bookmarksOpen = false }, modifier = Modifier.weight(1f)) { Column { Text(bookmark.title, maxLines = 1); Text(java.net.URI(bookmark.url).host ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1) } }
                    IconButton(onClick = { onRemoveBookmark(bookmark.id) }) { Icon(Icons.Filled.Close, "Remove bookmark") }
                } }
            } }, confirmButton = { TextButton(onClick = { bookmarksOpen = false }) { Text("Close") } },
        )
        if (historyOpen) AlertDialog(
            onDismissRequest = { historyOpen = false }, title = { Text("History") },
            text = { Column(Modifier.widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (history.isEmpty()) Text("No saved history.")
                history.forEach { entry ->
                    TextButton(onClick = {
                        session.navigateActive(entry.url)
                        historyOpen = false
                    }) {
                        Column {
                            Text(entry.title, maxLines = 1)
                            Text(
                                java.net.URI(entry.url).host ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { onClearHistory { history = emptyList() } }) { Text("Clear history") } },
        )
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
                Text(session.acceptanceReport(), style = MaterialTheme.typography.bodySmall)
                Text("Browser compatibility test", style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = { session.setAcceptanceFocusMode(BrowserFocusMode.CURRENT) }) { Text("Current") }
                    TextButton(onClick = { session.setAcceptanceFocusMode(BrowserFocusMode.EXPLICIT_WEBVIEW_FOCUS) }) { Text("Explicit WebView focus") }
                }
                TextButton(onClick = { session.setVerboseDiagnostics(!session.verboseDiagnosticsEnabled()) }) {
                    Text(if (session.verboseDiagnosticsEnabled()) "Verbose diagnostics: on" else "Verbose diagnostics: off")
                }
            } },
            confirmButton = { TextButton(onClick = { diagnosticsOpen = false }) { Text("Close") } },
            dismissButton = { Row {
                TextButton(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Private Gallery Browser acceptance trace", session.acceptanceReport())) }) { Text("Copy all") }
                TextButton(onClick = { session.clearAcceptanceReport() }) { Text("Clear") }
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
                fullscreen?.let { (view, _) -> AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
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
