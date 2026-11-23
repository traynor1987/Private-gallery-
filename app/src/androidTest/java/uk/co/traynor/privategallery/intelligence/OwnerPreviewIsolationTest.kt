package uk.co.traynor.privategallery.intelligence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore

class OwnerPreviewIsolationTest {
    @Test fun defaultOffWithSeparateEncryptedCredentialsAndRevocation() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.noBackupFilesDir, "preview-isolation-${UUID.randomUUID()}").apply { check(mkdir()) }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir() = directory
        }
        val production = NovaBrainConnectionStore(context)
        val preview = OwnerPreviewStore(context)
        val original = NovaBrainConfiguration("https://primary.example", "b".repeat(64))
        val temporaryToken = "a".repeat(64)
        try {
            production.save(original)
            val productionFile = File(directory, "ai-provider-nova-brain-connection.enc")
            val originalBytes = productionFile.readBytes()
            assertFalse(preview.isEnabled())
            assertNull(preview.read())
            assertThrows(IllegalStateException::class.java) { preview.enable(original.token) }

            preview.enable(temporaryToken)
            val first = checkNotNull(OwnerPreviewStore(context).read())
            assertTrue(preview.isCurrent(first))
            assertEquals(OWNER_PREVIEW_ORIGIN, first.configuration().origin)
            val previewFile = File(directory, "ai-provider-nova-brain-owner-preview.enc")
            assertTrue(previewFile.isFile)
            assertFalse(previewFile.readBytes().decodeToString().contains(temporaryToken))
            assertArrayEquals(originalBytes, productionFile.readBytes())
            assertEquals(original, production.read())

            preview.disable()
            assertFalse(preview.isCurrent(first))
            assertFalse(previewFile.exists())
            preview.enable(temporaryToken)
            assertFalse(preview.isCurrent(first))
            preview.disable()
            assertFalse(preview.isEnabled())
            assertEquals(original, production.read())
            assertArrayEquals(originalBytes, productionFile.readBytes())
        } finally { directory.deleteRecursively() }
    }
}
