package uk.co.traynor.privategallery.core.editor.local

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.PhotoRenderer

class LocalMemoryAdmissionTest {
    private val model = ModelCatalog.lightweight
    private val device = DeviceResources(36, true, true, 11 * ModelCatalog.GIB,
        7 * ModelCatalog.GIB / 2, false, false)

    @Test fun transientFreeRamBelowRecommendationIsLowMemoryNotUnsupported() {
        assertEquals("LOW_MEMORY", LocalCapabilityPolicy.evaluate(model, device, true).name)
        assertEquals(LocalAvailability.LOW_MEMORY, LocalCapabilityPolicy.evaluate(model, device.copy(availableRam = 2 * ModelCatalog.GIB), true))
        assertTrue(LocalCapabilityPolicy.canStart(model, device.copy(availableRam = 2 * ModelCatalog.GIB), true, true))
    }

    @Test fun ownerMustConfirmEveryTransientLowMemoryAttempt() {
        assertFalse(LocalCapabilityPolicy.canStart(model, device, true, false))
        assertTrue(LocalCapabilityPolicy.canStart(model, device, true, true))
        assertFalse(LocalCapabilityPolicy.canStart(model, device, true, false))
    }
    @Test fun unsafeResourcesCannotBeOverridden() {
        val unsafe = listOf(device.copy(lowMemory = true), device.copy(tooHot = true),
            device.copy(totalRam = 4 * ModelCatalog.GIB),
            device.copy(abiSupported = false), device.copy(runtimeAvailable = false), device.copy(api = 28),
            device.copy(lowMemoryThreshold = 4 * ModelCatalog.GIB, lowMemory = true))
        unsafe.forEach { assertFalse(LocalCapabilityPolicy.canStart(model, it, true, true)) }
        assertFalse(LocalCapabilityPolicy.canStart(model, device, false, true))
    }
    @Test fun normalAdmissionAndAdvancedProtectionRemain() {
        assertTrue(LocalCapabilityPolicy.canStart(model, device.copy(availableRam = 4 * ModelCatalog.GIB), true, false))
        assertFalse(LocalCapabilityPolicy.canStart(ModelCatalog.advanced,
            device.copy(totalRam = 16 * ModelCatalog.GIB, availableRam = 7 * ModelCatalog.GIB), true, true))
    }
    @Test fun pressureCancellationRetainsSystemReserve() {
        val reserve = LocalCapabilityPolicy.stopReserve(device)
        assertFalse(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = reserve)))
        assertFalse(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = reserve + 1)))
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(lowMemory = true)))
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(tooHot = true)))
        assertEquals(3 * ModelCatalog.GIB / 4, LocalCapabilityPolicy.stopReserve(device.copy(lowMemoryThreshold = ModelCatalog.GIB / 2)))
    }
    @Test fun cachedAvailableMemoryAloneCannotAbortLightweightInference() {
        assertFalse(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = 100L)))
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = 100L, lowMemory = true)))
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = 100L, tooHot = true)))
        assertTrue(LocalCapabilityPolicy.shouldStop(ModelCatalog.advanced, device.copy(availableRam = 100L)))
    }
    @Test fun androidPressureThresholdBlocksButCachedRamDoesNot() {
        val pressured = device.copy(availableRam = 2 * ModelCatalog.GIB, lowMemoryThreshold = 2 * ModelCatalog.GIB)
        assertEquals(LocalAvailability.LOW_MEMORY, LocalCapabilityPolicy.evaluate(model, pressured, true))
        assertTrue(LocalCapabilityPolicy.canStart(model, pressured, true, true))
        assertFalse(LocalCapabilityPolicy.canStart(model, pressured.copy(lowMemory = true), true, true))
        assertTrue(LocalMemoryReport.format(pressured.copy(lowMemory = true), mapOf(model to true)).contains("reason=ANDROID_LOWMEMORY"))
        assertEquals(LocalAvailability.LOW_MEMORY, LocalCapabilityPolicy.evaluate(model, pressured.copy(availableRam = 3 * ModelCatalog.GIB), true))
    }
    @Test fun localPreviewSamplesLargeImagesBeforeAllocation() {
        assertEquals(8, PhotoRenderer.sampleSizeForPixels(4000L * 3000, 512L * 512))
        assertEquals(1, PhotoRenderer.sampleSizeForPixels(512L * 512, 512L * 512))
    }
    @Test fun diagnosticSnapshotIncludesExactFactsAndDoesNotInventPeak() {
        val report = LocalMemoryReport.format(device.copy(memoryClassMb = 256, largeMemoryClassMb = 512,
            lowMemoryThreshold = 123456L, javaHeapLimit = 234567L), mapOf(model to true))
        assertTrue(report.contains("totalRamBytes=11811160064"))
        assertTrue(report.contains("availableRamBytes=3758096384"))
        assertTrue(report.contains("memoryClassMB=256; largeMemoryClassMB=512; javaHeapLimitBytes=234567"))
        assertTrue(report.contains("lowMemory=false; androidLowMemoryThresholdBytes=123456"))
        assertTrue(report.contains("LOW_MEMORY"))
        assertTrue(report.contains("recommended available=4294967296"))
        assertTrue(report.contains("Peak runtime estimate: not established"))
        assertTrue(report.contains("admission=WARNING"))
        assertTrue(report.contains("reason=AVAILABLE_BELOW_RECOMMENDATION"))
        assertTrue(report.contains("modelResidentBytes=unavailable"))
    }
}
