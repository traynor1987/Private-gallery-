Warning: truncated output (original token count: 60922)
Total output lines: 3959

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
import uk.co.traynor.privategallery.core.security.OwnedResource
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
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

/** The reservation owns the individual file deletions; this root only carries published values. */
private class BrowserUploadCopies(val files: MutableList<File>, val uris: MutableList<Uri>) : AutoCloseable {
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
                operation.checkProducerAdmission()
                block()
                ensureActive()
                operation.checkProducerAdmission()
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


    private val browserUploadCopies = mutableListOf<OwnedResource<BrowserUploadCopies>>()
    @Volatile private var browserUploadGeneration = 0L

    private fun clearBrowserUploadCopies() {
        browserUploadGeneration++
        browserUploadCopies.forEach { it.close() }
        browserUploadCopies.clear()
    }

    private fun prepareBrowserUpload(items: List<VaultItem>, onComplete: (Result<List<Uri>>) -> Unit) {
        if (hideContent) { onComplete(Result.failure(IllegalStateException("Unavailable"))); return }
        val operation = retained.authority.operationOrNull(setOf(PrimaryScope.READ, PrimaryScope.BROWSER_UPLOAD_EGRESS)) ?: run { onComplete(Result.failure(IllegalStateException("Vault locked"))); return }
        val generation = browserUploadGeneration
        launchProtected(operation) {
            val result = runCatching {
                require(items.size in 1..4) { "Select up to four items" }
                require(items.sumOf { it.plaintextSize } <= 256L * 1024 * 1024) { "Upload selection exceeds size limit" }
                val cleanup = operation.createSessionOwnedJob { attach ->
                    lifecycleScope.launch(start = CoroutineStart.LAZY) {
                        delay(5 * 60 * 1000L)
                        if (operation.isCurrent && generation == browserUploadGeneration) clearBrowserUploadCopies()
                    }.also(attach)
                }
                try {
                    operation.createSessionOwned(OwnedResourceManifest.io("root", *items.indices.map { "upload-$it" }.toTypedArray())) {
                        val directory = File(cacheDir, "browser-upload").apply { mkdirs() }
                        val repository = AndroidVaultRepository(applicationContext, operation)
                        val copies = attach("root", BrowserUploadCopies(mutableListOf(), mutableListOf()))
                        val uris = items.mapIndexed { index, item ->
                            if (hideContent || generation != browserUploadGeneration || !operation.isCurrent) throw java.io.IOException("Upload cancelled")
                            val folder = File(directory, java.util.UUID.randomUUID().toString())
                            val file = File(folder, uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.safeName(item.mimeType))
                            attach("upload-$index", AutoCloseable { file.delete(); folder.delete() })
                            folder.mkdirs()
                            copies.files += file
                            repository.prepareBrowserUpload(item, file) { hideContent || generation != browserUploadGeneration || !operation.isCurrent }
                            FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                        }
                        copies.uris += uris
                        copies
                    } to cleanup
                } catch (failure: Throwable) {
                    cleanup.cancel()
                    throw failure
                }
            }

            publishUi(operation, onDiscard = {
                result.getOrNull()?.let { (copies, cleanup) -> copies.close(); cleanup.cancel() }
            }) {
                if (hideContent || generation != browserUploadGeneration || !operation.isCurrent || result.isFailure) {
                    result.getOrNull()?.let { (copies, cleanup) -> copies.close(); cleanup.cancel() }
                    onComplete(Result.failure(result.exceptionOrNull() ?: java.io.IOException("Upload cancelled")))
                } else {
                    val (copies, cleanup) = checkNotNull(result.getOrNull())
                    browserUploadCopies += copies
                    onComplete(Result.success(copies.value.uris))
                    cleanup.start()
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
        val admitted = uk.co.traynor.privategallery.core.browser.v2.browserSessionAdmission(browserOwnerOperation, ::createBrowserSession)
        browserOwnerOperation = admitted.owner
        val owner = admitted.owner
        val replacement = admitted.session
        if (::appSettings.isInitialized) replacement.contentBlocker.enabled = appSettings.getBoolean("browser-content-blocking", true)
        ownedBrowserSession = replacement
        if (owner != null) {
            // Revocation can originate on an IO deadline check. WebView/UI cleanup must finish
            // on Main, and its registered Job keeps cleanupComplete false until that finishes.
            val cleanupJob = owner.createSessionOwnedJob { attach -> lifecycleScope.launch(Dispatchers.Main.immediate, start = CoroutineStart.LAZY) {
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
            }.also(attach) }
            cleanupJob.start()
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
        // Authenticate again to restart pe…40922 tokens truncated…t media and metadata will be saved. Your PIN, Browser and AI settings are excluded.")
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
