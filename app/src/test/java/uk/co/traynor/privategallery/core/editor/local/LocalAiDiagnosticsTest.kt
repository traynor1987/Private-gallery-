package uk.co.traynor.privategallery.core.editor.local

import org.junit.Assert.*
import org.junit.Test

class LocalAiDiagnosticsTest {
    @Test fun historyIsBoundedAndReportsActualBackendWithOptionalMetrics() {
        repeat(7) { index ->
            LocalAiDiagnostics.record(ModelCatalog.lightweight, 512, 512, 90100L + index, "SUCCESS", 0, null,
                BackendRunMetrics(backend = LocalBackend.VULKAN, probeMs = 2, generationMs = 30,
                    thermalStart = 0, thermalEnd = 1, thermalMax = 1, minAvailableRamBytes = 123,
                    fallbackReason = BackendFallbackReason.NONE))
        }
        val summary = LocalAiDiagnostics.summary.value
        assertFalse(summary.contains("90100 ms"))
        assertTrue(summary.contains("90101 ms"))
        assertTrue(summary.contains("90106 ms"))
        assertEquals(6, Regex("19bbbca1").findAll(summary).count())
        assertTrue(summary.contains("VULKAN"))
        assertTrue(summary.contains("probe=2"))
        assertTrue(summary.contains("load=unavailable"))
        assertTrue(summary.contains("minimum available RAM bytes=123"))
    }

    @Test fun unknownModelStringsCannotEnterDiagnostics() {
        val untrusted = ModelCatalog.lightweight.copy(id = "private-model-id", providerId = "private/path/prompt")
        LocalAiDiagnostics.record(untrusted, 512, 512, 1, "FAILED", 0, null)
        val newest = LocalAiDiagnostics.summary.value.substringAfter("Recent local runs")
        assertFalse(newest.contains("private-model-id"))
        assertFalse(newest.contains("private/path/prompt"))
        assertTrue(newest.contains("UNRECOGNIZED"))
        assertTrue(newest.contains("actual backend=unavailable"))
    }
}
