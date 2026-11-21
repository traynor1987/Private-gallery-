package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    onLaunch: (String) -> Unit,
    onLock: () -> Unit,
    onPanic: () -> Unit,
    onReturnToPrimary: (() -> Unit)?,
    onOpen: (NovaCoreLink) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
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
                        Text("Hidden Space", style = MaterialTheme.typography.headlineMedium)
                        Text("Unlocked", color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock Space") }
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
                    TextButton(onClick = { onLaunch("search") }) { Text("Search Hidden Space") }
                    TextButton(onClick = { onLaunch("hidden-workspace") }) { Text("Workspace") }
                    onReturnToPrimary?.let { TextButton(onClick = it) { Text("Return to Primary") } }
                    TextButton(onClick = onPanic) { Text("Panic Exit") }
                }
            }
        }
    }
}
