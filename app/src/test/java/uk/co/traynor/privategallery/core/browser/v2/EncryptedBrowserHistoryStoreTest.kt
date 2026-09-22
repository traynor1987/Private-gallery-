package uk.co.traynor.privategallery.core.browser.v2

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBrowserHistoryStoreTest {
    @Test fun history_is_encrypted_private_and_clearable() {
        val root = createTempDirectory("browser-history").toFile()
        val key = ByteArray(32) { 7 }
        val store = EncryptedBrowserHistoryStore(root, key)
        store.add("Example", "https://example.test/path")
        assertEquals(listOf("Example"), store.list().map { it.title })
        check(!File(root, "browser-history-v2.enc").readText().contains("example.test"))
        store.clear()
        assertTrue(store.list().isEmpty())
        key.fill(0); root.deleteRecursively()
    }

    @Test fun history_rejects_unsafe_schemes() {
        val root = createTempDirectory("browser-history").toFile()
        try {
            EncryptedBrowserHistoryStore(root, ByteArray(32)).add("bad", "intent://unsafe")
            throw AssertionError("Expected unsafe scheme to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        root.deleteRecursively()
    }
}
