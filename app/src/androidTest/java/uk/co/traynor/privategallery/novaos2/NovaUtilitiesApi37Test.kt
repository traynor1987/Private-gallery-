package uk.co.traynor.privategallery.novaos2

import android.content.ClipData
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.database.MatrixCursor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaTaskLibrary
import uk.co.traynor.privategallery.home2.NovaFile
import uk.co.traynor.privategallery.home2.androidCalendarRows
import uk.co.traynor.privategallery.home2.calendarDayWindow
import uk.co.traynor.privategallery.home2.novaFileShareIntent
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaUtilitiesApi37Test {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule { method ->
        if (method.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    @Test fun coverUtilityCardsDispatchExistingEntry() {
        val opened = mutableListOf<String>()
        showGrid(false, opened)
        compose.onNodeWithText("Nova Notes").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("Nova Notes"), opened) }
        captureNovaSystem("utilities-cover")
    }

    @Test fun innerUtilityCardsKeepBothColumnsAccessible() {
        val opened = mutableListOf<String>()
        showGrid(true, opened)
        compose.onNodeWithText("Nova Tasks").assertIsDisplayed().performClick()
        compose.onNodeWithText("Nova Update Centre").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("Nova Tasks"), opened) }
        captureNovaSystem("utilities-inner")
    }

    @Test fun shareTargetRejectsStreamsBeforeIssuingAnyHandoff() {
        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Fixture")
        assertEquals("Fixture", acceptedNovaShare(text))
        val stream = Uri.parse("content://fixture/private")
        assertNull(acceptedNovaShare(Intent(text).putExtra(Intent.EXTRA_STREAM, stream)))
        assertNull(acceptedNovaShare(Intent(text).also { it.clipData = ClipData.newRawUri("fixture", stream) }))
        assertNull(acceptedNovaShare(Intent(text).setType("text/html")))
        assertNull(acceptedNovaShare(Intent(text).setAction(Intent.ACTION_SEND_MULTIPLE)))
    }

    @Test fun reviewedShareWritesExistingEncryptedNotesAndTasksOnlyWhileAuthorized() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "nova-utilities-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val owner = Any()
        val gate = Any()
        fun access() = EvidenceAccess.primary(isolated, owner, { owner }, { true }, { ByteArray(32) { 27 } }, gate)
        val privateText = "Synthetic reviewed share with private content"
        try {
            access().use { lease -> SpaceContext(isolated, lease).use { space ->
                assertFalse(saveNovaSharedRecord(space, "Fixture", privateText, true) { false })
                assertTrue(NovaNotesLibrary(space).read().notes.isEmpty())
                assertTrue(saveNovaSharedRecord(space, "Fixture note", privateText, true) { true })
                assertTrue(saveNovaSharedRecord(space, "Fixture task", privateText, false) { true })
            } }
            assertTrue(root.walkTopDown().any { it.isFile && it.extension == "enc" })
            assertFalse(root.walkTopDown().filter(File::isFile).any { it.readBytes().decodeToString().contains(privateText) })
            access().use { lease -> SpaceContext(isolated, lease).use { space ->
                assertEquals(privateText, NovaNotesLibrary(space).read().notes.single().body)
                assertEquals(privateText, NovaTaskLibrary(space).read().tasks.single().description)
            } }
        } finally { root.deleteRecursively() }
    }

    @Test fun calendarLimitsTransientDeviceEventsAndUsesUtcForAllDay() {
        val day = LocalDate.of(2026, 3, 29)
        val zone = ZoneId.of("Europe/London")
        val local = calendarDayWindow(day, zone)
        val utc = calendarDayWindow(day, ZoneOffset.UTC)
        assertEquals(23L * 3_600_000, local.end - local.begin)
        MatrixCursor(arrayOf("begin", "end", "allDay", "title")).use { rows ->
            rows.addRow(arrayOf(local.begin - 1L, local.begin, 0, "Yesterday"))
            rows.addRow(arrayOf(utc.begin, utc.end, 1, "All day event"))
            rows.addRow(arrayOf(local.end, local.end + 1L, 0, "Tomorrow"))
            rows.addRow(arrayOf(local.end, utc.end, 1, "UTC-only overlap"))
            repeat(40) { rows.addRow(arrayOf(local.begin, local.begin + 1L, 0, "Timed event")) }
            val result = androidCalendarRows(rows, local, utc, zone) { true }
            assertTrue(result.truncated)
            assertEquals(32, result.events.size)
            assertEquals(listOf("All day event", "UTC-only overlap"), result.events.take(2).map { it.title })
            assertTrue(result.events.drop(2).all { it.title == "Timed event" })
            rows.moveToPosition(-1)
            assertTrue(runCatching { androidCalendarRows(rows, local, utc, zone) { false } }.isFailure)
        }
    }

    @Test fun selectedDocumentSharesOnlyItsReadGrant() {
        val root = DocumentsContract.buildTreeDocumentUri("example.documents", "root")
        val selected = DocumentsContract.buildDocumentUriUsingTree(root, "report")
        val file = NovaFile("report", "Report", "text/plain", 30L, null, 0)
        val share = novaFileShareIntent(selected, file)
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, share.flags)
        assertEquals("text/plain", share.type)
        assertEquals(selected, share.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(1, share.clipData!!.itemCount)
        assertEquals(selected, share.clipData!!.getItemAt(0).uri)
        for (wrong in listOf(file.copy(id = "sibling"),
            file.copy(mimeType = Document.MIME_TYPE_DIR),
            file.copy(flags = Document.FLAG_VIRTUAL_DOCUMENT))) {
            assertTrue(runCatching { novaFileShareIntent(selected, wrong) }.isFailure)
        }
        assertTrue(runCatching { novaFileShareIntent(root, file) }.isFailure)
    }

    @Test fun savedUsageTotalsRequireConsentReplaceDatesAndCanBeErased() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "nova-usage-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val owner = Any()
        val gate = Any()
        fun access() = EvidenceAccess.primary(isolated, owner, { owner }, { true }, { ByteArray(32) { 27 } }, gate)
        val start = LocalDate.of(2026, 1, 1)
        try {
            access().use { lease -> SpaceContext(isolated, lease).use { space ->
                assertTrue(runCatching { NovaUsageHistory.save(space, mapOf(start to 100L)) { false } }.isFailure)
                assertTrue(NovaUsageHistory.read(space).isEmpty())
                for (batch in 0..4) {
                    val days = (0..6).associate { start.plusDays((batch * 7 + it).toLong()) to 100L }
                    NovaUsageHistory.save(space, days) { true }
                }
                val last = start.plusDays(34)
                val saved = NovaUsageHistory.save(space, mapOf(last to 350L)) { true }
                assertEquals(31, saved.size)
                assertFalse(start in saved)
                assertEquals(350L, saved.getValue(last))
            } }
            assertTrue(root.walkTopDown().any { it.isFile && it.extension == "enc" })
            assertFalse(root.walkTopDown().filter(File::isFile).any { it.readBytes().decodeToString().contains("2026-01-") })
            access().use { lease -> SpaceContext(isolated, lease).use { space ->
                assertEquals(31, NovaUsageHistory.read(space).size)
                assertTrue(runCatching { NovaUsageHistory.clear(space) { false } }.isFailure)
                NovaUsageHistory.clear(space) { true }
                assertTrue(NovaUsageHistory.read(space).isEmpty())
            } }
        } finally { root.deleteRecursively() }
    }

    @Test fun installedVersionHistoryRecordsOnlyObservedVersionsAndDeduplicates() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val history = NovaSystemConfig(app, "test-updates-${UUID.randomUUID().toString().take(24)}")
        try {
            val installed = PackageInfo().apply {
                versionName = "fixture"
                setLongVersionCode(99L)
                lastUpdateTime = 100_000L
            }
            assertTrue(NovaUpdateHistory.read(history).isEmpty())
            repeat(2) { NovaUpdateHistory.recordObserved(history, installed) }
            assertEquals(listOf(NovaObservedVersion("fixture", 99L, 100_000L)), NovaUpdateHistory.read(history))
            installed.setLongVersionCode(100L)
            installed.lastUpdateTime = 200_000L
            NovaUpdateHistory.recordObserved(history, installed)
            assertEquals(listOf(100L, 99L), NovaUpdateHistory.read(history).map { it.build })
        } finally { history.clear() }
    }

    private fun showGrid(wide: Boolean, opened: MutableList<String>) {
        val labels = listOf("Nova Notes", "Nova Tasks", "Nova Calendar", "Nova Files", "Nova Clipboard", "Nova Contacts",
            "Nova Share Hub", "Nova App Usage", "Nova Update Centre")
        compose.setContent {
            MaterialTheme(colorScheme = if (wide) lightColorScheme() else darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        UtilitiesGrid(labels.map { label -> UtilityItem(label, "Open $label") { opened.add(label) } },
                            wide, onUnavailable = { error("The fixture action must be available") })
                    }
                }
            }
        }
    }
}
