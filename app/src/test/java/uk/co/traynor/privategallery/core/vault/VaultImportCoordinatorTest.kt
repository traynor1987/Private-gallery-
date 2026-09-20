package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultImportCoordinatorTest {
    @Test fun `delegates a private stream only after source metadata is valid`() {
        var opened = false
        val sink = object : VaultImportSink {
            override fun importVerified(source: VaultImportSource): ImportResult {
                source.openStream().use { assertEquals(3, it.readBytes().size) }
                return ImportResult.Imported(VaultItem("id", "image/png", "shot.png", 0, 3, ByteArray(32), ByteArray(12), VaultItemState.COMPLETE))
            }
        }
        val result = VaultImportCoordinator(sink).acquire(VaultImportSource("shot.png", "image/png", { opened = true; ByteArrayInputStream(byteArrayOf(1, 2, 3)) }))
        assertEquals(true, opened)
        assertEquals("id", (result as ImportResult.Imported).item.id)
    }

    @Test fun `source cleanup runs when encrypted import fails`() {
        var cleaned = false
        val failing = object : VaultImportSink {
            override fun importVerified(source: VaultImportSource): ImportResult = throw IllegalStateException("verification failed")
        }
        runCatching {
            VaultImportCoordinator(failing).acquire(VaultImportSource("shot.png", "image/png", { ByteArrayInputStream(byteArrayOf(1)) }, onConsumed = { cleaned = true }))
        }
        assertTrue(cleaned)
    }

    @Test fun `cancelled source never reaches a complete vault import`() {
        var imported = false
        val sink = object : VaultImportSink {
            override fun importVerified(source: VaultImportSource): ImportResult {
                source.openStream().use { it.readBytes() }
                imported = true
                return ImportResult.Imported(VaultItem("id", "image/png", "shot.png", 0, 0, ByteArray(32), ByteArray(12), VaultItemState.COMPLETE))
            }
        }

        val result = runCatching {
            VaultImportCoordinator(sink).acquire(VaultImportSource("shot.png", "image/png", { ByteArrayInputStream(byteArrayOf(1)) }, isCancelled = { true }))
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(!imported)
    }
}
