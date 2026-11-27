package uk.co.traynor.privategallery.novaos2

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovaUtilitiesTest {
    @Test fun sharedTextIsBoundedAndStaysPlain() {
        assertEquals("https://example.invalid/\n<tag>", parseNovaSharedText("https://example.invalid/\r\n<tag>"))
        assertNull(parseNovaSharedText(" \n\t"))
        assertNull(parseNovaSharedText("bad\u0000text"))
        assertNull(parseNovaSharedText("a".repeat(NOVA_SHARE_TEXT_LIMIT + 1)))
        assertEquals("A title", novaShareTitle("\n  A title  \nMore text"))
        assertEquals("x".repeat(199), novaShareTitle("x".repeat(199) + "\uD83D\uDE80"))
    }

    @Test fun dialAcceptsNumbersAndRejectsUrisOrServiceCommands() {
        assertEquals("+447700900123", normalizeNovaDialNumber("+44 (7700) 900-123"))
        assertEquals("112", normalizeNovaDialNumber("112"))
        for (raw in listOf("", "tel:112", "+", "#31#112", "123;4", "++123", "0000000000000000000000000")) {
            assertNull(normalizeNovaDialNumber(raw))
        }
    }

    @Test fun foregroundUnionExcludesProtectedTimeAndIgnoresUnlistedApps() {
        val start = Instant.parse("2026-11-26T00:00:00Z").toEpochMilli()
        fun whenAt(offset: Long, kind: NovaUsageEventKind, app: String = "") =
            NovaUsageEvent(start + offset, kind, app, "Activity")
        val events = sequenceOf(
            whenAt(-30L, NovaUsageEventKind.SCREEN_ON), whenAt(-25L, NovaUsageEventKind.UNLOCKED),
            whenAt(-20L, NovaUsageEventKind.RESUMED, "maps"), whenAt(5L, NovaUsageEventKind.RESUMED, "music"),
            whenAt(6L, NovaUsageEventKind.RESUMED, "excluded"), whenAt(10L, NovaUsageEventKind.PAUSED, "maps"),
            whenAt(15L, NovaUsageEventKind.SCREEN_OFF), whenAt(20L, NovaUsageEventKind.SCREEN_ON),
            whenAt(25L, NovaUsageEventKind.RESUMED, "music"), whenAt(30L, NovaUsageEventKind.LOCKED),
        )
        val report = aggregateNovaUsage(events, start, start + 40L, setOf("maps", "music"), ZoneOffset.UTC,
            listOf(NovaUsageExclusion(start + 8L, start + 12L)))
        assertEquals(mapOf("maps" to 8L, "music" to 11L), report.appMillis)
        assertEquals(mapOf(LocalDate.of(2026, 11, 26) to 16L), report.dailyMillis)
    }

    @Test fun noObservedUnlockedPeriodReturnsEmptyReport() {
        val events = sequenceOf(NovaUsageEvent(3, NovaUsageEventKind.RESUMED, "maps", "View"))
        assertEquals(NovaUsageReport(emptyMap(), emptyMap()), aggregateNovaUsage(events, 0, 9,
            setOf("maps"), ZoneOffset.UTC))
    }
}
