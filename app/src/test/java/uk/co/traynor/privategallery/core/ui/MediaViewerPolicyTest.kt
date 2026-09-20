package uk.co.traynor.privategallery.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
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

    @Test fun `fit-to-screen media pages while zoomed images pan`() {
        assertTrue(MediaViewerPolicy.canSwipePager(isImageZoomed = false))
        assertFalse(MediaViewerPolicy.canSwipePager(isImageZoomed = true))
    }

    @Test fun `viewer sources never cross their datasets`() {
        assertFalse(MediaViewerPolicy.canPageAcross(MediaViewerSource.GALLERY, MediaViewerSource.VAULT))
        assertTrue(MediaViewerPolicy.canPageAcross(MediaViewerSource.VAULT, MediaViewerSource.VAULT))
    }

    @Test fun `photo zoom clamps and double tap returns to fitted scale`() {
        assertEquals(5f, MediaViewerPolicy.clampedScale(9f), 0.001f)
        assertEquals(1f, MediaViewerPolicy.clampedScale(.2f), 0.001f)
        assertEquals(2.5f, MediaViewerPolicy.doubleTapScale(1f), 0.001f)
        assertEquals(1f, MediaViewerPolicy.doubleTapScale(2.5f), 0.001f)
    }

    @Test fun `zoomed photo pan stays bounded while fitted photo resets pan`() {
        val viewport = IntSize(1000, 800)
        val bounded = MediaViewerPolicy.boundedPan(Offset(2000f, -2000f), 3f, viewport)

        assertEquals(1000f, bounded.x, 0.001f)
        assertEquals(-800f, bounded.y, 0.001f)
        assertEquals(Offset.Zero, MediaViewerPolicy.boundedPan(Offset(20f, 30f), 1f, viewport))
    }
}
