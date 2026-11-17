package uk.co.traynor.privategallery.home2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext

private enum class CalendarView(val label: String) {
    MONTH("Month"), WEEK("Week"), DAY("Day"), AGENDA("Agenda");

    fun shift(date: LocalDate, direction: Long) = when (this) {
        MONTH -> date.plusMonths(direction)
        WEEK -> date.plusWeeks(direction)
        DAY -> date.plusDays(direction)
        AGENDA -> date.plusDays(30 * direction)
    }
}

private data class CalendarEdit(
    val event: NovaCalendarEvent,
    val date: String = event.firstDate.toString(),
    val time: String = event.time?.toString().orEmpty(),
    val zone: String = event.zone.id,
    val minutes: String = event.minutes.toString(),
    val allDay: Boolean = event.time == null,
    val reminder: String = event.reminderMinutes?.toString().orEmpty(),
) {
    fun build(): NovaCalendarEvent? = runCatching {
        check(event.title.isNotBlank())
        val day = LocalDate.parse(date)
        check(day.year in 1..9999)
        val start = if (allDay) null else LocalTime.parse(time)
        val eventZone = if (allDay) event.zone else ZoneId.of(zone.trim())
        check(start == null || eventZone.rules.getValidOffsets(day.atTime(start)).isNotEmpty())
        event.copy(title = event.title.trim(), firstDate = day, time = start, zone = eventZone,
            minutes = if (allDay) event.minutes else minutes.toInt(),
            reminderMinutes = reminder.trim().takeIf(String::isNotEmpty)?.toInt())
    }.getOrNull()
}

private enum class CalendarConfirmation { DELETE, DISCARD }

@Composable
internal fun NovaCalendarScreen(
    space: SpaceContext,
    onBack: () -> Unit,
    openEventId: String? = null,
    openDay: LocalDate? = null,
    onOpenLink: ((NovaCoreLink) -> Unit)? = null,
    sendToAndroidCalendar: ((NovaOccurrence) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    val displayZone = ZoneId.systemDefault()
    var store by remember(space) { mutableStateOf<NovaCalendarLibrary?>(null) }
    var snapshot by remember(space) { mutableStateOf<NovaCalendarLibrary.Snapshot?>(null) }
    var tasks by remember(space) { mutableStateOf<List<NovaTask>>(emptyList()) }
    var editor by remember(space) { mutableStateOf<CalendarEdit?>(null) }
    var focus by remember(space) { mutableStateOf(LocalDate.now(displayZone)) }
    var mode by remember(space) { mutableStateOf(CalendarView.MONTH) }
    var query by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var busy by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var pickNote by remember(space) { mutableStateOf(false) }
    var handoffOccurrence by remember(space) { mutableStateOf<NovaOccurrence?>(null) }
    var skipOccurrence by remember(space) { mutableStateOf<NovaOccurrence?>(null) }
    var confirmation by remember(space) { mutableStateOf<CalendarConfirmation?>(null) }

    LaunchedEffect(space, reload, openEventId, openDay) {
        busy = true
        val (calendar, dueTasks) = withContext(Dispatchers.IO) {
            runCatching { NovaCalendarLibrary(space).let { it to it.read() } } to
                runCatching { NovaTaskLibrary(space).read().tasks }
        }
        val reminderError = withContext(Dispatchers.IO) {
            calendar.getOrNull()?.second?.let { runCatching { NovaReminders.syncCalendar(space, it.events) }.isFailure } ?: false
        }
        if (!space.current()) return@LaunchedEffect
        calendar.onSuccess { (library, saved) ->
            store = library
            snapshot = saved
            tasks = dueTasks.getOrDefault(emptyList())
            val requested = openEventId?.let { id -> saved.events.find { it.id == id } }
            if (requested != null) editor = CalendarEdit(requested)
            if (openDay != null) {
                focus = openDay
                mode = CalendarView.DAY
            }
            status = when {
                openEventId != null && requested == null -> "The linked event is unavailable."
                dueTasks.isFailure -> "Task due dates are unavailable."
                reminderError -> "Android reminders could not be updated."
                else -> null
            }
        }.onFailure { status = "Encrypted events could not be opened." }
        busy = false
    }

    fun write(events: List<NovaCalendarEvent>, successMessage: String) {
        val library = store ?: return
        val previous = snapshot ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                library.save(previous, events)?.let { saved ->
                    saved to runCatching { NovaReminders.syncCalendar(space, saved.events) }.isSuccess
                }
            } }
            if (!space.current()) return@launch
            result.onSuccess { saved ->
                if (saved == null) status = "Events changed elsewhere. Close the editor and reload."
                else {
                    snapshot = saved.first
                    editor = null
                    status = if (saved.second) successMessage else "$successMessage. Android reminders could not be updated."
                }
            }.onFailure { status = "The encrypted calendar may be full or unavailable." }
            busy = false
        }
    }

    fun closeEditor() {
        val current = editor ?: return
        val saved = snapshot?.events?.find { it.id == current.event.id }?.let { CalendarEdit(it) }
        if (current != saved && (saved != null || current.event.title.isNotBlank() ||
                current.event.description.isNotBlank())) {
            confirmation = CalendarConfirmation.DISCARD
        } else editor = null
    }

    BackHandler(enabled = busy || editor != null) { if (!busy) closeEditor() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp).testTag("nova-calendar"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoreWorkspaceHeader("Nova Calendar", enabled = !busy, onBack = { if (editor != null) closeEditor() else onBack() })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (!busy && snapshot == null) TextButton(onClick = { reload++ }) { Text("Try again") }
        val current = editor
        val events = snapshot?.events.orEmpty()
        if (current == null) {
            NovaCoreAppActions {
                Button(enabled = !busy && snapshot != null, onClick = {
                    status = null
                    editor = CalendarEdit(NovaCalendarEvent(UUID.randomUUID().toString(), "", "", focus,
                        null, displayZone, 30, NovaEventRepeat.NONE))
                }) { Text("New event") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalendarView.entries.forEach { view ->
                    FilterChip(mode == view, { mode = view }, { Text(view.label) })
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkspaceSearch(query, { query = it }, "Search visible events", Modifier.fillMaxWidth(),
                enabled = !busy && snapshot != null)
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        CalendarDates(focus, mode, onDate = { focus = it })
                    }
                }
                val weekStart = focus.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
                val dates = when (mode) {
                    CalendarView.MONTH, CalendarView.DAY -> listOf(focus)
                    CalendarView.WEEK -> List(7) { weekStart.plusDays(it.toLong()) }
                    CalendarView.AGENDA -> List(30) { focus.plusDays(it.toLong()) }
                }
                val search = query.trim()
                val visibleEvents = events.filter { it.title.contains(search, true) || it.description.contains(search, true) }
                val visibleTasks = tasks.filter { !it.completed && it.title.contains(search, true) }.groupBy { it.due }
                var anyVisible = false
                for (date in dates) {
                    val matches = visibleEvents.flatMap { it.occurrencesOn(date, displayZone) }.sortedBy { it.start }
                    val due = visibleTasks[date].orEmpty()
                    if (matches.isEmpty() && due.isEmpty()) continue
                    anyVisible = true
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.titleSmall)
                    due.forEach { task ->
                        if (onOpenLink == null) Text("Task due · " + task.title)
                        else TextButton(enabled = !busy, onClick = {
                            onOpenLink(NovaCoreLink.Record(NovaCoreApp.TASKS, task.id))
                        }) { Text("Task due · " + task.title) }
                    }
                    matches.forEach { occurrence ->
                        val event = occurrence.event
                        CoreWorkspaceCard(onClick = { status = null; editor = CalendarEdit(event) },
                            enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                val whenText = occurrence.start?.let {
                                    it.atZone(displayZone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                                } ?: "All day"
                                Text(whenText, style = MaterialTheme.typography.labelMedium)
                                if (onOpenLink != null) event.noteId?.let { id ->
                                    TextButton(enabled = !busy, onClick = {
                                        onOpenLink(NovaCoreLink.Record(NovaCoreApp.NOTES, id))
                                    }) { Text("Open linked note") }
                                }
                                if (sendToAndroidCalendar != null) {
                                    TextButton(enabled = !busy, onClick = { handoffOccurrence = occurrence }) {
                                        Text("Copy to Android calendar")
                                    }
                                }
                                if (event.repeat != NovaEventRepeat.NONE) {
                                    TextButton(enabled = !busy, onClick = {
                                        status = null
                                        editor = CalendarEdit(occurrence.detached())
                                    }) { Text("Edit this occurrence") }
                                    TextButton(enabled = !busy, onClick = { skipOccurrence = occurrence }) {
                                        Text("Skip this occurrence")
                                    }
                                }
                            }
                        }
                    }
                }
                if (!anyVisible && !busy && snapshot != null) Text("No events or task due dates in this view")
            }
        } else {
            val candidate = current.build()
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarEditFields(current, !busy, onEdit = { editor = it })
                OutlinedTextField(current.reminder, { editor = current.copy(reminder = it.take(4)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Remind minutes before (optional)") },
                    supportingText = { if (current.allDay) Text("All-day reminders are based on 09:00 local time.") },
                    isError = current.reminder.isNotBlank() && current.reminder.trim().toIntOrNull() !in 0..1440,
                    singleLine = true, enabled = !busy)
                if (current.reminder.isNotBlank()) NovaReminderPermission(space, !busy)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(enabled = !busy, onClick = { pickNote = true }) {
                        Text(if (current.event.noteId == null) "Link a note" else "Change linked note")
                    }
                    if (current.event.noteId != null) TextButton(enabled = !busy, onClick = {
                        editor = current.copy(event = current.event.copy(noteId = null))
                    }) { Text("Unlink note") }
                }
                if (current.event.skippedDates.isNotEmpty()) {
                    Text("Skipped or edited dates")
                    current.event.skippedDates.sorted().forEach { date ->
                        val replacement = events.any { it.originalOccurrence == NovaOriginalOccurrence(current.event.id, date) }
                        TextButton(enabled = !busy, onClick = {
                            editor = current.copy(event = current.event.copy(skippedDates = current.event.skippedDates - date))
                        }) { Text(if (replacement) "Replace edited event with original on $date" else "Restore $date") }
                    }
                }
                if (candidate == null && current.event.title.isNotBlank()) {
                    Text("Check the date, time zone, duration and reminder. Reminders use a lead time of 0–1440 minutes.")
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !busy && candidate != null, onClick = {
                        val event = candidate ?: return@Button
                        val updated = runCatching { saveNovaCalendarEvent(events, event) }.getOrNull()
                        if (updated == null) status = "The original series is unavailable. Reload the calendar."
                        else write(updated, "Event saved")
                    }) { Text("Save event") }
                    TextButton(enabled = !busy, onClick = { closeEditor() }) { Text("Back to calendar") }
                    if (events.any { it.id == current.event.id }) {
                        TextButton(enabled = !busy, onClick = { confirmation = CalendarConfirmation.DELETE }) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (pickNote) NovaNotePicker(space, onChoose = { id ->
        editor = editor?.let { it.copy(event = it.event.copy(noteId = id)) }
        pickNote = false
    }, onDismiss = { pickNote = false })
    handoffOccurrence?.let { occurrence ->
        AlertDialog(
            onDismissRequest = { handoffOccurrence = null },
            title = { Text("Open your Android calendar?") },
            text = {
                Text("Your chosen calendar app will receive this occurrence’s title, description and time. " +
                    "Review it there and save it only if you want a separate copy.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    handoffOccurrence = null
                    if (sendToAndroidCalendar?.invoke(occurrence) != true) {
                        status = "No Android calendar opened. Check for a calendar app or try outside Sanctuary."
                    }
                }) { Text("Open calendar editor") }
            },
            dismissButton = { TextButton(onClick = { handoffOccurrence = null }) { Text("Cancel") } },
        )
    }
    skipOccurrence?.let { occurrence ->
        AlertDialog(
            onDismissRequest = { skipOccurrence = null },
            title = { Text("Skip this occurrence?") },
            text = { Text("The rest of the series will stay on your calendar.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    skipOccurrence = null
                    val events = snapshot?.events.orEmpty().map { event ->
                        if (event.id == occurrence.event.id) {
                            event.copy(skippedDates = event.skippedDates + occurrence.sourceDate)
                        } else event
                    }
                    write(events, "Occurrence skipped")
                }) { Text("Skip") }
            },
            dismissButton = { TextButton(onClick = { skipOccurrence = null }) { Text("Cancel") } },
        )
    }
    confirmation?.let { action ->
        val deleting = action == CalendarConfirmation.DELETE
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (deleting) "Delete this event and its recurrences?" else "Discard unsaved changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (deleting) {
                        editor?.event?.id?.let { id ->
                            write(snapshot?.events.orEmpty().filterNot {
                                it.id == id || it.originalOccurrence?.seriesId == id
                            }, "Event deleted")
                        }
                    }
                    else editor = null
                }) { Text(if (deleting) "Delete" else "Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CalendarDates(date: LocalDate, mode: CalendarView, onDate: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onDate(mode.shift(date, -1)) }) { Text("Previous") }
        TextButton(onClick = { onDate(LocalDate.now()) }) { Text("Today") }
        TextButton(onClick = { onDate(mode.shift(date, 1)) }) { Text("Next") }
    }
    if (mode == CalendarView.MONTH) {
        val startOfMonth = date.withDayOfMonth(1)
        val firstWeekday = WeekFields.of(locale).firstDayOfWeek
        val gridStart = startOfMonth.with(TemporalAdjusters.previousOrSame(firstWeekday))
        Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { day ->
                Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(firstWeekday.plus(day.toLong()).getDisplayName(TextStyle.SHORT, locale),
                        maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
        val offset = java.time.temporal.ChronoUnit.DAYS.between(gridStart, startOfMonth).toInt()
        repeat((offset + date.lengthOfMonth() + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { day ->
                    val cell = gridStart.plusDays((7 * week + day).toLong())
                    val cellLabel = cell.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                    if (cell.month == date.month) {
                        if (cell == date) {
                            Button(onClick = { onDate(cell) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semantics { contentDescription = cellLabel }, contentPadding = PaddingValues(0.dp)) {
                                Text(cell.dayOfMonth.toString())
                            }
                        } else {
                            TextButton(onClick = { onDate(cell) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semantics { contentDescription = cellLabel }, contentPadding = PaddingValues(0.dp)) {
                                Text(cell.dayOfMonth.toString())
                            }
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CalendarEditFields(value: CalendarEdit, enabled: Boolean, onEdit: (CalendarEdit) -> Unit) {
    OutlinedTextField(value.event.title, { onEdit(value.copy(event = value.event.copy(title = it.take(200)))) },
        modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = enabled)
    OutlinedTextField(value.event.description, { onEdit(value.copy(event = value.event.copy(description = it.take(4096)))) },
        modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, enabled = enabled)
    OutlinedTextField(value.date, { onEdit(value.copy(date = it.take(10))) },
        modifier = Modifier.fillMaxWidth(), label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, enabled = enabled)
    FilterChip(value.allDay, { onEdit(value.copy(allDay = !value.allDay)) }, { Text("All day") }, enabled = enabled)
    if (!value.allDay) {
        OutlinedTextField(value.time, { onEdit(value.copy(time = it.take(5))) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Start time (HH:MM)") }, singleLine = true, enabled = enabled)
        OutlinedTextField(value.zone, { onEdit(value.copy(zone = it.take(100))) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Time zone (e.g. Europe/London)") },
            singleLine = true, enabled = enabled)
        OutlinedTextField(value.minutes, { onEdit(value.copy(minutes = it.take(4))) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Duration in minutes") }, singleLine = true, enabled = enabled)
    }
    if (value.event.originalOccurrence != null) {
        Text("Editing one occurrence")
    } else {
        Text("Repeat")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NovaEventRepeat.entries.forEach { repeat ->
                FilterChip(value.event.repeat == repeat, { onEdit(value.copy(event = value.event.copy(repeat = repeat))) },
                    { Text(repeat.label) }, enabled = enabled)
            }
        }
    }
}
