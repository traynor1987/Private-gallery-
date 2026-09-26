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
        assertEquals(MediaSaveKind.STREAM, BrowserMediaSavePolicy.classify("https://example.org/stream.mpd", false).kind)
        assertEquals(MediaSaveKind.STREAM, BrowserMediaSavePolicy.classify("https://example.org/stream.m3u8", false).kind)
        assertEquals(MediaSaveKind.PROTECTED, BrowserMediaSavePolicy.classify("https://example.org/movie.mp4", true).kind)
        assertEquals(MediaSaveKind.UNKNOWN, BrowserMediaSavePolicy.classify("blob:https://example.org/id", false).kind)
        assertEquals(MediaSaveKind.UNKNOWN, BrowserMediaSavePolicy.classify("https://example.org/watch", false).kind)
        assertEquals(MediaSaveKind.DIRECT, BrowserMediaSavePolicy.classify("https://example.org/opaque/123", false, "video/mp4").kind)
        assertEquals(MediaSaveKind.STREAM, BrowserMediaSavePolicy.classify("https://example.org/opaque/456", false, "application/vnd.apple.mpegurl").kind)
        assertEquals(MediaSaveKind.STREAM, BrowserMediaSavePolicy.classify("https://example.org/opaque/789", false, "application/dash+xml").kind)
    }

    @Test fun observedMediaCanExplainBlobWithoutTreatingBlobAsHttp() {
        val observed = ObservedMediaRequests()
        observed.observe("https://example.org/app.js", emptyMap())
        assertEquals(MediaSaveReason.BLOB_WITHOUT_OBSERVED_SOURCE, observed.best("blob:https://example.org/id", false).reason)
        observed.observe("https://media.example.org/stream.m3u8", emptyMap())
        assertEquals(MediaSaveKind.STREAM, observed.best("blob:https://example.org/id", false).kind)
        observed.clear()
        assertEquals(MediaSaveReason.BLOB_WITHOUT_OBSERVED_SOURCE, observed.best("blob:https://example.org/id", false).reason)
        assertEquals(MediaSaveReason.DRM_DETECTED, observed.best("blob:https://example.org/id", true).reason)
    }

    @Test fun ordinaryHttpsRedirectResolvesWhileDowngradesAndCredentialsAreRejected() {
        val redirected = BrowserMediaProbe.resolveSafeRedirect(java.net.URI("https://example.org/play"),
            "https://cdn.example.net/file")
        assertNotNull(redirected)
        assertEquals(MediaSaveKind.DIRECT, BrowserMediaSavePolicy.classify(redirected.toString(), false, "video/mp4").kind)
        assertNull(BrowserMediaProbe.resolveSafeRedirect(java.net.URI("https://example.org/play"), "http://example.org/file.mp4"))
        assertNull(BrowserMediaProbe.resolveSafeRedirect(java.net.URI("https://example.org/play"), "https://user:pass@example.org/file.mp4"))
    }

    @Test fun onlyLikelyMediaRequestsAreRememberedAndNeverCredentials() {
        val observed = ObservedMediaRequests()
        observed.observe("https://example.org/opaque/1", mapOf("Accept" to "video/mp4", "Authorization" to "Bearer private"))
        assertEquals("https://example.org/opaque/1", observed.best("blob:https://example.org/id", false).url)
        assertFalse(observed.toString().contains("private"))
    }

    @Test fun ordinaryAdaptiveManifestsStayEligibleButEncryptionIsRejected() {
        val hls = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\npart001.ts\n#EXT-X-ENDLIST"
        val dash = "<MPD type=\"static\"><Period><AdaptationSet mimeType=\"video/mp4\"/><AdaptationSet mimeType=\"audio/mp4\"/></Period></MPD>"
        assertFalse(ManifestProtectionPolicy.isProtected(hls))
        assertFalse(ManifestProtectionPolicy.isProtected(dash))
        assertTrue(ManifestProtectionPolicy.isProtected("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\""))
        assertTrue(ManifestProtectionPolicy.isProtected("<MPD><ContentProtection schemeIdUri=\"urn:uuid:...\"/></MPD>"))
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
