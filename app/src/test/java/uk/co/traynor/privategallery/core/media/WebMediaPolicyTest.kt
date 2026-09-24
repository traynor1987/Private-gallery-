package uk.co.traynor.privategallery.core.media
import org.junit.Assert.*
import org.junit.Test
class WebMediaPolicyTest {
    @Test fun directAndAdaptiveFormats() {
        assertEquals("video/mp4", WebMediaPolicy.mime("https://example.com/video.MP4?x=1"))
        assertEquals("application/x-mpegURL", WebMediaPolicy.mime("https://example.com/a.m3u8"))
        assertEquals("application/dash+xml", WebMediaPolicy.mime("https://example.com/a.mpd"))
        assertEquals("video/*", WebMediaPolicy.mime("https://example.com/stream", true))
    }
    @Test fun noDrmOrCredentialOrPrivateSchemeHandoff() {
        listOf("blob:https://example.com/a", "file:///private/vault", "content://vault", "https://owner:secret@example.com/v.mp4", "javascript:video").forEach { assertNull(WebMediaPolicy.mime(it, true)) }
        assertNull(WebMediaPolicy.mime("https://example.com/v.mp4", true, true))
        assertNull(WebMediaPolicy.mime("https://example.com/page"))
    }
    @Test fun vpnLossRejectsFurtherReads() {
        WebMediaPolicy.requireNetwork(true)
        assertThrows(java.io.IOException::class.java) { WebMediaPolicy.requireNetwork(false) }
    }
}
