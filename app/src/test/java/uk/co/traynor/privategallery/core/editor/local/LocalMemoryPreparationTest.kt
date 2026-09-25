package uk.co.traynor.privategallery.core.editor.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMemoryPreparationTest {
    @Test fun staleActivityCannotRemoveNewActivitiesCleanup() {
        var first = 0
        var second = 0
        val old = LocalMemoryPreparation.attach { first++ }
        val current = LocalMemoryPreparation.attach { second++ }
        old()
        LocalMemoryPreparation.release()
        assertEquals(0, first)
        assertEquals(1, second)
        current()
        LocalMemoryPreparation.release()
        assertEquals(1, second)
    }
}
