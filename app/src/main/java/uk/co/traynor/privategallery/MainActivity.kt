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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import uk.co.traynor.privategallery.core.ui.VaultGridPolicy
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
    private lateinit var keys: PinVaultKeyStore
    private lateinit var biometrics: BiometricVaultKeyStore
    private lateinit var recoveryKeys: RecoveryVaultKeyStore
    private lateinit var appSettings: android.content.SharedPreferences
    private val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    private var route by mutableStateOf(Route.LOCK)
    private var biometricEnabled by mutableStateOf(false)
    private var biometricAvailable by mutableStateOf(false)
    private var autoLockTimeout by mutableStateOf(AutoLockTimeout.IMMEDIATELY)
    private var appTheme by mutableStateOf(AppTheme.SYSTEM)
    private var allowScreenshots by mutableStateOf(false)
    private var updateStatus by mutableStateOf("Not checked")
    private var updateLastChecked by mutableStateOf("Never")
    private var availableUpdate by mutableStateOf<ReleaseMetadata?>(null)
    private var browserSearchEngine by mutableStateOf(BrowserSearchEngine.GOOGLE)
    private var clearBrowserDataOnLock by mutableStateOf(false)
    private var browserAutoConnectVpn by mutableStateOf(false)
    private var browserRequireVpn by mutableStateOf(false)
    private var browserVpnState by mutableStateOf(VpnConnectionState.UNCONFIGURED)
    private var vpnProfileStatus by mutableStateOf("")
    private var vpnProfiles by mutableStateOf<List<VpnProfileSummary>>(emptyList())
    private var browserWebView: WebView? = null
    private var browserBookmarks by mutableStateOf<List<BrowserBookmark>>(emptyList())
    private var browserFullscreenExit: (() -> Unit)? = null
    private var mediaAccessAvailable by mutableStateOf(false)
    private var sessionKey: ByteArray? = null
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
                val cipher = result.cryptoObject?.cipher ?: return
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
                        null -> Unit
                    }
                }
                biometricPurpose = null
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancellation and lockout deliberately leave the PIN screen available without a retry loop.
                biometricPurpose = null
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
        keys = PinVaultKeyStore(this)
        biometrics = BiometricVaultKeyStore(this)
        recoveryKeys = RecoveryVaultKeyStore(this)
        biometricAvailable = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        appSettings = getSharedPreferences("private-gallery-settings", MODE_PRIVATE)
        autoLockTimeout = AutoLockPreference.decode(appSettings.getString("auto-lock-timeout", null))
        appTheme = ThemePreference.decode(appSettings.getString("app-theme", null))
        allowScreenshots = !ScreenPrivacyPreference.secureWindow(appSettings.getString("allow-screenshots", null))
        updateLastChecked = appSettings.getString("update-last-checked", null) ?: "Never"
        browserSearchEngine = BrowserSearchEngine.decode(appSettings.getString("browser-search-engine", null))
        clearBrowserDataOnLock = appSettings.getBoolean("browser-clear-data-on-lock", false)
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
        route = if (keys.isConfigured) Route.LOCK else Route.SETUP
        setContent {
            PrivateGalleryTheme(appTheme) {
                PrivateGalleryApp(route, ::createPin, ::unlock, ::changePin, ::recoverWithOfflineKey, ::finishRecoveryKeySetup, { route = Route.RECOVER }, { route = Route.LOCK }, ::lock, ::importSelected, ::moveSelected, ::loadItems, ::loadCollections, ::loadFavouriteCollection, ::createCollection, ::addItemsToCollection, ::removeItemsFromCollection, ::renameCollection, ::deleteCollection, ::loadCollectionItems, ::readForViewing, ::loadPreview, ::loadImageEdit, ::applyImageCrop, ::undoImageCrop, ::resetImageCrop, ::restore, ::delete, biometricEnabled, ::unlockWithBiometrics, ::enrollBiometrics, ::finishSetup, autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, availableUpdate != null, mediaAccessAvailable, ::requestDeviceMediaAccess, ::deviceMediaPages, ::loadDeviceThumbnail, ::openSettings, { openNonBrowser(Route.GALLERY); mediaAccessAvailable = hasDeviceMediaAccess() }, { openNonBrowser(Route.VAULT) }, { openNonBrowser(Route.FAVOURITE) }, ::openBrowser, ::applyAutoLockTimeout, ::applyTheme, ::applyAllowScreenshots, ::applyBrowserSearchEngine, ::applyClearBrowserDataOnLock, ::clearBrowserData, browserSearchEngine, clearBrowserDataOnLock, browserWebView, { view -> browserWebView = view }, { exit -> browserFullscreenExit = exit }, ::checkForUpdates, ::downloadUpdate, recoveryKeys.isConfigured, pendingRecoveryKey?.concatToString(), ::importBrowserSource, browserRequireVpn, browserVpnState == VpnConnectionState.CONNECTED, ::importWireGuardProfile, vpnProfileStatus, browserVpnState, browserAutoConnectVpn, ::applyBrowserAutoConnectVpn, ::applyBrowserRequireVpn, ::setFavouriteCollection, vpnProfiles, ::selectVpnProfile, ::removeVpnProfile, browserBookmarks, ::addBrowserBookmark, ::removeBrowserBookmark)
            }
        }
        window.decorView.post(::triggerAutomaticBiometricPromptIfNeeded)
    }

    override fun onStop() {
        super.onStop()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_background")) }
        session.onAppBackgrounded(System.currentTimeMillis())
        if (!session.isUnlocked) lock() else scheduleOwnedVpnDisconnect()
    }

    override fun onStart() {
        super.onStart()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_foreground")) }
        val wasUnlocked = session.isUnlocked
        session.onForegrounded(System.currentTimeMillis())
        if (!session.isUnlocked && keys.isConfigured) {
            if (wasUnlocked) automaticBiometricPromptAttempted = false
            route = Route.LOCK
            triggerAutomaticBiometricPromptIfNeeded()
        } else if (route == Route.BROWSER) {
            // Returning to the still-visible Browser is a Browser re-entry: retain a valid owned
            // tunnel during the grace period and cancel the pending teardown.
            browserVpnDisconnectJob?.cancel()
            browserVpnState = browserVpnController.enterBrowser(
                browserAutoConnectVpn,
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
        browserFullscreenExit?.invoke()
        browserFullscreenExit = null
        stopBrowserLoadingFor(BrowserWebViewLifecycleEvent.LOCKED)
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "lock")) }
        browserVpnDisconnectJob?.cancel()
        browserVpnController.onLock()
        browserVpnState = browserVpnController.state
        if (BrowserNavigationPolicy.clearDataOnLock(clearBrowserDataOnLock)) clearBrowserData()
        session.lock()
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
            key.fill(0)
            runOnUiThread { browserBookmarks = bookmarks }
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
                browserVpnState = browserVpnController.enterBrowser(browserAutoConnectVpn, browserRequireVpn, System.currentTimeMillis())
                if (browserRequireVpn && browserAutoConnectVpn && profile != null && browserVpnState != VpnConnectionState.CONNECTED) requestBrowserVpnPermissionOrConnect()
            }
        }
    }

    private fun requestBrowserVpnPermissionOrConnect() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            // Android owns this confirmation. Private Gallery never stops another app's VPN itself.
            vpnPermissionLauncher.launch(permissionIntent)
        } else connectBrowserVpn()
    }

    private fun importWireGuardProfile() {
        vpnProfileDocumentLauncher.launch(arrayOf("application/octet-stream", "text/plain", "application/wireguard"))
    }

    private fun connectBrowserVpn() {
        browserVpnState = browserVpnController.enterBrowser(browserAutoConnectVpn, browserRequireVpn, System.currentTimeMillis())
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

    private fun applyBrowserAutoConnectVpn(enabled: Boolean) {
        browserAutoConnectVpn = enabled
        appSettings.edit().putBoolean("browser-vpn-auto-connect", enabled).apply()
    }

    private fun applyBrowserRequireVpn(enabled: Boolean) {
        browserRequireVpn = enabled
        appSettings.edit().putBoolean("browser-vpn-required", enabled).apply()
    }

    /** Clears only WebView-managed browsing state; it never touches encrypted Vault storage. */
    private fun clearBrowserData() {
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
        DeviceGalleryRepository(applicationContext).pagedItems()

    private fun loadDeviceThumbnail(item: DeviceMediaItem, onLoaded: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val thumbnail = DeviceGalleryRepository(applicationContext).thumbnail(item, 360)?.asImageBitmap()
            runOnUiThread { onLoaded(thumbnail) }
        }
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
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = AndroidVaultRepository(applicationContext, key)
                var copied = 0
                var duplicates = 0
                uris.forEach { uri ->
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
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                VaultImportCoordinator(AndroidVaultRepository(applicationContext, key)).acquire(
                    source.copy(isCancelled = { !session.isUnlocked || (browserRequireVpn && browserVpnState != VpnConnectionState.CONNECTED) }),
                )
            }
            key.fill(0)
            runOnUiThread {
                onComplete(
                    when (result.getOrNull()) {
                        is ImportResult.Imported -> "Saved to Vault."
                        is ImportResult.Duplicate -> "Already in Vault."
                        null -> if (result.exceptionOrNull() is java.io.IOException) "Vault save cancelled." else "Unable to save to Vault."
                    },
                )
            }
        }
    }

    private fun loadItems(onLoaded: (List<VaultItem>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val items = runCatching { AndroidVaultRepository(applicationContext, key).items() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(items) }
        }
    }

    private fun loadCollections(onLoaded: (List<VaultCollection>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val collections = runCatching { AndroidVaultRepository(applicationContext, key).collections() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(collections) }
        }
    }

    private fun loadFavouriteCollection(onLoaded: (VaultCollection?) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val collection = runCatching { AndroidVaultRepository(applicationContext, key).migrateLegacyFavourite() }.getOrNull()
            key.fill(0)
            runOnUiThread { onLoaded(collection) }
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
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val items = runCatching { AndroidVaultRepository(applicationContext, key).itemsInCollection(collectionId) }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(items) }
        }
    }

    private fun moveSelected(uris: List<android.net.Uri>, onComplete: (String) -> Unit) {
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
                    (repository.import(uri) as? ImportResult.Imported)?.item
                }
                imported.forEach(repository::markDeletePending)
                val sources = imported.mapNotNull { it.sourceUri?.let(android.net.Uri::parse) }
                if (sources.isEmpty()) {
                    runOnUiThread { onComplete("Saved to Vault. The selected media was already protected or unavailable.") }
                } else {
                    pendingSourceDeletion = imported
                    pendingSourceDeletionCompletion = onComplete
                    val request = MediaStore.createDeleteRequest(contentResolver, sources)
                    runOnUiThread {
                        sourceDeletionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
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

    private fun readForViewing(item: VaultItem, onComplete: (Result<ByteArray>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).readForViewing(item) }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    /** Generates a bounded preview in memory only after the vault has been unlocked. */
    private fun loadPreview(item: VaultItem, onComplete: (Result<Bitmap>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize)) { "Preview is not available for this item" }
                val repository = AndroidVaultRepository(applicationContext, key)
                val bytes = repository.readForViewing(item)
                try {
                    if (item.mimeType.startsWith("video/")) {
                        MediaMetadataRetriever().let { retriever ->
                            try {
                                retriever.setDataSource(ByteArrayMediaDataSource(bytes))
                                checkNotNull(retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)) {
                                    "Unable to decode protected video preview"
                                }
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
                        val decoded = checkNotNull(
                            BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.size,
                                BitmapFactory.Options().apply { inSampleSize = sample },
                            ),
                        ) { "Unable to decode protected preview" }
                        VaultImageEdits.crop(VaultImageEdits.visuallyOrient(bytes, decoded), repository.imageEdit(item.id)?.crop)
                    }
                } finally {
                    bytes.fill(0)
                }
            }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    private fun loadImageEdit(item: VaultItem, onComplete: (ImageEditState?) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val edit = runCatching { AndroidVaultRepository(applicationContext, key).imageEdit(item.id) }.getOrNull()
            key.fill(0)
            runOnUiThread { onComplete(edit) }
        }
    }

    private fun applyImageCrop(item: VaultItem, crop: NormalizedCrop, onComplete: (Result<ImageEditState>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).applyImageCrop(item.id, crop) }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    private fun undoImageCrop(item: VaultItem, onComplete: (Result<ImageEditState?>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).undoImageCrop(item.id) }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    private fun resetImageCrop(item: VaultItem, onComplete: (Result<Unit>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).resetImageCrop(item.id) }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    private fun delete(item: VaultItem, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AndroidVaultRepository(applicationContext, key).deleteFromVault(item)
                runOnUiThread { onComplete("Removed from Vault.") }
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

private enum class Route { SETUP, RECOVERY_KEY_SETUP, BIOMETRIC_SETUP, LOCK, RECOVER, GALLERY, VAULT, FAVOURITE, BROWSER, SETTINGS }
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
) {
    var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }
    var cropRevision by remember { mutableStateOf(0) }
    var favouriteLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route) {
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
    ProtectedAppShell(route, favouriteLabel, onNavigate = { destination ->
        when (destination) {
            AppNavigationDestination.GALLERY -> onOpenGallery()
            AppNavigationDestination.VAULT -> onOpenVault()
            AppNavigationDestination.FAVOURITE -> onOpenFavourite()
            AppNavigationDestination.BROWSER -> onOpenBrowser()
            AppNavigationDestination.SETTINGS -> onOpenSettings()
        }
    }) { contentPadding ->
        when (route) {
            Route.GALLERY -> GalleryHome(deviceMediaAccessAvailable, onRequestDeviceMediaAccess, onDeviceMediaPages, onLoadDeviceThumbnail, onImport, onMove, onOpenViewer = { entries, index -> viewerRequest = ViewerRequest.Gallery(entries, index) }, modifier = Modifier.padding(contentPadding))
            Route.VAULT -> VaultHome(onLock, onImport, onLoadItems, onLoadCollections, onCreateCollection, onAddItemsToCollection, onRemoveItemsFromCollection, onRenameCollection, onDeleteCollection, onLoadCollectionItems, onLoadPreview, cropRevision, biometricEnabled, onEnrollBiometrics, onSetFavouriteCollection, onFavouriteStateChanged = { onLoadFavouriteCollection { favouriteLabel = it?.name } }, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index) }, modifier = Modifier.padding(contentPadding))
            Route.FAVOURITE -> FavouriteHome(onLoadFavouriteCollection, onLoadItems, onLoadCollectionItems, onAddItemsToCollection, onRemoveItemsFromCollection, onLoadPreview, cropRevision, onOpenVault, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index) }, modifier = Modifier.padding(contentPadding))
            Route.BROWSER -> BrowserHome(
                existingWebView = existingBrowserWebView,
                searchEngine = browserSearchEngine,
                onWebViewReady = onBrowserWebViewReady,
                onFullscreenExitChanged = onBrowserFullscreenExitChanged,
                onClearBrowsingData = onClearBrowserData,
                onOpenBrowserSettings = onOpenSettings,
                onSaveToVault = onSaveBrowserSource,
                requireVpnForBrowsing = requireVpnForBrowsing,
                vpnConnected = vpnConnected,
                bookmarks = bookmarks,
                onAddBookmark = onAddBookmark,
                onRemoveBookmark = onRemoveBookmark,
                modifier = Modifier.padding(contentPadding),
            )
            Route.SETTINGS -> SettingsHome(autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, updateAvailable, biometricEnabled, recoveryKeyConfigured, browserSearchEngine, clearBrowserDataOnLock, onAutoLockTimeoutChanged, onThemeChanged, onAllowScreenshotsChanged, onBrowserSearchEngineChanged, onClearBrowserDataOnLockChanged, onClearBrowserData, onCheckForUpdates, onDownloadUpdate, onChangePin, onLock, browserAutoConnectVpn, requireVpnForBrowsing, onBrowserAutoConnectVpnChanged, onBrowserRequireVpnChanged, onImportWireGuardProfile, vpnProfileStatus, vpnConnectionState, vpnProfiles, onSelectVpnProfile, onRemoveVpnProfile, modifier = Modifier.padding(contentPadding))
            else -> Unit
        }
    }
    viewerRequest?.let { request ->
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
                onLoadImageEdit = { id, loaded -> request.items[id]?.let { onLoadImageEdit(it, loaded) } ?: loaded(null) },
                onApplyImageCrop = { id, crop, completed -> request.items[id]?.let { onApplyImageCrop(it, crop, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onUndoImageCrop = { id, completed -> request.items[id]?.let { onUndoImageCrop(it, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onResetImageCrop = { id, completed -> request.items[id]?.let { onResetImageCrop(it, completed) } ?: completed(Result.failure(IllegalStateException("Missing Vault item"))) },
                onCropChanged = { cropRevision++ },
                onRestore = { entry -> request.items[entry.id]?.let { onRestore(it, false) {} } },
                onRestoreAndRemove = { entry -> request.items[entry.id]?.let { item -> onRestore(item, true) { viewerRequest = null } } },
                onDeleteFromVault = { entry -> request.items[entry.id]?.let { item -> onDelete(item) { viewerRequest = null } } },
            )
        }
    }
    }
    }
}
}

@Composable
private fun ProtectedAppShell(
    selected: Route,
    favouriteLabel: String?,
    onNavigate: (AppNavigationDestination) -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                AppNavigationPolicy.destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.matches(selected),
                        onClick = { onNavigate(destination) },
                        icon = { Text(destination.icon) },
                        label = { Text(AppNavigationPolicy.labelFor(destination, favouriteLabel)) },
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
private fun GalleryHome(
    deviceMediaAccessAvailable: Boolean,
    onRequestDeviceMediaAccess: () -> Unit,
    onDeviceMediaPages: () -> Flow<PagingData<DeviceMediaItem>>,
    onLoadDeviceThumbnail: (DeviceMediaItem, (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onOpenViewer: (List<ViewerMediaEntry>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf("Browse your device, then select media to protect.") }
    var selected by remember { mutableStateOf<Map<Long, android.net.Uri>>(emptyMap()) }
    var galleryOverflowExpanded by remember { mutableStateOf(false) }
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
            .padding(horizontal = GalleryTokens.PageHorizontal, vertical = GalleryTokens.PageVertical)
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(GalleryTokens.ContentGap),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GalleryPageTitle("On this device", "Gallery")
            Box {
                IconButton(onClick = { galleryOverflowExpanded = true }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                DropdownMenu(expanded = galleryOverflowExpanded, onDismissRequest = { galleryOverflowExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Add with Photo Picker") },
                        onClick = {
                            galleryOverflowExpanded = false
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    )
                }
            }
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
            if (selected.isNotEmpty()) {
                Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    status = "Encrypting selected media…"
                                    onImport(selected.values.toList()) { result ->
                                        status = result
                                        selected = emptyMap()
                                        deviceMedia.refresh()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Copy to Vault") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    status = "Encrypting and verifying…"
                                    onMove(selected.values.toList()) { result ->
                                        status = result
                                        selected = emptyMap()
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
                deviceMedia.loadState.refresh is androidx.paging.LoadState.Loading -> GalleryCard { Text("Loading device media…") }
                deviceMedia.loadState.refresh is androidx.paging.LoadState.Error -> GalleryCard {
                    Text("Unable to read device media. Check the Gallery permission.", color = MaterialTheme.colorScheme.error)
                    androidx.compose.material3.OutlinedButton(onClick = onRequestDeviceMediaAccess) { Text("Review Gallery access") }
                }
                deviceMedia.itemCount == 0 -> GalleryCard {
                    Text("No accessible media", style = MaterialTheme.typography.titleMedium)
                    Text("Photos and videos you allow Private Gallery to access will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(deviceMedia.itemCount, key = { index -> deviceMedia[index]?.id ?: "media-placeholder-$index" }) { index ->
                        deviceMedia[index]?.let { item ->
                            DeviceMediaTile(
                                item = item,
                                selected = item.id in selected,
                                onLoadThumbnail = onLoadDeviceThumbnail,
                                onClick = {
                                if (selected.isEmpty()) {
                                    val entries = deviceMedia.itemSnapshotList.items.map { loaded ->
                                        ViewerMediaEntry(loaded.id.toString(), loaded.mimeType, loaded.uri)
                                    }
                                    val index = entries.indexOfFirst { it.id == item.id.toString() }
                                    if (index >= 0) onOpenViewer(entries, index)
                                }
                                else selected = selected.toggle(item)
                                },
                                onLongClick = { selected = selected.toggle(item) },
                            )
                        }
                    }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(status, modifier = Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
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
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = GalleryTokens.MediaShape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(142.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            thumbnail?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Text(if (item.kind == DeviceMediaKind.VIDEO) "Video" else "Photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.kind == DeviceMediaKind.VIDEO) {
                Surface(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), shape = RoundedCornerShape(8.dp)) {
                    Text("▶ ${formatDuration(item.durationMillis)}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (selected) Text("✓", modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
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
private fun SettingsHome(
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
) {
    var changingPin by remember { mutableStateOf(false) }
    var confirmScreenshots by remember { mutableStateOf(false) }
    var browserDataCleared by remember { mutableStateOf(false) }
    var showingLicences by remember { mutableStateOf(false) }
    val settingsModifier = if (SettingsLayoutPolicy.isVerticallyScrollable) {
        modifier.verticalScroll(rememberScrollState())
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
        GalleryPageTitle("Private Gallery", "Settings")
        SettingsSection(SettingsSections.SECURITY) {
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
            AutoLockTimeout.entries.forEach { timeout ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { onAutoLockTimeoutChanged(timeout) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (timeout == autoLockTimeout) "✓ " + timeout.label else timeout.label)
                }
            }
        }
        SettingsSection(SettingsSections.PRIVACY) {
            Text("Secure-screen protection", style = MaterialTheme.typography.titleMedium)
            Text("Protected screens are excluded from screenshots and Recents previews where Android supports it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Backup protection", style = MaterialTheme.typography.titleMedium)
            Text("Private Gallery data is excluded from Android backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection(SettingsSections.DEBUG) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Allow screenshots", style = MaterialTheme.typography.titleMedium)
                    Text("Allows screenshots while using Private Gallery. Private content may be captured while enabled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(
                    checked = allowScreenshots,
                    onCheckedChange = { enabled -> if (enabled) confirmScreenshots = true else onAllowScreenshotsChanged(false) },
                )
            }
        }
        SettingsSection(SettingsSections.APPEARANCE) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Text("Choose light, dark, or follow your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    androidx.compose.material3.OutlinedButton(onClick = { onThemeChanged(theme) }) {
                        Text(if (theme == appTheme) "✓ ${theme.label}" else theme.label)
                    }
                }
            }
        }
        SettingsSection(SettingsSections.BROWSER) {
            Text("WireGuard VPN", style = MaterialTheme.typography.titleMedium)
            Text("Private Gallery only enables Browser networking after its own WireGuard tunnel reports connected. It does not claim a device-wide kill switch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Blocks new Browser navigation and downloads until the tunnel is confirmed connected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Search engine", style = MaterialTheme.typography.titleMedium)
            Text("Searches are sent only to the selected provider.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrowserSearchEngine.entries.forEach { engine ->
                    androidx.compose.material3.OutlinedButton(onClick = { onBrowserSearchEngineChanged(engine) }) {
                        Text(if (engine == browserSearchEngine) "✓ ${engine.label}" else engine.label)
                    }
                }
            }
            Text("Browsing data", style = MaterialTheme.typography.titleMedium)
            Text("Clears browser history, cache, cookies and site storage. Vault media is not affected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { onClearBrowserData(); browserDataCleared = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear browsing data") }
            if (browserDataCleared) Text("Browsing data cleared.", color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Clear data on lock", style = MaterialTheme.typography.titleMedium)
                    Text("Clears browser history, cache, cookies and site storage when Private Gallery locks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = clearBrowserDataOnLock, onCheckedChange = onClearBrowserDataOnLockChanged)
            }
        }
        SettingsSection(SettingsSections.UPDATES) {
            Text("Installed", style = MaterialTheme.typography.titleMedium)
            Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Build ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Latest", style = MaterialTheme.typography.titleMedium)
            Text(updateStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last checked", style = MaterialTheme.typography.titleMedium)
            Text(updateLastChecked, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = onCheckForUpdates, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
            if (updateAvailable) Button(onClick = onDownloadUpdate, modifier = Modifier.fillMaxWidth()) { Text("Download update") }
        }
        SettingsSection(SettingsSections.ABOUT) {
            Text("Private Gallery ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("Media stays in encrypted private app storage until you restore it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { showingLicences = true }, modifier = Modifier.fillMaxWidth()) { Text("Third-party licences") }
        }
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
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
        AlertDialog(
            onDismissRequest = { showingLicences = false },
            title = { Text("Third-party licences") },
            text = { Text("Complete notices for the dependencies packaged in this APK are stored as the app asset third_party_notices.txt. Private Gallery itself is proprietary and all rights reserved. This APK contains no OpenVPN, OpenSSL or LZO code.") },
            confirmButton = { TextButton(onClick = { showingLicences = false }) { Text("Close") } },
        )
    }
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

@Composable
private fun VaultHome(
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
    var status by remember { mutableStateOf("Select media in Gallery to move it into the encrypted Vault.") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var collections by remember { mutableStateOf<List<VaultCollection>>(emptyList()) }
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
                onLoadItems { updated -> vaultItems = updated; loaded = true }
            }
        }
    }
    fun refresh() {
        onLoadItems { items -> vaultItems = items; loaded = true }
        onLoadCollections { collections = it }
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(openCollection?.id) {
        openCollection?.let { collection -> onLoadCollectionItems(collection.id) { openCollectionItems = it } }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GalleryTokens.PageHorizontal, vertical = GalleryTokens.PageVertical)
            .widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(GalleryTokens.ContentGap),
    ) {
        CompactVaultHeader(
            title = openCollection?.name ?: "Vault",
            itemSummary = if (openCollection == null) VaultSummary.from(vaultItems).let { "${it.photos} photos · ${it.videos} videos" } else "${openCollectionItems.size} items",
            onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onBack = openCollection?.let { { openCollection = null } },
        )
        if (!biometricEnabled) {
            Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Biometric unlock is not enabled", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onEnrollBiometrics) { Text("Enable") }
                }
            }
        }
        if (openCollection == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(selected = contentMode == VaultContentMode.MEDIA, onClick = { contentMode = VaultContentMode.MEDIA }, label = { Text("Media") })
                androidx.compose.material3.FilterChip(selected = contentMode == VaultContentMode.COLLECTIONS, onClick = { contentMode = VaultContentMode.COLLECTIONS; selectedItemIds = emptySet() }, label = { Text("Collections") })
            }
        }
        when {
            !loaded -> GalleryCard(modifier = Modifier.fillMaxWidth()) { Text("Loading Vault…") }
            openCollection != null -> CollectionMediaGrid(
                openCollectionItems,
                onLoadPreview,
                onOpenViewer,
                cropRevision,
                Modifier.weight(1f),
                onRemove = { ids -> onRemoveItemsFromCollection(checkNotNull(openCollection).id, ids) { result ->
                    status = result
                    onLoadCollectionItems(checkNotNull(openCollection).id) { openCollectionItems = it }
                } },
            )
            contentMode == VaultContentMode.COLLECTIONS -> CollectionsGrid(
                collections = collections,
                items = vaultItems,
                onCreate = { creatingCollection = true },
                onOpen = { openCollection = it },
                onManage = { managingCollection = it },
            )
            vaultItems.isNotEmpty() -> {
                if (selectedItemIds.isNotEmpty()) {
                    Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${selectedItemIds.size} selected", modifier = Modifier.weight(1f))
                            TextButton(onClick = { collectionPickerFor = selectedItemIds.toList() }) { Text("Add to collection") }
                            TextButton(onClick = { selectedItemIds = emptySet() }) { Text("Cancel") }
                        }
                    }
                }
                VaultMediaGrid(vaultItems, onLoadPreview, cropRevision, selectedItemIds, onSelection = { id -> selectedItemIds = selectedItemIds.toggle(id) }, onOpenViewer = onOpenViewer, modifier = Modifier.weight(1f))
            }
            else -> GalleryCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("PG", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("Your vault is empty", style = MaterialTheme.typography.headlineSmall)
                Text("Photos and videos you add here are stored privately and encrypted on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, modifier = Modifier.fillMaxWidth()) { Text("Add media") }
                Text("To move existing device media, select it from Gallery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GalleryTokens.RowShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(status, modifier = Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
    }
    if (creatingCollection) NewCollectionDialog(
        onCreate = { name -> onCreateCollection(name) { result -> result.onSuccess { collections = collections + it; creatingCollection = false }.onFailure { status = "Unable to create collection." } } },
        onDismiss = { creatingCollection = false },
    )
    collectionPickerFor?.let { ids -> CollectionPickerDialog(collections, onChoose = { collection ->
        onAddItemsToCollection(collection.id, ids) { status = it; selectedItemIds = emptySet(); collectionPickerFor = null; refresh() }
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("PRIVATE GALLERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("+ Add") }
        }
        Text(itemSummary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(VaultGridPolicy.columnsFor(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { item ->
            VaultMediaTile(item, onLoadPreview, cropRevision, selected = item.id in selectedIds, onClick = {
                if (selectedIds.isEmpty()) {
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
            VaultMediaGrid(items, onLoadPreview, cropRevision, selectedIds, { selectedIds = selectedIds.toggle(it) }, onOpenViewer, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CollectionsGrid(
    collections: List<VaultCollection>,
    items: List<VaultItem>,
    onCreate: () -> Unit,
    onOpen: (VaultCollection) -> Unit,
    onManage: (VaultCollection) -> Unit,
) {
    if (collections.isEmpty()) {
        GalleryCard(modifier = Modifier.fillMaxWidth()) {
            Text("No collections yet", style = MaterialTheme.typography.titleMedium)
            Text("Collections organise Vault media without creating another copy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("New collection") }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onCreate) { Text("New collection") } }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(collections, key = { it.id }) { collection ->
                    Card(onClick = { onOpen(collection) }, shape = GalleryTokens.MediaShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(collection.name, style = MaterialTheme.typography.titleMedium)
                            Text("Open collection", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { onManage(collection) }) { Text("Manage") }
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
private fun FavouriteHome(
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
    LaunchedEffect(Unit) { onLoadFavouriteCollection { collection = it; onLoadItems { allItems = it } } }
    LaunchedEffect(collection?.id) { collection?.let { onLoadCollectionItems(it.id) { collectionItems = it } } }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = GalleryTokens.PageHorizontal, vertical = GalleryTokens.PageVertical).widthIn(max = 840.dp),
        verticalArrangement = Arrangement.spacedBy(GalleryTokens.ContentGap),
    ) {
        CompactVaultHeader(collection?.name ?: "Favourite", "${collectionItems.size} items", onAdd = { addingItems = true }, onBack = null)
        if (collection == null) GalleryCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No favourite collection selected.")
                Text("Choose one of your Collections from Vault. Fresh installs do not create a default collection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = GalleryTokens.MediaShape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(176.dp),
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
                if (item.mimeType.startsWith("video/")) "▶" else "",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ProtectedMediaTilePolicy.showVideoIndicator(item.mimeType)) {
                Surface(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                ) {
                    Text("▶", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (selected) Surface(
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) { Text("✓", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

private const val MAX_PICKED_MEDIA = 50
