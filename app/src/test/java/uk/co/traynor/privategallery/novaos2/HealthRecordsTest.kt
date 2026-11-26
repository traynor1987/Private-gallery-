package uk.co.traynor.privategallery.novaos2

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthRecordsTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test fun manualMeasurementsAreBoundedAndAcceptLocalDecimalNotation() {
        assertEquals("72.45", HealthMetric.WEIGHT.parse(" 72,45 ")?.toPlainString())
        assertEquals("0", HealthMetric.STEPS.parse("0")?.toPlainString())
        listOf("NaN", "Infinity", "1e3", "-1", "", "2,400.75", "500000000001").forEach {
            assertNull(HealthMetric.HYDRATION.parse(it))
        }
        assertNull(HealthMetric.SLEEP.parse("1441"))
        assertNull(HealthMetric.STEPS.parse("100.2"))
        assertNull(HealthMetric.WEIGHT.parse("75.123"))
        assertNull(HealthMetric.RECOVERY.parse("101"))
    }

    @Test fun weeklyReviewDistinguishesMissingRecordsFromRecordedZero() {
        assertNull(HealthRecords().week(today).sleepAverage)
        assertNull(HealthRecords().week(today).hydrationTotal)
        val start = HealthRecords().withHabit("Read")
        val habit = start.habits.single().id
        val logged = start.toggleHabit(today, habit)
            .withMeasurement(today.minusDays(1), HealthMetric.SLEEP, HealthMetric.SLEEP.parse("480"))
            .withMeasurement(today.minusDays(6), HealthMetric.SLEEP, HealthMetric.SLEEP.parse("360"))
            .withMeasurement(today.minusDays(7), HealthMetric.SLEEP, HealthMetric.SLEEP.parse("390"))
            .withMeasurement(today.minusDays(2), HealthMetric.STEPS, HealthMetric.STEPS.parse("0"))
            .withMeasurement(today.minusDays(1), HealthMetric.EXERCISE, HealthMetric.EXERCISE.parse("30"))
            .withMeasurement(today, HealthMetric.HYDRATION, HealthMetric.HYDRATION.parse("0"))
        val week = logged.week(today)
        assertEquals(4, week.recordedDays)
        assertEquals(1, week.activeDays)
        assertEquals("420", week.sleepAverage.toString())
        assertEquals(2, week.sleepDays)
        assertEquals("0", week.hydrationTotal.toString())
        assertEquals(1, week.hydrationDays)
        assertEquals(1, week.habitDays[habit])
        assertThrows(IllegalArgumentException::class.java) { start.withHabit(" read ") }
        assertFalse(logged.withoutHabit(habit).days.values.any { habit in it.habits })
    }

    @Test fun codecRejectsCrossReferencesAndDuplicateDays() {
        val log = HealthRecords().withHabit("Reflect")
        val active = log.toggleHabit(today, log.habits.single().id)
            .withMeasurement(today, HealthMetric.WEIGHT, HealthMetric.WEIGHT.parse("73.25"))
            .copy(goal = "A little outside time")
        assertEquals(active, HealthRecordCodec.decode(HealthRecordCodec.encode(active)))
        val duplicate = JSONObject(HealthRecordCodec.encode(active))
        val days = duplicate.getJSONArray("days")
        days.put(days.getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { HealthRecordCodec.decode(duplicate.toString()) }
        val unknown = JSONObject(HealthRecordCodec.encode(active))
        unknown.getJSONArray("days").getJSONObject(0).getJSONArray("habits")
            .put(0, "94d95d63-d27f-452d-85f3-ea3bfb040a34")
        assertThrows(IllegalArgumentException::class.java) { HealthRecordCodec.decode(unknown.toString()) }
    }
}
