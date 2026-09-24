package uk.co.traynor.privategallery.core.media

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.ui.GatedMediaDataSource
import androidx.media3.datasource.*
import android.net.Uri
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaBoundaryTest {
    @Test fun repositoryRejectsForgedUnrestrictedRestoreBeforeMediaStoreInsertion() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "synthetic-egress-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val key = ByteArray(32) { 3 }
        try {
            val repository = AndroidVaultRepository(context, key)
            val bytes = byteArrayOf(1,2,3)
            val restricted = (repository.importVerified(VaultImportSource("test.png", "image/png", { bytes.inputStream() }, origin = MediaOrigin.REMOTE_AI_EDIT, vaultOnly = true)) as ImportResult.Imported).item
            assertArrayEquals(bytes, repository.readForViewing(restricted))
            assertThrows(java.io.IOException::class.java) { repository.readForEditingPreview(restricted) { true } }
            VaultEgress.entries.forEach { action -> assertThrows(SecurityException::class.java) { repository.requireEgress(restricted.id, action) } }
            assertThrows(SecurityException::class.java) { repository.restore(restricted.copy(vaultOnly = false, origin = MediaOrigin.IMPORTED)) }
            assertThrows(SecurityException::class.java) { repository.restoreAndRemove(restricted.copy(vaultOnly = false)) }
            assertEquals(1, repository.items().size)
            val collection = repository.createCollection("Synthetic")
            repository.addItemsToCollection(collection.id, listOf(restricted.id))
            assertEquals(restricted.id, repository.itemsInCollection(collection.id).single().id)
            repository.applyImageCrop(restricted.id, NormalizedCrop(.1f,.1f,.9f,.9f))
            assertNotNull(repository.imageEdit(restricted.id))
            val descendant = repository.importEditedCopy(restricted.id, bytes, remoteAi = false, keepAiInVault = false) { false }
            assertTrue(descendant.vaultOnly)
            assertEquals(MediaOrigin.REMOTE_AI_EDIT, descendant.origin)
            assertThrows(SecurityException::class.java) { repository.restore(descendant) }
            repository.deleteFromVault(descendant)
            repository.deleteFromVault(restricted)
            assertTrue(repository.items().isEmpty())
        } finally { key.fill(0); root.deleteRecursively() }
    }

    @Test fun networkGateStopsManifestAndSegmentReadsAfterVpnLoss() {
        var allowed = false; var opened = 0; var reads = 0; var closed = 0
        val delegate = object : DataSource {
            override fun addTransferListener(listener: TransferListener) = Unit
            override fun open(spec: DataSpec): Long { opened++; return 10 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { reads++; return 1 }
            override fun getUri(): Uri? = null
            override fun close() { closed++ }
        }
        val gated = GatedMediaDataSource(delegate) { allowed }
        val spec = DataSpec.Builder().setUri("https://example.invalid/video.m3u8").build()
        assertThrows(java.io.IOException::class.java) { gated.open(spec) }
        assertEquals(0, opened)
        allowed = true; gated.open(spec); gated.read(ByteArray(1), 0, 1)
        allowed = false
        assertThrows(java.io.IOException::class.java) { gated.read(ByteArray(1), 0, 1) }
        assertEquals(1, reads); assertEquals(1, closed)
    }
}
