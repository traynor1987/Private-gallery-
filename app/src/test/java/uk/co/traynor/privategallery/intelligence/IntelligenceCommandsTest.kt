package uk.co.traynor.privategallery.intelligence

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.guardian.GuardianAssessment
import uk.co.traynor.privategallery.core.guardian.GuardianInboxRow
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode

class IntelligenceCommandsTest {
    private val today = LocalDate.of(2026, 11, 20)

    @Test fun agendaAndGuardianRequestsStayLocal() {
        assertEquals(IntelligenceCommand.Briefing(today), intelligenceCommand("Summarise my day.", today))
        assertEquals(IntelligenceCommand.Briefing(today.plusDays(1)), intelligenceCommand("What’s on tomorrow?", today))
        assertEquals(IntelligenceCommand.Briefing(today.plusDays(1)), intelligenceCommand("What do I have tomorrow?", today))
        assertEquals(IntelligenceCommand.Reminders, intelligenceCommand("What reminders do I have?", today))
        assertEquals(IntelligenceCommand.Notifications, intelligenceCommand("Any important messages?", today))
        assertEquals(IntelligenceCommand.Held, intelligenceCommand("Show my held messages.", today))
        assertEquals(IntelligenceDestination.NOTES, intelligenceDestination("Nova Notes"))
        assertEquals(IntelligenceDestination.TASKS, intelligenceDestination("Reminders"))
    }

    @Test fun taskAndNotePayloadsRequireContentsAndPreserveDates() {
        val due = today.plusDays(1)
        assertEquals(IntelligenceCommand.CreateTask("Call the garage", due),
            intelligenceCommand("Create a task for tomorrow: Call the garage", today))
        assertEquals(IntelligenceCommand.CreateTask("Call the garage", due),
            intelligenceCommand("Add task Call the garage tomorrow", today))
        assertEquals(due, (intelligenceCommand("Create a task for tomorrow.", today) as IntelligenceCommand.Clarify).taskDue)
        assertEquals(IntelligenceCommand.CreateNote("Ideas", "Write an outline"),
            intelligenceCommand("Create a note: Ideas | Write an outline", today))
        assertTrue(intelligenceCommand("Create a notebook", today) is IntelligenceCommand.Ask)
    }

    @Test fun onlyApprovedExactAppNamesResolveAndHiddenHasNoRoute() {
        val maps = IntelligenceApp("Maps", "example.maps/.Main")
        assertEquals(maps, intelligenceApp("MAPS", listOf(maps)))
        assertNull(intelligenceApp("Maps", listOf(maps, maps.copy(component = "example.maps2/.Main"))))
        assertNull(intelligenceApp("example.maps/.Main", listOf(maps)))
        assertNull(intelligenceDestination("Hidden Vault"))
        assertNull(intelligenceDestination("Settings"))
        assertTrue(intelligenceCommand("Open evidence content://vault/example", today) is IntelligenceCommand.Clarify)
        val id = "c".repeat(64)
        assertEquals(IntelligenceCommand.Evidence(id), intelligenceCommand("Open evidence $id", today))
    }

    @Test fun briefingCountsUseAssessmentAndHoldMetadataWithoutSenderOrMessageData() {
        val zone = ZoneId.of("Europe/London")
        val time = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val rows = listOf(
            GuardianInboxRow("safe", time, GuardianInboxSection.READY, 0, GuardianAssessment.LOW_RISK),
            GuardianInboxRow("held", time, GuardianInboxSection.COOLING, 100, GuardianAssessment.UPSETTING),
            GuardianInboxRow("pending", time, GuardianInboxSection.PENDING, 0, null),
            GuardianInboxRow("old", time - 86_400_000, GuardianInboxSection.PROTECTED, 0, GuardianAssessment.UPSETTING),
            GuardianInboxRow("protected after correction", time - 86_400_000, GuardianInboxSection.PROTECTED, 0, GuardianAssessment.LOW_RISK),
        )
        assertEquals(IntelligenceGuardian(5, 1, 3, 1, 3, 3, 1, true),
            summarizeIntelligenceGuardian(rows, today, zone, evidencePresent = true))
        assertEquals(LocalTime.of(18, 30), IntelligenceBriefingTimes(afterWork = LocalTime.of(18, 30)).windows().single().second)
    }

    @Test fun sanctuaryBriefingReflectsModeAndRoundsRemainingTimeUp() {
        assertEquals("Work Mode · Active · 1m left", intelligenceSanctuaryStatus(SanctuaryMode.WORK, false, 1L))
        assertEquals("Panic Mode · Launcher protection paused · 2h 1m left",
            intelligenceSanctuaryStatus(SanctuaryMode.PANIC, true, 7_200_001L))
    }
}
