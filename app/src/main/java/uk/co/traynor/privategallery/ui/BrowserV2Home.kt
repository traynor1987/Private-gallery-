@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package uk.co.traynor.privategallery.ui

import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.CookieManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import uk.co.traynor.privategallery.core.browser.v2.BrowserExternalResultGate
import uk.co.traynor.privategallery.core.browser.v2.BrowserScriptDialog
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
import uk.co.traynor.privategallery.core.browser.v2.MediaSaveReason
import uk.co.traynor.privategallery.core.browser.v2.streamVideoVaultSource
import uk.co.traynor.privategallery.core.browser.v2.MediaSaveCandidate
import uk.co.traynor.privategallery.core.browser.v2.MediaSaveKind
import java.util.concurrent.atomic.AtomicBoolean
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onLoadVaultPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException("Preview unavailable"))) },
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
            onLoadVaultPreview = onLoadVaultPreview,
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
    var pendingPermission by mutableStateOf<BrowserWebsitePermission?>(null)
    var pendingGeolocation by mutableStateOf<BrowserWebsitePermission?>(null)
    var permissionTabId:String?=null
    var permissionDocumentRevision:Long?=null
    var pendingFileResult by mutableStateOf<ValueCallback<Array<android.net.Uri>>?>(null)
    var deviceSelection:BrowserExternalResultGate.Owner<List<android.net.Uri>>?=null
    var devicePickerDeadline=0L
    var devicePickerStopExpected=false
    var devicePickerTimeout:kotlinx.coroutines.Job?=null
    var deferredDeviceResult:(()->Unit)?=null
    var fileFence:(()->Boolean)?=null
    var deviceValidationJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var fileAcceptTypes by mutableStateOf<Array<String>>(emptyArray())
    var fileMultiple by mutableStateOf(false)
    var fileOrigin by mutableStateOf("website")
    var vaultPickerOpen by mutableStateOf(false)
    var vaultPickerLoading by mutableStateOf(false)
    var vaultRestrictedCount by mutableIntStateOf(0)
    var vaultIncompatibleCount by mutableIntStateOf(0)
    var vaultIncompleteCount by mutableIntStateOf(0)
    var vaultPickerGeneration = 0L
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
    var downloadBusy by mutableStateOf(false)
    var downloadBytes by mutableLongStateOf(0)
    var downloadTotal by mutableLongStateOf(-1)
    var downloadCancelled = AtomicBoolean(false)
    var pendingImageResource by mutableStateOf<String?>(null)
    var pendingScreenshotFallback by mutableStateOf(false)
    var pendingScreenshotCapture by mutableStateOf<(() -> Unit)?>(null)
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
    onLoadVaultPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException("Preview unavailable"))) },
    onPrepareVaultUpload: (List<VaultItem>, (Result<List<android.net.Uri>>) -> Unit) -> Unit = { _, done -> done(Result.failure(IllegalStateException("Vault upload unavailable"))) },
    onClearVaultUpload: () -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val scrollChrome = remember(density) { uk.co.traynor.privategallery.core.browser.BrowserChromeScroll((48 * density).toInt().coerceAtLeast(1)) }
    val latestScrollChrome by rememberUpdatedState(scrollChrome)
    val ui = remember { BrowserUiState() }
    val context = LocalContext.current
    var privateApps by remember { mutableStateOf<uk.co.traynor.privategallery.core.privateapps.PrivateAppsController?>(null) }
    var privateAppSettings by remember { mutableStateOf<Pair<uk.co.traynor.privategallery.core.privateapps.PrivateAppsController,String>?>(null) }
    var privateAppSwitcher by remember { mutableStateOf(false) }


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
    val permissionActivity=context.playerActivity()
    val permissionOwners=remember(permissionActivity) {
        (permissionActivity as? androidx.lifecycle.ViewModelStoreOwner)?.let {
            androidx.lifecycle.ViewModelProvider(it)[BrowserPermissionOwners::class.java]
        } ?: BrowserPermissionOwners()
    }
    val videoLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val deviceValidationScope=androidx.compose.runtime.rememberCoroutineScope()
    val pickerContract=remember {BrowserDeviceDocumentContract(false)}
    val multiPickerContract=remember {BrowserDeviceDocumentContract(true)}
    val picker=rememberBrowserExternalLauncher(permissionOwners.deviceSingleKey,pickerContract,permissionOwners.device,permissionOwners.deviceSingleKey){uris ->uris.toList()}
    val multiPicker=rememberBrowserExternalLauncher(permissionOwners.deviceMultipleKey,multiPickerContract,permissionOwners.device,permissionOwners.deviceMultipleKey){uris ->uris.toList()}
    fun permissionFence():()->Boolean {
        val document=session.permissionDocumentRevision()
        val tab=session.tabs.activeTab.id
        val url=session.tabs.activeTab.url
        val view=session.activeWebViewOrNull()
        return {view!=null && session.mediaNetworkingAllowed() && session.permissionDocumentRevision()==document &&
            session.tabs.activeTab.id==tab && session.tabs.activeTab.url==url && session.permissionViewIsCurrent(tab,view)}
    }
    fun cancelUpload() {
        // Detach first: an old callback may synchronously request a new upload.
        val request=ui.pendingFileResult
        val selection=ui.deviceSelection
        val validation=ui.deviceValidationJob
        val timeout=ui.devicePickerTimeout
        ui.vaultPickerGeneration++
        ui.deviceValidationJob=null;ui.deviceSelection=null
        ui.devicePickerTimeout=null;ui.devicePickerDeadline=0L;ui.devicePickerStopExpected=false
        ui.deferredDeviceResult=null
        ui.pendingFileResult=null;ui.fileFence=null
        ui.vaultPickerOpen=false;ui.vaultPickerLoading=false;ui.vaultUploadChoice=false
        ui.selectedUploads=emptyList();ui.uploadBusy=false
        validation?.cancel();timeout?.cancel()
        onClearVaultUpload()
        session.recordAcceptanceUiEvent("UPLOAD_CANCELLED")
        session.recordAcceptanceUiEvent("UPLOAD_TEMP_CLEANED")
        selection?.cancel()
        request?.onReceiveValue(null)
    }
    fun launchDevicePicker() {
        val request=ui.pendingFileResult ?: return
        val generation=ui.vaultPickerGeneration
        val multiple=ui.fileMultiple
        val accept=ui.fileAcceptTypes.copyOf()
        val current=ui.fileFence ?: return
        if(!current() || BrowserUploadPreference.read(context)!=BrowserUploadPolicy.VAULT_AND_DEVICE){cancelUpload();return}
        val channel=if(multiple)permissionOwners.deviceMultipleKey else permissionOwners.deviceSingleKey
        lateinit var owner:BrowserExternalResultGate.Owner<List<android.net.Uri>>
        fun ownsRequest()=ui.pendingFileResult===request && ui.deviceSelection===owner
        fun finishDevice(values:Array<android.net.Uri>?) {
            // The wrapped callback checks its immutable fence before calling the website.
            // Cleanup afterwards is conditional: that call may synchronously create B.
            request.onReceiveValue(values)
            if(ownsRequest()) {
                ui.devicePickerTimeout?.cancel();ui.devicePickerTimeout=null
                ui.devicePickerDeadline=0L;ui.devicePickerStopExpected=false;ui.deferredDeviceResult=null
                ui.pendingFileResult=null;ui.fileFence=null;ui.deviceSelection=null;ui.vaultUploadChoice=false
            }
        }
        owner=BrowserExternalResultGate.Owner({uris ->
            fun valid()=ownsRequest() && ui.vaultPickerGeneration==generation &&
                android.os.SystemClock.elapsedRealtime()<ui.devicePickerDeadline &&
                videoLifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                current() && BrowserUploadPreference.read(context)==BrowserUploadPolicy.VAULT_AND_DEVICE
            val process:()->Unit = {
                if(!valid() || uris.isEmpty())finishDevice(null)
                else {
                    val job=deviceValidationScope.launch(start=kotlinx.coroutines.CoroutineStart.LAZY) {
                        val checked=try {validateBrowserDeviceSelection(context,uris.take(if(multiple)4 else 1),accept)}
                            catch(_:kotlinx.coroutines.TimeoutCancellationException){BrowserDeviceValidation(rejection="The document provider did not respond. Nothing was uploaded.")}
                            catch(_:kotlinx.coroutines.CancellationException){return@launch}
                        if(!valid()) {
                            if(ownsRequest())cancelUpload()
                            return@launch
                        }
                        ui.deviceValidationJob=null
                        checked.rejection?.let {ui.message=it}
                        finishDevice(checked.uris?.takeIf {it.isNotEmpty()}?.toTypedArray())
                    }
                    ui.deviceValidationJob=job;job.start()
                }
            }
            // Android may return before ON_START. Keep only this immutable owner's result;
            // resumption rechecks document, view, VPN, policy and the original deadline.
            if(ownsRequest() && !videoLifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                ui.deferredDeviceResult=process
            else process()
        },{finishDevice(null)})
        if(!permissionOwners.device.reserve(owner,channel)) {
            ui.message="Finish the current Android file selection, then choose again."
            return
        }
        ui.deviceSelection=owner;ui.vaultUploadChoice=false
        // A bounded exception for the explicit trusted document picker only. MainActivity's
        // privacy cover and authentication deadlines continue unchanged while it is open.
        ui.devicePickerDeadline=android.os.SystemClock.elapsedRealtime()+120_000L
        ui.devicePickerStopExpected=true
        ui.devicePickerTimeout=deviceValidationScope.launch {
            kotlinx.coroutines.delay(120_000L)
            if(ownsRequest())cancelUpload()
        }
        try {if(multiple)multiPicker.launch(accept) else picker.launch(accept)}
        catch(_:Exception){permissionOwners.device.launchFailed(owner);ui.message="Android file selection could not be opened. Try again."}
    }
    fun openVaultPicker() {
        ui.vaultUploadChoice = false
        ui.vaultItems = emptyList()
        ui.vaultPickerOpen = true
        ui.vaultPickerLoading = true
        ui.vaultRestrictedCount = 0
        ui.vaultIncompatibleCount = 0
        ui.vaultIncompleteCount = 0
        val pickerGeneration = ++ui.vaultPickerGeneration
        val acceptTypes = ui.fileAcceptTypes.copyOf()
        onLoadVaultItems { items ->
            if (ui.vaultPickerOpen && ui.vaultPickerGeneration == pickerGeneration) {
                ui.vaultRestrictedCount = items.count { it.vaultOnly }
                ui.vaultIncompleteCount = items.count { !it.vaultOnly && it.state != VaultItemState.COMPLETE }
                ui.vaultIncompatibleCount = items.count { !it.vaultOnly && it.state == VaultItemState.COMPLETE && !BrowserUploadPolicy.accepts(it.mimeType, acceptTypes) }
                ui.vaultItems = items.filter {
                    it.state == VaultItemState.COMPLETE && !it.vaultOnly && BrowserUploadPolicy.accepts(it.mimeType, acceptTypes)
                }
                ui.vaultPickerLoading = false
            }
        }
    }
    val capabilityRequests=remember {java.util.IdentityHashMap<PermissionRequest,BrowserWebsitePermission>()}
    val permissionContract=remember(context) {PrivacyPermissionContract(context.playerActivity() as? uk.co.traynor.privategallery.MainActivity)}
    DisposableEffect(permissionContract) {onDispose {permissionContract.close()}}
    val permissionLauncher=rememberBrowserExternalLauncher(permissionOwners.capabilityKey,permissionContract,permissionOwners.capability){it.toMap()}
    val locationContract=remember(context) {PrivacyPermissionContract(context.playerActivity() as? uk.co.traynor.privategallery.MainActivity)}
    DisposableEffect(locationContract) {onDispose {locationContract.close()}}
    val locationLauncher=rememberBrowserExternalLauncher(permissionOwners.locationKey,locationContract,permissionOwners.location){it.toMap()}
    fun cancelWebsitePermissions() {
        ui.pendingPermission?.decision?.cancel()
        ui.pendingGeolocation?.decision?.cancel()
    }
    fun originLabel(value:String):String? = runCatching {
        val uri=java.net.URI(value)
        if(uri.scheme !in setOf("https","http") || uri.host==null || uri.rawUserInfo!=null)null
        else "${uri.scheme}://${uri.host}${if(uri.port>=0)":"+uri.port else ""}"
    }.getOrNull()
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
    DisposableEffect(session, videoLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                cancelWebsitePermissions()
                val ownedPickerStop=ui.devicePickerStopExpected && ui.deviceSelection!=null &&
                    ui.deviceValidationJob==null && android.os.SystemClock.elapsedRealtime()<ui.devicePickerDeadline
                ui.devicePickerStopExpected=false
                if(ui.pendingFileResult!=null && !ownedPickerStop)cancelUpload()
                session.pauseForBackground()
                ui.webVideoView = false
                ui.internalMedia = null
                latestFullscreenChanged(false)
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) session.resumeForeground()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val deferred=ui.deferredDeviceResult
                ui.deferredDeviceResult=null
                deferred?.invoke()
            }
        }
        videoLifecycle.addObserver(observer)
        onDispose { videoLifecycle.removeObserver(observer) }
    }
    fun saveViewportScreenshot() {
        val activity=permissionActivity as? uk.co.traynor.privategallery.MainActivity
        val view=session.activeWebViewOrNull()
        val owner=activity?.captureBrowserScreenshotAuthority()
        val document=permissionFence()
        if(activity==null || view==null || owner==null){ui.message="Screenshot unavailable in this browser.";return}
        val current={owner() && document() && ui.fullscreen==null && !ui.webVideoView && !ui.overflow && !ui.settingsOpen && !ui.tabSwitcher && !ui.bookmarksOpen && !ui.historyOpen}
        ui.pendingScreenshotCapture={
            // Wait for the confirmation window to detach; never clear its secure flag.
            view.postDelayed({
                BrowserVaultScreenshot.capture(activity.window,view,true,current) {result ->
                    result.onSuccess {source ->latestSave(source){ui.message=it}}
                        .onFailure {ui.message=uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy.REFUSAL}
                }
            },150)
        }
    }


    DisposableEffect(session) {
        session.bindListener(object : BrowserV2Session.Listener {
            override fun onSessionChanged() {
                val tab=session.tabs.activeTab.id;val document=session.permissionDocumentRevision()
                if(ui.permissionTabId!=tab || ui.permissionDocumentRevision!=document) {
                    cancelWebsitePermissions()
                    if(ui.pendingFileResult!=null)cancelUpload()
                }
                ui.permissionTabId=tab;ui.permissionDocumentRevision=document;ui.revision++
            }
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
                if(ui.downloadBusy)return
                val mime=mimeType.substringBefore(';').trim().lowercase()
                if(!uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.acceptsDownload(mime)) {ui.message="Only complete images, videos, PDF, text and CSV documents can be saved.";return}
                val documentCurrent=permissionFence()
                val activity=context.playerActivity() as? uk.co.traynor.privategallery.MainActivity
                val network=activity?.hiddenOwnedVpnNetwork()
                if(network==null){ui.message="Connect Private Gallery VPN before downloading files into Vault.";return}
                val ownedActivity=checkNotNull(activity)
                val current={documentCurrent() && ownedActivity.hiddenOwnedVpnNetwork()==network}
                val cancellation=AtomicBoolean(false);ui.downloadCancelled=cancellation
                ui.downloadBusy=true;ui.downloadBytes=0;ui.downloadTotal=-1
                val handler=android.os.Handler(android.os.Looper.getMainLooper())
                var progressAt=0L
                val source=browserV2DownloadSource(url,userAgent,contentDisposition,mime,network,
                    {cancellation.get() || !current()},
                    {bytes,total ->val now=android.os.SystemClock.elapsedRealtime();if(now-progressAt>=100 || bytes==total || bytes==0L){progressAt=now;handler.post {if(ui.downloadCancelled===cancellation){ui.downloadBytes=bytes;ui.downloadTotal=total}}}},
                    {handler.post {if(ui.downloadCancelled===cancellation)ui.downloadBusy=false}})
                latestSave(source) {ui.downloadBusy=false;ui.message=it}
            }
            override fun onVideoSaveRequested() {
                if (ui.saveBusy) return
                session.requestSaveCandidate { candidate ->
                    ui.mediaCandidate = candidate
                    if (candidate?.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM)) ui.saveVideoDialog = true
                    else {
                        ui.message = "This video can be played here but can't be saved directly."
                        session.recordAcceptanceUiEvent("SAVE_TO_VAULT_FAILED", mapOf("category" to (candidate?.reason ?: MediaSaveReason.NO_MEDIA_CANDIDATE).name))
                    }
                }
            }
            override fun onImageLongPress(resourceUrl: String?) {
                if (resourceUrl != null && BrowserNavigationPolicy.isWebUrl(resourceUrl)) ui.pendingImageResource = resourceUrl
                else saveViewportScreenshot()
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
            override fun onPermissionRequest(request:PermissionRequest) {
                if(capabilityRequests[request]?.let {it===ui.pendingPermission}==true)return
                ui.pendingPermission?.decision?.cancel()
                val nativeOrigin=request.origin.toString()
                val origin=originLabel(nativeOrigin)
                if(origin==null){request.deny();return}
                val resources=request.resources.copyOf()
                val required=resources.mapNotNull {when(it){
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE->Manifest.permission.CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE->Manifest.permission.RECORD_AUDIO
                    else->null
                }}.distinct()
                val current=permissionFence()
                lateinit var handle:BrowserWebsitePermission
                val decision=BrowserExternalResultGate.Owner<Map<String,Boolean>>({grants ->
                    try {
                        if(ui.pendingPermission===handle && request.origin.toString()==nativeOrigin && current() && required.isNotEmpty() && required.all {grants[it]==true})request.grant(resources)
                        else request.deny()
                    } finally {capabilityRequests.remove(request);if(ui.pendingPermission===handle)ui.pendingPermission=null}
                },{
                    try {request.deny()} finally {capabilityRequests.remove(request);if(ui.pendingPermission===handle)ui.pendingPermission=null}
                })
                handle=BrowserWebsitePermission(origin,required,decision)
                ui.permissionTabId=session.tabs.activeTab.id;ui.permissionDocumentRevision=session.permissionDocumentRevision()
                ui.pendingPermission=handle;capabilityRequests[request]=handle
            }
            override fun onPermissionRequestCanceled(request:PermissionRequest) {
                // The wrapped native request has already completed. Match by immutable identity,
                // not by whichever new website dialog happens to be visible.
                capabilityRequests.remove(request)?.decision?.cancel()
            }
            override fun onGeolocationRequest(origin:String,callback:android.webkit.GeolocationPermissions.Callback) {
                ui.pendingGeolocation?.decision?.cancel()
                val label=originLabel(origin)
                if(label==null){callback.invoke(origin,false,false);return}
                val current=permissionFence()
                lateinit var handle:BrowserWebsitePermission
                val decision=BrowserExternalResultGate.Owner<Map<String,Boolean>>({grants ->
                    try {
                        val allow=ui.pendingGeolocation===handle && current() && (grants[Manifest.permission.ACCESS_FINE_LOCATION]==true || grants[Manifest.permission.ACCESS_COARSE_LOCATION]==true)
                        callback.invoke(origin,allow,false)
                    } finally {if(ui.pendingGeolocation===handle)ui.pendingGeolocation=null}
                },{try {callback.invoke(origin,false,false)}finally{if(ui.pendingGeolocation===handle)ui.pendingGeolocation=null}})
                handle=BrowserWebsitePermission(label,listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),decision)
                ui.permissionTabId=session.tabs.activeTab.id;ui.permissionDocumentRevision=session.permissionDocumentRevision()
                ui.pendingGeolocation=handle
            }
            override fun onShowFileChooser(callback: ValueCallback<Array<android.net.Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
                // Invalidate any older asynchronous Vault query before accepting a new request.
                cancelUpload()
                val requestedMode=params.mode
                val requestedPermission=params.permissionMode
                val requestedAccept=params.acceptTypes.copyOf()
                if(requestedMode !in setOf(WebChromeClient.FileChooserParams.MODE_OPEN,WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) || requestedPermission!=WebChromeClient.FileChooserParams.PERMISSION_MODE_READ) {
                    callback.onReceiveValue(null);ui.message="Only read-only file uploads are supported. Website file editing, saving and folder access are blocked.";return true
                }
                val current=permissionFence()
                val completed=AtomicBoolean()
                lateinit var wrapped:ValueCallback<Array<android.net.Uri>>
                wrapped=ValueCallback {values ->
                    if(completed.compareAndSet(false,true)) {
                        val valid=ui.pendingFileResult===wrapped && current() && BrowserUploadPreference.read(context)!=BrowserUploadPolicy.BLOCKED
                        callback.onReceiveValue(if(values==null || !valid)null else values)
                    }
                }
                ui.permissionTabId=session.tabs.activeTab.id;ui.permissionDocumentRevision=session.permissionDocumentRevision()
                ui.pendingFileResult=wrapped;ui.fileFence=current
                ui.fileAcceptTypes = requestedAccept
                ui.fileMultiple = requestedMode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                ui.fileOrigin = runCatching { java.net.URI(session.tabs.activeTab.url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "this website"
                session.recordAcceptanceUiEvent("FILE_CHOOSER_REQUEST")
                when (BrowserUploadPreference.read(context)) {
                    BrowserUploadPolicy.BLOCKED -> {
                        session.recordAcceptanceUiEvent("FILE_CHOOSER_POLICY", mapOf("policy" to "blocked"))
                        ui.message = "File uploads are blocked in Private Gallery Browser."
                        wrapped.onReceiveValue(null); ui.pendingFileResult = null;ui.fileFence=null
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
            ui.saveCancelled.set(true)
            ui.downloadCancelled.set(true)
            session.exitFullscreen()
            ui.fullscreen = null
            latestFullscreenChanged(false)
            cancelWebsitePermissions()
            capabilityRequests.clear()
            ui.pendingPermission = null
            cancelUpload()
            ui.pendingGeolocation = null
            session.pauseForBackground()
            session.bindListener(uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener)
        }
    }

    ui.internalMedia?.let { media ->
        BrowserInternalMediaDialog(media,session::mediaNetworkingAllowed,
            onClose={ui.internalMedia=null},
            onReady={session.pauseActiveMedia();session.recordMediaPath("MEDIA3_DIRECT")},
            onFallback={ui.internalMedia=null;openWebVideoView()})
    }

    // Deliberately read through revision so WebView callbacks update stable tab chrome.
    @Suppress("UNUSED_VARIABLE") val stateVersion = ui.revision
    val active = session.tabs.activeTab
    val activePrivateApp = session.privateApp(active.id)
    LaunchedEffect(active.id, active.url) {
        scrollChrome.reveal(); ui.chromeVisible = true
        if (ui.address != active.url) ui.address = active.url
        ui.webVideoView = false
        ui.internalMedia = null
        if (ui.fullscreen == null) latestFullscreenChanged(false)
    }
    LaunchedEffect(active.id, active.url, active.loading, connectionPresentation.blocked) {
        ui.mediaCandidate = null
        session.setVideoSaveAvailability(false)
        if (!active.loading && !connectionPresentation.blocked) while (true) {
            session.requestSaveCandidate {
                ui.mediaCandidate = it
                session.setVideoSaveAvailability(it?.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM))
            }
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
    fun openPrivateApps() {
        cancelUpload()
        privateApps=(context as? uk.co.traynor.privategallery.MainActivity)?.privateAppsController()
        if(privateApps==null)ui.message=(context as? uk.co.traynor.privategallery.MainActivity)?.privateAppProfileClearStatus() ?: "Unlock Primary and reopen the browser."
    }
    privateApps?.let {controller ->PrivateAppsLibrary(controller,{privateApps=null})}
    privateAppSettings?.let { (controller,id) ->
        PrivateAppSettings(controller,id,session::updatePrivateApp,{ui.overflow=true},{privateAppSettings=null})
    }
    if(privateAppSwitcher)PrivateAppSwitcher(
        session.tabs.tabs.mapNotNull { tab -> session.privateApp(tab.id)?.let { tab.id to it } },active.id,
        {cancelUpload();session.select(it)},::openPrivateApps,{privateAppSwitcher=false})

    Box(
        modifier = modifier.fillMaxSize()
            .then(if (acceptanceProbeEnabled) Modifier.background(Color(0xFF00A000)) else Modifier)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                session.recordAcceptanceUiEvent("BROWSER_ROOT_MEASURED", mapOf("x" to origin.x.toInt().toString(), "y" to origin.y.toInt().toString(), "width" to coordinates.size.width.toString(), "height" to coordinates.size.height.toString()))
            }
            .semantics { testTag = "browser-v2-root" },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
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
                (context as? uk.co.traynor.privategallery.MainActivity)?.privateAppProfileClearStatus()?.let {status ->
                    Text(status,Modifier.fillMaxWidth().padding(12.dp),color=MaterialTheme.colorScheme.error)
                }
                if(activePrivateApp!=null)PrivateAppControls(
                    app=activePrivateApp,url=active.url,
                    mode=(context as? uk.co.traynor.privategallery.MainActivity)?.privateAppConnectionLabel() ?: "Primary Vault",
                    enabled=!connectionPresentation.blocked,canGoBack=active.canGoBack,canGoForward=active.canGoForward,loading=active.loading,
                    onBack=session::goBackActive,onForward=session::goForwardActive,
                    onRefresh={if(active.loading)session.stopActive() else session.reloadActive()},
                    onHome={cancelUpload();session.navigateActive(activePrivateApp.startUrl())},
                    onLibrary=::openPrivateApps,onSwitch={cancelUpload();privateAppSwitcher=true},
                    onSettings={cancelUpload();(context as? uk.co.traynor.privategallery.MainActivity)?.privateAppsController()?.let {privateAppSettings=it to activePrivateApp.id()}},
                    onScreenshot=::saveViewportScreenshot)
                else if(context is uk.co.traynor.privategallery.MainActivity)TextButton(onClick=::openPrivateApps){Text("Private Apps")}

                if (acceptanceProbeEnabled) AcceptanceProbeLabel("BROWSER_CHROME", Color(0xFF0000CC))
                if (active.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(activePrivateApp==null || activePrivateApp.addressBar())BrowserAddressBar(ui.address, {
                    ui.address=it;session.recordAcceptanceUiEvent("OMNIBOX_TEXT_CHANGED")
                }, !connectionPresentation.blocked, active.loading, {
                    session.recordAcceptanceUiEvent("OMNIBOX_SUBMIT")
                    if(staticContentHost) {
                        session.recordAcceptanceUiEvent("OMNIBOX_SUBMIT_STATIC_HOST_IGNORED")
                        ui.message="Static content host is active. Switch to Real WebView before navigating."
                    } else runCatching {BrowserAddressPolicy.destinationFor(ui.address,searchEngine)}
                        .onSuccess {session.navigateActive(it.url)}.onFailure {ui.message="Enter a web address or search."}
                }, {if(active.loading)session.stopActive() else session.reloadActive()}, {
                    ui.addressFocused=it;session.recordAcceptanceUiEvent(if(it)"OMNIBOX_FOCUS_GAINED" else "OMNIBOX_FOCUS_CHANGED")
                })
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
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("BROWSER UNAVAILABLE", style = MaterialTheme.typography.titleMedium)
                        Text("The Android WebView provider could not start. Your tabs remain available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { session.retryActiveWebView() }) { Text("Retry") }
                    }
                }
                session.pageFailure(active.id)?.takeIf { !connectionPresentation.blocked }?.let { detail ->
                    BrowserPageError(detail,{session.reloadActive()},::openSettings)
                }
                if (acceptanceProbeEnabled) AcceptanceProbeLabel("CONTENT_HOST", Color(0xFFFFD800))
            }
            BrowserChromeBar(chromeFraction, top = false) {
                if(activePrivateApp!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton(onClick={ui.tabSwitcher=true}){Text("All tabs")}
                    TextButton(onClick={ui.overflow=true}){Text("Browser tools")}
                } else BrowserNavigationToolbar(!connectionPresentation.blocked && active.canGoBack,!connectionPresentation.blocked && active.canGoForward,session.tabs.tabs.size,
                    session::goBackActive,session::goForwardActive,{ui.bookmarksOpen=true},{ui.tabSwitcher=true},::openSettings,{ui.overflow=true})
            }
        }
        if (ui.webVideoView) {
            BrowserVideoWindow()
            IconButton(onClick = { ui.webVideoView = false; latestFullscreenChanged(false) }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .6f))) {
                Icon(Icons.Default.FullscreenExit, "Exit video view", tint = Color.White)
            }
            if (ui.mediaCandidate?.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM)) IconButton(onClick = { ui.saveVideoDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(24.dp))) {
                Icon(Icons.Default.FileDownload, "Save video to Vault", tint = Color.White)
            }
        }
        if(ui.downloadBusy) androidx.compose.material3.AlertDialog(
            onDismissRequest={ui.downloadCancelled.set(true);ui.downloadBusy=false},
            title={Text("Saving to Vault")},
            text={Column {Text(if(ui.downloadTotal>0) "${ui.downloadBytes / 1024} / ${ui.downloadTotal / 1024} KB" else "${ui.downloadBytes / 1024} KB received")
                Text("The encrypted copy appears after verification. Interrupted downloads can be retried from the page.")}},
            confirmButton={},dismissButton={TextButton(onClick={ui.downloadCancelled.set(true);ui.downloadBusy=false}) {Text("Cancel")}})
        if(ui.saveVideoDialog)BrowserSaveVideoDialog(ui.saveBusy,ui.saveProgress,ui.saveStage,ui.mediaCandidate?.mime,
            onSave=saveVideo@{
                val candidate = ui.mediaCandidate ?: return@saveVideo
                val userAgent = session.activeWebViewOrNull()?.settings?.userAgentString ?: return@saveVideo
                ui.saveCancelled = AtomicBoolean(false)
                ui.saveProgress = null
                ui.saveStage = "Downloading…"
                ui.saveBusy = true
                session.recordAcceptanceUiEvent("SAVE_TO_VAULT_STARTED")
                val failureReason = java.util.concurrent.atomic.AtomicReference<MediaSaveReason?>(null)
                val cancelled = { ui.saveCancelled.get() || !session.mediaNetworkingAllowed() }
                val progress: (Int?) -> Unit = { percent ->
                        ui.saveProgress = percent
                        if (percent == 100) { ui.saveProgress = null; ui.saveStage = "Checking encrypted copy…" }
                    }
                val validated: (uk.co.traynor.privategallery.core.browser.v2.VideoValidationFacts) -> Unit = { facts ->
                    session.recordAcceptanceUiEvent("MEDIA_VALIDATED", mapOf("bytes" to facts.bytes.toString(),
                        "duration_ms" to facts.durationMs.toString(), "video_tracks" to facts.videoTracks.toString(),
                        "audio_tracks" to facts.audioTracks.toString()))
                    ui.saveProgress = null; ui.saveStage = "Encrypting…"
                }
                val source = if (candidate.kind == MediaSaveKind.STREAM)
                    streamVideoVaultSource(context, candidate, userAgent, active.url, cancelled, progress,
                        { failureReason.set(it) }, validated)
                else uk.co.traynor.privategallery.core.browser.v2.validatedDirectVideoSource(context.cacheDir,
                    uk.co.traynor.privategallery.core.browser.v2.videoVaultSource(candidate, userAgent, active.url,
                        cancelled, progress, { ui.saveProgress = null; ui.saveStage = "Checking video…" }),
                    { failureReason.set(it) }, validated, candidate.expectedDurationMs)
                latestSave(source) { message ->
                    ui.saveBusy = false
                    ui.saveVideoDialog = false
                    ui.message = if (failureReason.get() != null && message !in setOf("Saved to Vault.", "Already in Vault.", "Vault save cancelled."))
                        "This video can be played here but can't be saved directly." else message
                    session.recordAcceptanceUiEvent(if (message == "Saved to Vault." || message == "Already in Vault.") "SAVE_TO_VAULT_COMPLETED" else "SAVE_TO_VAULT_FAILED",
                        if (message == "Saved to Vault." || message == "Already in Vault.") emptyMap() else mapOf("category" to (failureReason.get() ?: MediaSaveReason.MEDIA_REQUEST_FAILED).name))
                }
            },onCancel={ui.saveCancelled.set(true);ui.saveVideoDialog=false})
        if (ui.overflow) GalleryMenuSheet("Browser", onDismiss = { ui.overflow = false }) {
            SheetAction("Video view", Icons.Default.Fullscreen) { ui.overflow = false; openWebVideoView() }
            SheetAction("Save video to Vault", Icons.Default.FileDownload) {
                ui.overflow = false
                session.requestSaveCandidate { candidate ->
                    ui.mediaCandidate = candidate
                    if (candidate?.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM)) ui.saveVideoDialog = true
                    else { ui.message = "This video can be played here but can't be saved directly."
                        session.recordAcceptanceUiEvent("SAVE_TO_VAULT_FAILED", mapOf("category" to (candidate?.reason ?: MediaSaveReason.NO_MEDIA_CANDIDATE).name)) }
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
        if(ui.deviceValidationJob!=null)BrowserDeviceValidationDialog(::cancelUpload)
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
            GalleryDialogSurface(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(88.dp), modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Choose from Vault", style = MaterialTheme.typography.titleLarge)
                                Text(if (ui.fileMultiple) "Select up to four compatible items." else "Select one compatible item.", style = MaterialTheme.typography.bodySmall)
                                if (ui.vaultPickerLoading) {
                                    Text("Loading Vault media…", style = MaterialTheme.typography.bodyMedium)
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                } else if (ui.vaultItems.isEmpty()) {
                                    Text("No compatible Vault items. Vault-only restricted media cannot be uploaded.")
                                }
                                if (!ui.vaultPickerLoading) {
                                    if (ui.vaultRestrictedCount > 0) Text("${ui.vaultRestrictedCount} Vault-only item(s) cannot leave the Vault and cannot be uploaded.", style = MaterialTheme.typography.bodySmall)
                                    if (ui.vaultIncompatibleCount > 0) Text("${ui.vaultIncompatibleCount} item(s) do not match this website's accepted file types.", style = MaterialTheme.typography.bodySmall)
                                    if (ui.vaultIncompleteCount > 0) Text("${ui.vaultIncompleteCount} item(s) are not ready. Finish importing or recovering the transfer, then reopen this picker.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        items(ui.vaultItems, key = { it.id }) { item ->
                            BrowserVaultUploadTile(item, ui.selectedUploads.any { it.id == item.id }, onLoadVaultPreview) {
                                ui.selectedUploads = if (ui.selectedUploads.any { it.id == item.id }) ui.selectedUploads.filterNot { it.id == item.id }
                                    else if (ui.fileMultiple) (ui.selectedUploads + item).distinctBy { it.id }.take(4) else listOf(item)
                                session.recordAcceptanceUiEvent("VAULT_ITEM_SELECTED")
                            }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = ::cancelUpload) { Text("Cancel") }
                        Button(enabled = ui.selectedUploads.isNotEmpty(), onClick = { ui.vaultPickerOpen = false }) { Text("Continue") }
                    }
                }
            }
        }
        if (ui.selectedUploads.isNotEmpty() && !ui.vaultPickerOpen) AlertDialog(onDismissRequest = ::cancelUpload,
            title = { Text(if (ui.selectedUploads.size == 1) "Upload this file?" else "Upload these files?") },
            text = { Text("This sends a decrypted copy through the page at ${ui.fileOrigin}. An embedded upload service may receive it. Private Gallery cannot control how the destination stores or uses it.") },
            confirmButton = { TextButton(enabled = !ui.uploadBusy, onClick = {
                val request = ui.pendingFileResult ?: return@TextButton
                val generation = ui.vaultPickerGeneration
                ui.uploadBusy = true
                session.recordAcceptanceUiEvent("UPLOAD_CONFIRMED")
                onPrepareVaultUpload(ui.selectedUploads) { result ->
                    // Activity preparation owns cleanup of its obsolete operation. Never clear
                    // another request's callback, selection or temporary upload copies here.
                    if (ui.vaultPickerGeneration != generation || ui.pendingFileResult !== request) return@onPrepareVaultUpload
                    ui.uploadBusy = false
                    request.onReceiveValue(result.getOrNull()?.toTypedArray())
                    ui.pendingFileResult = null;ui.fileFence=null
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
        if(ui.findOpen)BrowserFindDialog(ui.findText,
            onValueChange={ui.findText=it;session.findInActivePage(it)},
            onNext=session::findNextInActivePage,onClose={session.clearFindInActivePage();ui.findOpen=false})
        if(ui.diagnosticsOpen && BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS)BrowserDiagnosticsDialog(
            session.acceptanceReport(),ui.diagnosticCaptureBusy,session.verboseDiagnosticsEnabled(),ui.diagnosticArmFailed,
            onArm={ui.diagnosticCaptureBusy=true;session.armAcceptanceInteraction {ready->ui.diagnosticCaptureBusy=false;ui.diagnosticArmFailed=!ready;if(ready)ui.diagnosticsOpen=false;ui.revision++}},
            onCapture={ui.diagnosticCaptureBusy=true;session.captureAcceptanceRuntime {ui.diagnosticCaptureBusy=false;ui.revision++}},
            onFocus={session.setAcceptanceFocusMode(it);ui.revision++},
            onToggleVerbose={session.setVerboseDiagnostics(!session.verboseDiagnosticsEnabled());ui.revision++},
            onCopy={context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Private Gallery Browser acceptance trace",session.acceptanceReport()))},
            onClear={session.clearAcceptanceReport();ui.revision++},onClose={ui.diagnosticsOpen=false})
        session.scriptDialog?.let { request ->
            androidx.compose.runtime.key(request) {
                var answer by remember { mutableStateOf(request.initialValue) }
                AlertDialog(onDismissRequest = { session.resolveScriptDialog(request, null) },
                    modifier = Modifier.semantics { testTag = "website-script-dialog" },
                    title = { Text(if (request.kind == BrowserScriptDialog.Kind.LEAVE) "Leave this page?" else "Website message") },
                    text = {
                        Text(request.origin, style = MaterialTheme.typography.labelLarge)
                        Text(request.message)
                        if (request.kind == BrowserScriptDialog.Kind.PROMPT) OutlinedTextField(answer, { answer = it.take(4000) }, label = { Text("Response") })
                    },
                    confirmButton = { TextButton(onClick = { session.resolveScriptDialog(request, answer) }) { Text(if (request.kind == BrowserScriptDialog.Kind.LEAVE) "Leave page" else "OK") } },
                    dismissButton = if (request.kind == BrowserScriptDialog.Kind.ALERT) null else ({ TextButton(onClick = { session.resolveScriptDialog(request, null) }) { Text(if (request.kind == BrowserScriptDialog.Kind.LEAVE) "Stay" else "Cancel") } }),
                )
            }
        }
        ui.message?.let {value->BrowserMessageDialog(value){ui.message=null}}
        ui.pendingPermission?.let {request->BrowserConsentDialog(BrowserConsentSurface.CAPABILITY,requestOrigin=request.origin,
            onAccept={
                val required=request.androidPermissions
                if(required.isEmpty())request.decision.cancel()
                else if(required.all {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED})request.decision.finish(required.associateWith {true})
                else if(permissionOwners.capability.reserve(request.decision)) {
                    try {permissionContract.launch(permissionLauncher,required.toTypedArray())}
                    catch(_:Exception){permissionOwners.capability.launchFailed(request.decision);ui.message="Android permission request could not be opened. Try again."}
                } else ui.message="Finish the current Android permission request, then try again."
            },onCancel={request.decision.cancel()})}
        ui.pendingGeolocation?.let {request->BrowserConsentDialog(BrowserConsentSurface.LOCATION,requestOrigin=request.origin,
            onAccept={
                val grants=request.androidPermissions.associateWith {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}
                if(grants.values.any {it})request.decision.finish(grants)
                else if(permissionOwners.location.reserve(request.decision)) {
                    try {locationContract.launch(locationLauncher,request.androidPermissions.toTypedArray())}
                    catch(_:Exception){permissionOwners.location.launchFailed(request.decision);ui.message="Android location request could not be opened. Try again."}
                } else ui.message="Finish the current Android location request, then try again."
            },onCancel={request.decision.cancel()})}
        ui.pendingImageResource?.let {resource->BrowserConsentDialog(BrowserConsentSurface.SAVE_IMAGE,
            onAccept={
                ui.pendingImageResource=null
                session.activeWebViewOrNull()?.let {view->latestSave(browserV2ImageSource(resource,view.settings.userAgentString,session.tabs.activeTab.url)){ui.message=it}}
                    ?:run {ui.message="Browser is unavailable. Retry before saving an image."}
            },onCancel={ui.pendingImageResource=null})}
        ui.pendingScreenshotCapture?.let {capture ->
            AlertDialog(onDismissRequest={ui.pendingScreenshotCapture=null},title={Text("Screenshot to Primary Vault?")},
                text={Text("Save only the visible website area encrypted in Primary Vault. Capture must be allowed in this Vault's settings. Video, embedded and protected content cannot be captured.")},
                confirmButton={TextButton(onClick={ui.pendingScreenshotCapture=null;capture()}){Text("Save screenshot")}},
                dismissButton={TextButton(onClick={ui.pendingScreenshotCapture=null}){Text("Cancel")}})
        }
        if(ui.pendingScreenshotFallback)BrowserConsentDialog(BrowserConsentSurface.SCREENSHOT,
            onAccept={ui.pendingScreenshotFallback=false;saveViewportScreenshot()},onCancel={ui.pendingScreenshotFallback=false})
        ui.pendingExternalNavigation?.let {value->
            val plan=remember(value){BrowserExternalNavigationPolicy.plan(context,value)}
            val choice=when {plan.openIntent!=null->BrowserExternalChoice.APP;plan.httpsFallback!=null->BrowserExternalChoice.WEBSITE;else->BrowserExternalChoice.UNSUPPORTED}
            BrowserExternalNavigationDialog(choice,onAccept={
                when(choice){
                    BrowserExternalChoice.APP->plan.openIntent?.let {intent->runCatching{context.startActivity(intent)}}
                    BrowserExternalChoice.WEBSITE->plan.httpsFallback?.let(session::navigateActive)
                    BrowserExternalChoice.UNSUPPORTED->Unit
                }
                ui.pendingExternalNavigation=null
            },onCancel={ui.pendingExternalNavigation=null})
        }
                ui.fullscreen?.takeUnless { connectionPresentation.blocked }?.let { (view, _) ->
                    BrowserVideoFullscreen(view, session::exitFullscreen,
                        ui.mediaCandidate?.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM)) {
                        if (ui.mediaCandidate?.kind in setOf(MediaSaveKind.DIRECT, MediaSaveKind.STREAM)) ui.saveVideoDialog = true
                    }
                }
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
    url: String, userAgent: String, contentDisposition: String, mimeType: String, network:android.net.Network,
    cancelled: () -> Boolean, progress: (Long,Long) -> Unit, consumed: () -> Unit,
): VaultImportSource {
    require(BrowserNavigationPolicy.isWebUrl(url)) { "Unsupported download URL" }
    val name = BrowserDownloadPolicy.safeDisplayName(contentDisposition.substringAfter("filename=", "download").trim().trim('"'))
    val capturedCookie=CookieManager.getInstance().getCookie(url)
    return VaultImportSource(name,mimeType,openStream={
        check(!cancelled())
        val client=uk.co.traynor.privategallery.core.privateapps.PrivateAppMetadataTransport.vpn(network){!cancelled()}
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).cache(null)
            .connectTimeout(15,java.util.concurrent.TimeUnit.SECONDS).readTimeout(15,java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(30,java.util.concurrent.TimeUnit.MINUTES).build()
        val call=client.newCall(okhttp3.Request.Builder().url(url).header("Accept-Encoding","identity")
            .header("User-Agent",userAgent).apply {capturedCookie?.let {header("Cookie",it)}}.build())
        val watch=java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val closed=AtomicBoolean(false)
        var response:okhttp3.Response?=null
        fun releaseDownload() {if(closed.compareAndSet(false,true)){watch.shutdownNow();call.cancel();response?.close();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}}
        watch.scheduleWithFixedDelay({if(runCatching{cancelled()}.getOrDefault(true))call.cancel()},0,100,java.util.concurrent.TimeUnit.MILLISECONDS)
        try {
            val reply=call.execute();response=reply
            require(reply.code==200 && reply.header("Content-Range")==null)
            val body=checkNotNull(reply.body)
            val actual=body.contentType()?.let {"${it.type}/${it.subtype}"}.orEmpty()
            require(actual==mimeType) {"Download type changed"}
            val expected=body.contentLength().takeIf {it>=0}
            val limit=if(uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.isDocument(mimeType))uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.maxBytes(mimeType) else 512L*1024*1024
            require(expected==null || expected in 1..limit)
            progress(0,expected ?: -1)
            object:java.io.FilterInputStream(uk.co.traynor.privategallery.core.browser.CompleteResponseInputStream(body.byteStream(),expected)) {
                var count=0L
                override fun read():Int {val one=ByteArray(1);return try {if(read(one,0,1)<0)-1 else one[0].toInt() and 255}finally {one.fill(0)}}
                override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                    check(!cancelled());val n=`in`.read(bytes,offset,length)
                    if(n>0) {count+=n;require(count<=limit);progress(count,expected ?: -1)}
                    check(!cancelled());return n
                }
                override fun close() {try {super.close()} finally {releaseDownload()}}
            }
        } catch(failure:Throwable) {releaseDownload();throw failure}
    },isCancelled=cancelled,onConsumed=consumed)
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
