package uk.co.traynor.privategallery.novaos2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.health.connect.HealthConnectManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.home2.BreathingExercise
import uk.co.traynor.privategallery.home2.MindData
import uk.co.traynor.privategallery.home2.MindEntries
import uk.co.traynor.privategallery.home2.NovaReminders
import uk.co.traynor.privategallery.intelligence.intelligenceDeviceReady
import uk.co.traynor.privategallery.ui.CredentialCaptureProtection

/** The launcher route contains no health data. Open it through a fresh Primary presentation. */
@Composable
internal fun NovaHealthMind(host: NovaSystemHost) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    NovaSystemSection("Private workspace") {
        Text("Track daily measurements, habits and mood. Your entries are encrypted in Primary Space.")
        Button(onClick = {
            failed = !host.foregroundAllowed() || !openNovaPrimaryPresentation(context) { space, back, allowed ->
                HealthMindScreen(space, back, allowed)
            }
        }) { Text("Open Health & Mind") }
        if (failed) Text("Unlock your phone and try again when NovaOS can open the private workspace.")
    }
}

private enum class HealthTab(val label: String) {
    TODAY("Today"), WEEK("Weekly review"), MOOD("Mood"), JOURNAL("Journal"), BREATHE("Breathe"), DATA("Data & sources"),
}

private enum class HealthConfirmation(val title: String, val message: String, val button: String) {
    ERASE_HEALTH("Delete the health log?", "Delete all manual measurements, habits and personal goals from this device. Your journal and calendar are separate.", "Delete health log"),
    ERASE_MIND("Delete mood and journal entries?", "Delete all existing Primary Space mood and journal entries. The health log and existing exports are separate.", "Delete entries"),
    EXPORT("Export a readable data copy?", "The selected file will contain your manual health log, mood and journal entries as readable JSON. Choose where it can be stored. A cloud destination may sync this copy.", "Choose destination"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HealthMindScreen(
    space: SpaceContext,
    onBack: () -> Unit,
    canUse: () -> Boolean = { space.current() },
    onBackup: (() -> Unit)? = null,
) {
    if (space.spaceId != "primary" || !space.current()) return
    val sanctuaryStore = remember(space) { SanctuaryStore.get(space.baseContext) }
    val sanctuary by sanctuaryStore.current.collectAsState()
    if (sanctuary != null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            Text("Health & Mind is paused during Sanctuary.")
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }
    CredentialCaptureProtection()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val base = space.baseContext
    val active by rememberUpdatedState(canUse)
    fun authorized() = space.current() && active() && intelligenceDeviceReady(base) && sanctuaryStore.current.value == null &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    val scope = rememberCoroutineScope()
    val store = remember(space) { HealthRecordStore(space) }
    val mind = remember(space) { runCatching { MindData(space) }.getOrNull() }
    val routine = remember(space) { HealthSleepRoutine(space) }
    var tab by rememberSaveable { mutableStateOf(HealthTab.TODAY) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var dayOffset by rememberSaveable { mutableStateOf(0) }
    val day = today.minusDays(dayOffset.toLong())
    var records by remember(space) { mutableStateOf<HealthRecords?>(null) }
    var loading by remember(space) { mutableStateOf(true) }
    var routineTime by remember(space) { mutableStateOf<LocalTime?>(null) }
    var routineAvailable by remember(space) { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf<HealthStepsResult?>(null) }
    var stepJob by remember { mutableStateOf<Job?>(null) }
    val healthConnectAvailable = remember(base) { base.getSystemService(HealthConnectManager::class.java) != null }
    var stepPermission by remember { mutableStateOf(healthStepsPermissionGranted(base)) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var confirmation by remember { mutableStateOf<HealthConfirmation?>(null) }
    var metricDialog by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf(false) }
    var habitDialog by remember { mutableStateOf(false) }
    var routineDialog by remember { mutableStateOf(false) }
    var habitToDelete by remember { mutableStateOf<HealthHabit?>(null) }

    val requestStepsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        stepPermission = granted && healthStepsPermissionGranted(base)
        if (!stepPermission) steps = HealthStepsResult.PermissionDenied
    }
    val requestNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = NovaReminders.canNotify(base)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        exportUri = uri
    }

    DisposableEffect(lifecycle, space) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    today = LocalDate.now()
                    stepPermission = healthStepsPermissionGranted(base)
                    notifications = NovaReminders.canNotify(base)
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    stepJob?.cancel()
                    steps = null
                    confirmation = null
                    metricDialog = false
                    goalDialog = false
                    habitDialog = false
                    routineDialog = false
                    habitToDelete = null
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stepJob?.cancel()
        }
    }
    LaunchedEffect(space, resumed) {
        if (!resumed || !authorized()) return@LaunchedEffect
        val log = withContext(Dispatchers.IO) { runCatching { store.read() } }
        records = log.getOrNull()
        loading = false
        if (log.isFailure) error = "The health log could not be read. Existing data has been preserved."
        val result = withContext(Dispatchers.IO) { runCatching { routine.time() } }
        routineAvailable = result.isSuccess
        routineTime = result.getOrNull()
    }

    suspend fun changeRecords(change: (HealthRecords) -> HealthRecords): HealthRecords = withContext(Dispatchers.IO) {
        check(authorized())
        store.update { current ->
            check(authorized())
            change(current)
        }
    }

    fun act(failure: String, change: suspend () -> Unit) {
        if (!authorized() || busy) return
        busy = true
        error = null
        scope.launch {
            try { change() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = failure }
            finally { busy = false }
        }
    }

    fun readSteps() {
        if (!authorized() || !healthConnectAvailable || stepJob?.isActive == true) return
        if (!healthStepsPermissionGranted(base)) {
            requestStepsPermission.launch(HEALTH_READ_STEPS)
            return
        }
        stepJob = scope.launch {
            try {
                val result = withTimeoutOrNull(8000) { readHealthSteps(base) } ?: HealthStepsResult.Unavailable
                if (authorized()) steps = result
            } finally {
                stepJob = null
            }
        }
    }

    fun saveRoutine(time: LocalTime?) = act("The sleep routine could not be saved. Check Nova Calendar and try again.") {
        val scheduled = withContext(Dispatchers.IO) {
            check(authorized())
            routine.setTime(time, today, ZoneId.systemDefault())
        }
        routineTime = time
        if (!scheduled) error = "The Calendar change was saved. NovaOS could not refresh scheduled reminders."
    }

    LaunchedEffect(exportUri, resumed, busy) {
        val uri = exportUri ?: return@LaunchedEffect
        if (!resumed || busy) return@LaunchedEffect
        exportUri = null
        if (!authorized()) {
            error = "Export expired while the workspace was locked. Unlock it and start a new export."
            return@LaunchedEffect
        }
        act("The export could not be completed. Check the selected destination for a partial copy.") {
            val mindData = checkNotNull(mind)
            val entries = mindData.entries("primary")
            check(mindData.error?.startsWith("Unable to read") != true)
            withContext(Dispatchers.IO) {
                writeHealthExport(space, uri, store.read(), entries, ::authorized)
            }
            error = "Export saved to the selected location."
        }
    }

    BackHandler(enabled = tab != HealthTab.TODAY) { tab = HealthTab.TODAY }
    Surface(Modifier.fillMaxSize().testTag("nova-health-mind")) {
        Column(Modifier.safeDrawingPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text("Health & Mind", style = MaterialTheme.typography.headlineSmall)
                    Text("Encrypted · Primary Space", style = MaterialTheme.typography.labelMedium)
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthTab.entries.forEach { page ->
                    FilterChip(tab == page, { tab = page }, { Text(page.label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            error?.let { Text(it, Modifier.padding(horizontal = 20.dp).testTag("health-status")) }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(key(tab) { rememberScrollState() }).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val log = records
                when (tab) {
                    HealthTab.TODAY -> if (log != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { dayOffset++ }, enabled = dayOffset < HealthRecords.MAX_DAYS) { Text("Earlier") }
                            Text(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { dayOffset-- }, enabled = dayOffset > 0) { Text("Later") }
                        }
                        HealthColumns(
                            first = {
                                NovaSystemSection("Manual measurements") {
                                    val values = log.days[day]?.measurements.orEmpty()
                                    if (values.isEmpty()) Text("No measurements recorded for this day.")
                                    for (metric in HealthMetric.entries) values[metric]?.let { HealthValue(metric.label, metric.format(it)) }
                                    Button(onClick = { metricDialog = true }, enabled = !busy, modifier = Modifier.testTag("health-log-measurement")) { Text("Log a measurement") }
                                }
                                NovaSystemSection("Sleep routine") {
                                    if (!routineAvailable) Text("Nova Calendar is unavailable.")
                                    else {
                                        Text(routineTime?.let { "Daily at ${it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))} in Nova Calendar." } ?: "No routine set.")
                                        Text(if (notifications) "Reminders use NovaOS notification settings." else "Notifications are disabled. Your routine can still be saved to Nova Calendar.")
                                        TextButton(onClick = { routineDialog = true }, enabled = !busy) { Text(if (routineTime == null) "Set a daily reminder" else "Change time") }
                                        if (routineTime != null) TextButton(onClick = { saveRoutine(null) }, enabled = !busy) { Text("Remove reminder") }
                                        if (!notifications) TextButton(onClick = {
                                            if (base.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                                if (!runCatching { base.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, base.packageName)) }.isSuccess) {
                                                    error = "Android notification settings could not be opened."
                                                }
                                            } else requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }) { Text("Notification settings") }
                                    }
                                }
                            },
                            second = {
                                NovaSystemSection("Personal goal") {
                                    Text(log.goal.ifEmpty { "No personal goal set." })
                                    TextButton(onClick = { goalDialog = true }, enabled = !busy) { Text(if (log.goal.isBlank()) "Set a goal" else "Edit goal") }
                                }
                                NovaSystemSection("Daily habits") {
                                    if (log.habits.isEmpty()) Text("Add a habit you’d like to track.")
                                    for (habit in log.habits) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            val checked = habit.id in log.days[day]?.habits.orEmpty()
                                            Checkbox(checked, onCheckedChange = {
                                                act("The habit could not be updated.") { records = changeRecords { it.toggleHabit(day, habit.id) } }
                                            }, enabled = !busy,
                                                modifier = Modifier.semantics { contentDescription = "Track ${habit.name}" })
                                            Text(habit.name, Modifier.weight(1f))
                                            TextButton(onClick = { habitToDelete = habit }, enabled = !busy,
                                                modifier = Modifier.semantics { contentDescription = "Remove ${habit.name}" }) {
                                                Text("Remove", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                    TextButton(onClick = { habitDialog = true }, enabled = !busy && log.habits.size < HealthRecords.MAX_HABITS) { Text("Add a habit") }
                                    if (log.habits.size >= HealthRecords.MAX_HABITS) Text("Habit limit reached.")
                                }
                            },
                        )
                    } else Text(if (loading) "Opening your health log…" else "Your health log is unavailable.")
                    HealthTab.WEEK -> if (log != null) HealthWeeklyReview(log, mind, space, today) else
                        Text(if (loading) "Opening your health log…" else "Your health log is unavailable.")
                    HealthTab.MOOD -> if (mind == null) Text("Mood entries are unavailable.") else MindEntries(mind, "primary", journal = false)
                    HealthTab.JOURNAL -> if (mind == null) Text("Your journal is unavailable.") else MindEntries(mind, "primary", journal = true)
                    HealthTab.BREATHE -> BreathingExercise()
                    HealthTab.DATA -> {
                        NovaSystemSection("Health Connect") {
                            if (!healthConnectAvailable) Text("Health Connect is unavailable on this device.")
                            else {
                                Text(if (stepPermission) "Step access granted. NovaOS reads today’s aggregate when you tap Read."
                                else "Step access is off. You can grant read-only access in Android.")
                                steps?.let { result ->
                                    Text(when (result) {
                                        is HealthStepsResult.Available -> "${result.steps} steps today · Health Connect"
                                        HealthStepsResult.None -> "Health Connect has no step records for today."
                                        HealthStepsResult.PermissionDenied -> "Step access was denied. Manual entry is still available."
                                        HealthStepsResult.Unavailable -> "Health Connect couldn’t read steps. Try again when the service is available."
                                    }, Modifier.testTag("health-steps-status"))
                                }
                                Button(onClick = ::readSteps, enabled = stepJob?.isActive != true, modifier = Modifier.testTag("health-steps-connect")) {
                                    Text(if (stepPermission) "Read today’s steps" else "Allow step access")
                                }
                                TextButton(onClick = {
                                    if (!runCatching { base.startActivity(Intent(HEALTH_CONNECT_SETTINGS)) }.isSuccess) {
                                        error = "Health Connect settings could not be opened."
                                    }
                                }) { Text("Health Connect settings") }
                            }
                            Text("Samsung Health and Galaxy Watch steps can appear here if they’re shared with Health Connect. WHOOP data can be entered manually.")
                        }
                        NovaSystemSection("Your data") {
                            Text("Your manual measurements, goals and habits are encrypted in Primary Space. Imported steps stay on this screen and aren’t included in exports.")
                            OutlinedButton(onClick = { confirmation = HealthConfirmation.EXPORT }, enabled = !busy && log != null && mind != null) { Text("Export local data") }
                            onBackup?.let { TextButton(onClick = it) { Text("Open Nova Backup") } }
                            TextButton(onClick = { confirmation = HealthConfirmation.ERASE_HEALTH }, enabled = !busy) { Text("Delete health log") }
                            TextButton(onClick = { confirmation = HealthConfirmation.ERASE_MIND }, enabled = !busy && mind != null) { Text("Delete mood and journal") }
                        }
                    }
                }
                if (tab in setOf(HealthTab.MOOD, HealthTab.JOURNAL)) mind?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (metricDialog && records != null) HealthMeasurementDialog(records?.days?.get(day), onDismiss = { metricDialog = false }) { metric, value ->
        act("The measurement could not be saved.") {
            records = changeRecords { it.withMeasurement(day, metric, value) }
            metricDialog = false
        }
    }
    if (goalDialog) HealthTextDialog("Personal goal", "Goal", records?.goal.orEmpty(), HealthRecords.MAX_GOAL_LENGTH, onDismiss = { goalDialog = false }) { value ->
        act("The goal could not be saved.") { records = changeRecords { it.copy(goal = value.trim()) }; goalDialog = false }
    }
    if (habitDialog) HealthTextDialog("Add a habit", "Habit name", "", HealthRecords.MAX_HABIT_LENGTH, requireText = true, onDismiss = { habitDialog = false }) { value ->
        act("The habit could not be saved. Check for a duplicate name.") { records = changeRecords { it.withHabit(value) }; habitDialog = false }
    }
    if (routineDialog) HealthTextDialog("Daily sleep reminder", "Time (HH:mm)", routineTime?.toString().orEmpty(), 5, requireText = true,
        valid = { parseRoutineTime(it) != null }, onDismiss = { routineDialog = false }) { value ->
        routineDialog = false
        saveRoutine(checkNotNull(parseRoutineTime(value)))
    }
    habitToDelete?.let { habit ->
        HealthConfirm("Remove this habit?", "Its recorded completions will also be removed.", "Remove habit", { habitToDelete = null }) {
            act("The habit could not be removed.") { records = changeRecords { it.withoutHabit(habit.id) }; habitToDelete = null }
        }
    }
    confirmation?.let { request ->
        HealthConfirm(request.title, request.message, request.button, { confirmation = null }) {
            confirmation = null
            when (request) {
                HealthConfirmation.ERASE_HEALTH -> act("The health log could not be deleted.") {
                    withContext(Dispatchers.IO) { check(authorized()); store.delete() }
                    records = HealthRecords()
                    error = "Health log deleted."
                }
                HealthConfirmation.ERASE_MIND -> act("The entries could not be deleted.") {
                    check(authorized() && mind?.deletePrimaryEntries() == true)
                    error = "Primary mood and journal entries deleted."
                }
                HealthConfirmation.EXPORT -> if (authorized()) exportFile.launch("nova-health-and-mind.json")
            }
        }
    }
}

@Composable
private fun HealthColumns(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { first() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { second() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { first(); second() }
    }
}

@Composable
private fun HealthValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.widthIn(max = 156.dp), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun HealthWeeklyReview(log: HealthRecords, mind: MindData?, space: SpaceContext, today: LocalDate) {
    val week = remember(log, today) { log.week(today) }
    val history = remember(space, today) {
        val zone = ZoneId.systemDefault()
        healthSanctuaryWeek(SanctuaryStore.get(space.baseContext).history(), today, Instant.now(), zone)
    }
    val weights = remember(log, today) { log.lastMeasurements(HealthMetric.WEIGHT, today, 7) }
    val mindEntries = remember(mind, mind?.revision, today, space) { mind?.entries("primary").orEmpty() }
    val moodDays = remember(mindEntries, today) {
        val zone = ZoneId.systemDefault()
        mindEntries.asSequence().filter { !it.journal }.map { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
            .filter { it in today.minusDays(6)..today }.distinct().count()
    }
    HealthColumns(
        first = {
            NovaSystemSection("Last seven days") {
                HealthValue("Days with health or habit entries", "${week.recordedDays} / 7")
                HealthValue("Days with recorded activity", "${week.activeDays} / 7")
                HealthValue("Average logged sleep", week.sleepAverage?.let { "$it min · ${week.sleepDays} ${if (week.sleepDays == 1) "entry" else "entries"}" } ?: "No records")
                HealthValue("Water logged", week.hydrationTotal?.let { "$it mL · ${week.hydrationDays} days" } ?: "No records")
                HealthValue("Mood check-in days", if (mind == null || mind.error?.startsWith("Unable to read") == true) "Unavailable" else "$moodDays / 7")
            }
            NovaSystemSection("Completed Sanctuary sessions") {
                HealthValue("Sessions in this period", "${history.sessions}")
                HealthValue("Recorded time in Sanctuary", "${history.minutes} min")
            }
        },
        second = {
            NovaSystemSection("Weight history") {
                if (weights.isEmpty()) Text("No weight measurements recorded.")
                weights.forEach { (date, value) -> HealthValue(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), HealthMetric.WEIGHT.format(value)) }
                if (weights.size >= 2) {
                    val change = weights.first().second.subtract(weights.last().second).stripTrailingZeros().toPlainString()
                    HealthValue("Change across these entries", "$change kg")
                }
            }
            NovaSystemSection("Habit review") {
                if (log.habits.isEmpty()) Text("No habits set.")
                log.habits.forEach { HealthValue(it.name, "${week.habitDays.getValue(it.id)} / 7 days") }
            }
        },
    )
}

@Composable
private fun HealthMeasurementDialog(day: HealthDay?, onDismiss: () -> Unit, onSave: (HealthMetric, BigDecimal?) -> Unit) {
    var selected by remember { mutableStateOf(HealthMetric.HYDRATION) }
    var chooseMetric by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(day?.measurements?.get(selected)?.toPlainString().orEmpty()) }
    val value = selected.parse(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Log a measurement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { chooseMetric = true }) { Text(selected.label) }
                    DropdownMenu(expanded = chooseMetric, onDismissRequest = { chooseMetric = false }) {
                        HealthMetric.entries.forEach { metric ->
                            DropdownMenuItem(text = { Text(metric.label) }, onClick = {
                                selected = metric
                                input = day?.measurements?.get(metric)?.toPlainString().orEmpty()
                                chooseMetric = false
                            })
                        }
                    }
                }
                OutlinedTextField(input, { input = it.take(24) }, Modifier.fillMaxWidth().testTag("health-value"),
                    label = { Text("Value (${selected.unit})") }, singleLine = true,
                    supportingText = {
                        if (input.isNotBlank() && value == null) Text("Enter a valid ${selected.unit} value.")
                        else Text("Enter your measured or recorded value.")
                    }, isError = input.isNotBlank() && value == null,
                    keyboardOptions = KeyboardOptions(keyboardType = if (selected.decimalPlaces == 0) KeyboardType.Number else KeyboardType.Decimal),
                )
                if (day?.measurements?.containsKey(selected) == true) TextButton(onClick = { onSave(selected, null) }) { Text("Delete this measurement") }
            }
        },
        confirmButton = { TextButton(onClick = { if (value != null) onSave(selected, value) }, enabled = value != null,
            modifier = Modifier.testTag("health-save-measurement")) { Text("Save locally") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HealthTextDialog(
    title: String, field: String, initial: String, limit: Int, requireText: Boolean = false,
    valid: (String) -> Boolean = { true }, onDismiss: () -> Unit, onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val enabled = (!requireText || text.isNotBlank()) && valid(text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(limit) }, label = { Text(field) }, singleLine = true,
            isError = text.isNotEmpty() && !valid(text.trim())) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = enabled) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HealthConfirm(title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun parseRoutineTime(raw: String): LocalTime? = raw.trim().takeIf { it.matches(Regex("[0-9]{2}:[0-9]{2}")) }
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
