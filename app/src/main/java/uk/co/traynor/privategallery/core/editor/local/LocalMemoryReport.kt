package uk.co.traynor.privategallery.core.editor.local

/** Numeric resource facts and fixed catalog IDs only; never includes device/user identifiers. */
object LocalMemoryReport {
    fun format(device: DeviceResources, installed: Map<ModelSpec, Boolean>): String = buildString {
        appendLine("Memory snapshot · Android API ${device.api}")
        appendLine("Sources: ActivityManager.MemoryInfo, memoryClass, largeMemoryClass; Runtime.maxMemory")
        appendLine("totalRamBytes=${device.totalRam}; availableRamBytes=${device.availableRam}")
        appendLine("memoryClassMB=${device.memoryClassMb}; largeMemoryClassMB=${device.largeMemoryClassMb}; javaHeapLimitBytes=${device.javaHeapLimit}")
        appendLine("lowMemory=${device.lowMemory}; androidLowMemoryThresholdBytes=${device.lowMemoryThreshold}; thermalBlocked=${device.tooHot}")
        appendLine("Cancellation reserve bytes=${LocalCapabilityPolicy.stopReserve(device)}")
        ModelCatalog.all.forEach { model ->
            appendLine("${model.id}: ${LocalCapabilityPolicy.evaluate(model, device, installed[model] == true)}")
            appendLine("Configured total RAM=${model.minimumRam}; effective total floor=${model.minimumRam * 9 / 10}; recommended available=${model.minimumAvailableRam} bytes")
            appendLine("Owner-attempt floor=${LocalCapabilityPolicy.attemptFloor(model, device)} bytes; enabled=${model == ModelCatalog.lightweight}")
        }
        append("Peak runtime estimate: not established for production weights. RAM thresholds are engineering guardrails, not measured peaks. See local-ai-memory-acceptance.md. Memory classes describe the managed heap; RAM Plus is not added to physical RAM.")
    }
}
