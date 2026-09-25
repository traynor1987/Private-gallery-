package uk.co.traynor.privategallery.core.editor.local

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.*
import java.io.File

class LocalAiVaultCopyTest {
    @Test fun localAiSaveCopyIsEncryptedImmutableAndRestrictedIncludingLaterEdits() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "local-ai-vault-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        val key = ByteArray(32) { it.toByte() }
        val original = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10, 1, 2, 3)
        val edited = original + byteArrayOf(4, 5)
        try {
            val repository = AndroidVaultRepository(context, key)
            val parent = (repository.importVerified(VaultImportSource("fixture.png", "image/png", { original.inputStream() }, createDistinctCopy = true)) as ImportResult.Imported).item
            val copy = repository.importAiEditedCopy(parent.id, edited, AiEditProvenance(AiProcessing.ON_DEVICE, "local-lightweight", "sd15-fp16-v1"), true) { false }
            assertNotEquals(parent.id, copy.id)
            assertEquals(MediaOrigin.LOCAL_AI_EDIT, copy.origin)
            assertTrue(copy.vaultOnly)
            assertTrue(copy.sourceUri!!.contains("ai:local-lightweight"))
            assertArrayEquals(original, repository.readForViewing(parent))
            assertArrayEquals(edited, repository.readForViewing(copy))
            for (file in File(root, "vault").walkTopDown().filter { it.isFile }) {
                assertFalse(file.readBytes().contentEquals(edited))
                assertFalse(file.readBytes().contentEquals(original))
            }
            val later = repository.importEditedCopy(copy.id, edited + 6.toByte(), false, false) { false }
            assertTrue(later.vaultOnly)
            VaultEgress.entries.forEach { action -> assertTrue(runCatching { VaultEgressPolicy.requireAllowed(later, action) }.exceptionOrNull() is SecurityException) }
            val before = repository.items().size
            assertTrue(runCatching { repository.importAiEditedCopy(parent.id, edited, AiEditProvenance(AiProcessing.ON_DEVICE, "local-advanced", "sdxl-base-1-v1"), true) { true } }.isFailure)
            assertEquals(before, repository.items().size)
        } finally { original.fill(0); edited.fill(0); key.fill(0); root.deleteRecursively() }
    }
}
