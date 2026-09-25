package uk.co.traynor.privategallery.core.editor

enum class AiProcessing { ON_DEVICE, CLOUD }
enum class AiProviderChoice(val label: String) {
    AUTO("Auto"), LIGHTWEIGHT("On-device · Local — Lightweight"), ADVANCED("On-device · Local — Advanced"), REPLICATE("Cloud · Replicate · Seedream 4.5")
}
/** Resolves without network requests. Never falls back after a local generation failure. */
class AutoAiProvider(private val local: () -> List<AiImageEditProvider>, private val cloud: () -> AiImageEditProvider?) : AiImageEditProvider {
    override val id = "auto"
    override val displayName = "Auto"
    override val automatic = true
    override val capabilities get() = (local().flatMap { it.capabilities } + cloud()?.capabilities.orEmpty()).toSet()
    override fun resolve(capability: AiCapability): AiImageEditProvider? =
        local().firstOrNull { it.ready && capability in it.capabilities } ?: cloud()?.takeIf { capability in it.capabilities }
    override suspend fun edit(request: AiEditRequest): ByteArray = throw AiEditFailure("Resolve Auto and confirm any cloud use before generation.")
}
