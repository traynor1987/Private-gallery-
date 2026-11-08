package uk.co.traynor.privategallery.ui

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceDates68Test {
    @Test fun localDayFilterCoversShortAndLongDaysWithoutChangingStoredUtc() {
        val zone = ZoneId.of("Europe/London")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 29) to 23L, LocalDate.of(2026, 10, 25) to 25L)) {
            val (start, end) = evidenceLocalDateRange(date, date, zone)
            assertEquals(hours * 60 * 60 * 1000, end - start + 1)
        }
    }
}
