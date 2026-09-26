package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Adapters own their documented API contract, HTTPS transport and secure credential retrieval.
 * They must support cancellation and bounded response streaming, without logging bodies/prompts.
 * They NEVER receive a Context, Vault repository, source ID, filename, key or Browser state here.
 * Network policy: Android default routing (including any active VPN); independent of Browser's
 * owned-VPN gate. No bindProcessToNetwork, socket bypass, or alternate VPN implementation.
 */
interface AiImageEditProvider {
    val id: String
    val displayName: String
    val processing: AiProcessing get() = AiProcessing.CLOUD
    val modelId: String? get() = null
    val timeoutMillis: Long get() = 90_000L
    val configured: Boolean get() = true
    val ready: Boolean get() = true
    val availabilityLabel: String get() = if (ready) "Available" else "Unavailable"
    val automatic: Boolean get() = false
    val progress: kotlinx.coroutines.flow.StateFlow<String?>? get() = null
    fun resolve(capability: AiCapability): AiImageEditProvider? = this
    val capabilities: Set<AiCapability>
    suspend fun edit(request: AiEditRequest): ByteArray
    fun invalidate() {}
}
enum class AiCapability(val label: String) {
    GENERATIVE_EDIT("Describe a change"), OBJECT_REMOVAL("Remove object"), GENERATIVE_FILL("Replace selection"),
    BACKGROUND_REMOVE("Remove background"), BACKGROUND_REPLACE("Replace background"),
    OUTPAINT("Expand canvas"), RESTYLE("Restyle"),
}
data class AiParameters(
    val capability: AiCapability = AiCapability.GENERATIVE_EDIT,
    val prompt: String = "",
    val strokes: List<MaskStroke> = emptyList(),
    val aspect: Float? = null,
) {
    init {
        require(prompt.length <= 4000 && strokes.size <= 128 && strokes.sumOf { it.points.size } <= 16384)
        require(aspect == null || (aspect.isFinite() && aspect in .25f..4f))
    }
}
/** image is newly encoded PNG. Mask coordinates refer to that image, never the screen. */
data class AiEditRequest(val image: ByteArray, val parameters: AiParameters)
open class AiEditFailure(message: String) : Exception(message)

/** Owner setup uses the documented Replicate adapter; other adapters keep the same boundary. */
object AiProviderRegistry {
    @Volatile private var configuration: AiProviderConfiguration? = null
    @Volatile private var openAiConfiguration: AiProviderConfiguration? = null
    @Volatile private var preferences: android.content.SharedPreferences? = null
    val replicate: AiProviderConfiguration? get() = configuration
    val openAi: AiProviderConfiguration? get() = openAiConfiguration
    val configured: AiImageEditProvider? get() = provider()
    val choice: AiProviderChoice get() = AiProviderChoice.entries.firstOrNull { it.name == preferences?.getString("provider_choice", null) } ?: AiProviderChoice.REPLICATE
    fun select(value: AiProviderChoice) { preferences?.edit()?.putString("provider_choice", value.name)?.apply() }
    fun provider(value: AiProviderChoice = choice): AiImageEditProvider? = when (value) {
        AiProviderChoice.REPLICATE -> configuration?.provider
        AiProviderChoice.OPENAI -> openAiConfiguration?.provider
    }
    val selected: AiImageEditProvider? get() = provider()
    @Synchronized fun initialize(context: android.content.Context): AiProviderConfiguration =
        configuration ?: androidAiConfiguration(context).also {
            configuration = it
            val selection = context.applicationContext.getSharedPreferences("ai_provider_selection", android.content.Context.MODE_PRIVATE)
            preferences = selection
            openAiConfiguration = androidOpenAiConfiguration(context)
            // Retire old choices without modifying tokens, consent or moderation.
            migrateProviderChoice(selection, it.provider != null)
            context.applicationContext.deleteSharedPreferences("ai_local_models")
        }
    const val NETWORK_POLICY = "Uses the device connection, including any active VPN. Independent of Browser VPN settings."
}

class AiEditPipeline(private val sanitize: (ByteArray) -> ByteArray, private val timeoutMillis: Long = 90_000) {
    suspend fun generate(provider: AiImageEditProvider?, consent: Boolean, selectedImage: ByteArray, parameters: AiParameters): ByteArray {
        val selected = provider ?: throw AiEditFailure("AI editing is not configured.")
        val adapter = selected.resolve(parameters.capability) ?: throw AiEditFailure("No installed provider supports this edit.")
        if (adapter.processing == AiProcessing.CLOUD && !consent) throw AiEditFailure("Remote processing consent is required.")
        if (parameters.capability !in adapter.capabilities) throw AiEditFailure("This provider does not support this tool.")
        if (parameters.capability in setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL) && parameters.strokes.isEmpty()) throw AiEditFailure("Mark the area to edit first.")
        if (selectedImage.size > MAX_BYTES) throw AiEditFailure("This image is too large for AI editing.")
        currentCoroutineContext().ensureActive()
        val outbound = sanitize(selectedImage)
        var response: ByteArray? = null
        var sanitized: ByteArray? = null
        try {
            currentCoroutineContext().ensureActive()
            if (outbound.size > MAX_BYTES) throw AiEditFailure("This image is too large for AI editing.")
            withTimeout(if (adapter.processing == AiProcessing.ON_DEVICE) adapter.timeoutMillis else timeoutMillis) { response = adapter.edit(AiEditRequest(outbound, parameters)) }
            currentCoroutineContext().ensureActive()
            if (response!!.isEmpty() || response!!.size > MAX_BYTES) throw AiEditFailure("The provider returned an invalid image.")
            sanitized = sanitize(response!!)
            currentCoroutineContext().ensureActive()
            val result = sanitized!!
            sanitized = null
            return result
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: Exception) { throw AiEditFailure("AI edit failed. Check your connection and try again.")
        } finally { outbound.fill(0); response?.fill(0); sanitized?.fill(0) }
    }
    companion object { const val MAX_BYTES = 32 * 1024 * 1024 }
}

/** Additive migration: leave all existing choices and all unrelated stores untouched. */
internal fun migrateProviderChoice(preferences: android.content.SharedPreferences, hasCloudConfiguration: Boolean) {
    if (AiProviderChoice.entries.none { it.name == preferences.getString("provider_choice", null) })
        preferences.edit().putString("provider_choice", AiProviderChoice.REPLICATE.name).apply()
}
