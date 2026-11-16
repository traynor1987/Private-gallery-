package uk.co.traynor.privategallery.home2

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovaReminderScheduleTest {
    private val london = ZoneId.of("Europe/London")
    private val utc = ZoneId.of("UTC")

    @Test fun calendarKeepsLocalHourAcrossDstAndSkipsExceptions() {
        val schedule = NovaReminderSchedule(LocalDate.parse("2026-03-28"), LocalTime.of(9, 0),
            london, NovaEventRepeat.DAILY, setOf(LocalDate.parse("2026-03-29")), 15)
        assertEquals(Instant.parse("2026-03-30T07:45:00Z"),
            schedule.nextAfter(Instant.parse("2026-03-28T08:45:00Z"), utc))
    }

    @Test fun taskFollowsCurrentZoneAndDoesNotRepeatWithoutANewTask() {
        val schedule = NovaReminderSchedule(LocalDate.parse("2026-04-02"), LocalTime.of(9, 0))
        assertEquals(Instant.parse("2026-04-02T08:00:00Z"),
            schedule.nextAfter(Instant.parse("2026-04-01T12:00:00Z"), london))
        assertNull(schedule.nextAfter(Instant.parse("2026-04-02T08:00:00Z"), london))
    }

    @Test fun monthlyRecurrenceRetainsOriginalDayAndLeadMayCrossMidnight() {
        val schedule = NovaReminderSchedule(LocalDate.parse("2026-01-31"), LocalTime.MIDNIGHT,
            utc, NovaEventRepeat.MONTHLY, emptySet(), 30)
        assertEquals(Instant.parse("2026-02-27T23:30:00Z"),
            schedule.nextAfter(Instant.parse("2026-02-02T00:00:00Z"), utc))
        assertEquals(Instant.parse("2026-03-30T23:30:00Z"),
            schedule.nextAfter(Instant.parse("2026-02-27T23:30:00Z"), utc))
    }
}
