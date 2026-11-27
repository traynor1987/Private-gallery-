package uk.co.traynor.privategallery.home2

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract.Instances
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.intelligence.intelligencePrivateAllowed

internal data class NovaCalendarDayWindow(val begin: Long, val end: Long) {
    fun overlaps(start: Long, finish: Long) =
        start < end && (finish > begin || (finish == start && start >= begin)) && finish >= start
}

internal fun calendarDayWindow(day: LocalDate, zone: ZoneId) = NovaCalendarDayWindow(
    day.atStartOfDay(zone).toInstant().toEpochMilli(),
    day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
)

internal data class DeviceOccurrence(val title: String, val time: String)
internal data class DeviceDay(val events: List<DeviceOccurrence>, val truncated: Boolean)

private fun SpaceContext.calendarPermitted() =
    spaceId == "primary" && current() && intelligencePrivateAllowed(baseContext.applicationContext) &&
        checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

private fun readAndroidCalendarDay(space: SpaceContext, date: LocalDate, zone: ZoneId): DeviceDay {
    check(space.calendarPermitted())
    val timed = calendarDayWindow(date, zone)
    // Android encodes all-day boundaries as UTC midnight, including west/east of UTC.
    val allDay = calendarDayWindow(date, ZoneOffset.UTC)
    val uri = Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, minOf(timed.begin, allDay.begin))
        ContentUris.appendId(it, maxOf(timed.end, allDay.end))
    }.build()
    val projection = arrayOf(Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.TITLE)
    val result = checkNotNull(space.contentResolver.query(uri, projection, "${Instances.VISIBLE} = ?", arrayOf("1"),
        "${Instances.BEGIN} ASC")).use { rows ->
        androidCalendarRows(rows, timed, allDay, zone, space::calendarPermitted)
    }
    check(space.calendarPermitted())
    return result
}

internal fun androidCalendarRows(
    rows: Cursor, timed: NovaCalendarDayWindow, allDay: NovaCalendarDayWindow, zone: ZoneId, permitted: () -> Boolean,
): DeviceDay {
    val events = mutableListOf<DeviceOccurrence>()
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    while (rows.moveToNext()) {
        check(permitted())
        val begin = rows.getLong(0)
        val end = rows.getLong(1)
        val fullDay = rows.getInt(2) != 0
        if (!(if (fullDay) allDay else timed).overlaps(begin, end)) continue
        if (events.size == 32) return DeviceDay(events, truncated = true)
        val label = rows.getString(3)?.take(180)?.takeIf(String::isNotBlank) ?: "Untitled event"
        val time = when {
            fullDay -> "All day"
            begin < timed.begin -> "Continues from earlier"
            else -> format.format(Instant.ofEpochMilli(begin).atZone(zone))
        }
        events += DeviceOccurrence(label, time)
    }
    return DeviceDay(events, truncated = false)
}

/** Explicit foreground read. Provider records and the opt-in are never persisted or uploaded. */
@Composable
internal fun NovaAndroidCalendarDay(space: SpaceContext, day: LocalDate, zone: ZoneId) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed = lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    var requested by remember(space, day) { mutableStateOf(false) }
    var report by remember(space, day) { mutableStateOf<DeviceDay?>(null) }
    var message by remember(space, day) { mutableStateOf<String?>(null) }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "Calendar access was declined. Local events are still available."
        else requested = true
    }
    LaunchedEffect(day, resumed, requested) {
        report = null
        if (!resumed) { requested = false; return@LaunchedEffect }
        if (!requested) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { runCatching { readAndroidCalendarDay(space, day, zone) } }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !space.current()) return@LaunchedEffect
        report = loaded.getOrNull()?.takeIf { space.calendarPermitted() }
        if (report == null) { message = "Android Calendar could not be read."; requested = false }
    }
    if (space.spaceId == "primary" && resumed && space.current() && intelligencePrivateAllowed(space.baseContext.applicationContext)) {
        Column {
            if (!requested) OutlinedButton(onClick = {
                message = null
                if (space.calendarPermitted()) requested = true
                else grant.launch(Manifest.permission.READ_CALENDAR)
            }) { Text("Show Android Calendar for selected day") }
            else {
                TextButton(onClick = { requested = false; report = null; message = null }) { Text("Hide Android Calendar") }
                if (report == null) Text("Reading visible calendars…")
                report?.takeIf { space.calendarPermitted() }?.let { value ->
                    if (value.events.isEmpty()) Text("No visible Android events for this day.")
                    for (event in value.events) Text("${event.time} · ${event.title}")
                    if (value.truncated) Text("Showing the first 32 events for this day.")
                }
            }
            message?.let { Text(it) }
        }
    }
}
