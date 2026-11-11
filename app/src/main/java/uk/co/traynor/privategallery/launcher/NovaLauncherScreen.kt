package uk.co.traynor.privategallery.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.R
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryAppVisibility
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.home2.Wallpaper
import uk.co.traynor.privategallery.home2.WallpaperSurface
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

internal const val LauncherExpandedWidth = 600

/** Android launcher presentation. Only the platform callbacks can launch apps or enter NovaOS. */
@Composable
internal fun NovaLauncherScreen(
    state: NovaLauncherState,
    apps: List<LauncherApp>,
    shortcuts: List<LauncherShortcut>,
    decisions: Map<String, Decision>,
    isDefaultHome: Boolean,
    outsideProtection: Boolean,
    actions: LauncherUiActions,
    homeGeneration: Int = 0,
    launcherProtected: Boolean = false,
    sanctuaryStatus: LauncherSanctuaryStatus? = null,
    smartState: LauncherSmartState? = null,
    smartSettings: (@Composable (List<LauncherApp>) -> Unit)? = null,
    defaultHomeUi: LauncherDefaultHomeUi? = null,
    smartFolderPresentation: LauncherSmartFolderPresentation = LauncherSmartFolderPresentation.AUTOMATIC,
    appVisibility: SanctuaryAppVisibility = SanctuaryAppVisibility(hideBlockedApps = state.hideBlocked,
        hideFromSearch = state.hideBlocked, hideFromSuggestions = state.hideBlocked, hideFromRecentlyUsed = state.hideBlocked),
) {
    val theme = when (state.appearance.theme) {
        LauncherTheme.SYSTEM -> AppTheme.SYSTEM
        LauncherTheme.LIGHT -> AppTheme.LIGHT
        LauncherTheme.DARK -> AppTheme.DARK
    }
    PrivateGalleryTheme(theme) {
        if (!state.configured) {
            val eligible = if (launcherProtected && appVisibility.hideBlockedApps)
                apps.filter { decisions.forLauncherApp(it)?.canLaunch == true } else apps
            LauncherSetup(state, eligible, decisions, actions, isDefaultHome, outsideProtection, launcherProtected)
        } else {
            LauncherRuntime(state, apps, shortcuts, decisions, actions, isDefaultHome, outsideProtection,
                launcherProtected, homeGeneration, sanctuaryStatus, smartState, smartSettings, defaultHomeUi, smartFolderPresentation, appVisibility)
        }
    }
}

private enum class LauncherSurface { HOME, DRAWER, LIBRARY, SETTINGS, GUARDIAN_SETTINGS, GUARDIAN_DIAGNOSTICS, LIBRARY_SETTINGS, EDIT }
private enum class LauncherLibraryEntry { PAGER, DRAWER }

private val openSmartFolderSaver = listSaver<LauncherItem.SmartFolder?, String>(
    save = { folder -> folder?.let { listOf(it.category.name, it.customName.orEmpty()) + it.included }.orEmpty() },
    restore = { fields -> fields.firstOrNull()?.let { category ->
        LauncherItem.SmartFolder(LauncherCategory.valueOf(category), fields[1].ifEmpty { null }, fields.drop(2))
    } },
)

@Composable
private fun LauncherRuntime(
    state: NovaLauncherState,
    apps: List<LauncherApp>,
    shortcuts: List<LauncherShortcut>,
    policy: Map<String, Decision>,
    actions: LauncherUiActions,
    isDefaultHome: Boolean,
    outsideProtection: Boolean,
    launcherProtected: Boolean,
    homeGeneration: Int,
    sanctuaryStatus: LauncherSanctuaryStatus?,
    smartState: LauncherSmartState?,
    smartSettings: (@Composable (List<LauncherApp>) -> Unit)?,
    defaultHomeUi: LauncherDefaultHomeUi?,
    smartFolderPresentation: LauncherSmartFolderPresentation,
    appVisibility: SanctuaryAppVisibility,
) {
    val guardianContext = LocalContext.current
    var guardianOptions by remember(guardianContext) { mutableStateOf(GuardianHomeOptions.read(guardianContext)) }
    var coverGuardian by rememberSaveable(homeGeneration) { mutableStateOf(false) }
    var innerGuardian by rememberSaveable(homeGeneration) { mutableStateOf(false) }
    var surface by rememberSaveable(homeGeneration) { mutableStateOf(LauncherSurface.HOME) }
    var animateDockOnReturn by remember(homeGeneration) { mutableStateOf(false) }
    fun showHome(animateDock: Boolean = false) {
        animateDockOnReturn = animateDock
        surface = LauncherSurface.HOME
    }
    var query by rememberSaveable(homeGeneration) { mutableStateOf("") }
    var coverPage by rememberSaveable(homeGeneration) { mutableIntStateOf(0) }
    var innerPage by rememberSaveable(homeGeneration) { mutableIntStateOf(0) }
    var coverLibrary by rememberSaveable(homeGeneration) { mutableStateOf<LauncherLibraryEntry?>(null) }
    var innerLibrary by rememberSaveable(homeGeneration) { mutableStateOf<LauncherLibraryEntry?>(null) }
    var coverLibraryQuery by rememberSaveable(homeGeneration) { mutableStateOf("") }
    var innerLibraryQuery by rememberSaveable(homeGeneration) { mutableStateOf("") }
    var coverLibraryCategory by rememberSaveable(homeGeneration) { mutableStateOf<LauncherCategory?>(null) }
    var innerLibraryCategory by rememberSaveable(homeGeneration) { mutableStateOf<LauncherCategory?>(null) }
    var searchRequested by rememberSaveable(homeGeneration) { mutableStateOf(false) }
    var folder by remember(homeGeneration) { mutableStateOf<LauncherItem.Folder?>(null) }
    val appsByComponent = remember(apps) { apps.associateBy { it.component } }
    val decisions = remember(apps, policy, launcherProtected) {
        if (!launcherProtected) policy else policy + apps.filter { policy.forLauncherApp(it) == null }
            .associate { it.component to Decision.UNAPPROVED }
    }
    fun approved(app: LauncherApp) = decisions.forLauncherApp(app)?.canLaunch == true
    fun canDisplay(component: String, hide: Boolean = appVisibility.hideBlockedApps): Boolean =
        appsByComponent[component]?.let { !launcherProtected || !hide || approved(it) } == true

    var category by rememberSaveable(homeGeneration) { mutableStateOf<LauncherCategory?>(null) }
    var homeFolder by rememberSaveable(homeGeneration, stateSaver = openSmartFolderSaver) {
        mutableStateOf<LauncherItem.SmartFolder?>(null)
    }
    var editingFolder by remember(homeGeneration) {
        mutableStateOf<Pair<LauncherSlot, LauncherItem.SmartFolder>?>(null)
    }
    val visibleApps = remember(apps, launcherProtected, appVisibility, decisions) {
        apps.filter { canDisplay(it.component) }
    }
    val searchApps = remember(apps, launcherProtected, appVisibility, decisions) {
        apps.filter { canDisplay(it.component, appVisibility.hideFromSearch) }
    }
    val smartApps = remember(apps, launcherProtected, decisions) {
        apps.filter { !launcherProtected || approved(it) }
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val identitySettings = remember(context) { LauncherIdentitySettings(context) }
    var libraryPins by remember(identitySettings) { mutableStateOf(identitySettings.libraryPins()) }
    var categoryOverrides by remember(identitySettings) { mutableStateOf(identitySettings.categoryOverrides()) }
    var categoryEditor by remember(homeGeneration) { mutableStateOf<LauncherApp?>(null) }
    val groups = remember(visibleApps, smartApps, smartState, launcherProtected, categoryOverrides,
        appVisibility, apps, decisions, state.launchCounts) {
        val suggestions = apps.filter { canDisplay(it.component, appVisibility.hideFromSuggestions) }
        val recents = apps.filter { canDisplay(it.component, appVisibility.hideFromRecentlyUsed) }
        launcherLibraryGroups(
            visibleApps,
            if (smartState?.showSuggestions == true) smartState.suggestedApps(suggestions) else emptyList(),
            smartState?.recentApps(recents).orEmpty(),
            categoryOverrides = categoryOverrides,
            suggestionCatalog = suggestions,
            recentCatalog = recents,
            launchCounts = if (smartState?.mode == LauncherTrackingMode.OFF) emptyMap()
                else state.launchCounts - smartState?.excluded.orEmpty(),
        )
    }
    fun members(folder: LauncherItem.SmartFolder): List<LauncherApp> {
        val showPanicLocks = launcherProtected && sanctuaryStatus?.mode == SanctuaryMode.PANIC &&
            !appVisibility.hideBlockedApps
        val candidates = if (showPanicLocks) visibleApps else smartApps
        val allowed = candidates.associateBy { it.component }
        val automatic = groups.firstOrNull { it.category == folder.category }?.apps.orEmpty()
        val pinned = if (folder.category == LauncherCategory.RECENTLY_USED)
            smartState?.pinnedApps(candidates).orEmpty()
        else emptyList()
        return (automatic.filter { it.component in allowed } + pinned + folder.included.mapNotNull(allowed::get))
            .distinctBy(LauncherApp::component)
    }
    fun openDrawer(search: Boolean = false) {
        homeFolder = null
        category = null
        query = ""
        searchRequested = search
        surface = LauncherSurface.DRAWER
    }
    fun closeLibrary() {
        query = ""
        when {
            homeFolder != null -> { homeFolder = null; category = null; showHome() }
            category != null -> category = null
            else -> openDrawer()
        }
    }
    fun open(item: LauncherItem) {
        when (item) {
            is LauncherItem.App -> actions.launchApp(item.component)
            is LauncherItem.Folder -> folder = item
            is LauncherItem.SmartFolder -> {
                homeFolder = item
                category = item.category
                query = ""
                surface = LauncherSurface.LIBRARY
            }
            LauncherItem.Workspace -> actions.openWorkspace()
        }
    }
    BackHandler(surface != LauncherSurface.HOME) {
        when (surface) {
            LauncherSurface.LIBRARY -> closeLibrary()
            LauncherSurface.LIBRARY_SETTINGS, LauncherSurface.GUARDIAN_SETTINGS -> surface = LauncherSurface.SETTINGS
            LauncherSurface.GUARDIAN_DIAGNOSTICS -> showHome()
            else -> { showHome(animateDock = surface == LauncherSurface.DRAWER); query = "" }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= LauncherExpandedWidth.dp
        val layout = state.layout(wide)
        val isInner = wide && state.separateDisplays
        val guardianSelected = guardianOptions.showPage && if (isInner) innerGuardian else coverGuardian
        fun selectGuardian(selected: Boolean) {
            if (isInner) innerGuardian = selected else coverGuardian = selected
        }
        val currentPage = (if (isInner) innerPage else coverPage).coerceIn(0, layout.pages.lastIndex.coerceAtLeast(0))
        val updatePage: (Int) -> Unit = { if (isInner) innerPage = it else coverPage = it }
        val libraryEntry = if (isInner) innerLibrary else coverLibrary
        val librarySelected = libraryEntry != null
        val libraryQuery = if (isInner) innerLibraryQuery else coverLibraryQuery
        val libraryCategory = if (isInner) innerLibraryCategory else coverLibraryCategory
        fun setLibraryQuery(value: String) { if (isInner) innerLibraryQuery = value else coverLibraryQuery = value }
        fun setLibraryCategory(value: LauncherCategory?) {
            if (isInner) innerLibraryCategory = value else coverLibraryCategory = value
        }
        fun selectLibrary(entry: LauncherLibraryEntry?) {
            if (isInner) innerLibrary = entry else coverLibrary = entry
            if (entry == null) {
                setLibraryCategory(null)
                setLibraryQuery("")
            }
        }
        fun handleLibraryBack(): Boolean = when {
            libraryQuery.isNotEmpty() -> { setLibraryQuery(""); true }
            libraryCategory != null -> { setLibraryCategory(null); true }
            libraryEntry == LauncherLibraryEntry.DRAWER -> {
                selectLibrary(null)
                openDrawer()
                true
            }
            else -> false
        }
        fun edit(next: LauncherLayout) {
            if (!state.dockOptions.locked || next.dock == layout.dock) actions.save(state.withLayout(wide, next))
        }

        val screenWidth = constraints.maxWidth
        val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
        var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val gestures = remember(screenWidth, isInner, homeGeneration, rightToLeft) {
            LauncherIconGestures(rightToLeft) { rootCoordinates }
        }
        var dragPageRequest by remember(gestures) { mutableStateOf<Int?>(null) }
        fun pageForDrop(): Int? = currentPage.takeIf {
            !librarySelected && !guardianSelected && (dragPageRequest == null || dragPageRequest == it)
        }
        val rowTolerance = with(LocalDensity.current) { 8.dp.toPx() }
        val edgeWidth = with(LocalDensity.current) { 34.dp.toPx() }
        LaunchedEffect(launcherProtected) {
            if (launcherProtected) {
                gestures.cancelDrag()
                gestures.dismissMenu()
                categoryEditor = null
                editingFolder = null
                query = ""
                coverLibraryQuery = ""
                innerLibraryQuery = ""
            }
        }
        val activeDrag = gestures.drag
        LauncherDefaultHomePrompt(
            defaultHomeUi,
            safeSurface = surface == LauncherSurface.HOME && !librarySelected && !guardianSelected && folder == null && editingFolder == null &&
                gestures.menu == null && activeDrag == null,
            actions = actions,
        )
        LaunchedEffect(activeDrag == null) {
            if (activeDrag == null) dragPageRequest = null
        }
        val edge = activeDrag?.position?.let { position ->
            if (surface != LauncherSurface.HOME || librarySelected ||
                position.y !in gestures.pageBounds.top..gestures.pageBounds.bottom) 0
            else when {
                position.x in 0f..edgeWidth -> if (rightToLeft) 1 else -1
                position.x in (screenWidth - edgeWidth)..screenWidth.toFloat() -> if (rightToLeft) -1 else 1
                else -> 0
            }
        } ?: 0
        LaunchedEffect(edge, currentPage, gestures) {
            val next = currentPage + edge
            if (edge != 0 && next in layout.pages.indices) {
                delay(550)
                dragPageRequest = next
            }
        }

        Box(Modifier.fillMaxSize().onGloballyPositioned { rootCoordinates = it }.launcherIconPointer(
            gestures,
            findSource = { position ->
                if (folder != null || gestures.menu != null || (surface == LauncherSurface.HOME && guardianSelected)) null
                else when (surface) {
                    LauncherSurface.HOME -> gestures.sourceAt(position, currentPage, drawer = librarySelected,
                        includeDock = librarySelected && state.dockOptions.showInLibrary && !state.dockOptions.locked)
                    LauncherSurface.DRAWER, LauncherSurface.LIBRARY -> gestures.sourceAt(position, currentPage, drawer = true)
                    else -> null
                }
            },
            startDrag = {
                if (librarySelected || surface == LauncherSurface.DRAWER || surface == LauncherSurface.LIBRARY) {
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                }
                if (librarySelected) {
                    // Keep Library selection and search results until the pager actually reaches Home.
                    dragPageRequest = if (libraryEntry == LauncherLibraryEntry.PAGER) layout.pages.lastIndex else currentPage
                }
                if (surface == LauncherSurface.DRAWER || surface == LauncherSurface.LIBRARY) {
                    homeFolder = null
                    category = null
                    showHome()
                    query = ""
                }
            },
            drop = { position ->
                val origin = gestures.drag?.origin
                if (origin != null && surface == LauncherSurface.HOME) {
                    gestures.targetAt(layout, pageForDrop(), position, rowTolerance)?.let { target ->
                        val next = layout.dropLauncherItem(origin.item, origin.slot, target.drop)
                        if (next != layout) edit(next)
                    }
                }
            },
        )) {
            when (surface) {
                LauncherSurface.HOME -> key(isInner, homeGeneration, guardianOptions.showPage) {
                    LauncherHome(
                        state, layout, wide, currentPage, appsByComponent, decisions, actions, launcherProtected,
                        onPage = updatePage, onDrawer = ::openDrawer,
                        onSettings = { surface = LauncherSurface.SETTINGS },
                        onEdit = { surface = LauncherSurface.EDIT },
                        onItem = ::open,
                        gestures = gestures,
                        dragPageRequest = dragPageRequest.takeIf { activeDrag != null },
                        librarySelected = librarySelected,
                        onLibrarySelected = { selected ->
                            selectLibrary(if (selected) libraryEntry ?: LauncherLibraryEntry.PAGER else null)
                        },
                        onLibraryBack = ::handleLibraryBack,
                        libraryPage = { goBack ->
                            LauncherLibrary(
                                state.appearance, groups, libraryCategory, libraryQuery, ::setLibraryQuery,
                                onCategory = ::setLibraryCategory, onBack = goBack,
                                onDrawer = {
                                    if (libraryEntry == LauncherLibraryEntry.DRAWER) selectLibrary(null)
                                    openDrawer()
                                },
                                onApp = { actions.launchApp(it.component) }, decisions = decisions, gestures = gestures,
                                recentHistoryEnabled = smartState?.mode != LauncherTrackingMode.OFF,
                                pinnedApps = smartState?.pinnedApps(smartApps).orEmpty(),
                                pinnedCategories = libraryPins,
                                searchApps = searchApps,
                                embedded = true,
                            )
                        },
                        showHomeRecents = smartState?.showHomeRecents != false,
                        showSuggestions = smartState?.showSuggestions != false,
                        smartMembers = ::members,
                        smartFolderPresentation = smartFolderPresentation,
                        sanctuaryStatus = sanctuaryStatus,
                        isAppVisible = { canDisplay(it) },
                        animateDockEntrance = animateDockOnReturn,
                        guardianSelected = guardianSelected,
                        onGuardianSelected = ::selectGuardian,
                        guardianPage = if (guardianOptions.showPage) {
                            {
                                GuardianHome(guardianOptions, sanctuaryStatus, apps, decisions,
                                    onSettings = { surface = LauncherSurface.GUARDIAN_SETTINGS },
                                    onSanctuary = actions.openSanctuary,
                                    onDiagnostics = { surface = LauncherSurface.GUARDIAN_DIAGNOSTICS })
                            }
                        } else null,
                    )
                }
                LauncherSurface.DRAWER -> LauncherDrawer(
                    state.copy(launchCounts = if (smartState?.mode == LauncherTrackingMode.OFF) emptyMap()
                        else state.launchCounts - smartState?.excluded.orEmpty()),
                    layout, wide, query, { query = it }, searchRequested,
                    visibleApps, shortcuts, decisions,
                    onBack = { showHome(animateDock = true) },
                    onApp = { actions.launchApp(it.component) },
                    onShortcut = actions.launchShortcut,
                    onFolder = { folder = it },
                    gestures = gestures,
                    onLibrary = {
                        query = ""
                        selectLibrary(LauncherLibraryEntry.DRAWER)
                        showHome()
                    },
                    searchApps = searchApps,
                )
                LauncherSurface.LIBRARY -> {
                    val opened = homeFolder
                    val activeGroups = if (opened == null) groups else listOf(
                        LauncherLibraryGroup(opened.category, members(opened))
                    )
                    LauncherLibrary(
                        state.appearance, activeGroups, category, query, { query = it },
                        onCategory = { category = it }, onBack = ::closeLibrary, onDrawer = { openDrawer() },
                        onApp = { actions.launchApp(it.component) }, decisions = decisions, gestures = gestures,
                        recentHistoryEnabled = smartState?.mode != LauncherTrackingMode.OFF,
                        categoryTitle = opened?.name,
                        pinnedApps = smartState?.pinnedApps(smartApps).orEmpty(),
                        pinnedCategories = libraryPins,
                    )
                }
                LauncherSurface.LIBRARY_SETTINGS -> GuardianTheme {
                    LauncherPanel("App Library", { surface = LauncherSurface.SETTINGS }) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                            smartSettings?.invoke(smartApps)
                            if (!launcherProtected) LauncherLibraryPinSettings(libraryPins) { pins ->
                                if (identitySettings.setLibraryPins(pins)) libraryPins = pins
                            }
                            actions.setSmartFolderPresentation?.let { selectMode ->
                                LauncherSmartFolderModeSetting(smartFolderPresentation, selectMode)
                            }
                            val next = layout.withSmartFolderTemplate(
                                onlyIfEmpty = false,
                                capacity = state.appearance.grid.columns(wide) * state.appearance.grid.rows,
                            )
                            OutlinedButton(enabled = next != layout, onClick = { edit(next) }) {
                                Text("Add or restore smart folders on Home page two")
                            }
                        }
                    }
                }
                LauncherSurface.GUARDIAN_DIAGNOSTICS -> GuardianTheme {
                    NotificationCompatibilityScreen(
                        allowPermissionChanges = !launcherProtected, onBack = { showHome() },
                    )
                }
                LauncherSurface.GUARDIAN_SETTINGS -> GuardianTheme {
                    GuardianHomeSettings(
                        guardianOptions,
                        onChange = {
                            if (!it.showPage) { coverGuardian = false; innerGuardian = false }
                            guardianOptions = it
                            it.save(guardianContext)
                        },
                        onBack = { surface = LauncherSurface.SETTINGS },
                    )
                }
                LauncherSurface.SETTINGS -> GuardianTheme {
                    LauncherSettings(
                        state, decisions, visibleApps, actions, isDefaultHome, outsideProtection, launcherProtected,
                        onBack = { showHome() },
                        onEdit = { surface = LauncherSurface.EDIT },
                        onLibrarySettings = { surface = LauncherSurface.LIBRARY_SETTINGS },
                        onGuardianSettings = { surface = LauncherSurface.GUARDIAN_SETTINGS },
                        defaultHomeUi = defaultHomeUi,
                        wide = wide,
                    )
                }
                LauncherSurface.EDIT -> LauncherLayoutEditor(
                    state, layout, wide, currentPage, apps.filter { canDisplay(it.component) }, decisions,
                    onEdit = ::edit, onPage = updatePage, actions = actions,
                    onBack = { showHome() },
                    protectedBySanctuary = launcherProtected,
                )
            }

            folder?.let { selected ->
                GuardianAlertDialog(
                    onDismissRequest = { folder = null },
                    title = { Text(selected.name) },
                    text = {
                        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                            val members = selected.apps
                                .filter { canDisplay(it) }
                                .mapNotNull(appsByComponent::get)
                            if (members.isEmpty()) Text("No available apps in this folder.")
                            members.forEach { app ->
                                TextButton(onClick = {
                                    folder = null
                                    actions.launchApp(app.component)
                                }) {
                                    LauncherArtworkIcon(app, state.appearance, Modifier.size(38.dp))
                                    Spacer(Modifier.width(10.dp))
                                    val label = app.profileLabel?.let { "${app.label} · $it" } ?: app.label
                                    Text(if (decisions.forLauncherApp(app)?.canLaunch == false)
                                        "$label · Locked by Sanctuary" else label)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { folder = null }) { Text("Close") } },
                )
            }
            editingFolder?.let { (slot, item) ->
                LauncherSmartFolderEditor(item, smartApps, decisions, onSave = { replacement ->
                    val items = layout.itemsAt(slot.page)
                    if (items?.getOrNull(slot.index) == item) {
                        edit(layout.withItemsAt(slot.page, items.mapIndexed { index, value ->
                            if (index == slot.index) replacement else value
                        }))
                    }
                    editingFolder = null
                }, onDismiss = { editingFolder = null })
            }
            gestures.menu?.let { anchor ->
                val component = (anchor.origin.item as? LauncherItem.App)?.component
                val app = component?.let(appsByComponent::get)
                if (component == null || app != null && (canDisplay(component) ||
                    ((libraryQuery.isNotBlank() || query.isNotBlank()) && canDisplay(component, appVisibility.hideFromSearch)))) {
                    LauncherIconMenu(
                        anchor, app, layout, currentPage, state.appearance.dock != LauncherDock.HIDDEN,
                        launcherProtected, actions,
                        dismiss = gestures::dismissMenu,
                        open = { open(anchor.origin.item) },
                        editHome = { surface = LauncherSurface.EDIT },
                        save = ::edit,
                        onEditSmartFolder = { slot, item -> editingFolder = slot to item },
                        recentPinned = component != null && component in smartState?.pinned.orEmpty(),
                        dockLocked = state.dockOptions.locked,
                        onEditCategory = { categoryEditor = it },
                    )
                } else {
                    LaunchedEffect(anchor) { gestures.dismissMenu() }
                }
            }
            categoryEditor?.takeUnless { launcherProtected }?.let { app ->
                LauncherCategoryEditor(app, categoryOverrides[app.component], onSelect = { selected ->
                    if (identitySettings.saveCategoryOverride(app.component, selected)) {
                        categoryOverrides = identitySettings.categoryOverrides()
                        categoryEditor = null
                    }
                }, onDismiss = { categoryEditor = null })
            }
            if (surface == LauncherSurface.HOME && activeDrag != null &&
                (activeDrag.origin.item as? LauncherItem.App)?.let { canDisplay(it.component) } != false
            ) {
                val app = (activeDrag.origin.item as? LauncherItem.App)?.let { appsByComponent[it.component] }
                val preview = gestures.targetAt(layout, pageForDrop(), activeDrag.position, rowTolerance)
                    ?.takeIf { layout.dropLauncherItem(activeDrag.origin.item, activeDrag.origin.slot, it.drop) != layout }
                LauncherDragOverlay(activeDrag, preview, app, launcherAccent(state.appearance.accent),
                    app?.let { decisions.forLauncherApp(it)?.canLaunch == false } == true)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LauncherHome(
    state: NovaLauncherState,
    layout: LauncherLayout,
    wide: Boolean,
    currentPage: Int,
    apps: Map<String, LauncherApp>,
    decisions: Map<String, Decision>,
    actions: LauncherUiActions,
    protectedBySanctuary: Boolean,
    onPage: (Int) -> Unit,
    onDrawer: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onItem: (LauncherItem) -> Unit,
    gestures: LauncherIconGestures,
    dragPageRequest: Int?,
    librarySelected: Boolean = false,
    onLibrarySelected: (Boolean) -> Unit = {},
    onLibraryBack: () -> Boolean = { false },
    libraryPage: (@Composable (goBack: () -> Unit) -> Unit)? = null,
    showHomeRecents: Boolean = true,
    showSuggestions: Boolean = true,
    smartMembers: (LauncherItem.SmartFolder) -> List<LauncherApp> = { emptyList() },
    smartFolderPresentation: LauncherSmartFolderPresentation = LauncherSmartFolderPresentation.AUTOMATIC,
    sanctuaryStatus: LauncherSanctuaryStatus? = null,
    isAppVisible: (String) -> Boolean = apps::containsKey,
    animateDockEntrance: Boolean = false,
    guardianSelected: Boolean = false,
    onGuardianSelected: (Boolean) -> Unit = {},
    guardianPage: (@Composable () -> Unit)? = null,
) {
    DisposableEffect(gestures) { onDispose { gestures.clearHome() } }
    val pages = layout.pages.ifEmpty { listOf(listOf(LauncherItem.Workspace)) }
    val firstHome = if (guardianPage == null) 0 else 1
    val libraryIndex = firstHome + pages.size
    val initialPage = when {
        guardianSelected && firstHome == 1 -> 0
        librarySelected && libraryPage != null -> libraryIndex
        else -> firstHome + currentPage.coerceIn(0, pages.lastIndex)
    }
    val pager = rememberPagerState(initialPage = initialPage) { libraryIndex + if (libraryPage == null) 0 else 1 }
    val scope = rememberCoroutineScope()
    val footerEntrance = remember { Animatable(if (animateDockEntrance) 0f else 1f) }
    LaunchedEffect(footerEntrance) {
        if (footerEntrance.value == 0f) footerEntrance.animateTo(1f, tween(durationMillis = 210))
    }
    val focus = LocalFocusManager.current
    val atLibrary = libraryPage != null && pager.settledPage == libraryIndex
    val atGuardian = firstHome == 1 && pager.settledPage == 0
    val goBackFromLibrary: () -> Unit = {
        focus.clearFocus()
        if (!onLibraryBack()) scope.launch { pager.animateScrollToPage(libraryIndex - 1) }
    }
    BackHandler(enabled = atLibrary, onBack = goBackFromLibrary)
    BackHandler(enabled = atGuardian) { scope.launch { pager.animateScrollToPage(firstHome) } }
    LaunchedEffect(dragPageRequest) {
        if (dragPageRequest != null && dragPageRequest in pages.indices) {
            // A search focus change can scroll the pager while the footer is being remeasured.
            // Request the drop page in that measurement and cancel any competing scroll.
            pager.requestScrollToPage(firstHome + dragPageRequest)
        }
    }
    LaunchedEffect(pager.settledPage, atLibrary, atGuardian) {
        if (!atLibrary && !atGuardian) {
            if (librarySelected) focus.clearFocus()
            onPage((pager.settledPage - firstHome).coerceIn(0, pages.lastIndex))
        }
        onGuardianSelected(atGuardian)
        onLibrarySelected(atLibrary)
    }
    val appearance = state.appearance
    val accent = launcherAccent(appearance.accent)
    val context = LocalContext.current
    val activeGesture by rememberUpdatedState(state.workspaceGesture)
    val openWorkspace by rememberUpdatedState(actions.openWorkspace)
    val openDrawer by rememberUpdatedState(onDrawer)
    val editLayout by rememberUpdatedState(onEdit)
    fun visible(item: LauncherItem): Boolean = when (item) {
        is LauncherItem.App -> isAppVisible(item.component)
        is LauncherItem.Folder -> item.apps.any { visible(LauncherItem.App(it)) }
        is LauncherItem.SmartFolder -> when (item.category) {
            LauncherCategory.RECENTLY_USED -> showHomeRecents
            LauncherCategory.SUGGESTIONS -> showSuggestions
            else -> true
        }
        LauncherItem.Workspace -> true
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag(if (wide) "nova-launcher-inner" else "nova-launcher-cover")) {
        val compact = maxHeight < 530.dp
        val homeWidth = maxWidth.value
        val homeHeight = maxHeight.value
        val largeLandscape = wide && compact
        val builtin = when (appearance.wallpaper) {
            LauncherWallpaper.OCEAN -> "Aurora"
            LauncherWallpaper.MIDNIGHT -> "Midnight"
            LauncherWallpaper.PLUM -> "Dusk"
            LauncherWallpaper.DEVICE -> null
        }
        if (builtin == null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .37f)))
        else WallpaperSurface(Wallpaper(builtin = builtin), context.filesDir, Modifier.fillMaxSize())

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                HorizontalPager(
                    pager,
                    Modifier.fillMaxWidth().weight(1f).testTag("launcher-home-pages"),
                    key = {
                        when {
                            it < firstHome -> "guardian"
                            it == libraryIndex -> "app-library"
                            else -> "home-${it - firstHome}"
                        }
                    },
                    userScrollEnabled = gestures.drag == null,
                ) { page ->
                    if (page < firstHome) {
                        guardianPage?.invoke()
                        return@HorizontalPager
                    }
                    if (page == libraryIndex) {
                        Box(Modifier.fillMaxSize()) { libraryPage?.invoke(goBackFromLibrary) }
                        return@HorizontalPager
                    }
                    val index = page - firstHome
                    val activePage = pager.settledPage == page
                    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 26.dp else 14.dp)) {
                        LauncherHeader(protectedBySanctuary, actions, { onDrawer(true) }, onSettings, onEdit)
                        LauncherSanctuaryStatusCard(sanctuaryStatus, expanded = wide && !compact, onOpen = actions.openSanctuary)
                        if (state.drawer.search != LauncherSearchPlacement.DRAWER) {
                            OutlinedButton(
                                onClick = { onDrawer(true) },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = .5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Icon(Icons.Default.Search, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Search apps")
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (activePage) {
                                DisposableEffect(gestures, index) { onDispose { gestures.clearPageContent(index) } }
                            }
                            val pageModifier = Modifier.fillMaxSize().then(
                                if (activePage) Modifier.onGloballyPositioned {
                                    gestures.setPage(it)
                                    gestures.setPageContent(index, it)
                                } else Modifier,
                            )
                            val items = pages[index]
                            val widgets = state.widgets.filter { widget ->
                                widget.wide == (wide && state.separateDisplays) && widget.page == index
                            }
                            val showClock = index == 0
                            val content: @Composable (Modifier) -> Unit = { modifier ->
                                Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                    widgets.forEach { widget ->
                                        key(widget.id) {
                                            val permitted = !protectedBySanctuary || apps.values.any { app ->
                                                app.profileSerial == null && app.packageName == widget.provider.substringBefore('/') &&
                                                    decisions.forLauncherApp(app)?.canLaunch == true
                                            }
                                            if (permitted) actions.renderWidget(widget,
                                                Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(bottom = 8.dp))
                                            else Spacer(Modifier.height(104.dp))
                                        }
                                    }
                                    LauncherHomeGrid(
                                        items, appearance.grid.columns(wide), appearance, apps, decisions, onItem,
                                        gestures = gestures, slots = items.indices.map { LauncherSlot(index, it) },
                                        smartMembers = smartMembers, visible = ::visible,
                                        expandedSmartFolders = smartFolderPresentation.expands(
                                            homeWidth, homeHeight, items,
                                        ),
                                    )
                                    Box(
                                        Modifier.fillMaxWidth().heightIn(min = if (items.isEmpty()) 144.dp else 72.dp)
                                            .testTag("launcher-home-gestures")
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = { if (activeGesture == LauncherGesture.DOUBLE_TAP) openWorkspace() },
                                                    onLongPress = { editLayout() },
                                                )
                                            }
                                            .pointerInput(Unit) {
                                                var distance = 0f
                                                detectVerticalDragGestures(
                                                    onDragStart = { distance = 0f },
                                                    onVerticalDrag = { _, delta -> distance += delta },
                                                    onDragEnd = {
                                                        val threshold = 60.dp.toPx()
                                                        if (distance < -threshold) openDrawer(false)
                                                        if (distance > threshold && activeGesture == LauncherGesture.SWIPE_DOWN) openWorkspace()
                                                    },
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (items.isEmpty() && widgets.isEmpty()) {
                                            TextButton(onClick = onEdit, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                                                Text("Edit this Home page")
                                            }
                                        }
                                    }
                                }
                            }
                            if (largeLandscape && showClock) {
                                Row(pageModifier) {
                                    LauncherClock(true, Modifier.widthIn(min = 180.dp).padding(top = 14.dp, end = 20.dp))
                                    content(Modifier.weight(1f).fillMaxHeight())
                                }
                            } else {
                                Column(pageModifier) {
                                    if (showClock) LauncherClock(compact, Modifier.padding(top = if (compact) 6.dp else 18.dp,
                                        bottom = if (compact) 10.dp else 24.dp))
                                    content(Modifier.weight(1f))
                                }
                            }
                        }

                    }
                }
                Column(
                    Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                        val footer = measurable.measure(constraints.copy(minHeight = 0))
                        val position = pager.currentPage + pager.currentPageOffsetFraction
                        val left = if (firstHome == 0) 1f else position.coerceIn(0f, 1f)
                        val right = if (libraryPage == null || state.dockOptions.showInLibrary) 1f
                            else (libraryIndex - position).coerceIn(0f, 1f)
                        val homeFraction = minOf(left, right)
                        val height = (footer.height * homeFraction).roundToInt()
                        layout(footer.width, height) {
                            if (height > 0) {
                                val entranceOffset = (height * (1f - footerEntrance.value)).roundToInt()
                                footer.placeRelative(0, entranceOffset)
                            }
                        }
                    }.padding(horizontal = if (wide) 26.dp else 14.dp),
                ) {
                    LauncherDots(pager.pageCount, pager.currentPage, accent, libraryLast = libraryPage != null,
                        guardianFirst = firstHome == 1) {
                        scope.launch { pager.animateScrollToPage(it) }
                    }
                    if (appearance.dock != LauncherDock.HIDDEN &&
                        (layout.dock.isNotEmpty() || gestures.drag != null && !state.dockOptions.locked)
                    ) {
                        LauncherSharedDock(
                            layout.dock, appearance, state.dockOptions, apps, decisions, compact, gestures,
                            onItem, ::visible, smartMembers, active = !atGuardian && (!atLibrary || state.dockOptions.showInLibrary),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                        if (!atLibrary && !atGuardian) TextButton(
                            onClick = { onDrawer(false) },
                            modifier = Modifier.heightIn(min = 46.dp).testTag("launcher-open-drawer"),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apps")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherHeader(
    protectedBySanctuary: Boolean,
    actions: LauncherUiActions,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.openWorkspace) {
            Image(painterResource(R.drawable.nova_identity), "Open NovaOS workspace", Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("NOVA LAUNCHER", fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (protectedBySanctuary) "Sanctuary active" else "Home", color = Color.White.copy(alpha = .77f),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search apps") }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, "Launcher settings") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Launcher menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val entries = listOf(
                    "Edit Home" to onEdit,
                    "Open Sanctuary" to actions.openSanctuary,
                    "Sanctuary rules" to actions.reviewSanctuary,
                    "Phone and emergency access" to actions.emergencyPhone,
                    "Android Home app settings" to actions.selectHome,
                )
                entries.forEach { (name, action) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; action() })
                }
            }
        }
    }
}

@Composable
private fun LauncherClock(compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timeFormat = DateFormat.getTimeFormat(context)
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = System.currentTimeMillis()
            now = next
            delay(60_000 - next % 60_000)
        }
    }
    Column(modifier) {
        Text(timeFormat.format(Date(now)), fontSize = if (compact) 36.sp else 59.sp,
            lineHeight = if (compact) 40.sp else 65.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault()).format(dateFormat),
            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .8f), maxLines = 1)
    }
}

@Composable
private fun LauncherDots(
    count: Int,
    selected: Int,
    accent: Color,
    libraryLast: Boolean = false,
    guardianFirst: Boolean = false,
    onPage: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            IconButton(
                onClick = { onPage(index) },
                modifier = Modifier.width(36.dp).height(42.dp).semantics { this.selected = index == selected },
            ) {
                Icon(if (index == selected) Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
                    when {
                        guardianFirst && index == 0 -> "Guardian page"
                        libraryLast && index == count - 1 -> "App Library page"
                        else -> "Home page ${index + if (guardianFirst) 0 else 1}"
                    },
                    Modifier.size(7.dp),
                    tint = if (index == selected) accent else LocalContentColor.current.copy(alpha = .75f))
            }
        }
    }
}

@Composable
internal fun LauncherHomeGrid(
    items: List<LauncherItem>,
    columns: Int,
    appearance: LauncherAppearance,
    apps: Map<String, LauncherApp>,
    decisions: Map<String, Decision>,
    onItem: (LauncherItem) -> Unit,
    interactive: Boolean = true,
    gestures: LauncherIconGestures? = null,
    slots: List<LauncherSlot> = emptyList(),
    smartMembers: (LauncherItem.SmartFolder) -> List<LauncherApp> = { emptyList() },
    expandedSmartFolders: Boolean = false,
    visible: (LauncherItem) -> Boolean = { true },
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        smartFolderRows(items, columns, expandedSmartFolders).forEach { row ->
            val expandedRow = expandedSmartFolders && row.firstOrNull()?.value is LauncherItem.SmartFolder
            val rowColumns = if (expandedRow) 2 else columns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (expandedRow) 12.dp else 0.dp)) {
                row.forEach { (index, item) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (visible(item)) {
                            LauncherTile(item, appearance, apps, decisions, { onItem(item) }, interactive = interactive,
                                gestures = gestures, origin = LauncherIconOrigin(item, slots.getOrNull(index)),
                                smartMembers = smartMembers, expandedSmartFolder = expandedRow,
                                folderMembers = { folder ->
                                    folder.apps.filter { visible(LauncherItem.App(it)) }.mapNotNull(apps::get)
                                })
                        } else {
                            val size = when (appearance.iconSize) {
                                LauncherIconSize.SMALL -> 44.dp
                                LauncherIconSize.MEDIUM -> 56.dp
                                LauncherIconSize.LARGE -> 68.dp
                            }
                            Spacer(Modifier.height(size + if (appearance.labels) 24.dp else 8.dp))
                        }
                    }
                }
                repeat(rowColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LauncherTile(
    item: LauncherItem,
    appearance: LauncherAppearance,
    apps: Map<String, LauncherApp>,
    decisions: Map<String, Decision>,
    onClick: () -> Unit,
    compact: Boolean = false,
    interactive: Boolean = true,
    gestures: LauncherIconGestures? = null,
    origin: LauncherIconOrigin = LauncherIconOrigin(item),
    modifier: Modifier = Modifier,
    showLabel: Boolean = appearance.labels && (!compact || item == LauncherItem.Workspace),
    smartMembers: (LauncherItem.SmartFolder) -> List<LauncherApp> = { emptyList() },
    expandedSmartFolder: Boolean = false,
    fullTileIcon: Boolean = false,
    folderMembers: (LauncherItem.Folder) -> List<LauncherApp> = { folder -> folder.apps.mapNotNull(apps::get) },
) {
    val app = (item as? LauncherItem.App)?.let { apps[it.component] }
    val blocked = app?.let { decisions.forLauncherApp(it)?.canLaunch == false } == true
    val label = when (item) {
        is LauncherItem.App -> app?.label ?: return
        is LauncherItem.Folder -> item.name
        is LauncherItem.SmartFolder -> item.name
        LauncherItem.Workspace -> "Open NovaOS"
    }
    val haptics = LocalHapticFeedback.current
    val registration = remember(origin) { Any() }
    val bounds = if (interactive && gestures != null) {
        Modifier.launcherIconBounds(gestures, origin, registration)
    } else Modifier
    Column(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(bounds)
            .then(if (app != null) Modifier.testTag("launcher-app-${app.component}") else Modifier)
            .semantics {
                val described = app?.profileLabel?.let { "$label, $it" } ?: label
                contentDescription = if (blocked) "$described, blocked by Sanctuary" else described
                if (interactive && gestures != null) {
                    onLongClick(label = "Options for $label") { gestures.showMenu(registration); true }
                }
            }
            .then(if (interactive) Modifier.clickable(role = Role.Button, onClick = {
                if (item is LauncherItem.SmartFolder) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }) else Modifier)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (expandedSmartFolder && item is LauncherItem.SmartFolder) {
            LauncherExpandedSmartFolder(item, smartMembers(item), appearance, decisions)
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val target = when (appearance.iconSize) {
                LauncherIconSize.SMALL -> 44.dp
                LauncherIconSize.MEDIUM -> 56.dp
                LauncherIconSize.LARGE -> 68.dp
            }
            val size = if (fullTileIcon) minOf(maxWidth, maxHeight)
                else minOf(if (compact) target * .85f else target, (maxWidth - 6.dp).coerceAtLeast(28.dp))
            val shape = if (fullTileIcon) RoundedCornerShape(size * .23f) else when (appearance.iconShape) {
                LauncherIconShape.ROUNDED -> RoundedCornerShape(size * .30f)
                LauncherIconShape.CIRCLE -> CircleShape
                LauncherIconShape.SQUIRCLE -> RoundedCornerShape(size * .41f)
            }
            val nativeArtwork = app != null && appearance.iconStyle == LauncherIconStyle.FULL_ICONS
            val background = launcherAccent(appearance.accent).copy(alpha = if (blocked) .19f else .29f)
            Box(
                Modifier.size(size).then(if (nativeArtwork) Modifier else Modifier.clip(shape).background(background)),
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is LauncherItem.App -> LauncherAppIcon(
                        checkNotNull(app),
                        Modifier.size(if (nativeArtwork) size else size * .69f)
                            .testTag("launcher-app-artwork-${item.component}"),
                    )
                    is LauncherItem.Folder -> {
                        val members = folderMembers(item)
                        if (members.isEmpty() || appearance.iconStyle == LauncherIconStyle.NOVA_TILES) {
                            Icon(Icons.Default.Folder, null, Modifier.size(size * .53f), tint = Color.White)
                        } else LauncherFolderPreview(members, Modifier.size(size * .88f), decisions, appearance = appearance)
                    }
                    is LauncherItem.SmartFolder -> LauncherFolderPreview(
                        smartMembers(item), Modifier.size(size * .8f), decisions, appearance,
                    )
                    LauncherItem.Workspace -> Image(painterResource(R.drawable.nova_identity), null, Modifier.size(size * .69f))
                }
                if (blocked) {
                    Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(alpha = .4f)))
                    Surface(
                        Modifier.align(Alignment.BottomStart).padding(2.dp), shape = CircleShape,
                        color = Color(0xFF273550), contentColor = Color.White,
                    ) { Icon(Icons.Default.Lock, null, Modifier.padding(3.dp).size(13.dp)) }
                }
            }
        }
        if (showLabel) {
            val displayedLabel = app?.profileLabel?.let { "$label · $it" } ?: label
            Text(displayedLabel, Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherDrawer(
    state: NovaLauncherState,
    layout: LauncherLayout,
    wide: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    searchRequested: Boolean,
    apps: List<LauncherApp>,
    shortcuts: List<LauncherShortcut>,
    decisions: Map<String, Decision>,
    onBack: () -> Unit,
    onApp: (LauncherApp) -> Unit,
    onShortcut: (LauncherShortcut) -> Unit,
    onFolder: (LauncherItem.Folder) -> Unit,
    gestures: LauncherIconGestures,
    onLibrary: () -> Unit,
    searchApps: List<LauncherApp> = apps,
) {
    var searchFocused by remember { mutableStateOf(false) }
    val queryText = query.trim()
    val catalog = if (searchFocused || searchRequested || queryText.isNotEmpty()) searchApps else apps
    val appsByComponent = remember(catalog) { catalog.associateBy { it.component } }
    val matchingApps = remember(catalog, queryText, state.drawer.sort, state.launchCounts, layout.favourites) {
        val filtered = catalog.filter { it.label.contains(queryText, ignoreCase = true) }
        val name = compareBy<LauncherApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        filtered.sortedWith(when (state.drawer.sort) {
            LauncherSort.NAME -> name
            LauncherSort.FAVOURITES -> compareByDescending<LauncherApp> { it.component in layout.favourites }.then(name)
            LauncherSort.FREQUENT -> compareByDescending<LauncherApp> { state.launchCounts[it.component] ?: 0 }.then(name)
        })
    }
    val folders = remember(layout, queryText, state.drawer.folders, appsByComponent) {
        if (!state.drawer.folders) emptyList()
        else (layout.pages.flatten() + layout.dock).filterIsInstance<LauncherItem.Folder>().distinct()
            .filter { it.name.contains(queryText, ignoreCase = true) && it.apps.any(appsByComponent::containsKey) }
    }
    val matches = matchingApps.map { LauncherItem.App(it.component) } + folders
    val eligiblePackages = remember(catalog) { catalog.mapTo(mutableSetOf()) { it.packageName } }
    val shortcutMatches = if (state.drawer.shortcutSearch && queryText.isNotEmpty())
        shortcuts.filter { it.packageName in eligiblePackages && it.label.contains(queryText, ignoreCase = true) }.take(8)
    else emptyList()
    val frequent = if (state.drawer.frequent && queryText.isEmpty())
        catalog.filter { (state.launchCounts[it.component] ?: 0) > 0 }
            .sortedByDescending { state.launchCounts[it.component] }.take(4)
    else emptyList()
    val accent = launcherAccent(state.appearance.accent)

    LauncherPanel("Apps", onBack) {
        OutlinedButton(onClick = onLibrary, modifier = Modifier.testTag("launcher-open-library")) {
            Text("App Library")
        }
        if (state.drawer.search != LauncherSearchPlacement.HOME || searchRequested) {
            OutlinedTextField(
                value = query,
                onValueChange = { onQuery(it.take(128)) },
                label = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .onFocusChanged { searchFocused = it.hasFocus }.testTag("launcher-drawer-search"),
            )
        } else {
            Text("Search is available from Home.", style = MaterialTheme.typography.labelMedium)
        }
        if (matches.isEmpty() && shortcutMatches.isEmpty()) {
            Text(if (queryText.isEmpty()) "No available apps" else "No apps match your search",
                Modifier.padding(vertical = 16.dp))
        }
        if (frequent.isNotEmpty()) {
            Text("Frequently used", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                frequent.forEach { app ->
                    TextButton(onClick = { onApp(app) }, modifier = Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (shortcutMatches.isNotEmpty()) {
            Text("App shortcuts", style = MaterialTheme.typography.labelLarge)
            Column(Modifier.heightIn(max = 164.dp).verticalScroll(rememberScrollState())) {
                shortcutMatches.forEach { shortcut ->
                    TextButton(onClick = { onShortcut(shortcut) }) { Text(shortcut.label) }
                }
            }
        }
        val columns = state.appearance.grid.columns(wide)
        val drawTile: @Composable (LauncherItem) -> Unit = { item ->
            LauncherTile(item, state.appearance, appsByComponent, decisions,
                onClick = {
                    when (item) {
                        is LauncherItem.App -> appsByComponent[item.component]?.let(onApp)
                        is LauncherItem.Folder -> onFolder(item)
                        is LauncherItem.SmartFolder -> Unit
                        LauncherItem.Workspace -> Unit
                    }
                },
                gestures = gestures,
                showLabel = true,
            )
        }
        if (state.drawer.orientation == LauncherDrawerOrientation.VERTICAL) {
            LazyVerticalGrid(
                GridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp),
            ) { items(matches) { drawTile(it) } }
        } else {
            val pages = matches.chunked(columns * state.appearance.grid.rows).ifEmpty { listOf(emptyList()) }
            val pager = rememberPagerState { pages.size }
            val scope = rememberCoroutineScope()
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("launcher-drawer-horizontal")) { page ->
                LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp),
                ) { items(pages[page]) { drawTile(it) } }
            }
            LauncherDots(pages.size, pager.currentPage, accent) { scope.launch { pager.animateScrollToPage(it) } }
        }
    }
}

@Composable
internal fun LauncherPanel(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
    }
}

internal fun launcherAccent(accent: LauncherAccent): Color = when (accent) {
    LauncherAccent.SKY -> Color(0xFF8AB6F6)
    LauncherAccent.TEAL -> Color(0xFF73DFD1)
    LauncherAccent.LAVENDER -> Color(0xFFD6BAFF)
    LauncherAccent.AMBER -> Color(0xFFFFC784)
}
