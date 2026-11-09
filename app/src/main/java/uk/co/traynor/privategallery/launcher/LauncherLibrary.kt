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
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
) {
    val focus = LocalFocusManager.current
    val appsByComponent = remember(groups, pinnedApps) {
        (groups.flatMap { it.apps } + pinnedApps).associateBy(LauncherApp::component)
    }
    val visibleGroups = remember(groups) {
        groups.filter { it.apps.isNotEmpty() || it.category in LauncherCategory.SUGGESTIONS..LauncherCategory.NOVA_OS }
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
        if (wallpaper == null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .54f)))
        else WallpaperSurface(Wallpaper(builtin = wallpaper, dim = .24f),
            LocalContext.current.filesDir, Modifier.fillMaxSize())

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding()
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
                    modifier = Modifier.fillMaxWidth().testTag("launcher-library-search"),
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
                        focusedContainerColor = Color.Black.copy(alpha = .28f),
                        unfocusedContainerColor = Color.Black.copy(alpha = .22f),
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                    ),
                )

                val renderApp: @Composable (LauncherApp, Modifier, Boolean) -> Unit = { app, modifier, showLabel ->
                    LauncherTile(
                        LauncherItem.App(app.component), appearance, appsByComponent, decisions,
                        onClick = { onApp(app) }, gestures = gestures,
                        modifier = modifier, showLabel = showLabel,
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
                            items(visibleGroups, key = { it.category.name }) { group ->
                                LibraryCategoryCard(group, decisions, renderApp) {
                                    focus.clearFocus()
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
    val shape = RoundedCornerShape(26.dp)
    val gaps = Arrangement.spacedBy(4.dp)
    Column(
        Modifier.fillMaxWidth().testTag("launcher-library-category-${group.category.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.White.copy(alpha = .09f))))
                .border(1.dp, Color.White.copy(alpha = .19f), shape).padding(9.dp),
            verticalArrangement = gaps,
        ) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gaps) {
                repeat(2) { index ->
                    LibraryCategoryPosition(group.apps.getOrNull(index), Modifier.weight(1f), renderApp)
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = gaps) {
                LibraryCategoryPosition(group.apps.getOrNull(2), Modifier.weight(1f), renderApp)
                val appCount = if (group.apps.size == 1) "1 app" else "${group.apps.size} apps"
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(17.dp))
                        .semantics { contentDescription = "Open ${group.category.label}, $appCount" }
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LauncherFolderPreview(
                        group.apps.drop(3), Modifier.fillMaxSize(), decisions,
                        showCategoryGlyph = true,
                    )
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

@Composable
private fun LibraryCategoryPosition(
    app: LauncherApp?,
    modifier: Modifier,
    renderApp: @Composable (LauncherApp, Modifier, Boolean) -> Unit,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        if (app != null) renderApp(app, Modifier.fillMaxHeight().wrapContentHeight(), false)
    }
}

/** Decorative preview. The enclosing tile owns click and accessibility actions. */
@Composable
internal fun LauncherFolderPreview(
    apps: List<LauncherApp>,
    modifier: Modifier = Modifier,
    decisions: Map<String, Decision> = emptyMap(),
    showCategoryGlyph: Boolean = false,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight)
        if (apps.isEmpty()) {
            Icon(
                if (showCategoryGlyph) Icons.Default.Apps else Icons.Default.Folder, null,
                Modifier.size(size * .64f), tint = Color.White.copy(alpha = .82f),
            )
        } else {
            val spacing = size * .06f
            val cell = (size - spacing) / 2
            Column(Modifier.size(size), verticalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        repeat(2) { column ->
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(cell * .26f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val app = apps.getOrNull(row * 2 + column)
                                if (app != null) {
                                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .14f)))
                                    LauncherAppIcon(app, Modifier.fillMaxSize(.90f))
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
}
