package uk.co.traynor.privategallery.core.guardian

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.brain.CloudLesson
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuardianEvidence90RuntimeTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName.endsWith(".hiddenprototype"))
    }
    private val key = GuardianConversationKey(GUARDIAN_WHATSAPP, 0, "shortcut:synthetic")
    private val configuration = NovaBrainConfiguration("https://synthetic.example", "synthetic-owner-token-".repeat(3))
    private val sentence = "Taylor please stop asking me about this"
    private fun capture(now: GuardianInboxClock) = GuardianCaptureEvent(key.profile, key.conversation, now.wall,
        listOf(GuardianCaptureEntry(now.wall, "Taylor", sentence, "Taylor")), captureAccount = "MAIN_WHATSAPP")

    private fun fixture(run: (GuardianCaptureStore, GuardianAutomaticCloudSettings, GuardianInboxClock) -> Unit) {
        val store = GuardianCaptureStore(context)
        val settings = GuardianAutomaticCloudSettings(context)
        val connection = NovaBrainConnectionStore(context)
        try {
            settings.disable()
            store.clear()
            store.resetLearning()
            connection.save(configuration)
            run(store, settings, guardianInboxClock(context))
        } finally {
            settings.disable()
            connection.clear()
            store.resetLearning()
            store.clear()
        }
    }

    @Test fun durableEvidenceAndQueueShareTheOriginalRecordAcrossReopenAndDeletion() = fixture { store, settings, now ->
        assertNull(settings.read())
        settings.enable(configuration.origin)
        assertTrue(settings.read()!!.scopes.isEmpty())
        assertTrue(settings.setScope(configuration.origin, key, true))
        val event = capture(now)
        val capture = store.append(event, 30, now.wall, assess = false, clock = now)
        assertTrue(capture.allDurable)
        val id = capture.addedIds.single()
        assertEquals(sentence, store.revealInboxMessage(id, 30, now))
        val archive = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
        assertFalse(archive.readBytes().toString(Charsets.UTF_8).contains(sentence))
        assertTrue(store.assessCaptured(event, 30, now, capture.addedIds, classifyWithoutExamples = true))
        val reopened = GuardianCaptureStore(context)
        val record = reopened.inboxRows(30, now).single()
        assertEquals(id, record.id)
        assertEquals("MAIN_WHATSAPP", record.captureAccount)
        assertEquals(GuardianAssessment.UNCERTAIN, record.assessment)
        assertEquals(GuardianCloudState.QUEUED, record.cloudState)
        val work = checkNotNull(reopened.nextAutomaticCloud(now.wall))
        assertEquals(id, work.evidenceId)
        assertEquals("[redacted] please stop asking me about this", reopened.claimAutomaticCloud(work, now.wall))
        assertNull(GuardianCaptureStore(context).nextAutomaticCloud(now.wall))
        assertTrue(reopened.automaticCloudMaySend(work))
        assertEquals(1, reopened.deleteInboxMessages(setOf(id), true, now).deleted)
        assertFalse(reopened.automaticCloudMaySend(work))
        reopened.finishAutomaticCloud(work, GuardianCloudState.COMPLETE,
            GuardianCloudAdvisory(CloudLesson("UNCERTAIN", emptyList(), "HIGH", "Synthetic advisory", null), "gpt-5-mini", now.wall))
        assertNull(reopened.cloudAdvisory(id, now))
        assertTrue(reopened.inboxRows(0, now).isEmpty())
    }

    @Test fun archiveRestoreAndRevocationKeepTheOriginalWithoutRequeuing() = fixture { store, settings, now ->
        settings.enable(configuration.origin)
        assertTrue(settings.setScope(configuration.origin, key, true))
        val event = capture(now)
        val id = store.append(event, 7, now.wall, assess = false, clock = now).addedIds.single()
        assertTrue(store.assessCaptured(event, 7, now, setOf(id), classifyWithoutExamples = true))
        val work = checkNotNull(store.nextAutomaticCloud(now.wall))
        store.deferAutomaticCloud(work, now.wall, retry = true)
        assertNull(store.nextAutomaticCloud(now.wall))
        assertEquals(work.requestId, store.nextAutomaticCloud(now.wall + 10_000)!!.requestId)
        settings.disable()
        assertNull(GuardianCaptureStore(context).nextAutomaticCloud(now.wall + 10_000))
        assertNull(store.claimAutomaticCloud(work, now.wall + 10_000))
        val later = now.copy(wall = now.wall + 31 * GuardianRetention89.DAY)
        assertEquals(GuardianInboxSection.HISTORY, store.inboxRows(30, later).single().section)
        assertTrue(store.reviewInboxMessage(id, GuardianInboxAction.RESTORE, 30, later))
        assertNotEquals(GuardianInboxSection.HISTORY, store.inboxRows(30, later).single().section)
        assertEquals(sentence, store.revealInboxMessage(id, 0, later))
        assertTrue(store.saveInboxForTraining(id, GuardianAssessment.LOW_RISK, 0, later))
        assertEquals(1, store.learningCount())
        assertTrue(store.saveInboxForTraining(id, GuardianAssessment.UPSETTING, 0, later))
        val corrected = GuardianCaptureStore(context).inboxRows(0, later).single()
        assertEquals(2, corrected.learningRevisions)
        assertEquals(GuardianAssessment.UNCERTAIN, corrected.originalAssessment)
        assertEquals(GuardianAssessment.UPSETTING, corrected.ownerLabel)
    }
}
