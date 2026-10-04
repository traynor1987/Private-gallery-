package uk.co.traynor.privategallery.core.browser.v2

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Character/UTF8 and caller entry points; no owner media or network. */
@RunWith(AndroidJUnit4::class)
class Phase3VideoHeaderPolicyTest {
    @Test fun platformWhitespacePreservesConservativeManifestClassification() {
        for (prefix in listOf("\u2005", "\u00a0", "\u3000", "\u001c")) {
            val bytes = (prefix + "#EXTM3U").toByteArray()
            assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoHeaderBytePolicy.reason("video/mp4", bytes, bytes.size))
        }
        val partial = "\u3000#EXTM3U".toByteArray()
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoHeaderBytePolicy.reason("video/mp4", partial, 1))
    }

    @Test fun actualValidationEntryPointUsesOnlyTheDeclaredHeaderSlice() {
        val bytes = "#EXTM3U\n".toByteArray(); val original = bytes.copyOf()
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoValidationPolicy.headerReason("video/mp4", bytes, 0))
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoValidationPolicy.headerReason("video/mp4", bytes, 5))
        assertEquals(MediaSaveReason.MANIFEST_DETECTED, VideoValidationPolicy.headerReason("video/mp4", bytes, 7))
        assertArrayEquals(original, bytes)
    }

    @Test fun primitiveContainerSignatureRetainsShortSliceDenial() {
        val bytes = byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray()
        assertNull(VideoValidationPolicy.headerReason("video/mp4", bytes, bytes.size))
        assertEquals(MediaSaveReason.NON_MEDIA_RESPONSE, VideoValidationPolicy.headerReason("video/mp4", bytes, 8))
        assertTrue(validHeader("video/mp4", bytes, bytes.size))
        assertFalse(validHeader("video/mp4", bytes, 8))
    }
}
