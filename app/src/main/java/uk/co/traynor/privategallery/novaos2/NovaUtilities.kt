package uk.co.traynor.privategallery.novaos2

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.home2.CoreWorkspaceHeader
import uk.co.traynor.privategallery.launcher.LauncherSmartTracking

private val coreAppDetails = mapOf(
    NovaCoreApp.NOTES to "Notebooks, checklists and private notes",
    NovaCoreApp.TASKS to "Due dates, priorities and reminders",
    NovaCoreApp.CALENDAR to "Local events and schedule",
    NovaCoreApp.FILES to "Find and organise permitted documents",
    NovaCoreApp.CLIPBOARD to "Save and reuse snippets you choose",
)

@Composable
internal fun NovaUtilities(host: NovaSystemHost) {
    val context = LocalContext.current
    var updateSummary by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<UpdateSummary?>(null) }
    var versionLoaded by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycle, context) {
        if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED) || !host.foregroundAllowed()) return@LaunchedEffect
        version = withContext(Dispatchers.IO) { runCatching {
            val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            val history = runCatching {
                NovaUpdateHistory.recordObserved(NovaSystemConfig(context, "observed-updates"), installed)
            }.getOrNull()
            UpdateSummary("${installed.versionName.orEmpty()} · Build ${installed.longVersionCode}", installed.lastUpdateTime,
                history)
        }.getOrNull() }
        versionLoaded = true
    }
    val items = coreAppDetails.map { (app, caption) ->
        UtilityItem(app.label, caption) { host.openCoreApp(app) }
    } + listOf(
        UtilityItem("Nova Contacts", "Search and dial device contacts") {
            host.foregroundAllowed() && openNovaPrimaryPresentation(context) { _, back, canUse ->
                NovaContactDirectory(back, canUse)
            }
        },
        UtilityItem("Nova Share Hub", "Save or share text you review") {
            host.foregroundAllowed() && openNovaPrimaryPresentation(context) { space, back, canUse ->
                NovaShareHub(space, null, back, canUse)
            }
        },
        UtilityItem("Nova App Usage", "Review observed screen time on this device") {
            if (!host.foregroundAllowed()) false else {
                val excluded = LauncherSmartTracking(context).state.value.excluded
                val excludedPackages = host.apps.filter { it.component in excluded }.map { it.packageName }.toSet()
                val eligible = host.apps.asSequence().filter { app ->
                    app.profileSerial == null && app.packageName != context.packageName &&
                        !app.packageName.startsWith("nova:") && app.packageName !in excludedPackages &&
                        host.decisions[app.component]?.canLaunch != false
                }.associate { it.packageName to it.label }
                val workingEnabled = NovaSystemId.WORKING in host.available
                openNovaPrimaryPresentation(context) { _, back, canUse -> NovaAppUsage(eligible, workingEnabled, back, canUse) }
            }
        },
        UtilityItem("Nova Update Centre", "Installed versions and verified updater") {
            updateSummary = true
            true
        },
    )
    BackHandler(enabled = updateSummary) { updateSummary = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("nova-utilities"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (updateSummary) {
            TextButton(onClick = { updateSummary = false; error = null }) { Text("Back to utilities") }
            NovaUpdateSummary(version, versionLoaded) {
                error = if (host.openUpdates()) null else "Updates & About could not be opened."
            }
        } else {
            UtilitiesGrid(items, host.wide) { name -> error = "Could not open $name here." }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

internal data class UtilityItem(val name: String, val detail: String, val open: () -> Boolean)

@Composable
internal fun UtilitiesGrid(items: List<UtilityItem>, wide: Boolean, onUnavailable: (String) -> Unit) {
    val columns = if (wide) 2 else 1
    items.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { item -> Card(
                onClick = { if (!item.open()) onUnavailable(item.name) },
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            } }
            if (row.size < columns) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }
}

private data class UpdateSummary(val installed: String, val updatedAt: Long, val history: List<NovaObservedVersion>?)

@Composable
private fun NovaUpdateSummary(summary: UpdateSummary?, loaded: Boolean, openVerifiedUpdater: () -> Unit) {
    NovaSystemSection("Installed NovaOS") {
        Text(summary?.installed ?: if (loaded) "Installed version unavailable" else "Reading installed version…")
        summary?.updatedAt?.takeIf { it > 0 }?.let { Text("Last Android package update · ${localUpdateDate(it)}") }
        Button(onClick = openVerifiedUpdater) { Text("Open Updates & About") }
    }
    NovaSystemSection("Observed version history") {
        val log = summary?.history
        when {
            log == null -> Text(if (loaded) "Installed version history unavailable." else "Reading installed versions…")
            log.isEmpty() -> Text("No installed versions have been observed yet.")
            else -> for (item in log) Text("${item.version} · Build ${item.build} · ${localUpdateDate(item.installedAt)}")
        }
    }
}

private fun localUpdateDate(whenMs: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .format(Instant.ofEpochMilli(whenMs).atZone(ZoneId.systemDefault()))

private data class ScreenTimeSummary(
    val usage: NovaUsageReport?,
    val sanctuarySessions: Int,
    val sanctuaryMillis: Long,
    val lastShiftMillis: Long?,
)

internal fun novaLastShiftMillis(value: String?): Long? = runCatching {
    val record = JSONObject(value ?: return null)
    if (record.getInt("version") != 1) return null
    val duration = record.optJSONObject("previous")?.opt("duration")
    when (duration) {
        is Long -> duration
        is Int -> duration.toLong()
        else -> null
    }?.takeIf { it in 0..64_800_000L }
}.getOrNull()

@Composable
private fun NovaAppUsage(apps: Map<String, String>, workingEnabled: Boolean, onBack: () -> Unit, canUse: () -> Boolean) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var days by rememberSaveable { mutableIntStateOf(1) }
    var permission by remember { mutableStateOf(SanctuaryGuardian.hasUsageAccess(context)) }
    var request by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<ScreenTimeSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val showDetails = resumed && canUse()
    LaunchedEffect(resumed) {
        if (resumed) permission = SanctuaryGuardian.hasUsageAccess(context)
        else { report = null; request = 0; failed = false }
    }
    LaunchedEffect(request, days, showDetails, permission) {
        report = null
        failed = false
        if (!showDetails || request == 0) return@LaunchedEffect
        loading = true
        try {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching {
                    val zone = ZoneId.systemDefault()
                    val end = System.currentTimeMillis()
                    val start = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                        .minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
                    val sessions = SanctuaryStore.get(context).history().filter { it.startedAt < end && it.endedAt > start }
                    val time = sessions.sumOf { (minOf(end, it.endedAt) - maxOf(start, it.startedAt)).coerceAtLeast(0L) }
                    val exclusions = sessions.map { NovaUsageExclusion(it.startedAt, it.endedAt) }
                    val usage = if (permission && apps.isNotEmpty()) {
                        queryNovaUsage(context, start, end, apps.keys, zone, exclusions)
                    } else null
                    val lastShift = if (workingEnabled) runCatching {
                        novaLastShiftMillis(NovaSystemConfig(context, "working-mode").read())
                    }.getOrNull() else null
                    ScreenTimeSummary(usage, sessions.size, time, lastShift)
                }.getOrNull()
            }
            if (canUse()) {
                report = snapshot
                failed = snapshot == null
            }
        } finally { loading = false }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("nova-app-usage"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoreWorkspaceHeader("Nova App Usage", true, onBack)
        NovaSystemSection("On-device activity") {
            Text("Read recent Android foreground events on request. The report stays in memory while this screen is open.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = days == 1, onClick = { days = 1 }, label = { Text("Today") })
                FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text("Last 7 days") })
            }
            if (!permission) OutlinedButton(enabled = showDetails, onClick = {
                if (runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }.isFailure) {
                    message = "Android Usage Access settings are unavailable."
                } else message = null
            }) { Text("Open Usage Access settings") }
            Button(onClick = { if (showDetails && canUse()) request++ }, enabled = showDetails && !loading) {
                Text(if (report == null) "Read activity for this period" else "Refresh this period")
            }
            if (loading) Text("Reading activity…")
            if (failed) Text("Activity could not be read.")
            message?.let { Text(it) }
        }
        report?.takeIf { showDetails }?.let { snapshot ->
            NovaSystemSection("Screen time") {
                when {
                    !permission -> Text("Android Usage Access is off.")
                    snapshot.usage == null -> Text("No screen-time data is available for the selected apps.")
                    snapshot.usage.dailyMillis.isEmpty() -> Text("No confirmed foreground time was observed in this period.")
                    else -> {
                        Text("${utilityDuration(snapshot.usage.dailyMillis.values.sum())} observed across visible apps")
                        Text("Android may omit events outside its retention window.", style = MaterialTheme.typography.bodySmall)
                        for ((date, time) in snapshot.usage.dailyMillis.toSortedMap()) {
                            Text("${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} · ${utilityDuration(time)}")
                        }
                        for ((id, time) in snapshot.usage.appMillis.entries.sortedByDescending { it.value }.take(12)) {
                            Text("${apps.getValue(id)} · ${utilityDuration(time)}")
                        }
                    }
                }
            }
            NovaSystemSection("Sanctuary") {
                Text("${snapshot.sanctuarySessions} completed sessions · ${utilityDuration(snapshot.sanctuaryMillis)}")
            }
            if (workingEnabled) NovaSystemSection("Working Mode") {
                Text(snapshot.lastShiftMillis?.let { "Last completed shift · ${utilityDuration(it)}" }
                    ?: "Completed shift duration is unavailable.")
            }
        }
    }
}
