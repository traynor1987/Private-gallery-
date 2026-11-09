package uk.co.traynor.privategallery.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryDnd
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.privateWorkspaceEntryIntent
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

/** The Android Home task reads only public app metadata, launcher storage and Sanctuary policy. */
class NovaLauncherActivity : ComponentActivity() {
    private val store by lazy { NovaLauncherStore(this) }
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
    private var defaultHome by mutableStateOf(false)
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

    private val packageChanges = object : LauncherApps.Callback() {
        private fun changed(user: UserHandle) {
            if (user == directory.user) refreshCatalogue()
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(user)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(user)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(user)
        override fun onShortcutsChanged(packageName: String, shortcuts: List<android.content.pm.ShortcutInfo>, user: UserHandle) =
            changed(user)
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
        setIntent(neutralHomeIntent())
        enableEdgeToEdge()
        model = store.read()
        launcherProtected = SanctuaryLauncherPolicy.launcherProtectionActive(this)
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
                        refreshProtection()
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
            save = { next -> save(next.copy(launchCounts = model.launchCounts)) },
            launchApp = ::launchApp,
            launchShortcut = ::launchShortcut,
            openWorkspace = ::openWorkspace,
            selectHome = ::openHomeSettings,
            continueWithCurrentHome = { if (defaultHome) openHomeSettings() else finish() },
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
        )
        setContent {
            val utility = activeUtility
            val utilityAllowed = utility != null && decisions[utility.app(packageName).packageName]?.canLaunch == true
            if (!utilityAllowed) {
                NovaLauncherScreen(model, apps, if (launcherProtected) emptyList() else shortcuts, decisions,
                    defaultHome, outsideProtection, actions, homeGeneration, launcherProtected, sanctuaryStatus)
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
                WidgetPickerDialog()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(neutralHomeIntent())
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
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
        refreshCatalogue()
    }

    override fun onResume() {
        super.onResume()
        refreshProtection()
        val previousDefault = defaultHome
        updateDefaultHome()
        if (defaultHome != previousDefault) refreshCatalogue()
    }

    private fun refreshProtection() {
        launcherProtected = SanctuaryLauncherPolicy.launcherProtectionActive(this)
        if (launcherProtected) {
            picker = null
            widgetFrames.forEach(GuardedWidgetFrame::disableContent)
        }
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity) SanctuaryLauncherPolicy.launcherResumed(this)
        else SanctuaryLauncherPolicy.launcherPaused()
    }

    override fun onStop() {
        if (callbacksRegistered) {
            directory.launcherApps.unregisterCallback(packageChanges)
            callbacksRegistered = false
        }
        catalogueJob?.cancel()
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) releasePendingWidget()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingWidget?.let {
            outState.putInt(WIDGET_ID, it.id)
            outState.putString(WIDGET_PROVIDER, it.provider.flattenToString())
            outState.putBoolean(WIDGET_WIDE, it.wide)
        }
    }

    private fun neutralHomeIntent() = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME).setComponent(componentName)

    private fun updateDefaultHome() {
        defaultHome = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }

    private fun refreshCatalogue() {
        catalogueJob?.cancel()
        val previous = apps
        catalogueJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val installed = runCatching { directory.apps() }.getOrElse {
                        previous.filter { LauncherBuiltIn.find(packageName, it.component) == null }
                    }
                    val builtIns = LauncherBuiltIn.entries.map { it.app(packageName) }
                    val session = sanctuary.refresh()
                    val policy = SanctuaryLauncherPolicy.decisions(applicationContext, installed.map { it.packageName }) +
                        LauncherBuiltIn.entries.associate { it.app(packageName).packageName to it.decision(session) }
                    val availableShortcuts = if (SanctuaryLauncherPolicy.launcherProtectionActive(applicationContext)) emptyList()
                        else directory.shortcuts(installed)
                    Triple(installed + builtIns, policy, availableShortcuts)
                }.getOrNull()
            }
            if (result != null) {
                val (entries, policy, availableShortcuts) = result
                apps = entries
                decisions = policy
                if (activeUtility?.let { policy[it.app(packageName).packageName]?.canLaunch != true } == true) {
                    activeUtility = null
                }
                shortcuts = availableShortcuts
            } else {
                shortcuts = emptyList()
                message("Some installed apps could not be refreshed.")
            }
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
                ?: SanctuaryLauncherPolicy.decision(this, target.packageName).canLaunch
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
            save(model.withSuccessfulLaunch(component))
        } else if (directory.start(target)) save(model.withSuccessfulLaunch(component))
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
        if (directory.start(shortcut)) shortcut.activityComponent?.let { save(model.withSuccessfulLaunch(it)) }
        else message("This app shortcut is no longer available.")
    }

    private fun openWorkspace() {
        startActivity(privateWorkspaceEntryIntent(this))
    }

    private fun openHomeSettings() {
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
                text = { Text("${target.label} is blocked by the active Sanctuary rules.") },
                confirmButton = {
                    TextButton(onClick = { blocked = null }) { Text("Back to Nova Launcher") }
                },
            )
        }
        if (review) {
            val mode = sanctuaryStatus?.mode
            val labels = apps.associate { it.packageName to it.label }
            val current = sanctuary.current.value
            val modes = if (mode != null) listOf(mode) else SanctuaryMode.entries
            AlertDialog(
                onDismissRequest = { review = false },
                title = { Text("Sanctuary approved apps") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        item { Text("Nova Launcher uses the same app rules as Sanctuary. Emergency and Android recovery access remain available.") }
                        modes.forEach { preset ->
                            item { Text(preset.label, style = MaterialTheme.typography.titleSmall) }
                            val packages = if (current?.mode == preset) {
                                if (current.allowExternalApps) current.androidApps else emptySet()
                            } else sanctuary.preset(preset).let { if (it.allowExternalApps) it.androidApps else emptySet() }
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
        private const val WIDGET_ID = "nova-launcher-pending-widget"
        private const val WIDGET_PROVIDER = "nova-launcher-pending-provider"
        private const val WIDGET_WIDE = "nova-launcher-pending-wide"
    }
}
