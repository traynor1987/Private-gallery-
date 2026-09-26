package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserVideoValidationPolicyTest {
    @Test fun `short header or disguised manifest cannot pass as final media`() {
        assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoValidationPolicy.headerReason(
            "video/mp4", "#EXTM3U\n#EXT-X-TARGETDURATION:4".toByteArray()))
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoValidationPolicy.headerReason(
            "video/mp4", "<html>Challenge</html>".toByteArray()))
    }

    @Test fun `valid header alone is not enough without complete parsable tracks`() {
        val header = byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray()
        assertNull(VideoValidationPolicy.headerReason("video/mp4", header))
        assertEquals(MediaSaveReason.ZERO_DURATION, VideoValidationPolicy.factsReason(12_000, 0, 1, false))
        assertEquals(MediaSaveReason.VIDEO_TRACK_MISSING, VideoValidationPolicy.factsReason(12_000, 2000, 0, true))
        assertEquals(MediaSaveReason.MEDIA_VALIDATION_FAILED, VideoValidationPolicy.factsReason(12, 2000, 1, true))
        assertNull(VideoValidationPolicy.factsReason(12_000, 2000, 1, true))
    }
}
