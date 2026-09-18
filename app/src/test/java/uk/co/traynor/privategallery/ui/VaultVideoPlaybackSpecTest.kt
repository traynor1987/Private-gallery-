package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultVideoPlaybackSpecTest {
    @Test fun `private video item keeps the original mime type for Media3`() {
        val mediaItem = VaultVideoPlaybackSpec.mediaItem("video/mp4")

        assertEquals("video/mp4", mediaItem.localConfiguration?.mimeType)
    }
}
