package uk.co.traynor.privategallery.core.editor.local

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.BuildConfig

class LocalBackendPolicyTest {
    @Test fun settingsOnlyRetainOverridesInAcceptanceBuilds() {
        try {
            LocalBackendSettings.setOverride(LocalBackendOverride.CPU)
            val expected = if (BuildConfig.DEBUG || BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS)
                LocalBackendOverride.CPU else LocalBackendOverride.AUTO
            assertEquals(expected, LocalBackendSettings.override.value)
        } finally {
            LocalBackendSettings.setOverride(LocalBackendOverride.AUTO)
        }
    }

    @Test fun autoChoosesBestGenuinelyCompatibleBackend() {
        assertEquals(LocalBackend.NPU, LocalBackendPolicy.select(LocalBackend.entries.toSet()))
        assertEquals(LocalBackend.VULKAN, LocalBackendPolicy.select(setOf(LocalBackend.CPU, LocalBackend.VULKAN)))
        assertEquals(LocalBackend.CPU, LocalBackendPolicy.select(setOf(LocalBackend.CPU)))
        assertNull(LocalBackendPolicy.select(emptySet()))
    }

    @Test fun removingFailedCandidateAllowsAutoFallback() {
        val compatible = setOf(LocalBackend.VULKAN, LocalBackend.CPU)
        val selected = LocalBackendPolicy.select(compatible)
        assertEquals(LocalBackend.CPU, LocalBackendPolicy.select(compatible - selected!!))
    }

    @Test fun unavailableForcedBackendDoesNotMasqueradeAsCpu() {
        assertNull(LocalBackendPolicy.select(setOf(LocalBackend.CPU), LocalBackendOverride.VULKAN, true))
        assertNull(LocalBackendPolicy.select(setOf(LocalBackend.VULKAN), LocalBackendOverride.CPU, true))
    }

    @Test fun acceptanceCanForceCpuBaseline() {
        assertEquals(LocalBackend.CPU, LocalBackendPolicy.select(setOf(LocalBackend.CPU, LocalBackend.VULKAN), LocalBackendOverride.CPU, true))
    }

    @Test fun productionRejectsOverrideAndAlwaysUsesAuto() {
        assertEquals(LocalBackend.VULKAN, LocalBackendPolicy.select(setOf(LocalBackend.CPU, LocalBackend.VULKAN), LocalBackendOverride.CPU, false))
        assertEquals(LocalBackend.CPU, LocalBackendPolicy.select(setOf(LocalBackend.CPU), LocalBackendOverride.VULKAN, false))
    }
}
