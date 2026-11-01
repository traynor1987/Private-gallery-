package uk.co.traynor.privategallery.core.editor

import org.junit.Assert.*
import org.junit.Test

class EditorToneModelTest {
    @Test fun neutralDefaultsAndHistoryRetainAllAdjustments() {
        val neutral = PhotoEdit()
        assertEquals(0f, neutral.exposure, 0f)
        assertEquals(0f, neutral.temperature, 0f)
        val adjusted = neutral.copy(exposure = 1f, temperature = .5f)
        val history = EditHistory().change(adjusted)
        assertEquals(adjusted, history.undo().redo().current)
        assertEquals(neutral, history.reset().current)
        assertEquals(adjusted, history.reset().undo().current)
    }
    @Test fun nonfiniteAndOutOfRangeAdjustmentsAreRejected() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -2.01f, 2.01f))
            assertThrows(IllegalArgumentException::class.java) { PhotoEdit(exposure = invalid) }
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1.01f, 1.01f))
            assertThrows(IllegalArgumentException::class.java) { PhotoEdit(temperature = invalid) }
    }
}
