package uk.co.traynor.privategallery.core.guardian

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GuardianCaptureStore77Test {
    @Test fun encryptedExamplesChangeFutureEstimatesAndDeletedRowsStayHidden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = guardianInboxClock(context)
        val store = GuardianCaptureStore(context)
        val text = "you have to come here right now"
        store.clear()
        store.resetLearning()
        try {
            val event = GuardianCaptureEvent(0, "test", now.wall, listOf(GuardianCaptureEntry(now.wall - 2, "sender", text)))
            assertTrue(store.teach(text, GuardianAssessment.UPSETTING))
            store.append(event, 7, now.wall)
            val reopened = GuardianCaptureStore(context)
            val first = reopened.inboxRows(7, now).single()
            assertEquals(GuardianAssessment.UPSETTING, first.assessment)
            assertTrue(reopened.teach(text, GuardianAssessment.LOW_RISK))
            assertEquals(1, reopened.learningCount())
            assertNull(reopened.assessInboxMessage(first.id, 7, now))
            val expired = now.copy(wall = now.wall + GUARDIAN_HOLD_MILLIS, elapsed = now.elapsed + GUARDIAN_HOLD_MILLIS)
            assertEquals(GuardianAssessment.LOW_RISK, reopened.assessInboxMessage(first.id, 7, expired))
            assertTrue(reopened.reviewInboxMessage(first.id, GuardianInboxAction.DELETE, 7, expired))
            assertNull(reopened.revealInboxMessage(first.id, 7, expired))
            assertEquals(0, reopened.append(event, 7, now.wall).added)
            assertTrue(reopened.inboxRows(7, now).isEmpty())
            val encrypted = File(context.noBackupFilesDir, "guardian-learning-v1.enc").readBytes()
            assertFalse(encrypted.toString(Charsets.UTF_8).contains(text))
            assertTrue(reopened.forgetExample(text))
            assertEquals(0, reopened.learningCount())
        } finally {
            store.clear()
            store.resetLearning()
        }
    }

    @Test fun verifiedPersistenceRequiresEveryEntryAndNewContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = guardianInboxClock(context)
        val store = GuardianCaptureStore(context)
        store.clear()
        store.resetLearning()
        try {
            val text = "you need to arrive here right now"
            val first = GuardianCaptureEntry(now.wall - 2, "private-key", text, "Synthetic sender")
            val event = GuardianCaptureEvent(0, "test", now.wall, listOf(first))
            assertTrue(store.teach(text, GuardianAssessment.UPSETTING))
            assertTrue(store.append(event, 7, now.wall, assess = false).allDurable)
            val reopened = GuardianCaptureStore(context)
            assertEquals("Synthetic sender", reopened.inboxRows(7, now).single().senderName)
            assertNull(reopened.inboxRows(7, now).single().assessment)
            assertFalse(reopened.append(event, 7, now.wall, assess = false).allDurable)
            val next = first.copy(time = now.wall - 1, text = "a new private message")
            val partial = event.copy(entries = listOf(first, next, next.copy(sender = "")))
            assertFalse(reopened.append(partial, 7, now.wall, assess = false).allDurable)
            reopened.assessCaptured(event, 7, now)
            assertEquals(GuardianAssessment.UPSETTING,
                reopened.inboxRows(7, now).single { it.time == first.time }.assessment)
            val bytes = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME).readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).contains("Synthetic sender"))
        } finally {
            store.clear()
            store.resetLearning()
        }
    }

    @Test fun diagnosticRowsContainOnlySectionAndSyntheticLayoutIdentity() {
        val record = GuardianInboxRow("encrypted-record-id", 1L, GuardianInboxSection.COOLING, 12_345L,
            GuardianAssessment.UPSETTING, "Private sender", arrivedAt = 2L)
        val diagnostic = guardianDiagnosticRows(listOf(record)).single()
        assertEquals(GuardianInboxRow("preview-0", 0L, GuardianInboxSection.COOLING, 0L), diagnostic)
    }

    @Test fun mixedConversationNeverSearchesOrExportsIndependentlyHeldMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = guardianInboxClock(context)
        val store = GuardianCaptureStore(context)
        val upsetting = "you have to come here right now"
        val harmless = "I will be there tomorrow afternoon"
        val conversation = "shortcut:synthetic"
        val second = now.copy(wall = now.wall + 60_000, elapsed = now.elapsed + 60_000)
        store.clear()
        store.resetLearning()
        try {
            assertTrue(store.teach(upsetting, GuardianAssessment.UPSETTING))
            assertTrue(store.teach(harmless, GuardianAssessment.LOW_RISK))
            store.append(GuardianCaptureEvent(0, conversation, now.wall, listOf(
                GuardianCaptureEntry(now.wall - 10, "sender", harmless),
                GuardianCaptureEntry(now.wall - 5, "sender", upsetting))), 7, now.wall, clock = now)
            store.append(GuardianCaptureEvent(0, conversation, second.wall, listOf(
                GuardianCaptureEntry(second.wall, "sender", upsetting))), 7, second.wall, clock = second)
            val reopened = GuardianCaptureStore(context)
            val rows = reopened.inboxRows(7, second).sortedBy { it.time }
            assertEquals(1, rows.map { it.conversation }.distinct().size)
            assertEquals(listOf(GuardianInboxSection.READY, GuardianInboxSection.COOLING, GuardianInboxSection.COOLING),
                rows.map { it.section })
            assertEquals(harmless, reopened.revealInboxMessage(rows.first().id, 7, second))
            assertTrue(reopened.searchInbox("right now", 7, second).isEmpty())
            assertNull(reopened.eligibleEvidenceMessage(rows[1].id, 7, second))
            val firstExpiry = now.copy(wall = now.wall + GUARDIAN_HOLD_MILLIS, elapsed = now.elapsed + GUARDIAN_HOLD_MILLIS)
            assertEquals(upsetting, reopened.revealInboxMessage(rows[1].id, 7, firstExpiry))
            assertNull(reopened.revealInboxMessage(rows[2].id, 7, firstExpiry))
            assertEquals(setOf(rows[1].id), reopened.searchInbox("right now", 7, firstExpiry))
            assertNull(reopened.eligibleEvidenceMessage(rows[2].id, 7, firstExpiry))
        } finally {
            store.clear()
            store.resetLearning()
        }
    }

    @Test fun voluntaryReviewAnalysesPendingContentBeforeAnyPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = guardianInboxClock(context)
        val store = GuardianCaptureStore(context)
        val text = "you have to come here right now"
        store.clear()
        store.resetLearning()
        try {
            val event = GuardianCaptureEvent(0, "shortcut:example", now.wall,
                listOf(GuardianCaptureEntry(now.wall, "sender", text)))
            store.append(event, 7, now.wall, assess = false)
            val id = store.inboxRows(7, now).single().id
            assertTrue(store.searchInbox("right now", 7, now).isEmpty())
            assertTrue(store.teach(text, GuardianAssessment.UPSETTING))
            assertNull(store.prepareInboxReview(id, 7, now))
            assertEquals(GuardianInboxSection.COOLING, store.inboxRows(7, now).single().section)
            assertNull(store.eligibleEvidenceMessage(id, 7, now))
        } finally {
            store.clear()
            store.resetLearning()
        }
    }

    @Test fun contactConsentIsEncryptedAppAndProfileScopedAndRemovable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = GuardianCaptureStore(context)
        val key = GuardianConversationKey(GUARDIAN_WHATSAPP, 0, "shortcut:synthetic")
        val contact = GuardianContact(key, "Synthetic private contact", holdUncertain = true)
        try {
            store.saveContact(key, contact)
            val reopened = GuardianCaptureStore(context)
            assertEquals(contact, reopened.monitoredContacts().single { it.key == key })
            assertFalse(reopened.monitoredContacts().any { it.key == key.copy(profile = 9) })
            assertFalse(reopened.monitoredContacts().any { it.key == key.copy(app = "another.app") })
            val bytes = File(context.noBackupFilesDir, "guardian-contacts-v1.enc").readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).contains(contact.name))
        } finally {
            store.saveContact(key, null)
        }
        assertFalse(GuardianCaptureStore(context).monitoredContacts().any { it.key == key })
    }

    @Test fun inboxCountsAndHoldsSurviveReopeningWithoutEarlyReveal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = guardianInboxClock(context)
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            val entry = GuardianCaptureEntry(now.wall - 1, "synthetic sender", "synthetic held message")
            val event = GuardianCaptureEvent(0, "conversation", now.wall, listOf(entry))
            store.append(event, 7, now.wall)
            assertEquals(1, store.inboxCounts(7, now).pending)
            val id = store.inboxRows(7, now).single().id
            assertTrue(store.reviewInboxMessage(id, GuardianInboxAction.READY, 7, now))
            assertEquals(1, store.inboxCounts(7, now).ready)
            assertTrue(store.reviewInboxMessage(id, GuardianInboxAction.HOLD, 7, now))
            val reopened = GuardianCaptureStore(context)
            assertEquals(1, reopened.inboxCounts(7, now).cooling)
            assertEquals(0, reopened.append(event, 7, now.wall).added)
            assertNull(reopened.revealInboxMessage(id, 7, now))
            assertFalse(reopened.reviewInboxMessage(id, GuardianInboxAction.READY, 7, now))
            val after = now.copy(wall = now.wall + GUARDIAN_HOLD_MILLIS, elapsed = now.elapsed + GUARDIAN_HOLD_MILLIS)
            assertEquals(1, reopened.inboxCounts(7, after).protected)
            assertEquals(entry.text, reopened.revealInboxMessage(id, 7, after))
            assertTrue(reopened.reviewInboxMessage(id, GuardianInboxAction.ARCHIVE, 7, after))
            assertEquals(1, reopened.inboxCounts(7, after).history)
        } finally {
            store.clear()
        }
    }

    @Test fun encryptedArchiveDeduplicatesUpdatesAcrossStoreReopens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val first = GuardianCaptureEntry(now - 2, "synthetic sender", "sensitive synthetic one")
        val second = GuardianCaptureEntry(now - 1, "synthetic sender", "sensitive synthetic two")
        val main = GuardianCaptureEvent(0, "conversation", now, listOf(first))
        val store = GuardianCaptureStore(context)
        store.clear()
        try {
            assertEquals(1, store.append(main, days = 7, now = now).added)
            val reopened = GuardianCaptureStore(context)
            assertEquals(0, reopened.append(main, days = 7, now = now).added)
            assertEquals(1, reopened.append(main.copy(entries = listOf(first, second)), days = 7, now = now).added)
            assertEquals(2, reopened.summary(days = 7, now = now))
            val file = File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
            val ciphertext = file.readBytes()
            assertFalse(ciphertext.toString(Charsets.UTF_8).contains(first.text))
            assertFalse(ciphertext.toString(Charsets.UTF_8).contains(second.text))
            assertEquals(0, reopened.summary(days = 1, now = now + 2 * 86_400_000L))
            assertEquals(0, reopened.append(main, days = 1, now = now + 2 * 86_400_000L).added)
            file.writeBytes(ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            assertTrue(runCatching { reopened.summary(days = 7, now = now) }.isFailure)
        } finally {
            store.clear()
        }
        assertEquals(0, GuardianCaptureStore(context).summary(days = 7, now = now))
        assertFalse(File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME).exists())
    }
    @Test fun conversationLabelsPersistLearnAndClearWithoutBypassingHolds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = guardianInboxClock(context)
        val store = GuardianCaptureStore(context)
        val phrase = "the appointment starts early tomorrow morning"
        store.clear()
        store.resetLearning()
        try {
            val event = GuardianCaptureEvent(0, "shortcut:label-fixture", now.wall,
                listOf(GuardianCaptureEntry(now.wall - 1, "sender", phrase)))
            store.append(event, 7, now.wall, assess = false)
            val pending = store.inboxRows(7, now).single()
            assertFalse(store.labelConversationMessage(pending.id, GuardianAssessment.LOW_RISK, 7, now))
            assertNotNull(store.prepareInboxReview(pending.id, 7, now))
            assertTrue(store.labelConversationMessage(pending.id, GuardianAssessment.LOW_RISK, 7, now))
            val reopened = GuardianCaptureStore(context)
            assertEquals(GuardianAssessment.LOW_RISK, reopened.inboxRows(7, now).single().ownerLabel)
            assertEquals(1, reopened.learningCount())
            val next = event.copy(entries = listOf(GuardianCaptureEntry(now.wall, "sender", phrase)))
            reopened.append(next, 7, now.wall, clock = now)
            assertEquals(GuardianAssessment.LOW_RISK, reopened.inboxRows(7, now).first().assessment)
            assertTrue(reopened.reviewInboxMessage(pending.id, GuardianInboxAction.HOLD, 7, now))
            assertNull(reopened.prepareInboxReview(pending.id, 7, now))
            assertFalse(reopened.labelConversationMessage(pending.id, GuardianAssessment.UPSETTING, 7, now))
            assertFalse(reopened.labelConversationMessage(pending.id, null, 7, now))
            val later = now.copy(wall = now.wall + GUARDIAN_HOLD_MILLIS, elapsed = now.elapsed + GUARDIAN_HOLD_MILLIS)
            assertTrue(reopened.labelConversationMessage(pending.id, null, 7, later))
            assertNull(GuardianCaptureStore(context).inboxRows(7, later).single { it.id == pending.id }.ownerLabel)
            assertEquals(0, reopened.learningCount())
        } finally {
            store.clear()
            store.resetLearning()
        }
    }
    @Test fun reassessingLowRiskAsUncertainEnforcesTheContactsHoldPolicy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = GuardianCaptureStore(context)
        val now = guardianInboxClock(context)
        val text = "I will arrive at the shop tomorrow"
        store.clear()
        store.resetLearning()
        try {
            assertTrue(store.teach(text, GuardianAssessment.LOW_RISK))
            store.append(GuardianCaptureEvent(0, "shortcut:hold-regression", now.wall,
                listOf(GuardianCaptureEntry(now.wall, "sender", text)), holdUncertain = true), 7, now.wall, clock = now)
            val id = store.inboxRows(7, now).single().id
            assertNotNull(store.prepareInboxReview(id, 7, now))
            store.resetLearning()
            assertEquals(GuardianAssessment.UNCERTAIN, store.assessInboxMessage(id, 7, now))
            assertEquals(GuardianInboxSection.COOLING, store.inboxRows(7, now).single().section)
            assertNull(store.prepareInboxReview(id, 7, now))
            assertNull(store.revealInboxMessage(id, 7, now))
            assertTrue(store.searchInbox("shop", 7, now).isEmpty())
        } finally {
            store.clear()
            store.resetLearning()
        }
    }
}
