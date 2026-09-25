package uk.co.traynor.privategallery.core.editor.local

import uk.co.traynor.privategallery.core.editor.AiEditFailure

enum class LocalStage(val label: String) {
    PREPARING("Preparing your photo…"), VERIFYING("Checking local AI…"),
    PROBING("Preparing GPU…"), LOADING("Loading local AI…"),
    GENERATING("Generating your edit…"), FINALISING("Finalising…"),
}

/** Only a completed diffusion step from the native callback may produce a percentage. */
data class LocalGenerationProgress(val stage: LocalStage, val completedSteps: Int? = null, val totalSteps: Int? = null) {
    val percent: Int? get() = if (stage == LocalStage.GENERATING && completedSteps != null && totalSteps != null &&
        totalSteps > 0 && completedSteps in 0..totalSteps) completedSteps * 100 / totalSteps else null
}

/** Fixed codes; native strings, prompt, filename and driver logs never enter the report. */
enum class LocalStopReason(val title: String, val detail: String) {
    ADMISSION("Local AI isn't ready", "The device needs more room before starting. Your original photo is safe."),
    ANDROID_LOW_MEMORY("Local AI needs more memory", "Android reported genuine memory pressure. Your original photo is safe."),
    OWN_PRESSURE_RESERVE("Local AI needs more memory", "Private Gallery stopped at its safety reserve. Your original photo is safe."),
    JAVA_HEAP("Local AI needs more memory", "The app could not allocate its image buffers. Your original photo is safe."),
    NATIVE_ALLOCATION("Local AI needs more memory", "The local engine could not allocate memory. Your original photo is safe."),
    THERMAL("Device needs to cool down", "Local AI stopped because the phone became too hot. Your original photo is safe."),
    GPU_ALLOCATION("Local AI couldn't complete this edit", "The local engine stopped safely. Your original photo is unchanged."),
    GPU_EXECUTION("Local AI couldn't complete this edit", "The local engine stopped safely. Your original photo is unchanged."),
    CPU_ALLOCATION("Local AI needs more memory", "The CPU engine could not allocate memory. Your original photo is safe."),
    CPU_EXECUTION("Local AI couldn't complete this edit", "The CPU engine stopped safely. Your original photo is unchanged."),
    MODEL_LOAD("Local AI couldn't load", "The local model could not load. Your original photo is unchanged."),
    INFERENCE("Local AI couldn't complete this edit", "The local engine stopped safely. Your original photo is unchanged."),
    WORKER_DIED("Local AI stopped", "The local worker ended before reporting a cause. Your original photo is unchanged."),
    PROMPT_TOO_LONG("Shorten the description", "Local models support up to 75 text tokens."),
    USER_CANCELLED("Edit cancelled", "Your original photo is unchanged."),
}

class LocalGenerationFailure(val reason: LocalStopReason) : AiEditFailure(reason.detail)

internal fun classifyLocalWorkerFailure(code: Int, gpu: Boolean): LocalStopReason = when (code) {
    LocalInferenceService.PROMPT_TOO_LONG -> LocalStopReason.PROMPT_TOO_LONG
    LocalInferenceService.MODEL_LOAD_FAILED -> LocalStopReason.MODEL_LOAD
    LocalInferenceService.NATIVE_ALLOCATION_FAILED -> if (gpu) LocalStopReason.GPU_ALLOCATION else LocalStopReason.CPU_ALLOCATION
    LocalInferenceService.JAVA_HEAP_FAILED -> LocalStopReason.JAVA_HEAP
    LocalInferenceService.ANDROID_LOW_MEMORY -> LocalStopReason.ANDROID_LOW_MEMORY
    else -> if (gpu) LocalStopReason.GPU_EXECUTION else LocalStopReason.CPU_EXECUTION
}

internal fun canRetryCpu(reason: LocalStopReason, gpuWorkerStopped: Boolean): Boolean = gpuWorkerStopped &&
    reason in setOf(LocalStopReason.GPU_ALLOCATION, LocalStopReason.GPU_EXECUTION,
        LocalStopReason.MODEL_LOAD, LocalStopReason.WORKER_DIED)
