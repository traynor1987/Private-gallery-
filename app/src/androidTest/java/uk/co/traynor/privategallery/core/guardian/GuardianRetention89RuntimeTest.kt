package uk.co.traynor.privategallery.core.guardian

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuardianRetention89RuntimeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName.endsWith(".hiddenprototype")) { "Isolated fixtures only" }
    }
    private fun event(now: Long, count: Int = 1, old: Long = now) = GuardianCaptureEvent(0, "synthetic retention fixture", now,
        List(count) { GuardianCaptureEntry(old - it, "synthetic sender", "Unique synthetic ordinary message $it") })
    private fun later(now: GuardianInboxClock, days: Long) = now.copy(wall = now.wall + days * GuardianRetention89.DAY,
        elapsed = now.elapsed + days * GuardianRetention89.DAY)

    @Test fun thirtyDaysAndSevenDaysArchiveWithoutDeletingOrRewritingAfterRestart() {
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val now = guardianInboxClock(context)
            assertTrue(store.append(event(now.wall, old = now.wall - 90 * GuardianRetention89.DAY), 30, now.wall, false, now).allDurable)
            val archive = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val bytes = archive.readBytes()
            for (days in listOf(7, 30)) {
                val reopened = GuardianCaptureStore(context)
                val before = later(now, days.toLong()).let { it.copy(wall = it.wall - 1, elapsed = it.elapsed - 1) }
                assertEquals(GuardianInboxSection.PENDING, reopened.inboxRows(days, before).single().section)
                assertEquals(1L, reopened.inboxCounts(days, before).refreshAfterMillis)
                assertEquals(GuardianInboxSection.HISTORY, reopened.inboxRows(days, later(now, days.toLong())).single().section)
                assertEquals("Unique synthetic ordinary message 0", reopened.revealInboxMessage(reopened.inboxRows(days, now).single().id, days, later(now, 365)))
                assertEquals(1, reopened.summary(days, later(now, 365).wall))
                assertArrayEquals(bytes, archive.readBytes())
            }
            assertFalse(bytes.toString(Charsets.UTF_8).contains("Unique synthetic"))
        } finally { store.clear() }
    }

    @Test fun foreverSurvivesMoreThan256CapturesAndBackgroundRefresh() {
        val store = GuardianCaptureStore(context)
        store.clear()
        val prefs = context.getSharedPreferences("nova_guardian_capture", 0)
        val previous = GuardianCapture.retention(context)
        try {
            val now = guardianInboxClock(context)
            assertTrue(store.append(event(now.wall, 300), 0, now.wall, false, now).allDurable)
            assertEquals(300, store.inboxRows(0, later(now, 365)).size)
            assertTrue(store.inboxRows(0, later(now, 365)).all { it.section == GuardianInboxSection.PENDING })
            assertNull(store.inboxCounts(0, later(now, 365)).refreshAfterMillis)
            check(prefs.edit().putInt("retention", 0).commit())
            GuardianCapture.refresh(context)
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (GuardianCapture.state.value.busy && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
            assertFalse(GuardianCapture.state.value.busy)
            assertEquals(300, GuardianCapture.state.value.retained)
            assertEquals(300, GuardianCaptureStore(context).summary(0, later(now, 365).wall))
        } finally { check(prefs.edit().putInt("retention", previous).commit()); store.clear() }
    }

    @Test fun directTeachingAndInboxClearingPreserveEncryptedTrainingOriginals() {
        val store = GuardianCaptureStore(context)
        store.clear()
        store.resetLearning()
        try {
            val now = guardianInboxClock(context)
            store.append(event(now.wall, 3), 30, now.wall, false, now)
            val rows = store.inboxRows(30, now)
            val saved = rows[0].id
            assertTrue(store.saveInboxForTraining(saved, GuardianAssessment.UNCERTAIN, 30, now))
            assertEquals(1, store.learningCount())
            assertTrue(store.reviewInboxMessage(rows[1].id, GuardianInboxAction.ARCHIVE, 30, now))
            val result = store.clearInboxPreservingTraining(now)
            assertEquals(2, result.deleted)
            assertEquals(1, result.trainingPreserved)
            val reopened = GuardianCaptureStore(context)
            val retained = reopened.inboxRows(7, later(now, 365)).single()
            assertEquals(saved, retained.id)
            assertTrue(retained.savedForTraining)
            assertEquals(GuardianInboxSection.HISTORY, retained.section)
            assertNotNull(reopened.revealInboxMessage(saved, 7, later(now, 365)))
            assertEquals(1, reopened.learningCount())
            assertTrue(reopened.forgetInboxTeaching(saved, 7, later(now, 365)))
            assertEquals(0, reopened.learningCount())
            assertTrue(reopened.inboxRows(7, later(now, 365)).single().savedForTraining)
            assertEquals(1, reopened.deleteInboxMessages(setOf(saved), true, later(now, 365)).deleted)
            assertEquals(0, reopened.summary(0, now.wall))
            assertEquals(0, reopened.inspectRecovery().recoverable)
        } finally { store.resetLearning(); store.clear() }
    }

    @Test fun recoveryPreservesRawAtomicBackupBeforeReadAndRespectsTombstones() {
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val now = guardianInboxClock(context)
            store.append(event(now.wall), 30, now.wall, false, now)
            val archive = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val older = archive.readBytes()
            store.append(event(now.wall + 1_000, old = now.wall + 1_000), 30, now.wall + 1_000, false, now)
            val newer = archive.readBytes()
            check(File(context.noBackupFilesDir, "guardian-recovery89").deleteRecursively())
            File(archive.path + ".bak").writeBytes(older)
            assertEquals(1, GuardianCaptureStore(context).summary(30, now.wall))
            assertArrayEquals(newer, File(context.noBackupFilesDir, "guardian-recovery89/capture-0.enc").readBytes())
            val report = store.inspectRecovery()
            assertEquals(2, report.copies)
            assertEquals(1, report.recoverable)
            assertEquals(1, store.restoreRecovery())
            assertEquals(2, store.summary(30, later(now, 365).wall))
            val ids = store.inboxRows(0, now).mapTo(linkedSetOf()) { it.id }
            assertEquals(2, store.deleteInboxMessages(ids, true, now).deleted)
            assertEquals(0, store.inspectRecovery().recoverable)
            assertEquals(0, store.restoreRecovery())
            val copies = File(context.noBackupFilesDir, "guardian-recovery89").listFiles()!!.filter { it.extension == "enc" }
            assertTrue(copies.isNotEmpty())
            assertTrue(copies.all { !it.readBytes().toString(Charsets.UTF_8).contains("synthetic") })
        } finally { store.clear() }
    }

    @Test fun activeHoldsCannotBeRevealedTaughtOrBulkDeleted() {
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val now = guardianInboxClock(context)
            store.append(event(now.wall), 0, now.wall, false, now)
            val id = store.inboxRows(0, now).single().id
            assertTrue(store.reviewInboxMessage(id, GuardianInboxAction.HOLD, 0, now))
            assertFalse(store.saveInboxForTraining(id, GuardianAssessment.LOW_RISK, 0, now))
            assertNull(store.revealInboxMessage(id, 0, now))
            assertEquals(1, store.deleteInboxMessages(setOf(id), true, now).held)
            assertEquals(1, store.summary(0, now.wall))
        } finally { store.clear() }
    }

    @Test fun storageCapacityRefusesNewCaptureWithoutEvictingOldMessages() {
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val now = guardianInboxClock(context)
            store.append(event(now.wall), 30, now.wall, false, now)
            val archive = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val original = archive.readBytes()
            val entries = List(3_500) { GuardianCaptureEntry(now.wall + 1, "synthetic", "$it " + "x".repeat(1_990)) }
            val result = store.append(GuardianCaptureEvent(0, "capacity fixture", now.wall + 1, entries), 0, now.wall + 1, false, now)
            assertFalse(result.allDurable)
            assertTrue(result.partial)
            assertEquals(0, result.added)
            assertArrayEquals(original, archive.readBytes())
            assertEquals(1, GuardianCaptureStore(context).summary(30, now.wall))
        } finally { store.clear() }
    }

    @Test fun unreadableRecoveryCopyIsReportedWithoutInventingMessages() {
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val now = guardianInboxClock(context)
            store.append(event(now.wall), 30, now.wall, false, now)
            val archive = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val original = archive.readBytes()
            val corrupt = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            File(context.noBackupFilesDir, "guardian-recovery89/capture-2.enc").writeBytes(corrupt)
            val report = store.inspectRecovery()
            assertEquals(1, report.unreadable)
            assertEquals(0, report.recoverable)
            assertEquals(0, store.restoreRecovery())
            assertArrayEquals(original, archive.readBytes())
            assertEquals(1, store.summary(0, now.wall))
        } finally { store.clear() }
    }

    @Test fun committedDeletionIntentSurvivesAtomicRollbackAndCannotBeRecovered() {
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val now = guardianInboxClock(context)
            store.append(event(now.wall), 30, now.wall, false, now)
            val archive = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val old = archive.readBytes()
            val id = store.inboxRows(30, now).single().id
            assertEquals(1, store.deleteInboxMessages(setOf(id), true, now).deleted)
            val journal = File(context.noBackupFilesDir, "guardian-recovery89/deletions.enc")
            assertTrue(journal.isFile)
            assertFalse(journal.readBytes().toString(Charsets.UTF_8).contains(id))
            // A rolled-back main archive must not reverse committed deletion intent.
            File(archive.path + ".bak").writeBytes(old)
            val reopened = GuardianCaptureStore(context)
            assertEquals(0, reopened.summary(30, now.wall))
            assertNull(reopened.revealInboxMessage(id, 30, now))
            assertEquals(0, reopened.inspectRecovery().recoverable)
            assertEquals(0, reopened.restoreRecovery())
            assertEquals(0, GuardianCaptureStore(context).summary(0, now.wall))
        } finally { store.clear() }
    }
}
