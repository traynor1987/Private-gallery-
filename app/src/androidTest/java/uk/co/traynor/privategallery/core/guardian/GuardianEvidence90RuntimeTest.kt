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

    @Test fun bundledNotificationHistoryNeverQueuesPastExcerpts() = fixture { store, settings, now ->
        settings.enable(configuration.origin)
        assertTrue(settings.setScope(configuration.origin, key, true))
        val event = capture(now).copy(entries = listOf(
            GuardianCaptureEntry(now.wall - 30_000, "Taylor", "Earlier synthetic notification text"),
            GuardianCaptureEntry(now.wall, "Taylor", sentence, "Taylor"),
        ))
        val ids = store.append(event, 30, now.wall, false, now).addedIds
        assertEquals(2, ids.size)
        assertTrue(store.assessCaptured(event, 30, now, ids, classifyWithoutExamples = true))
        val rows = store.inboxRows(30, now)
        assertTrue(rows.all { it.assessment == GuardianAssessment.UNCERTAIN })
        assertNull(rows.single { it.time == now.wall - 30_000 }.cloudState)
        val queued = checkNotNull(store.nextAutomaticCloud(now.wall))
        assertEquals(rows.single { it.time == now.wall }.id, queued.evidenceId)
        val stale = capture(now).copy(entries = listOf(
            GuardianCaptureEntry(now.wall - GuardianAutomaticCloudPolicy.WINDOW_MS, "Taylor", "An old synthetic notification"),
        ))
        val retained = store.append(stale, 30, now.wall, false, now).addedIds.single()
        assertFalse(store.assessCaptured(stale, 30, now, setOf(retained), classifyWithoutExamples = true))
        assertEquals("An old synthetic notification", store.revealInboxMessage(retained, 30, now))
    }

    @Test fun greetingsRemainVisibleAndEncryptedEvenWithCloudConsentAndUncertainHold() = fixture { store, settings, now ->
        settings.enable(configuration.origin)
        assertTrue(settings.setScope(configuration.origin, key, true))
        val message = capture(now).copy(entries = listOf(GuardianCaptureEntry(now.wall, "Taylor", "Hi")),
            holdUncertain = true)
        val result = store.append(message, 30, now.wall, assess = false, clock = now)
        assertTrue(result.allDurable)
        assertFalse(store.assessCaptured(message, 30, now, result.addedIds, classifyWithoutExamples = true))
        val reopened = GuardianCaptureStore(context)
        val row = reopened.notificationRows(message, 30, now).single()
        assertEquals(GuardianAssessment.LOW_RISK, row.assessment)
        assertEquals(GuardianInboxSection.READY, row.section)
        assertEquals("Hi", reopened.revealInboxMessage(row.id, 30, now))
        assertNull(row.cloudState)
        assertNull(reopened.nextAutomaticCloud(now.wall))
        assertEquals(0, reopened.append(message, 30, now.wall, clock = now).added)
        assertTrue(reopened.saveInboxForTraining(row.id, GuardianAssessment.UPSETTING, 30, now))
        val later = now.copy(wall = now.wall + 1_000, elapsed = now.elapsed + 1_000)
        val repeat = message.copy(observedAt = later.wall, entries = listOf(message.entries.single().copy(time = later.wall)))
        val afterReopen = GuardianCaptureStore(context)
        val saved = afterReopen.append(repeat, 30, later.wall, false, later)
        assertTrue(saved.allDurable)
        assertFalse(afterReopen.assessCaptured(repeat, 30, later, saved.addedIds, classifyWithoutExamples = true))
        val corrected = afterReopen.notificationRows(repeat, 30, later).single()
        assertEquals(GuardianAssessment.UPSETTING, corrected.assessment)
        assertEquals(GuardianAssessmentReason.OWNER_EXAMPLES, corrected.assessmentReason)
        assertEquals(GuardianInboxSection.COOLING, corrected.section)
        assertNull(afterReopen.revealInboxMessage(corrected.id, 30, later))
        assertEquals(2, afterReopen.inboxRows(0, later).size)
        val next = later.copy(wall = later.wall + 1_000, elapsed = later.elapsed + 1_000)
        val ambiguous = message.copy(observedAt = next.wall,
            entries = listOf(GuardianCaptureEntry(next.wall, "Taylor", "Come here after work")))
        val held = afterReopen.append(ambiguous, 30, next.wall, false, next)
        assertTrue(held.allDurable)
        assertTrue(afterReopen.assessCaptured(ambiguous, 30, next, held.addedIds, classifyWithoutExamples = true))
        val uncertain = afterReopen.notificationRows(ambiguous, 30, next).single()
        assertEquals(GuardianAssessment.UNCERTAIN, uncertain.assessment)
        assertEquals(GuardianInboxSection.COOLING, uncertain.section)
        assertEquals(GuardianCloudState.QUEUED, uncertain.cloudState)
        assertEquals(GuardianPresentationAction.KEEP_ORIGINAL,
            guardianPresentationAction(GuardianPresentationMode.SMART, false, false, true, false, true))
        assertEquals(GuardianPresentationAction.REPLACE,
            guardianPresentationAction(GuardianPresentationMode.SMART, true, false, true, false, true))
    }

    @Test fun captureUsesLocalRulesIfSeparateLearningArchiveCannotBeRead() = fixture { store, settings, now ->
        settings.enable(configuration.origin)
        assertTrue(settings.setScope(configuration.origin, key, true))
        val learning = File(context.noBackupFilesDir, "guardian-learning-v1.enc")
        val unreadable = byteArrayOf(1, 2, 3)
        learning.writeBytes(unreadable)

        val event = capture(now).copy(entries = listOf(
            GuardianCaptureEntry(now.wall - 3, "fixture", "Hi"),
            GuardianCaptureEntry(now.wall - 2, "fixture", "Good morning 😊"),
            GuardianCaptureEntry(now.wall - 1, "fixture", "How are you?"),
            GuardianCaptureEntry(now.wall, "fixture", "I will hit you."),
        ))
        val result = store.append(event, 0, now.wall, assess = false, clock = now)
        assertTrue(result.allDurable)
        assertEquals(4, result.added)
        assertTrue(store.captureDiagnostics(now).decision!!.contains("Awaiting local assessment"))
        assertFalse(store.assessCaptured(event, 0, now, result.addedIds, classifyWithoutExamples = true))

        val later = GuardianInboxClock(now.wall + 40 * GuardianRetention89.DAY, 10_000, now.boot + 1)
        val reopened = GuardianCaptureStore(context)
        val rows = reopened.inboxRows(0, now).associateBy { it.time }
        for (entry in event.entries.dropLast(1)) {
            val row = rows.getValue(entry.time)
            assertEquals(GuardianInboxSection.READY, row.section)
            assertEquals(entry.text, reopened.revealInboxMessage(row.id, 0, later))
        }
        val hostile = rows.getValue(event.entries.last().time)
        assertEquals(GuardianAssessment.UPSETTING, hostile.assessment)
        assertEquals(GuardianInboxSection.COOLING, hostile.section)
        assertNull(reopened.revealInboxMessage(hostile.id, 0, now))
        assertEquals(GuardianInboxSection.PROTECTED, reopened.inboxRows(0, later).single { it.id == hostile.id }.section)
        val uncertain = capture(later)
        val uncertainId = store.append(uncertain, 0, later.wall, assess = false, clock = later).addedIds.single()
        assertFalse(store.assessCaptured(uncertain, 0, later, setOf(uncertainId), classifyWithoutExamples = true))
        assertEquals(GuardianAssessment.UNCERTAIN, reopened.notificationRows(uncertain, 0, later).single().assessment)
        assertNull(reopened.nextAutomaticCloud(later.wall))
        assertEquals(5, reopened.inboxRows(0, later).size)
        assertArrayEquals(unreadable, learning.readBytes())
    }

}
