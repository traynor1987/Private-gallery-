package uk.co.traynor.privategallery.core.browser.v2

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EncryptedBrowserSessionStoreTest {
    @Test fun session_round_trip_preserves_only_metadata() {
        val root = createTempDirectory("browser-session").toFile()
        val key = ByteArray(32) { 3 }
        val store = EncryptedBrowserSessionStore(root, key)
        store.save(BrowserSessionSnapshot(listOf(BrowserTab(id = "one", url = "https://example.test", title = "Example", desktopSite = true, webViewHandle = "never")), "one"))

        val restored = checkNotNull(store.load())
        assertEquals("one", restored.selectedTabId)
        assertEquals("https://example.test", restored.tabs.single().url)
        assertFalse(restored.tabs.single().loading)
        assertEquals(null, restored.tabs.single().webViewHandle)
        check(!File(root, "browser-session-v2.enc").readText().contains("example.test"))
        key.fill(0); root.deleteRecursively()
    }
}
