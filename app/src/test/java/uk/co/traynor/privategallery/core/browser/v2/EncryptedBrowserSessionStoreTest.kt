package uk.co.traynor.privategallery.core.browser.v2

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher

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
        check(!File(root, "browser-session-v3.enc").readText().contains("example.test"))
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

    /** Exact historical v2 serialization, with no owner/provenance marker. */
    private fun writeLegacyV2(root: File, key: ByteArray, snapshot: BrowserSessionSnapshot): ByteArray {
        val plain = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeUTF(snapshot.selectedTabId.orEmpty()); data.writeInt(snapshot.tabs.size)
                snapshot.tabs.forEach { tab ->
                    data.writeUTF(tab.id); data.writeUTF(tab.url); data.writeUTF(tab.title); data.writeBoolean(tab.desktopSite)
                }
            }
            output.toByteArray()
        }
        val nonce = ByteArray(EncryptionHeader.NONCE_BYTES) { 17 }
        val legacy = File(root, "browser-session-v2.enc")
        try {
            FileOutputStream(legacy).use { output ->
                output.write(nonce)
                VaultCipher.encrypt(ByteArrayInputStream(plain), output, key, "private-gallery:browser-session:v2".encodeToByteArray(), nonce)
            }
            return legacy.readBytes()
        } finally { plain.fill(0) }
    }

    @Test fun ownerlessV2IsPreservedForExplicitRecoveryAndNeverAutomaticallyPresented() {
        val root = createTempDirectory("browser-legacy").toFile()
        val key = ByteArray(32) { 13 }
        try {
            // The same URL can legitimately occur under multiple owners; no URL inference is valid.
            val shared = "https://example.test/shared"
            val legacy = BrowserSessionSnapshot(listOf(
                BrowserTab(id = "ordinary-looking", url = shared, title = "Browser"),
                BrowserTab(id = "app-looking", url = shared, title = "Saved app"),
                BrowserTab(id = "other", url = "https://example.test/other", title = "Other"),
            ), "app-looking")
            val original = writeLegacyV2(root, key, legacy)
            val store = EncryptedBrowserSessionStore(root, key)
            assertEquals(null, store.loadForPresentation())
            assertEquals(legacy, store.load()) // Preserve the historical binary API's read semantics.
            assertEquals(legacy.tabs, store.legacyRecoveryEntries())
            val mislabeled = File(root, "browser-session-v3.enc")
            mislabeled.writeBytes(original)
            assertTrue(runCatching { store.loadForPresentation() }.isFailure)
            assertTrue(mislabeled.delete())

            val trusted = BrowserSessionSnapshot(listOf(BrowserTab(id = "explicit-new-browser", url = shared)), "explicit-new-browser")
            store.save(trusted)
            assertEquals(trusted, store.loadForPresentation())
            assertEquals(trusted, store.load())
            assertEquals(legacy.tabs, store.legacyRecoveryEntries())
            assertTrue(original.contentEquals(File(root, "browser-session-v2.enc").readBytes()))
        } finally { key.fill(0); root.deleteRecursively() }
    }
}
