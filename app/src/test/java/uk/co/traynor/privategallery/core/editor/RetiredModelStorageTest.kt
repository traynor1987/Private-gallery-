package uk.co.traynor.privategallery.core.editor

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RetiredModelStorageTest {
    @Test fun onlyKnownModelFilesAreCountedAndRemoved() {
        val appRoot = Files.createTempDirectory("private-gallery-cleanup").toFile()
        try {
            val models = File(appRoot, "ai-models").apply { mkdirs() }
            val known = File(models, "sd15-fp16-v1.safetensors").apply { writeBytes(ByteArray(13)) }
            val partial = File(models, "sdxl-base-1-v1.part").apply { writeBytes(ByteArray(7)) }
            val unrelated = File(models, "my-media.jpg").apply { writeBytes(ByteArray(5)) }
            val vault = File(appRoot, "vault").apply { mkdirs() }
            val media = File(vault, "sd15-fp16-v1.safetensors").apply { writeBytes(ByteArray(9)) }
            val cleanup = RetiredModelStorage(appRoot)
            assertEquals(20L, cleanup.reclaimableBytes())
            assertEquals(20L, cleanup.remove())
            assertFalse(known.exists()); assertFalse(partial.exists())
            assertTrue(unrelated.exists()); assertTrue(media.exists())
            assertEquals(0L, cleanup.reclaimableBytes())
        } finally { appRoot.deleteRecursively() }
    }
}
