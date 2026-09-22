package uk.co.traynor.privategallery.core.browser.v2

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EncryptedBrowserHistoryStoreTest {
    @Test fun history_is_encrypted_private_and_clearable() {
        val root = createTempDirectory("browser-history").toFile()
        val key = ByteArray(32) { 7 }
        val store = EncryptedBrowserHistoryStore(root, key)
        store.add("Example", "https://example.test/path")
        assertEquals(listOf("Example"), store.list().map { it.title })
        check(!File(root, "browser-history-v2.enc").readText().contains("example.test"))
        store.clear()
        assertEquals(emptyList(), store.list())
        key.fill(0); root.deleteRecursively()
    }

    @Test fun history_rejects_unsafe_schemes() {
        val root = createTempDirectory("browser-history").toFile()
        assertFailsWith<IllegalArgumentException> { EncryptedBrowserHistoryStore(root, ByteArray(32)).add("bad", "intent://unsafe") }
        root.deleteRecursively()
    }
}
