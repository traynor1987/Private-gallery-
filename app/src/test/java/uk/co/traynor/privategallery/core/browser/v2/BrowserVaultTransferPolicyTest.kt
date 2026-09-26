package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test

class BrowserVaultTransferPolicyTest {
    @Test fun containerHeadersAreCheckedBeforeVaultImport() {
        val mp4 = byteArrayOf(0,0,0,16) + "ftypisom".toByteArray()
        assertTrue(validHeader("video/mp4", mp4, mp4.size))
        assertFalse(validHeader("video/mp4", "<html>error".toByteArray(), 11))
        assertTrue(validHeader("video/webm", byteArrayOf(0x1a,0x45,0xdf.toByte(),0xa3.toByte()), 4))
    }
    @Test fun classifyOnlyRetrievableMedia() {
        assertEquals(MediaSaveKind.DIRECT, BrowserMediaSavePolicy.classify("https://example.org/movie.mp4", false).kind)
        assertEquals(MediaSaveKind.DIRECT, BrowserMediaSavePolicy.classify("https://example.org/movie.webm", false).kind)
        assertEquals(MediaSaveKind.UNSUPPORTED, BrowserMediaSavePolicy.classify("https://example.org/stream.mpd", false).kind)
        assertEquals(MediaSaveKind.UNSUPPORTED, BrowserMediaSavePolicy.classify("https://example.org/stream.m3u8", false).kind)
        assertEquals(MediaSaveKind.PROTECTED, BrowserMediaSavePolicy.classify("https://example.org/movie.mp4", true).kind)
        assertEquals(MediaSaveKind.UNKNOWN, BrowserMediaSavePolicy.classify("blob:https://example.org/id", false).kind)
        assertEquals(MediaSaveKind.UNKNOWN, BrowserMediaSavePolicy.classify("https://example.org/watch", false).kind)
    }

    @Test fun vaultUploadFilteringAndDefault() {
        assertEquals(BrowserUploadPolicy.VAULT_ONLY, BrowserUploadPolicy.parse(null))
        assertEquals(BrowserUploadPolicy.VAULT_ONLY, BrowserUploadPolicy.parse("unexpected"))
        assertTrue(BrowserUploadPolicy.accepts("image/jpeg", arrayOf("image/*")))
        assertFalse(BrowserUploadPolicy.accepts("video/mp4", arrayOf("image/*")))
        assertTrue(BrowserUploadPolicy.accepts("video/webm", arrayOf("video/*")))
        assertFalse(BrowserUploadPolicy.accepts("image/png", arrayOf("image/jpeg")))
        assertTrue(BrowserUploadPolicy.accepts("image/png", arrayOf("*/*")))
        assertEquals("upload.jpg", BrowserUploadPolicy.safeName("image/jpeg"))
        assertEquals("upload.webm", BrowserUploadPolicy.safeName("video/webm"))
    }

    @Test fun progressIsTruthful() {
        assertNull(BrowserMediaSavePolicy.percent(10, null))
        assertNull(BrowserMediaSavePolicy.percent(10, 0))
        assertEquals(42, BrowserMediaSavePolicy.percent(42, 100))
        assertEquals(100, BrowserMediaSavePolicy.percent(120, 100))
    }
}
