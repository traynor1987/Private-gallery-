package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test
import java.lang.management.ManagementFactory

class VideoHeaderBytePolicyTest {
    private val mp4 = byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray()

    @Test fun validContainerSignaturesRemainAccepted() {
        for (mime in listOf("video/mp4", "video/quicktime")) assertNull(VideoHeaderBytePolicy.reason(mime, mp4, mp4.size))
        val webm = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        for (mime in listOf("video/webm", "video/x-matroska")) assertNull(VideoHeaderBytePolicy.reason(mime, webm, webm.size))
        assertNull(VideoHeaderBytePolicy.reason("video/mp2t", byteArrayOf(0x47), 1))
    }
    @Test fun incompleteMp4SliceCannotUseStaleContainerBytes() {
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", mp4, 8))
    }
    @Test fun zeroLengthSliceCannotUseAStaleManifestMarker() {
        val bytes = "#EXTM3U\n".toByteArray()
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", bytes, 0))
    }
    @Test fun truncatedManifestMarkerCannotReadBeyondSlice() {
        val bytes = "#EXTM3U\n".toByteArray()
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", bytes, 5))
    }
    @Test fun incompleteHtmlMarkerCannotReadBeyondSlice() {
        val bytes = "<html>challenge</html>".toByteArray()
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", bytes, 3))
    }
    @Test fun manifestAndNonMediaCategoriesRemainConservative() {
        for (text in listOf("#EXTM3U\n", " \t\r\n<MPD", "#eXtM3u", "<mpd")) {
            val bytes = text.toByteArray()
            assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoHeaderBytePolicy.reason("video/mp4", bytes, bytes.size))
        }
        for (text in listOf("<html>", "<!doctype html>", " {\"error\":1}")) {
            val bytes = text.toByteArray()
            assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", bytes, bytes.size))
        }
    }
    @Test fun unicodeLeadingWhitespacePreservesLegacyManifestDenial() {
        for (prefix in listOf("\u2005", "\u00a0", "\u3000", "\u001c")) {
            val bytes = (prefix + "#EXTM3U").toByteArray()
            assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoHeaderBytePolicy.reason("video/mp4", bytes, bytes.size))
        }
    }
    @Test fun incompleteUnicodeWhitespaceCannotInspectTheLaterManifest() {
        val bytes = "\u3000#EXTM3U".toByteArray()
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", bytes, 1))
    }
    @Test fun completeManifestMarkerBeforeUnexaminedSuffixStillDenies() {
        val bytes = "#EXTM3U".toByteArray() + byteArrayOf(0xff.toByte(), 0, 0)
        assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoHeaderBytePolicy.reason("video/mp4", bytes, 7))
    }
    @Test fun unknownMimeAndMalformedHeaderCannotPass() {
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/unknown", mp4, mp4.size))
        val bytes = byteArrayOf(0xff.toByte(), 0xc0.toByte(), 0x80.toByte())
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", bytes, bytes.size))
    }
    @Test fun invalidSliceAndUnboundedBackingArrayDenyBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { VideoHeaderBytePolicy.reason("video/mp4", mp4, -1) }
        assertThrows(IllegalArgumentException::class.java) { VideoHeaderBytePolicy.reason("video/mp4", mp4, mp4.size + 1) }
        assertThrows(IllegalArgumentException::class.java) { VideoHeaderBytePolicy.reason("video/mp4", ByteArray(64 * 1024 + 1), 12) }
    }
    @Test fun classificationDoesNotModifyTheOriginalOwnedBytes() {
        val bytes = " \n#EXTM3U".toByteArray(); val before = bytes.copyOf()
        assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoHeaderBytePolicy.reason("video/mp4", bytes, bytes.size))
        assertArrayEquals(before, bytes)
    }
    @Test fun repeatedClassificationCreatesNoCopiedSecretStringOrArray() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        assertTrue(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val id = Thread.currentThread().id
        repeat(20_000) { VideoHeaderBytePolicy.reason("video/mp4", mp4, mp4.size) }
        val before = bean.getThreadAllocatedBytes(id)
        var nonMedia = 0
        repeat(50_000) { if (VideoHeaderBytePolicy.reason("video/mp4", mp4, mp4.size) != null) nonMedia++ }
        val allocated = bean.getThreadAllocatedBytes(id) - before
        assertEquals(0, nonMedia)
        assertTrue("Unexpected classification allocation: $allocated", allocated <= 8192)
    }
}
