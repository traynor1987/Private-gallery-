package uk.co.traynor.privategallery.core.editor.local

/** Reviewed DATA only. No custom repositories, dynamic manifests or executable downloads. */
data class ModelSpec(
    val id: String, val name: String, val providerId: String, val url: String,
    val bytes: Long, val sha256: String, val licence: String, val licenceAsset: String,
    val maxDimension: Int, val minimumRam: Long, val minimumAvailableRam: Long,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]+")))
        require(bytes > 0 && sha256.matches(Regex("[a-f0-9]{64}")))
    }
}
object ModelCatalog {
    const val GIB = 1024L * 1024 * 1024
    val lightweight = ModelSpec(
        "sd15-fp16-v1", "Stable Diffusion 1.5 · 2.13 GB", "local-lightweight",
        "https://huggingface.co/Comfy-Org/stable-diffusion-v1-5-archive/resolve/4fddeb7f9096623f1b77f4708feb96126a08a0cf/v1-5-pruned-emaonly-fp16.safetensors",
        2132696762L, "e9476a13728cd75d8279f6ec8bad753a66a1957ca375a1464dc63b37db6e3916",
        "CreativeML Open RAIL-M", "ai_models/sd15-license.txt", 512, 8 * GIB, 4 * GIB,
    )
    val advanced = ModelSpec(
        "sdxl-base-1-v1", "Stable Diffusion XL · 6.94 GB", "local-advanced",
        "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/f298da3c058bd8f1f1c62f3ecfa775244a243897/sd_xl_base_1.0.safetensors",
        6938078334L, "31e35c80fc4829d14f90153f4c74cd59c90b779f6afe05a74cd6120b893f7e5b",
        "CreativeML Open RAIL++-M", "ai_models/sdxl-license.txt", 768, 12 * GIB, 8 * GIB,
    )
    val all = listOf(lightweight, advanced)
}

enum class LocalAvailability(val label: String) {
    SUPPORTED_SLOWER("Supported · slower CPU processing"), INSUFFICIENT_RAM("Insufficient RAM"),
    UNSUPPORTED_CHIPSET("Unsupported device architecture"), UNSUPPORTED_ANDROID("Requires Android 10 or newer"),
    MODEL_NOT_INSTALLED("Model not installed"), RUNTIME_NOT_AVAILABLE("Runtime not available"),
    MEMORY_PRESSURE("Not enough free memory"), THERMAL_LIMIT("Device needs to cool down"),
}
data class DeviceResources(val api: Int, val abiSupported: Boolean, val runtimeAvailable: Boolean,
    val totalRam: Long, val availableRam: Long, val lowMemory: Boolean, val tooHot: Boolean)
object LocalCapabilityPolicy {
    /** Admission policy, not a performance guarantee. Recheck immediately before every generation. */
    fun evaluate(model: ModelSpec, device: DeviceResources, installed: Boolean): LocalAvailability = when {
        device.api < 29 -> LocalAvailability.UNSUPPORTED_ANDROID
        !device.abiSupported -> LocalAvailability.UNSUPPORTED_CHIPSET
        !device.runtimeAvailable -> LocalAvailability.RUNTIME_NOT_AVAILABLE
        // OS reserved RAM means marketed 12 GB does not report exactly 12 GiB.
        device.totalRam < model.minimumRam * 9 / 10 -> LocalAvailability.INSUFFICIENT_RAM
        !installed -> LocalAvailability.MODEL_NOT_INSTALLED
        device.lowMemory || device.availableRam < model.minimumAvailableRam -> LocalAvailability.MEMORY_PRESSURE
        device.tooHot -> LocalAvailability.THERMAL_LIMIT
        else -> LocalAvailability.SUPPORTED_SLOWER
    }
}
