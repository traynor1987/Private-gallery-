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
    @Test fun nativeCallbackExcludesModelLoadAndVaeTileProgress() {
        val completed = mutableListOf<Pair<Int, Int>>()
        val callback = NativeProgress({ step, total -> completed += step to total }, { _, _ -> })
        callback.onStage(4, 0)
        callback.onStep(1, 20) // An equally sized model-loading progress callback is still preparation.
        callback.onStage(5, 0)
        callback.onStep(1, 4) // VAE tiling shares the upstream callback.
        callback.onStep(0, 20)
        callback.onStep(1, 20)
        callback.onStep(1, 20)
        callback.onStep(2, 20)
        callback.onStage(2, 1500)
        callback.onStep(3, 20)
        assertEquals(listOf(1 to 20, 2 to 20), completed)
    }
    @Test fun workerFailuresAndCpuFallbackPreserveTheirCategories() {
        assertEquals(LocalStopReason.MODEL_LOAD, classifyLocalWorkerFailure(LocalInferenceService.MODEL_LOAD_FAILED, true))
        assertEquals(LocalStopReason.JAVA_HEAP, classifyLocalWorkerFailure(LocalInferenceService.JAVA_HEAP_FAILED, true))
        assertEquals(LocalStopReason.GPU_ALLOCATION, classifyLocalWorkerFailure(LocalInferenceService.NATIVE_ALLOCATION_FAILED, true))
        assertEquals(LocalStopReason.CPU_ALLOCATION, classifyLocalWorkerFailure(LocalInferenceService.NATIVE_ALLOCATION_FAILED, false))
        assertEquals(LocalStopReason.GPU_EXECUTION, classifyLocalWorkerFailure(LocalInferenceService.INFERENCE_FAILED, true))
        assertEquals(LocalStopReason.CPU_EXECUTION, classifyLocalWorkerFailure(LocalInferenceService.INFERENCE_FAILED, false))
        assertEquals(LocalStopReason.ANDROID_LOW_MEMORY, classifyLocalWorkerFailure(LocalInferenceService.ANDROID_LOW_MEMORY, false))
        assertFalse(canRetryCpu(LocalStopReason.GPU_EXECUTION, false))
        assertTrue(canRetryCpu(LocalStopReason.GPU_EXECUTION, true))
        assertFalse(canRetryCpu(LocalStopReason.THERMAL, true))
        assertFalse(canRetryCpu(LocalStopReason.JAVA_HEAP, true))
        assertFalse(canRetryCpu(LocalStopReason.USER_CANCELLED, true))
    }
}
