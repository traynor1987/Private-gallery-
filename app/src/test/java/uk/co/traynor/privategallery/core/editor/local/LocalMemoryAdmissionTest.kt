package uk.co.traynor.privategallery.core.editor.local

import org.junit.Assert.*
import org.junit.Test

class LocalMemoryAdmissionTest {
    private val model = ModelCatalog.lightweight
    private val device = DeviceResources(36, true, true, 11 * ModelCatalog.GIB,
        7 * ModelCatalog.GIB / 2, false, false)

    @Test fun transientFreeRamBelowRecommendationIsLowMemoryNotUnsupported() {
        assertEquals("LOW_MEMORY", LocalCapabilityPolicy.evaluate(model, device, true).name)
    }

    @Test fun ownerMustConfirmEveryTransientLowMemoryAttempt() {
        assertFalse(LocalCapabilityPolicy.canStart(model, device, true, false))
        assertTrue(LocalCapabilityPolicy.canStart(model, device, true, true))
        assertFalse(LocalCapabilityPolicy.canStart(model, device, true, false))
    }
    @Test fun unsafeResourcesCannotBeOverridden() {
        val unsafe = listOf(device.copy(lowMemory = true), device.copy(tooHot = true),
            device.copy(totalRam = 4 * ModelCatalog.GIB), device.copy(availableRam = 2 * ModelCatalog.GIB),
            device.copy(abiSupported = false), device.copy(runtimeAvailable = false), device.copy(api = 28),
            device.copy(lowMemoryThreshold = 3 * ModelCatalog.GIB))
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
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = reserve)))
        assertFalse(LocalCapabilityPolicy.shouldStop(device.copy(availableRam = reserve + 1)))
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(lowMemory = true)))
        assertTrue(LocalCapabilityPolicy.shouldStop(device.copy(tooHot = true)))
        assertEquals(3 * ModelCatalog.GIB / 4, LocalCapabilityPolicy.stopReserve(device.copy(lowMemoryThreshold = ModelCatalog.GIB / 2)))
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
    }
}
