package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class PeaceJournalTest {
    @Test fun intervalsAreClippedAndOverlapsAreCountedOnce() {
        assertEquals(18L, peaceProtectedMillis(listOf(15L to 25L, 1L to 12L, 8L to 18L), 4L, 22L))
        assertEquals(0L, peaceProtectedMillis(listOf(1L to 9L), 10L, 20L))
    }

    @Test fun journalStartsDisabledAndPreservesOnlyRetainedFeedback() {
        assertFalse(PeaceJournal.decode(emptyList()).enabled)
        val day = 86_400_000L
        val entry = PeaceSession("new", 9 * day, 10 * day, "Walk", helpful = true, waiting = WaitingOutcome.BETTER)
        val old = entry.copy(id = "old", end = 0)
        val journal = PeaceJournal(enabled = true, days = 7, sessions = listOf(entry, old))
        assertEquals(journal, PeaceJournal.decode(journal.encode()))
        assertEquals(listOf(entry), journal.retained(11 * day).sessions)
        assertEquals(WaitingOutcome.BETTER, entry.asImpact().waiting)
    }
}
