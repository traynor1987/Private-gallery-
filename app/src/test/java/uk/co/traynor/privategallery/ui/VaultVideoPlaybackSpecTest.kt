package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultVideoPlaybackSpecTest {
    @Test fun `private video item keeps the original mime type for Media3`() {
        assertEquals("video/mp4", VaultVideoPlaybackSpec.mimeType("video/mp4"))
    }
}
