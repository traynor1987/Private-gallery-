package uk.co.traynor.privategallery.core.security

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.EncryptedBookmarkStore
import uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore
import uk.co.traynor.privategallery.core.media.EncryptedPreviewCache
import uk.co.traynor.privategallery.core.vpn.EncryptedVpnProfileStore
import uk.co.traynor.privategallery.core.vpn.VpnProfileSnapshot

class ProtectedStorePromotionTest {
    private val deny: (() -> Unit) -> Unit = { throw IllegalStateException("synthetic revoked epoch") }
    @Test fun browserBookmarkAndHistoryDenialRetainsCommittedBytes() {
        val root = Files.createTempDirectory("phase0-store").toFile()
        val key = ByteArray(32) { 9 }
        try {
            val bookmarks = root.resolve("bookmarks")
            EncryptedBookmarkStore(bookmarks, key).add("synthetic", "https://example.invalid/")
            val bookmarkBefore = bookmarks.listFiles()!!.single { it.extension == "enc" }.readBytes()
            assertThrows(IllegalStateException::class.java) { EncryptedBookmarkStore(bookmarks, key, deny).add("denied", "https://example.invalid/denied") }
            assertArrayEquals(bookmarkBefore, bookmarks.listFiles()!!.single { it.extension == "enc" }.readBytes())
            val history = root.resolve("history")
            EncryptedBrowserHistoryStore(history, key).add("synthetic", "https://example.invalid/")
            val historyBefore = history.listFiles()!!.single { it.extension == "enc" }.readBytes()
            assertThrows(IllegalStateException::class.java) { EncryptedBrowserHistoryStore(history, key, deny).clear() }
            assertArrayEquals(historyBefore, history.listFiles()!!.single { it.extension == "enc" }.readBytes())
        } finally { key.fill(0); root.deleteRecursively() }
    }
    @Test fun previewAndVpnDenialCannotReplaceVerifiedCiphertext() {
        val root = Files.createTempDirectory("phase0-promotion").toFile()
        val key = ByteArray(32) { 7 }
        try {
            val previews = root.resolve("previews")
            EncryptedPreviewCache(previews).put("synthetic", "one", byteArrayOf(1, 2), key)
            val before = previews.listFiles()!!.single { it.extension == "enc" }.readBytes()
            assertThrows(IllegalStateException::class.java) { EncryptedPreviewCache(previews, commit = deny).put("synthetic", "two", byteArrayOf(3, 4), key) }
            assertArrayEquals(before, previews.listFiles()!!.single { it.extension == "enc" }.readBytes())
            val vpn = root.resolve("vpn")
            val store = EncryptedVpnProfileStore(vpn)
            store.save(VpnProfileSnapshot(), key)
            val vpnBefore = vpn.resolve("vpn-profiles.enc").readBytes()
            assertThrows(IllegalStateException::class.java) { store.save(VpnProfileSnapshot(), key, deny) }
            assertArrayEquals(vpnBefore, vpn.resolve("vpn-profiles.enc").readBytes())
        } finally { key.fill(0); root.deleteRecursively() }
    }
}
