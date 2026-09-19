package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NormalizedCropTest {
    @Test fun `original is complete source bounds`() = assertEquals(NormalizedCrop.ORIGINAL, NormalizedCrop(0f, 0f, 1f, 1f))

    @Test fun `invalid normalized crop is rejected`() {
        try {
            NormalizedCrop(-.1f, 0f, 1f, 1f)
            fail("Expected invalid crop to fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `minimum crop dimensions are enforced`() {
        try {
            NormalizedCrop(0f, 0f, .01f, 1f)
            fail("Expected undersized crop to fail")
        } catch (_: IllegalArgumentException) { }
    }
}
