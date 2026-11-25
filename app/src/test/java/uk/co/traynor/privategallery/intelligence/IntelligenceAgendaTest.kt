package uk.co.traynor.privategallery.intelligence

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.home2.NovaCalendarEvent
import uk.co.traynor.privategallery.home2.NovaEventRepeat
import uk.co.traynor.privategallery.home2.NovaTask

class IntelligenceAgendaTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val zone = ZoneId.of("Europe/London")

    @Test fun dashboardKeepsOpenTasksAndUpcomingRemindersAcrossTheDateBoundary() {
        val tasks = listOf(
            NovaTask("overdue", "Overdue", due = today.minusDays(1), remindAt = LocalTime.NOON),
            NovaTask("overdue-reminder", "Earlier task", due = today, remindAt = LocalTime.of(9, 0)),
            NovaTask("no-date", "Set a date"),
            NovaTask("completed", "Done", due = today, completed = true, remindAt = LocalTime.of(23, 0)),
        )
        val events = listOf(
            event("Ended appointment", today, LocalTime.NOON),
            event("Next group meeting", today.plusDays(1), LocalTime.of(10, 0), reminderMinutes = 1_440),
            event("Evening appointment", today, LocalTime.of(18, 0), reminderMinutes = 60),
        )
        val at = today.atTime(13, 0).atZone(zone).toInstant()
        val result = buildIntelligenceAgenda(tasks, events, today, today.plusDays(1), zone,
            includeOverdue = true, upcomingFrom = at)

        assertEquals(listOf("Overdue", "Earlier task"), result.tasks.map { it.substringBefore(" · ") })
        assertEquals(listOf("Set a date"), result.unscheduledTasks)
        assertEquals(listOf("Evening appointment"), result.events.map { it.substringBefore(" · ") })
        assertEquals(listOf("Evening appointment"), result.reminders.map { it.substringBefore(" · ") })

        val dailySummary = buildIntelligenceAgenda(tasks, events, today, today.plusDays(1), zone, includeOverdue = true)
        assertEquals(listOf("Earlier task", "Next group meeting", "Evening appointment"),
            dailySummary.reminders.map { it.substringBefore(" · ") })
    }

    @Test fun tomorrowOmitsOverdueAndUnscheduledTasksAndKeepsAllDayEvents() {
        val tomorrow = today.plusDays(1)
        val result = buildIntelligenceAgenda(listOf(NovaTask("open", "Undated"), NovaTask("today", "Today", due = today)),
            listOf(event("All-day event", tomorrow, null)), tomorrow, tomorrow.plusDays(1), zone, includeOverdue = false)

        assertTrue(result.tasks.isEmpty())
        assertTrue(result.unscheduledTasks.isEmpty())
        assertTrue(result.events.single().endsWith(" · All day"))
    }

    private fun event(title: String, date: LocalDate, time: LocalTime?, reminderMinutes: Int? = null) =
        NovaCalendarEvent(title, title, "", date, time, zone, 60, NovaEventRepeat.NONE, reminderMinutes = reminderMinutes)
}
