package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.BuildConfig

/** NPU is reserved for a future implemented, validated runtime; it is not an offered backend. */
enum class LocalBackend { NPU, VULKAN, CPU }
enum class LocalBackendOverride { AUTO, VULKAN, CPU }

/** Candidates must come from runtime/device/model validation, never a chipset name alone. */
object LocalBackendPolicy {
    fun select(
        compatible: Set<LocalBackend>,
        override: LocalBackendOverride = LocalBackendOverride.AUTO,
        allowOverride: Boolean = false,
    ): LocalBackend? {
        val effective = if (allowOverride) override else LocalBackendOverride.AUTO
        return when (effective) {
            LocalBackendOverride.AUTO -> listOf(LocalBackend.NPU, LocalBackend.VULKAN, LocalBackend.CPU).firstOrNull { it in compatible }
            LocalBackendOverride.VULKAN -> LocalBackend.VULKAN.takeIf { it in compatible }
            LocalBackendOverride.CPU -> LocalBackend.CPU.takeIf { it in compatible }
        }
    }
}

/** Session-only acceptance control. Production always uses automatic runtime selection. */
object LocalBackendSettings {
    val enabled get() = BuildConfig.DEBUG || BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS
    private val state = MutableStateFlow(LocalBackendOverride.AUTO)
    val override = state.asStateFlow()
    fun setOverride(value: LocalBackendOverride) {
        state.value = if (enabled) value else LocalBackendOverride.AUTO
    }
}
