package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedMediaTilePolicyTest {
    @Test fun `photos do not receive redundant labels`() {
        assertFalse(ProtectedMediaTilePolicy.showEncryptionFooter)
        assertFalse(ProtectedMediaTilePolicy.showVideoIndicator("image/jpeg"))
    }

    @Test fun `videos retain a restrained overlay indicator`() {
        assertTrue(ProtectedMediaTilePolicy.showVideoIndicator("video/mp4"))
    }
}
