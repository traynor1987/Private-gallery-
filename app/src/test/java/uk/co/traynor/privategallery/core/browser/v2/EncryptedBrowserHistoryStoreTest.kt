package uk.co.traynor.privategallery.core.browser.v2

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    @Test fun concurrent_visits_from_independent_stores_are_all_committed() {
        val root = createTempDirectory("browser-history-race").toFile()
        val key = ByteArray(32) { 9 }
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        try {
            val visits = (1..96).map { index -> pool.submit {
                start.await()
                EncryptedBrowserHistoryStore(root, key).add("Visit $index", "https://example.test/$index")
            } }
            start.countDown()
            visits.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(96, EncryptedBrowserHistoryStore(root, key).list().size)
        } finally { pool.shutdownNow(); key.fill(0); root.deleteRecursively() }
    }

    @Test fun unavailable_history_storage_is_nonfatal_to_a_visit() {
        val parent = createTempDirectory("browser-history-failure").toFile()
        val invalidRoot = File(parent, "not-a-directory").apply { writeText("keep") }
        val key = ByteArray(32) { 4 }
        try {
            assertTrue(!recordBrowserHistoryVisit(EncryptedBrowserHistoryStore(invalidRoot, key), "Title", "https://example.test"))
            assertEquals("keep", invalidRoot.readText())
        } finally { key.fill(0); parent.deleteRecursively() }
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
