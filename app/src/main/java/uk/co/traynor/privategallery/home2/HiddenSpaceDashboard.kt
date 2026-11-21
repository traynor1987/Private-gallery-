package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext

private val hiddenSpaceApps = listOf(
    LaunchApp("browser-v1", "Browser V1", "browser"),
    LaunchApp(NovaCoreApp.NOTES.route, "Notes", "edit"),
    LaunchApp(NovaCoreApp.TASKS.route, "Tasks", "tasks"),
    LaunchApp(NovaCoreApp.CALENDAR.route, "Calendar", "calendar"),
    LaunchApp("hidden-files", "Files", "folder"),
    LaunchApp(NovaCoreApp.CLIPBOARD.route, "Clipboard", "clipboard"),
    LaunchApp("vault", "Gallery", "photos"),
    LaunchApp("camera", "Camera", "camera"),
    LaunchApp("hidden-downloads", "Downloads", "download"),
    LaunchApp("hidden-tor", "Tor Browser", "browser"),
)

@Composable
internal fun HiddenSpaceDashboard(
    space: SpaceContext,
    wallpaper: Wallpaper,
    filesDir: File,
    onLaunch: (String) -> Unit,
    onDrawer: () -> Unit,
    onQuick: () -> Unit,
    onLock: () -> Unit,
    onPanic: () -> Unit,
    onReturnToPrimary: (() -> Unit)?,
    onOpen: (NovaCoreLink) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember(space) { mutableStateOf(false) }
    var reminders by remember(space) { mutableStateOf<List<Pair<String, NovaCoreLink.Record>>>(emptyList()) }
    LaunchedEffect(space) {
        while (space.current()) {
            val pending = withContext(Dispatchers.IO) {
                runCatching {
                    val now = Instant.now()
                    val earliest = now.minus(Duration.ofHours(12))
                    val zone = ZoneId.systemDefault()
                    fun due(schedule: NovaReminderSchedule?): Boolean =
                        schedule?.nextAfter(earliest, zone)?.let { it <= now } == true
                    buildList {
                        NovaTaskLibrary(space).read().tasks.filter { due(taskReminderSchedule(it)) }.forEach {
                            add(it.title to NovaCoreLink.Record(NovaCoreApp.TASKS, it.id))
                        }
                        NovaCalendarLibrary(space).read().events.filter { due(calendarReminderSchedule(it)) }.forEach {
                            add(it.title to NovaCoreLink.Record(NovaCoreApp.CALENDAR, it.id))
                        }
                    }
                }.getOrDefault(emptyList())
            }
            withContext(Dispatchers.Main.immediate) {
                space.publishIfCurrent { reminders = pending }
            }
            delay(60_000)
        }
    }
    Box(Modifier.fillMaxSize().testTag("preview-home")) {
        WallpaperSurface(wallpaper, filesDir, Modifier.fillMaxSize())
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("hidden-space-apps"),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hidden Space", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text("Unlocked", color = Color.White.copy(alpha = 0.8f))
                    }
                    IconButton(
                        onClick = { onLaunch("wallpaper") },
                        modifier = Modifier.testTag("app-wallpaper"),
                    ) { Icon(Icons.Default.Wallpaper, "Choose wallpaper", tint = Color.White) }
                    IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock Space", tint = Color.White) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Home menu", tint = Color.White)
                        }
                        PanicHomeDropdownMenu(menuOpen, { menuOpen = false }) {
                            onReturnToPrimary?.let { returnToPrimary ->
                                DropdownMenuItem(text = { Text("Return to Primary") }, onClick = {
                                    menuOpen = false
                                    returnToPrimary()
                                })
                            }
                            DropdownMenuItem(text = { Text("Panic Exit") }, onClick = {
                                menuOpen = false
                                onPanic()
                            })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onDrawer, modifier = Modifier.testTag("open-apps")) {
                        Icon(Icons.Default.Apps, "Open apps", tint = Color.White)
                    }
                    IconButton(onClick = { onLaunch("search") }) {
                        Icon(Icons.Default.Search, "Open Nova Search", tint = Color.White)
                    }
                    IconButton(onClick = onQuick) {
                        Icon(Icons.Default.Tune, "Open Quick Panel", tint = Color.White)
                    }
                }
            }
            if (reminders.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Due reminders", style = MaterialTheme.typography.titleSmall)
                    reminders.take(5).forEach { (label, record) ->
                        TextButton(onClick = { if (space.current()) onOpen(record) }) { Text(label) }
                    }
                }
            }
            items(hiddenSpaceApps, key = { it.id }) { app ->
                CoreWorkspaceCard(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLaunch(app.id)
                    },
                    enabled = true,
                    modifier = Modifier.testTag("hidden-app-${app.id}"),
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, 42.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(app.name, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                NovaCoreAppActions {
                    TextButton(onClick = { onLaunch("hidden-search") }) { Text("Search Hidden Space") }
                    TextButton(onClick = { onLaunch("hidden-workspace") }) { Text("Workspace") }
                }
            }
        }
    }
}
