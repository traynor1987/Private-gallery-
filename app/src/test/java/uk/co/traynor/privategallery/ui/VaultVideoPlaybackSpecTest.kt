package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultVideoPlaybackSpecTest {
    @Test fun `private video item keeps the original mime type for Media3`() {
        assertEquals("video/mp4", VaultVideoPlaybackSpec.mimeType("video/mp4"))
    }

    @Test fun `private video item has a meaningful container extension as well as mime type`() {
        assertEquals("memory://private-gallery/video.mp4", VaultVideoPlaybackSpec.uriFor("video/mp4").toString())
        assertEquals("memory://private-gallery/video.webm", VaultVideoPlaybackSpec.uriFor("video/webm").toString())
    }
}
