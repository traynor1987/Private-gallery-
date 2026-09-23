package uk.co.traynor.privategallery.core.browser.v2

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun concurrent_metadata_saves_never_share_or_lose_the_staging_file() {
        val root = createTempDirectory("browser-session-concurrent").toFile()
        val key = ByteArray(32) { 7 }
        val writers = 24
        val start = CountDownLatch(1)
        val done = CountDownLatch(writers)
        val failures = mutableListOf<Throwable>()
        val executor = Executors.newFixedThreadPool(writers)
        repeat(writers) { index ->
            executor.execute {
                try {
                    start.await()
                    EncryptedBrowserSessionStore(root, key).save(
                        BrowserSessionSnapshot(listOf(BrowserTab(id = "tab-$index")), "tab-$index"),
                    )
                } catch (failure: Throwable) {
                    synchronized(failures) { failures += failure }
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertTrue("Timed out waiting for metadata saves", done.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertTrue("Concurrent saves must not fail: $failures", failures.isEmpty())
        assertEquals(1, checkNotNull(EncryptedBrowserSessionStore(root, key).load()).tabs.size)
        key.fill(0); root.deleteRecursively()
    }
}
