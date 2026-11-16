package uk.co.traynor.privategallery.home2

import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal data class NovaCalendarHandoff(val begin: Instant, val end: Instant, val allDay: Boolean)

internal fun NovaCalendarEvent.calendarHandoff(occurrenceDate: LocalDate): NovaCalendarHandoff {
    val start = time
    if (start == null) return NovaCalendarHandoff(
        occurrenceDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
        occurrenceDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), true,
    )
    val begin = occurrenceDate.atTime(start).atZone(zone).toInstant()
    return NovaCalendarHandoff(begin, begin.plusSeconds(minutes * 60L), false)
}

internal fun NovaOccurrence.calendarInsertIntent(): Intent {
    val handoff = event.calendarHandoff(sourceDate)
    return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, handoff.begin.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, handoff.end.toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, handoff.allDay)
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
        .putExtra(CalendarContract.Events.EVENT_TIMEZONE, if (handoff.allDay) "UTC" else event.zone.id)
}
