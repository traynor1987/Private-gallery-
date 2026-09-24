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
    val capabilities: Set<AiCapability>
    suspend fun edit(request: AiEditRequest): ByteArray
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
class AiEditFailure(message: String) : Exception(message)

/** No speculative vendor/endpoint. A reviewed configured adapter can be injected without UI changes. */
object AiProviderRegistry {
    val configured: AiImageEditProvider? = null
    const val NETWORK_POLICY = "Uses the device connection, including any active VPN. Independent of Browser VPN settings."
}

class AiEditPipeline(private val sanitize: (ByteArray) -> ByteArray, private val timeoutMillis: Long = 90_000) {
    suspend fun generate(provider: AiImageEditProvider?, consent: Boolean, selectedImage: ByteArray, parameters: AiParameters): ByteArray {
        val adapter = provider ?: throw AiEditFailure("AI editing is not configured.")
        if (!consent) throw AiEditFailure("Remote processing consent is required.")
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
            response = withTimeout(timeoutMillis) { adapter.edit(AiEditRequest(outbound, parameters)) }
            currentCoroutineContext().ensureActive()
            if (response.isEmpty() || response.size > MAX_BYTES) throw AiEditFailure("The provider returned an invalid image.")
            sanitized = sanitize(response)
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
