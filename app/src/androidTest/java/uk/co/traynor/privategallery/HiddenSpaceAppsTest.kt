package uk.co.traynor.privategallery

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.backup.BackupPolicy
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.home2.*
import uk.co.traynor.privategallery.ui.HiddenMediaScope

class HiddenSpaceAppsTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sameRecordIdsStayIndependentAndHiddenHandlesRetireOnExpiry() {
        val root = File(base.cacheDir, "hidden-space-test-${UUID.randomUUID()}")
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "primary")
            override fun getNoBackupFilesDir() = File(root, "hidden")
        }
        var clock = 0L
        val session = HiddenSession { clock }
        val hiddenKey = ByteArray(32) { 38 }
        val primaryKey = ByteArray(32) { 72 }
        val identity = Any()
        assertTrue(session.complete(session.beginAuthentication(), hiddenKey.copyOf()))
        try {
            EvidenceAccess.primary(context, identity, { identity }, { true }, { primaryKey.copyOf() }, Any()).use { primary ->
                EvidenceAccess.hidden(context, session) { true }.use { hidden ->
                    SpaceContext(context, primary).use { p ->
                        SpaceContext(context, hidden).use { h ->
                            writeFixture(p, "primary marker")
                            writeFixture(h, "hidden marker")
                            assertFixture(p, "primary marker")
                            assertFixture(h, "hidden marker")
                            assertTrue(root.walkTopDown().filter { it.isFile }.none {
                                it.readBytes().toString(Charsets.ISO_8859_1).contains("hidden marker")
                            })
                            assertTrue(NovaCoreApp.entries.none { BackupPolicy.allowed(it.namespace, "hidden") })
                            session.onBackgroundAt(1000L, clock)
                            clock = 1001L
                            session.onForeground()
                            assertFalse(h.current())
                            assertTrue(runCatching { NovaNotesLibrary(h).read() }.isFailure)
                            assertFixture(p, "primary marker")
                        }
                    }
                }
            }
            assertTrue(session.complete(session.beginAuthentication(), hiddenKey.copyOf()))
            EvidenceAccess.hidden(context, session) { true }.use { hidden ->
                SpaceContext(context, hidden).use { assertFixture(it, "hidden marker") }
            }
        } finally {
            session.lock()
            hiddenKey.fill(0)
            primaryKey.fill(0)
            root.deleteRecursively()
        }
    }

    @Test fun downloadRemainsEncryptedAndDoesNotRetagAnExistingFile() {
        val root = File(base.cacheDir, "hidden-download-test-${UUID.randomUUID()}")
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        val session = HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(), ByteArray(32) { 11 }))
        try {
            val repository = HiddenRepository(context, session)
            val bytes = "private download fixture".encodeToByteArray()
            val file = repository.importMedia("document.txt", "text/plain", ByteArrayInputStream(bytes))
            val download = repository.importBrowserDownload("Hidden download.txt", "text/plain", ByteArrayInputStream(bytes))
            assertNotEquals(file.id, download.id)
            assertEquals(listOf(download.id), repository.items().filter(HiddenMediaScope.DOWNLOADS::includes).map { it.id })
            assertArrayEquals(bytes, repository.read(download.id))
            assertTrue(root.walkTopDown().filter { it.isFile }.none {
                it.readBytes().toString(Charsets.ISO_8859_1).contains("private download fixture")
            })
            session.lock()
            assertTrue(runCatching { repository.read(download.id) }.isFailure)
        } finally {
            session.lock()
            root.deleteRecursively()
        }
    }

    private fun writeFixture(space: SpaceContext, label: String) {
        val notes = NovaNotesLibrary(space)
        assertNotNull(notes.save(notes.read(), listOf(NovaNote("shared", label, label))))
        val tasks = NovaTaskLibrary(space)
        assertNotNull(tasks.save(tasks.read(), listOf(NovaTask("shared", label))))
        val calendar = NovaCalendarLibrary(space)
        assertNotNull(calendar.save(calendar.read(), listOf(
            NovaCalendarEvent("shared", label, label, LocalDate.of(2027, 1, 1),
                null, ZoneId.of("UTC"), 60, NovaEventRepeat.NONE),
        )))
        val clipboard = NovaClipboardLibrary(space)
        val now = Instant.now()
        assertNotNull(clipboard.save(clipboard.read(now), SnippetRetention.MANUAL,
            listOf(NovaSnippet("shared", label, label, "", false, false, now)), now))
    }

    private fun assertFixture(space: SpaceContext, label: String) {
        assertEquals(label, NovaNotesLibrary(space).read().notes.single().body)
        assertEquals(label, NovaTaskLibrary(space).read().tasks.single().title)
        assertEquals(label, NovaCalendarLibrary(space).read().events.single().title)
        assertEquals(label, NovaClipboardLibrary(space).read(Instant.now()).snippets.single().text)
    }
}
