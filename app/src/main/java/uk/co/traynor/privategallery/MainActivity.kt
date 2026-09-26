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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    private var secretDiscovered by mutableStateOf(false)
    private var sensitiveAuthentication: ((Boolean) -> Unit)? = null
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
    private var sessionKey: ByteArray?
        get() = retained.key
        set(value) { retained.key = value }
    /** Held only while the user is being shown the newly-created offline secret. */
    private var pendingRecoveryKey: CharArray? = null
    private var pendingSourceDeletion: List<VaultItem> = emptyList()
    /** Completion is retained only for the platform deletion confirmation round-trip. */
    private var pendingSourceDeletionCompletion: ((String) -> Unit)? = null
    private var biometricPurpose: BiometricPurpose? = null
    private val wireGuardEngine by lazy { WireGuardVpnEngine(OfficialWireGuardBackend(applicationContext)) }
    private val browserVpnController by lazy { BrowserVpnController(wireGuardEngine) }
    private var browserVpnDisconnectJob: Job? = null
    private var automaticBiometricPromptAttempted = false
    private val biometricPrompt by lazy {
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher ?: run {
                    sensitiveAuthentication?.let { sensitiveAuthentication = null; it(false) }
                    return
                }
                runCatching {
                    when (biometricPurpose) {
                        BiometricPurpose.UNLOCK -> {
                            sessionKey?.fill(0)
                            sessionKey = biometrics.unwrapAuthenticated(cipher)
                            session.unlock()
                            reconcileAfterUnlock()
                            route = if (prepareRecoveryKeyIfNeeded()) Route.RECOVERY_KEY_SETUP else Route.VAULT
                        }
                        BiometricPurpose.ENROLL -> {
                            val key = checkNotNull(sessionKey)
                            biometrics.saveAuthenticated(cipher, key)
                            biometricEnabled = true
                        }
                        BiometricPurpose.SENSITIVE -> {
                            val candidate = biometrics.unwrapAuthenticated(cipher)
                            val valid = session.isUnlocked && sessionKey?.let { java.security.MessageDigest.isEqual(candidate, it) } == true
                            candidate.fill(0)
                            sensitiveAuthentication?.let { sensitiveAuthentication = null; it(valid) }
                        }
                        null -> Unit
                    }
                }.onFailure { sensitiveAuthentication?.let { sensitiveAuthentication = null; it(false) } }
                biometricPurpose = null
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancellation and lockout deliberately leave the PIN screen available without a retry loop.
                biometricPurpose = null
                sensitiveAuthentication?.let { sensitiveAuthentication = null; it(false) }
            }
        })
    }
    private val sourceDeletionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val key = sessionKey?.copyOf() ?: return@registerForActivityResult
        val pending = pendingSourceDeletion
        pendingSourceDeletion = emptyList()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = AndroidVaultRepository(applicationContext, key)
                // RESULT_OK alone is not treated as source truth. Query each source after the
                // platform request so Gallery only refreshes removed media after deletion is
                // observable in MediaStore.
                val deleted = pending.associateWith { item ->
                    result.resultCode == RESULT_OK && item.sourceUri?.let(::sourceNoLongerExists) == true
                }
                runCatching { pending.forEach { repository.finishSourceDeletionRequest(it, deleted[it] == true) } }
                val removed = deleted.values.count { it }
                val completion = pendingSourceDeletionCompletion
                pendingSourceDeletionCompletion = null
                runOnUiThread {
                    completion?.invoke(
                        if (removed == pending.size && removed > 0) "Moved to Vault. Source removed from Gallery."
                        else "Saved to Vault. Some Gallery sources remain available."
                    )
                }
            } finally {
                key.fill(0)
            }
        }
    }
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaAccessAvailable = hasDeviceMediaAccess()
    }
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        browserVpnPermissionRequired = result.resultCode != RESULT_OK
        if (result.resultCode == RESULT_OK) connectBrowserVpn() else browserVpnState = VpnConnectionState.FAILED
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
        automaticBiometricPromptAttempted = savedInstanceState?.getBoolean(AUTO_BIOMETRIC_ATTEMPTED, false) ?: false
        route = if (session.isUnlocked && sessionKey != null) retained.route else if (keys.isConfigured) Route.LOCK else Route.SETUP
        setContent {
            PrivateGalleryTheme(appTheme) {
                PrivateGalleryApp(route, ::createPin, ::unlock, ::changePin, ::recoverWithOfflineKey, ::finishRecoveryKeySetup, { route = Route.RECOVER }, { route = Route.LOCK }, ::lock, ::importSelected, ::moveSelected, ::loadItems, ::loadCollections, ::loadFavouriteCollection, ::createCollection, ::addItemsToCollection, ::removeItemsFromCollection, ::renameCollection, ::deleteCollection, ::loadCollectionItems, ::readForViewing, ::loadPreview, ::loadImageEdit, ::applyImageCrop, ::undoImageCrop, ::resetImageCrop, ::restore, ::delete, biometricEnabled, ::unlockWithBiometrics, ::enrollBiometrics, ::finishSetup, autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, availableUpdate != null, mediaAccessAvailable, ::requestDeviceMediaAccess, ::deviceMediaPages, ::loadDeviceThumbnail, ::openSettings, { openNonBrowser(Route.GALLERY); mediaAccessAvailable = hasDeviceMediaAccess() }, { openNonBrowser(Route.VAULT) }, { openNonBrowser(Route.FAVOURITE) }, ::openBrowser, ::applyAutoLockTimeout, ::applyTheme, ::applyAllowScreenshots, ::applyBrowserSearchEngine, ::applyClearBrowserDataOnLock, ::clearBrowserData, browserSearchEngine, clearBrowserDataOnLock, browserSaveHistory, ::applyBrowserSaveHistory, browserWebView, { view -> browserWebView = view }, { exit -> browserFullscreenExit = exit }, ::checkForUpdates, ::downloadUpdate, recoveryKeys.isConfigured, pendingRecoveryKey?.concatToString(), ::importBrowserSource, browserRequireVpn, browserVpnState == VpnConnectionState.CONNECTED, ::importWireGuardProfile, vpnProfileStatus, browserVpnState, browserAutoConnectVpn, ::applyBrowserAutoConnectVpn, ::applyBrowserRequireVpn, ::setFavouriteCollection, vpnProfiles, ::selectVpnProfile, ::removeVpnProfile, browserBookmarks, ::addBrowserBookmark, ::removeBrowserBookmark, browserV2Session, ::loadBrowserHistory, ::clearBrowserHistory, ::recordBrowserHistory, browserVpnPreparing, browserVpnPermissionRequired, ::requestBrowserVpnPermissionOrConnect, { item, bytes, cancelled, completed -> saveEditedCopy(item, bytes, cancelled, completed) }, ::readForEditing, { item, bytes, cancelled, completed -> saveEditedCopy(item, bytes, cancelled, completed, true) }, onReadVideoForViewing = ::readVideoForViewing, onSaveAiCopy = { item, bytes, provenance, cancelled, completed -> saveEditedCopy(item, bytes, cancelled, completed, aiProvenance = provenance) }, onPrepareBrowserUpload = ::prepareBrowserUpload, onClearBrowserUpload = ::clearBrowserUploadCopies, hideContent = hideContent, secretDiscovered = secretDiscovered, onHideContentChanged = ::applyHideContent, onSecretDiscoveryChanged = ::applySecretDiscovery, onAuthenticateSensitive = ::authenticateSensitive, onVerifySecretPin = ::verifySecretPin)
            }
        }
        window.decorView.post(::triggerAutomaticBiometricPromptIfNeeded)
    }

    override fun onStop() {
        super.onStop()
        sensitiveAuthentication?.let { sensitiveAuthentication = null; it(false) }
        if (biometricPurpose == BiometricPurpose.SENSITIVE) {
            biometricPrompt.cancelAuthentication()
            biometricPurpose = null
        }
        clearBrowserUploadCopies()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_background")) }
        retained.route = route
        val interactive = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        session.onActivityStopped(android.os.SystemClock.elapsedRealtime(), isChangingConfigurations, interactive)
        if (isChangingConfigurations && interactive) return
        if (!session.isUnlocked) lock() else scheduleOwnedVpnDisconnect()
    }

    override fun onStart() {
        super.onStart()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_foreground")) }
        val wasUnlocked = session.isUnlocked
        session.onForegrounded(android.os.SystemClock.elapsedRealtime())
        if (!session.isUnlocked && keys.isConfigured) {
            if (wasUnlocked) automaticBiometricPromptAttempted = false
            lock()
            triggerAutomaticBiometricPromptIfNeeded()
        } else if (route == Route.BROWSER) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(AUTO_BIOMETRIC_ATTEMPTED, automaticBiometricPromptAttempted)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
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
        if (!isChangingConfigurations) { sessionKey?.fill(0); sessionKey = null; session.lock() }
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

    private fun unlock(pin: CharArray): Result<Unit> = runCatching {
        sessionKey?.fill(0)
        sessionKey = keys.unlock(pin)
        session.unlock()
        reconcileAfterUnlock()
        route = if (prepareRecoveryKeyIfNeeded()) Route.RECOVERY_KEY_SETUP else Route.VAULT
    }

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

    private fun lock() {
        sensitiveAuthentication?.let { sensitiveAuthentication = null; it(false) }
        clearBrowserUploadCopies()
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
        automaticBiometricPromptAttempted = false
        if (::keys.isInitialized && keys.isConfigured) route = Route.LOCK
    }

    /** Cleans interrupted ciphertext and never attempts to delete a source item. */
    private fun reconcileAfterUnlock() {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AndroidVaultRepository(applicationContext, key).reconcile() }
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key).list() }.getOrDefault(emptyList())
            val browserSession = runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserSessionStore(File(filesDir, "browser-session"), key).load() }.getOrNull()
            key.fill(0)
            runOnUiThread {
                if (!hideContent) {
                    browserBookmarks = bookmarks
                    browserSession?.let(browserV2Session::restoreMetadata)
                }
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

    private fun openSettings() {
        leaveBrowserIfOpen()
        route = Route.SETTINGS
        refreshVpnProfiles()
    }

    private fun openNonBrowser(destination: Route) {
        leaveBrowserIfOpen()
        route = destination
    }

    private fun leaveBrowserIfOpen() {
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
        browserV2Session.enforceNetworkPolicy()
        if (browserVpnState == VpnConnectionState.CONNECTED) {
            // The service is a task-removal hook only; background-start restrictions must never
            // affect the confirmed tunnel or Browser's fail-closed state.
            runCatching { startService(Intent(this, VpnOwnershipService::class.java)) }
        } else if (browserVpnState == VpnConnectionState.DISCONNECTED || browserVpnState == VpnConnectionState.FAILED) {
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

    private fun openBrowser() {
        browserVpnPreparing = browserRequireVpn
        browserVpnPermissionRequired = false
        route = Route.BROWSER
        browserVpnDisconnectJob?.cancel()
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = runCatching {
                VpnProfileRepository(File(filesDir, "vpn-profiles"), key).snapshot().let { snapshot ->
                    snapshot.profiles.singleOrNull { it.id == snapshot.activeProfileId }
                }
            }.getOrNull()
            key.fill(0)
            runOnUiThread {
                browserVpnController.select(profile)
                browserVpnState = browserVpnController.enterBrowser(false, browserRequireVpn, System.currentTimeMillis())
                browserVpnPreparing = false
                if (browserRequireVpn && browserAutoConnectVpn && profile != null) requestBrowserVpnPermissionOrConnect()
            }
        }
    }

    private fun requestBrowserVpnPermissionOrConnect() {
        if (browserVpnController.state in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING)) return
        val permissionIntent = VpnService.prepare(this)
        browserVpnPermissionRequired = permissionIntent != null
        if (permissionIntent != null) {
            // Android owns this confirmation. Private Gallery never stops another app's VPN itself.
            vpnPermissionLauncher.launch(permissionIntent)
        } else connectBrowserVpn()
    }

    private fun importWireGuardProfile() {
        vpnProfileDocumentLauncher.launch(arrayOf("application/octet-stream", "text/plain", "application/wireguard"))
    }

    private fun connectBrowserVpn() {
        browserVpnPermissionRequired = false
        if (browserVpnController.state in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING)) return
        // Explicit permission/retry action connects even when automatic connection is disabled.
        browserVpnState = browserVpnController.enterBrowser(true, browserRequireVpn, System.currentTimeMillis())
    }

    private fun finishSetup() {
        route = Route.VAULT
    }

    private fun finishRecoveryKeySetup() {
        pendingRecoveryKey?.fill('\u0000')
        pendingRecoveryKey = null
        route = if (biometricAvailable) Route.BIOMETRIC_SETUP else Route.VAULT
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

    override fun onBackPressed() {
        if (route == Route.BROWSER) {
            when (BrowserNavigationPolicy.backAction(browserFullscreenExit != null, browserWebView?.canGoBack() == true)) {
                uk.co.traynor.privategallery.core.browser.BrowserBackAction.EXIT_FULLSCREEN -> {
                    browserFullscreenExit?.invoke()
                    browserFullscreenExit = null
                    return
                }
                uk.co.traynor.privategallery.core.browser.BrowserBackAction.GO_BACK -> {
                    browserWebView?.goBack()
                    return
                }
                uk.co.traynor.privategallery.core.browser.BrowserBackAction.FALL_THROUGH -> Unit
            }
        }
        super.onBackPressed()
    }

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

    private fun deviceMediaPages(): Flow<PagingData<DeviceMediaItem>> =
        if (hideContent) kotlinx.coroutines.flow.flowOf(PagingData.empty()) else deviceGallery.pagedItems()

    private fun loadDeviceThumbnail(item: DeviceMediaItem, onLoaded: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) {
        if (hideContent) { onLoaded(null); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val thumbnail = deviceGallery.thumbnail(item, 360)?.asImageBitmap()
            runOnUiThread { onLoaded(if (hideContent) null else thumbnail) }
        }
    }

    private fun applyHideContent(hidden: Boolean) {
        if (hidden == hideContent) return
        check(appSettings.edit().putString("hide-content", hidden.toString()).commit()) { "Unable to save privacy setting" }
        hideContent = hidden
        if (hidden) {
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
        if (!hidden) reconcileAfterUnlock()
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
        sensitiveAuthentication?.invoke(false)
        sensitiveAuthentication = completed
        biometricPurpose = BiometricPurpose.SENSITIVE
        runCatching {
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle("Confirm owner identity")
                    .setSubtitle("Authenticate to change protected settings")
                    .setNegativeButtonText("Use PIN").build(),
                BiometricPrompt.CryptoObject(biometrics.newDecryptCipher()),
            )
        }.onFailure { biometricPurpose = null; sensitiveAuthentication = null; completed(false) }
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

    private fun importSelected(uris: List<android.net.Uri>, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("No media selected."); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = AndroidVaultRepository(applicationContext, key)
                var copied = 0
                var duplicates = 0
                uris.forEach { uri ->
                    if (hideContent) return@forEach
                    when (repository.import(uri)) {
                        is ImportResult.Imported -> copied++
                        is ImportResult.Duplicate -> duplicates++
                    }
                }
                runOnUiThread {
                    onComplete(
                        buildString {
                            append("Saved ").append(copied).append(" item")
                            if (copied != 1) append('s')
                            if (duplicates > 0) append("; ").append(duplicates).append(" duplicate(s) already in Vault")
                        },
                    )
                }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to copy the selected media. Originals were not changed.") }
            } finally {
                key.fill(0)
            }
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

    private fun moveSelected(uris: List<android.net.Uri>, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("No media selected."); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onComplete("Saved to Vault only. Android deletion confirmation requires Android 11 or later.")
            importSelected(uris, onComplete)
            return
        }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = AndroidVaultRepository(applicationContext, key)
            try {
                val imported = uris.mapNotNull { uri ->
                    if (hideContent) return@mapNotNull null
                    (repository.import(uri) as? ImportResult.Imported)?.item
                }
                if (hideContent) return@launch
                imported.forEach(repository::markDeletePending)
                val sources = imported.mapNotNull { it.sourceUri?.let(android.net.Uri::parse) }
                if (sources.isEmpty()) {
                    runOnUiThread { onComplete("Saved to Vault. The selected media was already protected or unavailable.") }
                } else {
                    pendingSourceDeletion = imported
                    pendingSourceDeletionCompletion = onComplete
                    val request = MediaStore.createDeleteRequest(contentResolver, sources)
                    runOnUiThread {
                        if (!hideContent) sourceDeletionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }
                }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to move media. Originals were not changed.") }
            } finally {
                key.fill(0)
            }
        }
    }

    private fun sourceNoLongerExists(raw: String): Boolean = runCatching {
        contentResolver.query(android.net.Uri.parse(raw), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            .use { cursor -> cursor == null || !cursor.moveToFirst() }
    }.getOrDefault(false)

    private fun restore(item: VaultItem, removeAfter: Boolean, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Unavailable."); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = AndroidVaultRepository(applicationContext, key)
                if (removeAfter) repository.restoreAndRemove(item) else repository.restore(item)
                runOnUiThread { onComplete(if (removeAfter) "Restored to Gallery and removed from Vault." else "Restored to Gallery. Vault copy retained.") }
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
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).applyImageCrop(item.id, crop) } }
            key.fill(0)
            runOnUiThread { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun undoImageCrop(item: VaultItem, onComplete: (Result<ImageEditState?>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).undoImageCrop(item.id) } }
            key.fill(0)
            runOnUiThread { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun resetImageCrop(item: VaultItem, onComplete: (Result<Unit>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).resetImageCrop(item.id) } }
            key.fill(0)
            runOnUiThread { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
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
        val key = sessionKey?.copyOf()
        if (key == null) { bytes.fill(0); completed(Result.failure(IllegalStateException("Vault locked"))); return }
        val ownerSession = sessionKey
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val repository = AndroidVaultRepository(applicationContext, key)
                repository.importAiEditedCopy(item.id, bytes, aiProvenance ?: if (remoteAi) uk.co.traynor.privategallery.core.editor.AiEditProvenance(uk.co.traynor.privategallery.core.editor.AiProcessing.CLOUD, "replicate-seedream", "seedream-4.5") else null,
                    uk.co.traynor.privategallery.core.editor.AiConsentStore(applicationContext).keepEditsInVault()) {
                    hideContent || cancelled() || sessionKey !== ownerSession
                }
            }
            key.fill(0); bytes.fill(0)
            runOnUiThread { completed(result) }
        }.invokeOnCompletion { key.fill(0); bytes.fill(0) }
    }

    private fun delete(item: VaultItem, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Unavailable."); return }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, key).deleteFromVault(item) }
                runOnUiThread { invalidatePreview(item.id); onComplete("Removed from Vault.") }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to remove this item from Vault.") }
            } finally { key.fill(0) }
        }
    }

    private fun unlockWithBiometrics() {
        if (!biometrics.isEnabled) return
        biometricPurpose = BiometricPurpose.UNLOCK
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Private Gallery")
                .setSubtitle("Unlock your Vault")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN")
                .build(),
            BiometricPrompt.CryptoObject(biometrics.newDecryptCipher()),
        )
    }

    private fun enrollBiometrics() {
        if (sessionKey == null) return
        biometricPurpose = BiometricPurpose.ENROLL
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric unlock")
                .setSubtitle("Use biometrics to unlock Private Gallery")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(biometrics.newEncryptCipher()),
        )
    }

    private fun triggerAutomaticBiometricPromptIfNeeded() {
        if (!::keys.isInitialized || !BiometricPromptPolicy.shouldAutoPrompt(
                isLocked = !session.isUnlocked && route == Route.LOCK,
                biometricEnabled = biometricEnabled,
                biometricAvailable = biometricAvailable,
                alreadyPromptedForLockEntry = automaticBiometricPromptAttempted,
            )
        ) return
        automaticBiometricPromptAttempted = true
        window.decorView.post {
            if (!isFinishing && route == Route.LOCK && !session.isUnlocked) unlockWithBiometrics()
        }
    }

    private companion object {
        const val AUTO_BIOMETRIC_ATTEMPTED = "automatic-biometric-attempted"
    }
}

internal enum class Route { SETUP, RECOVERY_KEY_SETUP, BIOMETRIC_SETUP, LOCK, RECOVER, GALLERY, VAULT, FAVOURITE, BROWSER, SETTINGS }
private enum class BiometricPurpose { UNLOCK, ENROLL, SENSITIVE }

private fun AppNavigationDestination.matches(route: Route): Boolean = when (this) {
    AppNavigationDestination.GALLERY -> route == Route.GALLERY
    AppNavigationDestination.VAULT -> route == Route.VAULT
    AppNavigationDestination.FAVOURITE -> route == Route.FAVOURITE
    AppNavigationDestination.BROWSER -> route == Route.BROWSER
    AppNavigationDestination.SETTINGS -> route == Route.SETTINGS
}

private sealed interface ViewerRequest {
    data class Gallery(val entries: List<ViewerMediaEntry>, val initialIndex: Int) : ViewerRequest
    data class Vault(val entries: List<ViewerMediaEntry>, val items: Map<String, VaultItem>, val initialIndex: Int) : ViewerRequest
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
    onDeviceMediaPages: () -> Flow<PagingData<DeviceMediaItem>>,
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
    onSaveAiCopy: (VaultItem, ByteArray, uk.co.traynor.privategallery.core.editor.AiEditProvenance, () -> Boolean, (Result<VaultItem>) -> Unit) -> Unit,
    onPrepareBrowserUpload: (List<VaultItem>, (Result<List<Uri>>) -> Unit) -> Unit,
    onClearBrowserUpload: () -> Unit,
    hideContent: Boolean,
    secretDiscovered: Boolean,
    onHideContentChanged: (Boolean) -> Unit,
    onSecretDiscoveryChanged: (Boolean) -> Unit,
    onAuthenticateSensitive: ((Boolean) -> Unit) -> Unit,
    onVerifySecretPin: (CharArray) -> Boolean,
) {
    // Acceptance aids are opt-in for this app composition and never saved to preferences.
    var browserStaticContentHost by remember { mutableStateOf(false) }
    var browserLayoutColours by remember { mutableStateOf(false) }
    var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }
    var cropRevision by remember { mutableStateOf(0) }
    var collectionTarget by remember { mutableStateOf<String?>(null) }
    var viewerCollections by remember { mutableStateOf<List<VaultCollection>>(emptyList()) }
    var favouriteLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route, hideContent) {
        if (hideContent) {
            viewerRequest = null
            collectionTarget = null
            viewerCollections = emptyList()
            favouriteLabel = null
            return@LaunchedEffect
        }
        if (route == Route.LOCK || route == Route.SETUP || route == Route.RECOVERY_KEY_SETUP || route == Route.BIOMETRIC_SETUP || route == Route.RECOVER) viewerRequest = null
        if (route == Route.GALLERY || route == Route.VAULT || route == Route.FAVOURITE || route == Route.BROWSER || route == Route.SETTINGS) {
            onLoadFavouriteCollection { favouriteLabel = it?.name }
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    when (route) {
    Route.SETUP -> PinSetup(onCreatePin)
    Route.RECOVERY_KEY_SETUP -> RecoveryKeySetup(checkNotNull(recoveryKeyForSetup), onFinishRecoveryKeySetup)
    Route.BIOMETRIC_SETUP -> BiometricSetup(onEnrollBiometrics, onFinishSetup)
    Route.LOCK -> PinUnlock(onUnlock, biometricEnabled, onBiometricUnlock, onForgotPin = onOpenRecovery)
    Route.RECOVER -> RecoveryKeyUnlock(onRecoverWithOfflineKey, onCancel = onCloseRecovery)
    Route.GALLERY, Route.VAULT, Route.FAVOURITE, Route.BROWSER, Route.SETTINGS -> Box(Modifier.fillMaxSize()) {
    // Register the destination fallback before child handlers: viewer, settings,
    // WebView history and fullscreen consume Back first, including system gestures.
    androidx.activity.compose.BackHandler(route != Route.GALLERY && viewerRequest == null) { onOpenGallery() }
    ProtectedAppShell(route, if (hideContent) null else favouriteLabel, hideNavigation = route == Route.BROWSER && !hideContent, onNavigate = { destination ->
        when (destination) {
            AppNavigationDestination.GALLERY -> onOpenGallery()
            AppNavigationDestination.VAULT -> onOpenVault()
            AppNavigationDestination.FAVOURITE -> onOpenFavourite()
            AppNavigationDestination.BROWSER -> onOpenBrowser()
            AppNavigationDestination.SETTINGS -> onOpenSettings()
        }
    }) { contentPadding ->
        if (hideContent && route != Route.SETTINGS) {
            val title = when (route) { Route.GALLERY -> "Gallery"; Route.VAULT -> "Vault"; Route.FAVOURITE -> "Favourite"; else -> "Browser" }
            val empty = when (route) { Route.GALLERY -> "No photos"; Route.VAULT -> "Your Vault is empty"; Route.FAVOURITE -> "No favourites"; else -> "No recent pages" }
            Column(Modifier.fillMaxSize().padding(contentPadding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GalleryPageTitle(title, empty)
            }
        } else when (route) {
            Route.GALLERY -> GalleryHome(deviceMediaAccessAvailable, onRequestDeviceMediaAccess, onDeviceMediaPages, onLoadDeviceThumbnail, onImport, onMove, onOpenViewer = { entries, index -> viewerRequest = ViewerRequest.Gallery(entries, index) }, modifier = Modifier.padding(contentPadding))
            Route.VAULT -> VaultHome(onLock, onImport, onLoadItems, onLoadCollections, onCreateCollection, onAddItemsToCollection, onRemoveItemsFromCollection, onRenameCollection, onDeleteCollection, onLoadCollectionItems, onLoadPreview, cropRevision, biometricEnabled, onEnrollBiometrics, onSetFavouriteCollection, onFavouriteStateChanged = { onLoadFavouriteCollection { favouriteLabel = it?.name } }, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index) }, modifier = Modifier.padding(contentPadding))
            Route.FAVOURITE -> FavouriteHome(onLoadFavouriteCollection, onLoadItems, onLoadCollectionItems, onAddItemsToCollection, onRemoveItemsFromCollection, onLoadPreview, cropRevision, onOpenVault, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index) }, modifier = Modifier.padding(contentPadding))
            Route.BROWSER -> BrowserV2ProductionDestination(
                session = browserV2Session,
                connectionPresentation = BrowserConnectionPresentation.from(requireVpnForBrowsing, vpnConnectionState, vpnPermissionRequired, vpnPreparing),
                onConnectVpn = onConnectBrowserVpn,
                onOpenGallery = onOpenGallery,
                onOpenVault = onOpenVault,
                onOpenFavourite = onOpenFavourite,
                favouriteLabel = favouriteLabel ?: "Favourite",
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
                onSaveToVault = onSaveBrowserSource,
                onLoadVaultItems = onLoadItems,
                onPrepareVaultUpload = onPrepareBrowserUpload,
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
            Route.SETTINGS -> SettingsHome(autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, updateAvailable, biometricEnabled, recoveryKeyConfigured, browserSearchEngine, clearBrowserDataOnLock, onAutoLockTimeoutChanged, onThemeChanged, onAllowScreenshotsChanged, onBrowserSearchEngineChanged, onClearBrowserDataOnLockChanged, onClearBrowserData, onCheckForUpdates, onDownloadUpdate, onChangePin, onLock, browserAutoConnectVpn, requireVpnForBrowsing, onBrowserAutoConnectVpnChanged, onBrowserRequireVpnChanged, onImportWireGuardProfile, vpnProfileStatus, vpnConnectionState, vpnProfiles, onSelectVpnProfile, onRemoveVpnProfile, modifier = Modifier.padding(contentPadding), browserSaveHistory = browserSaveHistory, onBrowserSaveHistoryChanged = onBrowserSaveHistoryChanged, browserStaticContentHost = browserStaticContentHost, onBrowserStaticContentHostChanged = { browserStaticContentHost = it }, browserLayoutColours = browserLayoutColours, onBrowserLayoutColoursChanged = { browserLayoutColours = it }, contentBlocker = browserV2Session.contentBlocker, hideContent = hideContent, secretDiscovered = secretDiscovered, onHideContentChanged = onHideContentChanged, onSecretDiscoveryChanged = onSecretDiscoveryChanged, onAuthenticateSensitive = onAuthenticateSensitive, onVerifySecretPin = onVerifySecretPin)
            else -> Unit
        }
    }
    if (!hideContent) collectionTarget?.let { itemId ->
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
                onCopyToVault = { entry -> entry.uri?.let { onImport(listOf(it)) {} } },
                onMoveToVault = { entry -> entry.uri?.let { onMove(listOf(it)) {} } },
            )
            is ViewerRequest.Vault -> FullscreenMediaViewer(
                entries = request.entries,
                source = MediaViewerSource.VAULT,
                initialIndex = request.initialIndex,
                onClose = { viewerRequest = null },
                onLoadProtectedBytes = { id, loaded -> request.items[id]?.let { onReadForViewing(it, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadVideoBytes = { id, cancelled, progress, loaded -> request.items[id]?.let { onReadVideoForViewing(it, cancelled, progress, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadImageEdit = { id, loaded -> request.items[id]?.let { onLoadImageEdit(it, loaded) } ?: loaded(null) },
                onApplyImageCrop = { id, crop, completed -> request.items[id]?.let { onApplyImageCrop(it, crop, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onUndoImageCrop = { id, completed -> request.items[id]?.let { onUndoImageCrop(it, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onResetImageCrop = { id, completed -> request.items[id]?.let { onResetImageCrop(it, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onCropChanged = { cropRevision++ },
                onLoadEditorBytes = { id, cancelled, loaded -> request.items[id]?.let { onReadForEditing(it, cancelled, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing item"))) },
                onAddToCollection = { entry -> onLoadCollections { viewerCollections = it; collectionTarget = entry.id } },
                onSaveEditedCopy = { id, bytes, cancelled, completed ->
                    val original = request.items[id]
                    if (original == null) completed(Result.failure(IllegalStateException("Missing item")))
                    else onSaveEditedCopy(original, bytes, cancelled) { result ->
                        completed(result.map { Unit })
                        result.onSuccess { copy ->
                            if (cancelled()) return@onSuccess
                            cropRevision++
                            viewerRequest = ViewerRequest.Vault(listOf(ViewerMediaEntry(copy.id, copy.mimeType)), mapOf(copy.id to copy), 0)
                        }
                    }
                },
                onSaveRemoteCopy = { id, bytes, cancelled, completed ->
                    val original = request.items[id]
                    if (original == null) completed(Result.failure(IllegalStateException("Missing item")))
                    else onSaveRemoteCopy(original, bytes, cancelled) { result ->
                        completed(result.map { Unit })
                        result.onSuccess { copy ->
                            if (cancelled()) return@onSuccess
                            cropRevision++
                            viewerRequest = ViewerRequest.Vault(listOf(ViewerMediaEntry(copy.id, copy.mimeType)), mapOf(copy.id to copy), 0)
                        }
                    }
                },
                onSaveAiCopy = { id, bytes, provenance, cancelled, completed ->
                    val original = request.items[id]
                    if (original == null) completed(Result.failure(IllegalStateException("Missing item")))
                    else onSaveAiCopy(original, bytes, provenance, cancelled) { result ->
                        completed(result.map { Unit })
                        result.onSuccess { copy ->
                            if (cancelled()) return@onSuccess
                            cropRevision++
                            viewerRequest = ViewerRequest.Vault(listOf(ViewerMediaEntry(copy.id, copy.mimeType)), mapOf(copy.id to copy), 0)
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
}

@Composable
internal fun ProtectedAppShell(
    selected: Route,
    favouriteLabel: String?,
    onNavigate: (AppNavigationDestination) -> Unit,
    hideNavigation: Boolean = selected == Route.BROWSER,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideNavigation) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                AppNavigationPolicy.destinations.forEach { destination ->
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
    onDeviceMediaPages: () -> Flow<PagingData<DeviceMediaItem>>,
    onLoadDeviceThumbnail: (DeviceMediaItem, (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Map<Long, android.net.Uri>>(emptyMap()) }
    var galleryOverflowExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var density by rememberSaveable { mutableStateOf(ThumbnailDensity.COMPACT) }
    val deviceMediaFlow = remember(deviceMediaAccessAvailable) {
        if (deviceMediaAccessAvailable) onDeviceMediaPages() else flowOf(PagingData.empty())
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
        )
        if (galleryOverflowExpanded) GalleryMenuSheet("Gallery", onDismiss = { galleryOverflowExpanded = false }) {
            SheetSection("Media")
            SheetAction("Select items", Icons.Default.CheckCircle, enabled = deviceMedia.itemCount > 0) {
                selectionMode = true; galleryOverflowExpanded = false
            }
            SheetAction("Refresh", Icons.Default.Refresh) { deviceMedia.refresh(); galleryOverflowExpanded = false }
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
                            ) { Text("Copy to Vault") }
                            androidx.compose.material3.OutlinedButton(
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
                            ) { Text("Move to Vault") }
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
                    Text("No accessible media", style = MaterialTheme.typography.titleMedium)
                    Text("Photos and videos you allow Private Gallery to access will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun PinSetup(onCreatePin: (CharArray) -> Result<Unit>) {
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
private fun PinUnlock(onUnlock: (CharArray) -> Result<Unit>, biometricEnabled: Boolean, onBiometricUnlock: () -> Unit, onForgotPin: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PinPage(
        title = "Private Gallery",
        detail = "Unlock to view protected media.",
        pin = pin,
        onPinChange = { pin = it },
        action = "Unlock",
        message = message,
        secondaryAction = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onForgotPin) { Text("Forgot PIN?") }
                if (biometricEnabled) TextButton(onClick = onBiometricUnlock) { Text("Use biometrics") }
            }
        },
    ) {
        onUnlock(pin.toCharArray()).onSuccess {
            pin = ""
            message = null
        }.onFailure {
            pin = ""
            message = if (it is InvalidPinException) "Incorrect PIN." else "Unable to unlock the vault."
        }
    }
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
private fun PinPage(
    title: String,
    detail: String,
    pin: String,
    onPinChange: (String) -> Unit,
    action: String,
    message: String?,
    confirmation: String? = null,
    onConfirmationChange: ((String) -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    onAction: () -> Unit,
) {
    LockSurface {
        GalleryPageTitle(if (action == "Unlock") "Protected on this device" else "Welcome", title)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = pin,
            onValueChange = { onPinChange(it.filter(Char::isDigit)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
        )
        if (confirmation != null && onConfirmationChange != null) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = { onConfirmationChange(it.filter(Char::isDigit)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
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
    onVerifySecretPin: (CharArray) -> Boolean = { it.fill('\u0000'); false },
) {
    val appContext = LocalContext.current.applicationContext
    val aiConfiguration = remember(appContext) { uk.co.traynor.privategallery.core.editor.AiProviderRegistry.initialize(appContext) }
    val aiStatus by aiConfiguration.status.collectAsState()
    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var secretOpen by remember { mutableStateOf(false) }
    var authAction by remember { mutableStateOf<String?>(null) }
    var discovery by remember { mutableStateOf(uk.co.traynor.privategallery.core.security.SecretDiscoveryState()) }
    androidx.activity.compose.BackHandler(category != null || secretOpen) {
        if (secretOpen) secretOpen = false else category = null
    }
    LaunchedEffect(category, secretDiscovered) {
        if (category != SettingsCategory.SECURITY || !secretDiscovered) secretOpen = false
    }
    var changingPin by remember { mutableStateOf(false) }
    var confirmScreenshots by remember { mutableStateOf(false) }
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
            SettingsCategory.entries.forEach { destination ->
                val summary = when (destination) {
                    SettingsCategory.SECURITY -> "PIN, biometrics and auto-lock"
                    SettingsCategory.BROWSER -> "${browserSearchEngine.label} · History ${if (browserSaveHistory) "on" else "off"}"
                    SettingsCategory.VPN -> uk.co.traynor.privategallery.core.vpn.VpnProfilePresentation.from(vpnProfiles, vpnConnectionState).let { "${it.selectedProfileName} · ${it.connectionLabel}" }
                    SettingsCategory.AI -> uk.co.traynor.privategallery.core.editor.AiProviderRegistry.choice.label
                    SettingsCategory.ABOUT -> "${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}"
                    else -> destination.summary
                }
                if (destination == SettingsCategory.DEBUG) androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.Surface(onClick = { category = destination }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(when (destination) {
                            SettingsCategory.SECURITY -> Icons.Default.Shield
                            SettingsCategory.GALLERY -> Icons.Default.PhotoLibrary
                            SettingsCategory.BROWSER -> Icons.Default.Language
                            SettingsCategory.VPN -> Icons.Default.VpnKey
                            SettingsCategory.AI -> Icons.Default.AutoAwesome
                            SettingsCategory.APPEARANCE -> Icons.Default.Palette
                            SettingsCategory.ABOUT -> Icons.Default.Info
                            SettingsCategory.DEBUG -> Icons.Default.BugReport
                        }, null, Modifier.padding(end = 16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(destination.title, style = MaterialTheme.typography.titleMedium)
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
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
        if (category == SettingsCategory.GALLERY) SettingsSection("Gallery & Vault") {
            Text("Thumbnail size", style = MaterialTheme.typography.titleMedium)
            Text("Open the Gallery or Vault menu to choose thumbnail size for that grid. Vault search, sort and media filters remain in the Vault menu.")
            Text("Collections", style = MaterialTheme.typography.titleMedium)
            Text("Manage collections and choose your Favourite from Vault. Originals are retained when saving an edited copy.")
        }
        if (category == SettingsCategory.SECURITY && !secretOpen && secretDiscovered) SettingsSection("Protected settings") {
            androidx.compose.material3.Surface(onClick = { authAction = "enter" }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
            Text("Auto-lock", style = MaterialTheme.typography.titleMedium)
            Text("Lock protected content after Private Gallery leaves the foreground.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                AutoLockTimeout.entries.forEach { timeout ->
                    GalleryChoiceRow(timeout.label, timeout == autoLockTimeout) { onAutoLockTimeoutChanged(timeout) }
                }
            }
        }
        if (category == SettingsCategory.SECURITY && !secretOpen) SettingsSection(SettingsSections.PRIVACY) {
            Text("Secure-screen protection", style = MaterialTheme.typography.titleMedium)
            Text("Protected screens are excluded from screenshots and Recents previews where Android supports it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Backup protection", style = MaterialTheme.typography.titleMedium)
            Text("Private Gallery data is excluded from Android backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (category == SettingsCategory.DEBUG) SettingsSection(SettingsSections.DEBUG) {
            if (BuildConfig.DEBUG || BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) {
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
                    if (it) onHideContentChanged(true) else authAction = "reveal"
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
                    onCheckedChange = { enabled -> if (enabled) authAction = "screenshots" else onAllowScreenshotsChanged(false) },
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
        if (category == SettingsCategory.BROWSER) uk.co.traynor.privategallery.ui.BrowserSettingsContent(
            browserSearchEngine, onBrowserSearchEngineChanged, browserSaveHistory, onBrowserSaveHistoryChanged,
            browserRequireVpn, onBrowserRequireVpnChanged, browserAutoConnectVpn, onBrowserAutoConnectVpnChanged,
            clearBrowserDataOnLock, onClearBrowserDataOnLockChanged, onClearBrowserData, contentBlocker,
        )
        if (category == SettingsCategory.AI) uk.co.traynor.privategallery.ui.AiEditingSettings()
        if (category == SettingsCategory.ABOUT) SettingsSection(SettingsSections.UPDATES) {
            androidx.compose.material3.Surface(onClick = {
                discovery = discovery.tap()
                if (discovery.discovered && !secretDiscovered) onSecretDiscoveryChanged(true)
            }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Installed", style = MaterialTheme.typography.titleMedium)
                    Text("${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (discovery.taps in 7..9 && !secretDiscovered) Text("${discovery.remaining} more taps to unlock protected settings")
            if (discovery.discovered && secretDiscovered) Text("Protected settings unlocked")
            Text("Latest", style = MaterialTheme.typography.titleMedium)
            Text(updateStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last checked", style = MaterialTheme.typography.titleMedium)
            Text(updateLastChecked, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
            if (updateAvailable) Button(onClick = onDownloadUpdate, modifier = Modifier.fillMaxWidth()) { Text("Download update") }
        }
        if (category == SettingsCategory.ABOUT) SettingsSection(SettingsSections.ABOUT) {
            Text("Private Gallery ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("Media stays in encrypted private app storage until you restore it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { showingLicences = true }, modifier = Modifier.fillMaxWidth()) { Text("Third-party licences") }
        }
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
    }
    authAction?.let { requested ->
        SecretAuthenticationDialog(
            biometricEnabled = biometricEnabled,
            onBiometric = { finished -> onAuthenticateSensitive { valid -> if (authAction == requested) finished(valid) } },
            onVerifyPin = onVerifySecretPin,
            onDone = { authenticated ->
                if (authAction == requested) {
                    authAction = null
                    if (authenticated) when (requested) {
                        "enter" -> if (secretDiscovered) secretOpen = true
                        "reveal" -> if (secretOpen && hideContent) onHideContentChanged(false)
                        "screenshots" -> if (secretOpen && !allowScreenshots) confirmScreenshots = true
                    }
                }
            },
        )
    }
    if (changingPin) ChangePinDialog(onChangePin) { changingPin = false }
    if (confirmScreenshots) {
        AlertDialog(
            onDismissRequest = { confirmScreenshots = false },
            title = { Text("Allow screenshots?") },
            text = { Text("Screenshots and screen recordings may contain private vault content while this setting is enabled.") },
            confirmButton = { TextButton(onClick = { confirmScreenshots = false; onAllowScreenshotsChanged(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { confirmScreenshots = false }) { Text("Cancel") } },
        )
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
    biometricEnabled: Boolean,
    onBiometric: ((Boolean) -> Unit) -> Unit,
    onVerifyPin: (CharArray) -> Boolean,
    onDone: (Boolean) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
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
) {
    var status by remember { mutableStateOf("") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var collections by remember { mutableStateOf<List<VaultCollection>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var density by rememberSaveable { mutableStateOf(ThumbnailDensity.COMPACT) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MediaKindFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(MediaSort.NEWEST) }
    val displayedItems = remember(vaultItems, query, kind, sort) { VaultBrowsePolicy.apply(vaultItems, query, kind, sort) }
    var refreshRevision by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var contentMode by remember { mutableStateOf(VaultContentMode.MEDIA) }
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
    fun refresh() {
        refreshRevision++
        onLoadItems { items -> vaultItems = items; loaded = true }
        onLoadCollections { collections = it }
    }
    LaunchedEffect(cropRevision) { refresh() }
    LaunchedEffect(openCollection?.id, cropRevision, refreshRevision) {
        openCollection?.let { collection -> onLoadCollectionItems(collection.id) { openCollectionItems = it } }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GalleryTokens.MediaGap, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaHeader(
            title = openCollection?.name ?: "Vault",
            subtitle = if (openCollection == null) VaultSummary.from(vaultItems).let { "${it.photos} photos · ${it.videos} videos" } else "${openCollectionItems.size} items",
            onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onBack = openCollection?.let { { openCollection = null } },
            onLock = onLock,
            onMenu = { menuOpen = true },
        )
        if (menuOpen) GalleryMenuSheet("Vault", onDismiss = { menuOpen = false }) {
            SheetSection("Add and organise")
            SheetAction("Add media", Icons.Default.AddPhotoAlternate) { menuOpen = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            if (openCollection == null && contentMode == VaultContentMode.MEDIA) SheetAction("Select items", Icons.Default.CheckCircle, enabled = vaultItems.isNotEmpty()) {
                selectionMode = true; menuOpen = false
            }
            SheetAction("New collection", Icons.Default.CreateNewFolder) { creatingCollection = true; menuOpen = false }
            SheetAction("Refresh", Icons.Default.Refresh) { refresh(); menuOpen = false }
            if (openCollection == null && contentMode == VaultContentMode.MEDIA) MediaSortChoices(sort) { sort = it }
            SheetSection("View")
            ThumbnailSizeChoices(density) { density = it }
            SheetSection("Security")
            if (!biometricEnabled) SheetAction("Enable biometric unlock", Icons.Default.Fingerprint) { menuOpen = false; onEnrollBiometrics() }
            SheetAction("Lock Vault", Icons.Default.Lock) { menuOpen = false; onLock() }
        }
        if (openCollection == null) {
            androidx.compose.material3.TabRow(selectedTabIndex = if (contentMode == VaultContentMode.MEDIA) 0 else 1) {
                androidx.compose.material3.Tab(selected = contentMode == VaultContentMode.MEDIA, onClick = { contentMode = VaultContentMode.MEDIA }, text = { Text("Media") })
                androidx.compose.material3.Tab(selected = contentMode == VaultContentMode.COLLECTIONS, onClick = { contentMode = VaultContentMode.COLLECTIONS; selectedItemIds = emptySet(); selectionMode = false }, text = { Text("Collections") })
            }
        }
        if (openCollection == null && contentMode == VaultContentMode.MEDIA && vaultItems.isNotEmpty()) {
            VaultBrowseControls(query, { query = it; selectedItemIds = emptySet(); selectionMode = false }, kind, { kind = it; selectedItemIds = emptySet(); selectionMode = false })
        }
        when {
            !loaded -> GalleryLoadingState("Loading Vault…")
            openCollection != null -> CollectionMediaGrid(
                openCollectionItems,
                onLoadPreview,
                onOpenViewer,
                cropRevision,
                Modifier.weight(1f),
                thumbnailMinSize = density.minSize,
                onRemove = { ids -> onRemoveItemsFromCollection(checkNotNull(openCollection).id, ids) { result ->
                    status = result
                    onLoadCollectionItems(checkNotNull(openCollection).id) { openCollectionItems = it }
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
                            TextButton(enabled = selectedItemIds.isNotEmpty(), onClick = { collectionPickerFor = selectedItemIds.toList() }) { Text("Add to collection") }
                            TextButton(onClick = { selectedItemIds = emptySet(); selectionMode = false }) { Text("Cancel") }
                            }
                        }
                    }
                }
                VaultMediaGrid(displayedItems, onLoadPreview, cropRevision, selectedItemIds, onSelection = { id -> selectionMode = true; selectedItemIds = selectedItemIds.toggle(id) }, onOpenViewer = onOpenViewer, modifier = Modifier.weight(1f), selectionMode = selectionMode, thumbnailMinSize = density.minSize)
            }
            else -> GalleryCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your vault is empty", style = MaterialTheme.typography.headlineSmall)
                Text("Photos and videos you add here are stored privately and encrypted on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, modifier = Modifier.fillMaxWidth()) { Text("Add media") }
                Text("To move existing device media, select it from Gallery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (creatingCollection) NewCollectionDialog(
        onCreate = { name -> onCreateCollection(name) { result -> result.onSuccess { collections = collections + it; creatingCollection = false }.onFailure { status = "Unable to create collection." } } },
        onDismiss = { creatingCollection = false },
    )
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

private enum class VaultContentMode { MEDIA, COLLECTIONS }

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
        GalleryCard(modifier = Modifier.fillMaxWidth()) {
            Text("No media in this collection", style = MaterialTheme.typography.titleMedium)
            Text("Add encrypted Vault media to this collection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        GalleryCard(modifier = Modifier.fillMaxWidth()) {
            Text("No collections yet", style = MaterialTheme.typography.titleMedium)
            Text("Keep related photos and videos together.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreate) { Text("New collection") }
        }
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
                Column {
                    if (cover != null) VaultMediaTile(cover, onLoadPreview, cropRevision, onClick = { onOpen(collection) }, onLongClick = { onManage(collection) })
                    else Card(onClick = { onOpen(collection) }, shape = RoundedCornerShape(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Icon(Icons.Default.PhotoAlbum, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(collection.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(members?.let { "${it.size} items" } ?: "Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onManage(collection) }) { Icon(Icons.Default.MoreVert, "Manage ${collection.name}") }
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
        dismissButton = { Row { if (!confirmingDelete) TextButton(onClick = onSetFavourite) { Text("Set favourite") }; if (!confirmingDelete) TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
    )
}

@Composable
private fun CollectionPickerDialog(collections: List<VaultCollection>, onChoose: (VaultCollection) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to collection") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        CompactVaultHeader(collection?.name ?: "Favourite", "${collectionItems.size} items", onAdd = { addingItems = true }, onBack = null)
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
private fun VaultMediaTile(
    item: VaultItem,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    cropRevision: Int,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    var preview by remember(item.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(item.id, cropRevision) {
        if (VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize)) {
            onLoadPreview(item) { result -> preview = result.getOrNull()?.asImageBitmap() }
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
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } ?: Text(
                if (item.mimeType.startsWith("video/")) "Video" else "Photo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ProtectedMediaTilePolicy.showVideoIndicator(item.mimeType)) {
                Surface(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                ) {
                    Text("▶", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
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
