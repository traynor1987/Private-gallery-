package uk.co.traynor.privategallery.novaos2

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.novaos2.working.DeliveryStatus
import uk.co.traynor.privategallery.novaos2.working.MAX_SHIFT_MILLIS
import uk.co.traynor.privategallery.novaos2.working.WorkTotals
import uk.co.traynor.privategallery.novaos2.working.WorkingLocation
import uk.co.traynor.privategallery.novaos2.working.WorkingLocationStatus
import uk.co.traynor.privategallery.novaos2.working.WorkingSchedule
import uk.co.traynor.privategallery.novaos2.working.WorkingShortcut
import uk.co.traynor.privategallery.novaos2.working.WorkingState
import uk.co.traynor.privategallery.novaos2.working.WorkingVehicleSignal
import uk.co.traynor.privategallery.novaos2.working.WorkingVehicleSettings
import uk.co.traynor.privategallery.novaos2.working.WorkingVehiclePicker
import uk.co.traynor.privategallery.novaos2.working.scaledWorkNumber
import uk.co.traynor.privategallery.novaos2.working.workingLocation
import uk.co.traynor.privategallery.novaos2.working.workingScheduleMinute
import uk.co.traynor.privategallery.novaos2.working.workingTimeLabel
import uk.co.traynor.privategallery.novaos2.working.workingVehiclePermission
import uk.co.traynor.privategallery.novaos2.working.workingVehicleSignal

@Composable
internal fun NovaWorkingMode(host: NovaSystemHost) {
    val context = LocalContext.current
    val config = remember(context) { NovaSystemConfig(context, "working-mode") }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<WorkingState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var choosingShortcut by remember { mutableStateOf<WorkingShortcut?>(null) }
    var editingTotals by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf(false) }
    var vehiclePermissionRevision by remember { mutableIntStateOf(0) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
    }
    val vehiclePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vehiclePermissionRevision++
    }

    LaunchedEffect(config) {
        try {
            data = withContext(Dispatchers.IO) { WorkingState.decode(config.read()?.let(::JSONObject)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "The saved Working Mode settings could not be opened." }
    }

    fun save(next: WorkingState, afterSave: () -> Unit = {}) {
        if (busy || !host.foregroundAllowed()) return
        busy = true
        message = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { config.write(next.encode().toString()) }
                data = next
                afterSave()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "The shift changes could not be saved." }
            finally { busy = false }
        }
    }

    val current = data
    if (current == null) {
        Text(message ?: "Opening Working Mode…", Modifier.padding(16.dp))
        return
    }
    val session = current.active
    val foreground = host.foregroundAllowed()
    val clock = key(session?.startedElapsedMillis) { workingClock(session != null, current.schedule != null, foreground) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val localNow = Instant.ofEpochMilli(clock.wallMillis).atZone(ZoneId.systemDefault())
    val occurrence = current.schedule?.occurrence(localNow)?.toEpochDay()
    val scheduledDay = current.eligibleSchedule(localNow)
    val duration = session?.duration(clock.wallMillis, clock.elapsedMillis)
    val driving = session?.driving == true
    val monitoredVehicle = current.vehicle?.identifier?.takeUnless { driving }
    val vehicleSignal by key(monitoredVehicle, foreground, vehiclePermissionRevision, lifecycleState) {
        workingVehicleSignal(monitoredVehicle, vehiclePermissionRevision, host.foregroundAllowed)
    }
    fun markObservedVehicle(next: WorkingState) = if (vehicleSignal == WorkingVehicleSignal.CONNECTED) next.handleVehicle() else next
    val scroll = rememberScrollState()
    LaunchedEffect(session != null, driving) { scroll.scrollTo(0) }
    val location by workingLocation(driving && duration != MAX_SHIFT_MILLIS && duration != null, permissionRevision, host.foregroundAllowed)
    LaunchedEffect(current, duration == MAX_SHIFT_MILLIS, session != null && duration == null, foreground) {
        if (foreground && session != null && (duration == MAX_SHIFT_MILLIS || duration == null)) {
            save(markObservedVehicle(current.finish(clock.wallMillis, clock.elapsedMillis).skipScheduledDay(occurrence))) {
                message = if (duration == null) "The previous shift ended after the device clock changed."
                else "The 18-hour shift limit ended this session."
            }
        }
    }
    LaunchedEffect(scheduledDay, editingSchedule, editingVehicle, choosingShortcut, lifecycleState, foreground) {
        if (scheduledDay != null && !editingSchedule && !editingVehicle && choosingShortcut == null &&
            foreground && lifecycleState == Lifecycle.State.RESUMED) {
            val now = workClock()
            save(markObservedVehicle(current.start(now.wallMillis, now.elapsedMillis, scheduledDay))) {
                message = "Scheduled shift started."
            }
        }
    }
    LaunchedEffect(vehicleSignal, current.vehicle, session != null, foreground, editingVehicle, editingSchedule, choosingShortcut) {
        if (!foreground || editingVehicle || editingSchedule || choosingShortcut != null || !workingVehiclePermission(context)) {
            return@LaunchedEffect
        }
        val now = workClock()
        val next = current.syncVehicle(vehicleSignal, now.wallMillis, now.elapsedMillis, occurrence)
        if (next != current) save(next) {
            if (session == null && next.active != null) message = "Shift started from the paired vehicle."
        }
    }

    val choices = remember(host.apps, context.packageName) {
        host.apps.filter { it.profile == null && !it.packageName.startsWith("nova:") && it.packageName != context.packageName }
    }
    fun target(slot: WorkingShortcut): LauncherApp? {
        val selected = current.shortcuts[slot]
        return if (selected == null) choices.firstOrNull { it.packageName in slot.knownPackages }
        else choices.firstOrNull { it.component == selected }
    }
    fun open(slot: WorkingShortcut) {
        if (!host.foregroundAllowed()) return
        val app = target(slot)
        if (app == null || !host.launch(app)) message = "${slot.label} is unavailable here."
    }
    val quickActions: @Composable () -> Unit = {
        WorkingActions(host.wide,
            canNavigate = target(WorkingShortcut.NAVIGATION) != null,
            onNavigate = { open(WorkingShortcut.NAVIGATION) },
            onPlayPause = {
                if (host.foregroundAllowed() && !playPause(context)) message = "Android media controls are unavailable."
            },
            onPhone = { if (host.foregroundAllowed()) host.actions.emergencyPhone() },
        )
    }

    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session == null) {
            NovaSystemSection("Shift") {
                Text("Track a shift and open your work apps. Driving Mode has a separate, simplified dashboard.")
                Button(onClick = {
                    val now = workClock()
                    save(markObservedVehicle(current.start(now.wallMillis, now.elapsedMillis, occurrence)))
                }, enabled = !busy, modifier = Modifier.heightIn(min = 56.dp).testTag("start-working-shift")) {
                    Text("Start shift")
                }
            }
            current.previous?.let { finished ->
                NovaSystemSection("Last shift") { ShiftSummary(finished.durationMillis, finished.totals) }
            }
        } else {
            NovaSystemSection(if (driving) "Driving" else "Current shift") {
                if (driving) {
                    DrivingReadout(location, SystemClock.elapsedRealtime())
                    quickActions()
                }
                ShiftSummary(duration, session.totals)
                Text("Delivery status  ${session.deliveryStatus.label}")
                if (driving) {
                    if (location.status == WorkingLocationStatus.PERMISSION) {
                        OutlinedButton(onClick = {
                            if (host.foregroundAllowed()) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION))
                        }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Allow location") }
                    }
                    if (location.status == WorkingLocationStatus.DISABLED) {
                        OutlinedButton(onClick = { host.openSystemSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
                            modifier = Modifier.heightIn(min = 56.dp)) { Text("Location settings") }
                    }
                    Text("Speed limits and camera alerts require a supported data source.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { save(current.copy(active = session.copy(driving = false))) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 56.dp)) { Text("Leave Driving Mode") }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeliveryStatus.entries.forEach { status ->
                            FilterChip(selected = session.deliveryStatus == status, enabled = !busy,
                                onClick = { save(current.copy(active = session.copy(deliveryStatus = status))) },
                                label = { Text(status.label) })
                        }
                    }
                    Button(onClick = { save(current.copy(active = session.copy(driving = true))) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 56.dp).testTag("enter-driving-mode")) { Text("Enter Driving Mode") }
                    TextButton(enabled = !busy, onClick = { editingTotals = true }) { Text("Record shift totals") }
                    TextButton(enabled = !busy, onClick = {
                        val now = workClock()
                        save(current.finish(now.wallMillis, now.elapsedMillis).skipScheduledDay(occurrence)
                            .manualStopVehicle(vehicleSignal))
                    }) { Text("End shift") }
                }
            }
        }
        if (!driving) NovaSystemSection("Quick actions") { quickActions() }
        if (!driving) NovaSystemSection("Work apps") {
            WorkingShortcut.entries.forEach { slot ->
                val app = target(slot)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(slot.label, style = MaterialTheme.typography.titleSmall)
                        Text(app?.label ?: "Choose an installed app", style = MaterialTheme.typography.bodySmall)
                    }
                    if (app != null) TextButton(onClick = { open(slot) }) { Text("Open") }
                    TextButton(enabled = !busy, onClick = { choosingShortcut = slot }) { Text("Choose") }
                }
            }
            Text("Shift totals are entered here. No verified Shift Tracker connection is configured.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (!driving) NovaSystemSection("Shift schedule") {
            val schedule = current.schedule
            if (schedule == null) Text("Automatic shift start is off.") else {
                val days = schedule.days.sorted().joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, Locale.UK) }
                Text("${workingTimeLabel(schedule.startMinute)}–${workingTimeLabel(schedule.endMinute)}  $days")
                Text("Starts one shift per scheduled day when Working Mode is open.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { editingSchedule = true }, enabled = !busy) {
                Text(if (schedule == null) "Set shift schedule" else "Edit shift schedule")
            }
            if (schedule != null) TextButton(enabled = !busy, onClick = { save(current.copy(schedule = null)) }) { Text("Turn schedule off") }
        }
        if (!driving) WorkingVehicleSettings(current.vehicle, vehicleSignal, busy, session == null,
            onSetUp = { editingVehicle = true },
            onDisable = { save(current.copy(vehicle = null)) },
            onDismissOffer = { save(current.handleVehicle()) },
            onStart = {
                if (vehicleSignal == WorkingVehicleSignal.CONNECTED && workingVehiclePermission(context)) {
                    val now = workClock()
                    val next = current.syncVehicle(vehicleSignal, now.wallMillis, now.elapsedMillis, occurrence, acceptOffer = true)
                    if (next != current) save(next)
                }
            })
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
    }
    if (!driving) choosingShortcut?.let { slot ->
        AlertDialog(onDismissRequest = { choosingShortcut = null }, title = { Text("Choose ${slot.label}") }, text = {
            if (choices.isEmpty()) Text("No eligible apps are installed.") else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(choices, key = LauncherApp::component) { app ->
                    TextButton(enabled = !busy, onClick = {
                        save(current.copy(shortcuts = current.shortcuts + (slot to app.component))) { choosingShortcut = null }
                    }, modifier = Modifier.fillMaxWidth()) { Text(app.label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosingShortcut = null }) { Text("Close") } })
    }
    if (editingTotals && session != null && !driving) EditShiftTotals(session.totals, busy, onDismiss = { editingTotals = false }) { next ->
        save(current.copy(active = session.copy(totals = next))) { editingTotals = false }
    }
    if (editingSchedule && !driving) EditWorkingSchedule(current.schedule, busy, onDismiss = { editingSchedule = false }) { next ->
        save(current.copy(schedule = next)) { editingSchedule = false }
    }
    if (editingVehicle && !driving) WorkingVehiclePicker(current.vehicle, vehiclePermissionRevision, busy,
        allowed = host.foregroundAllowed,
        onPermission = { if (host.foregroundAllowed()) vehiclePermission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        onSettings = { host.openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
        onDismiss = { editingVehicle = false },
        onSave = { identifier, automatic -> save(current.setVehicle(identifier, automatic)) { editingVehicle = false } },
    )
}

@Composable
private fun DrivingReadout(location: WorkingLocation, elapsedMillis: Long) {
    val mph = location.speed?.mph(elapsedMillis)
    val accuracy = location.speed?.accuracy(elapsedMillis)
    val position = location.speed?.positionAccuracy(elapsedMillis)
    Text(mph?.let { "$it mph" } ?: "Speed unavailable", style = MaterialTheme.typography.displayMedium,
        modifier = Modifier.testTag("driving-speed"))
    val signal = if (location.status == WorkingLocationStatus.RECEIVING && location.speed?.isCurrent(elapsedMillis) != true) {
        "Waiting for a fresh location reading."
    } else location.status.description
    Text(signal, style = MaterialTheme.typography.bodyMedium)
    if (position != null) Text("Position accuracy ±$position m", style = MaterialTheme.typography.bodySmall)
    if (accuracy != null) Text("Speed accuracy ±$accuracy mph", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ShiftSummary(duration: Long?, totals: WorkTotals) {
    Text(duration?.let { elapsed ->
        val time = Duration.ofMillis(elapsed)
        "${time.toHours()} h ${time.toMinutesPart()} m"
    } ?: "Duration unavailable", style = MaterialTheme.typography.titleLarge)
    Text("${totals.deliveries} recorded ${if (totals.deliveries == 1) "delivery" else "deliveries"}")
    Text("Earnings ${totals.earningsPence?.asPounds() ?: "unrecorded"} · Fuel ${totals.fuelPence?.asPounds() ?: "unrecorded"}")
    Text("Mileage ${totals.mileageTenths?.asDecimal(1)?.let { "$it mi" } ?: "unrecorded"}")
}

@Composable
private fun WorkingActions(wide: Boolean, canNavigate: Boolean, onNavigate: () -> Unit, onPlayPause: () -> Unit, onPhone: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val horizontal = wide && maxWidth >= 420.dp
        if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrivingAction("Emergency phone", onPhone)
            DrivingAction("Navigation", onNavigate, canNavigate)
            DrivingAction("Play / pause", onPlayPause)
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DrivingAction("Emergency phone", onPhone, modifier = Modifier.fillMaxWidth())
            DrivingAction("Navigation", onNavigate, canNavigate, Modifier.fillMaxWidth())
            DrivingAction("Play / pause", onPlayPause, modifier = Modifier.fillMaxWidth())
        }
    }
    if (!canNavigate) Text("Choose a navigation app from Work apps while parked.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DrivingAction(label: String, action: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) { Text(label) }
}

@Composable
private fun EditShiftTotals(totals: WorkTotals, busy: Boolean, onDismiss: () -> Unit, onSave: (WorkTotals) -> Unit) {
    var deliveries by rememberSaveable { mutableStateOf(totals.deliveries.toString()) }
    var earnings by rememberSaveable { mutableStateOf(totals.earningsPence?.asDecimal(2).orEmpty()) }
    var fuel by rememberSaveable { mutableStateOf(totals.fuelPence?.asDecimal(2).orEmpty()) }
    var mileage by rememberSaveable { mutableStateOf(totals.mileageTenths?.asDecimal(1).orEmpty()) }
    val candidate = runCatching {
        WorkTotals(deliveries.toInt(), scaledWorkNumber(earnings, 2), scaledWorkNumber(fuel, 2), scaledWorkNumber(mileage, 1))
    }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Record shift totals") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShiftNumber("Deliveries", deliveries, { deliveries = it }, KeyboardType.Number)
            ShiftNumber("Earnings (£)", earnings, { earnings = it }, KeyboardType.Decimal)
            ShiftNumber("Fuel (£)", fuel, { fuel = it }, KeyboardType.Decimal)
            ShiftNumber("Mileage (mi)", mileage, { mileage = it }, KeyboardType.Decimal)
            if (candidate == null) Text("Check the lengths and use up to two decimal places for amounts and one for miles.",
                color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = candidate != null && !busy, onClick = { candidate?.let(onSave) }) { Text("Save totals") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ShiftNumber(label: String, value: String, onInput: (String) -> Unit, type: KeyboardType) {
    OutlinedTextField(value, { if (it.length <= 9) onInput(it) }, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = type))
}

@Composable
private fun EditWorkingSchedule(schedule: WorkingSchedule?, busy: Boolean, onDismiss: () -> Unit,
                                onSave: (WorkingSchedule) -> Unit) {
    var start by rememberSaveable { mutableStateOf(schedule?.startMinute?.let(::workingTimeLabel).orEmpty()) }
    var end by rememberSaveable { mutableStateOf(schedule?.endMinute?.let(::workingTimeLabel).orEmpty()) }
    var days by remember { mutableStateOf(schedule?.days.orEmpty()) }
    val startMinute = workingScheduleMinute(start)
    val endMinute = workingScheduleMinute(end)
    val next = if (startMinute != null && endMinute != null && startMinute != endMinute && days.isNotEmpty()) {
        WorkingSchedule(startMinute, endMinute, days)
    } else null
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Shift schedule") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enter local times as HH:mm and choose the days your shift starts. Driving Mode starts manually.")
            OutlinedTextField(start, { if (it.length <= 5) start = it }, label = { Text("Start time") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { if (it.length <= 5) end = it }, label = { Text("End time") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            DayOfWeek.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { day ->
                        FilterChip(day in days, onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.UK)) })
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = next != null && !busy, onClick = { next?.let(onSave) }) { Text("Save schedule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private data class WorkingClock(val wallMillis: Long, val elapsedMillis: Long)
private fun workClock() = WorkingClock(System.currentTimeMillis(), SystemClock.elapsedRealtime())

@Composable
private fun workingClock(active: Boolean, scheduled: Boolean, foreground: Boolean): WorkingClock {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock by produceState(workClock(), active, scheduled, lifecycle, foreground) {
        if (!foreground) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = workClock()
            if (active || scheduled) while (true) {
                delay(if (active) 1_000 else 60_000)
                value = workClock()
            }
        }
    }
    return clock
}

private fun Int.asPounds() = NumberFormat.getCurrencyInstance(Locale.UK).format(BigDecimal.valueOf(toLong(), 2))
private fun Int.asDecimal(places: Int) = BigDecimal.valueOf(toLong(), places).toPlainString()

private fun playPause(context: Context) = runCatching {
    val audio = checkNotNull(context.getSystemService(AudioManager::class.java))
    val key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
}.isSuccess
