package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

class CropEditorGeometryTest {
    @Test fun `portrait image fits inside inset editor workspace`() {
        val image = CropEditorGeometry.fitImage(900f, 1400f, 9f / 16f)
        assertTrue(image.left > 0f)
        assertEquals(0f, image.top, .001f)
    }

    @Test fun `landscape image fits inside inset editor workspace`() {
        val image = CropEditorGeometry.fitImage(900f, 1400f, 16f / 9f)
        assertEquals(0f, image.left, .001f)
        assertTrue(image.top > 0f)
    }

    @Test fun `display coordinate maps back to normalized source coordinate`() {
        val image = CropEditorGeometry.fitImage(900f, 1200f, 1f)
        val crop = CropEditorGeometry.cropBounds(image, NormalizedCrop(.1f, .2f, .9f, .8f))
        val point = CropEditorGeometry.normalizePoint(image, crop.left, crop.top)
        assertEquals(.1f, point.first, .001f)
        assertEquals(.2f, point.second, .001f)
    }
}
