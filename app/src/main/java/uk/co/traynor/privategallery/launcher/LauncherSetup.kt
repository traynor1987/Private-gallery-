package uk.co.traynor.privategallery.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.home2.Wallpaper
import uk.co.traynor.privategallery.home2.WallpaperSurface

private val appearanceSaver = listSaver<LauncherAppearance, Any>(
    save = { listOf(it.wallpaper.name, it.theme.name, it.accent.name, it.iconShape.name, it.iconSize.name, it.labels, it.grid.name, it.dock.name) },
    restore = {
        LauncherAppearance(
            LauncherWallpaper.valueOf(it[0] as String), LauncherTheme.valueOf(it[1] as String),
            LauncherAccent.valueOf(it[2] as String), LauncherIconShape.valueOf(it[3] as String),
            LauncherIconSize.valueOf(it[4] as String), it[5] as Boolean, LauncherGrid.valueOf(it[6] as String),
            LauncherDock.valueOf(it[7] as String),
        )
    },
)
private val drawerSaver = listSaver<LauncherDrawerOptions, Any>(
    save = { listOf(it.orientation.name, it.search.name, it.sort.name, it.folders, it.frequent, it.shortcutSearch) },
    restore = {
        LauncherDrawerOptions(LauncherDrawerOrientation.valueOf(it[0] as String),
            LauncherSearchPlacement.valueOf(it[1] as String), LauncherSort.valueOf(it[2] as String),
            it[3] as Boolean, it[4] as Boolean, it[5] as Boolean)
    },
)

@Composable
internal fun LauncherSetup(
    previous: NovaLauncherState,
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    actions: LauncherUiActions,
    defaultHome: Boolean,
    outsideProtection: Boolean,
    launcherProtected: Boolean,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var starting by rememberSaveable { mutableStateOf(LauncherStartingLayout.START_FRESH) }
    var template by rememberSaveable { mutableStateOf(LauncherTemplate.CLASSIC) }
    var separate by rememberSaveable { mutableStateOf(previous.separateDisplays) }
    var selectedCopyComponents by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var copySearch by rememberSaveable { mutableStateOf("") }
    val initial = remember(starting, template, apps, selectedCopyComponents) {
        launcherInitialState(starting, template, apps.map { it.component }, selectedCopyComponents)
    }
    var appearance by rememberSaveable(starting, template, stateSaver = appearanceSaver) { mutableStateOf(initial.appearance) }
    var drawer by rememberSaveable(starting, template, stateSaver = drawerSaver) { mutableStateOf(initial.drawer) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val draft = initial.copy(
        configured = true,
        separateDisplays = separate,
        appearance = appearance,
        drawer = drawer,
    )
    val titles = listOf("Choose starting layout", "Foldable configuration", "Appearance",
        "App drawer and search", "Sanctuary protection", "Preview and activate")
    val onBack: () -> Unit = { if (step > 0) step-- else actions.continueWithCurrentHome() }
    BackHandler(step > 0, onBack = onBack)
    LauncherPanel(titles[step], onBack) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Nova Launcher setup", style = MaterialTheme.typography.labelLarge)
            Text("Step ${step + 1} of ${titles.size}", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = (step + 1) / 6f, modifier = Modifier.fillMaxWidth())
        key(step) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        Text("Choose how your everyday Android Home starts. The private NovaOS workspace opens separately.")
                        LauncherChoice("Copy current Home", "Choose which existing apps to place on Home", starting == LauncherStartingLayout.COPY_CURRENT) {
                            starting = LauncherStartingLayout.COPY_CURRENT
                        }
                        if (starting == LauncherStartingLayout.COPY_CURRENT) {
                            Text("Samsung One UI keeps its exact positions, folders and widgets private. Pick the apps you want to recreate on Nova Home. Apps you leave unchecked stay in the drawer.",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = copySearch,
                                onValueChange = { copySearch = it.take(128) },
                                label = { Text("Find apps to copy") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val limit = LauncherGrid.STANDARD.columns(false) * LauncherGrid.STANDARD.rows
                            Text("${selectedCopyComponents.size} of $limit shortcuts selected",
                                style = MaterialTheme.typography.labelMedium)
                            apps.asSequence()
                                .filter { it.label.contains(copySearch.trim(), ignoreCase = true) }
                                .take(12)
                                .forEach { app ->
                                    val selected = app.component in selectedCopyComponents
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = selected,
                                            enabled = selected || selectedCopyComponents.size < limit,
                                            modifier = Modifier.testTag("launcher-copy-${app.component}"),
                                            onCheckedChange = { checked ->
                                                selectedCopyComponents = ArrayList(
                                                    if (checked) selectedCopyComponents + app.component
                                                    else selectedCopyComponents - app.component,
                                                )
                                            },
                                        )
                                        Text(app.label)
                                    }
                                }
                        }
                        LauncherChoice("Start Fresh", "A clean Home with a shortcut to NovaOS", starting == LauncherStartingLayout.START_FRESH) {
                            starting = LauncherStartingLayout.START_FRESH
                        }
                        LauncherChoice("Choose Template", "Preview a layout before applying it", starting == LauncherStartingLayout.TEMPLATE) {
                            starting = LauncherStartingLayout.TEMPLATE
                        }
                        if (starting == LauncherStartingLayout.TEMPLATE) {
                            LauncherOptions("Template", LauncherTemplate.entries, template, { template = it })
                            Text(when (template) {
                                LauncherTemplate.MINIMAL -> "A quieter Home with larger tiles and room to grow."
                                LauncherTemplate.CLASSIC -> "A familiar app grid and a dock with quick access to NovaOS."
                                LauncherTemplate.PRODUCTIVITY -> "A compact grid, extra search access and related apps together."
                                LauncherTemplate.GAMING -> "A midnight palette with a horizontal app drawer."
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                        LauncherLookPreview(draft, apps, decisions, compact = true)
                    }
                    1 -> {
                        LauncherChoice("Separate cover and unfolded layouts",
                            "Keep app pages, folders, docks and favourites for each display.", separate) { separate = true }
                        LauncherChoice("Shared responsive layout",
                            "Use one layout that adapts when you unfold your phone.", !separate) { separate = false }
                    }
                    2 -> {
                        LauncherLookPreview(draft, apps, decisions, compact = true)
                        LauncherAppearanceOptions(appearance, { appearance = it }, actions.chooseWallpaper)
                    }
                    3 -> LauncherDrawerSettings(drawer, { drawer = it })
                    4 -> {
                        LauncherPolicySummary(apps, decisions, launcherProtected, outsideProtection)
                        Text("Nova Launcher uses your existing Sanctuary approvals. While protection is active, an unapproved icon opens a protection screen. Android handles app access outside the launcher; Sanctuary can cover supported transitions while its authorised service is active.")
                        Text("Your approvals and background protection settings are preserved during setup.")
                        OutlinedButton(onClick = actions.reviewSanctuary) { Text("Review Sanctuary approved apps") }
                        TextButton(onClick = actions.emergencyPhone) { Text("Phone and emergency access") }
                    }
                    5 -> {
                        LauncherLookPreview(draft, apps, decisions, compact = false)
                        Text(
                            if (defaultHome) "Nova Launcher is currently Android’s Home app. Android’s Home settings can switch you back at any time."
                            else "You can keep your current Home or choose Nova Launcher in Android’s Home app settings.",
                        )
                        if (failed) Text("Your layout could not be saved. Try again.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            failed = !actions.save(draft)
                            if (!failed) actions.selectHome()
                        }, modifier = Modifier.fillMaxWidth().testTag("launcher-activate")) { Text("Use Nova Launcher") }
                        OutlinedButton(onClick = {
                            failed = !actions.save(draft)
                            if (!failed) actions.continueWithCurrentHome()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Continue using Samsung Home") }
                        TextButton(onClick = { step = 0; failed = false }) { Text("Return to setup") }
                    }
                }
            }
        }
        if (step < 5) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (step == 0) TextButton(onClick = actions.emergencyPhone) { Text("Phone and emergency") }
            else TextButton(onClick = { step-- }) { Text("Previous") }
            Button(onClick = { step++ }, modifier = Modifier.testTag("launcher-setup-next")) { Text("Next") }
        }
    }
}

@Composable
private fun LauncherChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LauncherLookPreview(
    state: NovaLauncherState,
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    compact: Boolean,
) {
    val shape = RoundedCornerShape(23.dp)
    val context = LocalContext.current
    val byComponent = remember(apps) { apps.associateBy { it.component } }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = if (compact) 165.dp else 234.dp).clip(shape)) {
        val wide = maxWidth >= LauncherExpandedWidth.dp
        val layout = state.layout(wide)
        val preset = when (state.appearance.wallpaper) {
            LauncherWallpaper.OCEAN, LauncherWallpaper.DEVICE -> "Aurora"
            LauncherWallpaper.MIDNIGHT -> "Midnight"
            LauncherWallpaper.PLUM -> "Dusk"
        }
        WallpaperSurface(Wallpaper(builtin = preset), context.filesDir, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NOVA LAUNCHER PREVIEW", color = Color.White, fontSize = 10.sp,
                letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold)
            Text("09:41", color = Color.White, fontSize = if (compact) 36.sp else 49.sp,
                lineHeight = if (compact) 38.sp else 51.sp, fontWeight = FontWeight.SemiBold)
            val count = state.appearance.grid.columns(wide)
            val items = layout.pages.firstOrNull().orEmpty().take(if (compact) count else count * 2)
            if (items.isNotEmpty()) CompositionLocalProvider(LocalContentColor provides Color.White) {
                LauncherHomeGrid(items, count, state.appearance, byComponent, decisions, {}, interactive = false)
            }
            if (state.appearance.dock != LauncherDock.HIDDEN) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = .15f)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    layout.dock.take(5).forEach { item ->
                        when (item) {
                            LauncherItem.Workspace -> Icon(Icons.Default.Lock, "Open NovaOS", Modifier.size(21.dp), tint = Color.White)
                            is LauncherItem.App -> byComponent[item.component]?.let { LauncherAppIcon(it, Modifier.size(21.dp)) }
                            is LauncherItem.Folder -> Icon(Icons.Default.Folder, item.name, Modifier.size(21.dp), tint = Color.White)
                            is LauncherItem.SmartFolder -> Icon(Icons.Default.Folder, item.name, Modifier.size(21.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LauncherPolicySummary(
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    protection: Boolean,
    outsideProtection: Boolean,
) {
    val packages = apps.distinctBy { it.packageName }
    val approved = packages.count { decisions[it.packageName] == Decision.APPROVED }
    val blocked = packages.count { decisions[it.packageName] == Decision.UNAPPROVED }
    val essential = packages.count { decisions[it.packageName] == Decision.ESSENTIAL }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(if (protection) "Sanctuary is active" else "Sanctuary is ready", style = MaterialTheme.typography.titleSmall)
            Text("Unapproved apps: Block during a Sanctuary session", style = MaterialTheme.typography.bodyMedium)
            Text("Apply Sanctuary rules to launcher: Enabled", style = MaterialTheme.typography.bodyMedium)
            Text("Guardian background overlay: Enabled during protected sessions", style = MaterialTheme.typography.bodyMedium)
            Text("Persistent shield: ${if (outsideProtection) "Enabled" else "Off"}", style = MaterialTheme.typography.bodyMedium)
            if (protection) Text("$approved approved · $blocked blocked · $essential essential",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun LauncherAppearanceOptions(
    value: LauncherAppearance,
    change: (LauncherAppearance) -> Unit,
    chooseWallpaper: () -> Unit,
) {
    LauncherOptions("Wallpaper", LauncherWallpaper.entries, value.wallpaper, { change(value.copy(wallpaper = it)) })
    TextButton(onClick = chooseWallpaper) { Text("Android wallpaper settings") }
    LauncherOptions("Theme", LauncherTheme.entries, value.theme, { change(value.copy(theme = it)) })
    LauncherOptions("Accent colour", LauncherAccent.entries, value.accent, { change(value.copy(accent = it)) })
    LauncherOptions("Icon shape", LauncherIconShape.entries, value.iconShape, { change(value.copy(iconShape = it)) })
    LauncherOptions("Icon size", LauncherIconSize.entries, value.iconSize, { change(value.copy(iconSize = it)) })
    LauncherToggle("App labels", value.labels) { change(value.copy(labels = it)) }
    LauncherOptions("Grid density", LauncherGrid.entries, value.grid, { change(value.copy(grid = it)) })
    LauncherOptions("Dock", LauncherDock.entries, value.dock, { change(value.copy(dock = it)) })
}

@Composable
internal fun LauncherDrawerSettings(value: LauncherDrawerOptions, change: (LauncherDrawerOptions) -> Unit) {
    LauncherOptions("Drawer direction", LauncherDrawerOrientation.entries, value.orientation, { change(value.copy(orientation = it)) })
    LauncherOptions("Search placement", LauncherSearchPlacement.entries, value.search, { change(value.copy(search = it)) })
    LauncherOptions("Sort apps", LauncherSort.entries, value.sort, { change(value.copy(sort = it)) })
    LauncherToggle("Show folders in drawer", value.folders) { change(value.copy(folders = it)) }
    LauncherToggle("Show frequently used apps", value.frequent) { change(value.copy(frequent = it)) }
    LauncherToggle("Search Android app shortcuts", value.shortcutSearch) { change(value.copy(shortcutSearch = it)) }
    if (value.shortcutSearch) Text("Android grants shortcut access when Nova Launcher is the default Home app. Shortcut search pauses during Sanctuary protection.",
        style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : Enum<T>> LauncherOptions(
    title: String,
    choices: List<T>,
    selected: T,
    change: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                FilterChip(selected == choice, onClick = { change(choice) },
                    label = { Text(choice.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) })
            }
        }
    }
}

@Composable
internal fun LauncherToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, trailingContent = {
        Switch(checked, onChange)
    })
}
