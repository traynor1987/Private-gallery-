package uk.co.traynor.privategallery.core.media
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
class EncryptedPreviewCacheTest {
    @Test fun durableHitAndAuthenticatedKey() {
        val root = Files.createTempDirectory("previews").toFile()
        try {
            val key = ByteArray(32) { 7 }; val pixels = "private-preview".toByteArray()
            EncryptedPreviewCache(root).put("item", "revision1", pixels, key)
            assertArrayEquals(pixels, EncryptedPreviewCache(root).get("item", "revision1", key))
            assertNull(EncryptedPreviewCache(root).get("item", "revision2", key))
            assertNull(EncryptedPreviewCache(root).get("item", "revision1", ByteArray(32)))
            assertFalse(root.walk().filter { it.isFile }.any { it.readBytes().contentEquals(pixels) })
            EncryptedPreviewCache(root).remove("item")
            assertNull(EncryptedPreviewCache(root).get("item", "revision1", key))
        } finally { root.deleteRecursively() }
    }
    @Test fun diskBudgetIsBounded() {
        val root = Files.createTempDirectory("previews").toFile()
        try {
            val cache = EncryptedPreviewCache(root, 180)
            repeat(8) { cache.put("$it", "v1", ByteArray(60), ByteArray(32)) }
            assertTrue(root.listFiles()!!.sumOf { it.length() } <= 180)
        } finally { root.deleteRecursively() }
    }
}
