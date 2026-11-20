package uk.co.traynor.privategallery.home2

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class NovaReminderSchedule(
    val date: LocalDate,
    val time: LocalTime,
    val zone: ZoneId? = null,
    val repeat: NovaEventRepeat = NovaEventRepeat.NONE,
    val skippedDates: Set<LocalDate> = emptySet(),
    val minutesBefore: Int = 0,
) {
    init {
        require(minutesBefore in 0..1440)
    }

    fun nextAfter(after: Instant, deviceZone: ZoneId): Instant? {
        val localZone = zone ?: deviceZone
        val firstPossible = after.plusSeconds(minutesBefore * 60L).atZone(localZone).toLocalDate()
        val dates = when (repeat) {
            NovaEventRepeat.NONE -> sequenceOf(date)
            NovaEventRepeat.DAILY -> generateSequence(maxOf(date, firstPossible)) { it.plusDays(1) }
            NovaEventRepeat.WEEKLY -> {
                val offset = maxOf(0L, ChronoUnit.DAYS.between(date, firstPossible)) / 7
                generateSequence(date.plusWeeks(offset)) { it.plusWeeks(1) }
            }
            NovaEventRepeat.MONTHLY -> {
                val offset = maxOf(0L, ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(firstPossible)))
                generateSequence(offset) { it + 1 }.map(date::plusMonths)
            }
            NovaEventRepeat.YEARLY -> {
                val offset = maxOf(0L, firstPossible.year.toLong() - date.year)
                generateSequence(offset) { it + 1 }.map(date::plusYears)
            }
        }
        return dates.takeWhile { it.year <= 9999 }.filterNot { it in skippedDates }
            .map { it.atTime(time).atZone(localZone).toInstant().minusSeconds(minutesBefore * 60L) }
            .firstOrNull { it > after }
    }
}


internal fun taskReminderSchedule(task: NovaTask): NovaReminderSchedule? {
    val date = task.due ?: return null
    val time = task.remindAt ?: return null
    return if (task.completed) null else NovaReminderSchedule(date, time)
}

internal fun calendarReminderSchedule(event: NovaCalendarEvent): NovaReminderSchedule? {
    val lead = event.reminderMinutes ?: return null
    return NovaReminderSchedule(event.firstDate, event.time ?: LocalTime.of(9, 0),
        event.zone.takeIf { event.time != null }, event.repeat, event.skippedDates, lead)
}
