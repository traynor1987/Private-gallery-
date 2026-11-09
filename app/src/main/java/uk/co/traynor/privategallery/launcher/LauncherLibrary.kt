package uk.co.traynor.privategallery.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.home2.Wallpaper
import uk.co.traynor.privategallery.home2.WallpaperSurface

private data class LibraryView(val category: LauncherCategory?, val searching: Boolean)

@Composable
internal fun LauncherLibraryPinSettings(
    pinnedCategories: Set<LauncherCategory>,
    onChange: (Set<LauncherCategory>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("Pin categories") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Pin categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep selected categories visible in App Library even when they’re empty.")
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(LauncherCategory.entries.size) { index ->
                        val category = LauncherCategory.entries[index]
                        val pinned = category in pinnedCategories
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(pinned, role = Role.Checkbox) {
                                onChange(if (it) pinnedCategories + category else pinnedCategories - category)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = pinned, onCheckedChange = null)
                            Text(category.label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } },
    )
}

@Composable
internal fun LauncherLibrary(
    appearance: LauncherAppearance,
    groups: List<LauncherLibraryGroup>,
    selectedCategory: LauncherCategory?,
    query: String,
    onQuery: (String) -> Unit,
    onCategory: (LauncherCategory) -> Unit,
    onBack: () -> Unit,
    onDrawer: () -> Unit,
    onApp: (LauncherApp) -> Unit,
    decisions: Map<String, Decision>,
    gestures: LauncherIconGestures,
    recentHistoryEnabled: Boolean = true,
    categoryTitle: String? = null,
    pinnedApps: List<LauncherApp> = emptyList(),
    pinnedCategories: Set<LauncherCategory> = emptySet(),
    embedded: Boolean = false,
) {
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val appsByComponent = remember(groups, pinnedApps) {
        (groups.flatMap { it.apps } + pinnedApps).associateBy(LauncherApp::component)
    }
    val visibleGroups = remember(groups, pinnedCategories, pinnedApps) {
        launcherLibraryVisibleGroups(groups, pinnedCategories, pinnedApps)
    }
    val categoryGrid = rememberLazyGridState()
    val term = query.trim()
    val wallpaper = when (appearance.wallpaper) {
        LauncherWallpaper.OCEAN -> "Aurora"
        LauncherWallpaper.MIDNIGHT -> "Midnight"
        LauncherWallpaper.PLUM -> "Dusk"
        LauncherWallpaper.DEVICE -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("launcher-app-library")) {
        val wide = maxWidth >= LauncherExpandedWidth.dp
        val columns = if (wide) (maxWidth.value / 194f).toInt().coerceAtLeast(3) else 2
        if (!embedded) {
            if (wallpaper == null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .54f)))
            else WallpaperSurface(Wallpaper(builtin = wallpaper, dim = .24f),
                LocalContext.current.filesDir, Modifier.fillMaxSize())
        }

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                Modifier.fillMaxSize().then(if (embedded) Modifier else Modifier.safeDrawingPadding()).imePadding()
                    .padding(horizontal = if (wide) 24.dp else 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        focus.clearFocus()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text(
                        categoryTitle ?: selectedCategory?.label ?: "App Library",
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { focus.clearFocus(); onDrawer() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        modifier = Modifier.testTag("launcher-library-drawer"),
                    ) {
                        Icon(Icons.Default.Apps, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Drawer")
                    }
                }

                TextField(
                    query, { onQuery(it.take(128)) },
                    modifier = Modifier.fillMaxWidth().testTag("launcher-library-search")
                        .border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(28.dp)),
                    placeholder = { Text("Search apps", color = Color.White.copy(alpha = .8f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, "Clear search", tint = Color.White)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = .20f),
                        unfocusedContainerColor = Color.White.copy(alpha = .13f),
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                    ),
                )

                val renderApp: @Composable (LauncherApp, Modifier, Boolean) -> Unit = { app, modifier, showLabel ->
                    LauncherTile(
                        LauncherItem.App(app.component), appearance, appsByComponent, decisions,
                        onClick = { onApp(app) }, gestures = gestures,
                        modifier = modifier, showLabel = showLabel, fullTileIcon = !showLabel,
                    )
                }

                AnimatedContent(
                    targetState = LibraryView(selectedCategory, term.isNotEmpty()),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(160, delayMillis = 40)) + scaleIn(tween(200), initialScale = .98f))
                            .togetherWith(fadeOut(tween(90)))
                    },
                    label = "App Library navigation",
                ) { view ->
                    if (view.category == null && !view.searching) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns), state = categoryGrid, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (visibleGroups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("No available apps", Modifier.padding(16.dp), textAlign = TextAlign.Center)
                            }
                            items(visibleGroups, key = { it.category.name }) { group ->
                                LibraryCategoryCard(group, decisions, renderApp) {
                                    focus.clearFocus()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCategory(group.category)
                                }
                            }
                        }
                    } else {
                        val group = groups.firstOrNull { it.category == view.category }
                        val isRecents = view.category == LauncherCategory.RECENTLY_USED
                        val pinned = if (isRecents) pinnedApps else emptyList()
                        val recentMembers = group?.apps.orEmpty()
                        val matches = remember(group, view, term, appsByComponent, recentMembers, pinned) {
                            when {
                                !view.searching -> {
                                    val pinnedComponents = pinned.mapTo(mutableSetOf(), LauncherApp::component)
                                    recentMembers.filterNot { it.component in pinnedComponents }
                                }
                                view.category != null -> launcherLibraryMatches(recentMembers + pinned, term)
                                else -> launcherLibraryMatches(appsByComponent.values.toList(), term)
                            }
                        }
                        val emptyMessage = when {
                            view.searching -> "No apps match your search"
                            isRecents && !recentHistoryEnabled -> "App tracking is off"
                            isRecents -> "No recently used apps"
                            view.category == LauncherCategory.RECENTLY_ADDED -> "No apps added in the last 14 days"
                            view.category == LauncherCategory.SUGGESTIONS -> "No suggestions yet"
                            else -> "No available apps"
                        }
                        LibraryResults(matches, pinned.takeUnless { view.searching }.orEmpty(),
                            term, emptyMessage, renderApp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryResults(
    apps: List<LauncherApp>,
    pinned: List<LauncherApp>,
    term: String,
    emptyMessage: String,
    renderApp: @Composable (LauncherApp, Modifier, Boolean) -> Unit,
) {
    val results = rememberLazyGridState()
    LaunchedEffect(term) {
        if (term.isNotEmpty() && apps.isNotEmpty()) results.scrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 79.dp), state = results,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pinned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { LibrarySectionHeading("Pinned") }
            items(pinned, key = { "pinned:${it.component}" }) { app -> renderApp(app, Modifier, true) }
            item(span = { GridItemSpan(maxLineSpan) }) { LibrarySectionHeading("Recent") }
        }
        if (apps.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(emptyMessage, Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
            }
        }
        items(apps, key = LauncherApp::component) { app -> renderApp(app, Modifier, true) }
    }
}

@Composable
private fun LibrarySectionHeading(title: String) {
    Text(
        title, Modifier.padding(top = 6.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun LibraryCategoryCard(
    group: LauncherLibraryGroup,
    decisions: Map<String, Decision>,
    renderApp: @Composable (LauncherApp, Modifier, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val gap = 8.dp
    val count = group.apps.size
    val appCount = if (count == 1) "1 app" else "$count apps"
    Column(
        Modifier.fillMaxWidth().testTag("launcher-library-category-${group.category.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.linearGradient(listOf(Color(0x59CFF8FF), Color(0x24D9E9FF), Color(0x33E8F1FF))))
                .border(1.dp, Brush.linearGradient(listOf(Color(0x99EDFCFF), Color(0x2EE1F1FF), Color(0x73E1F1FF))), shape)
                .semantics { contentDescription = "Open ${group.category.label}, $appCount" }
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            val tileSize = ((maxWidth - gap) / 2).coerceAtMost(82.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    Modifier.fillMaxWidth().height(tileSize),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    group.apps.take(2).forEach { app -> renderApp(app, Modifier.size(tileSize), false) }
                    if (count == 0) Icon(Icons.Default.Folder, null, Modifier.size(30.dp), tint = Color.White.copy(alpha = .80f))
                }
                if (count > 2) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+${count - 2}", color = Color.White.copy(alpha = .84f),
                            style = MaterialTheme.typography.labelMedium)
                        val previewShape = RoundedCornerShape(13.dp)
                        LauncherFolderPreview(
                            group.apps.drop(2),
                            Modifier.size(tileSize * .76f).clip(previewShape)
                                .background(Color.Black.copy(alpha = .16f)).padding(5.dp),
                            decisions,
                        )
                    }
                }
            }
        }
        Text(
            group.category.label, Modifier.fillMaxWidth().padding(top = 7.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Decorative preview. The enclosing tile owns click and accessibility actions. */
@Composable
internal fun LauncherFolderPreview(
    apps: List<LauncherApp>,
    modifier: Modifier = Modifier,
    decisions: Map<String, Decision> = emptyMap(),
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight)
        if (apps.isEmpty()) {
            Icon(
                Icons.Default.Folder, null,
                Modifier.size(size * .64f), tint = Color.White.copy(alpha = .82f),
            )
        } else {
            val spacing = size * .08f
            val cell = (size - spacing) / 2
            Column(Modifier.size(size), verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically)) {
                apps.take(4).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)) {
                        row.forEach { app ->
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(cell * .26f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                LauncherAppIcon(app, Modifier.fillMaxSize())
                                if (decisions[app.packageName]?.canLaunch == false) {
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
                                    Icon(Icons.Default.Lock, null, Modifier.size(cell * .54f)
                                        .background(Color.Black.copy(alpha = .4f), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
