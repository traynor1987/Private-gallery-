package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerPolicyTest {
    @Test fun `viewer opens on the tapped item and clamps invalid indices`() {
        assertEquals(2, MediaViewerPolicy.initialPage(2, 5))
        assertEquals(0, MediaViewerPolicy.initialPage(-1, 5))
        assertEquals(4, MediaViewerPolicy.initialPage(99, 5))
    }

    @Test fun `tap toggles viewer controls`() {
        assertFalse(MediaViewerPolicy.toggleControls(true))
        assertTrue(MediaViewerPolicy.toggleControls(false))
    }

    @Test fun `viewer sources never cross their datasets`() {
        assertFalse(MediaViewerPolicy.canPageAcross(MediaViewerSource.GALLERY, MediaViewerSource.VAULT))
        assertTrue(MediaViewerPolicy.canPageAcross(MediaViewerSource.VAULT, MediaViewerSource.VAULT))
    }
}
