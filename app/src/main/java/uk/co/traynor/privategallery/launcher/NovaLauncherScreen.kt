package uk.co.traynor.privategallery.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.R
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
) {
    val theme = when (state.appearance.theme) {
        LauncherTheme.SYSTEM -> AppTheme.SYSTEM
        LauncherTheme.LIGHT -> AppTheme.LIGHT
        LauncherTheme.DARK -> AppTheme.DARK
    }
    PrivateGalleryTheme(theme) {
        if (!state.configured) {
            LauncherSetup(state, apps, decisions, actions, isDefaultHome, outsideProtection, launcherProtected)
        } else {
            LauncherRuntime(state, apps, shortcuts, decisions, actions, isDefaultHome, outsideProtection,
                launcherProtected, homeGeneration, sanctuaryStatus, smartState, smartSettings, defaultHomeUi, smartFolderPresentation)
        }
    }
}

private enum class LauncherSurface { HOME, DRAWER, LIBRARY, SETTINGS, LIBRARY_SETTINGS, EDIT }
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
    decisions: Map<String, Decision>,
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
) {
    var surface by rememberSaveable(homeGeneration) { mutableStateOf(LauncherSurface.HOME) }
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
    fun canDisplay(component: String): Boolean {
        val app = appsByComponent[component] ?: return false
        if (!launcherProtected || !state.hideBlocked) return true
        val decision = decisions[app.packageName]
        return decision == Decision.APPROVED || decision == Decision.ESSENTIAL
    }
    var category by rememberSaveable(homeGeneration) { mutableStateOf<LauncherCategory?>(null) }
    var homeFolder by rememberSaveable(homeGeneration, stateSaver = openSmartFolderSaver) {
        mutableStateOf<LauncherItem.SmartFolder?>(null)
    }
    var editingFolder by remember(homeGeneration) {
        mutableStateOf<Pair<LauncherSlot, LauncherItem.SmartFolder>?>(null)
    }
    val visibleApps = remember(apps, launcherProtected, state.hideBlocked, decisions) {
        apps.filter { canDisplay(it.component) }
    }
    val smartApps = remember(visibleApps, launcherProtected, decisions) {
        visibleApps.filter { !launcherProtected || decisions[it.packageName]?.canLaunch == true }
    }
    val context = LocalContext.current
    val identitySettings = remember(context) { LauncherIdentitySettings(context) }
    var libraryPins by remember(identitySettings) { mutableStateOf(identitySettings.libraryPins()) }
    val visibleLibraryPins = libraryPins.takeUnless { launcherProtected }.orEmpty()
    val groups = remember(visibleApps, smartApps, smartState, launcherProtected) {
        launcherLibraryGroups(
            visibleApps,
            if (launcherProtected || smartState?.showSuggestions != true) emptyList()
            else smartState.suggestedApps(smartApps),
            if (launcherProtected) emptyList() else smartState?.recentApps(smartApps).orEmpty(),
        )
    }
    fun members(folder: LauncherItem.SmartFolder): List<LauncherApp> {
        val allowed = smartApps.associateBy { it.component }
        val automatic = groups.firstOrNull { it.category == folder.category }?.apps.orEmpty()
        val pinned = if (folder.category == LauncherCategory.RECENTLY_USED && !launcherProtected)
            smartState?.pinnedApps(smartApps).orEmpty()
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
            homeFolder != null -> { homeFolder = null; category = null; surface = LauncherSurface.HOME }
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
            LauncherSurface.LIBRARY_SETTINGS -> surface = LauncherSurface.SETTINGS
            else -> { surface = LauncherSurface.HOME; query = "" }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= LauncherExpandedWidth.dp
        val layout = state.layout(wide)
        val isInner = wide && state.separateDisplays
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
        fun edit(next: LauncherLayout) { actions.save(state.withLayout(wide, next)) }

        val screenWidth = constraints.maxWidth
        val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
        var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val gestures = remember(screenWidth, isInner, homeGeneration, rightToLeft) {
            LauncherIconGestures(rightToLeft) { rootCoordinates }
        }
        var dragPageRequest by remember(gestures) { mutableStateOf<Int?>(null) }
        fun pageForDrop(): Int? = currentPage.takeIf {
            !librarySelected && (dragPageRequest == null || dragPageRequest == it)
        }
        val rowTolerance = with(LocalDensity.current) { 8.dp.toPx() }
        val edgeWidth = with(LocalDensity.current) { 34.dp.toPx() }
        val activeDrag = gestures.drag
        LauncherDefaultHomePrompt(
            defaultHomeUi,
            safeSurface = surface == LauncherSurface.HOME && !librarySelected && folder == null && editingFolder == null &&
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
                if (folder != null || gestures.menu != null) null
                else when (surface) {
                    LauncherSurface.HOME -> gestures.sourceAt(position, currentPage, drawer = librarySelected)
                    LauncherSurface.DRAWER, LauncherSurface.LIBRARY -> gestures.sourceAt(position, currentPage, drawer = true)
                    else -> null
                }
            },
            startDrag = {
                if (librarySelected) {
                    selectLibrary(null)
                    dragPageRequest = currentPage
                }
                if (surface == LauncherSurface.DRAWER || surface == LauncherSurface.LIBRARY) {
                    homeFolder = null
                    category = null
                    surface = LauncherSurface.HOME
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
                LauncherSurface.HOME -> key(isInner, homeGeneration) {
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
                                pinnedApps = if (launcherProtected) emptyList() else smartState?.pinnedApps(smartApps).orEmpty(),
                                pinnedCategories = visibleLibraryPins,
                                embedded = true,
                            )
                        },
                        showHomeRecents = smartState?.showHomeRecents != false,
                        showSuggestions = smartState?.showSuggestions != false,
                        smartMembers = ::members,
                        smartFolderPresentation = smartFolderPresentation,
                        sanctuaryStatus = sanctuaryStatus,
                    )
                }
                LauncherSurface.DRAWER -> LauncherDrawer(
                    state.copy(launchCounts = if (smartState?.mode == LauncherTrackingMode.OFF) emptyMap()
                        else state.launchCounts - smartState?.excluded.orEmpty()),
                    layout, wide, query, { query = it }, searchRequested,
                    apps.filter { canDisplay(it.component) }, shortcuts, decisions,
                    onBack = { surface = LauncherSurface.HOME },
                    onApp = { actions.launchApp(it.component) },
                    onShortcut = actions.launchShortcut,
                    onFolder = { folder = it },
                    gestures = gestures,
                    onLibrary = {
                        query = ""
                        selectLibrary(LauncherLibraryEntry.DRAWER)
                        surface = LauncherSurface.HOME
                    },
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
                        pinnedApps = if (launcherProtected) emptyList() else smartState?.pinnedApps(smartApps).orEmpty(),
                        pinnedCategories = visibleLibraryPins,
                    )
                }
                LauncherSurface.LIBRARY_SETTINGS -> LauncherPanel("App Library", { surface = LauncherSurface.SETTINGS }) {
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
                LauncherSurface.SETTINGS -> LauncherSettings(
                    state, decisions, apps, actions, isDefaultHome, outsideProtection, launcherProtected,
                    onBack = { surface = LauncherSurface.HOME },
                    onEdit = { surface = LauncherSurface.EDIT },
                    onLibrarySettings = { surface = LauncherSurface.LIBRARY_SETTINGS },
                    defaultHomeUi = defaultHomeUi,
                )
                LauncherSurface.EDIT -> LauncherLayoutEditor(
                    state, layout, wide, currentPage, apps.filter { canDisplay(it.component) }, decisions,
                    onEdit = ::edit, onPage = updatePage, actions = actions,
                    onBack = { surface = LauncherSurface.HOME },
                )
            }

            folder?.let { selected ->
                AlertDialog(
                    onDismissRequest = { folder = null },
                    title = { Text(selected.name) },
                    text = {
                        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                            val members = selected.apps
                                .filter { surface != LauncherSurface.DRAWER || canDisplay(it) }
                                .mapNotNull(appsByComponent::get)
                            if (members.isEmpty()) Text("No available apps in this folder.")
                            members.forEach { app ->
                                TextButton(onClick = {
                                    folder = null
                                    actions.launchApp(app.component)
                                }) { Text(app.label) }
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
                if (component == null || app != null && (surface != LauncherSurface.DRAWER || canDisplay(component))) {
                    LauncherIconMenu(
                        anchor, app, layout, currentPage, state.appearance.dock != LauncherDock.HIDDEN,
                        launcherProtected, actions,
                        dismiss = gestures::dismissMenu,
                        open = { open(anchor.origin.item) },
                        editHome = { surface = LauncherSurface.EDIT },
                        save = ::edit,
                        onEditSmartFolder = { slot, item -> editingFolder = slot to item },
                        recentPinned = component != null && component in smartState?.pinned.orEmpty(),
                    )
                } else {
                    LaunchedEffect(anchor) { gestures.dismissMenu() }
                }
            }
            if (surface == LauncherSurface.HOME && activeDrag != null) {
                val app = (activeDrag.origin.item as? LauncherItem.App)?.let { appsByComponent[it.component] }
                val preview = gestures.targetAt(layout, pageForDrop(), activeDrag.position, rowTolerance)
                    ?.takeIf { layout.dropLauncherItem(activeDrag.origin.item, activeDrag.origin.slot, it.drop) != layout }
                LauncherDragOverlay(activeDrag, preview, app, launcherAccent(state.appearance.accent),
                    app?.let { decisions[it.packageName]?.canLaunch == false } == true)
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
) {
    DisposableEffect(gestures) { onDispose { gestures.clearHome() } }
    val pages = layout.pages.ifEmpty { listOf(listOf(LauncherItem.Workspace)) }
    val initialPage = if (librarySelected && libraryPage != null) pages.size else currentPage.coerceIn(0, pages.lastIndex)
    val pager = rememberPagerState(initialPage = initialPage) { pages.size + if (libraryPage == null) 0 else 1 }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val atLibrary = libraryPage != null && pager.settledPage == pages.size
    val goBackFromLibrary: () -> Unit = {
        focus.clearFocus()
        if (!onLibraryBack()) scope.launch { pager.animateScrollToPage(pages.lastIndex) }
    }
    BackHandler(enabled = atLibrary, onBack = goBackFromLibrary)
    LaunchedEffect(dragPageRequest) {
        if (dragPageRequest != null && dragPageRequest in pages.indices) {
            if (atLibrary) focus.clearFocus()
            pager.scrollToPage(dragPageRequest)
        }
    }
    LaunchedEffect(pager.settledPage, atLibrary) {
        if (!atLibrary) {
            if (librarySelected) focus.clearFocus()
            onPage(pager.settledPage.coerceIn(0, pages.lastIndex))
        }
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
        is LauncherItem.App -> item.component in apps
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
            HorizontalPager(
                pager,
                Modifier.fillMaxSize().safeDrawingPadding().testTag("launcher-home-pages"),
                key = { if (it == pages.size) "app-library" else "home-$it" },
                userScrollEnabled = gestures.drag == null,
            ) { index ->
                if (index == pages.size) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) { libraryPage?.invoke(goBackFromLibrary) }
                        LauncherDots(pager.pageCount, pager.currentPage, accent, libraryLast = true) {
                            scope.launch { pager.animateScrollToPage(it) }
                        }
                    }
                    return@HorizontalPager
                }
                val activePage = pager.settledPage == index
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
                        val indexedItems = pages[index].withIndex().filter { visible(it.value) }
                        val items = indexedItems.map { it.value }
                        val widgets = state.widgets.filter { it.wide == (wide && state.separateDisplays) && it.page == index }
                        val showClock = index == 0
                        val content: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                widgets.forEach { widget ->
                                    key(widget.id) { actions.renderWidget(widget, Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(bottom = 8.dp)) }
                                }
                                LauncherHomeGrid(
                                    items, appearance.grid.columns(wide), appearance, apps, decisions, onItem,
                                    gestures = gestures, slots = indexedItems.map { LauncherSlot(index, it.index) },
                                    smartMembers = smartMembers,
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

                    LauncherDots(pager.pageCount, pager.currentPage, accent, libraryLast = libraryPage != null) {
                        scope.launch { pager.animateScrollToPage(it) }
                    }
                    val dock = layout.dock.withIndex().filter { visible(it.value) }
                    if (appearance.dock != LauncherDock.HIDDEN && (dock.isNotEmpty() || gestures.drag != null)) {
                        if (activePage) {
                            DisposableEffect(gestures) { onDispose { gestures.clearDock() } }
                        }
                        Surface(
                            Modifier.fillMaxWidth().padding(top = 1.dp).then(
                                if (activePage) Modifier.onGloballyPositioned(gestures::setDock) else Modifier,
                            ),
                            color = if (appearance.dock == LauncherDock.GLASS) Color.White.copy(alpha = .14f) else Color(0xFF273550),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(27.dp),
                        ) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val scrollDock = maxWidth < (dock.size * 48 + 12).dp
                                val scroll = if (scrollDock) Modifier.horizontalScroll(rememberScrollState()) else Modifier
                                Row(scroll.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = if (compact) 4.dp else 10.dp)) {
                                    dock.forEach { (index, item) ->
                                        val cell = if (scrollDock) Modifier.width(52.dp) else Modifier.weight(1f)
                                        Box(cell, contentAlignment = Alignment.Center) {
                                            LauncherTile(item, appearance, apps, decisions, { onItem(item) }, compact = true,
                                                gestures = gestures.takeIf { activePage },
                                                origin = LauncherIconOrigin(item, LauncherSlot(null, index)), smartMembers = smartMembers)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { onDrawer(false) },
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 46.dp).testTag("launcher-open-drawer"),
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
    onPage: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            IconButton(
                onClick = { onPage(index) },
                modifier = Modifier.width(36.dp).height(42.dp).semantics { this.selected = index == selected },
            ) {
                Icon(if (index == selected) Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
                    if (libraryLast && index == count - 1) "App Library page" else "Home page ${index + 1}",
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
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        smartFolderRows(items, columns, expandedSmartFolders).forEach { row ->
            val expandedRow = expandedSmartFolders && row.firstOrNull()?.value is LauncherItem.SmartFolder
            val rowColumns = if (expandedRow) 2 else columns
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (expandedRow) 12.dp else 0.dp)) {
                row.forEach { (index, item) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        LauncherTile(item, appearance, apps, decisions, { onItem(item) }, interactive = interactive,
                            gestures = gestures, origin = LauncherIconOrigin(item, slots.getOrNull(index)),
                            smartMembers = smartMembers, expandedSmartFolder = expandedRow)
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
) {
    val app = (item as? LauncherItem.App)?.let { apps[it.component] }
    val blocked = app?.let { decisions[it.packageName]?.canLaunch == false } == true
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
            LauncherExpandedSmartFolder(item, smartMembers(item))
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
            val background = if (fullTileIcon) Color.White.copy(alpha = .08f)
                else launcherAccent(appearance.accent).copy(alpha = if (blocked) .19f else .29f)
            Box(
                Modifier.size(size).clip(shape)
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is LauncherItem.App -> LauncherAppIcon(checkNotNull(app), Modifier.size(if (fullTileIcon) size else size * .69f))
                    is LauncherItem.Folder -> Icon(Icons.Default.Folder, null, Modifier.size(size * .53f), tint = Color.White)
                    is LauncherItem.SmartFolder -> LauncherFolderPreview(smartMembers(item), Modifier.size(size * .8f))
                    LauncherItem.Workspace -> Image(painterResource(R.drawable.nova_identity), null, Modifier.size(size * .69f))
                }
                if (blocked) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)))
                    Surface(
                        Modifier.align(Alignment.BottomEnd).padding(2.dp), shape = CircleShape,
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
) {
    val appsByComponent = remember(apps) { apps.associateBy { it.component } }
    val queryText = query.trim()
    val matchingApps = remember(apps, queryText, state.drawer.sort, state.launchCounts, layout.favourites) {
        val filtered = apps.filter { it.label.contains(queryText, ignoreCase = true) }
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
    val eligiblePackages = remember(apps) { apps.mapTo(mutableSetOf()) { it.packageName } }
    val shortcutMatches = if (state.drawer.shortcutSearch && queryText.isNotEmpty())
        shortcuts.filter { it.packageName in eligiblePackages && it.label.contains(queryText, ignoreCase = true) }.take(8)
    else emptyList()
    val frequent = if (state.drawer.frequent && queryText.isEmpty())
        apps.filter { (state.launchCounts[it.component] ?: 0) > 0 }
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("launcher-drawer-search"),
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
