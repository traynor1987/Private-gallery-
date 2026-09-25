package uk.co.traynor.privategallery.core.editor.local

import org.junit.Assert.*
import org.junit.Test

class LocalGenerationProgressTest {
    @Test fun percentExistsOnlyForActualCompletedSteps() {
        assertNull(LocalGenerationProgress(LocalStage.LOADING).percent)
        assertNull(LocalGenerationProgress(LocalStage.GENERATING).percent)
        assertEquals(0, LocalGenerationProgress(LocalStage.GENERATING, 0, 20).percent)
        assertEquals(5, LocalGenerationProgress(LocalStage.GENERATING, 1, 20).percent)
        assertEquals(95, LocalGenerationProgress(LocalStage.GENERATING, 19, 20).percent)
        assertEquals(100, LocalGenerationProgress(LocalStage.GENERATING, 20, 20).percent)
        assertNull(LocalGenerationProgress(LocalStage.FINALISING, 20, 20).percent)
        assertNull(LocalGenerationProgress(LocalStage.GENERATING, 21, 20).percent)
    }
    @Test fun reasonCategoriesAndCopyNeverContainUntrustedMessages() {
        assertEquals("Local AI couldn't complete this edit", LocalStopReason.GPU_EXECUTION.title)
        assertEquals("Device needs to cool down", LocalStopReason.THERMAL.title)
        assertEquals("Local AI needs more memory", LocalStopReason.ANDROID_LOW_MEMORY.title)
    }
}
