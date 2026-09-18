package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPreviewPolicyTest {
    @Test
    fun `generates a protected in-memory preview for supported bounded video`() {
        assertTrue(VaultPreviewPolicy.shouldGenerate(mimeType = "video/mp4", plaintextSize = 12L * 1024 * 1024))
    }

    @Test
    fun `does not eagerly decrypt very large videos for a grid preview`() {
        assertFalse(VaultPreviewPolicy.shouldGenerate(mimeType = "video/mp4", plaintextSize = 50L * 1024 * 1024))
    }

    @Test
    fun `keeps image previews supported`() {
        assertTrue(VaultPreviewPolicy.shouldGenerate(mimeType = "image/jpeg", plaintextSize = 100L * 1024 * 1024))
    }
}
