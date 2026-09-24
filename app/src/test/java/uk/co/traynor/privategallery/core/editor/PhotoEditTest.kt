package uk.co.traynor.privategallery.core.editor

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

class PhotoEditTest {
    @Test fun historyRestoresEveryOperationAndResetIsUndoable() {
        val start = PhotoEdit()
        val rotated = start.rotate().copy(flipHorizontal = true, brightness = .2f, contrast = 1.2f, saturation = .5f)
        val cropped = rotated.copy(crop = NormalizedCrop(.1f, .2f, .8f, .9f))
        var history = EditHistory(start).change(rotated).change(cropped)
        assertEquals(rotated, history.undo().current)
        assertEquals(cropped, history.undo().redo().current)
        history = history.reset()
        assertEquals(start, history.current)
        assertEquals(cropped, history.undo().current)
        assertFalse(history.undo().change(start.rotate()).canRedo)
    }
    @Test fun fourRotationsAreIdentity() { assertEquals(PhotoEdit(), (1..4).fold(PhotoEdit()) { state, _ -> state.rotate() }) }
    @Test fun invalidAdjustmentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PhotoEdit(brightness = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { PhotoEdit(contrast = 10f) }
    }
    @Test fun maskFitIgnoresLetterboxAndMapsImageCentre() {
        assertNull(MaskGeometry.point(50f, 10f, 100f, 200f, 2f))
        assertEquals(MaskPoint(.5f, .5f), MaskGeometry.point(50f, 100f, 100f, 200f, 2f))
    }
    @Test fun historyIsBounded() { var h = EditHistory(); repeat(120) { h = h.change(h.current.rotate()) }; assertTrue(h.past.size <= 40) }
}
