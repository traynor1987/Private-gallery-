package uk.co.traynor.privategallery.launcher

import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.SizeF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryAppVisibility
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryDnd
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.core.sanctuary.panicSanctuaryPersonalApps
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.privateWorkspaceEntryIntent
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

/** Android Home task. Protected launcher changes verify the Primary PIN without starting a vault session. */
class NovaLauncherActivity : ComponentActivity() {
    private val store by lazy { NovaLauncherStore(this) }
    private val tracking by lazy { LauncherSmartTracking(applicationContext) }
    private val identitySettings by lazy { LauncherIdentitySettings(applicationContext) }
    private var smartFolderPresentation by mutableStateOf(LauncherSmartFolderPresentation.AUTOMATIC)
    private var renaming by mutableStateOf<LauncherApp?>(null)
    private var rawApps = emptyList<LauncherApp>()
    private var pendingUsageRefresh = false
    private var usageRefresh: Job? = null
    private var profileReceiverRegistered = false

    private val profileChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshCatalogue()
    }

    private val usageAccess = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tracking.finishExtendedConsent()
        pendingUsageRefresh = true
        refreshUsageIfEligible()
    }
    private val directory by lazy { LauncherAppDirectory(applicationContext) }
    private val sanctuary by lazy { SanctuaryStore.get(applicationContext) }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { AppWidgetHost(this, WIDGET_HOST_ID) }
    private var model by mutableStateOf(NovaLauncherState())
    private var activeUtility by mutableStateOf<LauncherBuiltIn?>(null)
    private var apps by mutableStateOf(emptyList<LauncherApp>())
    private var shortcuts by mutableStateOf(emptyList<LauncherShortcut>())
    private var decisions by mutableStateOf(emptyMap<String, SanctuaryLauncherPolicy.Decision>())
    private var launcherProtected by mutableStateOf(false)
    private var defaultHome by mutableStateOf<Boolean?>(null)
    private var askDefaultHome by mutableStateOf(true)
    private var defaultHomeOpening by mutableStateOf(LauncherDefaultHomePromptSession())
    private var homeTopResumed by mutableStateOf(false)
    private var homeWindowFocused by mutableStateOf(false)
    private lateinit var launcherExit: LauncherExitProtection
    private var outsideProtection by mutableStateOf(true)
    private var homeGeneration by mutableIntStateOf(0)
    private var blocked by mutableStateOf<LauncherApp?>(null)
    private var review by mutableStateOf(false)
    private var sanctuaryOpen by mutableStateOf(false)
    private var sanctuaryStatus by mutableStateOf<LauncherSanctuaryStatus?>(null)
    private var picker by mutableStateOf<WidgetPicker?>(null)
    private var pendingWidget: PendingWidget? = null
    private var catalogueJob: Job? = null
    private var callbacksRegistered = false
    private val widgetFrames = mutableSetOf<GuardedWidgetFrame>()

    private data class WidgetPicker(val wide: Boolean, val providers: List<AppWidgetProviderInfo>)
    private data class PendingWidget(val id: Int, val provider: ComponentName, val wide: Boolean)

    private var appVisibility by mutableStateOf(SanctuaryAppVisibility())
    private val pendingAddedPackages = mutableSetOf<Pair<String, UserHandle>>()

    private val packageChanges = object : LauncherApps.Callback() {
        private fun changed() = refreshCatalogue()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            if (model.newAppPlacement == LauncherNewAppPlacement.HOME) pendingAddedPackages += packageName to user
            changed()
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = changed()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = changed()
        override fun onShortcutsChanged(packageName: String, shortcuts: List<android.content.pm.ShortcutInfo>, user: UserHandle) =
            changed()
    }

    private val defaultHomeRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (updateDefaultHome()) refreshCatalogue()
    }

    private val widgetBind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK && result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id)
                .let { it == null || it == pending.id }) {
            configureOrSaveWidget(pending)
        } else releasePendingWidget()
    }
    private val layoutExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(contentResolver.openOutputStream(uri, "wt")).use { it.write(store.exportLayout()) }
                }.isSuccess
            }
            message(if (saved) "Launcher layout exported" else "The launcher layout could not be exported.")
        }
    }
    private val layoutImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = checkNotNull(contentResolver.openInputStream(uri)).use {
                        it.readNBytes(MAX_LAUNCHER_BACKUP_BYTES + 1)
                    }
                    store.restoreLayout(bytes)
                }.getOrDefault(false)
            }
            if (restored) {
                model = store.read()
                updateWallpaper()
                homeGeneration++
            }
            message(if (restored) "Launcher layout restored" else "This file is not a valid Nova Launcher layout.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcherExit = LauncherExitProtection(
            this,
            sessionId = { sanctuary.refresh()?.id },
            foreground = {
                homeTopResumed && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    !isFinishing && !isDestroyed &&
                    getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false
            },
            openAndroidHomeSettings = ::openAndroidHomeSettings,
        )
        setIntent(neutralHomeIntent())
        enableEdgeToEdge()
        val guardianProbeBuild = BuildConfig.DEBUG && packageName.endsWith(".guardianprobe")
        model = store.read().let { if (guardianProbeBuild) it.copy(configured = true) else it }
        smartFolderPresentation = identitySettings.smartFolderPresentation()
        offerSmartTemplate()
        if (guardianProbeBuild) store.setAskForDefaultHome(false)
        askDefaultHome = store.askForDefaultHome()
        defaultHomeOpening = LauncherDefaultHomePromptSession(savedInstanceState?.getBoolean(DEFAULT_HOME_OFFERED) == true)
        launcherProtected = SanctuaryLauncherPolicy.launcherProtectionActive(this)
        appVisibility = SanctuaryLauncherPolicy.visibility(this)
        sanctuaryStatus = sanctuary.refresh()?.let(::sanctuarySnapshot)
        outsideProtection = SanctuaryLauncherPolicy.outsideProtectionEnabled(this)
        pendingWidget = savedInstanceState?.let { saved ->
            val id = saved.getInt(WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val provider = saved.getString(WIDGET_PROVIDER)?.let(ComponentName::unflattenFromString)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && provider != null) {
                PendingWidget(id, provider, saved.getBoolean(WIDGET_WIDE))
            } else null
        }
        reconcileWidgetIds()
        updateWallpaper()
        updateDefaultHome()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sanctuary.current, SanctuaryLauncherPolicy.state()) { session, _ -> session }
                    .collectLatest { session ->
                        launcherExit.onSessionChanged(session?.id)
                        refreshProtection(invalidatePolicy = true)
                        outsideProtection = SanctuaryLauncherPolicy.outsideProtectionEnabled(this@NovaLauncherActivity)
                        refreshCatalogue()
                        sanctuaryStatus = session?.let(::sanctuarySnapshot)
                        while (session != null && sanctuaryStatus != null) {
                            val remaining = checkNotNull(sanctuaryStatus).remainingMillis
                            val nextTick = remaining % 1000L
                            delay(if (nextTick == 0L) 1000L else nextTick)
                            sanctuaryStatus = sanctuarySnapshot(session)
                            if (sanctuaryStatus == null) sanctuary.refresh()
                        }
                    }
            }
        }
        val actions = LauncherUiActions(
            save = { next ->
                save(next.copy(launchCounts = model.launchCounts)).also { if (it) offerSmartTemplate() }
            },
            reloadWorkspaceLayout = ::reloadWorkspaceLayout,
            launchApp = ::launchApp,
            launchShortcut = ::launchShortcut,
            openWorkspace = ::openWorkspace,
            selectHome = ::openHomeSettings,
            continueWithCurrentHome = { if (defaultHome == true) openHomeSettings() else finish() },
            emergencyPhone = ::openEmergencyPhone,
            reviewSanctuary = { review = true },
            setOutsideProtection = { enabled ->
                if (!SanctuaryLauncherPolicy.setOutsideProtectionEnabled(this, enabled)) {
                    message("The protection setting could not be saved.")
                }
            },
            chooseWallpaper = ::chooseDeviceWallpaper,
            addWidget = ::pickWidget,
            removeWidget = ::removeWidget,
            exportLayout = { layoutExport.launch("Nova-Launcher-layout.json") },
            importLayout = { layoutImport.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            renderWidget = { widget, modifier -> WidgetView(widget, modifier) },
            openSanctuary = ::openSanctuary,
            openAppInfo = ::openAppInfo,
            renameApp = { key -> rawApps.firstOrNull { it.component == key && it.packageName != packageName }
                ?.let { renaming = it } },
            toggleRecentPin = { tracking.togglePin(it) },
            checkDefaultHome = ::checkDefaultHome,
            setAskForDefaultHome = ::setAskForDefaultHome,
            claimDefaultHomePrompt = ::claimDefaultHomePrompt,
            requestDefaultHome = ::requestDefaultHome,
            configureExitProtection = launcherExit::requestConfiguration,
            setSmartFolderPresentation = { mode ->
                if (identitySettings.setSmartFolderPresentation(mode)) smartFolderPresentation = mode
                else message("The smart folder presentation could not be saved.")
            },
        )
        setContent {
            val smart by tracking.state.collectAsState()
            LaunchedEffect(smart.excluded) {
                val counts = model.launchCounts - smart.excluded
                if (counts != model.launchCounts) save(model.copy(launchCounts = counts))
            }
            val utility = activeUtility
            val utilityAllowed = utility != null && decisions[utility.app(packageName).component]?.canLaunch == true
            if (!utilityAllowed) {
                NovaLauncherScreen(model, apps, if (launcherProtected) emptyList() else shortcuts, decisions,
                    defaultHome == true, outsideProtection, actions, homeGeneration, launcherProtected, sanctuaryStatus,
                    defaultHomeUi = LauncherDefaultHomeUi(defaultHome, askDefaultHome,
                        canShowDefaultHomePrompt(), homeWindowFocused, defaultHomeOpening),
                    smartState = smart, smartFolderPresentation = smartFolderPresentation, appVisibility = appVisibility,
                    smartSettings = { eligible ->
                        LauncherSmartSettings(tracking, eligible, onClearLegacyHistory = {
                            save(model.copy(launchCounts = emptyMap()))
                        }, onRequestUsageAccess = ::openUsageAccess)
                    })
            }
            val theme = when (model.appearance.theme) {
                LauncherTheme.SYSTEM -> AppTheme.SYSTEM
                LauncherTheme.LIGHT -> AppTheme.LIGHT
                LauncherTheme.DARK -> AppTheme.DARK
            }
            PrivateGalleryTheme(theme) {
                if (utilityAllowed && utility != null) {
                    LauncherUtilityScreen(
                        checkNotNull(utility.utility),
                        onBack = { activeUtility = null },
                        onOpenWorkspace = { activeUtility = null; openWorkspace() },
                    )
                }
                SanctuaryDialogs()
                LauncherExitProtectionDialogs(launcherExit)
                WidgetPickerDialog()
                RenameLauncherAppDialog()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(neutralHomeIntent())
        // Reopening the same task keeps its existing reminder decision.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            launcherExit.cancel()
            activeUtility = null
            blocked = null
            review = false
            sanctuaryOpen = false
            picker = null
            homeGeneration++
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
        callbacksRegistered = runCatching { directory.launcherApps.registerCallback(packageChanges) }.isSuccess
        profileReceiverRegistered = runCatching {
            registerReceiver(profileChanges, IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
            }, Context.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        refreshCatalogue()
    }

    override fun onResume() {
        super.onResume()
        reloadWorkspaceLayout()
        refreshProtection()
        askDefaultHome = store.askForDefaultHome()
        pendingUsageRefresh = true
        val homeRoleChanged = updateDefaultHome()
        if (homeRoleChanged || rawApps.isEmpty()) refreshCatalogue()
        else lifecycleScope.launch { lifecycle.withResumed { refreshUsageIfEligible() } }
    }

    override fun onPause() {
        usageRefresh?.cancel()
        launcherExit.cancel()
        super.onPause()
    }

    private fun refreshProtection(invalidatePolicy: Boolean = false) {
        val active = SanctuaryLauncherPolicy.launcherProtectionActive(this)
        if (active && (invalidatePolicy || !launcherProtected)) {
            decisions = emptyMap()
            blocked = null
            review = false
        }
        launcherProtected = active
        appVisibility = SanctuaryLauncherPolicy.visibility(this)
        if (active) {
            tracking.suspendExtended()
            picker = null
            widgetFrames.forEach(GuardedWidgetFrame::disableContent)
        }
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        homeTopResumed = isTopResumedActivity
        if (isTopResumedActivity) SanctuaryLauncherPolicy.launcherResumed(this)
        else {
            launcherExit.cancel()
            SanctuaryLauncherPolicy.launcherPaused()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        homeWindowFocused = hasFocus
        if (hasFocus) {
            askDefaultHome = store.askForDefaultHome()
            if (updateDefaultHome()) refreshCatalogue()
        }
    }

    override fun onStop() {
        if (callbacksRegistered) {
            directory.launcherApps.unregisterCallback(packageChanges)
            callbacksRegistered = false
        }
        if (profileReceiverRegistered) {
            unregisterReceiver(profileChanges)
            profileReceiverRegistered = false
        }
        catalogueJob?.cancel()
        usageRefresh?.cancel()
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) releasePendingWidget()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(DEFAULT_HOME_OFFERED, defaultHomeOpening.offered)
        pendingWidget?.let {
            outState.putInt(WIDGET_ID, it.id)
            outState.putString(WIDGET_PROVIDER, it.provider.flattenToString())
            outState.putBoolean(WIDGET_WIDE, it.wide)
        }
    }

    private fun neutralHomeIntent() = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME).setComponent(componentName)

    private fun updateDefaultHome(): Boolean {
        val previous = defaultHome
        defaultHome = readDefaultHome(this)
        return previous != defaultHome
    }

    private fun checkDefaultHome() {
        if (updateDefaultHome()) refreshCatalogue()
        message(launcherDefaultHomeStatus(defaultHome))
    }

    private fun setAskForDefaultHome(ask: Boolean) {
        if (store.setAskForDefaultHome(ask)) askDefaultHome = ask
        else message("The default launcher reminder setting could not be saved.")
    }

    private fun canShowDefaultHomePrompt(): Boolean = homeTopResumed && !launcherProtected && sanctuaryStatus == null &&
        activeUtility == null && blocked == null && !review && !sanctuaryOpen && picker == null && renaming == null

    private fun claimDefaultHomePrompt(): Boolean {
        val safeHome = canShowDefaultHomePrompt() && hasWindowFocus() && !isFinishing &&
            runCatching { sanctuary.refresh() == null }.getOrDefault(false)
        if (!safeHome) return false
        askDefaultHome = store.askForDefaultHome()
        if (updateDefaultHome()) refreshCatalogue()
        return defaultHomeOpening.claim(defaultHome, askDefaultHome, safeHome)
    }

    private fun requestDefaultHome() {
        if (!canShowDefaultHomePrompt() || isFinishing ||
            !runCatching { sanctuary.refresh() == null }.getOrDefault(false)
        ) return
        if (updateDefaultHome()) refreshCatalogue()
        if (defaultHome == true) return
        val requested = requestDefaultHomeRole(this) { defaultHomeRequest.launch(it) }
        if (!requested) openHomeSettings()
    }

    private fun refreshCatalogue() {
        catalogueJob?.cancel()
        val previous = rawApps
        val added = pendingAddedPackages.toSet()
        val observedAt = System.currentTimeMillis()
        catalogueJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val discovery = runCatching { directory.apps() }
                    val installed = discovery.getOrElse {
                        previous.filter { LauncherBuiltIn.find(packageName, it.component) == null }
                    }
                    val builtIns = LauncherBuiltIn.entries.map { it.app(packageName) }
                    val session = sanctuary.refresh()
                    val policy = SanctuaryLauncherPolicy.appDecisions(applicationContext, installed, allowEnrollment = discovery.isSuccess) +
                        LauncherBuiltIn.entries.associate { it.app(packageName).component to it.decision(session) }
                    val availableShortcuts = if (SanctuaryLauncherPolicy.launcherProtectionActive(applicationContext)) emptyList()
                        else directory.shortcuts(installed)
                    Triple(installed + builtIns, policy, availableShortcuts) to discovery.isSuccess
                }.getOrNull()
            }
            if (result != null) {
                val (catalogue, complete) = result
                val (entries, policy, availableShortcuts) = catalogue
                if (complete) {
                    val installed = entries.filter { LauncherBuiltIn.find(packageName, it.component) == null }
                    val addedKeys = installed.filter { app ->
                        (app.packageName to (app.profile?.user ?: android.os.Process.myUserHandle())) in added
                    }.mapTo(mutableSetOf(), LauncherApp::component)
                    store.observeInstalledApps(installed, observedAt, addedKeys)?.let { model = it }
                    pendingAddedPackages.removeAll(added)
                }
                rawApps = entries
                apps = entries.map(identitySettings::label)
                decisions = policy
                refreshUsageIfEligible()
                if (activeUtility?.let { policy[it.app(packageName).component]?.canLaunch != true } == true) {
                    activeUtility = null
                }
                shortcuts = availableShortcuts
            } else {
                shortcuts = emptyList()
                message("Some installed apps could not be refreshed.")
            }
        }
    }

    private fun reloadWorkspaceLayout() {
        val persisted = store.read()
        if (persisted != model) {
            model = persisted
            updateWallpaper()
        }
    }

    private fun save(next: NovaLauncherState): Boolean {
        if (!store.save(next)) {
            message("The launcher layout could not be saved.")
            return false
        }
        model = next
        updateWallpaper()
        return true
    }

    private fun updateWallpaper() {
        if (model.appearance.wallpaper == LauncherWallpaper.DEVICE) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.rgb(7, 17, 31)))
        }
    }

    private fun launchApp(component: String) {
        val target = apps.firstOrNull { it.component == component } ?: return
        val builtIn = LauncherBuiltIn.find(packageName, component)
        val allowed = runCatching {
            builtIn?.decision(sanctuary.refresh())?.canLaunch
                ?: SanctuaryLauncherPolicy.appDecision(this, target).canLaunch
        }.getOrNull()
        if (allowed == null) {
            message("Sanctuary could not verify this app. Use Android Settings for recovery.")
            return
        }
        if (!allowed) {
            blocked = target
            return
        }
        if (builtIn != null) {
            when (builtIn) {
                LauncherBuiltIn.SANCTUARY -> openSanctuary()
                LauncherBuiltIn.PRIMARY_GALLERY -> startActivity(privateWorkspaceEntryIntent(this, primaryGallery = true))
                else -> activeUtility = builtIn
            }
        } else if (directory.start(target)) recordLauncherSuccess(target)
        else {
            message("This app is no longer available.")
            refreshCatalogue()
        }
    }

    private fun launchShortcut(shortcut: LauncherShortcut) {
        val allowed = runCatching {
            !SanctuaryLauncherPolicy.launcherProtectionActive(this) &&
                SanctuaryLauncherPolicy.decision(this, shortcut.packageName).canLaunch
        }.getOrDefault(false)
        if (shortcut !in shortcuts || !allowed) {
            message("This shortcut is unavailable during Sanctuary protection.")
            return
        }
        if (directory.start(shortcut)) {
            apps.firstOrNull { it.component == shortcut.activityComponent }?.let(::recordLauncherSuccess)
        } else message("This app shortcut is no longer available.")
    }

    private fun openUsageAccess(intent: Intent) {
        if (!isTrackingSafe()) {
            tracking.cancelExtendedConsent()
            message("Usage Access settings are available outside Sanctuary protection.")
            return
        }
        val systemActivity = packageManager.resolveActivity(intent, PackageManager.MATCH_SYSTEM_ONLY)?.activityInfo
        if (systemActivity != null && runCatching {
                usageAccess.launch(intent.setComponent(ComponentName(systemActivity.packageName, systemActivity.name)))
            }.isSuccess) return

        tracking.cancelExtendedConsent()
        message("Android Usage Access settings are unavailable.")
    }

    private fun recordLauncherSuccess(app: LauncherApp) {
        val allowedFriendsLaunch = app.packageName == "com.whatsapp" && app.profileSerial != null &&
            trackingDeviceUnlocked() && runCatching {
                SanctuaryLauncherPolicy.appDecision(this, app) == SanctuaryLauncherPolicy.Decision.APPROVED
            }.getOrDefault(false)
        if (isTrackingSafe() || allowedFriendsLaunch) {
            tracking.recordLaunch(app)
            val smart = tracking.state.value
            if (smart.mode != LauncherTrackingMode.OFF && app.component !in smart.excluded) {
                save(model.withSuccessfulLaunch(app.component))
            }
        }
    }

    private fun trackingDeviceUnlocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.let { !it.isDeviceLocked && !it.isKeyguardLocked } == true

    private fun isTrackingSafe(): Boolean =
        trackingDeviceUnlocked() && runCatching { sanctuary.refresh() == null }.getOrDefault(false)

    private fun refreshUsageIfEligible() {
        if (!pendingUsageRefresh || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        pendingUsageRefresh = false
        if (!isTrackingSafe()) {
            tracking.suspendExtended()
            return
        }
        val installed = rawApps.filter { it.packageName != packageName }
        usageRefresh?.cancel()
        usageRefresh = lifecycleScope.launch { tracking.refreshExtended(installed) }
    }

    private fun offerSmartTemplate() {
        if (!model.configured) return
        val displays = if (model.separateDisplays) listOf(false, true) else listOf(false)
        for (inner in displays) {
            if (identitySettings.templateHandled(inner)) continue
            val layout = model.layout(inner)
            val next = if (model.widgets.any { it.wide == inner && it.page == 1 }) layout else
                layout.withSmartFolderTemplate(onlyIfEmpty = true,
                    capacity = model.appearance.grid.columns(inner) * model.appearance.grid.rows)
            if (next == layout || save(model.withLayout(inner, next))) identitySettings.markTemplateHandled(inner)
        }
    }

    @Composable
    private fun RenameLauncherAppDialog() {
        val target = renaming ?: return
        var label by remember(target) {
            mutableStateOf(identitySettings.label(target).label.takeUnless { it == target.label }.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename launcher shortcut") },
            text = {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(LauncherIdentitySettings.MAX_LABEL) },
                    label = { Text(target.label) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (identitySettings.saveLabel(target.component, label)) {
                        apps = rawApps.map(identitySettings::label)
                        renaming = null
                    } else message("The shortcut name could not be saved.")
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    private fun openWorkspace() {
        startActivity(privateWorkspaceEntryIntent(this))
    }

    private fun openHomeSettings() = launcherExit.request()

    private fun openAndroidHomeSettings() {
        if (!SanctuaryLauncherPolicy.openHomeSettings(this)) {
            message("Open Settings from Android Quick Settings, then Apps and Default apps to change your Home app.")
        }
    }

    private fun openEmergencyPhone() {
        if (!SanctuaryLauncherPolicy.openEmergencyPhone(this)) message("Use Android's lock-screen Emergency call option.")
    }

    private fun sanctuarySnapshot(session: SanctuarySession): LauncherSanctuaryStatus? {
        val remaining = sanctuary.remainingMillis(session)
        if (remaining <= 0L) return null
        return LauncherSanctuaryStatus(session.mode, remaining, SanctuaryLauncherPolicy.isPaused(session))
    }

    private fun openSanctuary() {
        review = false
        blocked = null
        sanctuaryOpen = true
    }

    private fun startSanctuary(mode: SanctuaryMode) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!sanctuary.start(mode)) {
            message("A Sanctuary session could not be started.")
            return
        }
        SanctuaryGuardian.start(applicationContext)
        SanctuaryDnd.start(applicationContext)
        SanctuaryLauncherPolicy.signalPolicyChange()
    }

    private fun chooseDeviceWallpaper() {
        if (SanctuaryLauncherPolicy.launcherProtectionActive(this)) {
            message("Device wallpaper selection is available outside Sanctuary. Launcher colours are still editable.")
        } else if (!tryStartSystem(Intent(Intent.ACTION_SET_WALLPAPER))) {
            message("Open Android Settings to change the device wallpaper.")
        }
    }

    private fun openAppInfo(component: String) {
        if (LauncherBuiltIn.find(packageName, component) != null) return
        val target = apps.firstOrNull { it.component == component } ?: return
        if (runCatching { SanctuaryLauncherPolicy.launcherProtectionActive(this) }.getOrDefault(true)) {
            message("App info is available outside Sanctuary protection.")
            return
        }
        if (!directory.openAppInfo(target)) message("App info is no longer available.")
    }

    @Composable
    private fun SanctuaryDialogs() {
        if (sanctuaryOpen) {
            LauncherSanctuaryDialog(
                status = sanctuaryStatus,
                modeMinutes = SanctuaryMode.entries.associateWith { sanctuary.preset(it).minutes },
                guardianStatus = SanctuaryGuardian.status(this),
                onStart = ::startSanctuary,
                onReview = { sanctuaryOpen = false; review = true },
                onPhone = { sanctuaryOpen = false; openEmergencyPhone() },
                onManage = { sanctuaryOpen = false; openWorkspace() },
                onDismiss = { sanctuaryOpen = false },
            )
        }
        blocked?.let { target ->
            AlertDialog(
                onDismissRequest = { blocked = null },
                title = { Text("Sanctuary protection") },
                text = { Text(if (appVisibility.hideBlockedApps || appVisibility.hideFromSearch ||
                    appVisibility.hideFromSuggestions || appVisibility.hideFromRecentlyUsed)
                    "This app is blocked by the active Sanctuary rules."
                    else "${target.label} is blocked by the active Sanctuary rules.") },
                confirmButton = {
                    TextButton(onClick = { blocked = null }) { Text("Back to Nova Launcher") }
                },
            )
        }
        if (review) {
            val mode = sanctuaryStatus?.mode
            val labels = apps.filter { it.profile == null }.associate { it.packageName to it.label }
            val current = sanctuary.current.value
            val modes = if (mode != null) listOf(mode) else SanctuaryMode.entries
            AlertDialog(
                onDismissRequest = { review = false },
                title = { Text("Sanctuary approved apps") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        item { Text("Nova Launcher uses the same app rules as Sanctuary. Emergency and Android recovery access remain available.") }
                        if (launcherProtected) {
                            item { Text("Open Sanctuary in NovaOS to review the authenticated app rules.") }
                        }
                        modes.filter { !launcherProtected }.forEach { preset ->
                            item { Text(preset.label, style = MaterialTheme.typography.titleSmall) }
                            val settings = sanctuary.preset(preset)
                            val active = current?.takeIf { it.mode == preset }
                            val packages = when {
                                !(active?.allowExternalApps ?: settings.allowExternalApps) -> emptySet()
                                preset == SanctuaryMode.PANIC -> panicSanctuaryPersonalApps
                                else -> active?.androidApps ?: settings.androidApps
                            }
                            if (packages.isEmpty()) item { Text("No additional apps approved") }
                            items(packages.sorted()) { Text(labels[it] ?: it) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { review = false; openWorkspace() }) { Text("Manage in NovaOS") } },
                dismissButton = { TextButton(onClick = { review = false }) { Text("Done") } },
            )
        }
    }

    private fun pickWidget(wide: Boolean) {
        if (SanctuaryLauncherPolicy.launcherProtectionActive(this)) {
            message("Widget actions are paused during Sanctuary.")
            return
        }
        if (model.widgets.size >= MAX_LAUNCHER_WIDGETS || pendingWidget != null) {
            message("Remove a widget or finish the open widget request.")
            return
        }
        lifecycleScope.launch {
            val installed = withContext(Dispatchers.IO) {
                runCatching {
                    widgetManager.getInstalledProvidersForProfile(Process.myUserHandle()).filter {
                        it.provider.packageName != packageName &&
                            (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
                    }.sortedBy { it.loadLabel(packageManager).lowercase() }
                }.getOrDefault(emptyList())
            }
            if (isFinishing) return@launch
            if (installed.isEmpty()) message("No Home widgets are available.")
            else picker = WidgetPicker(wide, installed)
        }
    }

    @Composable
    private fun WidgetPickerDialog() {
        val request = picker ?: return
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text("Add an Android widget") },
            text = {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(request.providers, key = { it.provider.flattenToString() }) { provider ->
                        TextButton(onClick = { picker = null; bindWidget(provider, request.wide) }) {
                            Text(provider.loadLabel(packageManager))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
        )
    }

    internal fun bindWidget(provider: AppWidgetProviderInfo, wide: Boolean) {
        if (provider.profile != Process.myUserHandle() || provider.provider.packageName == packageName ||
            SanctuaryLauncherPolicy.launcherProtectionActive(this) || model.widgets.size >= MAX_LAUNCHER_WIDGETS ||
            pendingWidget != null) return
        val id = runCatching { widgetHost.allocateAppWidgetId() }.getOrNull()
        if (id == null || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            message("The widget host could not allocate a widget.")
            return
        }
        val request = PendingWidget(id, provider.provider, wide)
        pendingWidget = request
        val alreadyAllowed = runCatching { widgetManager.bindAppWidgetIdIfAllowed(id, provider.provider) }.getOrDefault(false)
        if (alreadyAllowed) configureOrSaveWidget(request)
        else if (runCatching {
                val consent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                widgetBind.launch(systemIntent(consent))
            }.isFailure) {
            releasePendingWidget()
            message("Android could not open the widget permission prompt.")
        }
    }

    private fun configureOrSaveWidget(request: PendingWidget) {
        val provider = validWidgetInfo(request.id, request.provider)
        if (provider == null || SanctuaryLauncherPolicy.launcherProtectionActive(this)) {
            releasePendingWidget()
            return
        }
        if (provider.configure == null) saveWidget(request)
        else if (runCatching {
                widgetHost.startAppWidgetConfigureActivityForResult(this, request.id, 0, CONFIGURE_WIDGET, null)
            }.isFailure) {
            releasePendingWidget()
            message("This widget's configuration screen is unavailable.")
        }
    }

    @Deprecated("AppWidgetHost delivers configuration through the framework Activity result.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CONFIGURE_WIDGET) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) saveWidget(pending) else releasePendingWidget()
    }

    private fun saveWidget(request: PendingWidget) {
        if (validWidgetInfo(request.id, request.provider) == null ||
            SanctuaryLauncherPolicy.launcherProtectionActive(this) ||
            !save(model.copy(widgets = model.widgets + LauncherWidget(request.id, request.provider.flattenToString(), request.wide)))) {
            releasePendingWidget()
        } else pendingWidget = null
    }

    private fun removeWidget(widget: LauncherWidget) {
        if (widget !in model.widgets) return
        if (save(model.copy(widgets = model.widgets - widget))) releaseWidgetId(widget.id)
    }

    private fun releasePendingWidget() {
        pendingWidget?.let { releaseWidgetId(it.id) }
        pendingWidget = null
    }

    private fun releaseWidgetId(id: Int) {
        runCatching { widgetHost.deleteAppWidgetId(id) }
    }

    private fun reconcileWidgetIds() {
        val owned = runCatching { widgetHost.appWidgetIds.toSet() }.getOrNull() ?: return
        val placed = model.widgets.mapTo(mutableSetOf()) { it.id }
        pendingWidget?.let { placed += it.id }
        (owned - placed).forEach(::releaseWidgetId)
    }

    private fun validWidgetInfo(id: Int, component: ComponentName): AppWidgetProviderInfo? = runCatching {
        if (component.packageName == packageName || id !in widgetHost.appWidgetIds) return null
        widgetManager.getAppWidgetInfo(id)?.takeIf { it.provider == component && it.profile == Process.myUserHandle() }
    }.getOrNull()

    @Composable
    private fun WidgetView(widget: LauncherWidget, modifier: Modifier) {
        if (launcherProtected) {
            Box(modifier) { Text("Widget actions are paused during Sanctuary.") }
            return
        }
        val provider = ComponentName.unflattenFromString(widget.provider)
            ?.let { validWidgetInfo(widget.id, it) }
        if (provider == null) {
            Box(modifier) { Text("Widget unavailable") }
            return
        }
        val density = LocalDensity.current.density
        var size by remember(widget.id) { mutableStateOf(IntSize.Zero) }
        AndroidView(
            factory = { context ->
                GuardedWidgetFrame(context, widget.id, provider).also { widgetFrames += it }
            },
            modifier = modifier.fillMaxWidth().onSizeChanged { size = it },
            update = { frame ->
                if (size.width > 0 && size.height > 0) {
                    frame.resize(SizeF(size.width / density, size.height / density))
                }
            },
            onReset = null,
            onRelease = { frame -> widgetFrames -= frame; frame.disableContent() },
        )
    }

    /** Widget PendingIntent destinations are opaque. Detach remote content on policy changes
     * and recheck on dispatch so queued Compose work cannot open a stale remote click target. */
    private inner class GuardedWidgetFrame(
        context: android.content.Context,
        id: Int,
        provider: AppWidgetProviderInfo,
    ) : FrameLayout(context) {
        private val widget = runCatching { widgetHost.createView(context, id, provider) }.getOrNull()
        private var dimensions: SizeF? = null

        init {
            if (SanctuaryLauncherPolicy.launcherProtectionActive(context)) disableContent()
            else if (widget != null) addView(widget, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        fun disableContent() {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (childCount != 0) removeAllViews()
        }

        fun resize(size: SizeF) {
            if (dimensions == size || childCount == 0) return
            dimensions = size
            runCatching { widget?.updateAppWidgetSize(Bundle(), listOf(size)) }
        }

        private fun intercept(): Boolean = SanctuaryLauncherPolicy.launcherProtectionActive(context).also { active ->
            if (active) disableContent()
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean =
            intercept() || super.dispatchTouchEvent(event)

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            intercept() || super.dispatchKeyEvent(event)

        override fun onRequestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean =
            !intercept() && super.onRequestSendAccessibilityEvent(child, event)
    }

    private fun systemIntent(intent: Intent): Intent {
        val handler = checkNotNull(packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
        ).firstOrNull()?.activityInfo)
        return intent.setComponent(ComponentName(handler.packageName, handler.name))
    }

    private fun tryStartSystem(intent: Intent) = runCatching { startActivity(systemIntent(intent)) }.isSuccess
    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    companion object {
        internal const val WIDGET_HOST_ID = 70_001
        private const val CONFIGURE_WIDGET = 7_101
        private const val DEFAULT_HOME_OFFERED = "nova-launcher-default-home-offered"
        private const val WIDGET_ID = "nova-launcher-pending-widget"
        private const val WIDGET_PROVIDER = "nova-launcher-pending-provider"
        private const val WIDGET_WIDE = "nova-launcher-pending-wide"
    }
}
