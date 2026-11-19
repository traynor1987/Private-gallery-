package uk.co.traynor.privategallery.core.brain

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import java.io.File

/** Isolated synthetic fixtures only. Never runs against the owner's installed package. */
class CloudTeacher83RuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val connection = NovaBrainConnectionStore(context)
    private val store = CloudTeacherStore(context)
    private val credentials = AiCredentialStore(context)
    private val config = NovaBrainConfiguration("https://synthetic.example", "synthetic-owner-token-".repeat(3))
    private val excerpt = "Synthetic private excerpt must never be duplicated in cloud history."
    private val lesson = CloudLesson("UNCERTAIN", listOf("BOUNDARY_PRESSURE"), "HIGH", "Synthetic model text could echo the private original.", 338)
    @Before fun prepare() {
        check(context.packageName.endsWith(".hiddenprototype"))
        connection.save(config); store.clearHistory(); store.setEnabled(false)
    }
    @After fun cleanup() { store.clearHistory(); store.setEnabled(false); connection.clear(); credentials.clear("cloud83-unrelated") }

    @Test fun defaultsOffAndRecreatedPermissionIsEncryptedWithoutChangingSavedToken() {
        assertFalse(store.enabled())
        assertThrows(IllegalStateException::class.java) { store.prepare(config,excerpt,CloudExampleSource.GUARDIAN) }
        store.setEnabled(true)
        assertTrue(CloudTeacherStore(context).enabled())
        val raw=File(context.noBackupFilesDir,"ai-provider-nova-cloud-teacher-permission.enc").readBytes().decodeToString()
        assertFalse(raw.contains("consentVersion"));assertFalse(raw.contains("enabled"))
        assertEquals(config,connection.read())
        store.setEnabled(false);assertFalse(CloudTeacherStore(context).enabled())
    }
    @Test fun explicitApprovedExampleUsesStableIdentityAndOnlyEncryptedClassificationIsRetained() {
        store.setEnabled(true)
        val plan=store.prepare(config,excerpt,CloudExampleSource.EVIDENCE)
        assertEquals(plan.id,CloudTeacherStore(context).prepare(config,excerpt,CloudExampleSource.EVIDENCE).id)
        assertTrue(store.recordSuccess(plan.id,config,lesson))
        val cached=CloudTeacherStore(context).prepare(config,excerpt,CloudExampleSource.EVIDENCE)
        assertEquals(plan.id,cached.id)
        assertEquals(lesson.assessment,cached.cached?.assessment)
        assertEquals("Model summary not retained for privacy.",cached.cached?.summary)
        assertEquals(CloudExampleSource.EVIDENCE,store.history().single().source)
        for(file in context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith("ai-provider-nova-cloud-") }) {
            val raw=file.readBytes().decodeToString()
            for(secret in listOf(excerpt,lesson.summary,config.token,plan.id,"BOUNDARY_PRESSURE")) assertFalse(file.name,raw.contains(secret))
        }
        assertNotNull(store.history().single().completedAt)
    }
    @Test fun permissionRevocationRejectsLateReplyWithoutResurrectingResults() {
        store.setEnabled(true);val id=store.prepare(config,excerpt,CloudExampleSource.GUARDIAN).id
        store.setEnabled(false)
        assertFalse(store.recordSuccess(id,config,lesson));assertNull(store.readResult(id))
        assertEquals(config,connection.read())
    }
    @Test fun changedOrRemovedConnectionRejectsStaleReply() {
        store.setEnabled(true);val id=store.prepare(config,excerpt,CloudExampleSource.GUARDIAN).id
        connection.save(config.copy(token="another-synthetic-owner-token-".repeat(2)))
        assertFalse(store.recordSuccess(id,config,lesson))
        assertThrows(IllegalStateException::class.java) { store.prepare(config,excerpt,CloudExampleSource.GUARDIAN) }
        connection.clear();assertFalse(store.recordSuccess(id,config,lesson))
    }
    @Test fun deletionRejectsInflightReplyAndPreservesOtherCredentials() {
        val other="synthetic other credential".toByteArray();credentials.save("cloud83-unrelated",other)
        store.setEnabled(true);val id=store.prepare(config,excerpt,CloudExampleSource.GUARDIAN).id
        store.delete(id);assertFalse(store.recordSuccess(id,config,lesson));assertTrue(store.history().isEmpty())
        assertArrayEquals(other,credentials.read("cloud83-unrelated"));assertEquals(config,connection.read())
    }
    @Test fun fullHistoryNeverEvictsAnUncertainPaidIdentity() {
        store.setEnabled(true)
        val ids=(1..CloudTeacherStore.MAX_HISTORY).map { store.prepare(config,"Synthetic approved example $it",CloudExampleSource.GUARDIAN).id }
        assertThrows(IllegalStateException::class.java) { store.prepare(config,"another synthetic",CloudExampleSource.GUARDIAN) }
        assertEquals(ids.toSet(),store.history().map { it.id }.toSet())
        assertEquals(ids.first(),store.prepare(config,"Synthetic approved example 1",CloudExampleSource.GUARDIAN).id)
    }
    @Test fun corruptIndexCanBeClearedWithoutTouchingConnectionOrOtherKeys() {
        store.setEnabled(true);val id=store.prepare(config,excerpt,CloudExampleSource.GUARDIAN).id
        store.recordSuccess(id,config,lesson)
        credentials.save("cloud83-unrelated","untouched".toByteArray())
        File(context.noBackupFilesDir,"ai-provider-nova-cloud-teacher-history.enc").writeText("synthetic corrupt ciphertext")
        assertThrows(Exception::class.java) { store.history() }
        store.clearHistory()
        assertTrue(store.history().isEmpty());assertNull(store.readResult(id))
        assertTrue(store.enabled());assertEquals(config,connection.read())
        assertEquals("untouched",credentials.read("cloud83-unrelated")!!.decodeToString())
        assertTrue(context.noBackupFilesDir.listFiles().orEmpty().none { it.name.startsWith("ai-provider-nova-cloud-lesson-") })
    }
    @Test fun clearedHistoryCannotBeResurrectedByLateResponseAndFreshPaidRequestNeedsNewIdentity() {
        store.setEnabled(true);val id=store.prepare(config,excerpt,CloudExampleSource.GUARDIAN).id
        store.clearHistory();assertFalse(store.recordSuccess(id,config,lesson));assertNull(store.readResult(id))
        assertNotEquals(id,store.prepare(config,excerpt,CloudExampleSource.GUARDIAN).id)
    }
}
