package uk.co.traynor.privategallery.intelligence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.home2.NovaCalendarEvent
import uk.co.traynor.privategallery.home2.NovaCalendarLibrary
import uk.co.traynor.privategallery.home2.NovaEventRepeat
import uk.co.traynor.privategallery.home2.NovaNote
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaTaskLibrary

class IntelligenceWorkspaceTest {
    @Test fun readsAndWritesOriginalLibrariesAndRestoresEncryptedMemoryAfterReopen() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(intelligenceDeviceReady(app))
        val root = File(app.cacheDir, "intelligence-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val day = LocalDate.of(2026, 11, 20)
        val zone = ZoneId.of("Europe/London")
        val id = UUID.randomUUID().toString()
        val privateText = "Synthetic private intelligence memory marker"
        val briefingName = "Synthetic greeting marker"
        val exchange = IntelligenceExchange(privateText, "An encrypted response")
        val memory = IntelligenceMemory(true, listOf(exchange))
        val identity = Any()
        val gate = Any()
        fun reopen() = EvidenceAccess.primary(isolated, identity, { identity }, { true }, { ByteArray(32) { 41 } }, gate)
        try {
            reopen().use { access ->
                SpaceContext(isolated, access).use { space ->
                    val workspace = IntelligenceWorkspace(space)
                    val events = NovaCalendarLibrary(space)
                    val event = NovaCalendarEvent(UUID.randomUUID().toString(), "Fixture appointment", "", day.plusDays(1),
                        LocalTime.of(11, 0), zone, 60, NovaEventRepeat.NONE, reminderMinutes = 30)
                    assertNotNull(events.save(events.read(), listOf(event)))
                    workspace.createTask(id, "Fixture task", day.plusDays(1))
                    workspace.createTask(id, "Duplicate task", day)
                    workspace.createNote(UUID.randomUUID().toString(), "Fixture note", "Synthetic")
                    val notes = NovaNotesLibrary(space)
                    val current = notes.read()
                    val privateNote = NovaNote(UUID.randomUUID().toString(), "Fixture confidential", "No preview", notebook = "Private")
                    assertNotNull(notes.save(current, current.notes + privateNote))
                    assertNotNull(notes.setNotebookProtected(notes.read(), "Private", true))
                    assertEquals("Fixture task", NovaTaskLibrary(space).read().tasks.single().title)
                    assertEquals(listOf("Fixture note", "Fixture task", "Fixture appointment"), workspace.find("Fixture").map { it.second })
                    val tomorrow = workspace.agenda(day.plusDays(1), day.plusDays(2), zone, false)
                    assertTrue(tomorrow.tasks.single().contains("Fixture task"))
                    assertTrue(tomorrow.events.single().contains("Fixture appointment"))
                    assertTrue(tomorrow.reminders.single().contains("Fixture appointment"))
                    assertNull(workspace.briefingName())
                    workspace.saveBriefingName("  $briefingName  ")
                    assertTrue(runCatching { workspace.saveBriefingName("Invalid\nname") }.isFailure)
                    assertTrue(runCatching { IntelligenceWorkspace(space) { false }.briefingName() }.isFailure)
                    workspace.saveMemory(memory)
                    assertTrue(runCatching { IntelligenceWorkspace(space) { false }.createTask(id, "Blocked", day) }.isFailure)
                }
            }
            val files = root.walkTopDown().filter(File::isFile).toList()
            assertTrue(files.any { it.extension == "enc" })
            assertFalse(files.any { it.readBytes().decodeToString().contains(privateText) })
            assertFalse(files.any { it.readBytes().decodeToString().contains(briefingName) })
            reopen().use { access ->
                SpaceContext(isolated, access).use { space ->
                    val restarted = IntelligenceWorkspace(space)
                    assertEquals(briefingName, restarted.briefingName())
                    restarted.saveBriefingName(null)
                    assertNull(restarted.briefingName())
                    assertEquals(memory, restarted.memory())
                    assertFalse(restarted.saveAutomaticMemory(listOf(exchange), restarted.memoryGeneration()) { true })
                    val stamped = exchange.copy(timestamp = Instant.parse("2026-11-23T12:01:02.123456Z"))
                    restarted.saveMemory(memory.copy(autoSave = true))
                    val oldGeneration = restarted.memoryGeneration()
                    assertTrue(restarted.saveAutomaticMemory(listOf(stamped), oldGeneration) { true })
                    assertEquals(stamped, restarted.memory().exchanges.single())
                    restarted.saveMemory(IntelligenceMemory(true, emptyList(), autoSave = true))
                    assertFalse(restarted.saveAutomaticMemory(listOf(stamped), oldGeneration) { true })
                    assertTrue(restarted.memory().exchanges.isEmpty())
                    restarted.saveMemory(IntelligenceMemory(false, emptyList()))
                    assertFalse(restarted.memory().enabled)
                }
            }
        } finally { root.deleteRecursively() }
    }
}
