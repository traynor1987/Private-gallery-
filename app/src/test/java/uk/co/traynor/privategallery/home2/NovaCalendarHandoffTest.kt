package uk.co.traynor.privategallery.home2

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NovaCalendarHandoffTest {
    private val date = LocalDate.of(2026, 10, 25)
    private val zone = ZoneId.of("Europe/London")

    @Test fun allDayDateUsesUtcCalendarProviderBoundaries() {
        val event = NovaCalendarEvent("id", "Event", "Private", date, null, zone, 60, NovaEventRepeat.NONE)
        val timing = event.calendarHandoff(date)
        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), timing.begin)
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), timing.end)
    }

    @Test fun timedHandoffUsesOccurrenceDateAndElapsedDurationAcrossDst() {
        val event = NovaCalendarEvent("id", "Event", "Private", date.minusWeeks(1),
            LocalTime.of(1, 30), zone, 90, NovaEventRepeat.WEEKLY)
        val timing = event.calendarHandoff(date)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), timing.begin)
        assertEquals(Instant.parse("2026-10-25T02:00:00Z"), timing.end)
    }
}
