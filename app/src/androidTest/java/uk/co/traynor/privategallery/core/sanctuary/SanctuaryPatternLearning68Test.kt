package uk.co.traynor.privategallery.core.sanctuary

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryPatternLearning68.Pattern

class SanctuaryPatternLearning68Test {
    @Test fun savesOnlyRecurringConfirmedCategoriesInASeparateEncryptedSpaceProfile() = fixture { context, primary, hidden, _ ->
        val first = record('a')
        val duplicate = record('a')
        val second = record('b')
        insert(primary, first, duplicate, second, record('c'))
        val requests = setOf(Pattern.IMMEDIATE_REPLY_REQUEST)

        SanctuaryPatternLearning68(context, primary).use { learner ->
            val duplicateOnly = learner.prepare(mapOf(first.id() to requests, duplicate.id() to requests))
            assertTrue(duplicateOnly.patterns.isEmpty())
            assertThrows(IllegalStateException::class.java) { learner.save(duplicateOnly) }
            val approved = learner.prepare(mapOf(first.id() to (requests + Pattern.INSULT), second.id() to requests))
            assertEquals(requests, approved.patterns)
            assertTrue(learner.currentProfile().isEmpty())
            assertEquals(requests, learner.save(approved))
            assertThrows(IllegalStateException::class.java) { learner.save(approved) }
            val ciphertext = File(context.noBackupFilesDir, "sanctuary-patterns-v1/primary.enc")
            assertEquals(30L, ciphertext.length()) // 12-byte IV, 2-byte profile, 16-byte tag.
        }

        SanctuaryPatternLearning68(context, primary).use { assertEquals(requests, it.currentProfile()) }
        SanctuaryPatternLearning68(context, hidden).use { learner ->
            assertTrue(learner.currentProfile().isEmpty())
            assertThrows(IllegalArgumentException::class.java) { learner.prepare(mapOf(first.id() to requests)) }
        }
    }

    @Test fun consentIsSingleUseSpaceBoundAndRetiredOnLock() = fixture { context, primary, hidden, hiddenSession ->
        val first = record('a')
        val second = record('b')
        insert(hidden, first, second)
        val followUp = setOf(Pattern.REPEATED_FOLLOW_UP)
        val selection = mapOf(first.id() to followUp, second.id() to followUp)
        SanctuaryPatternLearning68(context, hidden).use { learner ->
            val discarded = learner.prepare(selection)
            learner.discard(discarded)
            assertThrows(IllegalStateException::class.java) { learner.save(discarded) }
            val superseded = learner.prepare(selection)
            val approved = learner.prepare(selection)
            assertThrows(IllegalStateException::class.java) { learner.save(superseded) }
            SanctuaryPatternLearning68(context, primary).use { other ->
                assertThrows(IllegalStateException::class.java) { other.save(approved) }
            }
            hiddenSession.lock()
            assertThrows(IllegalStateException::class.java) { learner.save(approved) }
            assertThrows(IllegalStateException::class.java) { learner.currentProfile() }
            assertFalse(File(context.noBackupFilesDir, "sanctuary-patterns-v1/hidden.enc").exists())
        }
        assertTrue(hiddenSession.complete(hiddenSession.beginAuthentication(), ByteArray(32) { 7 }))
        EvidenceAccess.hidden(context, hiddenSession) { true }.use { fresh ->
            SanctuaryPatternLearning68(context, fresh).use { learner ->
                assertTrue(learner.currentProfile().isEmpty())
                val approved = learner.prepare(selection)
                learner.close()
                assertThrows(IllegalStateException::class.java) { learner.save(approved) }
            }
        }
    }

    private fun insert(access: EvidenceAccess, vararg evidence: EvidenceCatalog.Evidence) {
        evidence.forEach { record -> access.store.update { it.upsertEvidence(record, 100L, "TEST_FIXTURE") } }
    }

    private fun record(digest: Char) = EvidenceCatalog.Evidence(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Private screenshot", "image/png",
        digest.toString().repeat(64), 100L, 100L,
        EvidenceCatalog.Metadata("Original screenshot", 0L, 0L, 0L, 0L, 1080, 1920, emptyMap(), false),
        "Private description", "Private notes", emptyList(), listOf("Private participant"), emptyList(), emptyList(),
    )

    private fun fixture(run: (Context, EvidenceAccess, EvidenceAccess, HiddenSession) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "sanctuary-learning-" + UUID.randomUUID())
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        val key = ByteArray(32) { 4 }
        val hiddenSession = HiddenSession { 100L }
        assertTrue(hiddenSession.complete(hiddenSession.beginAuthentication(), ByteArray(32) { 7 }))
        try {
            EvidenceAccess.primary(context, key, { key }, { true }, { key.copyOf() }, Any()).use { primary ->
                EvidenceAccess.hidden(context, hiddenSession) { true }.use { hidden -> run(context, primary, hidden, hiddenSession) }
            }
        } finally {
            hiddenSession.lock()
            key.fill(0)
            root.deleteRecursively()
        }
    }
}
