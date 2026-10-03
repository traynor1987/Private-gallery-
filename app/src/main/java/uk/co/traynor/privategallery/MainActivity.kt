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
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlin.coroutines.CoroutineContext
import uk.co.traynor.privategallery.core.security.PrimaryOperation
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.PrimarySessionBinding
import uk.co.traynor.privategallery.core.security.SessionEpoch
import uk.co.traynor.privategallery.core.security.RecoverySetupState
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

/** The posted closure keeps the originating epoch even after its key lease ends. */
internal fun publishProtected(operation: PrimaryOperation, post: (() -> Unit) -> Unit,
    onDiscard: () -> Unit = {}, action: () -> Unit) {
    post {
        var admitted = false
        try { operation.publish { admitted = true; action() } }
        catch (failure: IllegalStateException) { if (admitted) throw failure else onDiscard() }
    }
}

/** Lazy start prevents work from escaping revocation before the Job has been registered. */
internal fun launchOwned(operation: PrimaryOperation, scope: CoroutineScope, context: CoroutineContext,
    block: suspend CoroutineScope.() -> Unit): Job {
    try {
        val job = operation.createOwnedJob { attach ->
            scope.launch(context, start = CoroutineStart.LAZY) {
                operation.checkValid(); block()
            }.also(attach)
        }
        // A cancelled lifecycle can complete a lazy Job immediately. Install the key-lease
        // teardown only after its pre-funded original ownership registration has returned.
        job.invokeOnCompletion { operation.close() }
        job.start()
        return job
    } catch (failure: Throwable) {
        operation.close()
        throw failure
    }
}

/** Authentication-only restore can promote several stages under one immutable attempt. */
internal class AuthenticationAttemptAuthority {
    private val gate = Any()
    private var generation = 0L
    private var admitted = false
    val currentGeneration: Long get() = synchronized(gate) { generation }
    fun begin(): Long = synchronized(gate) { admitted = true; ++generation }
    fun revoke() = synchronized(gate) { admitted = false; ++generation }
    fun isCurrent(attempt: Long): Boolean = synchronized(gate) { admitted && generation == attempt }
    fun <T> commit(attempt: Long, action: () -> T): T = synchronized(gate) {
        check(admitted && generation == attempt) { "Authentication attempt unavailable" }
        action()
    }
}

class MainActivity : FragmentActivity() {
    private fun launchProtected(operation: PrimaryOperation, block: suspend CoroutineScope.() -> Unit): Job =
        launchOwned(operation, lifecycleScope, Dispatchers.IO, block)
    private fun publishUi(operation: PrimaryOperation, onDiscard: () -> Unit = {}, action: () -> Unit) =
        publishProtected(operation, { runOnUiThread(it) }, onDiscard, action)
    private fun bind0(owner: PrimarySessionBinding?, action: () -> Unit): () -> Unit = { if (owner?.isCurrent == true) runCatching { owner.commit(action) } }
    private fun <A> bind1(owner: PrimarySessionBinding?, action: (A) -> Unit): (A) -> Unit = { a -> bind0(owner) { action(a) }.invoke() }
    private fun <A, B> bind2(owner: PrimarySessionBinding?, action: (A, B) -> Unit): (A, B) -> Unit = { a, b -> bind0(owner) { action(a, b) }.invoke() }
    private fun <A, B, C> bind3(owner: PrimarySessionBinding?, action: (A, B, C) -> Unit): (A, B, C) -> Unit = { a, b, c -> bind0(owner) { action(a, b, c) }.invoke() }
    private fun <A, B, C, D> bind4(owner: PrimarySessionBinding?, action: (A, B, C, D) -> Unit): (A, B, C, D) -> Unit = { a, b, c, d -> bind0(owner) { action(a, b, c, d) }.invoke() }
    private fun <A, R> bindResult1(owner: PrimarySessionBinding?, fallback: R, action: (A) -> R): (A) -> R = { a ->
        if (owner?.isCurrent == true) runCatching { owner.commit { action(a) } }.getOrDefault(fallback) else fallback
    }
    private fun <A, B, R> bindResult2(owner: PrimarySessionBinding?, fallback: R, action: (A, B) -> R): (A, B) -> R = { a, b ->
        if (owner?.isCurrent == true) runCatching { owner.commit { action(a, b) } }.getOrDefault(fallback) else fallback
    }
    private fun <A, B, C, R> bindResult3(owner: PrimarySessionBinding?, fallback: R, action: (A, B, C) -> R): (A, B, C) -> R = { a, b, c ->
        if (owner?.isCurrent == true) runCatching { owner.commit { action(a, b, c) } }.getOrDefault(fallback) else fallback
    }
    private var primaryEpoch by mutableStateOf<SessionEpoch?>(null)
    private val authenticationAttempts get() = retained.authenticationAttempts
    private val authenticationGeneration get() = authenticationAttempts.currentGeneration
    private var pendingDeleteOperation: PrimaryOperation? = null
    private var pendingVpnPermissionOperation: PrimaryOperation? = null
    private var pendingVpnProfileOperation: PrimaryOperation? = null
    private var pendingBackupExportOperation: PrimaryOperation? = null
    private var backupExportOperation: PrimaryOperation? = null
    private var pendingBackupRestoreAttempt: Long? = null
    private var backupRestoreAttempt: Long? = null
    private var sensitiveOperation: PrimaryOperation? = null


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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.BROWSER_UPLOAD_EGRESS)) ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        val key = operation.key
        val generation = browserUploadGeneration
        launchProtected(operation) {
            val files = mutableListOf<File>()
            val result = runCatching {
                require(items.size in 1..4) { "Select up to four items" }
                require(items.sumOf { it.plaintextSize } <= 256L * 1024 * 1024) { "Upload selection exceeds size limit" }
                val directory = File(cacheDir, "browser-upload").apply { mkdirs() }
                val repository = AndroidVaultRepository(applicationContext, operation)
                items.map { item ->
                    if (hideContent || generation != browserUploadGeneration || !operation.isCurrent) throw java.io.IOException("Upload cancelled")
                    val folder = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                    val file = File(folder, uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.safeName(item.mimeType))
                    files += file
                    repository.prepareBrowserUpload(item, file) { hideContent || generation != browserUploadGeneration || !operation.isCurrent }
                    FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                }
            }

            publishUi(operation, onDiscard = { files.forEach { it.delete(); it.parentFile?.delete() } }) {
                if (hideContent || generation != browserUploadGeneration || !operation.isCurrent || result.isFailure) {
                    files.forEach { it.delete(); it.parentFile?.delete() }
                    onComplete(Result.failure(result.exceptionOrNull() ?: java.io.IOException("Upload cancelled")))
                } else {
                    operation.ownForSession(AutoCloseable { files.forEach { it.delete(); it.parentFile?.delete() } })
                    browserUploadFiles.addAll(files)
                    onComplete(result)
                    val cleanup = lifecycleScope.launch {
                        delay(5 * 60 * 1000L)
                        if (operation.isCurrent && generation == browserUploadGeneration) clearBrowserUploadCopies()
                    }
                    // The closed key lease remains usable for epoch-only cleanup ownership.
                    if (operation.isCurrent) browserOwnerOperation?.own(cleanup) else cleanup.cancel()
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
            if (evicted) previewKeys.entries.removeAll { it.value == key }
        }
    }
    private val previewJobs = mutableSetOf<Job>()
    private val previewKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val previewEpochs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun invalidatePreview(id: String) {
        previewEpochs[id] = (previewEpochs[id] ?: 0L) + 1L
        previewKeys.remove(id)?.let { previewMemory.remove(it) }
    }
    private val deviceGallery by lazy { DeviceGalleryRepository(applicationContext) }
    private lateinit var primarySlots: uk.co.traynor.privategallery.core.security.PrimaryKeySlots
    private lateinit var keys: PinVaultKeyStore
    private lateinit var biometrics: BiometricVaultKeyStore
    private lateinit var recoveryKeys: RecoveryVaultKeyStore
    private lateinit var appSettings: android.content.SharedPreferences
    private val retained by lazy { androidx.lifecycle.ViewModelProvider(this)[ProtectedSessionState::class.java] }
    private val session get() = retained.session
    private var secondaryController: uk.co.traynor.privategallery.core.domain.SecondaryController? = null
    private var secondaryWorker: java.util.concurrent.ExecutorService? = null
    private var secondaryObserver: Job? = null
    private var secondaryTimer: Job? = null
    private var secondaryPrompt: BiometricPrompt? = null
    // Once protected pixels have been drawn, retain protection for this window lifetime.
    // Compose disposal and Android backgrounding do not establish that old pixels are gone.
    private var protectedSpaceWindow = false
    private var secondaryState by mutableStateOf(uk.co.traynor.privategallery.core.domain.SecondaryUiState())
    private var route by mutableStateOf(Route.LOCK)
    private var biometricEnabled by mutableStateOf(false)
    private var biometricAvailable by mutableStateOf(false)
    private var autoLockTimeout by mutableStateOf(AutoLockTimeout.IMMEDIATELY)
    private var appTheme by mutableStateOf(AppTheme.SYSTEM)
    private var allowScreenshots by mutableStateOf(false)
    private var hideContent by mutableStateOf(false)
    @Volatile private var hideGate = false
    private var browserPresentationGeneration = 0L
    private var sensitivePrompt: BiometricPrompt? = null
    private var sensitiveGeneration = 0L
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
    private var ownedBrowserSession: BrowserV2Session? = null
    private var browserOwnerOperation: PrimaryOperation? = null
    private val browserV2Session: BrowserV2Session
        get() = ownedBrowserSession ?: createBrowserSession(null).also { ownedBrowserSession = it }

    private fun createBrowserSession(owner: PrimaryOperation?): BrowserV2Session = BrowserV2Session(
        appContext = this@MainActivity,
        vpnGate = BrowserVpnGate { owner?.isCurrent == true && browserVpnController.browserNetworkingAllowed(browserRequireVpn) },
        contentBlocker = uk.co.traynor.privategallery.core.browser.v2.BrowserContentBlocker { enabled ->
            if (owner != null && owner.isCurrent) owner.publish { appSettings.edit().putBoolean("browser-content-blocking", enabled).apply() }
        },
        listener = NoopBrowserV2Listener,
        primaryOwner = owner,
        onMetadataChanged = { snapshot -> if (owner != null && owner.isCurrent) saveBrowserSessionMetadata(snapshot, owner) },
    )

    private fun replaceBrowserSession() {
        ownedBrowserSession?.destroyAll()
        browserOwnerOperation?.close()
        browserOwnerOperation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.BROWSER_UPLOAD_EGRESS))
        val owner = browserOwnerOperation
        val replacement = createBrowserSession(owner)
        if (::appSettings.isInitialized) replacement.contentBlocker.enabled = appSettings.getBoolean("browser-content-blocking", true)
        ownedBrowserSession = replacement
        if (owner != null) {
            // Revocation can originate on an IO deadline check. WebView/UI cleanup must finish
            // on Main, and its registered Job keeps cleanupComplete false until that finishes.
            val cleanupJob = lifecycleScope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
                try { kotlinx.coroutines.awaitCancellation() }
                finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                    replacement.destroyAll()
                    browserBookmarks = emptyList(); vpnProfiles = emptyList()
                    previewMemory.snapshot().values.forEach { if (it.isMutable && !it.isRecycled) it.eraseColor(android.graphics.Color.TRANSPARENT) }
                    previewMemory.evictAll(); previewKeys.clear(); deviceGallery.clearThumbnailCache()
                    pendingRecoveryKey?.fill('\u0000'); pendingRecoveryKey = null
                    clearBrowserUploadCopies()
                    if (!owner.isCurrent) {
                        session.lock(); retained.route = Route.LOCK
                        primaryEpoch = null; route = Route.LOCK
                    }
                } }
            }
            owner.own(cleanupJob)
        }
        wireGuardEngine.onStateChanged = { state ->
            if (owner != null) publishUi(owner) {
                onVpnStateObserved(state)
                browserWebView?.let { view ->
                    BrowserCallbackBindings.recordAcceptance(view, "VPN_STATE", mapOf("state" to state.name.lowercase()))
                }
                if (browserRequireVpn && state != VpnConnectionState.CONNECTED) stopBrowserLoadingFor(BrowserWebViewLifecycleEvent.VPN_NOT_CONNECTED)
            }
        }
    }

    private var browserBookmarks by mutableStateOf<List<BrowserBookmark>>(emptyList())
    private var browserFullscreenExit: (() -> Unit)? = null
    private var mediaAccessAvailable by mutableStateOf(false)
    private var backupExportUri by mutableStateOf<Uri?>(null)
    private var backupRestoreUri by mutableStateOf<Uri?>(null)
    private var backupStatus by mutableStateOf("")
    private var vaultState by mutableStateOf(uk.co.traynor.privategallery.core.security.PrimaryVaultState.LOCKED)
    private fun installAuthenticatedKey(value: ByteArray?) {
        if (value == null) retained.authority.revoke() else retained.authority.open(value)
        primaryEpoch = retained.authority.bindingOrNull()?.epoch
        replaceBrowserSession()
    }
    /** Held only while the user is being shown the newly-created offline secret. */
    private var pendingRecoveryKey: CharArray? = null
    private var pendingSourceDeletion: List<VaultItem> = emptyList()
    /** Completion is retained only for the platform deletion confirmation round-trip. */
    private var pendingSourceDeletionCompletion: ((String) -> Unit)? = null
    private val wireGuardEngine by lazy { WireGuardVpnEngine(OfficialWireGuardBackend(applicationContext)) }
    private val browserVpnController by lazy { BrowserVpnController(wireGuardEngine) }
    private var browserVpnDisconnectJob: Job? = null
    private var automaticBiometricPromptAttempted = false
    private val generationJobs = mutableSetOf<Job>()
    private val biometricAttempts = uk.co.traynor.privategallery.core.security.BiometricUnlockAttemptState()
    private var activeBiometricPrompt: BiometricPrompt? = null
    private fun newBiometricPrompt(purpose: BiometricPurpose): BiometricPrompt {
        activeBiometricPrompt?.cancelAuthentication()
        val owner = if (purpose == BiometricPurpose.ENROLL) retained.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)) else null
        check(purpose != BiometricPurpose.ENROLL || owner != null) { "Primary unavailable" }
        val attempt = biometricAttempts.begin()
        val authenticationAttempt = authenticationAttempts.begin()
        return BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!biometricAttempts.isCurrent(attempt) || !authenticationAttempts.isCurrent(authenticationAttempt)) return
                biometricAttempts.cancel(attempt)
                activeBiometricPrompt = null
                val cipher = result.cryptoObject?.cipher ?: return
                runCatching { authenticationAttempts.commit(authenticationAttempt) {
                    when (purpose) {
                        BiometricPurpose.UNLOCK -> {
                            val unwrapped = biometrics.unwrapAuthenticated(cipher)
                            installAuthenticatedKey(unwrapped)
                            session.unlock()
                            reconcileAfterUnlock()
                            route = if (prepareRecoveryKeyIfNeeded()) Route.RECOVERY_KEY_SETUP else Route.VAULT
                        }
                        BiometricPurpose.ENROLL -> {
                            val operation = checkNotNull(owner)
                            primarySlots.enrollBiometric(operation, cipher); biometricEnabled = true
                            operation.close()
                        }
                    }
                } }.onFailure { owner?.close(); android.util.Log.w("PGAuth", "BIOMETRIC_OPERATION_UNAVAILABLE") }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!biometricAttempts.isCurrent(attempt) || !authenticationAttempts.isCurrent(authenticationAttempt)) return
                biometricAttempts.cancel(attempt)
                activeBiometricPrompt = null
                owner?.close()
            }
        }).also { prompt -> activeBiometricPrompt = prompt; owner?.own(AutoCloseable { prompt.cancelAuthentication() }) }
    }
    private val sourceDeletionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val operation = pendingDeleteOperation ?: return@registerForActivityResult
        pendingDeleteOperation = null
        if (!operation.isCurrent) { operation.close(); return@registerForActivityResult }
        val pending = pendingSourceDeletion
        pendingSourceDeletion = emptyList()
        val completion = pendingSourceDeletionCompletion
        pendingSourceDeletionCompletion = null
        launchProtected(operation) {
            try {
                val repository = AndroidVaultRepository(applicationContext, operation)
                // RESULT_OK alone is not treated as source truth. Query each source after the
                // platform request so Gallery only refreshes removed media after deletion is
                // observable in MediaStore.
                val deleted = pending.associateWith { item ->
                    result.resultCode == RESULT_OK && item.sourceUri?.let(::sourceNoLongerExists) == true
                }
                runCatching { pending.forEach { repository.finishSourceDeletionRequest(it, deleted[it] == true) } }
                val removed = deleted.values.count { it }
                publishUi(operation) {
                    completion?.invoke(
                        if (removed == pending.size && removed > 0) "Moved to Vault. Source removed from Gallery."
                        else "Saved to Vault. Some Gallery sources remain available."
                    )
                }
            } finally {

            }
        }
    }
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaAccessAvailable = hasDeviceMediaAccess()
    }
    private val backupExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val operation = pendingBackupExportOperation ?: return@registerForActivityResult
        pendingBackupExportOperation = null
        if (uri == null || !operation.isCurrent) { operation.close(); return@registerForActivityResult }
        operation.publish { backupExportOperation?.close(); backupExportOperation = operation; backupExportUri = uri }
    }
    private val backupRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val attempt = pendingBackupRestoreAttempt ?: return@registerForActivityResult
        pendingBackupRestoreAttempt = null
        if (authenticationAttempts.isCurrent(attempt) && route in setOf(Route.SETUP, Route.LOCK)) { backupRestoreAttempt = attempt; backupRestoreUri = uri }
    }
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val operation = pendingVpnPermissionOperation ?: return@registerForActivityResult
        pendingVpnPermissionOperation = null
        try { operation.publish {
            browserVpnPermissionRequired = result.resultCode != RESULT_OK
            if (result.resultCode == RESULT_OK) connectBrowserVpn(operation) else browserVpnState = VpnConnectionState.FAILED
        } } finally { operation.close() }
    }
    private val vpnProfileDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val operation = pendingVpnProfileOperation ?: return@registerForActivityResult
        pendingVpnProfileOperation = null
        if (uri == null || !operation.isCurrent) { operation.close(); return@registerForActivityResult }
        val key = operation.key
        launchProtected(operation) {
            val result = runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Unable to read selected profile")
                require(text.length <= 256 * 1024) { "Profile is too large" }
                val repository = VpnProfileRepository(File(filesDir, "vpn-profiles"), key, operation::commit)
                repository.import(uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "WireGuard", text).also { imported ->
                    if (imported is uk.co.traynor.privategallery.core.vpn.VpnProfileImportResult.Accepted) repository.select(imported.profile.id)
                }
            }

            publishUi(operation) {
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
        File(cacheDir, "browser-video").deleteRecursively() // interrupted plaintext exports only
        ContextCompat.registerReceiver(this, screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        uk.co.traynor.privategallery.core.editor.AiProviderRegistry.initialize(applicationContext)
        if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) {
            BrowserV2FatalCrashCapture.install(applicationContext)
        }
        primarySlots = uk.co.traynor.privategallery.core.security.PrimaryKeySlots(this)
        keys = primarySlots.pin
        biometrics = BiometricVaultKeyStore(this)
        recoveryKeys = primarySlots.recovery
        biometricAvailable = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        appSettings = getSharedPreferences("private-gallery-settings", MODE_PRIVATE)
        autoLockTimeout = AutoLockPreference.decode(appSettings.getString("auto-lock-timeout", null))
        appTheme = ThemePreference.decode(appSettings.getString("app-theme", null))
        allowScreenshots = !ScreenPrivacyPreference.secureWindow(appSettings.getString("allow-screenshots", null))
        hideContent = uk.co.traynor.privategallery.core.security.HideContentPolicy.decode(
            appSettings.contains("hide-content"), runCatching { appSettings.getString("hide-content", null) }.getOrNull())
        hideGate = hideContent
        updateLastChecked = appSettings.getString("update-last-checked", null) ?: "Never"
        browserSearchEngine = BrowserSearchEngine.decode(appSettings.getString("browser-search-engine", null))
        clearBrowserDataOnLock = appSettings.getBoolean("browser-clear-data-on-lock", false)
        browserSaveHistory = appSettings.getBoolean("browser-save-history", false)
        browserV2Session.contentBlocker.enabled = appSettings.getBoolean("browser-content-blocking", true)
        browserAutoConnectVpn = appSettings.getBoolean("browser-vpn-auto-connect", false)
        browserRequireVpn = appSettings.getBoolean("browser-vpn-required", false)
        wireGuardEngine.onStateChanged = { _ -> }
        OwnedVpnTunnelRegistry.attach(wireGuardEngine)
        mediaAccessAvailable = hasDeviceMediaAccess()
        applyScreenPrivacy()
        session.setTimeout(autoLockTimeout)
        biometricEnabled = biometrics.isEnabled
        // A saved one-shot flag can survive process death while the old biometric prompt cannot.
        // Every new locked Activity gets one prompt; cancellation still leaves the PIN screen.
        automaticBiometricPromptAttempted = false
        primaryEpoch = retained.authority.bindingOrNull()?.epoch
        route = restoredPrimaryRoute(session.isUnlocked, primaryEpoch != null, retained.route, keys.isConfigured, pendingRecoveryKey != null)
        // The one-time secret belongs to the destroyed Activity, never to saved state.
        // Authenticate again to restart pending confirmation without replacing the VDEK.
        if (route == Route.LOCK && session.isUnlocked) lock()
        else if (primaryEpoch != null) replaceBrowserSession()
        retained.route = route
        setContent {
            val uiOwner = remember(primaryEpoch) { retained.authority.bindingOrNull() }
            DisposableEffect(uiOwner) { onDispose { uiOwner?.close() } }
            PrivateGalleryTheme(appTheme) {
                if (secondaryState.route != uk.co.traynor.privategallery.core.domain.SecondaryRoute.CLOSED) {
                    secondaryController?.let { controller ->
                        uk.co.traynor.privategallery.ui.PrivateSpaceFlow(secondaryState, uk.co.traynor.privategallery.ui.PrivateSpaceActions(controller))
                    }
                } else PrivateGalleryApp(route, ::createPin, ::unlock, bindResult2(uiOwner, Result.failure(IllegalStateException("Primary unavailable")), ::changePin), ::recoverWithOfflineKey, bindResult1(uiOwner, Result.failure(IllegalStateException("Primary unavailable")), ::finishRecoveryKeySetup), { route = Route.RECOVER }, { route = Route.LOCK }, bind0(uiOwner, ::lock), bind2(uiOwner, ::importSelected), bind2(uiOwner, ::moveSelected), bind1(uiOwner, ::loadItems), bind1(uiOwner, ::loadCollections), bind1(uiOwner, ::loadFavouriteCollection), bind2(uiOwner, ::createCollection), bind3(uiOwner, ::addItemsToCollection), bind3(uiOwner, ::removeItemsFromCollection), bind3(uiOwner, ::renameCollection), bind2(uiOwner, ::deleteCollection), bind2(uiOwner, ::loadCollectionItems), bind2(uiOwner, ::readForViewing), bind2(uiOwner, ::loadPreview), bind2(uiOwner, ::loadImageEdit), bind3(uiOwner, ::applyImageCrop), bind2(uiOwner, ::undoImageCrop), bind2(uiOwner, ::resetImageCrop), bind3(uiOwner, ::restore), bind2(uiOwner, ::delete), biometricEnabled, ::unlockWithBiometrics, bind0(uiOwner, ::enrollBiometrics), bind0(uiOwner, ::finishSetup), autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, availableUpdate != null, mediaAccessAvailable, ::requestDeviceMediaAccess, bindResult1(uiOwner, flowOf(PagingData.empty<DeviceMediaItem>()), ::deviceMediaPages), { if (uiOwner?.isCurrent == true) deviceGallery.albums() else emptyList() }, bind2(uiOwner, ::loadDeviceThumbnail), bind0(uiOwner, ::openSettings), bind0(uiOwner) { openNonBrowser(Route.GALLERY); mediaAccessAvailable = hasDeviceMediaAccess() }, bind0(uiOwner) { openNonBrowser(Route.VAULT) }, bind0(uiOwner) { openNonBrowser(Route.FAVOURITE) }, bind0(uiOwner, ::openBrowser), bind1(uiOwner, ::applyAutoLockTimeout), bind1(uiOwner, ::applyTheme), bind1(uiOwner, ::applyAllowScreenshots), bind1(uiOwner, ::applyBrowserSearchEngine), bind1(uiOwner, ::applyClearBrowserDataOnLock), bind0(uiOwner, ::clearBrowserData), browserSearchEngine, clearBrowserDataOnLock, browserSaveHistory, bind1(uiOwner, ::applyBrowserSaveHistory), browserWebView, bind1(uiOwner) { view -> browserWebView = view }, bind1(uiOwner) { exit -> browserFullscreenExit = exit }, ::checkForUpdates, ::downloadUpdate, recoveryKeys.isConfigured, pendingRecoveryKey?.concatToString(), bind2(uiOwner, ::importBrowserSource), browserRequireVpn, browserVpnState == VpnConnectionState.CONNECTED, bind0(uiOwner, ::importWireGuardProfile), vpnProfileStatus, browserVpnState, browserAutoConnectVpn, bind1(uiOwner, ::applyBrowserAutoConnectVpn), bind1(uiOwner, ::applyBrowserRequireVpn), bind2(uiOwner, ::setFavouriteCollection), vpnProfiles, bind1(uiOwner, ::selectVpnProfile), bind1(uiOwner, ::removeVpnProfile), browserBookmarks, bind3(uiOwner, ::addBrowserBookmark), bind1(uiOwner, ::removeBrowserBookmark), browserV2Session, bind1(uiOwner, ::loadBrowserHistory), bind1(uiOwner, ::clearBrowserHistory), bind2(uiOwner, ::recordBrowserHistory), browserVpnPreparing, browserVpnPermissionRequired, bind0(uiOwner, ::requestBrowserVpnPermissionOrConnect), { item, bytes, cancelled, completed -> if (uiOwner?.isCurrent == true) uiOwner.commit { saveEditedCopy(item, bytes, { cancelled() || !uiOwner.isCurrent }, completed) } else bytes.fill(0) }, bind3(uiOwner, ::readForEditing), { item, bytes, cancelled, completed -> if (uiOwner?.isCurrent == true) uiOwner.commit { saveEditedCopy(item, bytes, { cancelled() || !uiOwner.isCurrent }, completed, true) } else bytes.fill(0) }, onReadVideoForViewing = bind4(uiOwner, ::readVideoForViewing), onOpenVideoSession = bind3(uiOwner, ::openVideoSession), onSaveAiCopy = { item, bytes, provenance, cancelled, completed -> if (uiOwner?.isCurrent == true) uiOwner.commit { saveEditedCopy(item, bytes, { cancelled() || !uiOwner.isCurrent }, completed, aiProvenance = provenance) } else bytes.fill(0) }, onPrepareBrowserUpload = bind2(uiOwner, ::prepareBrowserUpload), onClearBrowserUpload = bind0(uiOwner, ::clearBrowserUploadCopies), hideContent = hideContent, onHideContentChanged = bind1(uiOwner, ::applyHideContent), onDiscovered = bind1(uiOwner, ::openPrivateSpace), onAuthenticateSensitive = bind1(uiOwner, ::authenticateSensitive), onCancelSensitiveAuthentication = bind0(uiOwner, ::cancelSensitiveAuthentication), onVerifySecretPin = bindResult1(uiOwner, false, ::verifySecretPin), onGenerateImage = bindResult3(uiOwner, {}, ::generateVaultImage), onChooseBackupExport = bind0(uiOwner, ::chooseBackupExport), backupExportUri = backupExportUri, onCancelBackupExport = bind0(uiOwner, ::cancelBackupExport), onExportBackup = bind1(uiOwner, ::exportEncryptedBackup), backupStatus = backupStatus, onChooseBackupRestore = ::chooseBackupRestore, backupRestoreUri = backupRestoreUri, onCancelBackupRestore = { backupRestoreUri = null }, onRestoreBackup = ::restoreEncryptedBackup, onLoadRecentlyDeleted = bind1(uiOwner, ::loadRecentlyDeleted), onMoveItemsToRecentlyDeleted = bind2(uiOwner, ::moveItemsToRecentlyDeleted), onChangeRecentlyDeleted = bind3(uiOwner, ::changeRecentlyDeleted), onRestoreSelectedVaultCopies = bind2(uiOwner, ::restoreSelectedVaultCopies), onBeginProtectedWork = { scopes -> runCatching { uiOwner?.operation(scopes) }.getOrNull() }, canResumeBackupRestore = !keys.hasEnvelopeMaterial, vaultState = vaultState)
            }
        }
        window.decorView.post(::triggerAutomaticBiometricPromptIfNeeded)
    }

    override fun onStop() {
        super.onStop()
        cancelSensitiveAuthentication()
        secondaryPrompt?.cancelAuthentication(); secondaryPrompt = null
        val secondaryTimeout = secondaryController?.state?.value?.autoLock?.timeoutMillis ?: 0L
        secondaryController?.onBackgrounded()
        secondaryState = uk.co.traynor.privategallery.core.domain.SecondaryUiState()
        secondaryTimer?.cancel()
        secondaryController?.let { controller ->
            val timeout = secondaryTimeout
            if (timeout > 0) secondaryTimer = lifecycleScope.launch { delay(timeout); controller.checkBackgroundExpiry() }
        }
        clearBrowserUploadCopies()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_background")) }
        retained.route = route
        val interactive = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        session.onActivityStopped(android.os.SystemClock.elapsedRealtime(), isChangingConfigurations, interactive)
        if (isChangingConfigurations && interactive) return
        retained.onBackgrounded(if (!interactive) 0 else autoLockTimeout.milliseconds)
        if (!session.isUnlocked || retained.authority.bindingOrNull() == null) lock() else scheduleOwnedVpnDisconnect()
    }

    override fun onStart() {
        super.onStart()
        browserWebView?.let { BrowserCallbackBindings.recordAcceptance(it, "WEBVIEW_LIFECYCLE", mapOf("reason" to "app_foreground")) }
        secondaryTimer?.cancel(); secondaryTimer = null
        secondaryController?.onForegrounded()
        val wasUnlocked = session.isUnlocked
        retained.onForegrounded()
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

    override fun onDestroy() {
        secondaryPrompt?.cancelAuthentication(); secondaryPrompt = null
        secondaryController?.close(); secondaryController = null
        secondaryWorker?.shutdown(); secondaryWorker = null
        secondaryObserver?.cancel(); secondaryObserver = null
        secondaryTimer?.cancel(); secondaryTimer = null
        authenticationAttempts.revoke()
        biometricAttempts.lock()
        activeBiometricPrompt?.cancelAuthentication(); activeBiometricPrompt = null
        cancelSensitiveAuthentication()
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
        browserOwnerOperation?.close(); browserOwnerOperation = null
        if (!isChangingConfigurations) { installAuthenticatedKey(null); session.lock() }
        super.onDestroy()
    }

    private fun createPin(pin: CharArray): Result<Unit> = runCatching {
        check(route == Route.SETUP && !keys.isConfigured) { "Vault setup unavailable" }
        authenticationAttempts.begin()
        installAuthenticatedKey(keys.create(pin))
        val recoveryReady = prepareRecoveryKeyIfNeeded()
        session.unlock()
        reconcileAfterUnlock()
        route = if (recoveryReady) Route.RECOVERY_KEY_SETUP else if (biometricAvailable) Route.BIOMETRIC_SETUP else Route.VAULT
    }

    private fun exportEncryptedBackup(recoveryKey: CharArray) {
        val uri = backupExportUri ?: run { recoveryKey.fill('\u0000'); return }
        backupExportUri = null
        val operation = backupExportOperation ?: run { recoveryKey.fill('\u0000'); backupStatus = "Unlock Vault before exporting."; return }
        backupExportOperation = null
        val key = operation.key
        backupStatus = "Verifying and exporting encrypted Vault…"
        launchProtected(operation) {
            val exportJob = coroutineContext[Job]
            val result = runCatching {
                check(operation.isCurrent && !hideContent)
                val envelope = primarySlots.recoveryEnvelope(operation)
                val output = checkNotNull(contentResolver.openOutputStream(uri, "wt")) { "Cannot open backup destination" }
                output.use {
                    AndroidVaultRepository(applicationContext, operation).exportBackup(recoveryKey, envelope, it, progress = { done, total ->
                        check(operation.isCurrent && !hideContent) { "Vault locked during export" }
                        publishUi(operation) { backupStatus = "Exporting encrypted Vault · $done/$total files" }
                    }, cancelled = { !operation.isCurrent || hideContent || exportJob?.isActive != true })
                }
            }
            recoveryKey.fill('\u0000')
            publishUi(operation) {
                backupStatus = if (result.isSuccess) "Encrypted Vault backup saved. Keep your offline recovery key separately."
                    else "Backup did not finish. Delete the incomplete document and try again."
            }
        }
    }

    private fun restoreEncryptedBackup(recoveryKey: CharArray, pin: CharArray, completed: (Boolean) -> Unit) {
        val uri = backupRestoreUri ?: run { recoveryKey.fill('\u0000'); pin.fill('\u0000'); completed(false); return }
        val attempt = backupRestoreAttempt ?: run { recoveryKey.fill('\u0000'); pin.fill('\u0000'); completed(false); return }
        val originRoute = route
        if (!authenticationAttempts.isCurrent(attempt) || originRoute !in setOf(Route.SETUP, Route.LOCK) || keys.hasEnvelopeMaterial) { recoveryKey.fill('\u0000'); pin.fill('\u0000'); completed(false); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                check(authenticationAttempts.isCurrent(attempt) && route == originRoute && !keys.hasEnvelopeMaterial)
                checkNotNull(contentResolver.openInputStream(uri)) { "Cannot open backup" }.use { input ->
                    AndroidVaultRepository.restoreBackup(applicationContext, input, recoveryKey, pin, keys, recoveryKeys, commit = { action ->
                        authenticationAttempts.commit(attempt) {
                            check(route == originRoute) { "Restore cancelled" }
                            action()
                        }
                    })
                }
            }
            recoveryKey.fill('\u0000'); pin.fill('\u0000')
            runOnUiThread {
                if (!authenticationAttempts.isCurrent(attempt) || route != originRoute) { result.getOrNull()?.fill(0); return@runOnUiThread }
                result.onSuccess { restored ->
                    installAuthenticatedKey(restored)
                    session.unlock()
                    route = Route.VAULT
                    backupRestoreUri = null
                    completed(true)
                }.onFailure { completed(false) }
            }
        }
    }

    private fun unlock(pin: CharArray): Result<Unit> = runCatching {
        check(route == Route.LOCK) { "Unlock attempt unavailable" }
        authenticationAttempts.begin()
        installAuthenticatedKey(keys.unlock(pin))
        session.unlock()
        reconcileAfterUnlock()
        route = if (prepareRecoveryKeyIfNeeded()) Route.RECOVERY_KEY_SETUP else Route.VAULT
    }

    private fun recoverWithOfflineKey(recoveryKey: CharArray, newPin: CharArray): Result<Unit> = runCatching {
        check(route == Route.RECOVER) { "Recovery attempt unavailable" }
        val attempt = authenticationAttempts.begin()
        require(newPin.size >= 6) { "PIN must be at least six digits" }
        val recovered = primarySlots.recover(recoveryKey, newPin) { action -> authenticationAttempts.commit(attempt, action) }
        try {
            biometricEnabled = false
            installAuthenticatedKey(recovered.copyOf())
            session.unlock()
            reconcileAfterUnlock()
            route = Route.VAULT
        } finally {
            recovered.fill(0)
        }
    }

    private fun changePin(currentPin: CharArray, newPin: CharArray): Result<Unit> = runCatching {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)) ?: error("Primary unavailable")
        operation.use {
            require(newPin.size >= 6) { "PIN must be at least six digits" }
            primarySlots.changePin(operation, currentPin, newPin)
        }
    }

    private fun lock() {
        secondaryController?.onPrimaryLocked()
        secondaryPrompt?.cancelAuthentication(); secondaryPrompt = null
        secondaryState = uk.co.traynor.privategallery.core.domain.SecondaryUiState()
        vaultState = uk.co.traynor.privategallery.core.security.PrimaryVaultState.LOCKED
        retained.authority.revoke()
        primaryEpoch = null
        authenticationAttempts.revoke()
        listOf(pendingDeleteOperation, pendingVpnPermissionOperation, pendingVpnProfileOperation,
            pendingBackupExportOperation, backupExportOperation).forEach { it?.close() }
        pendingDeleteOperation = null; pendingVpnPermissionOperation = null; pendingVpnProfileOperation = null
        pendingBackupExportOperation = null; backupExportOperation = null; pendingBackupRestoreAttempt = null; backupRestoreAttempt = null
        pendingSourceDeletion = emptyList(); pendingSourceDeletionCompletion = null
        backupExportUri = null; backupRestoreUri = null
        browserBookmarks = emptyList(); vpnProfiles = emptyList()

        browserPresentationGeneration++
        generationJobs.toList().forEach { it.cancel() }
        biometricAttempts.lock()
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        cancelSensitiveAuthentication()
        clearBrowserUploadCopies()
        File(cacheDir, "browser-video").deleteRecursively()
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
        deviceGallery.clearThumbnailCache()
        installAuthenticatedKey(null)
        pendingRecoveryKey?.fill('\u0000')
        pendingRecoveryKey = null
        automaticBiometricPromptAttempted = false
        if (::keys.isInitialized && keys.isConfigured) route = Route.LOCK
    }

    /** Cleans interrupted ciphertext and never attempts to delete a source item. */
    private fun reconcileAfterUnlock() {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            runCatching { AndroidVaultRepository(applicationContext, operation).reconcile() }

            publishUi(operation) {
                if (!hideContent) restoreBrowserPresentation()
            }
        }
    }

    private fun saveBrowserSessionMetadata(snapshot: uk.co.traynor.privategallery.core.browser.v2.BrowserSessionSnapshot, owner: PrimaryOperation) {
        val operation = runCatching { owner.fork(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) }.getOrNull() ?: return
        val key = operation.key
        launchProtected(operation) {
            try {
                uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserSessionStore(File(filesDir, "browser-session"), key, operation::commit).save(snapshot)
            } catch (failure: Exception) {
                // Restore metadata is best-effort. Keep the in-memory Browser session alive and
                // surface only a privacy-safe failure category in acceptance diagnostics.
                publishUi(operation) {
                    browserV2Session.recordAcceptanceUiEvent(
                        "BROWSER_SESSION_SAVE_FAILED",
                        mapOf("type" to failure.javaClass.simpleName.take(80)),
                    )
                }
            }
            finally { }
        }
    }

    private fun addBrowserBookmark(title: String, url: String, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        val key = operation.key
        launchProtected(operation) {
            val result = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key, operation::commit).add(title, url) }
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key, operation::commit).list() }.getOrDefault(emptyList())

            publishUi(operation) { browserBookmarks = bookmarks; onComplete(if (result.isSuccess) "Bookmark saved." else "This page cannot be bookmarked.") }
        }
    }

    private fun removeBrowserBookmark(id: String) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        val key = operation.key
        launchProtected(operation) {
            runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key, operation::commit).remove(id) }
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key, operation::commit).list() }.getOrDefault(emptyList())

            publishUi(operation) { browserBookmarks = bookmarks }
        }
    }

    private fun loadBrowserHistory(onLoaded: (List<uk.co.traynor.privategallery.core.browser.v2.BrowserHistoryEntry>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        val key = operation.key
        launchProtected(operation) {
            val values = runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(filesDir, "browser-history"), key, operation::commit).list() }.getOrDefault(emptyList())

            publishUi(operation) { onLoaded(if (hideContent) emptyList() else values) }
        }
    }

    private fun clearBrowserHistory(onComplete: () -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        val key = operation.key
        launchProtected(operation) {
            runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(filesDir, "browser-history"), key, operation::commit).clear() }

            publishUi(operation) { onComplete() }
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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        val key = operation.key
        launchProtected(operation) {
            val summaries = runCatching { VpnProfileRepository(File(filesDir, "vpn-profiles"), key, operation::commit).summaries() }.getOrDefault(emptyList())

            publishUi(operation) { vpnProfiles = summaries }
        }
    }

    private fun selectVpnProfile(profileId: String) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        val key = operation.key
        launchProtected(operation) {
            val profile = runCatching {
                VpnProfileRepository(File(filesDir, "vpn-profiles"), key, operation::commit).also { it.select(profileId) }.snapshot().profiles.single { it.id == profileId }
            }.getOrNull()

            publishUi(operation) {
                if (profile == null) {
                    vpnProfileStatus = "Unable to select WireGuard profile."
                    return@publishUi
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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        val key = operation.key
        launchProtected(operation) {
            val result = runCatching { VpnProfileRepository(File(filesDir, "vpn-profiles"), key, operation::commit).remove(profileId) }

            publishUi(operation) {
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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        val key = operation.key
        launchProtected(operation) {
            val profile = runCatching {
                VpnProfileRepository(File(filesDir, "vpn-profiles"), key, operation::commit).snapshot().let { snapshot ->
                    snapshot.profiles.singleOrNull { it.id == snapshot.activeProfileId }
                }
            }.getOrNull()

            publishUi(operation) {
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
            val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
            pendingVpnPermissionOperation?.close(); pendingVpnPermissionOperation = operation
            operation.commit { vpnPermissionLauncher.launch(permissionIntent) }
        } else retained.authority.operationOrNull(setOf(PrimaryScope.READ))?.use { connectBrowserVpn(it) }
    }

    private fun importWireGuardProfile() {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        pendingVpnProfileOperation?.close(); pendingVpnProfileOperation = operation
        operation.commit { vpnProfileDocumentLauncher.launch(arrayOf("application/octet-stream", "text/plain", "application/wireguard")) }
    }

    private fun connectBrowserVpn(operation: PrimaryOperation) {
        operation.checkValid()
        browserVpnPermissionRequired = false
        if (browserVpnController.state in setOf(VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING)) return
        // Explicit permission/retry action connects even when automatic connection is disabled.
        browserVpnState = browserVpnController.enterBrowser(true, browserRequireVpn, System.currentTimeMillis())
    }

    private fun finishSetup() {
        route = Route.VAULT
    }

    private fun finishRecoveryKeySetup(secret: CharArray): Result<Unit> = runCatching {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)) ?: error("Primary unavailable")
        operation.use { primarySlots.confirmRecovery(it, secret) }
        pendingRecoveryKey?.fill('\u0000'); pendingRecoveryKey = null
        route = if (biometricAvailable) Route.BIOMETRIC_SETUP else Route.VAULT
    }.also { secret.fill('\u0000') }

    /** Pending setup can restart after lock; an existing confirmed/legacy envelope stays intact. */
    private fun prepareRecoveryKeyIfNeeded(): Boolean {
        val state = recoveryKeys.setupState
        if (state == RecoverySetupState.CONFIRMED) return false
        if (state == RecoverySetupState.CORRUPT) { backupStatus = "Recovery configuration is unavailable."; return false }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)) ?: return false
        return operation.use {
            runCatching {
                pendingRecoveryKey?.fill('\u0000')
                pendingRecoveryKey = if (state == RecoverySetupState.PENDING_CONFIRMATION)
                    primarySlots.prepareRecovery(it, true) else primarySlots.prepareRecovery(it, false)
                true
            }.getOrElse { backupStatus = "Recovery setup is unavailable."; false }
        }
    }

    private fun chooseBackupExport() {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.BACKUP, PrimaryScope.EGRESS)) ?: return
        pendingBackupExportOperation?.close(); pendingBackupExportOperation = operation
        operation.commit { backupExportLauncher.launch("private-gallery-vault.pgvault") }
    }

    private fun cancelBackupExport() {
        pendingBackupExportOperation?.close(); pendingBackupExportOperation = null
        backupExportOperation?.close(); backupExportOperation = null; backupExportUri = null
    }

    private fun chooseBackupRestore() {
        if (route != Route.SETUP && route != Route.LOCK || keys.hasEnvelopeMaterial) return
        pendingBackupRestoreAttempt = authenticationAttempts.begin()
        backupRestoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        val key = operation.key
        launchProtected(operation) {
            try {
                val saved = uk.co.traynor.privategallery.core.browser.v2.recordBrowserHistoryVisit(
                    uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(filesDir, "browser-history"), key, operation::commit), title, url)
                if (!saved) android.util.Log.w("PrivateGalleryBrowser", "Browser history write failed (nonfatal)")
            } finally { }
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

    private fun deviceMediaPages(albumId: String?): Flow<PagingData<DeviceMediaItem>> =
        if (hideContent) kotlinx.coroutines.flow.flowOf(PagingData.empty()) else deviceGallery.pagedItems(albumId)

    private fun loadDeviceThumbnail(item: DeviceMediaItem, onLoaded: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit) {
        if (hideContent) { onLoaded(null); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        launchProtected(operation) {
            val thumbnail = deviceGallery.thumbnail(item, 360)
            thumbnail?.let { bitmap ->
                val reference = java.lang.ref.WeakReference(bitmap)
                operation.ownForSession(AutoCloseable {
                    reference.get()?.let { if (it.isMutable && !it.isRecycled) it.eraseColor(android.graphics.Color.TRANSPARENT) }
                })
            }
            publishUi(operation) { onLoaded(if (hideContent) null else thumbnail?.asImageBitmap()) }
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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        val key = operation.key
        val generation = ++browserPresentationGeneration
        val before = browserV2Session.metadataSnapshot()
        launchProtected(operation) {
            val bookmarks = runCatching { EncryptedBookmarkStore(File(filesDir, "browser-bookmarks"), key, operation::commit).list() }.getOrDefault(emptyList())
            val browserSession = runCatching { uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserSessionStore(File(filesDir, "browser-session"), key, operation::commit).load() }.getOrNull()

            publishUi(operation) {
                if (!hideContent && operation.isCurrent && generation == browserPresentationGeneration &&
                    browserV2Session.metadataSnapshot() == before) {
                    browserBookmarks = bookmarks
                    browserSession?.let(browserV2Session::restoreMetadata)
                }
            }
        }
    }

    /** Only About discovery may create this transient controller; ordinary startup never queries its store. */
    private fun openPrivateSpace(challenge: uk.co.traynor.privategallery.core.domain.DiscoveryChallenge) {
        if (retained.authority.bindingOrNull()?.use { it.isCurrent } != true) return
        protectedSpaceWindow = true
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val controller = secondaryController ?: run {
            val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
            secondaryWorker = worker
            uk.co.traynor.privategallery.core.domain.SecondaryController(
                uk.co.traynor.privategallery.core.domain.SecondaryStore(filesDir),
                uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority(android.os.SystemClock::elapsedRealtime),
                uk.co.traynor.privategallery.core.domain.SecondaryAuthPolicy(android.os.SystemClock::elapsedRealtime),
                uk.co.traynor.privategallery.core.domain.SecondaryBiometricSlot(), worker,
                { pin -> verifySecretPin(pin.copyOf()) },
            ).also { created ->
                secondaryController = created
                secondaryObserver = lifecycleScope.launch {
                    created.state.collect { state ->
                        // Privacy flags are applied before Compose observes the discovered route.
                        if (state.route != uk.co.traynor.privategallery.core.domain.SecondaryRoute.CLOSED) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        secondaryState = state
                        if (state.route == uk.co.traynor.privategallery.core.domain.SecondaryRoute.CLOSED) {
                            secondaryPrompt?.cancelAuthentication(); secondaryPrompt = null
                            applyScreenPrivacy()
                        }
                        state.biometricRequestId?.let { created.takeBiometricRequest()?.let { request -> showSecondaryBiometric(created, request) } }
                    }
                }
            }
        }
        controller.discover(challenge)
        secondaryState = controller.state.value
    }

    private fun showSecondaryBiometric(controller: uk.co.traynor.privategallery.core.domain.SecondaryController,
        request: uk.co.traynor.privategallery.core.domain.SecondaryBiometricRequest) {
        secondaryPrompt?.cancelAuthentication()
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                secondaryPrompt = null
                val cipher = result.cryptoObject?.cipher
                if (cipher == null) controller.cancelBiometric(request) else controller.completeBiometric(request, cipher)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                secondaryPrompt = null; controller.cancelBiometric(request)
            }
        })
        secondaryPrompt = prompt
        try {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Authenticate")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN").build(), BiometricPrompt.CryptoObject(request.cipher))
        } catch (_: Exception) { secondaryPrompt = null; controller.cancelBiometric(request) }
    }

    private fun verifySecretPin(pin: CharArray): Boolean { return try {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)) ?: return false
        operation.use {
            val candidate = keys.unlock(pin)
            try { it.commit { java.security.MessageDigest.isEqual(candidate, it.key) } }
            finally { candidate.fill(0) }
        }
    } catch (_: Exception) { false } finally { pin.fill('\u0000') } }

    private fun authenticateSensitive(completed: (Boolean) -> Unit) {
        cancelSensitiveAuthentication()
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS))
        if (operation == null || !biometricEnabled) { operation?.close(); completed(false); return }
        sensitiveOperation = operation
        val generation = ++sensitiveGeneration
        fun finish(valid: Boolean) {
            if (generation != sensitiveGeneration) return
            sensitivePrompt = null; sensitiveOperation = null
            publishUi(operation) { completed(valid) }
            operation.close()
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (generation != sensitiveGeneration || !operation.isCurrent) return
                val candidate = runCatching { result.cryptoObject?.cipher?.let(biometrics::unwrapAuthenticated) }.getOrNull()
                val valid = candidate?.let { candidateKey ->
                    runCatching { operation.commit { java.security.MessageDigest.isEqual(candidateKey, operation.key) } }.getOrDefault(false)
                } == true
                candidate?.fill(0)
                finish(valid)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish(false) }
        })
        sensitivePrompt = prompt
        operation.own(AutoCloseable { prompt.cancelAuthentication() })
        runCatching { operation.commit {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Confirm owner identity")
                .setSubtitle("Authenticate to change protected settings").setNegativeButtonText("Use PIN").build(),
                BiometricPrompt.CryptoObject(biometrics.newDecryptCipher()))
        } }.onFailure { finish(false) }
    }

    private fun cancelSensitiveAuthentication() {
        sensitiveGeneration++
        sensitivePrompt?.cancelAuthentication(); sensitivePrompt = null
        sensitiveOperation?.close(); sensitiveOperation = null
    }

    private fun applyAllowScreenshots(allowed: Boolean) {
        allowScreenshots = allowed
        appSettings.edit().putString("allow-screenshots", allowed.toString()).apply()
        applyScreenPrivacy()
    }

    private fun applyScreenPrivacy() {
        if (allowScreenshots && !protectedSpaceWindow && secondaryController?.state?.value?.route.let { it == null || it == uk.co.traynor.privategallery.core.domain.SecondaryRoute.CLOSED }) {
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
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            try {
                val repository = AndroidVaultRepository(applicationContext, operation)
                var copied = 0
                var duplicates = 0
                uris.forEach { uri ->
                    if (hideContent) return@forEach
                    when (repository.import(uri)) {
                        is ImportResult.Imported -> copied++
                        is ImportResult.Duplicate -> duplicates++
                    }
                }
                publishUi(operation) {
                    onComplete(
                        buildString {
                            append("Saved ").append(copied).append(" item")
                            if (copied != 1) append('s')
                            if (duplicates > 0) append("; ").append(duplicates).append(" duplicate(s) already in Vault")
                        },
                    )
                }
            } catch (_: Throwable) {
                publishUi(operation) { onComplete("Unable to copy the selected media. Originals were not changed.") }
            } finally {

            }
        }
    }

    private fun importBrowserSource(source: uk.co.traynor.privategallery.core.vault.VaultImportSource, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Vault save cancelled."); return }
        val operation = uk.co.traynor.privategallery.core.browser.v2.browserImportOperation(retained.authority, source.openScopedStream != null) ?: return
        launchProtected(operation) {
            val result = runCatching {
                VaultImportCoordinator(AndroidVaultRepository(applicationContext, operation)).acquire(
                    source.copy(isCancelled = { hideContent || source.isCancelled() || !operation.isCurrent || (browserRequireVpn && browserVpnState != VpnConnectionState.CONNECTED) }),
                )
            }

            publishUi(operation) {
                onComplete(
                    when (result.getOrNull()) {
                        is ImportResult.Imported -> "Saved to Vault."
                        is ImportResult.Duplicate -> "Already in Vault."
                        null -> when {
                            result.exceptionOrNull() is uk.co.traynor.privategallery.core.browser.v2.BrowserVideoUnavailableException -> "This video can be played here but can't be saved directly."
                            source.isCancelled() || !operation.isCurrent -> "Vault save cancelled."
                            else -> "Unable to save to Vault."
                        }
                    },
                )
            }
        }
    }

    private fun reportVaultLoadFailure(operation: PrimaryOperation, failure: Throwable) = publishUi(operation) {
        vaultState = if (failure is java.io.IOException) uk.co.traynor.privategallery.core.security.PrimaryVaultState.UNAVAILABLE
            else uk.co.traynor.privategallery.core.security.PrimaryVaultState.CORRUPT
        backupStatus = "Vault metadata is unavailable."
    }

    private fun loadItems(onLoaded: (List<VaultItem>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        launchProtected(operation) {
            val items = runCatching { AndroidVaultRepository(applicationContext, operation).items() }.getOrElse { reportVaultLoadFailure(operation, it); return@launchProtected }

            publishUi(operation) { vaultState = if (items.isEmpty()) uk.co.traynor.privategallery.core.security.PrimaryVaultState.EMPTY else uk.co.traynor.privategallery.core.security.PrimaryVaultState.READY; onLoaded(if (hideContent) emptyList() else items) }
        }
    }

    private fun loadCollections(onLoaded: (List<VaultCollection>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        launchProtected(operation) {
            val collections = runCatching { AndroidVaultRepository(applicationContext, operation).collections() }.getOrElse { reportVaultLoadFailure(operation, it); return@launchProtected }

            publishUi(operation) { onLoaded(if (hideContent) emptyList() else collections) }
        }
    }

    private fun loadFavouriteCollection(onLoaded: (VaultCollection?) -> Unit) {
        if (hideContent) { onLoaded(null); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val collection = runCatching { AndroidVaultRepository(applicationContext, operation).migrateLegacyFavourite() }.getOrElse { failure -> reportVaultLoadFailure(operation, failure); return@launchProtected }

            publishUi(operation) { onLoaded(if (hideContent) null else collection) }
        }
    }

    private fun setFavouriteCollection(collectionId: String, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).setFavouriteCollection(collectionId) }

            publishUi(operation) { onComplete(if (result.isSuccess) "Favourite collection updated." else "Unable to update favourite collection.") }
        }
    }

    private fun createCollection(name: String, onComplete: (Result<VaultCollection>) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).createCollection(name) }

            publishUi(operation) { onComplete(result) }
        }
    }

    private fun addItemsToCollection(collectionId: String, items: List<VaultItem>, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).let { repo -> repo.addItemsToCollection(collectionId, items.map(repo::scopedHandle)) } }

            publishUi(operation) { onComplete(if (result.isSuccess) "Added to collection." else "Unable to update collection.") }
        }
    }

    private fun renameCollection(collectionId: String, name: String, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).renameCollection(collectionId, name) }

            publishUi(operation) { onComplete(if (result.isSuccess) "Collection renamed." else "Unable to rename collection.") }
        }
    }

    private fun deleteCollection(collectionId: String, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).deleteCollection(collectionId) }

            publishUi(operation) { onComplete(if (result.isSuccess) "Collection deleted. Vault media was retained." else "Unable to delete collection.") }
        }
    }

    private fun removeItemsFromCollection(collectionId: String, items: List<VaultItem>, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val result = runCatching {
                AndroidVaultRepository(applicationContext, operation).also { repository -> items.forEach { repository.removeItemFromCollection(collectionId, repository.scopedHandle(it)) } }
            }

            publishUi(operation) { onComplete(if (result.isSuccess) "Removed from collection. Vault media was retained." else "Unable to update collection.") }
        }
    }

    private fun loadCollectionItems(collectionId: String, onLoaded: (List<VaultItem>) -> Unit) {
        if (hideContent) { onLoaded(emptyList()); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        launchProtected(operation) {
            val items = runCatching { AndroidVaultRepository(applicationContext, operation).itemsInCollection(collectionId) }.getOrElse { failure -> reportVaultLoadFailure(operation, failure); return@launchProtected }

            publishUi(operation) { vaultState = if (items.isEmpty()) uk.co.traynor.privategallery.core.security.PrimaryVaultState.EMPTY else uk.co.traynor.privategallery.core.security.PrimaryVaultState.READY; onLoaded(if (hideContent) emptyList() else items) }
        }
    }

    private fun moveSelected(uris: List<android.net.Uri>, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("No media selected."); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onComplete("Saved to Vault only. Android deletion confirmation requires Android 11 or later.")
            importSelected(uris, onComplete)
            return
        }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            val repository = AndroidVaultRepository(applicationContext, operation)
            try {
                val imported = uris.mapNotNull { uri ->
                    if (hideContent) return@mapNotNull null
                    (repository.import(uri) as? ImportResult.Imported)?.item
                }
                if (hideContent) return@launchProtected
                imported.forEach(repository::markDeletePending)
                val sources = imported.mapNotNull { it.sourceUri?.let(android.net.Uri::parse) }
                if (sources.isEmpty()) {
                    publishUi(operation) { onComplete("Saved to Vault. The selected media was already protected or unavailable.") }
                } else {
                    val callbackOperation = operation.fork()
                    val request = operation.commit { MediaStore.createDeleteRequest(contentResolver, sources) }
                    publishUi(operation, onDiscard = { callbackOperation.close() }) {
                        if (!hideContent && callbackOperation.isCurrent) {
                            pendingDeleteOperation?.close(); pendingDeleteOperation = callbackOperation
                            pendingSourceDeletion = imported
                            pendingSourceDeletionCompletion = onComplete
                            callbackOperation.commit { sourceDeletionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build()) }
                        } else callbackOperation.close()
                    }
                }
            } catch (_: Throwable) {
                publishUi(operation) { onComplete("Unable to move media. Originals were not changed.") }
            } finally {

            }
        }
    }

    private fun sourceNoLongerExists(raw: String): Boolean = runCatching {
        contentResolver.query(android.net.Uri.parse(raw), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            .use { cursor -> cursor == null || !cursor.moveToFirst() }
    }.getOrDefault(false)

    private fun restore(item: VaultItem, removeAfter: Boolean, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Unavailable."); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.EGRESS)) ?: return
        val cancelled = { hideGate || !operation.isCurrent }
        launchProtected(operation) {
            try {
                if (cancelled()) throw java.io.IOException("Restore cancelled")
                val repository = AndroidVaultRepository(applicationContext, operation)
                val publish: ((() -> Unit) -> Unit) = { commit ->
                    if (cancelled()) throw java.io.IOException("Restore cancelled")
                    operation.commit<Unit> { commit() }
                    if (cancelled()) throw java.io.IOException("Restore cancelled")
                }
                if (removeAfter) repository.restoreAndRemove(item, cancelled, publish) else repository.restore(item, cancelled, publish)
                publishUi(operation) { onComplete(if (cancelled()) "Restore interrupted. Vault copy retained." else if (removeAfter) "Restored to Gallery and removed from Vault." else "Restored to Gallery. Vault copy retained.") }
            } catch (_: Throwable) {
                publishUi(operation) { onComplete("Restore failed. The Vault copy was retained.") }
            } finally { }
        }
    }

    private fun readForViewing(item: VaultItem, onComplete: (Result<ByteArray>) -> Unit) =
        readVideoForViewing(item, { false }, {}, onComplete)

    private fun readVideoForViewing(item: VaultItem, cancelled: () -> Boolean, progress: (Int) -> Unit, onComplete: (Result<ByteArray>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        launchProtected(operation) {
            val task = coroutineContext[Job]!!
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).readVideoForViewing(item,
                { hideContent || cancelled() || !operation.isCurrent || !task.isActive },
                { percent -> publishUi(operation) { if (!hideContent && operation.isCurrent && !cancelled()) progress(percent) } },
            ) }

            publishUi(operation, onDiscard = { result.getOrNull()?.fill(0) }) {
                if (!hideContent && operation.isCurrent && !cancelled()) onComplete(result)
                else result.getOrNull()?.fill(0)
            }
        }
    }

    private fun openVideoSession(item: VaultItem, cancelled: () -> Boolean,
                                 onComplete: (Result<uk.co.traynor.privategallery.core.vault.VaultVideoSession>) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        launchProtected(operation) {
            val task = coroutineContext[Job]!!
            val allowed = { !hideContent && operation.isCurrent }
            val interrupted = { !allowed() || cancelled() || !task.isActive }
            val result = runCatching { operation.ownForSession(AndroidVaultRepository(applicationContext, operation).openVideoSession(item, allowed, interrupted)) }

            publishUi(operation, onDiscard = { result.getOrNull()?.close() }) {
                if (allowed() && !cancelled()) onComplete(result) else result.getOrNull()?.close()
            }
        }
    }

    /** Generates a bounded preview in memory only after the vault has been unlocked. */
    private fun loadPreview(item: VaultItem, onComplete: (Result<Bitmap>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        val key = operation.key
        val previewDisk = uk.co.traynor.privategallery.core.media.EncryptedPreviewCache(File(filesDir, "vault/previews"), commit = operation::commit)
        val epoch = previewEpochs[item.id] ?: 0L
        var loadedCacheKey: String? = null
        val job = launchProtected(operation) {
            val task = coroutineContext[Job]!!
            val owned = mutableListOf<Bitmap>()
            fun own(bitmap: Bitmap): Bitmap = bitmap.also { owned.add(it) }
            fun checkActive() { check(!hideContent && operation.isCurrent && task.isActive) { "Preview cancelled" } }
            val result = runCatching { synchronized(previewCacheLock) {
                check(VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize)) { "Preview is not available for this item" }
                val repository = AndroidVaultRepository(applicationContext, operation)
                checkActive()
                val identity = operation.cacheIdentity(repository.validateItem(item), repository.imageEdit(checkNotNull(item.scopedHandle))?.crop.toString())
                val revision = identity.revision
                val cacheKey = identity.primaryName + ":" + revision
                loadedCacheKey = cacheKey
                previewMemory.get(cacheKey)?.let { return@synchronized it }
                previewDisk.get(identity.primaryName, revision, key)?.let { encoded ->
                    try { BitmapFactory.decodeByteArray(encoded, 0, encoded.size, BitmapFactory.Options().apply { inMutable = true })?.let { bitmap ->
                        own(bitmap)
                        checkActive()
                        return@synchronized bitmap
                    } } finally { encoded.fill(0) }
                }
                val bytes = repository.readForEditingPreview(item) { hideContent || !operation.isCurrent || !task.isActive }
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
                        own(VaultImageEdits.crop(own(VaultImageEdits.visuallyOrient(bytes, decoded)), repository.imageEdit(checkNotNull(item.scopedHandle))?.crop))
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
                try { checkActive(); previewDisk.put(identity.primaryName, revision, encoded, key) } finally { encoded.fill(0) }
                bounded
            } }

            val delivered = result.getOrNull()
            owned.filter { it !== delivered }.distinct().forEach { if (!it.isRecycled) it.recycle() }
            val cleanup = { if (delivered != null && delivered in owned && !delivered.isRecycled) delivered.recycle() }
            if (delivered != null) {
                val reference = java.lang.ref.WeakReference(delivered)
                operation.ownForSession(AutoCloseable {
                    reference.get()?.let { bitmap -> if (bitmap.isMutable && !bitmap.isRecycled) bitmap.eraseColor(android.graphics.Color.TRANSPARENT) }
                })
            }
            publishUi(operation, onDiscard = cleanup) {
                if (!hideContent && operation.isCurrent && !task.isCancelled && (previewEpochs[item.id] ?: 0L) == epoch) {
                    result.getOrNull()?.let { bitmap -> loadedCacheKey?.let { cacheKey -> previewKeys[item.id] = cacheKey; previewMemory.put(cacheKey, bitmap) } }
                    onComplete(result)
                } else if (delivered != null && delivered in owned && !delivered.isRecycled) delivered.recycle()
            }
        }
        previewJobs.add(job)
        job.invokeOnCompletion { runOnUiThread { previewJobs.remove(job) } }
    }

    private fun loadImageEdit(item: VaultItem, onComplete: (ImageEditState?) -> Unit) {
        if (hideContent) { onComplete(null); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: return
        launchProtected(operation) {
            val edit = runCatching { AndroidVaultRepository(applicationContext, operation).imageEdit(checkNotNull(item.scopedHandle)) }.getOrNull()

            publishUi(operation) { onComplete(if (hideContent) null else edit) }
        }
    }

    private fun applyImageCrop(item: VaultItem, crop: NormalizedCrop, onComplete: (Result<ImageEditState>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.LOCAL_EDIT)) ?: return
        launchProtected(operation) {
            val result = runCatching {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, operation).applyImageCrop(checkNotNull(item.scopedHandle), crop) }
            }

            publishUi(operation) { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun undoImageCrop(item: VaultItem, onComplete: (Result<ImageEditState?>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.LOCAL_EDIT)) ?: return
        launchProtected(operation) {
            val result = runCatching {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, operation).undoImageCrop(checkNotNull(item.scopedHandle)) }
            }

            publishUi(operation) { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun resetImageCrop(item: VaultItem, onComplete: (Result<Unit>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.LOCAL_EDIT)) ?: return
        launchProtected(operation) {
            val result = runCatching {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) { AndroidVaultRepository(applicationContext, operation).resetImageCrop(checkNotNull(item.scopedHandle)) }
            }

            publishUi(operation) { if (result.isSuccess) invalidatePreview(item.id); onComplete(result) }
        }
    }

    private fun readForEditing(item: VaultItem, cancelled: () -> Boolean, completed: (Result<ByteArray>) -> Unit) {
        if (hideContent) { completed(Result.failure(IllegalStateException("Unavailable"))); return }
        if (!item.mimeType.startsWith("image/") || item.plaintextSize > uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES) {
            completed(Result.failure(IllegalArgumentException("Image too large"))); return
        }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.LOCAL_EDIT)) ?: run { completed(Result.failure(IllegalStateException("Vault locked"))); return }
        launchProtected(operation) {
            val result = runCatching { AndroidVaultRepository(applicationContext, operation).readForEditing(item) { hideContent || cancelled() || !operation.isCurrent } }

            publishUi(operation, onDiscard = { result.getOrNull()?.fill(0) }) {
                if (hideContent || cancelled() || !operation.isCurrent) { result.getOrNull()?.fill(0); completed(Result.failure(IllegalStateException("Editing cancelled"))) }
                else completed(result)
            }
        }
    }

    private fun saveEditedCopy(item: VaultItem, bytes: ByteArray, cancelled: () -> Boolean, completed: (Result<VaultItem>) -> Unit, remoteAi: Boolean = false, aiProvenance: uk.co.traynor.privategallery.core.editor.AiEditProvenance? = null) {
        if (hideContent || cancelled()) { bytes.fill(0); completed(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.LOCAL_EDIT)) ?: run { bytes.fill(0); completed(Result.failure(IllegalStateException("Vault locked"))); return }
        launchProtected(operation) {
            val result = runCatching {
                val repository = AndroidVaultRepository(applicationContext, operation)
                repository.importAiEditedCopy(checkNotNull(item.scopedHandle), bytes, aiProvenance ?: if (remoteAi) uk.co.traynor.privategallery.core.editor.AiEditProvenance(uk.co.traynor.privategallery.core.editor.AiProcessing.CLOUD, "replicate-seedream", "seedream-4.5") else null,
                    uk.co.traynor.privategallery.core.editor.AiConsentStore(applicationContext).keepEditsInVault()) {
                    hideContent || cancelled() || !operation.isCurrent
                }
            }
            bytes.fill(0)
            publishUi(operation) { completed(result) }
        }.invokeOnCompletion { bytes.fill(0) }
    }

    private fun generateVaultImage(request: uk.co.traynor.privategallery.core.editor.GenerationRequest,
        onStage: (String) -> Unit, completed: (Result<VaultItem>) -> Unit): () -> Unit {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.REMOTE_AI_EGRESS))
        if (operation == null || hideContent) {
            operation?.close()
            completed(Result.failure(IllegalStateException("Unlock Vault to create an image.")))
            return {}
        }
        val job = launchProtected(operation) {
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
                val references = if (request.referenceHandles.isEmpty()) emptyList() else {
                    val repo = AndroidVaultRepository(applicationContext, operation)
                    request.referenceHandles.map { handle ->
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        check(!hideContent && operation.isCurrent) { "Generation cancelled." }
                        operation.validate(handle)
                        val item = repo.items().single { it.id == handle.itemId }
                        check(repo.scopedHandle(item).revision == handle.revision)
                        check(item.mimeType.startsWith("image/") && item.plaintextSize <= uk.co.traynor.privategallery.core.editor.PhotoRenderer.MAX_SOURCE_BYTES)
                        val plain = repo.readForEditing(item) { hideContent || !operation.isCurrent || activeJob?.isActive != true }
                        try {
                            val prepared = uk.co.traynor.privategallery.core.editor.ReplicateImagePreparation.prepare(plain)
                            try { "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(prepared) }
                            finally { prepared.fill(0) }
                        } finally { plain.fill(0) }
                    }
                }
                operation.checkValid()
                val nativeTransport = uk.co.traynor.privategallery.core.editor.PrivateAiHttpTransport()
                val guardedTransport = uk.co.traynor.privategallery.core.editor.AiHttpTransport { request ->
                    operation.checkValid()
                    val response = nativeTransport.execute(request)
                    try { operation.checkValid(); response } catch (failure: Throwable) { response.bytes.fill(0); throw failure }
                }
                remote = withContext(uk.co.traynor.privategallery.core.security.PrimaryIoContext(uk.co.traynor.privategallery.core.security.ScopedIoGuard(operation, uk.co.traynor.privategallery.core.security.PrimaryScope.REMOTE_AI_EGRESS))) {
                    uk.co.traynor.privategallery.core.editor.ReplicateImageGenerationApi(
                    guardedTransport).generate(token!!, request.copy(prompt = effectivePrompt, references = references, referenceHandles = emptyList())) { stage ->
                    publishUi(operation) { if (!hideContent && operation.isCurrent) onStage(stage) }
                    }
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(!hideContent && operation.isCurrent) { "Generation cancelled." }
                sanitized = uk.co.traynor.privategallery.core.editor.PhotoRenderer.sanitize(remote!!)
                remote?.fill(0); remote = null
                publishUi(operation) { if (!hideContent && operation.isCurrent) onStage("Encrypting…") }
                val item = AndroidVaultRepository(applicationContext, operation).importAiGeneratedImage(sanitized!!,
                    request.model.modelId, consent.keepEditsInVault()) {
                    hideContent || !operation.isCurrent || activeJob?.isActive != true
                }
                publishUi(operation) { if (!hideContent && operation.isCurrent) completed(Result.success(item)) }
            } catch (failure: kotlinx.coroutines.CancellationException) {
                publishUi(operation) { if (operation.isCurrent && !hideContent) completed(Result.failure(IllegalStateException("Generation cancelled."))) }
            } catch (failure: Exception) {
                publishUi(operation) { if (operation.isCurrent && !hideContent) completed(Result.failure(failure)) }
            } finally {
                token?.fill(0); remote?.fill(0); sanitized?.fill(0);
                publishUi(operation) { activeJob?.let(generationJobs::remove) }
            }
        }
        generationJobs.add(job)
        return { job.cancel() }
    }

    private fun delete(item: VaultItem, onComplete: (String) -> Unit) {
        moveItemsToRecentlyDeleted(listOf(item), onComplete)
    }

    private fun restoreSelectedVaultCopies(items: List<VaultItem>, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE, PrimaryScope.EGRESS)) ?: run { onComplete("Vault locked."); return }
        launchProtected(operation) {
            val repository = AndroidVaultRepository(applicationContext, operation)
            var restored = 0
            items.distinctBy { it.id }.forEach { selected ->
                if (hideContent || !operation.isCurrent) return@forEach
                if (runCatching {
                    val item = selected
                    repository.restore(item, cancelled = { hideContent || !operation.isCurrent })
                }.isSuccess) restored++
            }

            publishUi(operation) { onComplete("$restored/${items.distinctBy { it.id }.size} copies restored to Gallery. Vault originals retained.") }
        }
    }

    private fun loadRecentlyDeleted(onLoaded: (List<VaultItem>) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ)) ?: run { onLoaded(emptyList()); return }
        launchProtected(operation) {
            val deleted = runCatching { AndroidVaultRepository(applicationContext, operation).recentlyDeleted() }.getOrElse { failure -> reportVaultLoadFailure(operation, failure); return@launchProtected }

            publishUi(operation) { onLoaded(if (operation.isCurrent && !hideContent) deleted else emptyList()) }
        }
    }

    private fun moveItemsToRecentlyDeleted(items: List<VaultItem>, onComplete: (String) -> Unit) {
        if (hideContent) { onComplete("Unavailable."); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: return
        launchProtected(operation) {
            var moved = 0
            try {
                check(!hideContent) { "Unavailable" }
                synchronized(previewCacheLock) {
                    val repository = AndroidVaultRepository(applicationContext, operation)
                    items.distinctBy { it.id }.forEach { item ->
                        if (!operation.isCurrent || hideContent) return@forEach
                        if (runCatching { repository.moveToRecentlyDeleted(repository.scopedHandle(item)) }.isSuccess) moved++
                    }
                }
                publishUi(operation) {
                    items.forEach { invalidatePreview(it.id) }
                    onComplete("$moved/${items.distinctBy { it.id }.size} moved to Recently Deleted. Kept for 30 days.")
                }
            } catch (_: Throwable) {
                publishUi(operation) { onComplete("Unable to move these items to Recently Deleted.") }
            } finally { }
        }
    }

    private fun changeRecentlyDeleted(item: VaultItem, permanently: Boolean, onComplete: (String) -> Unit) {
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.WRITE)) ?: run { onComplete("Vault locked."); return }
        launchProtected(operation) {
            val result = runCatching {
                check(operation.isCurrent && !hideContent)
                synchronized(previewCacheLock) {
                    val repository = AndroidVaultRepository(applicationContext, operation)
                    if (permanently) {
                        check(repository.recentlyDeleted().any { it.id == item.id })
                        repository.deleteFromVault(item)
                    } else repository.restoreRecentlyDeleted(repository.scopedHandle(item))
                }
            }

            publishUi(operation) { onComplete(if (result.isSuccess) if (permanently) "Deleted permanently." else "Restored to Vault."
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
            android.util.Log.w("PGAuth", "BIOMETRIC_PROMPT_UNAVAILABLE") }
    }

    private fun enrollBiometrics() {
        if (primaryEpoch == null) return
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

    private fun triggerAutomaticBiometricPromptIfNeeded() {
        if (!::keys.isInitialized || !BiometricPromptPolicy.shouldAutoPrompt(
                isLocked = !session.isUnlocked && route == Route.LOCK,
                biometricEnabled = biometricEnabled,
                alreadyPromptedForLockEntry = automaticBiometricPromptAttempted,
            )
        ) return
        automaticBiometricPromptAttempted = true
        window.decorView.post {
            if (!isFinishing && route == Route.LOCK && !session.isUnlocked) unlockWithBiometrics()
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
    data class Vault(val entries: List<ViewerMediaEntry>, val items: Map<String, VaultItem>, val initialIndex: Int) : ViewerRequest
}

@Composable
private fun PrivateGalleryApp(
    route: Route,
    onCreatePin: (CharArray) -> Result<Unit>,
    onUnlock: (CharArray) -> Result<Unit>,
    onChangePin: (CharArray, CharArray) -> Result<Unit>,
    onRecoverWithOfflineKey: (CharArray, CharArray) -> Result<Unit>,
    onFinishRecoveryKeySetup: (CharArray) -> Result<Unit>,
    onOpenRecovery: () -> Unit,
    onCloseRecovery: () -> Unit,
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onLoadCollections: ((List<VaultCollection>) -> Unit) -> Unit,
    onLoadFavouriteCollection: ((VaultCollection?) -> Unit) -> Unit,
    onCreateCollection: (String, (Result<VaultCollection>) -> Unit) -> Unit,
    onAddItemsToCollection: (String, List<VaultItem>, (String) -> Unit) -> Unit,
    onRemoveItemsFromCollection: (String, List<VaultItem>, (String) -> Unit) -> Unit,
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
    onHideContentChanged: (Boolean) -> Unit,
    onDiscovered: (uk.co.traynor.privategallery.core.domain.DiscoveryChallenge) -> Unit,
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
    onMoveItemsToRecentlyDeleted: (List<VaultItem>, (String) -> Unit) -> Unit,
    onChangeRecentlyDeleted: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onRestoreSelectedVaultCopies: (List<VaultItem>, (String) -> Unit) -> Unit,
    onBeginProtectedWork: (Set<PrimaryScope>) -> PrimaryOperation?,
    canResumeBackupRestore: Boolean,
    vaultState: uk.co.traynor.privategallery.core.security.PrimaryVaultState = uk.co.traynor.privategallery.core.security.PrimaryVaultState.LOCKED,
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
    Route.SETUP -> PinSetup(onCreatePin, onChooseBackupRestore, backupRestoreUri, onCancelBackupRestore, onRestoreBackup)
    Route.RECOVERY_KEY_SETUP -> RecoveryKeySetup(checkNotNull(recoveryKeyForSetup), onFinishRecoveryKeySetup)
    Route.BIOMETRIC_SETUP -> BiometricSetup(onEnrollBiometrics, onFinishSetup)
    Route.LOCK -> Box(Modifier.fillMaxSize()) {
        PinUnlock(onUnlock, biometricEnabled, onBiometricUnlock, onForgotPin = onOpenRecovery,
            onResumeBackupRestore = if (canResumeBackupRestore) onChooseBackupRestore else null)
        if (canResumeBackupRestore && backupRestoreUri != null)
            BackupRestoreControls(backupRestoreUri, onChooseBackupRestore, onCancelBackupRestore, onRestoreBackup)
    }
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
            Route.GALLERY -> GalleryHome(deviceMediaAccessAvailable, onRequestDeviceMediaAccess, onDeviceMediaPages, onLoadDeviceAlbums, onLoadDeviceThumbnail, onImport, onMove, onOpenViewer = { entries, index -> viewerRequest = ViewerRequest.Gallery(entries, index) }, modifier = Modifier.padding(contentPadding))
            Route.VAULT -> VaultHome(onLock, onImport, onLoadItems, onLoadCollections, onCreateCollection, onAddItemsToCollection, onRemoveItemsFromCollection, onRenameCollection, onDeleteCollection, onLoadCollectionItems, onLoadPreview, cropRevision, biometricEnabled, onEnrollBiometrics, onSetFavouriteCollection, onFavouriteStateChanged = { onLoadFavouriteCollection { favouriteLabel = it?.name } }, onOpenViewer = { entries, items, index -> viewerRequest = ViewerRequest.Vault(entries, items, index) }, modifier = Modifier.padding(contentPadding), onGenerateImage = onGenerateImage, onLoadRecentlyDeleted = onLoadRecentlyDeleted, onMoveItemsToRecentlyDeleted = onMoveItemsToRecentlyDeleted, onChangeRecentlyDeleted = onChangeRecentlyDeleted, onRestoreSelectedVaultCopies = onRestoreSelectedVaultCopies, vaultState = vaultState)
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
                onLoadVaultPreview = onLoadPreview,
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
            Route.SETTINGS -> SettingsHome(autoLockTimeout, appTheme, allowScreenshots, updateStatus, updateLastChecked, updateAvailable, biometricEnabled, recoveryKeyConfigured, browserSearchEngine, clearBrowserDataOnLock, onAutoLockTimeoutChanged, onThemeChanged, onAllowScreenshotsChanged, onBrowserSearchEngineChanged, onClearBrowserDataOnLockChanged, onClearBrowserData, onCheckForUpdates, onDownloadUpdate, onChangePin, onLock, browserAutoConnectVpn, requireVpnForBrowsing, onBrowserAutoConnectVpnChanged, onBrowserRequireVpnChanged, onImportWireGuardProfile, vpnProfileStatus, vpnConnectionState, vpnProfiles, onSelectVpnProfile, onRemoveVpnProfile, modifier = Modifier.padding(contentPadding), browserSaveHistory = browserSaveHistory, onBrowserSaveHistoryChanged = onBrowserSaveHistoryChanged, browserStaticContentHost = browserStaticContentHost, onBrowserStaticContentHostChanged = { browserStaticContentHost = it }, browserLayoutColours = browserLayoutColours, onBrowserLayoutColoursChanged = { browserLayoutColours = it }, contentBlocker = browserV2Session.contentBlocker, hideContent = hideContent, onHideContentChanged = onHideContentChanged, onDiscovered = onDiscovered, onAuthenticateSensitive = onAuthenticateSensitive, onCancelSensitiveAuthentication = onCancelSensitiveAuthentication, onVerifySecretPin = onVerifySecretPin, beginProtectedWork = onBeginProtectedWork, onChooseBackupExport = onChooseBackupExport, backupExportUri = backupExportUri, onCancelBackupExport = onCancelBackupExport, onExportBackup = onExportBackup, backupStatus = backupStatus)
            else -> Unit
        }
    }
    if (!hideContent) collectionTarget?.let { itemId ->
        GalleryMenuSheet("Add to collection", { collectionTarget = null }) {
            if (viewerCollections.isEmpty()) Text("Create a collection in Vault first.", Modifier.padding(24.dp))
            viewerCollections.forEach { collection ->
                SheetAction(collection.name, Icons.Default.Folder) { (viewerRequest as? ViewerRequest.Vault)?.items?.get(itemId)?.let { onAddItemsToCollection(collection.id, listOf(it)) {} }; collectionTarget = null }
            }
        }
    }
    if (!hideContent) viewerRequest?.let { request ->
        when (request) {
            is ViewerRequest.Gallery -> FullscreenMediaViewer(
                entries = request.entries,
                source = MediaViewerSource.GALLERY,
                onBeginProtectedWork = onBeginProtectedWork,
                initialIndex = request.initialIndex,
                onClose = { viewerRequest = null },
                onCopyToVault = { entry -> entry.uri?.let { onImport(listOf(it)) {} } },
                onMoveToVault = { entry -> entry.uri?.let { onMove(listOf(it)) {} } },
            )
            is ViewerRequest.Vault -> FullscreenMediaViewer(
                entries = request.entries,
                source = MediaViewerSource.VAULT,
                onBeginProtectedWork = onBeginProtectedWork,
                initialIndex = request.initialIndex,
                onClose = { viewerRequest = null },
                onLoadProtectedBytes = { id, loaded -> request.items[id]?.let { onReadForViewing(it, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadVideoBytes = { id, cancelled, progress, loaded -> request.items[id]?.let { onReadVideoForViewing(it, cancelled, progress, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
                onLoadVideoSession = { id, cancelled, loaded -> request.items[id]?.let { onOpenVideoSession(it, cancelled, loaded) } ?: loaded(Result.failure(IllegalStateException("Missing Vault item"))) },
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
    onDeviceMediaPages: (String?) -> Flow<PagingData<DeviceMediaItem>>,
    onLoadDeviceAlbums: suspend () -> List<DeviceAlbum>,
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
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var albumMenuExpanded by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<DeviceAlbum>>(emptyList()) }
    var albumRefresh by remember { mutableStateOf(0) }
    LaunchedEffect(deviceMediaAccessAvailable, albumRefresh) {
        albums = if (deviceMediaAccessAvailable) runCatching { onLoadDeviceAlbums() }.getOrDefault(emptyList()) else emptyList()
    }
    val deviceMediaFlow = remember(deviceMediaAccessAvailable, albumId) {
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
        )
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
    BackupRestoreControls(backupRestoreUri, onChooseBackupRestore, onCancelBackupRestore, onRestoreBackup)
}

@Composable
private fun BackupRestoreControls(backupRestoreUri: Uri?, onChooseBackupRestore: () -> Unit,
    onCancelBackupRestore: () -> Unit, onRestoreBackup: (CharArray, CharArray, (Boolean) -> Unit) -> Unit) {
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
internal fun PinUnlock(onUnlock: (CharArray) -> Result<Unit>, biometricEnabled: Boolean, onBiometricUnlock: () -> Unit, onForgotPin: () -> Unit,
    onResumeBackupRestore: (() -> Unit)? = null) {
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
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onForgotPin) { Text("Forgot PIN?") }
                    if (biometricEnabled) TextButton(onClick = onBiometricUnlock) { Text("Use biometrics") }
                }
                onResumeBackupRestore?.let { resume -> TextButton(onClick = resume) { Text("Resume encrypted backup restore") } }
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
private fun RecoveryKeySetup(recoveryKey: String, onFinish: (CharArray) -> Result<Unit>) {
    var stored by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LockSurface {
        GalleryPageTitle("Keep this offline", "Recovery key")
        if (!stored) {
            Text("Store this key securely. You will re-enter it to confirm recovery before setup finishes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(recoveryKey, modifier = Modifier.padding(GalleryTokens.RowPaddingHorizontal, GalleryTokens.RowPaddingVertical), style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = { stored = true }, modifier = Modifier.fillMaxWidth()) { Text("I have stored this key") }
        } else {
            Text("Enter the recovery key you stored to confirm it.")
            OutlinedTextField(entered, { entered = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Recovery key") }, visualTransformation = PasswordVisualTransformation())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                onFinish(entered.toCharArray()).onSuccess { entered = "" }.onFailure { entered = ""; error = "Recovery key could not be confirmed. Try again." }
            }, enabled = entered.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Confirm recovery key") }
        }
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
    onHideContentChanged: (Boolean) -> Unit = {},
    onDiscovered: (uk.co.traynor.privategallery.core.domain.DiscoveryChallenge) -> Unit = {},
    onAuthenticateSensitive: ((Boolean) -> Unit) -> Unit = { it(false) },
    onCancelSensitiveAuthentication: () -> Unit = {},
    onVerifySecretPin: (CharArray) -> Boolean = { it.fill('\u0000'); false },
    onChooseBackupExport: () -> Unit = {},
    backupExportUri: Uri? = null,
    onCancelBackupExport: () -> Unit = {},
    onExportBackup: (CharArray) -> Unit = { it.fill('\u0000') },
    backupStatus: String = "",
    beginProtectedWork: ((Set<PrimaryScope>) -> PrimaryOperation?)? = null,
) {
    val appContext = LocalContext.current.applicationContext
    val aiConfiguration = remember(appContext) { uk.co.traynor.privategallery.core.editor.AiProviderRegistry.initialize(appContext) }
    val aiStatus by aiConfiguration.status.collectAsState()
    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var secretOpen by remember { mutableStateOf(false) }
    var authAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    val discovery = remember { uk.co.traynor.privategallery.core.domain.DiscoverySequence(android.os.SystemClock::elapsedRealtime) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                discovery.reset()
                secretOpen = false
                authAction = null
                onCancelSensitiveAuthentication()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { discovery.reset(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    androidx.activity.compose.BackHandler(category != null || secretOpen) {
        if (secretOpen) secretOpen = false else category = null
    }
    LaunchedEffect(category) {
        if (category != SettingsCategory.SECURITY) secretOpen = false
        if (category != SettingsCategory.ABOUT) discovery.reset()
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
                Text("Presentation controls", style = MaterialTheme.typography.headlineSmall)
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
        if (category == SettingsCategory.SECURITY && !secretOpen) SettingsSection("Protected settings") {
            androidx.compose.material3.Surface(onClick = { authAction = java.util.UUID.randomUUID().toString() to "enter" }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Presentation controls", style = MaterialTheme.typography.titleMedium); Text("Content visibility and screen capture", style = MaterialTheme.typography.bodySmall) }
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
        if (category == SettingsCategory.SECURITY && secretOpen) SettingsSection("Privacy presentation") {
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
        if (category == SettingsCategory.SECURITY && secretOpen) SettingsSection("Screen capture") {
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
                secretOpen = false
            }, modifier = Modifier.fillMaxWidth()) { Text("Close presentation controls") }
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
        if (category == SettingsCategory.AI) uk.co.traynor.privategallery.ui.AiEditingSettings(beginProtectedWork = beginProtectedWork)
        if (category == SettingsCategory.ABOUT) SettingsSection(SettingsSections.UPDATES) {
            androidx.compose.material3.Surface(onClick = {
                discovery.consume(uk.co.traynor.privategallery.core.domain.DiscoveryEvent.INSTALLED)?.let(onDiscovered)
            }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Installed", style = MaterialTheme.typography.titleMedium)
                    Text("${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Latest", style = MaterialTheme.typography.titleMedium)
            Text(updateStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last checked", style = MaterialTheme.typography.titleMedium)
            Text(updateLastChecked, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { discovery.reset(); onCheckForUpdates() }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
            if (updateAvailable) Button(onClick = { discovery.reset(); onDownloadUpdate() }, modifier = Modifier.fillMaxWidth()) { Text("Download update") }
        }
        if (category == SettingsCategory.ABOUT) SettingsSection(SettingsSections.ABOUT) {
            androidx.compose.material3.Surface(onClick = {
                discovery.consume(uk.co.traynor.privategallery.core.domain.DiscoveryEvent.VERSION)?.let(onDiscovered)
            }, shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("Private Gallery ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.titleMedium)
            }
            Text("Media stays in encrypted private app storage until you restore it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = { discovery.reset(); showingLicences = true }, modifier = Modifier.fillMaxWidth()) { Text("Third-party licences") }
        }
        Button(onClick = { discovery.reset(); onLock() }, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
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
                        "enter" -> secretOpen = true
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
    onAddItemsToCollection: (String, List<VaultItem>, (String) -> Unit) -> Unit,
    onRemoveItemsFromCollection: (String, List<VaultItem>, (String) -> Unit) -> Unit,
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
    onMoveItemsToRecentlyDeleted: (List<VaultItem>, (String) -> Unit) -> Unit = { _, done -> done("Unavailable.") },
    onChangeRecentlyDeleted: (VaultItem, Boolean, (String) -> Unit) -> Unit = { _, _, done -> done("Unavailable.") },
    onRestoreSelectedVaultCopies: (List<VaultItem>, (String) -> Unit) -> Unit = { _, done -> done("Unavailable.") },
    vaultState: uk.co.traynor.privategallery.core.security.PrimaryVaultState = uk.co.traynor.privategallery.core.security.PrimaryVaultState.READY,
) {
    var status by remember { mutableStateOf("") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var collections by remember { mutableStateOf<List<VaultCollection>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var creatingImage by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var density by rememberSaveable { mutableStateOf(ThumbnailDensity.COMPACT) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MediaKindFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(MediaSort.NEWEST) }
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
    fun refresh() {
        refreshRevision++
        onLoadItems { items -> vaultItems = items; loaded = true }
        onLoadCollections { collections = it }
        onLoadRecentlyDeleted { recentlyDeleted = it }
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
            title = openCollection?.name ?: if (contentMode == VaultContentMode.TRASH) "Recently Deleted" else "Vault",
            subtitle = if (contentMode == VaultContentMode.TRASH) "${recentlyDeleted.size} items · deleted after 30 days" else if (openCollection == null) VaultSummary.from(vaultItems).let { "${it.photos} photos · ${it.videos} videos" } else "${openCollectionItems.size} items",
            onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onBack = when { contentMode == VaultContentMode.TRASH -> ({ contentMode = VaultContentMode.MEDIA }); openCollection != null -> ({ openCollection = null }); else -> null },
            onLock = onLock,
            onMenu = { menuOpen = true },
            onGenerate = if (openCollection == null) ({ creatingImage = true }) else null,
        )
        if (menuOpen) GalleryMenuSheet("Vault", onDismiss = { menuOpen = false }) {
            SheetSection("Add and organise")
            SheetAction("Add media", Icons.Default.AddPhotoAlternate) { menuOpen = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
            SheetAction("Create image", Icons.Default.AutoAwesome) { menuOpen = false; creatingImage = true }
            if (openCollection == null && contentMode == VaultContentMode.MEDIA) SheetAction("Select items", Icons.Default.CheckCircle, enabled = vaultItems.isNotEmpty()) {
                selectionMode = true; menuOpen = false
            }
            SheetAction("New collection", Icons.Default.CreateNewFolder) { creatingCollection = true; menuOpen = false }
            SheetAction("Recently Deleted", Icons.Default.DeleteOutline) {
                openCollection = null; contentMode = VaultContentMode.TRASH; selectedItemIds = emptySet(); selectionMode = false
                onLoadRecentlyDeleted { recentlyDeleted = it }; menuOpen = false
            }
            SheetAction("Refresh", Icons.Default.Refresh) { refresh(); menuOpen = false }
            if (openCollection == null && contentMode == VaultContentMode.MEDIA) MediaSortChoices(sort) { sort = it }
            SheetSection("View")
            ThumbnailSizeChoices(density) { density = it }
            SheetSection("Security")
            if (!biometricEnabled) SheetAction("Enable biometric unlock", Icons.Default.Fingerprint) { menuOpen = false; onEnrollBiometrics() }
            SheetAction("Lock Vault", Icons.Default.Lock) { menuOpen = false; onLock() }
        }
        if (openCollection == null && contentMode != VaultContentMode.TRASH) {
            androidx.compose.material3.TabRow(selectedTabIndex = if (contentMode == VaultContentMode.MEDIA) 0 else 1) {
                androidx.compose.material3.Tab(selected = contentMode == VaultContentMode.MEDIA, onClick = { contentMode = VaultContentMode.MEDIA }, text = { Text("Media") })
                androidx.compose.material3.Tab(selected = contentMode == VaultContentMode.COLLECTIONS, onClick = { contentMode = VaultContentMode.COLLECTIONS; selectedItemIds = emptySet(); selectionMode = false }, text = { Text("Collections") })
            }
        }
        if (openCollection == null && contentMode == VaultContentMode.MEDIA && vaultItems.isNotEmpty()) {
            VaultBrowseControls(query, { query = it; selectedItemIds = emptySet(); selectionMode = false }, kind, { kind = it; selectedItemIds = emptySet(); selectionMode = false })
        }
        when {
            vaultState in setOf(uk.co.traynor.privategallery.core.security.PrimaryVaultState.CORRUPT, uk.co.traynor.privategallery.core.security.PrimaryVaultState.UNAVAILABLE) -> GalleryCard {
                Text("Vault unavailable", style = MaterialTheme.typography.titleMedium)
                Text("Existing or partial Vault material could not be authenticated. Lock and try again, or use your independent encrypted backup. Fresh setup is disabled.")
            }
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
                            TextButton(enabled = selectedItemIds.isNotEmpty(), onClick = { confirmingBulkRestore = true }) { Text("Restore copies") }
                            TextButton(enabled = selectedItemIds.isNotEmpty(), onClick = { confirmingBulkTrash = true }) { Text("Delete") }
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
        if (confirmingBulkTrash) AlertDialog(onDismissRequest = { confirmingBulkTrash = false },
            title = { Text("Move ${selectedItemIds.size} items to Recently Deleted?") },
            text = { Text("Encrypted copies remain recoverable for 30 days. Collections are restored with them.") },
            confirmButton = { TextButton(onClick = {
                confirmingBulkTrash = false
                onMoveItemsToRecentlyDeleted(vaultItems.filter { it.id in selectedItemIds }) {
                    status = it; selectedItemIds = emptySet(); selectionMode = false; refresh()
                }
            }) { Text("Move to Recently Deleted") } },
            dismissButton = { TextButton(onClick = { confirmingBulkTrash = false }) { Text("Cancel") } })
        if (confirmingBulkRestore) AlertDialog(onDismissRequest = { confirmingBulkRestore = false },
            title = { Text("Restore ${selectedItemIds.size} copies to Gallery?") },
            text = { Text("Every copy is verified before publication. Restricted AI items stay in the Vault; Vault originals are retained.") },
            confirmButton = { TextButton(onClick = {
                confirmingBulkRestore = false
                onRestoreSelectedVaultCopies(vaultItems.filter { it.id in selectedItemIds }) {
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
        }, onDismiss = { creatingImage = false }, vaultImages = vaultItems, onLoadPreview = onLoadPreview)
    collectionPickerFor?.let { ids -> CollectionPickerDialog(collections, onChoose = { collection ->
        onAddItemsToCollection(collection.id, vaultItems.filter { it.id in ids }) { status = it; selectedItemIds = emptySet(); selectionMode = false; collectionPickerFor = null; refresh() }
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
    onRemove: ((List<VaultItem>) -> Unit)? = null,
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
                        TextButton(onClick = { onRemove(items.filter { it.id in selectedIds }); selectedIds = emptySet() }) { Text("Remove") }
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
    onAddItemsToCollection: (String, List<VaultItem>, (String) -> Unit) -> Unit,
    onRemoveItemsFromCollection: (String, List<VaultItem>, (String) -> Unit) -> Unit,
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
private fun ExistingVaultItemsDialog(items: List<VaultItem>, onAdd: (List<VaultItem>) -> Unit, onDismiss: () -> Unit) {
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
        confirmButton = { TextButton(onClick = { if (selected.isNotEmpty()) onAdd(items.filter { it.id in selected }) }) { Text("Add") } },
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
