package uk.co.traynor.privategallery

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import uk.co.traynor.privategallery.ui.MediaHeader
import uk.co.traynor.privategallery.ui.GalleryMenuSheet
import uk.co.traynor.privategallery.ui.SheetAction
import uk.co.traynor.privategallery.ui.SheetSection
import uk.co.traynor.privategallery.ui.GalleryChoiceRow
import uk.co.traynor.privategallery.ui.GalleryLoadingState
import uk.co.traynor.privategallery.ui.BrowserConnectionPresentation
import androidx.compose.foundation.selection.selectableGroup
import uk.co.traynor.privategallery.ui.ThumbnailDensity
import uk.co.traynor.privategallery.ui.ThumbnailSizeChoices
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import uk.co.traynor.privategallery.ui.VaultBrowseControls
import uk.co.traynor.privategallery.ui.MediaSortChoices
import uk.co.traynor.privategallery.core.ui.MediaKindFilter
import uk.co.traynor.privategallery.core.ui.MediaSort
import uk.co.traynor.privategallery.core.ui.VaultBrowsePolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedButton
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import uk.co.traynor.privategallery.core.vault.AndroidVaultRepository
import uk.co.traynor.privategallery.core.vault.ImportResult
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultCollection
import uk.co.traynor.privategallery.core.vault.ImageEditState
import uk.co.traynor.privategallery.core.vault.NormalizedCrop
import uk.co.traynor.privategallery.core.ui.VaultSummary
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.ui.ThemePreference
import uk.co.traynor.privategallery.core.update.GithubReleaseUpdateService
import uk.co.traynor.privategallery.core.update.UpdateCheck
import uk.co.traynor.privategallery.core.update.ReleaseMetadata
import uk.co.traynor.privategallery.core.update.UpdateInstallPolicy
import uk.co.traynor.privategallery.core.crypto.InvalidPinException
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.core.security.AutoLockPreference
import uk.co.traynor.privategallery.core.security.LockSession
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.BiometricVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryKeySetupPolicy
import uk.co.traynor.privategallery.core.crypto.InvalidRecoveryKeyException
import uk.co.traynor.privategallery.core.security.ScreenPrivacyPreference
import uk.co.traynor.privategallery.core.security.BiometricPromptPolicy
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import uk.co.traynor.privategallery.ui.BrowserDiagnosticsSettings
import uk.co.traynor.privategallery.ui.BrowserV2ProductionDestination
import uk.co.traynor.privategallery.ui.FullscreenMediaViewer
import uk.co.traynor.privategallery.ui.ViewerMediaEntry
import uk.co.traynor.privategallery.ui.GalleryCard
import uk.co.traynor.privategallery.ui.GalleryEmptyState
import uk.co.traynor.privategallery.ui.GalleryFeedback
import uk.co.traynor.privategallery.ui.GallerySettingsRow
import uk.co.traynor.privategallery.ui.GalleryCardHeading
import uk.co.traynor.privategallery.ui.GalleryPageTitle
import uk.co.traynor.privategallery.ui.GallerySectionLabel
import uk.co.traynor.privategallery.ui.GalleryTokens
import uk.co.traynor.privategallery.ui.BrowserHome
import uk.co.traynor.privategallery.ui.BrowserCallbackBindings
import uk.co.traynor.privategallery.ui.VaultImageEdits
import uk.co.traynor.privategallery.core.ui.SettingsCategory
import uk.co.traynor.privategallery.core.ui.SettingsSections
import uk.co.traynor.privategallery.core.ui.SettingsLayoutPolicy
import uk.co.traynor.privategallery.core.ui.MediaViewerSource
import uk.co.traynor.privategallery.core.ui.AppNavigationDestination
import uk.co.traynor.privategallery.core.ui.AppNavigationPolicy
import uk.co.traynor.privategallery.core.ui.VaultPreviewPolicy
import uk.co.traynor.privategallery.core.ui.ProtectedMediaTilePolicy
import uk.co.traynor.privategallery.core.gallery.DeviceGalleryPolicy
import uk.co.traynor.privategallery.core.gallery.DeviceGalleryRepository
import uk.co.traynor.privategallery.core.gallery.DeviceAlbum
import uk.co.traynor.privategallery.core.gallery.DeviceMediaItem
import uk.co.traynor.privategallery.core.gallery.DeviceMediaKind
import uk.co.traynor.privategallery.core.browser.BrowserNavigationPolicy
import uk.co.traynor.privategallery.core.browser.BrowserDiagnosticsPolicy
import uk.co.traynor.privategallery.core.browser.BrowserWebViewLifecycleEvent
import uk.co.traynor.privategallery.core.browser.BrowserWebViewLifecyclePolicy
import uk.co.traynor.privategallery.core.browser.BrowserBookmark
import uk.co.traynor.privategallery.core.browser.EncryptedBookmarkStore
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.vault.VaultImportCoordinator
import uk.co.traynor.privategallery.core.vpn.BrowserVpnController
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2FatalCrashCapture
import uk.co.traynor.privategallery.core.browser.v2.BrowserVpnGate
import uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener
import uk.co.traynor.privategallery.core.vpn.VpnProfileRepository
import uk.co.traynor.privategallery.core.vpn.VpnProfileSummary
import uk.co.traynor.privategallery.core.vpn.OwnedVpnTunnelRegistry
import uk.co.traynor.privategallery.core.vpn.OfficialWireGuardBackend
import uk.co.traynor.privategallery.core.vpn.VpnConnectionState
import uk.co.traynor.privategallery.core.vpn.WireGuardVpnEngine

/** Supplies a transient decrypted buffer to Android's frame extractor without creating a file. */
private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= bytes.size) return -1
        val count = minOf(size, bytes.size - position.toInt())
        bytes.copyInto(buffer, destinationOffset = offset, startIndex = position.toInt(), endIndex = position.toInt() + count)
        return count
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}

class MainActivity : FragmentActivity() {
    private var vaultCamera by mutableStateOf<uk.co.traynor.privategallery.core.camera.VaultCameraController?>(null)
    internal var cameraVolumeAction: (() -> Unit)? = null
    internal var cameraRevision by mutableStateOf(0)
        private set
    private val cameraRecoveryOwners=mutableSetOf<String>()
    private val cameraRecoveryAgain=mutableSetOf<String>()
    private fun captureAccess(hidden: uk.co.traynor.privategallery.core.hidden.HiddenSession?): uk.co.traynor.privategallery.core.camera.VaultCaptureAccess? {
        if (hideContent || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return null
        if (hidden != null) { if (!retained.hidden.selected || hidden !== retained.hidden.session || !hidden.isUnlocked) return null }
        else if (!session.isUnlocked || sessionKey == null || retained.hidden.selected) return null
        val primaryIdentity = sessionKey
        return uk.co.traynor.privategallery.core.camera.VaultCaptureAccess(applicationContext, hidden,
            primaryKey = { sessionKey?.copyOf() }, authorized = {
                !hideContent && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                if (hidden != null) retained.hidden.selected && hidden.isUnlocked else !retained.hidden.selected && session.isUnlocked && sessionKey != null && sessionKey === primaryIdentity
            })
    }
    internal fun openVaultCamera(hidden: uk.co.traynor.privategallery.core.hidden.HiddenSession? = null) {
        if(vaultCamera!=null)return
        val access=captureAccess(hidden) ?: return
        vaultCamera=uk.co.traynor.privategallery.core.camera.VaultCameraController(applicationContext,access,::lock)
    }
    internal fun recoverVaultCamera(hidden: uk.co.traynor.privategallery.core.hidden.HiddenSession? = null) {
        if(vaultCamera!=null)return
        val access=captureAccess(hidden) ?: return
        if(!cameraRecoveryOwners.add(access.domain)){access.revoke();cameraRecoveryAgain.add(access.domain);return}
        lifecycleScope.launch {
            try {
                val saved=withContext(Dispatchers.IO){
                    var count=0
                    for(id in access.journal.entries()) {
                        if(!access.allowed())break
                        runCatching {access.recover(id).use {ticket->
                            if(ticket.hash==null)access.journal.seal(ticket,false,0)
                            else if(ticket.plain.exists())access.journal.seal(ticket,ticket.complete,ticket.duration)
                            if(ticket.complete){access.import(ticket);count++}
                        }}
                    }
                    count
                }
                if(saved>0&&access.allowed())cameraRevision++
            } finally {
                access.revoke();cameraRecoveryOwners.remove(access.domain)
                if(cameraRecoveryAgain.remove(access.domain))recoverVaultCamera(if(retained.hidden.selected)retained.hidden.session else null)
            }
        }
    }
    private fun closeVaultCamera() { cameraVolumeAction = null; vaultCamera?.revoke(); vaultCamera = null; cameraRevision++ }
    private fun exitVaultCamera() {
        closeVaultCamera()
        recoverVaultCamera(if(retained.hidden.selected)retained.hidden.session else null)
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && cameraVolumeAction != null) {
            if (event.repeatCount == 0) cameraVolumeAction?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && cameraVolumeAction != null) return true
        return super.onKeyUp(keyCode, event)
    }
    override fun onPause() {
        if (vaultCamera?.entered == true) lock()
        super.onPause()
    }

    internal var hiddenBrowserOwner: uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserRuntime? = null
    internal fun hiddenWireGuardReleased(): Boolean = !wireGuardEngine.ownsTunnel && wireGuardEngine.state == VpnConnectionState.DISCONNECTED
    internal fun hiddenOwnedVpnNetwork(): android.net.Network? {
        if (android.os.Build.VERSION.SDK_INT < 30 || !wireGuardEngine.ownsTunnel || wireGuardEngine.state != VpnConnectionState.CONNECTED) return null
        val manager = getSystemService(android.net.ConnectivityManager::class.java)
        return manager.allNetworks.singleOrNull { network ->
            manager.getNetworkCapabilities(network)?.let { it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) && it.ownerUid == android.os.Process.myUid() } == true
        }
    }
    internal fun prepareHiddenBrowserTransport(mode: uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy.Mode) {
        if (!retained.hidden.selected || !retained.hidden.session.isUnlocked || route != Route.BROWSER) return
        browserVpnDisconnectJob?.cancel()
        browserV2Session.destroyAll()
        browserWebView?.apply { stopLoading(); destroy() }; browserWebView = null
        if (mode == uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy.Mode.TOR_ONLY) {
            browserVpnController.onLock(); return
        }
        if (mode != uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy.Mode.VPN_ONLY) return
        val owner = hiddenBrowserOwner ?: return
        val key = sessionKey?.copyOf() ?: run {
            // Primary may expire while Android shows VPN permission. Its already selected
            // in-memory WireGuard configuration survives controller lock; no vault key is reused.
            if (browserVpnController.hasSelectedProfile) requestBrowserVpnPermissionOrConnect()
            else if (hiddenOwnedVpnNetwork() == null) owner.connectionPreparationFailed()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnProfileLoader.load(File(filesDir,"vpn-profiles"),key)
            withContext(Dispatchers.Main) {
                if (hiddenBrowserOwner === owner && owner.mode == mode && retained.hidden.selected && retained.hidden.session.isUnlocked && route == Route.BROWSER) {
                    if (profile == null) owner.connectionPreparationFailed()
                    else { browserVpnController.select(profile); requestBrowserVpnPermissionOrConnect() }
                }
            }
        }
    }

    private val browserUploadFiles = mutableListOf<File>()
    @Volatile private var browserUploadGeneration = 0L

    private fun clearBrowserUploadCopies() {
        browserUploadGeneration++
        browserUploadFiles.forEach { it.delete() }
        browserUploadFiles.clear()
        File(cacheDir, "browser-upload").listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun prepareBrowserUpload(items: List<VaultItem>, onComplete: (Result<List<Uri>>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val key = sessionKey?.copyOf() ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        val generation = browserUploadGeneration
        lifecycleScope.launch(Dispatchers.IO) {
            val files = mutableListOf<File>()
            val result = runCatching {
                require(items.size in 1..4) { "Select up to four items" }
                require(items.sumOf { it.plaintextSize } <= 256L * 1024 * 1024) { "Upload selection exceeds size limit" }
                val directory = File(cacheDir, "browser-upload").apply { mkdirs() }
                val repository = AndroidVaultRepository(applicationContext, key)
                items.map { item ->
                    if (hideContent || generation != browserUploadGeneration || !session.isUnlocked) throw java.io.IOException("Upload cancelled")
                    val folder = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                    val file = File(folder, uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.safeName(item.mimeType))
                    files += file
                    repository.prepareBrowserUpload(item, file) { hideContent || generation != browserUploadGeneration || !session.isUnlocked }
                    FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                }
            }
            key.fill(0)
            runOnUiThread {
                if (hideContent || generation != browserUploadGeneration || !session.isUnlocked || result.isFailure) {
                    files.forEach { it.delete(); it.parentFile?.delete() }
                    onComplete(Result.failure(result.exceptionOrNull() ?: java.io.IOException("Upload cancelled")))
                } else {
                    browserUploadFiles.addAll(files)
                    onComplete(result)
                    lifecycleScope.launch {
                        delay(5 * 60 * 1000L)
                        if (generation == browserUploadGeneration) clearBrowserUploadCopies()
                    }
                }
            }
        }
    }
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) lock()
        }
    }
    private val previewCacheLock = Any()
    private val previewMemory = object : android.util.LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) previewKeys.remove(key.substringBefore(":"), key)
        }
    }
    private val previewJobs = mutableSetOf<Job>()
    private val previewKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val previewEpochs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun invalidatePreview(id: String) {
        previewEpochs[id] = (previewEpochs[id] ?: 0L) + 1L
        previewKeys.remove(id)?.let { previewMemory.remove(it) }
    }
    private val previewDisk by lazy { uk.co.traynor.privategallery.core.media.EncryptedPreviewCache(File(filesDir, "vault/previews")) }
    private val deviceGallery by lazy { DeviceGalleryRepository(applicationContext) }
    private lateinit var keys: PinVaultKeyStore
    private lateinit var biometrics: BiometricVaultKeyStore
    private lateinit var recoveryKeys: RecoveryVaultKeyStore
    private lateinit var appSettings: android.content.SharedPreferences
    private val retained by lazy { androidx.lifecycle.ViewModelProvider(this)[ProtectedSessionState::class.java] }
    private val session get() = retained.session
    private var route by mutableStateOf(Route.LOCK)
    private var biometricEnabled by mutableStateOf(false)
    private var biometricAvailable by mutableStateOf(false)
    private var autoLockTimeout by mutableStateOf(AutoLockTimeout.IMMEDIATELY)
    private var appTheme by mutableStateOf(AppTheme.SYSTEM)
    private var allowScreenshots by mutableStateOf(false)
    private var hideContent by mutableStateOf(false)
    @Volatile private var hideGate = false
    private var browserPresentationGeneration = 0L
    private var secretDiscovered by mutableStateOf(false)
    private var sensitivePrompt: BiometricPrompt? = null
    private var sensitiveGeneration = 0L
    private var primaryCopyAccess: uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess? = null
    private fun createPrimaryCopy(hidden: uk.co.traynor.privategallery.core.hidden.HiddenSession): uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess? {
        primaryCopyAccess?.close()
        if (!session.isUnlocked || sessionKey == null || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return null
        return runCatching { uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess(applicationContext, hidden) }.getOrNull()?.also { primaryCopyAccess = it }
    }
    private var updateStatus by mutableStateOf("Not checked")
    private var updateLastChecked by mutableStateOf("Never")
    private var availableUpdate by mutableStateOf<ReleaseMetadata?>(null)
    private var browserSearchEngine by mutableStateOf(BrowserSearchEngine.GOOGLE)
    private var clearBrowserDataOnLock by mutableStateOf(false)
    private var browserSaveHistory by mutableStateOf(false)
    private var browserAutoConnectVpn by mutableStateOf(false)
    private var browserRequireVpn by mutableStateOf(false)
    private var browserVpnPreparing by mutableStateOf(false)
    private var browserVpnPermissionRequired by mutableStateOf(false)
    private var browserVpnState by mutableStateOf(VpnConnectionState.UNCONFIGURED)
    private var vpnProfileStatus by mutableStateOf("")
    private var vpnProfiles by mutableStateOf<List<VpnProfileSummary>>(emptyList())
    private var browserWebView: WebView? = null
    /** V2 owns its tab WebViews independently of Compose and of the legacy single-view field. */
    private val browserV2Session by lazy {
        BrowserV2Session(
            // WebView needs an Activity context for platform UI (fullscreen, picker and grants).
            // The session, rather than Compose, remains its owner for this Activity lifetime.
            appContext = this@MainActivity,
            vpnGate = BrowserVpnGate { browserVpnController.browserNetworkingAllowed(browserRequireVpn) },
            contentBlocker = uk.co.traynor.privategallery.core.browser.v2.BrowserContentBlocker { enabled ->
                appSettings.edit().putBoolean("browser-content-blocking", enabled).apply()
            },
            listener = NoopBrowserV2Listener,
            onMetadataChanged = ::saveBrowserSessionMetadata,
        )
    }
    private var browserBookmarks by mutableStateOf<List<BrowserBookmark>>(emptyList())
    private var browserFullscreenExit: (() -> Unit)? = null
    private var mediaAccessAvailable by mutableStateOf(false)
    private var backupExportUri by mutableStateOf<Uri?>(null)
    private var backupRestoreUri by mutableStateOf<Uri?>(null)
    private var backupStatus by mutableStateOf("")
    private var sessionKey: ByteArray?
        get() = retained.key
        set(value) { retained.key = value }
    /** Held only while the user is being shown the newly-created offline secret. */
    private var pendingRecoveryKey: CharArray? = null
    private var pendingSourceDeletion: List<Uri> = emptyList()
    private var phoneMoveBusy = false
    private var phoneMoveStatus by mutableStateOf("")
    private var pendingVerifiedPhoneCopies=0
    private val phoneTransferGate=Any()
    @Volatile private var phoneTransferGeneration=0L
    private fun revokePhoneTransfers() {synchronized(phoneTransferGate) {phoneTransferGeneration++}}
    /** Completion is retained only for the platform deletion confirmation round-trip. */
    private var pendingSourceDeletionCompletion: ((String) -> Unit)? = null
    private val wireGuardEngine by lazy { WireGuardVpnEngine(OfficialWireGuardBackend(applicationContext)) }
    private val browserVpnController by lazy { BrowserVpnController(wireGuardEngine) }
    private var browserVpnDisconnectJob: Job? = null
    private var automaticBiometricPromptAttempted: Boolean
        get() = retained.automaticBiometricPromptAttempted
        set(value) { retained.automaticBiometricPromptAttempted = value }
    private val generationJobs = mutableSetOf<Job>()
    private val biometricAttempts = uk.co.traynor.privategallery.core.security.BiometricUnlockAttemptState()
    private var activeBiometricPrompt: BiometricPrompt? = null
    private fun newBiometricPrompt(purpose: BiometricPurpose): BiometricPrompt {
        activeBiometricPrompt?.cancelAuthentication()
        val attempt = biometricAttempts.begin()
        return BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!biometricAttempts.isCurrent(attempt)) return
                biometricAttempts.cancel(attempt)
                activeBiometricPrompt = null
                if(isFinishing||isDestroyed||!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return
                val cipher = result.cryptoObject?.cipher ?: return
                runCatching {
                    when (purpose) {
                        BiometricPurpose.UNLOCK -> {
                            val unwrapped = biometrics.unwrapAuthenticated(cipher)
                            if (!(retained.primaryGallery && retained.hidden.selected && retained.hidden.session.isUnlocked)) retained.hidden.leave()
                            sessionKey?.fill(0)
                            sessionKey = unwrapped
                            session.unlock()
                            reconcileAfterUnlock()
                            route = if (prepareRecoveryKeyIfNeeded()) Route.RECOVERY_KEY_SETUP else retained.afterAuthentication
                        }
                        BiometricPurpose.ENROLL -> {
                            val key = checkNotNull(sessionKey)
                            biometrics.saveAuthenticated(cipher, key)
                            biometricEnabled = true
                        }
                    }
                }.onFailure { android.util.Log.w("PGAuth", "Biometric ${purpose.name.lowercase()} failed: ${it.javaClass.simpleName}") }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!biometricAttempts.isCurrent(attempt)) return
                biometricAttempts.cancel(attempt)
                activeBiometricPrompt = null
            }
        }).also { activeBiometricPrompt = it }
    }
    private val sourceDeletionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> finishPhoneSourceDeletion(result.resultCode) }

    private fun finishPhoneSourceDeletion(resultCode: Int) {
        val pending = pendingSourceDeletion
        val completion = pendingSourceDeletionCompletion
        val verified = pendingVerifiedPhoneCopies
        pendingVerifiedPhoneCopies=0
        pendingSourceDeletion = emptyList()
        pendingSourceDeletionCompletion = null
        phoneMoveBusy = false
        if(pending.isEmpty()) {
            phoneMoveStatus="Deletion confirmation ended after interruption. Counts are unavailable; check Gallery and Vault. No further deletion was requested."
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val removed = pending.count { resultCode == RESULT_OK && sourceNoLongerExists(it.toString()) }
            withContext(Dispatchers.Main) {
                phoneMoveStatus="$verified verified copies; $removed originals removed. Remaining originals were retained."
                completion?.invoke(phoneMoveStatus)
            }
        }
    }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaAccessAvailable = hasDeviceMediaAccess()
    }
    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        backupExportUri = uri
    }
    private val backupRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        backupRestoreUri = uri
    }
    private var hiddenVpnPermissionMode: uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy.Mode? = null
    private var vpnPermissionWasHidden = false
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val expectedMode = hiddenVpnPermissionMode
        val wasHidden = vpnPermissionWasHidden
        hiddenVpnPermissionMode = null; vpnPermissionWasHidden = false
        if (wasHidden && (expectedMode != uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy.Mode.VPN_ONLY ||
            !retained.hidden.selected || !retained.hidden.session.isUnlocked || route != Route.BROWSER ||
            getSharedPreferences("hidden-browser-v16", MODE_PRIVATE).getString("mode", null) != "VPN_ONLY")) return@registerForActivityResult
        if (!wasHidden && retained.hidden.selected) return@registerForActivityResult
        browserVpnPermissionRequired = result.resultCode != RESULT_OK
        if (result.resultCode == RESULT_OK) {
            // Backgrounding retired the old runtime. Its foreground replacement prepares this
            // same transport again; only a current authenticated VPN owner may connect here.
            if (!wasHidden || hiddenBrowserOwner?.mode == expectedMode) connectBrowserVpn()
        } else {
            browserVpnState = VpnConnectionState.FAILED
            if(wasHidden)hiddenBrowserOwner?.connectionPermissionDenied()
        }
    }
    private val vpnProfileDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = sessionKey?.copyOf() ?: return@registerForActivityResult
        if (uri == null) { key.fill(0); return@registerForActivityResult }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Unable to read selected profile")
                require(text.length <= 256 * 1024) { "Profile is too large" }
                val repository = VpnProfileRepository(File(filesDir, "vpn-profiles"), key)
                repository.import(uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "WireGuard", text).also { imported ->
                    if (imported is uk.co.traynor.privategallery.core.vpn.VpnProfileImportResult.Accepted) repository.select(imported.profile.id)
                }
            }
            key.fill(0)
            runOnUiThread {
                vpnProfileStatus = when (result.getOrNull()) {
                    is uk.co.traynor.privategallery.core.vpn.VpnProfileImportResult.Accepted -> "WireGuard profile imported and selected."
                    is uk.co.traynor.privategallery.core.vpn.VpnProfileImportResult.Rejected -> "Profile was rejected."
                    null -> "Unable to import WireGuard profile."
                }
                refreshVpnProfiles()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clearBrowserUploadCopies()
        File(cacheDir, "browser-video").deleteRecursively()
        File(cacheDir, "hidden-download-validation").deleteRecursively() // interrupted plaintext exports only
        ContextCompat.registerReceiver(this, screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        uk.co.traynor.privategallery.core.editor.AiProviderRegistry.initialize(applicationContext)
        if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) {
            BrowserV2FatalCrashCapture.install(applicationContext)
        }
        keys = PinVaultKeyStore(this)
        biometrics = BiometricVaultKeyStore(this)
        recoveryKeys = RecoveryVaultKeyStore(this)
        biometricAvailable = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        appSettings = getSharedPreferences("private-gallery-settings", MODE_PRIVATE)
        autoLockTimeout = AutoLockPreference.decode(appSettings.getString("auto-lock-timeout", null))
        appTheme = ThemePreference.decode(appSettings.getString("app-theme", null))
        allowScreenshots = !ScreenPrivacyPreference.secureWindow(appSettings.getString("allow-screenshots", null))
        hideContent = uk.co.traynor.privategallery.core.security.HideContentPolicy.decode(
            appSettings.contains("hide-content"), runCatching { appSettings.getString("hide-content", null) }.getOrNull())
        hideGate = hideContent
        secretDiscovered = appSettings.getBoolean("secret-discovered", false)
        updateLastChecked = appSettings.getString("update-last-checked", null) ?: "Never"
        browserSearchEngine = BrowserSearchEngine.decode(appSettings.getString("browser-search-engine", null))
        clearBrowserDataOnLock = appSettings.getBoolean("browser-clear-data-on-lock", false)
        browserSaveHistory = appSettings.getBoolean("browser-save-history", false)
        browserV2Session.contentBlocker.enabled = appSettings.getBoolean("browser-content-blocking", true)
        browserAutoConnectVpn = appSettings.getBoolean("browser-vpn-auto-connect", false)
        browserRequireVpn = appSettings.getBoolean("browser-vpn-required", false)
        wireGuardEngine.onStateChanged = { state ->
            runOnUiThread {
                onVpnStateObserved(state)
                browserWebView?.let { view ->
                    BrowserCallbackBindings.recordAcceptance(view, "VPN_STATE", mapOf("state" to state.name.lowercase()))
                    BrowserCallbackBindings.recordAcceptance(view, if (state == VpnConnectionState.CONNECTED) "VPN_GATE_OPEN" else "VPN_GATE_CLOSED")
                }
                if (browserRequireVpn && state != VpnConnectionState.CONNECTED) {
                    stopBrowserLoadingFor(BrowserWebViewLifecycleEvent.VPN_NOT_CONNECTED)
                }
            }
        }
        OwnedVpnTunnelRegistry.attach(wireGuardEngine)
        mediaAccessAvailable = hasDeviceMediaAccess()
        applyScreenPrivacy()
        session.setTimeout(autoLockTimeout)
        biometricEnabled = biometrics.isEnabled
        // A saved one-shot flag can survive process death while the old biometric prompt cannot.
        // Every new locked Activity gets one prompt; cancellation still leaves the PIN screen.
        if(savedInstanceState==null) automaticBiometricPromptAttempted = false
        route = if ((session.isUnlocked && sessionKey != null) || (retained.hidden.selected && retained.hidden.session.isUnlocked)) retained.route else if (keys.isConfigured) Route.LOCK else Route.SETUP
        setContent {
            PrivateGalleryTheme(appTheme) {
                PrivateGalleryApp(route, ::createPin, ::unlock, ::changePin, ::recoverWithOfflineKey, ::finishRecoveryKeySetup, { route = Route.RECOVER }, { route = Route.LOCK }, ::lock, ::importSelected, ::moveSelected, ::loadItems, ::loadCollections, ::loadFavouriteCollection, ::createCollection, ::addItemsToCollection, ::removeItemsFromCollection, ::renameCollection, ::deleteCollection, ::loadCollectionItems, ::readForViewing, ::loadPreview, ::loadImageEdit, ::applyImageCrop, ::undoImageCrop, ::resetImageCrop, ::restore, ::delete, biometricEnabled, ::unlockWithBiometrics, ::enrollBiometrics, ::finishSetup, autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, availableUpdate != null, mediaAccessAvailable, ::requestDeviceMediaAccess, ::deviceMediaPages, { deviceGallery.albums() }, ::loadDeviceThumbnail, ::openSettings, { openNonBrowser(Route.GALLERY); mediaAccessAvailable = hasDeviceMediaAccess() }, { openNonBrowser(Route.VAULT) }, { openNonBrowser(Route.FAVOURITE) }, ::openBrowser, ::applyAutoLockTimeout, ::applyTheme, ::applyAllowScreenshots, ::applyBrowserSearchEngine, ::applyClearBrowserDataOnLock, ::clearBrowserData, browserSearchEngine, clearBrowserDataOnLock, browserSaveHistory, ::applyBrowserSaveHistory, browserWebView, { view -> browserWebView = view }, { exit -> browserFullscreenExit = exit }, ::checkForUpdates, ::downloadUpdate, recoveryKeys.isConfigured, pendingRecoveryKey?.concatToString(), ::importBrowserSource, browserRequireVpn, browserVpnState == VpnConnectionState.CONNECTED, ::importWireGuardProfile, vpnProfileStatus, browserVpnState, browserAutoConnectVpn, ::applyBrowserAutoConnectVpn, ::applyBrowserRequireVpn, ::setFavouriteCollection, vpnProfiles, ::selectVpnProfile, ::removeVpnProfile, browserBookmarks, ::addBrowserBookmark, ::removeBrowserBookmark, browserV2Session, ::loadBrowserHistory, ::clearBrowserHistory, ::recordBrowserHistory, browserVpnPreparing, browserVpnPermissionRequired, ::requestBrowserVpnPermissionOrConnect, { item, bytes, cancelled, completed -> saveEditedCopy(item, bytes, cancelled, completed) }, ::readForEditing, { item, bytes, cancelled, completed -> saveEditedCopy(item, bytes, cancelled, completed, true) }, onReadVideoForViewing = ::readVideoForViewing, onOpenVideoSession = ::openVideoSession, onSaveAiCopy = { item, bytes, provenance, cancelled, completed -> saveEditedCopy(item, bytes, cancelled, completed, aiProvenance = provenance) }, onPrepareBrowserUpload = ::prepareBrowserUpload, onClearBrowserUpload = ::clearBrowserUploadCopies, hideContent = hideContent, secretDiscovered = secretDiscovered, onHideContentChanged = ::applyHideContent, onSecretDiscoveryChanged = ::applySecretDiscovery, onAuthenticateSensitive = ::authenticateSensitive, onCancelSensitiveAuthentication = ::cancelSensitiveAuthentication, onVerifySecretPin = ::verifySecretPin, onGenerateImage = ::generateVaultImage, onChooseBackupExport = { backupExportLauncher.launch("private-gallery-vault.pgvault") }, backupExportUri = backupExportUri, onCancelBackupExport = { backupExportUri = null }, onExportBackup = ::exportEncryptedBackup, backupStatus = backupStatus, onChooseBackupRestore = { backupRestoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }, backupRestoreUri = backupRestoreUri, onCancelBackupRestore = { backupRestoreUri = null }, onRestoreBackup = ::restoreEncryptedBackup, onLoadRecentlyDeleted = ::loadRecentlyDeleted, onMoveItemsToRecentlyDeleted = ::moveItemsToRecentlyDeleted, onChangeRecentlyDeleted = ::changeRecentlyDeleted, onRestoreSelectedVaultCopies = ::restoreSelectedVaultCopies, onLeaveHidden = ::leaveHiddenToDestination, hiddenState = retained.hidden, primaryAuthority = {session.isUnlocked && sessionKey != null}, onReturnToPrimary = ::returnToPrimaryVault, createPrimaryCopy = ::createPrimaryCopy, primaryGallerySelected = retained.primaryGallery, onGallerySourceSelected = ::selectGallerySource, onTransferToHidden = ::transferPrimaryToHidden, phoneMoveStatus = phoneMoveStatus, onCancelGalleryAuthentication = ::cancelGalleryAuthentication, onCapturePrimaryEditorAuthority = ::capturePrimaryEditorAuthority, onInvalidatePrimaryPresentation = ::invalidatePrimaryBrowserPresentation, onDirectHiddenUnlock = ::unlockHiddenDirect)
                vaultCamera?.let { camera -> uk.co.traynor.privategallery.ui.VaultCameraScreen(camera, ::exitVaultCamera) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                uk.co.traynor.privategallery.core.camera.CaptureCompletionEvents.revision.collect {
                    cameraRevision++
                    recoverVaultCamera(if(retained.hidden.selected)retained.hidden.session else null)
                }
            }
        }
        window.decorView.post(::triggerAutomaticBiometricPromptIfNeeded)
    }

    override fun onStop() {
        val hiddenWasAuthenticated=retained.hidden.session.isUnlocked
        biometricAttempts.lock();activeBiometricPrompt?.cancelAuthentication();activeBiometricPrompt=null
        hiddenBrowserOwner?.close()
        revokePhoneTransfers()
        primaryCopyAccess?.close(); primaryCopyAccess = null
        super.onStop()
        cancelSensitiveAuthentication()
        clearBrowserUploadCopies()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_background")) }
        retained.route = route
        val interactive = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        session.onActivityStopped(android.os.SystemClock.elapsedRealtime(), isChangingConfigurations, interactive)
        if (isChangingConfigurations && interactive) return
        retained.hidden.session.onBackground(uk.co.traynor.privategallery.ui.HiddenPreferences(applicationContext).timeout.milliseconds)
        if(hiddenWasAuthenticated && !retained.hidden.session.isUnlocked)automaticBiometricPromptAttempted=false
        if (!session.isUnlocked) lockPrimary(revokeHidden = !interactive) else scheduleOwnedVpnDisconnect()
    }

    override fun onStart() {
        uk.co.traynor.privategallery.ui.cleanupShareCopies(this)
        super.onStart()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_foreground")) }
        retained.hidden.session.onForeground()
        val wasUnlocked = session.isUnlocked
        session.onForegrounded(android.os.SystemClock.elapsedRealtime())
        if (retained.hidden.selected && !retained.hidden.session.isUnlocked) {
            automaticBiometricPromptAttempted = false
            retained.hidden.leave()
            retained.afterAuthentication = Route.VAULT
            lockPrimary(revokeHidden = false)
        }
        if (!session.isUnlocked && keys.isConfigured && !retained.hidden.session.isUnlocked) {
            if (wasUnlocked) automaticBiometricPromptAttempted = false
            lockPrimary(revokeHidden = false)
            triggerAutomaticBiometricPromptIfNeeded()
        } else if (route == Route.BROWSER && !retained.hidden.selected) {
            // Returning to the still-visible Browser is a Browser re-entry: retain a valid owned
            // tunnel during the grace period and cancel the pending teardown.
            browserVpnDisconnectJob?.cancel()
            browserVpnState = browserVpnController.enterBrowser(
                browserAutoConnectVpn && !browserVpnPermissionRequired &&
                    browserVpnController.state !in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING),
                browserRequireVpn,
                System.currentTimeMillis(),
            )
        }
    }

    override fun onDestroy() {
        biometricAttempts.lock();activeBiometricPrompt?.cancelAuthentication();activeBiometricPrompt=null
        closeVaultCamera()
        hiddenBrowserOwner?.close()
        primaryCopyAccess?.close(); primaryCopyAccess = null
        unregisterReceiver(screenOffReceiver)
        previewJobs.toList().forEach { it.cancel() }
        previewMemory.snapshot().values.forEach { if (it.isMutable && !it.isRecycled) it.eraseColor(android.graphics.Color.TRANSPARENT) }
        previewMemory.evictAll()
        previewKeys.clear()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_DESTROY_REQUESTED", mapOf("reason" to "explicit_cleanup")) }
        if (isFinishing && !isChangingConfigurations) {
            browserVpnDisconnectJob?.cancel()
            browserVpnController.onTaskRemoved()?.let { browserVpnState = it }
        }
        browserWebView?.apply {
            stopLoading()
            destroy()
        }
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_DESTROYED", mapOf("reason" to "explicit_cleanup")) }
        browserWebView = null
        browserV2Session.destroyAll()
        if (!isChangingConfigurations) { retained.hidden.leave(); sessionKey?.fill(0); sessionKey = null; session.lock() }
        super.onDestroy()
    }

    private fun createPin(pin: CharArray): Result<Unit> = runCatching {
        sessionKey?.fill(0)
        sessionKey = keys.create(pin)
        val recoveryReady = prepareRecoveryKeyIfNeeded()
        session.unlock()
        reconcileAfterUnlock()
        route = if (recoveryReady) Route.RECOVERY_KEY_SETUP else if (biometricAvailable) Route.BIOMETRIC_SETUP else Route.VAULT
    }

    private fun exportEncryptedBackup(recoveryKey: CharArray) {
        val uri = backupExportUri ?: run { recoveryKey.fill('\u0000'); return }
        backupExportUri = null
        val key = sessionKey?.copyOf() ?: run { recoveryKey.fill('\u0000'); backupStatus = "Unlock Vault before exporting."; return }
        backupStatus = "Verifying and exporting encrypted Vault…"
        lifecycleScope.launch(Dispatchers.IO) {
            val exportJob = coroutineContext[Job]
            val result = runCatching {
                check(session.isUnlocked && !hideContent)
                val envelope = recoveryKeys.exportEnvelope()
                val output = checkNotNull(contentResolver.openOutputStream(uri, "wt")) { "Cannot open backup destination" }
                output.use {
                    AndroidVaultRepository(applicationContext, key).exportBackup(recoveryKey, envelope, it, progress = { done, total ->
                        check(session.isUnlocked && !hideContent) { "Vault locked during export" }
                        runOnUiThread { backupStatus = "Exporting encrypted Vault · $done/$total files" }
                    }, cancelled = { !session.isUnlocked || hideContent || exportJob?.isActive != true })
                }
            }
            key.fill(0); recoveryKey.fill('\u0000')
            runOnUiThread {
                backupStatus = if (result.isSuccess) "Encrypted Vault backup saved. Keep your offline recovery key separately."
                    else "Backup did not finish. Delete the incomplete document and try again."
            }
        }
    }

    private fun restoreEncryptedBackup(recoveryKey: CharArray, pin: CharArray, completed: (Boolean) -> Unit) {
        val uri = backupRestoreUri ?: run { recoveryKey.fill('\u0000'); pin.fill('\u0000'); completed(false); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(route == Route.SETUP && !keys.isConfigured)
                checkNotNull(contentResolver.openInputStream(uri)) { "Cannot open backup" }.use { input ->
                    AndroidVaultRepository.restoreBackup(applicationContext, input, recoveryKey, pin, keys, recoveryKeys)
                }
            }
            recoveryKey.fill('\u0000'); pin.fill('\u0000')
            runOnUiThread {
                result.onSuccess { restored ->
                    sessionKey?.fill(0)
                    sessionKey = restored
                    session.unlock()
                    route = Route.VAULT
                    backupRestoreUri = null
                    completed(true)
                }.onFailure { completed(false) }
            }
        }
    }

    private fun unlock(pin: CharArray): Result<Unit> = runCatching {
        val key = keys.unlock(pin)
        // Explicit fresh Primary-source auth keeps only the already-authorized Hidden transfer scope.
        if (!(retained.primaryGallery && retained.hidden.selected && retained.hidden.session.isUnlocked)) retained.hidden.leave()
        sessionKey?.fill(0)
        sessionKey = key
        session.unlock()
        reconcileAfterUnlock()
        route = if (prepareRecoveryKeyIfNeeded()) Route.RECOVERY_KEY_SETUP else retained.afterAuthentication
    }

    /** Explicit discreet entry checks only Hidden's independent credential; never provisions it. */
    private fun unlockHiddenDirect(pin: CharArray): Result<Unit> = runCatching {
        check(BuildConfig.HIDDEN_ENABLED && route == Route.LOCK)
        val hidden = retained.hidden
        val store = uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore(applicationContext)
        check(store.isConfigured)
        val attempt = hidden.session.beginAuthentication()
        try {
            val key = store.unlock(pin)
            check(hidden.session.complete(attempt, key))
            // Revoke Primary, pending biometric results, renderers and operations before presentation.
            hidden.select(Route.VAULT)
            lockPrimary(revokeHidden = false)
            retained.primaryGallery = false
            retained.afterAuthentication = Route.VAULT
            hidden.authenticated = true
            route = Route.VAULT
        } catch (failure: Exception) {
            hidden.session.rejectAuthentication(attempt)
            throw failure
        }
    }.also { pin.fill('\u0000') }

    private fun recoverWithOfflineKey(recoveryKey: CharArray, newPin: CharArray): Result<Unit> = runCatching {
        require(newPin.size >= 6) { "PIN must be at least six digits" }
        val recovered = recoveryKeys.unlock(recoveryKey)
        try {
            keys.replacePinForRecoveredVault(newPin, recovered)
            biometrics.disable()
            biometricEnabled = false
            sessionKey?.fill(0)
            sessionKey = recovered.copyOf()
            session.unlock()
            reconcileAfterUnlock()
            route = Route.VAULT
        } finally {
            recovered.fill(0)
        }
    }

    private fun changePin(currentPin: CharArray, newPin: CharArray): Result<Unit> = runCatching {
        require(newPin.size >= 6) { "PIN must be at least six digits" }
        keys.changePin(currentPin, newPin)
    }

    private fun lock() = lockPrimary(revokeHidden = true)

    private fun lockPrimary(revokeHidden: Boolean) {
        val authenticatedEntry=sessionKey!=null || session.isUnlocked || (revokeHidden && retained.hidden.session.isUnlocked)
        val closingCamera=vaultCamera!=null
        closeVaultCamera()
        hiddenBrowserOwner?.close()
        revokePhoneTransfers()
        primaryCopyAccess?.close(); primaryCopyAccess = null
        if (revokeHidden) retained.hidden.leave()
        browserPresentationGeneration++
        generationJobs.toList().forEach { it.cancel() }
        biometricAttempts.lock()
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        cancelSensitiveAuthentication()
        clearBrowserUploadCopies()
        File(cacheDir, "browser-video").deleteRecursively()
        File(cacheDir, "hidden-download-validation").deleteRecursively()
        browserFullscreenExit?.invoke()
        browserFullscreenExit = null
        stopBrowserLoadingFor(BrowserWebViewLifecycleEvent.LOCKED)
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "lock")) }
        browserVpnDisconnectJob?.cancel()
        browserVpnController.onLock()
        browserVpnState = browserVpnController.state
        // Lock ends active renderer lifetime; only non-content tab metadata remains in memory.
        browserV2Session.destroyAll()
        if (BrowserNavigationPolicy.clearDataOnLock(clearBrowserDataOnLock)) clearBrowserData()
        session.lock()
        previewJobs.toList().forEach { it.cancel() }
        previewMemory.snapshot().values.forEach { if (it.isMutable && !it.isRecycled) it.eraseColor(android.graphics.Color.TRANSPARENT) }
        previewMemory.evictAll()
        previewKeys.clear()
        sessionKey?.fill(0)
        sessionKey = null
        pendingRecoveryKey?.fill('\u0000')
        pendingRecoveryKey = null
        automaticBiometricPromptAttempted = BiometricPromptPolicy.attemptedAfterLock(automaticBiometricPromptAttempted,authenticatedEntry || closingCamera)
        if (::keys.isInitialized && (keys.isConfigured || closingCamera) && !(retained.hidden.selected && retained.hidden.session.isUnlocked)) {
            retained.hidden.leave()
            retained.afterAuthentication = Route.VAULT
            route = if(keys.isConfigured) Route.LOCK else Route.SETUP
        }
    }

    /** Cleans interrupted ciphertext and never attempts to delete a source item. */
    private fun reconcileAfterUnlock() {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AndroidVaultRepository(applicationContext, key).reconcile() }
            key.fill(0)
            runOnUiThread {
                if (!hideContent) restoreBrowserPresentation()
            }
        }
    }

    private fun saveBrowserSessionMetadata(snapshot: uk.co.traynor.privategallery.core.browser.v2.BrowserSessionSnapshot) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserSessionStore(File(filesDir, "browser-session"), key).save(snapshot)
            } catch (failure: Exception) {
                // Restore metadata is best-effort. Keep the in-memory Browser session alive and
                // surface only a privacy-safe failure category in acceptance diagnostics.
                runOnUiThread {
                    browserV2Session.recordAcceptanceUiEvent(
                        "BROWSER_SESSION_SAVE_FAILED",
                        mapOf("type" to failure.javaClass.simpleName.take(80)),
                    )
                }
            }
            finally { key.fill(0) }
        }
    }

    private fun addBrowserBookmark(title: String, url: String, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key).add(title, url) }
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key).list() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { browserBookmarks = bookmarks; onComplete(if (result.isSuccess) "Bookmark saved." else "This page cannot be bookmarked.") }
        }
    }

    private fun removeBrowserBookmark(id: String) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key).remove(id) }
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key).list() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { browserBookmarks = bookmarks }
        }
    }

    private fun loadBrowserHistory(onLoaded: (List<uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val values = runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(filesDir, "browser-history"), key).list() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(if (hideContent) emptyList() else values) }
        }
    }

    private fun clearBrowserHistory(onComplete: () -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(filesDir, "browser-history"), key).clear() }
            key.fill(0)
            runOnUiThread { onComplete() }
        }
    }

    /** Hidden presentation/work has already been revoked; authentication may remain valid. Primary authority is checked here, not by a UI route flag. */
    private fun leaveHiddenToDestination(destination: AppNavigationDestination) {
        val hiddenAuthorized = retained.hidden.selected && retained.hidden.session.isUnlocked
        if (!hiddenAuthorized && (!session.isUnlocked || sessionKey == null)) return
        when (destination) {
            AppNavigationDestination.GALLERY -> { openNonBrowser(Route.GALLERY); mediaAccessAvailable = hasDeviceMediaAccess() }
            AppNavigationDestination.BROWSER -> openBrowser()
            AppNavigationDestination.SETTINGS -> openSettings()
            else -> Unit // Vault/Favourite never hand off Hidden data to Primary.
        }
    }

    /** Explicit vault switch is a fresh authentication boundary, even if Primary was unlocked. */
    private fun returnToPrimaryVault() = authenticatePrimaryDestination(Route.VAULT)

    private fun authenticatePrimaryDestination(destination: Route) {
        require(destination == Route.VAULT || destination == Route.GALLERY)
        retained.hidden.leave()
        retained.primaryGallery = false
        lockPrimary(revokeHidden = false)
        retained.afterAuthentication = destination
        route = if (keys.isConfigured) Route.LOCK else Route.SETUP
    }

    private fun cancelGalleryAuthentication() {
        if(!retained.hidden.selected) return
        biometricAttempts.lock()
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt=null
        retained.primaryGallery=false
        retained.afterAuthentication=Route.VAULT
        retained.hidden.select()
        route=Route.GALLERY
    }

    private fun selectGallerySource(primary: Boolean) {
        revokePhoneTransfers()
        primaryCopyAccess?.close();primaryCopyAccess=null
        retained.primaryGallery = primary
        if (!primary && retained.hidden.selected && retained.hidden.session.isUnlocked) {
            retained.hidden.outside()
            openNonBrowser(Route.GALLERY)
            mediaAccessAvailable = hasDeviceMediaAccess()
            return
        }
        if ((primary && retained.hidden.selected) || !session.isUnlocked || sessionKey == null) {
            retained.hidden.outside()
            lockPrimary(revokeHidden = false)
            retained.afterAuthentication = Route.GALLERY
            route = if (keys.isConfigured) Route.LOCK else Route.SETUP
        }
    }

    private fun openSettings() {
        revokePhoneTransfers()
        primaryCopyAccess?.close();primaryCopyAccess=null
        vpnProfileStatus = wireGuardEngine.diagnosticSummary()
        leaveBrowserIfOpen()
        route = Route.SETTINGS
        refreshVpnProfiles()
    }

    private fun openNonBrowser(destination: Route) {
        revokePhoneTransfers()
        primaryCopyAccess?.close();primaryCopyAccess=null
        leaveBrowserIfOpen()
        route = destination
    }

    private fun leaveBrowserIfOpen() {
        hiddenBrowserOwner?.close()
        if (route != Route.BROWSER) return
        // Leaving only starts the owned-tunnel grace period. Cancelling the existing WebView
        // load here left modern single-page apps with their shell/background but no application
        // state when Browser was reopened during that grace period.
        stopBrowserLoadingFor(BrowserWebViewLifecycleEvent.LEAVE_BROWSER)
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "browser_destination_leave")) }
        scheduleOwnedVpnDisconnect()
    }

    private fun stopBrowserLoadingFor(event: BrowserWebViewLifecycleEvent) {
        val webView = browserWebView ?: return
        if (!BrowserWebViewLifecyclePolicy.shouldStopLoading(event)) return
        if (event == BrowserWebViewLifecycleEvent.VPN_NOT_CONNECTED) {
            BrowserCallbackBindings.recordDiagnostic(
                webView,
                BrowserDiagnosticsPolicy.vpnGateStopLoading(browserVpnState.name),
            )
            BrowserCallbackBindings.recordAcceptance(webView, "REQUEST_BLOCKED_BY_VPN_GATE", mapOf("state" to browserVpnState.name.lowercase()), true)
        }
        webView.stopLoading()
    }

    private fun scheduleOwnedVpnDisconnect() {
        browserVpnController.onAppBackgrounded(System.currentTimeMillis())
        browserVpnState = browserVpnController.state
        browserVpnDisconnectJob?.cancel()
        browserVpnDisconnectJob = lifecycleScope.launch {
            delay(30_000)
            browserVpnController.tick(System.currentTimeMillis())?.let { onVpnStateObserved(it) }
        }
    }

    private fun onVpnStateObserved(state: VpnConnectionState) {
        browserVpnState = browserVpnController.onEngineState(state)
        vpnProfileStatus = wireGuardEngine.diagnosticSummary()
        browserV2Session.enforceNetworkPolicy()
        if (wireGuardEngine.ownsTunnel) {
            // The service is a task-removal hook only; background-start restrictions must never
            // affect the confirmed tunnel or Browser's fail-closed state.
            runCatching { startService(Intent(this, VpnOwnershipService::class.java)) }
        } else {
            stopService(Intent(this, VpnOwnershipService::class.java))
        }
    }

    private fun refreshVpnProfiles() {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val summaries = runCatching { VpnProfileRepository(File(filesDir, "vpn-profiles"), key).summaries() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { vpnProfiles = summaries }
        }
    }

    private fun selectVpnProfile(profileId: String) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = runCatching {
                VpnProfileRepository(File(filesDir, "vpn-profiles"), key).also { it.select(profileId) }.snapshot().profiles.single { it.id == profileId }
            }.getOrNull()
            key.fill(0)
            runOnUiThread {
                if (profile == null) {
                    vpnProfileStatus = "Unable to select WireGuard profile."
                    return@runOnUiThread
                }
                browserWebView?.stopLoading()
                browserVpnController.onLock()
                browserVpnController.select(profile)
                browserVpnState = browserVpnController.state
                vpnProfileStatus = "WireGuard profile selected."
                refreshVpnProfiles()
            }
        }
    }

    private fun removeVpnProfile(profileId: String) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { VpnProfileRepository(File(filesDir, "vpn-profiles"), key).remove(profileId) }
            key.fill(0)
            runOnUiThread {
                vpnProfileStatus = if (result.isSuccess) "WireGuard profile removed." else "Select another profile before removing the active profile."
                refreshVpnProfiles()
            }
        }
    }

    private fun invalidatePrimaryBrowserPresentation() {
        revokePhoneTransfers()
        browserPresentationGeneration++
        clearBrowserUploadCopies()
        browserVpnDisconnectJob?.cancel()
        browserV2Session.destroyAll()
        browserWebView?.apply {stopLoading();destroy()};browserWebView=null
    }

    private fun openBrowser() {
        if (retained.hidden.selected) {
            browserV2Session.destroyAll()
            browserWebView?.apply {stopLoading();destroy()};browserWebView=null
            browserVpnDisconnectJob?.cancel()
            route=Route.BROWSER
            return
        }
        revokePhoneTransfers()
        primaryCopyAccess?.close();primaryCopyAccess=null
        browserVpnPreparing = browserRequireVpn
        browserVpnPermissionRequired = false
        route = Route.BROWSER
        browserVpnDisconnectJob?.cancel()
        val ownerKey = sessionKey ?: return
        val ownerGeneration = browserPresentationGeneration
        val key = ownerKey.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = runCatching {
                VpnProfileRepository(File(filesDir, "vpn-profiles"), key).snapshot().let { snapshot ->
                    snapshot.profiles.singleOrNull { it.id == snapshot.activeProfileId }
                }
            }.getOrNull()
            key.fill(0)
            runOnUiThread {
                if(retained.hidden.selected || route != Route.BROWSER || sessionKey !== ownerKey ||
                    !session.isUnlocked || browserPresentationGeneration != ownerGeneration ||
                    !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@runOnUiThread
                browserVpnController.select(profile)
                browserVpnState = browserVpnController.enterBrowser(false, browserRequireVpn, System.currentTimeMillis())
                browserVpnPreparing = false
                if (browserRequireVpn && browserAutoConnectVpn && profile != null) requestBrowserVpnPermissionOrConnect()
            }
        }
    }

    private fun requestBrowserVpnPermissionOrConnect() {
        if(vpnPermissionWasHidden)return // A foreground replacement waits for the existing OS request.
        if (browserVpnController.state in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING)) return
        val permissionIntent = VpnService.prepare(this)
        browserVpnPermissionRequired = permissionIntent != null
        if (permissionIntent != null) {
            // Android owns this confirmation. Private Gallery never stops another app's VPN itself.
            vpnPermissionWasHidden = retained.hidden.selected
            hiddenVpnPermissionMode = if (vpnPermissionWasHidden) hiddenBrowserOwner?.mode else null
            vpnPermissionLauncher.launch(permissionIntent)
        } else connectBrowserVpn()
    }

    private fun importWireGuardProfile() {
        vpnProfileDocumentLauncher.launch(arrayOf("application/octet-stream", "text/plain", "application/wireguard"))
    }

    private fun connectBrowserVpn() {
        if (retained.hidden.selected && (hiddenBrowserOwner?.mode != uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy.Mode.VPN_ONLY || !retained.hidden.session.isUnlocked || route != Route.BROWSER)) return
        browserVpnPermissionRequired = false
        if (browserVpnController.state in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING)) return
        // Explicit permission/retry action connects even when automatic connection is disabled.
        browserVpnState = browserVpnController.enterBrowser(true, retained.hidden.selected || browserRequireVpn, System.currentTimeMillis())
    }

    private fun finishSetup() {
        route = retained.afterAuthentication
    }

    private fun finishRecoveryKeySetup() {
        pendingRecoveryKey?.fill('\u0000')
        pendingRecoveryKey = null
        route = if (biometricAvailable) Route.BIOMETRIC_SETUP else retained.afterAuthentication
    }

    /** A migration path for vaults created before offline recovery existed. */
    private fun prepareRecoveryKeyIfNeeded(): Boolean {
        if (!RecoveryKeySetupPolicy.shouldShowAfterUnlock(recoveryKeys.isConfigured)) return false
        return runCatching {
            pendingRecoveryKey?.fill('\u0000')
            pendingRecoveryKey = recoveryKeys.create(checkNotNull(sessionKey))
            true
        }.getOrDefault(false)
    }

    private fun applyAutoLockTimeout(timeout: AutoLockTimeout) {
        autoLockTimeout = timeout
        session.setTimeout(timeout)
        appSettings.edit().putString("auto-lock-timeout", AutoLockPreference.encode(timeout)).apply()
    }

    private fun applyTheme(theme: AppTheme) {
        appTheme = theme
        appSettings.edit().putString("app-theme", ThemePreference.encode(theme)).apply()
    }

    private fun applyBrowserSearchEngine(engine: BrowserSearchEngine) {
        browserSearchEngine = engine
        appSettings.edit().putString("browser-search-engine", engine.name).apply()
    }

    private fun applyClearBrowserDataOnLock(enabled: Boolean) {
        clearBrowserDataOnLock = enabled
        appSettings.edit().putBoolean("browser-clear-data-on-lock", enabled).apply()
    }

    private fun applyBrowserSaveHistory(enabled: Boolean) {
        browserSaveHistory = enabled
        appSettings.edit().putBoolean("browser-save-history", enabled).apply()
    }

    private fun recordBrowserHistory(title: String, url: String) {
        if (!browserSaveHistory) return
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val saved = uk.co.traynor.privategallery.core.browser.v2.recordBrowserHistoryVisit(
                    uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(filesDir, "browser-history"), key), title, url)
                if (!saved) android.util.Log.w("PrivateGalleryBrowser", "Browser history write failed (nonfatal)")
            } finally { key.fill(0) }
        }
    }

    private fun applyBrowserAutoConnectVpn(enabled: Boolean) {
        browserAutoConnectVpn = enabled
        appSettings.edit().putBoolean("browser-vpn-auto-connect", enabled).apply()
    }

    private fun applyBrowserRequireVpn(enabled: Boolean) {
        browserRequireVpn = enabled
        browserV2Session.enforceNetworkPolicy()
        appSettings.edit().putBoolean("browser-vpn-required", enabled).apply()
    }

    /** Clears only WebView-managed browsing state; it never touches encrypted Vault storage. */
    private fun clearBrowserData() {
        browserV2Session.clearWebData()
        // Bookmarks are intentionally separate encrypted user data and remain untouched.
        clearBrowserHistory {}
        browserWebView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            clearFormData()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    // System Back is owned by the Compose destination handlers, including browser
    // history/fullscreen. A legacy Activity override bypasses gesture dispatch.

    private fun hasDeviceMediaAccess(): Boolean {
        val images = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val videos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else images
        val selected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return DeviceGalleryPolicy.canBrowse(images, videos, selected)
    }

    private fun requestDeviceMediaAccess() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        mediaPermissionLauncher.launch(permissions)
    }

    internal fun primaryStorageSummary(done:(Result<uk.co.traynor.privategallery.core.media.VaultStorageSummary>)->Unit) {
        val identity=sessionKey ?: return
        val key=identity.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val result=try {runCatching {check(session.isUnlocked && sessionKey===identity);AndroidVaultRepository(applicationContext,key).storageSummary()}} finally {key.fill(0)}
            withContext(Dispatchers.Main){if(sessionKey===identity && session.isUnlocked && !hideContent)done(result)}
        }
    }

    internal fun primaryMediaDetails(id:String,done:(Result<VaultItem>)->Unit) {
        val identity=sessionKey ?: return
        val key=identity.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val result=try {runCatching {check(session.isUnlocked && sessionKey===identity);AndroidVaultRepository(applicationContext,key).items().first {it.id==id}}} finally {key.fill(0)}
            withContext(Dispatchers.Main){if(sessionKey===identity && session.isUnlocked && !hideContent)done(result)}
        }
    }
    internal fun adjustPrimaryMediaDate(id:String,time:Long?,zone:String?,done:(Result<Unit>)->Unit) {
        val identity=sessionKey ?: return
        val key=identity.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val result=try {runCatching {
                check(session.isUnlocked && sessionKey===identity && !hideContent)
                AndroidVaultRepository(applicationContext,key).adjustMediaDate(id,time,zone) {publish->
                    kotlinx.coroutines.runBlocking(Dispatchers.Main.immediate) {
                        check(session.isUnlocked && sessionKey===identity && !hideContent);publish()
                    }
                }
            }} finally {key.fill(0)}
            withContext(Dispatchers.Main){if(sessionKey===identity && session.isUnlocked && !hideContent)done(result)}
        }
    }

    internal fun loadPrimaryMediaSort(done:(MediaSort)->Unit) {
        val identity=sessionKey
        val key=identity?.copyOf()
        if(key==null||!session.isUnlocked){done(MediaSort.NEWEST);return}
        lifecycleScope.launch(Dispatchers.IO) {
            val result=try{runCatching { MediaSort.decode(AndroidVaultRepository(applicationContext,key).presentationSort()) }.getOrDefault(MediaSort.NEWEST)}finally{key.fill(0)}
            withContext(Dispatchers.Main){if(sessionKey===identity&&session.isUnlocked)done(result)}
        }
    }
    internal fun savePrimaryMediaSort(value:MediaSort) {
        val identity=sessionKey ?: return
        val key=identity.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            try{runCatching { if(sessionKey===identity&&session.isUnlocked)AndroidVaultRepository(applicationContext,key).presentationSort(value.name) }}finally{key.fill(0)}
        }
    }

    private fun deviceMediaPages(albumId: String?): Flow<PagingData<DeviceMediaItem>> =
        if (hideContent) kotlinx.coroutines.flow.flowOf(PagingData.empty()) else deviceGallery.pagedItems(albumId,MediaSort.decode(getSharedPreferences("private-gallery-media-ui",MODE_PRIVATE).getString("phone-sort",null)))

    private fun loadDeviceThumbnail(item: DeviceMediaItem, onLoaded: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) {
        if (hideContent) { onLoaded(null); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val thumbnail = deviceGallery.thumbnail(item, 360)?.asImageBitmap()
            runOnUiThread { onLoaded(if (hideContent) null else thumbnail) }
        }
    }

    private fun applyHideContent(hidden: Boolean) {
        if (hidden == hideContent) return
        if (hidden) { hideGate = true; hideContent = true }
        check(appSettings.edit().putString("hide-content", hidden.toString()).commit()) { "Unable to save privacy setting" }
        if (!hidden) { hideContent = false; hideGate = false }
        browserPresentationGeneration++
        if (hidden) {
            generationJobs.toList().forEach { it.cancel() }
            clearBrowserUploadCopies()
            browserFullscreenExit?.invoke()
            browserFullscreenExit = null
            browserV2Session.destroyAll()
            previewJobs.toList().forEach { it.cancel() }
            previewMemory.snapshot().values.forEach { if (it.isMutable && !it.isRecycled) it.eraseColor(android.graphics.Color.TRANSPARENT) }
            previewMemory.evictAll()
            previewKeys.clear()
            browserBookmarks = emptyList()
        }
        browserV2Session.recordAcceptanceUiEvent(if (hidden) "HIDE_CONTENT_ENABLED" else "HIDE_CONTENT_REVEALED")
        if (!hidden) restoreBrowserPresentation()
    }

    /** Reveal rereads Browser presentation only; it does not reconcile or modify Vault storage. */
    private fun restoreBrowserPresentation() {
        val owner = sessionKey ?: return
        val key = owner.copyOf()
        val generation = ++browserPresentationGeneration
        val before = browserV2Session.metadataSnapshot()
        lifecycleScope.launch(Dispatchers.IO) {
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key).list() }.getOrDefault(emptyList())
            val browserSession = runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserSessionStore(File(filesDir, "browser-session"), key).load() }.getOrNull()
            key.fill(0)
            runOnUiThread {
                if (!hideContent && session.isUnlocked && sessionKey === owner && generation == browserPresentationGeneration &&
                    browserV2Session.metadataSnapshot() == before) {
                    browserBookmarks = bookmarks
                    browserSession?.let(browserV2Session::restoreMetadata)
                }
            }
        }
    }

    private fun applySecretDiscovery(discovered: Boolean) {
        if (appSettings.edit().putBoolean("secret-discovered", discovered).commit()) secretDiscovered = discovered
    }

    private fun verifySecretPin(pin: CharArray): Boolean = try {
        val candidate = keys.unlock(pin)
        try { session.isUnlocked && sessionKey?.let { java.security.MessageDigest.isEqual(candidate, it) } == true }
        finally { candidate.fill(0) }
    } catch (_: Exception) { false } finally { pin.fill('\u0000') }

    private fun authenticateSensitive(completed: (Boolean) -> Unit) {
        if (!session.isUnlocked || sessionKey == null || !biometricEnabled) { completed(false); return }
        cancelSensitiveAuthentication()
        val generation = ++sensitiveGeneration
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (generation != sensitiveGeneration) return
                val candidate = runCatching { result.cryptoObject?.cipher?.let(biometrics::unwrapAuthenticated) }.getOrNull()
                val valid = candidate?.let { session.isUnlocked && sessionKey?.let { owner -> java.security.MessageDigest.isEqual(it, owner) } == true } == true
                candidate?.fill(0)
                sensitivePrompt = null
                completed(valid)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (generation != sensitiveGeneration) return
                sensitivePrompt = null
                completed(false)
            }
        })
        sensitivePrompt = prompt
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle("Confirm owner identity")
                    .setSubtitle("Authenticate to change protected settings")
                    .setNegativeButtonText("Use PIN").build(),
                BiometricPrompt.CryptoObject(biometrics.newDecryptCipher()),
            )
        }.onFailure { if (generation == sensitiveGeneration) { sensitivePrompt = null; completed(false) } }
    }

    private fun cancelSensitiveAuthentication() {
        sensitiveGeneration++
        sensitivePrompt?.cancelAuthentication()
        sensitivePrompt = null
    }

    private fun applyAllowScreenshots(allowed: Boolean) {
        allowScreenshots = allowed
        appSettings.edit().putString("allow-screenshots", allowed.toString()).apply()
        applyScreenPrivacy()
    }

    private fun applyScreenPrivacy() {
        if (allowScreenshots) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
    }

    private fun checkForUpdates() {
        updateStatus = "Checking…"
        availableUpdate = null
        lifecycleScope.launch(Dispatchers.IO) {
            val result = GithubReleaseUpdateService().check(BuildConfig.VERSION_NAME)
            when (result) {
                is UpdateCheck.UpToDate -> publishUpdateStatus("${result.release.version.raw} · Up to date", null)
                is UpdateCheck.Available -> publishUpdateStatus("Update available · ${result.release.version.raw}", result.release)
                is UpdateCheck.Failed -> publishUpdateStatus(result.reason.userMessage, null)
            }
        }
    }

    private fun publishUpdateStatus(status: String, release: ReleaseMetadata?) {
        val checked = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        appSettings.edit().putString("update-last-checked", checked).apply()
        runOnUiThread {
            updateStatus = status
            updateLastChecked = checked
            availableUpdate = release
        }
    }

    private fun downloadUpdate() {
        val release = availableUpdate ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            updateStatus = "Allow installs from Private Gallery, then download again."
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updateStatus = "Downloading…"
        lifecycleScope.launch(Dispatchers.IO) {
            val apk = GithubReleaseUpdateService().downloadVerified(release)
            if (apk == null) {
                runOnUiThread { updateStatus = "Verification failed — update was not installed." }
                return@launch
            }
            try {
                val updateDirectory = File(cacheDir, "updates").apply { mkdirs() }
                val output = File(updateDirectory, "private-gallery-release.apk")
                FileOutputStream(output).use { stream -> stream.write(apk); stream.fd.sync() }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", output)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
                    val installerAvailable = installIntent.resolveActivity(packageManager) != null
                    if (!UpdateInstallPolicy.canHandOffToAndroid(canInstall, installerAvailable)) {
                        updateStatus = "Android installer is unavailable."
                        return@runOnUiThread
                    }
                    runCatching { startActivity(installIntent) }
                        .onSuccess { updateStatus = "Installation handed to Android" }
                        .onFailure { updateStatus = "Android installer could not be opened." }
                }
            } catch (_: Throwable) {
                runOnUiThread { updateStatus = "Download failed" }
            } finally {
                apk.fill(0)
            }
        }
    }

    private fun importSelected(uris: List<Uri>, onComplete: (String) -> Unit) {
        if(hideContent) return
        val owner=sessionKey ?: return
        val key=owner.copyOf()
        val generation=phoneTransferGeneration
        val selectedHidden=retained.hidden.selected
        val current={ phoneTransferGeneration==generation && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && !hideGate && sessionKey===owner && session.isUnlocked && retained.hidden.selected==selectedHidden }
        lifecycleScope.launch(Dispatchers.IO) {
            var copied=0
            try {
                val transfer=uk.co.traynor.privategallery.core.vault.PhoneVaultTransfer(applicationContext,key,null,publish={commit -> synchronized(phoneTransferGate) {check(current());commit()}},authorized=current)
                uris.distinct().forEach {uri -> if(current()) runCatching {transfer.copy(uri)}.onSuccess {copied++} }
                withContext(Dispatchers.Main) {if(current())onComplete("$copied of ${uris.distinct().size} verified copies. Originals retained.")}
            } finally {key.fill(0)}
        }
    }

    private fun importBrowserSource(source: uk.co.traynor.privategallery.core.vault.VaultImportSource, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Vault save cancelled."); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                VaultImportCoordinator(AndroidVaultRepository(applicationContext, key)).acquire(
                    source.copy(isCancelled = { hideContent || source.isCancelled() || !session.isUnlocked || (browserRequireVpn && browserVpnState != VpnConnectionState.CONNECTED) }),
                )
            }
            key.fill(0)
            runOnUiThread {
                onComplete(
                    when (result.getOrNull()) {
                        is ImportResult.Imported -> "Saved to Vault."
                        is ImportResult.Duplicate -> "Already in Vault."
                        null -> when {
                            result.exceptionOrNull() is uk.co.traynor.privategallery.core.browser.v2.BrowserVideoUnavailableException -> "This video can be played here but can't be saved directly."
                            source.isCancelled() || !session.isUnlocked -> "Vault save cancelled."
                            else -> "Unable to save to Vault."
                        }
                    },
                )
            }
        }
    }

    private fun loadItems(onLoaded: (List<VaultItem>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val items = runCatching { AndroidVaultRepository(applicationContext, key).items() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(if (hideContent) emptyList() else items) }
        }
    }

    private fun loadCollections(onLoaded: (List<VaultCollection>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val collections = runCatching { AndroidVaultRepository(applicationContext, key).collections() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(if (hideContent) emptyList() else collections) }
        }
    }

    private fun loadFavouriteCollection(onLoaded: (VaultCollection?) -> Unit) {
        if (hideContent) { onLoaded(null); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val collection = runCatching { AndroidVaultRepository(applicationContext, key).migrateLegacyFavourite() }.getOrNull()
            key.fill(0)
            runOnUiThread { onLoaded(if (hideContent) null else collection) }
        }
    }

    private fun setFavouriteCollection(collectionId: String, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).setFavouriteCollection(collectionId) }
            key.fill(0)
            runOnUiThread { onComplete(if (result.isSuccess) "Favourite collection updated." else "Unable to update favourite collection.") }
        }
    }

    private fun createCollection(name: String, onComplete: (Result<VaultCollection>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).createCollection(name) }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    private fun addItemsToCollection(collectionId: String, itemIds: List<String>, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).addItemsToCollection(collectionId, itemIds) }
            key.fill(0)
            runOnUiThread { onComplete(if (result.isSuccess) "Added to collection." else "Unable to update collection.") }
        }
    }

    private fun renameCollection(collectionId: String, name: String, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).renameCollection(collectionId, name) }
            key.fill(0)
            runOnUiThread { onComplete(if (result.isSuccess) "Collection renamed." else "Unable to rename collection.") }
        }
    }

    private fun deleteCollection(collectionId: String, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).deleteCollection(collectionId) }
            key.fill(0)
            runOnUiThread { onComplete(if (result.isSuccess) "Collection deleted. Vault media was retained." else "Unable to delete collection.") }
        }
    }

    private fun removeItemsFromCollection(collectionId: String, itemIds: List<String>, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                AndroidVaultRepository(applicationContext, key).also { repository -> itemIds.forEach { repository.removeItemFromCollection(collectionId, it) } }
            }
            key.fill(0)
            runOnUiThread { onComplete(if (result.isSuccess) "Removed from collection. Vault media was retained." else "Unable to update collection.") }
        }
    }

    private fun loadCollectionItems(collectionId: String, onLoaded: (List<VaultItem>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val items = runCatching { AndroidVaultRepository(applicationContext, key).itemsInCollection(collectionId) }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(if (hideContent) emptyList() else items) }
        }
    }

    private fun moveSelected(uris: List<Uri>, onComplete: (String) -> Unit) {
        if (hideContent || phoneMoveBusy) { onComplete("Move unavailable or already in progress."); return }
        val owner = sessionKey
        val generation=phoneTransferGeneration
        val hiddenSelected = retained.hidden.selected
        if(!hiddenSelected && owner==null) {onComplete("Unlock Primary first. Originals retained.");return}
        val hidden = retained.hidden.session
        val operation = if (hiddenSelected) runCatching { hidden.beginOperation() }.getOrNull() else null
        if (hiddenSelected && operation == null) { onComplete("Unlock Hidden first. Originals retained."); return }
        val key = if(hiddenSelected)null else checkNotNull(owner).copyOf()
        val current = { phoneTransferGeneration==generation && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && !hideGate && (hiddenSelected || (sessionKey === owner && session.isUnlocked)) &&
            route==Route.GALLERY && !(retained.hidden.selected && retained.primaryGallery) && !retained.hidden.visible && retained.hidden.selected == hiddenSelected && (!hiddenSelected || hidden.isCurrent(operation)) }
        phoneMoveBusy = true
        lifecycleScope.launch(Dispatchers.IO) {
            val sources = mutableListOf<Uri>()
            var verified = 0
            try {
                val transfer=uk.co.traynor.privategallery.core.vault.PhoneVaultTransfer(applicationContext,key,if(hiddenSelected)hidden else null, publish={commit -> synchronized(phoneTransferGate) {check(current());commit()}}, authorized=current)
                for (uri in uris.distinct()) {
                    if (!current()) break
                    try {
                        val item=transfer.copy(uri)
                        verified++
                        transfer.deletionSource(uri,item)?.let {sources+=it}
                    } catch (_: Exception) { /* A failed item never authorizes source deletion. */ }
                }
                withContext(Dispatchers.Main) {
                    if (current() && sources.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val request=MediaStore.createDeleteRequest(contentResolver,sources)
                            check(current())
                            pendingSourceDeletion=sources.toList()
                            pendingVerifiedPhoneCopies=verified
                            pendingSourceDeletionCompletion={ message -> if(phoneTransferGeneration==generation && sessionKey === owner && retained.hidden.selected==hiddenSelected) onComplete(message) }
                            sourceDeletionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        } catch (_: Exception) {
                            pendingSourceDeletion=emptyList();pendingSourceDeletionCompletion=null;phoneMoveBusy=false
                            onComplete("$verified verified copies. Originals retained; deletion unavailable.")
                        }
                    } else {
                        phoneMoveBusy=false
                        if(current()) onComplete("$verified verified copies. Originals retained.")
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    phoneMoveStatus="$verified verified copies. Move interrupted; originals retained."
                    if(current()) onComplete(phoneMoveStatus)
                }
            } finally { key?.fill(0);operation?.close();if(pendingSourceDeletion.isEmpty())phoneMoveBusy=false }
        }
    }

    @Volatile private var primaryHiddenTransferBusy = false

    private fun transferPrimaryToHidden(ids: List<String>, move: Boolean, cancelled: () -> Boolean,
                                        progress: (Int, Int) -> Unit, done: (String) -> Unit) {
        val owner=sessionKey
        if(owner == null || route!=Route.GALLERY || !retained.primaryGallery || !retained.hidden.selected || hideContent) {
            done("Transfer unavailable. Originals retained."); return
        }
        if(primaryHiddenTransferBusy) { done("A transfer is already in progress."); return }
        val access=runCatching {createPrimaryCopy(retained.hidden.session)?.also {it.authorize(owner)}}.getOrNull()
            ?: run {done("Unlock both vaults first. Originals retained.");return}
        primaryHiddenTransferBusy = true
        val interrupted = { cancelled() || sessionKey !== owner || !session.isUnlocked || hideContent || !access.isActive() }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result=runCatching {access.transfer(ids,move,interrupted) { completed,total ->
                    runOnUiThread { progress(completed,total) }
                }}
                withContext(Dispatchers.Main) {
                    done(result.fold({
                        val remaining=it.total-it.verified-it.failed
                        "${if(it.interrupted) "Transfer interrupted" else if(it.failed > 0 || (move && it.moved < it.verified)) "Transfer partially complete" else "Transfer complete"}: ${it.verified}/${it.total} verified Hidden copies; ${it.moved} Primary originals moved to Recently Deleted; ${it.verified-it.moved} copied with originals retained; ${it.failed} failed; $remaining unprocessed. Failed and unprocessed originals retained."
                    }, {"Transfer interrupted. Verified copies retained; remaining originals retained."}))
                }
            } finally { access.close();primaryHiddenTransferBusy=false }
        }
    }

    private fun sourceNoLongerExists(raw: String): Boolean = runCatching {
        contentResolver.query(android.net.Uri.parse(raw), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            .use { cursor -> cursor != null && !cursor.moveToFirst() }
    }.getOrDefault(false)

    private fun restore(item: VaultItem, removeAfter: Boolean, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Unavailable."); return }
        val owner = sessionKey ?: return
        val key = owner.copyOf()
        val cancelled = { hideGate || sessionKey !== owner || !session.isUnlocked }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (cancelled()) throw java.io.IOException("Restore cancelled")
                val repository = AndroidVaultRepository(applicationContext, key)
                val publish: ((() -> Unit) -> Unit) = { commit ->
                    if (cancelled()) throw java.io.IOException("Restore cancelled")
                    commit()
                    if (cancelled()) throw java.io.IOException("Restore cancelled")
                }
                if (removeAfter) repository.restoreAndRemove(item, cancelled, publish) else repository.restore(item, cancelled, publish)
                runOnUiThread { onComplete(if (cancelled()) "Restore interrupted. Vault copy retained." else if (removeAfter) "Restored to Gallery and removed from Vault." else "Restored to Gallery. Vault copy retained.") }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Restore failed. The Vault copy was retained.") }
            } finally { key.fill(0) }
        }
    }

    private fun readForViewing(item: VaultItem, onComplete: (Result<ByteArray>) -> Unit) =
        readVideoForViewing(item, { false }, {}, onComplete)

    private fun readVideoForViewing(item: VaultItem, cancelled: () -> Boolean, progress: (Int) -> Unit, onComplete: (Result<ByteArray>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val owner = sessionKey ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        val key = owner.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val task = coroutineContext[Job]!!
            val result = runCatching { AndroidVaultRepository(applicationContext, key).readVideoForViewing(item,
                { hideContent || cancelled() || sessionKey !== owner || !task.isActive },
                { percent -> runOnUiThread { if (!hideContent && sessionKey === owner && !cancelled()) progress(percent) } },
            ) }
            key.fill(0)
            runOnUiThread {
                if (!hideContent && sessionKey === owner && !cancelled()) onComplete(result)
                else result.getOrNull()?.fill(0)
            }
        }.invokeOnCompletion { key.fill(0) }
    }

    private fun openVideoSession(item: VaultItem, cancelled: () -> Boolean,
                                 onComplete: (Result<uk.co.traynor.privategallery.core.vault.VaultVideoSession>) -> Unit) {
        val owner = sessionKey ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        val key = owner.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val task = coroutineContext[Job]!!
            val allowed = { !hideContent && session.isUnlocked && sessionKey === owner }
            val interrupted = { !allowed() || cancelled() || !task.isActive }
            val result = runCatching { AndroidVaultRepository(applicationContext, key).openVideoSession(item, allowed, interrupted) }
            key.fill(0)
            runOnUiThread {
                if (allowed() && !cancelled()) onComplete(result) else result.getOrNull()?.close()
            }
        }.invokeOnCompletion { key.fill(0) }
    }

    /** Generates a bounded preview in memory only after the vault has been unlocked. */
    private fun loadPreview(item: VaultItem, onComplete: (Result<Bitmap>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val owner = sessionKey ?: return
        previewKeys[item.id]?.let { previewMemory.get(it) }?.let { onComplete(Result.success(it)); return }
        val key = owner.copyOf()
        val epoch = previewEpochs[item.id] ?: 0L
        var loadedCacheKey: String? = null
        val job = lifecycleScope.launch(Dispatchers.IO) {
            val task = coroutineContext[Job]!!
            val owned = mutableListOf<Bitmap>()
            fun own(bitmap: Bitmap): Bitmap = bitmap.also { owned.add(it) }
            fun checkActive() { check(!hideContent && sessionKey === owner && task.isActive) { "Preview cancelled" } }
            val result = runCatching { synchronized(previewCacheLock) {
                check(VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize)) { "Preview is not available for this item" }
                val repository = AndroidVaultRepository(applicationContext, key)
                checkActive()
                val revision = item.plaintextSha256.joinToString("") { "%02x".format(it) } + ":" + repository.imageEdit(item.id)?.crop.toString()
                val cacheKey = item.id + ":" + revision
                loadedCacheKey = cacheKey
                previewMemory.get(cacheKey)?.let { return@synchronized it }
                previewDisk.get(item.id, revision, key)?.let { encoded ->
                    try { BitmapFactory.decodeByteArray(encoded, 0, encoded.size, BitmapFactory.Options().apply { inMutable = true })?.let { bitmap ->
                        own(bitmap)
                        checkActive()
                        return@synchronized bitmap
                    } } finally { encoded.fill(0) }
                }
                val bytes = repository.readForEditingPreview(item) { hideContent || sessionKey !== owner || !task.isActive }
                val bitmap =
                try {
                    if (item.mimeType.startsWith("video/")) {
                        MediaMetadataRetriever().let { retriever ->
                            try {
                                retriever.setDataSource(ByteArrayMediaDataSource(bytes))
                                own(checkNotNull(if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 420, 420)
                                else {
                                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
                                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
                                    check(width * height in 1..4_000_000L) { "Video preview exceeds safe decode size" }
                                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                }) {
                                    "Unable to decode protected video preview"
                                })
                            } finally {
                                retriever.release()
                            }
                        }
                    } else {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val largest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                        var sample = 1
                        while (sample * 2 <= largest / 420) sample *= 2
                        val decoded = own(checkNotNull(
                            BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.size,
                                BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true },
                            ),
                        ) { "Unable to decode protected preview" })
                        own(VaultImageEdits.crop(own(VaultImageEdits.visuallyOrient(bytes, decoded)), repository.imageEdit(item.id)?.crop))
                    }
                } finally {
                    bytes.fill(0)
                }
                checkActive()
                val largest = maxOf(bitmap.width, bitmap.height)
                val scaled = if (largest <= 512) bitmap else own(Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * 512L / largest).toInt().coerceAtLeast(1),
                    (bitmap.height * 512L / largest).toInt().coerceAtLeast(1), true))
                val bounded = if (scaled.isMutable) scaled else own(scaled.copy(Bitmap.Config.ARGB_8888, true))
                val encoded = java.io.ByteArrayOutputStream().use { out -> bounded.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
                try { checkActive(); previewDisk.put(item.id, revision, encoded, key) } finally { encoded.fill(0) }
                bounded
            } }
            key.fill(0)
            val delivered = result.getOrNull()
            owned.filter { it !== delivered }.distinct().forEach { if (!it.isRecycled) it.recycle() }
            runOnUiThread {
                if (!hideContent && sessionKey === owner && !task.isCancelled && (previewEpochs[item.id] ?: 0L) == epoch) {
                    result.getOrNull()?.let { bitmap -> loadedCacheKey?.let { cacheKey -> previewKeys[item.id] = cacheKey; previewMemory.put(cacheKey, bitmap) } }
                    onComplete(result)
                } else if (delivered != null && delivered in owned && !delivered.isRecycled) delivered.recycle()
            }
        }
        previewJobs.add(job)
        job.invokeOnCompletion { key.fill(0); runOnUiThread { previewJobs.remove(job) } }
    }

    private fun loadImageEdit(item: VaultItem, onComplete: (ImageEditState?) -> Unit) {
        if (hideContent) { onComplete(null); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val edit = runCatching { AndroidVaultRepository(applicationContext, key).imageEdit(item.id) }.getOrNull()
            key.fill(0)
            runOnUiThread { onComplete(if (hideContent) null else edit) }
        }
    }

    private fun applyImageCrop(item: VaultItem, crop: NormalizedCrop, onComplete: (Result<ImageEditState>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).applyImageCrop(item.id, crop) }
            }
            key.fill(0)
            runOnUiThread { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun undoImageCrop(item: VaultItem, onComplete: (Result<ImageEditState?>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).undoImageCrop(item.id) }
            }
            key.fill(0)
            runOnUiThread { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun resetImageCrop(item: VaultItem, onComplete: (Result<Unit>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).resetImageCrop(item.id) }
            }
            key.fill(0)
            runOnUiThread { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun capturePrimaryEditorAuthority():()->Boolean {
        val owner=sessionKey
        val generation=phoneTransferGeneration
        val ownerRoute=route
        val selectedHidden=retained.hidden.selected
        return {owner!=null && sessionKey===owner && session.isUnlocked && !hideContent &&
            phoneTransferGeneration==generation && route==ownerRoute && retained.hidden.selected==selectedHidden &&
            !retained.hidden.visible && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)}
    }

    private fun readForEditing(item: VaultItem, cancelled: () -> Boolean, completed: (Result<ByteArray>) -> Unit) {
        if (hideContent) { completed(Result.failure(IllegalStateException("Unavailable"))); return }
        if (!item.mimeType.startsWith("image/") || item.plaintextSize > uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES) {
            completed(Result.failure(IllegalArgumentException("Image too large"))); return
        }
        val ownerSession = sessionKey
        val key = ownerSession?.copyOf()
        if (key == null) { completed(Result.failure(IllegalStateException("Vault locked"))); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).readForEditing(item) { hideContent || cancelled() || sessionKey !== ownerSession } }
            key.fill(0)
            runOnUiThread {
                if (hideContent || cancelled() || sessionKey !== ownerSession) { result.getOrNull()?.fill(0); completed(Result.failure(IllegalStateException("Editing cancelled"))) }
                else completed(result)
            }
        }.invokeOnCompletion { key.fill(0) }
    }

    private fun saveEditedCopy(item: VaultItem, bytes: ByteArray, cancelled: () -> Boolean, completed: (Result<VaultItem>) -> Unit, remoteAi: Boolean = false, aiProvenance: uk.co.traynor.privategallery.core.editor.AiEditProvenance? = null) {
        if (hideContent) { bytes.fill(0); completed(Result.failure(IllegalStateException("Unavailable"))); return }
        val current=capturePrimaryEditorAuthority()
        val key = sessionKey?.copyOf()
        if (key == null) { bytes.fill(0); completed(Result.failure(IllegalStateException("Vault locked"))); return }
        val ownerSession = sessionKey
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(current() && !cancelled()) {"Editing cancelled"}
                val repository = AndroidVaultRepository(applicationContext, key)
                repository.importAiEditedCopy(item.id, bytes, aiProvenance ?: if (remoteAi) uk.co.traynor.privategallery.core.editor.AiEditProvenance(uk.co.traynor.privategallery.core.editor.AiProcessing.CLOUD, "replicate-seedream", "seedream-4.5") else null,
                    uk.co.traynor.privategallery.core.editor.AiConsentStore(applicationContext).keepEditsInVault(), publishIfAllowed={commit -> synchronized(phoneTransferGate) {check(current() && !cancelled());commit()}}) {
                    !current() || cancelled() || sessionKey !== ownerSession
                }
            }
            key.fill(0); bytes.fill(0)
            runOnUiThread { completed(if(current() && !cancelled()) result else Result.failure(IllegalStateException("Editing cancelled"))) }
        }.invokeOnCompletion { key.fill(0); bytes.fill(0) }
    }

    private fun generateVaultImage(request: uk.co.traynor.privategallery.core.editor.GenerationRequest,
        onStage: (String) -> Unit, completed: (Result<VaultItem>) -> Unit): () -> Unit {
        val current=capturePrimaryEditorAuthority()
        val ownerSession = sessionKey
        val key = ownerSession?.copyOf()
        if (key == null || !current()) {
            key?.fill(0)
            completed(Result.failure(IllegalStateException("Unlock Vault to create an image.")))
            return {}
        }
        val job = lifecycleScope.launch(Dispatchers.IO) {
            val activeJob = kotlinx.coroutines.currentCoroutineContext()[Job]
            var token: ByteArray? = null
            var remote: ByteArray? = null
            var sanitized: ByteArray? = null
            try {
                val consent = uk.co.traynor.privategallery.core.editor.AiConsentStore(applicationContext)
                check(consent.hasConsent(uk.co.traynor.privategallery.core.editor.ReplicateSeedreamProvider.ID)) {
                    "Confirm Replicate remote processing first."
                }
                token = uk.co.traynor.privategallery.core.editor.AiCredentialStore(applicationContext)
                    .read(uk.co.traynor.privategallery.core.editor.ReplicateSeedreamProvider.ID)
                    ?: error("Set up Replicate in AI editing settings.")
                val templates = uk.co.traynor.privategallery.core.editor.PromptEnhancementStore(applicationContext)
                val effectivePrompt = templates.effective(request.prompt, uk.co.traynor.privategallery.core.editor.PromptKind.CREATE,
                    request.model.modelId, request.enhancePrompt)
                val references = if (request.referenceItemIds.isEmpty()) emptyList() else {
                    val repo = AndroidVaultRepository(applicationContext, key)
                    val byId = repo.items().associateBy { it.id }
                    request.referenceItemIds.map { id ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        check(current() && sessionKey === ownerSession) { "Generation cancelled." }
                        val item = byId[id] ?: error("Reference image unavailable.")
                        check(item.mimeType.startsWith("image/") && item.plaintextSize <= uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES)
                        val plain = repo.readForEditing(item) { !current() || sessionKey !== ownerSession || activeJob?.isActive != true }
                        try {
                            val prepared = uk.co.traynor.privategallery.core.editor.ReplicateImagePreparation.prepare(plain)
                            try { "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(prepared) }
                            finally { prepared.fill(0) }
                        } finally { plain.fill(0) }
                    }
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(current()) { "Generation cancelled." }
                remote = uk.co.traynor.privategallery.core.editor.ReplicateImageGenerationApi(
                    uk.co.traynor.privategallery.core.editor.PrivateAiHttpTransport()).generate(token!!, request.copy(prompt = effectivePrompt, references = references, referenceItemIds = emptyList())) { stage ->
                    runOnUiThread { if (current() && sessionKey === ownerSession) onStage(stage) }
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(current() && sessionKey === ownerSession) { "Generation cancelled." }
                sanitized = uk.co.traynor.privategallery.core.editor.PhotoRenderer.sanitize(remote!!)
                remote?.fill(0); remote = null
                runOnUiThread { if (current() && sessionKey === ownerSession) onStage("Encrypting…") }
                val item = AndroidVaultRepository(applicationContext, key).importAiGeneratedImage(sanitized!!,
                    request.model.modelId, consent.keepEditsInVault(), publishIfAllowed={commit -> synchronized(phoneTransferGate) {check(current() && activeJob?.isActive==true);commit()}}) {
                    !current() || sessionKey !== ownerSession || !session.isUnlocked || activeJob?.isActive != true
                }
                runOnUiThread { if (current() && sessionKey === ownerSession) completed(Result.success(item)) }
            } catch (failure: kotlinx.coroutines.CancellationException) {
                runOnUiThread { if (sessionKey === ownerSession && current()) completed(Result.failure(IllegalStateException("Generation cancelled."))) }
            } catch (failure: Exception) {
                runOnUiThread { if (sessionKey === ownerSession && current()) completed(Result.failure(failure)) }
            } finally {
                token?.fill(0); remote?.fill(0); sanitized?.fill(0); key.fill(0)
                runOnUiThread { activeJob?.let(generationJobs::remove) }
            }
        }
        generationJobs.add(job)
        return { job.cancel() }
    }

    private fun delete(item: VaultItem, onComplete: (String) -> Unit) {
        moveItemsToRecentlyDeleted(listOf(item.id), onComplete)
    }

    private fun restoreSelectedVaultCopies(ids: List<String>, onComplete: (String) -> Unit) {
        val owner = sessionKey ?: run { onComplete("Vault locked."); return }
        val key = owner.copyOf()
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = AndroidVaultRepository(applicationContext, key)
            var restored = 0
            ids.distinct().forEach { id ->
                if (hideContent || sessionKey !== owner) return@forEach
                if (runCatching {
                    val item = repository.requireEgress(id, uk.co.traynor.privategallery.core.vault.VaultEgress.RESTORE)
                    repository.restore(item, cancelled = { hideContent || sessionKey !== owner })
                }.isSuccess) restored++
            }
            key.fill(0)
            runOnUiThread { onComplete("$restored/${ids.distinct().size} copies restored to Gallery. Vault originals retained.") }
        }
    }

    private fun loadRecentlyDeleted(onLoaded: (List<VaultItem>) -> Unit) {
        val key = sessionKey?.copyOf() ?: run { onLoaded(emptyList()); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val deleted = runCatching { AndroidVaultRepository(applicationContext, key).recentlyDeleted() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(if (session.isUnlocked && !hideContent) deleted else emptyList()) }
        }
    }

    private fun moveItemsToRecentlyDeleted(ids: List<String>, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Unavailable."); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            var moved = 0
            try {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) {
                    val repository = AndroidVaultRepository(applicationContext, key)
                    ids.distinct().forEach { id ->
                        if (!session.isUnlocked || hideContent) return@forEach
                        if (runCatching { repository.moveToRecentlyDeleted(id) }.isSuccess) moved++
                    }
                }
                runOnUiThread {
                    ids.forEach(::invalidatePreview)
                    onComplete("$moved/${ids.distinct().size} moved to Recently Deleted. Kept for 30 days.")
                }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to move these items to Recently Deleted.") }
            } finally { key.fill(0) }
        }
    }

    private fun changeRecentlyDeleted(item: VaultItem, permanently: Boolean, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: run { onComplete("Vault locked."); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(session.isUnlocked && !hideContent)
                synchronized(previewCacheLock) {
                    val repository = AndroidVaultRepository(applicationContext, key)
                    if (permanently) {
                        check(repository.recentlyDeleted().any { it.id == item.id })
                        repository.deleteFromVault(item)
                    } else repository.restoreRecentlyDeleted(item.id)
                }
            }
            key.fill(0)
            runOnUiThread { onComplete(if (result.isSuccess) if (permanently) "Deleted permanently." else "Restored to Vault."
                else "Unable to update this deleted item.") }
        }
    }

    private fun unlockWithBiometrics() {
        if (!biometrics.isEnabled) return
        runCatching {
            val cipher = biometrics.newDecryptCipher()
            val prompt = newBiometricPrompt(BiometricPurpose.UNLOCK)
            prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Private Gallery")
                .setSubtitle("Unlock your Vault")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN")
                .build(),
            BiometricPrompt.CryptoObject(cipher),
        ) }.onFailure { biometricAttempts.lock(); activeBiometricPrompt = null;
            android.util.Log.w("PGAuth", "Biometric prompt unavailable: ${it.javaClass.simpleName}") }
    }

    private fun enrollBiometrics() {
        if (sessionKey == null) return
        val cipher = biometrics.newEncryptCipher()
        val prompt = newBiometricPrompt(BiometricPurpose.ENROLL)
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric unlock")
                .setSubtitle("Use biometrics to unlock Private Gallery")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(cipher),
        )
    }

    internal fun beginPasscodeEntry() {
        automaticBiometricPromptAttempted=true
        biometricAttempts.lock()
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt=null
    }

    private fun triggerAutomaticBiometricPromptIfNeeded() {
        if (!::keys.isInitialized || !BiometricPromptPolicy.shouldAutoPrompt(
                isLocked = !session.isUnlocked && route == Route.LOCK,
                biometricEnabled = biometricEnabled,
                alreadyPromptedForLockEntry = automaticBiometricPromptAttempted,
            )
        ) return
        automaticBiometricPromptAttempted = true
        val automaticIntent=biometricAttempts.begin()
        window.decorView.post {
            if (biometricAttempts.isCurrent(automaticIntent) && !isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && route == Route.LOCK && !session.isUnlocked) unlockWithBiometrics()
        }
    }

}

internal enum class Route { SETUP, RECOVERY_KEY_SETUP, BIOMETRIC_SETUP, LOCK, RECOVER, GALLERY, VAULT, FAVOURITE, BROWSER, SETTINGS }
private enum class BiometricPurpose { UNLOCK, ENROLL }

private fun AppNavigationDestination.matches(route: Route): Boolean = when (this) {
    AppNavigationDestination.GALLERY -> route == Route.GALLERY
    AppNavigationDestination.VAULT -> route == Route.VAULT
    AppNavigationDestination.FAVOURITE -> route == Route.FAVOURITE
    AppNavigationDestination.BROWSER -> route == Route.BROWSER
    AppNavigationDestination.SETTINGS -> route == Route.SETTINGS
}

private sealed interface ViewerRequest {
    data class Gallery(val entries: List<ViewerMediaEntry>, val initialIndex: Int) : ViewerRequest
    data class Vault(val entries: List<ViewerMediaEntry>, val items: Map<String, VaultItem>, val initialIndex: Int, val current: () -> Boolean) : ViewerRequest
}

@Composable
private fun PrivateGalleryApp(
    route: Route,
    onCreatePin: (CharArray) -> Result<Unit>,
    onUnlock: (CharArray) -> Result<Unit>,
    onChangePin: (CharArray, CharArray) -> Result<Unit>,
    onRecoverWithOfflineKey: (CharArray, CharArray) -> Result<Unit>,
    onFinishRecoveryKeySetup: () -> Unit,
    onOpenRecovery: () -> Unit,
    onCloseRecovery: () -> Unit,
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onLoadCollections: ((List<VaultCollection>) -> Unit) -> Unit,
    onLoadFavouriteCollection: ((VaultCollection?) -> Unit) -> Unit,
    onCreateCollection: (String, (Result<VaultCollection>) -> Unit) -> Unit,
    onAddItemsToCollection: (String, List<String>, (String) -> Unit) -> Unit,
    onRemoveItemsFromCollection: (String, List<String>, (String) -> Unit) -> Unit,
    onRenameCollection: (String, String, (String) -> Unit) -> Unit,
    onDeleteCollection: (String, (String) -> Unit) -> Unit,
    onLoadCollectionItems: (String, (List<VaultItem>) -> Unit) -> Unit,
    onReadForViewing: (VaultItem, (Result<ByteArray>) -> Unit) -> Unit,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    onLoadImageEdit: (VaultItem, (ImageEditState?) -> Unit) -> Unit,
    onApplyImageCrop: (VaultItem, NormalizedCrop, (Result<ImageEditState>) -> Unit) -> Unit,
    onUndoImageCrop: (VaultItem, (Result<ImageEditState?>) -> Unit) -> Unit,
    onResetImageCrop: (VaultItem, (Result<Unit>) -> Unit) -> Unit,
    onRestore: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onDelete: (VaultItem, (String) -> Unit) -> Unit,
    biometricEnabled: Boolean,
    onBiometricUnlock: () -> Unit,
    onEnrollBiometrics: () -> Unit,
    onFinishSetup: () -> Unit,
    autoLockTimeout: AutoLockTimeout,
    appTheme: AppTheme,
    allowScreenshots: Boolean,
    updateStatus: String,
    updateLastChecked: String,
    updateAvailable: Boolean,
    deviceMediaAccessAvailable: Boolean,
    onRequestDeviceMediaAccess: () -> Unit,
    onDeviceMediaPages: (String?) -> Flow<PagingData<DeviceMediaItem>>,
    onLoadDeviceAlbums: suspend () -> List<DeviceAlbum>,
    onLoadDeviceThumbnail: (DeviceMediaItem, (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenFavourite: () -> Unit,
    onOpenBrowser: () -> Unit,
    onAutoLockTimeoutChanged: (AutoLockTimeout) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onAllowScreenshotsChanged: (Boolean) -> Unit,
    onBrowserSearchEngineChanged: (BrowserSearchEngine) -> Unit,
    onClearBrowserDataOnLockChanged: (Boolean) -> Unit,
    onClearBrowserData: () -> Unit,
    browserSearchEngine: BrowserSearchEngine,
    clearBrowserDataOnLock: Boolean,
    browserSaveHistory: Boolean,
    onBrowserSaveHistoryChanged: (Boolean) -> Unit,
    existingBrowserWebView: WebView?,
    onBrowserWebViewReady: (WebView) -> Unit,
    onBrowserFullscreenExitChanged: ((() -> Unit)?) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    recoveryKeyConfigured: Boolean,
    recoveryKeyForSetup: String?,
    onSaveBrowserSource: (uk.co.traynor.privategallery.core.vault.VaultImportSource, (String) -> Unit) -> Unit,
    requireVpnForBrowsing: Boolean,
    vpnConnected: Boolean,
    onImportWireGuardProfile: () -> Unit,
    vpnProfileStatus: String,
    vpnConnectionState: VpnConnectionState,
    browserAutoConnectVpn: Boolean,
    onBrowserAutoConnectVpnChanged: (Boolean) -> Unit,
    onBrowserRequireVpnChanged: (Boolean) -> Unit,
    onSetFavouriteCollection: (String, (String) -> Unit) -> Unit,
    vpnProfiles: List<VpnProfileSummary>,
    onSelectVpnProfile: (String) -> Unit,
    onRemoveVpnProfile: (String) -> Unit,
    bookmarks: List<BrowserBookmark>,
    onAddBookmark: (String, String, (String) -> Unit) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    browserV2Session: BrowserV2Session,
    onLoadBrowserHistory: ((List<uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry>) -> Unit) -> Unit,
    onClearBrowserHistory: (() -> Unit) -> Unit,
    onRecordBrowserHistory: (String, String) -> Unit,
    vpnPreparing: Boolean,
    vpnPermissionRequired: Boolean,
    onConnectBrowserVpn: () -> Unit,
    onSaveEditedCopy: (VaultItem, ByteArray, () -> Boolean, (Result<VaultItem>) -> Unit) -> Unit,
    onReadForEditing: (VaultItem, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit,
    onSaveRemoteCopy: (VaultItem, ByteArray, () -> Boolean, (Result<VaultItem>) -> Unit) -> Unit,
    onReadVideoForViewing: (VaultItem, () -> Boolean, (Int) -> Unit, (Result<ByteArray>) -> Unit) -> Unit,
    onOpenVideoSession: (VaultItem, () -> Boolean, (Result<uk.co.traynor.privategallery.core.vault.VaultVideoSession>) -> Unit) -> Unit,
    onSaveAiCopy: (VaultItem, ByteArray, uk.co.traynor.privategallery.core.editor.AiEditProvenance, () -> Boolean, (Result<VaultItem>) -> Unit) -> Unit,
    onPrepareBrowserUpload: (List<VaultItem>, (Result<List<Uri>>) -> Unit) -> Unit,
    onClearBrowserUpload: () -> Unit,
    hideContent: Boolean,
    secretDiscovered: Boolean,
    onHideContentChanged: (Boolean) -> Unit,
    onSecretDiscoveryChanged: (Boolean) -> Unit,
    onAuthenticateSensitive: ((Boolean) -> Unit) -> Unit,
    onCancelSensitiveAuthentication: () -> Unit,
    onVerifySecretPin: (CharArray) -> Boolean,
    onGenerateImage: (uk.co.traynor.privategallery.core.editor.GenerationRequest, (String) -> Unit, (Result<VaultItem>) -> Unit) -> (() -> Unit) = { _, _, _ -> {} },
    onChooseBackupExport: () -> Unit,
    backupExportUri: Uri?,
    onCancelBackupExport: () -> Unit,
    onExportBackup: (CharArray) -> Unit,
    backupStatus: String,
    onChooseBackupRestore: () -> Unit,
    backupRestoreUri: Uri?,
    onCancelBackupRestore: () -> Unit,
    onRestoreBackup: (CharArray, CharArray, (Boolean) -> Unit) -> Unit,
    onLoadRecentlyDeleted: ((List<VaultItem>) -> Unit) -> Unit,
    onMoveItemsToRecentlyDeleted: (List<String>, (String) -> Unit) -> Unit,
    onChangeRecentlyDeleted: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onRestoreSelectedVaultCopies: (List<String>, (String) -> Unit) -> Unit,
    onLeaveHidden: (AppNavigationDestination) -> Unit,
    hiddenState: uk.co.traynor.privategallery.ui.HiddenVaultState,
    primaryAuthority: () -> Boolean,
    onReturnToPrimary: () -> Unit,
    createPrimaryCopy: ((uk.co.traynor.privategallery.core.hidden.HiddenSession) -> uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?)? = null,
    primaryGallerySelected: Boolean = false,
    onGallerySourceSelected: (Boolean) -> Unit = {},
    phoneMoveStatus: String = "",
    onCancelGalleryAuthentication: () -> Unit = {},
    onCapturePrimaryEditorAuthority: () -> (() -> Boolean) = {{true}},
    onInvalidatePrimaryPresentation: () -> Unit = {},
    onDirectHiddenUnlock: ((CharArray) -> Result<Unit>)? = null,
    onTransferToHidden: (List<String>, Boolean, () -> Boolean, (Int, Int) -> Unit, (String)->Unit)->Unit = { _,_,_,_,done -> done("Unavailable") },
) {
    // Acceptance aids are opt-in for this app composition and never saved to preferences.
    val metadataActivity=androidx.activity.compose.LocalActivity.current as? MainActivity
    val hiddenContext = LocalContext.current.applicationContext
    val hiddenPolicy = remember(hiddenContext) { uk.co.traynor.privategallery.ui.HiddenPreferences(hiddenContext) }
    var hiddenTimeout by remember { mutableStateOf(hiddenPolicy.timeout) }
    val primaryAvailable = route in setOf(Route.GALLERY, Route.VAULT, Route.FAVOURITE, Route.BROWSER, Route.SETTINGS)

    val primaryPresentation = primaryAuthority() && (!hiddenState.selected || (route == Route.GALLERY && primaryGallerySelected && !hiddenState.visible))
    val openScopedVault: () -> Unit = { if(hiddenState.selected) {hiddenState.select(Route.VAULT)} else onOpenVault() }
    val openScopedFavourite: () -> Unit = { if(hiddenState.selected) {hiddenState.select(Route.FAVOURITE)} else onOpenFavourite() }
    val hiddenImportScope = rememberCoroutineScope()
    val importToSelectedVault: (List<Uri>,(String)->Unit)->Unit = { uris,done ->
        if(!hiddenState.selected) onImport(uris,done)
        else {
            // Capture the authenticated epoch before queuing any IO. Never fall back to Primary.
            val repository=runCatching {check(hiddenState.authenticated && hiddenState.session.isUnlocked);uk.co.traynor.privategallery.core.hidden.HiddenRepository(hiddenContext,hiddenState.session)}.getOrNull()
            if(repository==null) done("Unlock Hidden Vault before importing. Originals were retained.")
            else hiddenImportScope.launch(Dispatchers.IO) {
                var imported=0
                uris.forEach {uri ->try {val item=repository.import(uri);repository.verify(item.id);imported++}catch(_:Exception){}}
                withContext(Dispatchers.Main) {
                    if(hiddenState.selected && hiddenState.authenticated && hiddenState.session.isUnlocked) done("Imported $imported of ${uris.size} into Hidden. Originals were retained.")
                    else done("Import ended. Original files were retained.")
                }
            }
        }
    }
    var browserStaticContentHost by remember { mutableStateOf(false) }
    var browserLayoutColours by remember { mutableStateOf(false) }
    var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }
    var cropRevision by remember { mutableStateOf(0) }
    var collectionTarget by remember { mutableStateOf<String?>(null) }
    var viewerCollections by remember { mutableStateOf<List<VaultCollection>>(emptyList()) }
    var favouriteLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route, hideContent, hiddenState.selected, primaryGallerySelected, hiddenState.visible) {
        if (hideContent || !primaryPresentation || !primaryAvailable) {
            viewerRequest = null
            collectionTarget = null
            viewerCollections = emptyList()
            favouriteLabel = null
            return@LaunchedEffect
        }
        if (route == Route.LOCK || route == Route.SETUP || route == Route.RECOVERY_KEY_SETUP || route == Route.BIOMETRIC_SETUP || route == Route.RECOVER) viewerRequest = null
        if (!hiddenState.selected && (route == Route.GALLERY || route == Route.VAULT || route == Route.FAVOURITE || route == Route.BROWSER || route == Route.SETTINGS)) {
            onLoadFavouriteCollection { favouriteLabel = it?.name }
        }
    }
    Box(Modifier.fillMaxSize()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    // Do not compose Primary/device content behind an active Hidden entry or scoped view.
    if (hiddenState.selected && hiddenState.visible && primaryAvailable && BuildConfig.HIDDEN_ENABLED) {
        Box(Modifier.fillMaxSize())
    } else when (route) {
    Route.SETUP -> PinSetup(onCreatePin, onChooseBackupRestore, backupRestoreUri, onCancelBackupRestore, onRestoreBackup)
    Route.RECOVERY_KEY_SETUP -> RecoveryKeySetup(checkNotNull(recoveryKeyForSetup), onFinishRecoveryKeySetup)
    Route.BIOMETRIC_SETUP -> BiometricSetup(onEnrollBiometrics, onFinishSetup)
    Route.LOCK -> Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {PinUnlock(onUnlock, biometricEnabled, onBiometricUnlock, onForgotPin = onOpenRecovery, onDirectHiddenUnlock = onDirectHiddenUnlock)}
    }
    Route.RECOVER -> RecoveryKeyUnlock(onRecoverWithOfflineKey, onCancel = onCloseRecovery)
    Route.GALLERY, Route.VAULT, Route.FAVOURITE, Route.BROWSER, Route.SETTINGS -> Box(Modifier.fillMaxSize()) {
    // Register the destination fallback before child handlers: viewer, settings,
    // WebView history and fullscreen consume Back first, including system gestures.
    androidx.activity.compose.BackHandler(route != Route.GALLERY && viewerRequest == null) { onOpenGallery() }
    ProtectedAppShell(route, if (hideContent || hiddenState.selected) null else favouriteLabel, hideNavigation = route == Route.BROWSER && !hideContent && !hiddenState.selected, onNavigate = { destination ->
        viewerRequest=null;collectionTarget=null;viewerCollections=emptyList()
        when (destination) {
            AppNavigationDestination.GALLERY -> onOpenGallery()
            AppNavigationDestination.VAULT -> openScopedVault()
            AppNavigationDestination.FAVOURITE -> openScopedFavourite()
            AppNavigationDestination.BROWSER -> onOpenBrowser()
            AppNavigationDestination.SETTINGS -> onOpenSettings()
        }
    }) { contentPadding ->
        if (hiddenState.selected && route in setOf(Route.VAULT,Route.FAVOURITE)) {
            // Primary unlock/configuration can restore this route. Never compose Primary data in Hidden mode.
            LaunchedEffect(route) {hiddenState.select(route)}
            Box(Modifier.fillMaxSize())
        } else if (hideContent && route != Route.SETTINGS) {
            val title = when (route) { Route.GALLERY -> "Gallery"; Route.VAULT -> "Vault"; Route.FAVOURITE -> "Favourite"; else -> "Browser" }
            val empty = when (route) { Route.GALLERY -> "No photos"; Route.VAULT -> "Your Vault is empty"; Route.FAVOURITE -> "No favourites"; else -> "No recent pages" }
            Column(Modifier.fillMaxSize().padding(contentPadding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GalleryPageTitle(title, empty)
            }
        } else when (route) {
            Route.GALLERY -> Column(Modifier.fillMaxSize().padding(contentPadding)) {
                if((!hiddenState.selected || !primaryGallerySelected) && phoneMoveStatus.isNotBlank()) Text(phoneMoveStatus,Modifier.padding(12.dp))
                if (hiddenState.selected) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = !primaryGallerySelected, onClick = { viewerRequest = null; onGallerySourceSelected(false) }, label = { Text("Phone Gallery") })
                    FilterChip(selected = primaryGallerySelected, onClick = { viewerRequest = null; onGallerySourceSelected(true) }, label = { Text("Primary Vault") })
                }
                if (hiddenState.selected && primaryGallerySelected) {
                    if (primaryPresentation) {
                        VaultHome(onLock, onImport, onLoadItems, onLoadCollections, onCreateCollection, onAddItemsToCollection, onRemoveItemsFromCollection, onRenameCollection, onDeleteCollection, onLoadCollectionItems, onLoadPreview, cropRevision, biometricEnabled, onEnrollBiometrics, onSetFavouriteCollection, onFavouriteStateChanged = { onLoadFavouriteCollection { favouriteLabel = it?.name } }, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index, onCapturePrimaryEditorAuthority()) }, modifier = Modifier.weight(1f), onGenerateImage = onGenerateImage, onLoadRecentlyDeleted = onLoadRecentlyDeleted, onMoveItemsToRecentlyDeleted = onMoveItemsToRecentlyDeleted, onChangeRecentlyDeleted = onChangeRecentlyDeleted, onRestoreSelectedVaultCopies = onRestoreSelectedVaultCopies, onTransferToHidden = if(hiddenState.selected && hiddenState.session.isUnlocked) onTransferToHidden else null)
                    } else Text("Unlock Primary Vault to browse encrypted media.", Modifier.padding(24.dp))
                } else GalleryHome(deviceMediaAccessAvailable, onRequestDeviceMediaAccess, onDeviceMediaPages, onLoadDeviceAlbums, onLoadDeviceThumbnail,
                    importToSelectedVault,
                    onMove,
                    onOpenViewer = { entries, index -> viewerRequest = ViewerRequest.Gallery(entries, index) },
                    modifier = Modifier.weight(1f), hiddenDestination = hiddenState.selected, allowMove = true)
            }
            Route.VAULT -> VaultHome(onLock, onImport, onLoadItems, onLoadCollections, onCreateCollection, onAddItemsToCollection, onRemoveItemsFromCollection, onRenameCollection, onDeleteCollection, onLoadCollectionItems, onLoadPreview, cropRevision, biometricEnabled, onEnrollBiometrics, onSetFavouriteCollection, onFavouriteStateChanged = { onLoadFavouriteCollection { favouriteLabel = it?.name } }, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index, onCapturePrimaryEditorAuthority()) }, modifier = Modifier.padding(contentPadding), onGenerateImage = onGenerateImage, onLoadRecentlyDeleted = onLoadRecentlyDeleted, onMoveItemsToRecentlyDeleted = onMoveItemsToRecentlyDeleted, onChangeRecentlyDeleted = onChangeRecentlyDeleted, onRestoreSelectedVaultCopies = onRestoreSelectedVaultCopies)
            Route.FAVOURITE -> FavouriteHome(onLoadFavouriteCollection, onLoadItems, onLoadCollectionItems, onAddItemsToCollection, onRemoveItemsFromCollection, onLoadPreview, cropRevision, onOpenVault, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index, onCapturePrimaryEditorAuthority()) }, modifier = Modifier.padding(contentPadding))
            Route.BROWSER -> if (hiddenState.selected) {
                if (hiddenState.session.isUnlocked && hiddenState.authenticated) uk.co.traynor.privategallery.ui.HiddenBrowserHome(
                    hiddenState.session,onOpenGallery,openScopedVault,openScopedFavourite,onOpenSettings,Modifier.padding(contentPadding))
                else Column(Modifier.padding(contentPadding).padding(16.dp)) { Text("Unlock Hidden Vault before browsing."); Button(onClick=openScopedVault) {Text("Unlock Hidden")} }
            } else BrowserV2ProductionDestination(
                session = browserV2Session,
                connectionPresentation = BrowserConnectionPresentation.from(requireVpnForBrowsing, vpnConnectionState, vpnPermissionRequired, vpnPreparing),
                onConnectVpn = onConnectBrowserVpn,
                onOpenGallery = onOpenGallery,
                onOpenVault = openScopedVault,
                onOpenFavourite = openScopedFavourite,
                favouriteLabel = if(hiddenState.selected) "Favourite" else favouriteLabel ?: "Favourite",
                staticContentHost = BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS && browserStaticContentHost,
                acceptanceProbeEnabled = BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS && browserLayoutColours,
                searchEngine = browserSearchEngine,
                onOpenBrowserSettings = onOpenSettings,
                browserSettings = {
                    uk.co.traynor.privategallery.ui.BrowserSettingsContent(
                        browserSearchEngine, onBrowserSearchEngineChanged, browserSaveHistory, onBrowserSaveHistoryChanged,
                        requireVpnForBrowsing, onBrowserRequireVpnChanged, browserAutoConnectVpn, onBrowserAutoConnectVpnChanged,
                        clearBrowserDataOnLock, onClearBrowserDataOnLockChanged, onClearBrowserData, browserV2Session.contentBlocker,
                    )
                },
                onSaveToVault = { source,done -> if(hiddenState.selected) done("Open Hidden Vault and use Add media. Browser import to Primary is unavailable while Hidden is selected.") else onSaveBrowserSource(source,done) },
                onLoadVaultItems = { done -> if(hiddenState.selected) done(emptyList()) else onLoadItems(done) },
                onLoadVaultPreview = { item,done -> if(hiddenState.selected) done(Result.failure(IllegalStateException("Hidden content is not exposed in Browser"))) else onLoadPreview(item,done) },
                onPrepareVaultUpload = { request,done -> if(hiddenState.selected) done(Result.failure(IllegalStateException("Vault upload is unavailable while Hidden is selected"))) else onPrepareBrowserUpload(request,done) },
                onClearVaultUpload = onClearBrowserUpload,
                onHistoryVisited = onRecordBrowserHistory,
                saveHistory = browserSaveHistory,
                onSaveHistoryChanged = onBrowserSaveHistoryChanged,
                bookmarks = bookmarks,
                onAddBookmark = onAddBookmark,
                onRemoveBookmark = onRemoveBookmark,
                onLoadHistory = onLoadBrowserHistory,
                onClearHistory = onClearBrowserHistory,
                modifier = Modifier.padding(contentPadding),
            )
            Route.SETTINGS -> SettingsHome(autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, updateAvailable, biometricEnabled, recoveryKeyConfigured, browserSearchEngine, clearBrowserDataOnLock, onAutoLockTimeoutChanged, onThemeChanged, onAllowScreenshotsChanged, onBrowserSearchEngineChanged, onClearBrowserDataOnLockChanged, onClearBrowserData, onCheckForUpdates, onDownloadUpdate, onChangePin, onLock, browserAutoConnectVpn, requireVpnForBrowsing, onBrowserAutoConnectVpnChanged, onBrowserRequireVpnChanged, onImportWireGuardProfile, vpnProfileStatus, vpnConnectionState, vpnProfiles, onSelectVpnProfile, onRemoveVpnProfile, modifier = Modifier.padding(contentPadding), browserSaveHistory = browserSaveHistory, onBrowserSaveHistoryChanged = onBrowserSaveHistoryChanged, browserStaticContentHost = browserStaticContentHost, onBrowserStaticContentHostChanged = { browserStaticContentHost = it }, browserLayoutColours = browserLayoutColours, onBrowserLayoutColoursChanged = { browserLayoutColours = it }, contentBlocker = browserV2Session.contentBlocker, hideContent = hideContent, secretDiscovered = secretDiscovered, onHideContentChanged = onHideContentChanged, onSecretDiscoveryChanged = onSecretDiscoveryChanged, onAuthenticateSensitive = onAuthenticateSensitive, onCancelSensitiveAuthentication = onCancelSensitiveAuthentication, onVerifySecretPin = onVerifySecretPin, onChooseBackupExport = onChooseBackupExport, backupExportUri = backupExportUri, onCancelBackupExport = onCancelBackupExport, onExportBackup = onExportBackup, backupStatus = backupStatus, onOpenHidden = { onInvalidatePrimaryPresentation(); hiddenState.select() },
                storagePanel = ({ uk.co.traynor.privategallery.ui.VaultStoragePanel {done->
                    if(hiddenState.selected) {
                        val currentSession=hiddenState.session
                        val authority=runCatching {currentSession.authority()}.getOrNull()
                        if(authority!=null)hiddenImportScope.launch {
                            val result=withContext(Dispatchers.IO){runCatching {uk.co.traynor.privategallery.core.hidden.HiddenRepository(hiddenContext,currentSession).storageSummary()}}
                            if(runCatching {currentSession.beginOperation(authority).use {currentSession.isCurrent(it)}}.getOrDefault(false))done(result)
                        }
                    } else metadataActivity?.primaryStorageSummary(done)
                } }),
                hiddenBrowserSettings = if(hiddenState.selected && hiddenState.authenticated && hiddenState.session.isUnlocked) ({ uk.co.traynor.privategallery.ui.HiddenBrowserPreferences() }) else null,
                allowHiddenDiagnostics = hiddenState.selected && hiddenState.authenticated && hiddenState.session.isUnlocked,
                hiddenSettings = if(hiddenState.selected && hiddenState.authenticated) ({
                    HiddenPolicySettings(hiddenTimeout,onTimeoutChanged={ value ->
                        if(hiddenState.session.isUnlocked) { hiddenPolicy.timeout=value;hiddenTimeout=value }
                    },onUsePrimary=onReturnToPrimary)
                }) else null)
            else -> Unit
        }
    }
    if (!hideContent && primaryPresentation) collectionTarget?.let { itemId ->
        GalleryMenuSheet("Add to collection", { collectionTarget = null }) {
            if (viewerCollections.isEmpty()) Text("Create a collection in Vault first.", Modifier.padding(24.dp))
            viewerCollections.forEach { collection ->
                SheetAction(collection.name, Icons.Default.Folder) { onAddItemsToCollection(collection.id, listOf(itemId)) { }; collectionTarget = null }
            }
        }
    }
    if (!hideContent) viewerRequest?.let { request ->
        when (request) {
            is ViewerRequest.Gallery -> FullscreenMediaViewer(
                entries = request.entries,
                source = MediaViewerSource.GALLERY,
                initialIndex = request.initialIndex,
                onClose = { viewerRequest = null },
                onCopyToVault = { entry -> entry.uri?.let { importToSelectedVault(listOf(it)) {} } },
                copyToVaultLabel = if(hiddenState.selected) "Copy to Hidden" else "Copy to Vault",
                onMoveToVault = {entry -> entry.uri?.let {onMove(listOf(it)) {};viewerRequest=null}},
                moveToVaultLabel = if(hiddenState.selected) "Move to Hidden" else "Move to Vault",
            )
            is ViewerRequest.Vault -> if (primaryPresentation && request.current()) FullscreenMediaViewer(
                entries = request.entries,
                source = MediaViewerSource.VAULT,
                initialIndex = request.initialIndex,
                onClose = { viewerRequest = null },
                onLoadMediaDetails = {id,done->metadataActivity?.primaryMediaDetails(id,done)},
                onAdjustMediaDate = {id,time,zone,done->metadataActivity?.adjustPrimaryMediaDate(id,time,zone,done)},
                onLoadProtectedBytes = { id, loaded -> request.items[id]?.let { onReadForViewing(it, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadVideoBytes = { id, cancelled, progress, loaded -> request.items[id]?.let { onReadVideoForViewing(it, cancelled, progress, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadVideoSession = { id, cancelled, loaded -> request.items[id]?.let { onOpenVideoSession(it, cancelled, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadImageEdit = { id, loaded -> request.items[id]?.let { onLoadImageEdit(it, loaded) } ?: loaded(null) },
                onApplyImageCrop = { id, crop, completed -> request.items[id]?.let { onApplyImageCrop(it, crop, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onUndoImageCrop = { id, completed -> request.items[id]?.let { onUndoImageCrop(it, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onResetImageCrop = { id, completed -> request.items[id]?.let { onResetImageCrop(it, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onCropChanged = { cropRevision++ },
                onLoadEditorBytes = { id, cancelled, loaded -> request.items[id]?.let { onReadForEditing(it, {cancelled() || !request.current() || viewerRequest !== request}, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing item"))) },
                onAddToCollection = { entry -> onLoadCollections { if (primaryAuthority() && primaryPresentation && viewerRequest === request) { viewerCollections = it; collectionTarget = entry.id } } },
                onSaveEditedCopy = { id, bytes, cancelled, completed ->
                    val original = request.items[id]
                    if (original == null) completed(Result.failure(IllegalStateException("Missing item")))
                    else onSaveEditedCopy(original, bytes, {cancelled() || !request.current() || viewerRequest !== request}) { result ->
                        completed(result.map { Unit })
                        result.onSuccess { copy ->
                            if (cancelled() || !request.current() || viewerRequest !== request) return@onSuccess
                            cropRevision++
                            viewerRequest = ViewerRequest.Vault(listOf(ViewerMediaEntry(copy.id, copy.mimeType)), mapOf(copy.id to copy), 0, request.current)
                        }
                    }
                },
                onSaveRemoteCopy = { id, bytes, cancelled, completed ->
                    val original = request.items[id]
                    if (original == null) completed(Result.failure(IllegalStateException("Missing item")))
                    else onSaveRemoteCopy(original, bytes, {cancelled() || !request.current() || viewerRequest !== request}) { result ->
                        completed(result.map { Unit })
                        result.onSuccess { copy ->
                            if (cancelled() || !request.current() || viewerRequest !== request) return@onSuccess
                            cropRevision++
                            viewerRequest = ViewerRequest.Vault(listOf(ViewerMediaEntry(copy.id, copy.mimeType)), mapOf(copy.id to copy), 0, request.current)
                        }
                    }
                },
                onSaveAiCopy = { id, bytes, provenance, cancelled, completed ->
                    val original = request.items[id]
                    if (original == null) completed(Result.failure(IllegalStateException("Missing item")))
                    else onSaveAiCopy(original, bytes, provenance, {cancelled() || !request.current() || viewerRequest !== request}) { result ->
                        completed(result.map { Unit })
                        result.onSuccess { copy ->
                            if (cancelled() || !request.current() || viewerRequest !== request) return@onSuccess
                            cropRevision++
                            viewerRequest = ViewerRequest.Vault(listOf(ViewerMediaEntry(copy.id, copy.mimeType)), mapOf(copy.id to copy), 0, request.current)
                        }
                    }
                },
                restrictedIds = request.items.values.filter { it.vaultOnly }.mapTo(mutableSetOf()) { it.id },
                onRestore = { entry -> request.items[entry.id]?.let { onRestore(it, false) {} } },
                onRestoreAndRemove = { entry -> request.items[entry.id]?.let { item -> onRestore(item, true) { cropRevision++; viewerRequest = null } } },
                onDeleteFromVault = { entry -> request.items[entry.id]?.let { item -> onDelete(item) { cropRevision++; viewerRequest = null } } },
            )
        }
    }
    }
    }
}
    if (hiddenState.selected && BuildConfig.HIDDEN_ENABLED) uk.co.traynor.privategallery.ui.HiddenVaultDialog(
        primaryAvailable = primaryAvailable, onDismiss = onReturnToPrimary, onReturnToPrimary = onReturnToPrimary, createPrimaryCopy = createPrimaryCopy,
        onBrowseDeviceGallery = { onGallerySourceSelected(false); hiddenState.outside(); onLeaveHidden(AppNavigationDestination.GALLERY) },
        externalSession = hiddenState.session, showEntry = hiddenState.visible,
        initialDestination = hiddenState.destination, onScopedDestinationChanged = {hiddenState.destination=it},
        onAuthenticationChanged = { hiddenState.authenticated = it },
        onNavigateOutside = { destination -> hiddenState.outside(); onLeaveHidden(destination) },
    )
    }
}

@Composable
internal fun ProtectedAppShell(
    selected: Route,
    favouriteLabel: String?,
    onNavigate: (AppNavigationDestination) -> Unit,
    hideNavigation: Boolean = selected == Route.BROWSER,
    destinations: List<AppNavigationDestination> = AppNavigationPolicy.destinations,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideNavigation) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.matches(selected),
                        onClick = { onNavigate(destination) },
                        icon = { Icon(when (destination) {
                            AppNavigationDestination.GALLERY -> Icons.Default.PhotoLibrary
                            AppNavigationDestination.VAULT -> Icons.Default.Lock
                            AppNavigationDestination.FAVOURITE -> Icons.Default.Favorite
                            AppNavigationDestination.BROWSER -> Icons.Default.Language
                            AppNavigationDestination.SETTINGS -> Icons.Default.Settings
                        }, contentDescription = null) },
                        label = { Text(AppNavigationPolicy.labelFor(destination, favouriteLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
internal fun GalleryHome(
    deviceMediaAccessAvailable: Boolean,
    onRequestDeviceMediaAccess: () -> Unit,
    onDeviceMediaPages: (String?) -> Flow<PagingData<DeviceMediaItem>>,
    onLoadDeviceAlbums: suspend () -> List<DeviceAlbum>,
    onLoadDeviceThumbnail: (DeviceMediaItem, (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Int) -> Unit,
    modifier: Modifier = Modifier,
    hiddenDestination: Boolean = false,
    allowMove: Boolean = true,
    onBrowsePrimary: (() -> Unit)? = null,
) {
    val sortContext=androidx.compose.ui.platform.LocalContext.current
    val sortPreferences=remember {sortContext.getSharedPreferences("private-gallery-media-ui",android.content.Context.MODE_PRIVATE)}
    var gallerySort by remember {mutableStateOf(MediaSort.decode(sortPreferences.getString("phone-sort",null)))}

    var status by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Map<Long, android.net.Uri>>(emptyMap()) }
    var galleryOverflowExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var density by rememberSaveable { mutableStateOf(ThumbnailDensity.COMPACT) }
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var albumMenuExpanded by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<DeviceAlbum>>(emptyList()) }
    var albumRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(deviceMediaAccessAvailable, albumRefresh) {
        albums = if (deviceMediaAccessAvailable) runCatching { onLoadDeviceAlbums() }.getOrDefault(emptyList()) else emptyList()
    }
    val deviceMediaFlow = remember(deviceMediaAccessAvailable, albumId,gallerySort) {
        if (deviceMediaAccessAvailable) onDeviceMediaPages(albumId) else flowOf(PagingData.empty())
    }
    val deviceMedia = deviceMediaFlow.collectAsLazyPagingItems()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA),
    ) { uris ->
        if (uris.isNotEmpty()) {
            status = "Encrypting selected media…"
            onImport(uris) { status = it }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GalleryTokens.MediaGap, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaHeader(
            "Gallery", if (selectionMode) "${selected.size} selected" else if (deviceMedia.itemCount > 0) "${deviceMedia.itemCount} loaded · On this device" else "On this device",
            onMenu = { galleryOverflowExpanded = true },
            sort=gallerySort,onSort={gallerySort=it;sortPreferences.edit().putString("phone-sort",it.name).apply()},
        )
        onBrowsePrimary?.let { browse ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Device Gallery · Copy to Hidden", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = browse) { Text("Primary Vault") }
            }
        }
        if (deviceMediaAccessAvailable && albums.isNotEmpty()) {
            Box {
                TextButton(onClick = { albumMenuExpanded = true }) {
                    Text("Album: ${albums.firstOrNull { it.id == albumId }?.name ?: "All media"}")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                androidx.compose.material3.DropdownMenu(expanded = albumMenuExpanded, onDismissRequest = { albumMenuExpanded = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("All media") }, onClick = {
                        albumId = null; selected = emptyMap(); selectionMode = false; albumMenuExpanded = false
                    })
                    albums.forEach { album ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(album.name) }, onClick = {
                            albumId = album.id; selected = emptyMap(); selectionMode = false; albumMenuExpanded = false
                        })
                    }
                }
            }
        }
        if (galleryOverflowExpanded) GalleryMenuSheet("Gallery", onDismiss = { galleryOverflowExpanded = false }) {
            SheetSection("Media")
            SheetAction("Select items", Icons.Default.CheckCircle, enabled = deviceMedia.itemCount > 0) {
                selectionMode = true; galleryOverflowExpanded = false
            }
            SheetAction("Refresh", Icons.Default.Refresh) { deviceMedia.refresh(); albumRefresh++; galleryOverflowExpanded = false }
            SheetAction("Add with Photo Picker", Icons.Default.AddPhotoAlternate) {
                galleryOverflowExpanded = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            SheetSection("View")
            ThumbnailSizeChoices(density) { density = it }
        }
        if (!deviceMediaAccessAvailable) {
            GalleryCard {
                GalleryCardHeading("Show your device media")
                Text("Allow Private Gallery to show your device photos and videos. This does not let the app silently delete originals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRequestDeviceMediaAccess, modifier = Modifier.fillMaxWidth()) { Text("Allow Gallery access") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Select with Android Photo Picker") }
            }
        } else {
            if (selectionMode || selected.isNotEmpty()) {
                Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${selected.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { selected = emptyMap(); selectionMode = false }) { Text("Cancel") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    status = "Encrypting selected media…"
                                    onImport(selected.values.toList()) { result ->
                                        status = result
                                        selected = emptyMap(); selectionMode = false
                                        deviceMedia.refresh()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(if(hiddenDestination) "Copy to Hidden" else "Copy to Vault") }
                            if (allowMove) androidx.compose.material3.OutlinedButton(
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    status = "Encrypting and verifying…"
                                    onMove(selected.values.toList()) { result ->
                                        status = result
                                        selected = emptyMap(); selectionMode = false
                                        deviceMedia.refresh()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(if(hiddenDestination) "Move to Hidden" else "Move to Vault") }
                        }
                    }
                }
            }
            when {
                deviceMedia.loadState.refresh is androidx.paging.LoadState.Loading && deviceMedia.itemCount == 0 -> GalleryLoadingState("Loading device media…")
                deviceMedia.loadState.refresh is androidx.paging.LoadState.Error && deviceMedia.itemCount == 0 -> GalleryCard {
                    Text("Unable to read device media. Try again or review Gallery access.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { deviceMedia.retry() }) { Text("Retry") }
                    androidx.compose.material3.OutlinedButton(onClick = onRequestDeviceMediaAccess) { Text("Review Gallery access") }
                }
                deviceMedia.itemCount == 0 -> GalleryCard {
                    Text(if (albumId == null) "No accessible media" else "No media in this album", style = MaterialTheme.typography.titleMedium)
                    Text(if (albumId == null) "Photos and videos you allow Private Gallery to access will appear here."
                        else "Choose another album or refresh Gallery access.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = density.minSize.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(GalleryTokens.MediaGap),
                    verticalArrangement = Arrangement.spacedBy(GalleryTokens.MediaGap),
                ) {
                    items(deviceMedia.itemCount, key = deviceMedia.itemKey { it.uri.toString() }) { index ->
                        deviceMedia[index]?.let { item ->
                            DeviceMediaTile(
                                item = item,
                                selected = item.id in selected,
                                onLoadThumbnail = onLoadDeviceThumbnail,
                                onClick = {
                                if (!selectionMode && selected.isEmpty()) {
                                    val entries = deviceMedia.itemSnapshotList.items.map { loaded ->
                                        ViewerMediaEntry(loaded.id.toString(), loaded.mimeType, loaded.uri)
                                    }
                                    val index = entries.indexOfFirst { it.id == item.id.toString() }
                                    if (index >= 0) onOpenViewer(entries, index)
                                }
                                else selected = selected.toggle(item)
                                },
                                onLongClick = { selectionMode = true; selected = selected.toggle(item) },
                            )
                        }
                    }
                }
            }
            if (status.isNotBlank()) Text(
                status,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Map<Long, android.net.Uri>.toggle(item: DeviceMediaItem): Map<Long, android.net.Uri> =
    if (item.id in this) this - item.id else this + (item.id to item.uri)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceMediaTile(
    item: DeviceMediaItem,
    selected: Boolean,
    onLoadThumbnail: (DeviceMediaItem, (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var thumbnail by remember(item.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(item.id) { onLoadThumbnail(item) { thumbnail = it } }
    Card(
        modifier = Modifier.semantics { contentDescription = item.displayName; this.selected = selected }.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select media"),
        shape = GalleryTokens.ThumbnailShape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
            thumbnail?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Text(if (item.kind == DeviceMediaKind.VIDEO) "Video" else "Photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.kind == DeviceMediaKind.VIDEO) {
                Surface(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), shape = RoundedCornerShape(4.dp)) {
                    Text("▶ ${formatDuration(item.durationMillis)}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            if (selected) Surface(
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(4.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) { Text("✓", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun PinSetup(onCreatePin: (CharArray) -> Result<Unit>, onChooseBackupRestore: () -> Unit,
                     backupRestoreUri: Uri?, onCancelBackupRestore: () -> Unit,
                     onRestoreBackup: (CharArray, CharArray, (Boolean) -> Unit) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PinPage(
        title = "Set up Private Gallery",
        detail = "Selected media is encrypted in this device's private storage. If you lose your PIN and biometric access is unavailable, the Vault may be unrecoverable.",
        pin = pin,
        onPinChange = { pin = it },
        confirmation = confirmation,
        onConfirmationChange = { confirmation = it },
        action = "Create PIN",
        message = message,
    ) {
        if (pin.length < 6 || pin != confirmation) {
            message = "Use and confirm a PIN of at least six digits."
        } else {
            onCreatePin(pin.toCharArray()).onSuccess {
                pin = ""
                confirmation = ""
            }.onFailure { message = "Unable to create the vault." }
        }
    }
    if (backupRestoreUri == null) {
        // A fresh installation can restore ciphertext before generating a new device-local PIN.
        Box(Modifier.fillMaxSize().padding(bottom = 12.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            TextButton(onClick = onChooseBackupRestore) { Text("Restore encrypted Vault backup") }
        }
    } else {
        var recovery by remember(backupRestoreUri) { mutableStateOf("") }
        var newPin by remember(backupRestoreUri) { mutableStateOf("") }
        var confirm by remember(backupRestoreUri) { mutableStateOf("") }
        var restoring by remember(backupRestoreUri) { mutableStateOf(false) }
        var error by remember(backupRestoreUri) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!restoring) onCancelBackupRestore() },
            title = { Text("Restore encrypted Vault") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the offline recovery key saved with this Vault. Choose a new PIN for this device.")
                OutlinedTextField(recovery, { recovery = it }, label = { Text("Recovery key") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit) }, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit) }, label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !restoring, onClick = {
                if (newPin.length < 6 || newPin != confirm || recovery.isBlank()) error = "Enter your recovery key and confirm a PIN of at least six digits."
                else {
                    restoring = true
                    onRestoreBackup(recovery.toCharArray(), newPin.toCharArray()) { success ->
                        restoring = false
                        if (!success) error = "Unable to authenticate this backup. Check the key and archive."
                    }
                    recovery = ""; newPin = ""; confirm = ""
                }
            }) { Text(if (restoring) "Restoring…" else "Restore Vault") } },
            dismissButton = { TextButton(enabled = !restoring, onClick = onCancelBackupRestore) { Text("Cancel") } })
    }
}

@Composable
private fun BiometricSetup(onEnrollBiometrics: () -> Unit, onFinish: () -> Unit) {
    LockSurface {
        GalleryPageTitle("One more option", "Use biometrics?")
        Text("Use your device biometrics to unlock Private Gallery more quickly. Your PIN remains available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onEnrollBiometrics, modifier = Modifier.fillMaxWidth()) { Text("Enable biometric unlock") }
        androidx.compose.material3.OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
    }
}

@Composable
internal fun PinUnlock(onUnlock: (CharArray) -> Result<Unit>, biometricEnabled: Boolean, onBiometricUnlock: () -> Unit, onForgotPin: () -> Unit, onDirectHiddenUnlock: ((CharArray) -> Result<Unit>)? = null) {
    var directHidden by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var entryGeneration by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    DisposableEffect(owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){entryGeneration++;directHidden=false;pin="";message=null;busy=false}
        }
        owner.lifecycle.addObserver(observer)
        onDispose {pin="";directHidden=false;owner.lifecycle.removeObserver(observer)}
    }
    uk.co.traynor.privategallery.ui.PasscodeKeypad(pin=pin,onPinChange={value->
        activity?.beginPasscodeEntry();pin=value
    },onSubmit={
        if(!busy&&pin.isNotEmpty()) {
            busy=true;activity?.beginPasscodeEntry()
            val input=pin.toCharArray();pin=""
            val generation=entryGeneration
            val authenticate=if(directHidden)onDirectHiddenUnlock ?: onUnlock else onUnlock
            scope.launch {
                kotlinx.coroutines.yield()
                try {if(generation==entryGeneration&&owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) authenticate(input).onSuccess {message=null}.onFailure {message="Unable to unlock. Check your PIN."}}
                finally {input.fill('\u0000');busy=false}
            }
        }
    },enabled=!busy,message=message,onDiscreetEntry=if(onDirectHiddenUnlock==null)null else ({
        activity?.beginPasscodeEntry();directHidden=true;pin="";message=null
    }),secondary={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={activity?.beginPasscodeEntry();onForgotPin()}){Text("Forgot PIN?")}
            if(biometricEnabled&&!directHidden)TextButton(onClick={pin="";onBiometricUnlock()}){Text("Use fingerprint")}
        }
    })
}

@Composable
private fun RecoveryKeySetup(recoveryKey: String, onFinish: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    LockSurface {
        GalleryPageTitle("Keep this offline", "Recovery key")
        Text("This key can restore access if you forget your PIN. Store it somewhere safe. It will only be shown once.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Text(recoveryKey, modifier = Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
            Text("I have stored this recovery key securely.")
        }
        Button(onClick = onFinish, enabled = acknowledged, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun RecoveryKeyUnlock(onRecover: (CharArray, CharArray) -> Result<Unit>, onCancel: () -> Unit) {
    var recoveryKey by remember { mutableStateOf("") }
    var replacementPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LockSurface {
        GalleryPageTitle("Restore access", "Recovery key")
        Text("Enter your offline recovery key and choose a new PIN. Your encrypted media will not be changed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(recoveryKey, { recoveryKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Recovery key") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(replacementPin, { replacementPin = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("New PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(confirmation, { confirmation = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm new PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            if (replacementPin.length < 6 || replacementPin != confirmation) {
                error = "Use and confirm a PIN of at least six digits."
            } else {
                onRecover(recoveryKey.toCharArray(), replacementPin.toCharArray()).onSuccess {
                    recoveryKey = ""; replacementPin = ""; confirmation = ""
                }.onFailure {
                    recoveryKey = ""
                    error = if (it is InvalidRecoveryKeyException) "Recovery key is incorrect." else "Unable to restore access."
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Restore access") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Back to unlock") }
    }
}

@Composable
internal fun PinPage(
    title: String,
    detail: String,
    pin: String,
    onPinChange: (String) -> Unit,
    action: String,
    message: String?,
    confirmation: String? = null,
    onConfirmationChange: ((String) -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    actionEnabled: Boolean = enabled,
    pinLabel: String = "PIN",
    confirmationLabel: String = "Confirm PIN",
    titleModifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    LockSurface {
        GalleryPageTitle(if (action == "Unlock") "Protected on this device" else "Welcome", title, titleModifier)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = pin,
            onValueChange = { onPinChange(it.filter(Char::isDigit)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(pinLabel) },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
        )
        if (confirmation != null && onConfirmationChange != null) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = { onConfirmationChange(it.filter(Char::isDigit)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(confirmationLabel) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onAction, enabled = actionEnabled, modifier = Modifier.fillMaxWidth()) { Text(action) }
        secondaryAction?.let { actionContent ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { actionContent() }
        }
    }
}

@Composable
private fun LockSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GalleryTokens.PageHorizontal, vertical = 32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        GalleryCard(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(GalleryTokens.ContentGap)) { content() }
        }
    }
}

@Composable
internal fun SettingsHome(
    autoLockTimeout: AutoLockTimeout,
    appTheme: AppTheme,
    allowScreenshots: Boolean,
    updateStatus: String,
    updateLastChecked: String,
    updateAvailable: Boolean,
    biometricEnabled: Boolean,
    recoveryKeyConfigured: Boolean,
    browserSearchEngine: BrowserSearchEngine,
    clearBrowserDataOnLock: Boolean,
    onAutoLockTimeoutChanged: (AutoLockTimeout) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onAllowScreenshotsChanged: (Boolean) -> Unit,
    onBrowserSearchEngineChanged: (BrowserSearchEngine) -> Unit,
    onClearBrowserDataOnLockChanged: (Boolean) -> Unit,
    onClearBrowserData: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onChangePin: (CharArray, CharArray) -> Result<Unit>,
    onLock: () -> Unit,
    browserAutoConnectVpn: Boolean,
    browserRequireVpn: Boolean,
    onBrowserAutoConnectVpnChanged: (Boolean) -> Unit,
    onBrowserRequireVpnChanged: (Boolean) -> Unit,
    onImportWireGuardProfile: () -> Unit,
    vpnProfileStatus: String,
    vpnConnectionState: VpnConnectionState,
    vpnProfiles: List<VpnProfileSummary>,
    onSelectVpnProfile: (String) -> Unit,
    onRemoveVpnProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    browserSaveHistory: Boolean = false,
    onBrowserSaveHistoryChanged: (Boolean) -> Unit = {},
    browserStaticContentHost: Boolean = false,
    onBrowserStaticContentHostChanged: (Boolean) -> Unit = {},
    browserLayoutColours: Boolean = false,
    onBrowserLayoutColoursChanged: (Boolean) -> Unit = {},
    contentBlocker: uk.co.traynor.privategallery.core.browser.v2.BrowserContentBlocker? = null,
    hideContent: Boolean = false,
    secretDiscovered: Boolean = false,
    onHideContentChanged: (Boolean) -> Unit = {},
    onSecretDiscoveryChanged: (Boolean) -> Unit = {},
    onAuthenticateSensitive: ((Boolean) -> Unit) -> Unit = { it(false) },
    onCancelSensitiveAuthentication: () -> Unit = {},
    onVerifySecretPin: (CharArray) -> Boolean = { it.fill('\u0000'); false },
    onChooseBackupExport: () -> Unit = {},
    backupExportUri: Uri? = null,
    onCancelBackupExport: () -> Unit = {},
    onExportBackup: (CharArray) -> Unit = { it.fill('\u0000') },
    backupStatus: String = "",
    onOpenHidden: (() -> Unit)? = null,
    hiddenSettings: (@Composable () -> Unit)? = null,
    hiddenBrowserSettings: (@Composable () -> Unit)? = null,
    allowHiddenDiagnostics: Boolean = false,
    storagePanel: (@Composable () -> Unit)? = null,
) {
    val appContext = LocalContext.current.applicationContext
    val aiConfiguration = remember(appContext) { uk.co.traynor.privategallery.core.editor.AiProviderRegistry.initialize(appContext) }
    val aiStatus by aiConfiguration.status.collectAsState()
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    val diagnosticsGate = remember { uk.co.traynor.privategallery.core.browser.hidden.OwnerDiagnosticsGate(android.os.SystemClock::elapsedRealtime) }
    var diagnosticsAuthenticated by remember { mutableStateOf(false) }
    var secretOpen by remember { mutableStateOf(false) }
    var authAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var discovery by remember { mutableStateOf(uk.co.traynor.privategallery.core.security.SecretDiscoveryState()) }
    var hiddenOpen by remember { mutableStateOf(false) }
    val hiddenEntry = remember { uk.co.traynor.privategallery.core.hidden.HiddenEntrySequence(android.os.SystemClock::elapsedRealtime) }
    var hiddenPickerActive by remember { mutableStateOf(false) }
    if (hiddenOpen && BuildConfig.HIDDEN_ENABLED) uk.co.traynor.privategallery.ui.HiddenVaultDialog(
        onPickerActive = { hiddenPickerActive = it }, onDismiss = { hiddenOpen = false },
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (!hiddenPickerActive) hiddenOpen = false
                hiddenEntry.reset()
                diagnosticsGate.reset()
                diagnosticsAuthenticated = false
                if (category == SettingsCategory.DEBUG) category = null
                secretOpen = false
                authAction = null
                onCancelSensitiveAuthentication()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); onCancelSensitiveAuthentication() }
    }
    androidx.activity.compose.BackHandler(category != null || secretOpen) {
        if (secretOpen) secretOpen = false else category = null
    }
    LaunchedEffect(category, secretDiscovered) {
        if (category != SettingsCategory.SECURITY || !secretDiscovered) secretOpen = false
        if (category != SettingsCategory.ABOUT) { discovery = discovery.reset(); hiddenEntry.reset(); diagnosticsGate.reset() }
        if (category != SettingsCategory.DEBUG) diagnosticsAuthenticated = false
    }
    var changingPin by remember { mutableStateOf(false) }
    var showingLicences by remember { mutableStateOf(false) }
    val settingsModifier = if (SettingsLayoutPolicy.isVerticallyScrollable) {
        modifier.verticalScroll(androidx.compose.runtime.key(category) { rememberScrollState() })
    } else {
        modifier
    }
    Column(
        modifier = settingsModifier
            .fillMaxSize()
            .padding(horizontal = GalleryTokens.PageHorizontal, vertical = GalleryTokens.PageVertical)
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(GalleryTokens.ContentGap),
    ) {
        if (category == null) {
            GalleryPageTitle("Private Gallery", "Settings")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 600.dp) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsCategory.ordinary.chunked(columns).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { destination ->
                                val summary = when (destination) {
                                    SettingsCategory.SECURITY -> "PIN, biometrics and auto-lock"
                                    SettingsCategory.BROWSER -> if (hiddenBrowserSettings != null) "Connection mode and private browsing data" else "${browserSearchEngine.label} · History ${if (browserSaveHistory) "on" else "off"}"
                                    SettingsCategory.VPN -> uk.co.traynor.privategallery.core.vpn.VpnProfilePresentation.from(vpnProfiles, vpnConnectionState).let { "${it.selectedProfileName} · ${it.connectionLabel}" }
                                    SettingsCategory.AI -> uk.co.traynor.privategallery.core.editor.AiProviderRegistry.choice.label
                                    SettingsCategory.ABOUT -> "${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}"
                                    else -> destination.summary
                                }
                                GallerySettingsRow(
                                    title = if (destination == SettingsCategory.BROWSER && hiddenBrowserSettings != null) "Hidden Browser" else destination.title,
                                    summary = summary,
                                    icon = when (destination) {
                                        SettingsCategory.SECURITY -> Icons.Default.Shield
                                        SettingsCategory.GALLERY -> Icons.Default.PhotoLibrary
                                        SettingsCategory.BROWSER -> Icons.Default.Language
                                        SettingsCategory.VPN -> Icons.Default.VpnKey
                                        SettingsCategory.AI -> Icons.Default.AutoAwesome
                                        SettingsCategory.APPEARANCE -> Icons.Default.Palette
                                        SettingsCategory.ABOUT -> Icons.Default.Info
                                        SettingsCategory.DEBUG -> Icons.Default.BugReport
                                    }, modifier = Modifier.weight(1f), onClick = { category = destination })
                            }
                            if (group.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else if (secretOpen) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { secretOpen = false }) { Icon(Icons.Default.ArrowBack, "Back to Security") }
                Text("Secret", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { category = null }) { Icon(Icons.Default.ArrowBack, "Back to Settings") }
                Text(category!!.title, style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (category == SettingsCategory.GALLERY) storagePanel?.invoke()
        if (category == SettingsCategory.GALLERY) SettingsSection("Gallery & Vault") {
            Text("Thumbnail size", style = MaterialTheme.typography.titleMedium)
            Text("Open the Gallery or Vault menu to choose thumbnail size for that grid. Vault search, sort and media filters remain in the Vault menu.")
            Text("Collections", style = MaterialTheme.typography.titleMedium)
            Text("Manage collections and choose your Favourite from Vault. Originals are retained when saving an edited copy.")
        }
        if (category == SettingsCategory.GALLERY) SettingsSection("Camera preferences") {
            Text("Open Camera from either vault menu to choose photo or video, microphone audio, quality and vibration before capturing.", style = MaterialTheme.typography.bodyMedium)
            Text("Audio preferences are protected separately in each vault. Black-Screen Camera keeps the capture surface black.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (category == SettingsCategory.SECURITY && !secretOpen && secretDiscovered) SettingsSection("Protected settings") {
            androidx.compose.material3.Surface(onClick = { authAction = java.util.UUID.randomUUID().toString() to "enter" }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Secret", style = MaterialTheme.typography.titleMedium); Text("Protected settings", style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        if (category == SettingsCategory.SECURITY && !secretOpen) SettingsSection(SettingsSections.SECURITY) {
            androidx.compose.material3.OutlinedButton(onClick = { changingPin = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Change PIN")
            }
            Text("Biometric unlock", style = MaterialTheme.typography.titleMedium)
            Text(
                if (biometricEnabled) "Enabled on this device" else "Enable it from the Vault after unlocking.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Recovery key", style = MaterialTheme.typography.titleMedium)
            Text(
                if (recoveryKeyConfigured) "Configured offline. It is never stored as plaintext in Private Gallery." else "Not configured. Set up a recovery key before relying on this Vault.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(onClick = onChooseBackupExport,
                enabled = recoveryKeyConfigured && !hideContent, modifier = Modifier.fillMaxWidth()) {
                Text("Export encrypted Vault backup")
            }
            Text("Verify your offline recovery key before export. Keep it separately from the backup file.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (backupStatus.isNotBlank()) Text(backupStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Auto-lock", style = MaterialTheme.typography.titleMedium)
            Text("Lock protected content after Private Gallery leaves the foreground.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                AutoLockTimeout.entries.forEach { timeout ->
                    GalleryChoiceRow(timeout.label, timeout == autoLockTimeout) { onAutoLockTimeoutChanged(timeout) }
                }
            }
        }
        if (category == SettingsCategory.SECURITY && !secretOpen) hiddenSettings?.invoke()
        if (category == SettingsCategory.SECURITY && !secretOpen) SettingsSection(SettingsSections.PRIVACY) {
            Text("Secure-screen protection", style = MaterialTheme.typography.titleMedium)
            Text("Protected screens are excluded from screenshots and Recents previews where Android supports it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Backup protection", style = MaterialTheme.typography.titleMedium)
            Text("Private Gallery data is excluded from Android backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (category == SettingsCategory.DEBUG && diagnosticsAuthenticated) SettingsSection(SettingsSections.DEBUG) {
            if (allowHiddenDiagnostics && (BuildConfig.DEBUG || BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS)) {
                Text("Vault video diagnostics", style = MaterialTheme.typography.titleMedium)
                Text(uk.co.traynor.privategallery.ui.VaultPlaybackDiagnostics.summary(), style = MaterialTheme.typography.bodySmall)
            }
            BrowserDiagnosticsSettings(
                staticContentHost = browserStaticContentHost,
                onStaticContentHostChanged = onBrowserStaticContentHostChanged,
                layoutColours = browserLayoutColours,
                onLayoutColoursChanged = onBrowserLayoutColoursChanged,
            )
        }
        if (category == SettingsCategory.SECURITY && secretOpen && secretDiscovered) SettingsSection("Privacy presentation") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide content", style = MaterialTheme.typography.titleMedium)
                    Text("Temporarily make Private Gallery appear empty. Your encrypted content remains safely stored.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = hideContent, onCheckedChange = {
                    if (it) onHideContentChanged(true) else authAction = java.util.UUID.randomUUID().toString() to "reveal"
                })
            }
        }
        if (category == SettingsCategory.SECURITY && secretOpen && secretDiscovered) SettingsSection("Screen capture") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Allow screenshots", style = MaterialTheme.typography.titleMedium)
                    Text("Allows screenshots while using Private Gallery. Private content may be captured while enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(
                    checked = allowScreenshots,
                    onCheckedChange = { enabled -> if (enabled) authAction = java.util.UUID.randomUUID().toString() to "screenshots" else onAllowScreenshotsChanged(false) },
                )
            }
            androidx.compose.material3.OutlinedButton(onClick = {
                onSecretDiscoveryChanged(false)
                secretOpen = false
                discovery = uk.co.traynor.privategallery.core.security.SecretDiscoveryState()
            }, modifier = Modifier.fillMaxWidth()) { Text("Hide Secret settings again") }
        }
        if (category == SettingsCategory.APPEARANCE) SettingsSection(SettingsSections.APPEARANCE) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Text("Choose light, dark, or follow your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                AppTheme.entries.forEach { value ->
                    GalleryChoiceRow(value.label, value == appTheme) { onThemeChanged(value) }
                }
            }
        }
        if (category == SettingsCategory.VPN) SettingsSection("VPN") {
            Text("WireGuard VPN", style = MaterialTheme.typography.titleMedium)
            Text("When VPN is required, browsing stays paused until your selected VPN is connected. This protects the in-app Browser, not other apps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Auto-connect for Browser", style = MaterialTheme.typography.titleMedium)
                    Text("Connect the selected private WireGuard profile when Browser opens.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = browserAutoConnectVpn, onCheckedChange = onBrowserAutoConnectVpnChanged)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Require VPN for browsing", style = MaterialTheme.typography.titleMedium)
                    Text("Wait for a secure VPN connection before browsing or downloading.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = browserRequireVpn, onCheckedChange = onBrowserRequireVpnChanged)
            }
            androidx.compose.material3.OutlinedButton(onClick = onImportWireGuardProfile, modifier = Modifier.fillMaxWidth()) { Text("Import WireGuard profile") }
            val vpnPresentation = uk.co.traynor.privategallery.core.vpn.VpnProfilePresentation.from(vpnProfiles, vpnConnectionState)
            Text("Selected profile", style = MaterialTheme.typography.titleMedium)
            Text(vpnPresentation.selectedProfileName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Text(vpnPresentation.connectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vpnProfileStatus.isNotBlank()) Text(vpnProfileStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Imported profiles", style = MaterialTheme.typography.titleMedium)
            if (vpnProfiles.isEmpty()) {
                Text("No WireGuard profiles imported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                vpnProfiles.forEach { profile ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(if (profile.active) "WireGuard · Active" else "WireGuard", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!profile.active) {
                            androidx.compose.material3.TextButton(onClick = { onSelectVpnProfile(profile.id) }) { Text("Use") }
                            androidx.compose.material3.TextButton(onClick = { onRemoveVpnProfile(profile.id) }) { Text("Remove") }
                        }
                    }
                }
                if (vpnProfiles.size == 1 && vpnProfiles.single().active) {
                    Text("Import and select another profile before removing the active profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (category == SettingsCategory.BROWSER && hiddenBrowserSettings != null) hiddenBrowserSettings()
        if (category == SettingsCategory.BROWSER && hiddenBrowserSettings == null) uk.co.traynor.privategallery.ui.BrowserSettingsContent(
            browserSearchEngine, onBrowserSearchEngineChanged, browserSaveHistory, onBrowserSaveHistoryChanged,
            browserRequireVpn, onBrowserRequireVpnChanged, browserAutoConnectVpn, onBrowserAutoConnectVpnChanged,
            clearBrowserDataOnLock, onClearBrowserDataOnLockChanged, onClearBrowserData, contentBlocker,
        )
        if (category == SettingsCategory.AI) uk.co.traynor.privategallery.ui.AiEditingSettings()
        if (category == SettingsCategory.ABOUT) SettingsSection(SettingsSections.UPDATES) {
            androidx.compose.material3.Surface(onClick = {
                discovery = discovery.tapInstalled()
                if (hiddenEntry.tap(false) && BuildConfig.HIDDEN_ENABLED) { if (onOpenHidden != null) onOpenHidden() else hiddenOpen = true }
                if (discovery.discovered && !secretDiscovered) onSecretDiscoveryChanged(true)
            }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Installed", style = MaterialTheme.typography.titleMedium)
                    Text("${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (discovery.discovered && secretDiscovered) Text("Protected settings unlocked")
            Text("Latest", style = MaterialTheme.typography.titleMedium)
            Text(updateStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last checked", style = MaterialTheme.typography.titleMedium)
            Text(updateLastChecked, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { discovery = discovery.reset(); hiddenEntry.reset(); onCheckForUpdates() }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
            if (updateAvailable) Button(onClick = { discovery = discovery.reset(); hiddenEntry.reset(); onDownloadUpdate() }, modifier = Modifier.fillMaxWidth()) { Text("Download update") }
        }
        if (category == SettingsCategory.ABOUT) SettingsSection(SettingsSections.ABOUT) {
            androidx.compose.material3.Surface(onClick = {
                discovery = discovery.tapVersion()
                hiddenEntry.tap(true)
                if (diagnosticsGate.tap()) authAction = java.util.UUID.randomUUID().toString() to "diagnostics"
            }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("Private Gallery ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.titleMedium)
            }
            Text("Media stays in encrypted private app storage until you restore it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { discovery = discovery.reset(); hiddenEntry.reset(); showingLicences = true }, modifier = Modifier.fillMaxWidth()) { Text("Third-party licences") }
        }
        Button(onClick = { discovery = discovery.reset(); hiddenEntry.reset(); onLock() }, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
    }
    authAction?.let { requested ->
        SecretAuthenticationDialog(
            requestId = requested.first,
            biometricEnabled = biometricEnabled,
            onBiometric = { finished -> onAuthenticateSensitive { valid -> if (authAction == requested) finished(valid) } },
            onVerifyPin = onVerifySecretPin,
            onDone = { authenticated ->
                if (authAction == requested) {
                    onCancelSensitiveAuthentication()
                    authAction = null
                    if (authenticated) when (requested.second) {
                        "diagnostics" -> if (category == SettingsCategory.ABOUT) { diagnosticsAuthenticated = true; category = SettingsCategory.DEBUG }
                        "enter" -> if (secretDiscovered) secretOpen = true
                        "reveal" -> if (secretOpen && hideContent) onHideContentChanged(false)
                        "screenshots" -> if (secretOpen && !allowScreenshots) onAllowScreenshotsChanged(true)
                    }
                }
            },
        )
    }
    if (changingPin) ChangePinDialog(onChangePin) { changingPin = false }
    if (backupExportUri != null && category == SettingsCategory.SECURITY) {
        var recovery by remember(backupExportUri) { mutableStateOf("") }
        var error by remember(backupExportUri) { mutableStateOf("") }
        AlertDialog(onDismissRequest = onCancelBackupExport,
            title = { Text("Verify recovery key") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only encrypted Vault media and metadata will be saved. Your PIN, Browser and AI settings are excluded.")
                OutlinedTextField(recovery, { recovery = it }, label = { Text("Offline recovery key") },
                    visualTransformation = PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(onClick = {
                if (recovery.isBlank()) error = "Enter the recovery key."
                else { onExportBackup(recovery.toCharArray()); recovery = "" }
            }) { Text("Export") } },
            dismissButton = { TextButton(onClick = onCancelBackupExport) { Text("Cancel") } })
    }
    if (showingLicences) {
        val notices = remember {
            runCatching {
                appContext.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() }
            }.getOrElse { "Third-party notices are unavailable in this installation." }
        }
        AlertDialog(
            onDismissRequest = { showingLicences = false },
            title = { Text("Third-party licences") },
            text = {
                Column(modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    Text(notices)
                }
            },
            confirmButton = { TextButton(onClick = { showingLicences = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SecretAuthenticationDialog(
    requestId: String,
    biometricEnabled: Boolean,
    onBiometric: ((Boolean) -> Unit) -> Unit,
    onVerifyPin: (CharArray) -> Boolean,
    onDone: (Boolean) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(requestId) {
        if (biometricEnabled) onBiometric { valid -> if (valid) onDone(true) }
    }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text("Confirm owner identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use your Private Gallery PIN${if (biometricEnabled) " or biometrics" else ""}.")
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit) }, label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (error) Text("Authentication failed.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            val accepted = onVerifyPin(pin.toCharArray())
            pin = ""
            if (accepted) onDone(true) else error = true
        }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { onDone(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun ChangePinDialog(onChangePin: (CharArray, CharArray) -> Result<Unit>, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This updates protection around the Vault key; media files are not re-encrypted.")
                OutlinedTextField(current, { current = it.filter(Char::isDigit) }, label = { Text("Current PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(replacement, { replacement = it.filter(Char::isDigit) }, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit) }, label = { Text("Confirm new PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (replacement != confirm || replacement.length < 6) {
                    error = "Use and confirm a PIN of at least six digits."
                } else {
                    onChangePin(current.toCharArray(), replacement.toCharArray()).onSuccess { onDismiss() }
                        .onFailure { error = if (it is InvalidPinException) "Current PIN is incorrect." else "Unable to change PIN." }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    GalleryCard(modifier = Modifier.fillMaxWidth()) {
        GalleryCardHeading(title)
        content()
    }
}

private val AutoLockTimeout.label: String
    get() = when (this) {
        AutoLockTimeout.IMMEDIATELY -> "Immediately"
        AutoLockTimeout.SECONDS_30 -> "After 30 seconds"
        AutoLockTimeout.MINUTE_1 -> "After 1 minute"
        AutoLockTimeout.MINUTES_5 -> "After 5 minutes"
    }

private val AppTheme.label: String
    get() = when (this) {
        AppTheme.SYSTEM -> "System"
        AppTheme.LIGHT -> "Light"
        AppTheme.DARK -> "Dark"
    }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VaultHome(
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onLoadCollections: ((List<VaultCollection>) -> Unit) -> Unit,
    onCreateCollection: (String, (Result<VaultCollection>) -> Unit) -> Unit,
    onAddItemsToCollection: (String, List<String>, (String) -> Unit) -> Unit,
    onRemoveItemsFromCollection: (String, List<String>, (String) -> Unit) -> Unit,
    onRenameCollection: (String, String, (String) -> Unit) -> Unit,
    onDeleteCollection: (String, (String) -> Unit) -> Unit,
    onLoadCollectionItems: (String, (List<VaultItem>) -> Unit) -> Unit,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    cropRevision: Int,
    biometricEnabled: Boolean,
    onEnrollBiometrics: () -> Unit,
    onSetFavouriteCollection: (String, (String) -> Unit) -> Unit,
    onFavouriteStateChanged: () -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Map<String, VaultItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onGenerateImage: (uk.co.traynor.privategallery.core.editor.GenerationRequest, (String) -> Unit, (Result<VaultItem>) -> Unit) -> (() -> Unit) = { _, _, _ -> {} },
    onLoadRecentlyDeleted: ((List<VaultItem>) -> Unit) -> Unit = { it(emptyList()) },
    onMoveItemsToRecentlyDeleted: (List<String>, (String) -> Unit) -> Unit = { _, done -> done("Unavailable.") },
    onChangeRecentlyDeleted: (VaultItem, Boolean, (String) -> Unit) -> Unit = { _, _, done -> done("Unavailable.") },
    onRestoreSelectedVaultCopies: (List<String>, (String) -> Unit) -> Unit = { _, done -> done("Unavailable.") },
    vaultTitle: String = "Vault",
    allowGeneration: Boolean = true,
    editingVaultLabel: String = "Primary Vault",
    allowBiometrics: Boolean = true,
    allowRestore: Boolean = true,
    onRequestImport: (() -> Unit)? = null,
    statusLabel: String? = null,
    onReturnToPrimary: (() -> Unit)? = null,
    onCopyFromPrimary: (() -> Unit)? = null,
    onTransferToHidden: ((List<String>, Boolean, () -> Boolean, (Int, Int) -> Unit, (String)->Unit)->Unit)? = null,
    onRestoreFromHidden: ((List<String>)->Unit)? = null,
    onCamera: (() -> Unit)? = null,
    onRecoverCamera: (() -> Unit)? = null,
    onLoadSort: (((MediaSort)->Unit)->Unit)? = null,
    onSaveSort: ((MediaSort)->Unit)? = null,
) {
    val cameraActivity = androidx.activity.compose.LocalActivity.current as? MainActivity
    val cameraAction = onCamera ?: { cameraActivity?.openVaultCamera() }
    var pendingHiddenMove by remember { mutableStateOf<Pair<List<String>, Boolean>?>(null) }
    var transferCompleted by remember { mutableStateOf(0) }
    var transferTotal by remember { mutableStateOf(0) }
    var transferStopping by remember { mutableStateOf(false) }
    val transferCancelled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { transferCancelled.set(true) } }
    var transferring by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var collections by remember { mutableStateOf<List<VaultCollection>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var creatingImage by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var density by rememberSaveable { mutableStateOf(ThumbnailDensity.COMPACT) }
    var query by remember(editingVaultLabel) { mutableStateOf("") }
    var searchExpanded by remember(editingVaultLabel) { mutableStateOf(false) }
    var kind by remember { mutableStateOf(MediaKindFilter.ALL) }
    var sort by remember(editingVaultLabel) { mutableStateOf(MediaSort.NEWEST) }
    var sortReady by remember(editingVaultLabel) {mutableStateOf(false)}
    LaunchedEffect(editingVaultLabel) {
        val done:(MediaSort)->Unit={sort=it;sortReady=true}
        if(onLoadSort!=null)onLoadSort(done) else if(cameraActivity!=null)cameraActivity.loadPrimaryMediaSort(done) else done(MediaSort.NEWEST)
    }
    fun chooseSort(value:MediaSort){sort=value;if(onSaveSort!=null)onSaveSort(value) else cameraActivity?.savePrimaryMediaSort(value)}
    val displayedItems = remember(vaultItems, query, kind, sort) { VaultBrowsePolicy.apply(vaultItems, query, kind, sort) }
    var refreshRevision by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var contentMode by remember { mutableStateOf(VaultContentMode.MEDIA) }
    var recentlyDeleted by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var confirmingBulkTrash by remember { mutableStateOf(false) }
    var confirmingBulkRestore by remember { mutableStateOf(false) }
    var permanentlyDelete by remember { mutableStateOf<VaultItem?>(null) }
    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var creatingCollection by remember { mutableStateOf(false) }
    var collectionPickerFor by remember { mutableStateOf<List<String>?>(null) }
    var managingCollection by remember { mutableStateOf<VaultCollection?>(null) }
    var openCollection by remember { mutableStateOf<VaultCollection?>(null) }
    var openCollectionItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA),
    ) { uris ->
        if (uris.isNotEmpty()) {
            status = "Importing selected media…"
            onImport(uris) {
                status = it
                onLoadItems { updated -> vaultItems = updated; loaded = true; refreshRevision++ }
            }
        }
    }
    val requestImport = onRequestImport ?: { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    fun refresh() {
        refreshRevision++
        onLoadItems { items -> vaultItems = items; loaded = true }
        onLoadCollections { collections = it }
        onLoadRecentlyDeleted { recentlyDeleted = it }
    }
    LaunchedEffect(Unit) { (onRecoverCamera ?: {cameraActivity?.recoverVaultCamera()}).invoke() }
    LaunchedEffect(cropRevision,cameraActivity?.cameraRevision) { refresh() }
    LaunchedEffect(openCollection?.id, cropRevision, refreshRevision,sort) {
        openCollection?.let { collection -> onLoadCollectionItems(collection.id) { openCollectionItems = VaultBrowsePolicy.apply(it,sort=sort) } }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GalleryTokens.MediaGap, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaHeader(
            title = openCollection?.name ?: if (contentMode == VaultContentMode.TRASH) "Recently Deleted" else vaultTitle,
            subtitle = (statusLabel?.let { "$it · " } ?: "") + if (contentMode == VaultContentMode.TRASH) "${recentlyDeleted.size} items · deleted after 30 days" else if (openCollection == null) VaultSummary.from(vaultItems).let { "${it.photos} photos · ${it.videos} videos" } else "${openCollectionItems.size} items",
            onAdd = { requestImport() },
            onBack = when { contentMode == VaultContentMode.TRASH -> ({ contentMode = VaultContentMode.MEDIA }); openCollection != null -> ({ openCollection = null }); else -> null },
            onLock = onLock,
            sort=if(sortReady&&contentMode!=VaultContentMode.TRASH&&(openCollection!=null||contentMode==VaultContentMode.MEDIA))sort else null,
            onSort=::chooseSort,
            onSearch = if (openCollection == null && contentMode == VaultContentMode.MEDIA && vaultItems.isNotEmpty()) ({ searchExpanded = !searchExpanded }) else null,
            searchActive = query.isNotBlank(),
            onMenu = { menuOpen = true },
            onGenerate = if (allowGeneration && openCollection == null) ({ creatingImage = true }) else null,
        )
        if (menuOpen) GalleryMenuSheet(vaultTitle, onDismiss = { menuOpen = false }) {
            SheetSection("Add and organise")
            SheetAction("Camera", Icons.Default.PhotoCamera) { menuOpen = false; cameraAction() }
            onReturnToPrimary?.let { action -> SheetAction("Return to Primary Vault", Icons.Default.Lock) { menuOpen = false; action() } }
            onCopyFromPrimary?.let { action -> SheetAction("Copy from Primary Vault", Icons.Default.PhotoLibrary) { menuOpen = false; action() } }
            SheetAction("Add media", Icons.Default.AddPhotoAlternate) { menuOpen = false; requestImport() }
            if (allowGeneration) SheetAction("Create image", Icons.Default.AutoAwesome) { menuOpen = false; creatingImage = true }
            if (openCollection == null && contentMode == VaultContentMode.MEDIA) SheetAction("Select items", Icons.Default.CheckCircle, enabled = vaultItems.isNotEmpty()) {
                selectionMode = true; menuOpen = false
            }
            SheetAction("New collection", Icons.Default.CreateNewFolder) { creatingCollection = true; menuOpen = false }
            SheetAction("Recently Deleted", Icons.Default.DeleteOutline) {
                openCollection = null; contentMode = VaultContentMode.TRASH; selectedItemIds = emptySet(); selectionMode = false
                onLoadRecentlyDeleted { recentlyDeleted = it }; menuOpen = false
            }
            SheetAction("Refresh", Icons.Default.Refresh) { refresh(); menuOpen = false }
            if (openCollection == null && contentMode == VaultContentMode.MEDIA) MediaSortChoices(sort,::chooseSort)
            SheetSection("View")
            ThumbnailSizeChoices(density) { density = it }
            SheetSection("Security")
            if (allowBiometrics && !biometricEnabled) SheetAction("Enable biometric unlock", Icons.Default.Fingerprint) { menuOpen = false; onEnrollBiometrics() }
            SheetAction("Lock Vault", Icons.Default.Lock) { menuOpen = false; onLock() }
        }
        if (openCollection == null && contentMode != VaultContentMode.TRASH) {
            androidx.compose.material3.TabRow(selectedTabIndex = if (contentMode == VaultContentMode.MEDIA) 0 else 1) {
                androidx.compose.material3.Tab(selected = contentMode == VaultContentMode.MEDIA, onClick = { contentMode = VaultContentMode.MEDIA }, text = { Text("Media") })
                androidx.compose.material3.Tab(selected = contentMode == VaultContentMode.COLLECTIONS, onClick = { contentMode = VaultContentMode.COLLECTIONS; selectedItemIds = emptySet(); selectionMode = false }, text = { Text("Collections") })
            }
        }
        if (openCollection == null && contentMode == VaultContentMode.MEDIA && vaultItems.isNotEmpty()) {
            VaultBrowseControls(query, { query = it; selectedItemIds = emptySet(); selectionMode = false }, kind, { kind = it; selectedItemIds = emptySet(); selectionMode = false }, expanded = searchExpanded, onCollapse = { searchExpanded = false })
        }
        when {
            !loaded -> GalleryLoadingState("Loading Vault…")
            contentMode == VaultContentMode.TRASH -> {
                if (recentlyDeleted.isEmpty()) GalleryCard {
                    Text("Recently Deleted is empty", style = MaterialTheme.typography.titleMedium)
                    Text("Vault items moved here can be restored for 30 days.")
                } else androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentlyDeleted.size, key = { recentlyDeleted[it].id }) { index ->
                        val item = recentlyDeleted[index]
                        GalleryCard {
                            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("Encrypted · ${item.mimeType}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onChangeRecentlyDeleted(item, false) { status = it; refresh() } }) { Text("Restore") }
                                TextButton(onClick = { permanentlyDelete = item }) { Text("Delete now") }
                            }
                        }
                    }
                }
            }
            openCollection != null -> CollectionMediaGrid(
                openCollectionItems,
                onLoadPreview,
                onOpenViewer,
                cropRevision,
                Modifier.weight(1f),
                thumbnailMinSize = density.minSize,
                onRemove = { ids -> onRemoveItemsFromCollection(checkNotNull(openCollection).id, ids) { result ->
                    status = result
                    onLoadCollectionItems(checkNotNull(openCollection).id) { openCollectionItems = VaultBrowsePolicy.apply(it,sort=sort) }
                } },
            )
            contentMode == VaultContentMode.COLLECTIONS -> CollectionsGrid(
                collections = collections,
                onLoadCollectionItems = onLoadCollectionItems,
                onLoadPreview = onLoadPreview,
                cropRevision = cropRevision + refreshRevision,
                modifier = Modifier.weight(1f),
                onCreate = { creatingCollection = true },
                onOpen = { openCollection = it },
                onManage = { managingCollection = it },
            )
            vaultItems.isNotEmpty() && displayedItems.isEmpty() -> GalleryCard {
                Text("No matching media", style = MaterialTheme.typography.titleMedium)
                Text("Try another filename or show all media.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { query = ""; kind = MediaKindFilter.ALL }) { Text("Clear filters") }
            }
            displayedItems.isNotEmpty() -> {
                if (selectionMode || selectedItemIds.isNotEmpty()) {
                    Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${selectedItemIds.size} selected", style = MaterialTheme.typography.titleMedium)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { selectedItemIds = displayedItems.map { it.id }.toSet() }) { Text("All") }
                            onTransferToHidden?.let {
                                TextButton(enabled=selectedItemIds.isNotEmpty() && !transferring,onClick={pendingHiddenMove=selectedItemIds.toList() to false}) {Text("Copy to Hidden")}
                                TextButton(enabled=selectedItemIds.isNotEmpty() && !transferring,onClick={pendingHiddenMove=selectedItemIds.toList() to true}) {Text("Move to Hidden")}
                            }
                            onRestoreFromHidden?.let { restore ->
                                TextButton(enabled=selectedItemIds.isNotEmpty(),onClick={restore(selectedItemIds.toList())}) {Text("Restore to…")}
                            }
                            TextButton(enabled = selectedItemIds.isNotEmpty(), onClick = { collectionPickerFor = selectedItemIds.toList() }) { Text("Add to collection") }
                            if (allowRestore) TextButton(enabled = selectedItemIds.isNotEmpty(), onClick = { confirmingBulkRestore = true }) { Text("Restore copies") }
                            TextButton(enabled = selectedItemIds.isNotEmpty(), onClick = { confirmingBulkTrash = true }) { Text("Delete") }
                            TextButton(onClick = { selectedItemIds = emptySet(); selectionMode = false }) { Text("Cancel") }
                            }
                        }
                    }
                }
                VaultMediaGrid(displayedItems, onLoadPreview, cropRevision, selectedItemIds, onSelection = { id -> selectionMode = true; selectedItemIds = selectedItemIds.toggle(id) }, onOpenViewer = onOpenViewer, modifier = Modifier.weight(1f), selectionMode = selectionMode, thumbnailMinSize = density.minSize)
            }
            else -> GalleryEmptyState("Your vault is empty",
                "Photos and videos you add here are stored privately and encrypted on this device.",
                Icons.Default.Lock, actionLabel = "Add media", onAction = { requestImport() })
        }
        if (status.isNotBlank()) GalleryFeedback(status)
        pendingHiddenMove?.let { (ids, move) ->
            val action = if (move) "Move" else "Copy"
            AlertDialog(onDismissRequest={pendingHiddenMove=null},title={Text("$action ${ids.size} selected items?")},
                text={Text("Source: Primary Vault\nDestination: Hidden Vault\nSelected: ${ids.size}\n\n" +
                    if(move) "Each Hidden copy is verified before its Primary original enters Recently Deleted for 30 days. Failed transfers retain their originals."
                    else "Each Hidden copy is verified. Primary originals are retained.")},
                confirmButton={TextButton(enabled=!transferring,onClick={
                    pendingHiddenMove=null;transferring=true;transferCompleted=0;transferTotal=ids.size;transferStopping=false;transferCancelled.set(false)
                    onTransferToHidden?.invoke(ids,move,{transferCancelled.get()},{completed,total -> transferCompleted=completed;transferTotal=total}) {
                        status=it;transferring=false;selectedItemIds=emptySet();selectionMode=false;refresh()
                    }
                }) {Text("$action to Hidden")}},dismissButton={TextButton(onClick={pendingHiddenMove=null}) {Text("Cancel")}})
        }
        if (transferring) AlertDialog(onDismissRequest={},title={Text("Transferring to Hidden")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Primary Vault → Hidden Vault")
                Text("$transferCompleted / $transferTotal completed")
                androidx.compose.material3.LinearProgressIndicator(progress={if(transferTotal==0) 0f else transferCompleted.toFloat()/transferTotal},modifier=Modifier.fillMaxWidth())
                Text(if(transferStopping) "Stopping after the current copy is safely verified…" else "Keep the app open. Cancel safely stops after the current copy; originals for unfinished items are retained.")
            }},confirmButton={TextButton(enabled=!transferStopping,onClick={transferStopping=true;transferCancelled.set(true)}) {Text("Cancel transfer")}})
        if (confirmingBulkTrash) AlertDialog(onDismissRequest = { confirmingBulkTrash = false },
            title = { Text("Move ${selectedItemIds.size} items to Recently Deleted?") },
            text = { Text("Encrypted copies remain recoverable for 30 days. Collections are restored with them.") },
            confirmButton = { TextButton(onClick = {
                confirmingBulkTrash = false
                onMoveItemsToRecentlyDeleted(selectedItemIds.toList()) {
                    status = it; selectedItemIds = emptySet(); selectionMode = false; refresh()
                }
            }) { Text("Move to Recently Deleted") } },
            dismissButton = { TextButton(onClick = { confirmingBulkTrash = false }) { Text("Cancel") } })
        if (confirmingBulkRestore) AlertDialog(onDismissRequest = { confirmingBulkRestore = false },
            title = { Text("Restore ${selectedItemIds.size} copies to Gallery?") },
            text = { Text("Every copy is verified before publication. Restricted AI items stay in the Vault; Vault originals are retained.") },
            confirmButton = { TextButton(onClick = {
                confirmingBulkRestore = false
                onRestoreSelectedVaultCopies(selectedItemIds.toList()) {
                    status = it; selectedItemIds = emptySet(); selectionMode = false; refresh()
                }
            }) { Text("Restore copies") } },
            dismissButton = { TextButton(onClick = { confirmingBulkRestore = false }) { Text("Cancel") } })
        permanentlyDelete?.let { item -> AlertDialog(onDismissRequest = { permanentlyDelete = null },
            title = { Text("Delete permanently?") },
            text = { Text("This permanently removes the encrypted copy. It cannot be undone.") },
            confirmButton = { TextButton(onClick = {
                permanentlyDelete = null
                onChangeRecentlyDeleted(item, true) { status = it; refresh() }
            }) { Text("Delete now") } },
            dismissButton = { TextButton(onClick = { permanentlyDelete = null }) { Text("Cancel") } }) }
    }
    if (creatingCollection) NewCollectionDialog(
        onCreate = { name -> onCreateCollection(name) { result -> result.onSuccess { collections = collections + it; creatingCollection = false }.onFailure { status = "Unable to create collection." } } },
        onDismiss = { creatingCollection = false },
    )
    if (creatingImage) uk.co.traynor.privategallery.ui.CreateImageSheet(onGenerateImage,
        onSaved = { item ->
            creatingImage = false
            refresh()
            onOpenViewer(listOf(ViewerMediaEntry(item.id, item.mimeType)), mapOf(item.id to item), 0)
        }, onDismiss = { creatingImage = false }, vaultImages = vaultItems, onLoadPreview = onLoadPreview, vaultLabel = editingVaultLabel)
    collectionPickerFor?.let { ids -> CollectionPickerDialog(collections, onChoose = { collection ->
        onAddItemsToCollection(collection.id, ids) { status = it; selectedItemIds = emptySet(); selectionMode = false; collectionPickerFor = null; refresh() }
    }, onDismiss = { collectionPickerFor = null }) }
    managingCollection?.let { collection -> CollectionManagerDialog(
        collection = collection,
        onRename = { name -> onRenameCollection(collection.id, name) { status = it; managingCollection = null; refresh(); onFavouriteStateChanged() } },
        onDelete = { onDeleteCollection(collection.id) { status = it; managingCollection = null; refresh(); onFavouriteStateChanged() } },
        onSetFavourite = { onSetFavouriteCollection(collection.id) { status = it; managingCollection = null; refresh(); onFavouriteStateChanged() } },
        onDismiss = { managingCollection = null },
    ) }
}

private enum class VaultContentMode { MEDIA, COLLECTIONS, TRASH }

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

@Composable
private fun CompactVaultHeader(title: String, itemSummary: String, onAdd: () -> Unit, onBack: (() -> Unit)?) {
    MediaHeader(title, itemSummary, onAdd = onAdd, onBack = onBack)
}

@Composable
private fun VaultMediaGrid(
    items: List<VaultItem>,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    cropRevision: Int,
    selectedIds: Set<String>,
    onSelection: (String) -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Map<String, VaultItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    thumbnailMinSize: Int = ThumbnailDensity.COMPACT.minSize,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = thumbnailMinSize.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GalleryTokens.MediaGap),
        verticalArrangement = Arrangement.spacedBy(GalleryTokens.MediaGap),
    ) {
        items(items, key = { it.id }) { item ->
            VaultMediaTile(item, onLoadPreview, cropRevision, selected = item.id in selectedIds, onClick = {
                if (!selectionMode && selectedIds.isEmpty()) {
                    val entries = items.map { protected -> ViewerMediaEntry(protected.id, protected.mimeType) }
                    onOpenViewer(entries, items.associateBy { it.id }, entries.indexOfFirst { it.id == item.id })
                } else onSelection(item.id)
            }, onLongClick = { onSelection(item.id) })
        }
    }
}

@Composable
private fun CollectionMediaGrid(
    items: List<VaultItem>,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Map<String, VaultItem>, Int) -> Unit,
    cropRevision: Int,
    modifier: Modifier = Modifier,
    onRemove: ((List<String>) -> Unit)? = null,
    thumbnailMinSize: Int = ThumbnailDensity.COMPACT.minSize,
) {
    if (items.isEmpty()) {
        GalleryEmptyState("No media in this collection", "Add encrypted Vault media to this collection.", Icons.Default.PhotoLibrary)
    } else {
        var selectedIds by remember(items.map { it.id }) { mutableStateOf<Set<String>>(emptySet()) }
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedIds.isNotEmpty() && onRemove != null) {
                Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${selectedIds.size} selected", modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(selectedIds.toList()); selectedIds = emptySet() }) { Text("Remove") }
                        TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") }
                    }
                }
            }
            VaultMediaGrid(items, onLoadPreview, cropRevision, selectedIds, { selectedIds = selectedIds.toggle(it) }, onOpenViewer, Modifier.weight(1f), thumbnailMinSize = thumbnailMinSize)
        }
    }
}

@Composable
private fun CollectionsGrid(
    collections: List<VaultCollection>,
    onLoadCollectionItems: (String, (List<VaultItem>) -> Unit) -> Unit,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    cropRevision: Int,
    onCreate: () -> Unit,
    onOpen: (VaultCollection) -> Unit,
    onManage: (VaultCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (collections.isEmpty()) {
        GalleryEmptyState("No collections yet", "Keep related photos and videos together.", Icons.Default.PhotoAlbum,
            actionLabel = "New collection", onAction = onCreate)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(collections, key = { it.id }) { collection ->
                var members by remember(collection.id) { mutableStateOf<List<VaultItem>?>(null) }
                LaunchedEffect(collection.id, cropRevision) { onLoadCollectionItems(collection.id) { members = it } }
                val cover = members?.firstOrNull { it.id == collection.coverVaultItemId } ?: members?.firstOrNull()
                Surface(shape = GalleryTokens.CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(6.dp)) {
                    if (cover != null) VaultMediaTile(cover, onLoadPreview, cropRevision, onClick = { onOpen(collection) }, onLongClick = { onManage(collection) })
                    else Card(onClick = { onOpen(collection) }, shape = RoundedCornerShape(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Icon(Icons.Default.PhotoAlbum, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(collection.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(members?.let { "${it.size} ${if (it.size == 1) "item" else "items"}" } ?: "Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onManage(collection) }) { Icon(Icons.Default.MoreVert, "Manage ${collection.name}") }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun NewCollectionDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New collection") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Collection name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.trim().isNotEmpty()) onCreate(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CollectionManagerDialog(collection: VaultCollection, onRename: (String) -> Unit, onDelete: () -> Unit, onSetFavourite: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(collection.id) { mutableStateOf(collection.name) }
    var confirmingDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage collection") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            Text("Deleting this collection removes organisation only. Vault media is retained.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (confirmingDelete) Text("Delete ${collection.name}? Vault media will not be deleted.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { if (confirmingDelete) TextButton(onClick = onDelete) { Text("Delete collection") } else TextButton(onClick = { if (name.trim().isNotEmpty()) onRename(name) }) { Text("Save") } },
        dismissButton = { androidx.compose.foundation.layout.FlowRow { if (!confirmingDelete) TextButton(onClick = onSetFavourite) { Text("Set favourite") }; if (!confirmingDelete) TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
    )
}

@Composable
private fun CollectionPickerDialog(collections: List<VaultCollection>, onChoose: (VaultCollection) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to collection") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (collections.isEmpty()) Text("Create a collection first.")
            collections.forEach { collection -> androidx.compose.material3.OutlinedButton(onClick = { onChoose(collection) }, modifier = Modifier.fillMaxWidth()) { Text(collection.name) } }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun FavouriteHome(
    onLoadFavouriteCollection: ((VaultCollection?) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onLoadCollectionItems: (String, (List<VaultItem>) -> Unit) -> Unit,
    onAddItemsToCollection: (String, List<String>, (String) -> Unit) -> Unit,
    onRemoveItemsFromCollection: (String, List<String>, (String) -> Unit) -> Unit,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    cropRevision: Int,
    onOpenVault: () -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Map<String, VaultItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var collection by remember { mutableStateOf<VaultCollection?>(null) }
    var allItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var collectionItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var addingItems by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(cropRevision) { onLoadFavouriteCollection { collection = it; onLoadItems { allItems = it } } }
    LaunchedEffect(collection?.id, cropRevision) { collection?.let { onLoadCollectionItems(it.id) { collectionItems = it } } }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = GalleryTokens.MediaGap, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactVaultHeader(collection?.name ?: "Favourite", "${collectionItems.size} ${if (collectionItems.size == 1) "item" else "items"}", onAdd = { addingItems = true }, onBack = null)
        if (collection == null) GalleryCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No favourite collection selected.")
                Text("Choose one of your Collections from Vault. Open its menu and choose Set favourite.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.OutlinedButton(onClick = onOpenVault) { Text("Open Vault") }
            }
        }
        else CollectionMediaGrid(collectionItems, onLoadPreview, onOpenViewer, cropRevision, Modifier.weight(1f), onRemove = { ids ->
            onRemoveItemsFromCollection(collection!!.id, ids) { result ->
                status = result
                onLoadCollectionItems(collection!!.id) { collectionItems = it }
            }
        })
        if (status.isNotBlank()) Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) { Text(status, Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical)) }
    }
    if (addingItems && collection != null) ExistingVaultItemsDialog(
        items = allItems,
        onAdd = { ids -> onAddItemsToCollection(collection!!.id, ids) { result ->
            status = result
            addingItems = false
            onLoadCollectionItems(collection!!.id) { collectionItems = it }
        } },
        onDismiss = { addingItems = false },
    )
}

@Composable
private fun ExistingVaultItemsDialog(items: List<VaultItem>, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vault media") },
        text = { Column(modifier = Modifier.height(300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (items.isEmpty()) Text("No Vault media available.")
            items.forEach { item ->
                androidx.compose.material3.OutlinedButton(onClick = { selected = selected.toggle(item.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (item.id in selected) "✓ ${item.displayName}" else item.displayName, maxLines = 1)
                }
            }
        } },
        confirmButton = { TextButton(onClick = { if (selected.isNotEmpty()) onAdd(selected.toList()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VaultMediaTile(
    item: VaultItem,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    cropRevision: Int,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val sensitive = uk.co.traynor.privategallery.ui.LocalSensitivePreviews.current
    val previewAlive = remember(item.id, cropRevision) { java.util.concurrent.atomic.AtomicBoolean(true) }
    var preview by remember(item.id, cropRevision) { mutableStateOf<Bitmap?>(null) }
    var previewLoading by remember(item.id, cropRevision) { mutableStateOf(VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize)) }
    var previewFailed by remember(item.id, cropRevision) { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(previewAlive) { onDispose { previewAlive.set(false) } }
    androidx.compose.runtime.DisposableEffect(preview) {
        val owned = preview
        onDispose { if (sensitive) owned?.recycle() }
    }
    LaunchedEffect(item.id, cropRevision) {
        if (VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize)) {
            onLoadPreview(item) { result ->
                val candidate = result.getOrNull()
                if (!previewAlive.get()) { if (sensitive) candidate?.recycle() }
                else { preview = candidate; previewFailed = result.isFailure || candidate == null; previewLoading = false }
            }
        }
    }
    Card(
        modifier = Modifier.semantics { contentDescription = item.displayName; this.selected = selected }.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select media"),
        shape = GalleryTokens.ThumbnailShape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } ?: Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (previewLoading) CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = "Loading preview" }, strokeWidth = 2.dp)
                else Icon(if (previewFailed) Icons.Default.BrokenImage else if (item.mimeType.startsWith("video/")) Icons.Default.Videocam else Icons.Default.Image,
                    contentDescription = if (previewFailed) "Preview unavailable" else if (item.mimeType.startsWith("video/")) "Video" else "Photo",
                    modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ProtectedMediaTilePolicy.showVideoIndicator(item.mimeType)) {
                Surface(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                ) {
                    Icon(Icons.Default.PlayArrow, "Video", Modifier.padding(3.dp).size(16.dp), tint = androidx.compose.ui.graphics.Color.White)
                }
            }
            if (selected) Surface(
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(4.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) { Text("✓", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

private const val MAX_PICKED_MEDIA = 50

@Composable
internal fun HiddenPolicySettings(timeout:AutoLockTimeout,onTimeoutChanged:(AutoLockTimeout)->Unit,onUsePrimary:()->Unit) {
    SettingsSection("Hidden Vault — independent settings") {
        Text("Hidden auto-lock",style=MaterialTheme.typography.titleMedium)
        Text("Applies after the app leaves the foreground, not while using it or switching tabs. Manual Primary lock and screen-off still lock Hidden immediately. Primary may require its own PIN before Hidden can be shown.")
        Column(Modifier.selectableGroup()) {AutoLockTimeout.entries.forEach {value ->
            GalleryChoiceRow(value.label,value==timeout) {onTimeoutChanged(value)}
        }}
        Text("Locks Hidden immediately and requires fresh Primary PIN or authorized biometric authentication before showing Primary media.")
        OutlinedButton(onClick=onUsePrimary,modifier=Modifier.fillMaxWidth()) {Text("Return to Primary Vault")}
    }
}
