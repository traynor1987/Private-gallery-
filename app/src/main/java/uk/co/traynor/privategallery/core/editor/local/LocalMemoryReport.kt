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
            val state = LocalCapabilityPolicy.evaluate(model, device, installed[model] == true)
            val admission = when (state) {
                LocalAvailability.SUPPORTED_SLOWER -> "ALLOWED"
                LocalAvailability.LOW_MEMORY -> "WARNING"
                else -> "BLOCKED"
            }
            val reason = when (state) {
                LocalAvailability.SUPPORTED_SLOWER -> "NORMAL_RESOURCES"
                LocalAvailability.LOW_MEMORY -> "AVAILABLE_BELOW_RECOMMENDATION"
                LocalAvailability.MEMORY_PRESSURE -> when {
                    device.lowMemory -> "ANDROID_LOWMEMORY"
                    model == ModelCatalog.advanced && device.availableRam > LocalCapabilityPolicy.stopReserve(device) -> "ADVANCED_ADMISSION_FLOOR"
                    else -> "AVAILABLE_AT_PRESSURE_RESERVE"
                }
                LocalAvailability.INSUFFICIENT_RAM -> "TOTAL_RAM_BELOW_FLOOR"
                else -> state.name
            }
            appendLine("${model.id}: admission=$admission reason=$reason state=$state")
            appendLine("Configured total RAM=${model.minimumRam}; effective total floor=${model.minimumRam * 9 / 10}; recommended available=${model.minimumAvailableRam} bytes")
            appendLine("Memory pressure floor=${LocalCapabilityPolicy.attemptFloor(model, device)} bytes; owner warning enabled=${model == ModelCatalog.lightweight}; modelFileBytes=${model.bytes}; modelResidentBytes=unavailable")
        }
        append("Peak runtime estimate: not established for production weights. Observed worker peak RSS and timing appear after a physical run; mmap resident pages cannot be inferred from file size. RAM thresholds are engineering guardrails, not measured peaks. Memory classes describe the managed heap; RAM Plus is not added to physical RAM.")
    }
}
